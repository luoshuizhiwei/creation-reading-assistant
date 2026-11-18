package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.layout.BreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.TextRuler
import com.creationreadingassistant.feature.reader.locator.LearningProgressEstimator
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import android.os.Build
import android.os.Trace
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun pageIndexContentKey(contentKey: String, source: PagedChapterSource): String {
    if (contentKey.isBlank()) return ""
    val profileKey = (source as? ProjectedChapterSource)?.replaceProfileKey ?: return contentKey
    return "$contentKey|replace=$profileKey"
}

/**
 * 左右翻页的状态机：持有「当前章的整章排版 + 当前页号」。
 *
 * TXT / EPUB 的差别全部收在 [PagedChapterSource] 后面（P4 起 EPUB 也走这里；
 * 类名保留 TxtPagedController 时代的职责描述，实际已与格式无关）。
 *
 * 职责边界：
 * - 章文本加载跑 [Dispatchers.IO]（EPUB 要解压），排版跑 [Dispatchers.Default]，
 *   UI 线程只读状态
 * - 跨章翻页：本章末页再往后 → 排下一章、停在其首页；本章首页再往前 → 排上一章、停在其**末页**
 * - 位置的真源是**全书字符偏移**（[currentPageStartAbs]），页号只是它的展示形式。
 *   改字号/换视口后用旧偏移调 [open]，就会停在同一句。
 *   EPUB 的全书偏移是 LegacyOffsetCodec 的估算基准 —— 与历史 locator 同源，勿自算。
 * - [PageIndexStore] 是写通缓存：每次整章排版后把 pageStarts 落库。
 */
class PagedReaderController(
    private val source: PagedChapterSource,
    private val cfg: LayoutConfig,
    private val ruler: TextRuler,
    private val oracle: BreakOracle,
    private val scope: CoroutineScope,
    private val store: PageIndexStore? = null,
    private val contentKey: String = "",
    private val layoutMutex: Mutex = Mutex(),
) {
    private val effectivePageIndexContentKey = pageIndexContentKey(contentKey, source)

    data class ReaderPageFrame(
        val page: ChapterPaginator.Page,
        val chapterText: String,
        val chapterIndex: Int,
    )

    private data class LoadedChapter(
        val content: PagedChapterContent,
        val projection: BoundedReplaceResult.Exact?,
    )

    private data class CachedChapter(
        val text: String,
        val projection: BoundedReplaceResult.Exact?,
        val layout: ChapterPaginator.ChapterLayout,
    )

    var chapterIndex by mutableIntStateOf(0)
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var layout by mutableStateOf<ChapterPaginator.ChapterLayout?>(null)
        private set

    /** 目标页等待或目标章仍在后台分页。首批页面可在排版期间使用。 */
    var isLayingOut by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    private var awaitingTarget by mutableStateOf(false)
    private var layoutIsPartial by mutableStateOf(false)
    private var currentProjection: BoundedReplaceResult.Exact? = null

    internal val canReportPosition: Boolean get() = currentPage != null && !awaitingTarget
    internal val hasFinalPageCount: Boolean get() = canReportPosition && !layoutIsPartial
    internal val hasNextReadyPage: Boolean get() = !awaitingTarget && pageIndex < pageCount - 1

    val pageCount: Int get() = layout?.pages?.size ?: 0
    val currentPage: ChapterPaginator.Page? get() = layout?.pages?.getOrNull(pageIndex)

    /** 当前章文本（绘制层按 lineParaOffsets 从中切簇）。 */
    var chapterText: String = ""
        private set

    /** 当前页首字符的全书偏移。进度持久化以它为准。 */
    val currentPageStartAbs: Int
        get() {
            val chapterStart = source.chapterStartAbs(chapterIndex)
            val l = layout ?: return chapterStart
            val localDisplay = l.pageStarts.getOrNull(pageIndex) ?: 0
            return currentChapterLocalDisplayToGlobalSource(localDisplay)
        }

    /**
     * 当前页覆盖的全书偏移区间 [start, end)。排版未完成时为 null。
     *
     * 锚点纪律要用它：改字号/转屏引发的重排结束后，若阅读锚点仍落在本区间内，
     * **绝不能把锚点改成新的页首** —— 页首只会往前挪，连续几次重排锚点就会
     * 一路往回漂（真机上表现为连点几次 A+ 后倒退了两三页）。
     */
    val currentPageRangeAbs: IntRange?
        get() {
            val page = currentPage ?: return null
            val start = currentChapterLocalDisplayToGlobalSource(page.startCharOffset)
            val end = currentChapterLocalDisplayToGlobalSource(page.endCharOffset)
            return start until (if (end > start) end else start + 1)
        }

    /** 当前章起始的全书偏移（选区/TTS 的章内↔全书换算用）。 */
    val currentChapterStartAbs: Int
        get() = source.chapterStartAbs(chapterIndex)

    /** 当前章 display 局部偏移 → 持久化用的全书 source 偏移。 */
    fun currentChapterLocalDisplayToGlobalSource(localDisplayOffset: Int): Int =
        currentProjection?.localDisplayToGlobalSource(localDisplayOffset)
            ?: (currentChapterStartAbs + localDisplayOffset)

    /**
     * 持久化层的 source 半开区间 → 当前章 display 局部半开区间。
     * 无投影时保持既有的 `- chapterStart` 语义。
     */
    fun currentChapterSourceRangeToLocalDisplay(
        globalSourceStart: Int,
        globalSourceEnd: Int,
    ): Pair<Int, Int> {
        val projection = currentProjection
        return if (projection != null) {
            projection.globalSourceToLocalDisplay(globalSourceStart) to
                projection.globalSourceToLocalDisplay(globalSourceEnd)
        } else {
            val start = currentChapterStartAbs
            (globalSourceStart - start) to (globalSourceEnd - start)
        }
    }

    val canGoPrev: Boolean get() = pageIndex > 0 || chapterIndex > 0
    val canGoNext: Boolean
        get() = pageIndex < pageCount - 1 || chapterIndex < source.chapterCount - 1 ||
            (layoutIsPartial && isLayingOut)

    /** 全书进度百分比。末章末页视为 100。EPUB 分母是估算值，够显示用。 */
    val progressPercent: Float
        get() {
            val atBookEnd = hasFinalPageCount &&
                chapterIndex == source.chapterCount - 1 && pageIndex == pageCount - 1
            if (source.chapterLengthsAreEstimated) {
                val estimate = progressEstimate ?: LearningProgressEstimator.prepare(
                    estimatedCharCounts,
                    actualCharCounts,
                ).also { progressEstimate = it }
                return estimate.percent(
                    chapterIndex = chapterIndex,
                    charOffset = currentPage?.startCharOffset ?: 0,
                    atBookEnd = atBookEnd,
                ).coerceAtMost(if (atBookEnd) 100f else 99.99999f)
            }
            val total = source.totalChars
            if (total <= 0) return 0f
            if (atBookEnd) return 100f
            return (currentPageStartAbs * 100f / total).coerceIn(0f, 100f)
        }

    private var layoutJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchDirection = 1
    private var pendingPrefixPage = AtomicInteger(0)
    // 排版期间也要接住用户翻页。否则首屏或跨章加载的短窗口中一次点按会被静默丢弃。
    // 留一个等待排版的意图槽，其余等待额度留给动画层。
    private val pendingPageTurns = PendingTurnQueue(capacity = 1)
    internal var turnSession by mutableIntStateOf(0)
        private set
    private data class PrefetchRead(val index: Int, val result: Deferred<Result<LoadedChapter>>)

    // 单一投机读取由 controller 持有。取消预排版时，同章前台请求可以接管读取。
    private var prefetchRead: PrefetchRead? = null
    private val prefetchReadMutex = Mutex()
    private val chapterCache = LinkedHashMap<Int, CachedChapter>(5, 0.75f, true)
    private val actualCharCounts = LinkedHashMap<Int, Int>()
    private val estimatedCharCounts by lazy { List(source.chapterCount, source::chapterEstimatedCharCount) }
    private var progressEstimate: LearningProgressEstimator.Prepared? = null
    private var layoutRequestId = 0
    private var persistedMetricsLoaded = false
    var cacheRevision by mutableIntStateOf(0)
        private set

    // ── TxtFirstPageReady 异步 Trace 状态 ──
    private var firstPageReadyCookie = 0
    private var firstPageReadyTraceActive = false
    private var firstPageReadyStartNs = 0L

    /** 相邻页快照；章内直接取，跨章仅在预排版完成后提供。 */
    fun frameAt(delta: Int): ReaderPageFrame? {
        if (delta == 0) {
            val page = currentPage ?: return null
            return ReaderPageFrame(page, chapterText, chapterIndex)
        }
        if (awaitingTarget) return null
        val currentLayout = layout ?: return null
        val candidate = pageIndex + delta
        if (candidate in currentLayout.pages.indices) {
            return ReaderPageFrame(currentLayout.pages[candidate], chapterText, chapterIndex)
        }
        if (layoutIsPartial) return null
        val targetChapter = chapterIndex + if (delta < 0) -1 else 1
        val cached = chapterCache[targetChapter] ?: return null
        val targetPage = if (delta < 0) cached.layout.pages.lastOrNull() else cached.layout.pages.firstOrNull()
        return targetPage?.let { ReaderPageFrame(it, cached.text, targetChapter) }
    }

    /** 打开到全书偏移 [absOffset] 所在的页。幂等，可反复调。 */
    fun open(absOffset: Int) {
        turnSession++
        pendingPageTurns.clear()
        // ── TxtFirstPageReady: 结束旧的异步 trace（竞态保护） ──
        if (firstPageReadyTraceActive) {
            endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
            firstPageReadyTraceActive = false
        }
        firstPageReadyCookie++
        beginAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
        firstPageReadyTraceActive = true
        firstPageReadyStartNs = SystemClock.elapsedRealtimeNanos()

        val target = absOffset.coerceIn(0, (source.totalChars - 1).coerceAtLeast(0))
        val chIdx = source.chapterIndexFor(target)
        loadChapter(chIdx, targetSourceOffset = target)
    }

    /**
     * 终止此 controller 派生出的排版和预取工作。
     *
     * 宿主会在 source / 配置 / ruler 变化导致 controller 重建时调用；否则旧实例会继续
     * 占用后台线程，并可能保留未结束的首屏 trace。
     */
    fun close() {
        turnSession++
        layoutRequestId++
        layoutJob?.cancel()
        layoutJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchRead?.result?.cancel()
        prefetchRead = null
        pendingPageTurns.clear()
        if (firstPageReadyTraceActive) {
            endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
            firstPageReadyTraceActive = false
        }
    }

    fun nextPage() = requestTurn(1)

    fun prevPage() = requestTurn(-1)

    internal fun discardOppositeWaitingTurn(direction: Int) {
        pendingPageTurns.discardOpposite(direction)
        if (direction < 0) pendingPrefixPage.set(0)
    }

    private fun requestTurn(direction: Int) {
        pendingPageTurns.enqueue(direction)
        replayPendingPageTurns()
    }

    private fun replayPendingPageTurns() {
        pendingPrefixPage.set(0)
        while (!awaitingTarget && layout != null && !pendingPageTurns.isEmpty) {
            val direction = pendingPageTurns.peek() ?: return
            val nextPage = pageIndex + direction
            if (nextPage in 0 until pageCount) {
                pendingPageTurns.poll()
                pageIndex = nextPage
                continue
            }
            val nextChapter = chapterIndex + direction
            if (layoutIsPartial && nextPage >= pageCount) {
                // 预览页的尾部尚不能判定为章尾。失败时丢弃等待未生成页面的输入。
                if (isLayingOut) pendingPrefixPage.set(nextPage + 1) else pendingPageTurns.clear()
                return
            }
            if (layoutIsPartial && isLayingOut && nextChapter in 0 until source.chapterCount) return
            pendingPageTurns.poll()
            if (nextChapter !in 0 until source.chapterCount) continue
            prefetchDirection = direction
            val atEnd = direction < 0
            val cached = chapterCache[nextChapter]
            if (cached == null) {
                loadChapter(nextChapter, atEnd = atEnd)
            } else {
                applyChapter(nextChapter, cached, if (atEnd) cached.layout.pages.lastIndex else 0)
            }
        }
    }

    /** 发布目标章的首批可用页面，并接续排版期间积压的翻页。 */
    private fun applyInterim(chIdx: Int, chapter: LoadedChapter, interimLayout: ChapterPaginator.ChapterLayout, targetPage: Int) {
        chapterText = chapter.content.text
        currentProjection = chapter.projection
        chapterIndex = chIdx
        layout = interimLayout
        pageIndex = targetPage.coerceIn(0, interimLayout.pages.lastIndex.coerceAtLeast(0))
        layoutIsPartial = true
        awaitingTarget = false
        loadError = null

        // ── TxtFirstPageReady: 首页就绪，结束异步 trace ──
        if (firstPageReadyTraceActive) {
            endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
            firstPageReadyTraceActive = false
            val readyEndNs = SystemClock.elapsedRealtimeNanos()
            AppLog.debug("TxtPerfTrace", "TxtFirstPageReady: ${(readyEndNs - firstPageReadyStartNs) / 1_000_000} ms")
        }
        replayPendingPageTurns()
    }

    private fun applyChapter(chIdx: Int, cached: CachedChapter, targetPage: Int) {
        chapterText = cached.text
        currentProjection = cached.projection
        chapterIndex = chIdx
        layout = cached.layout
        pageIndex = targetPage.coerceIn(0, cached.layout.pages.lastIndex.coerceAtLeast(0))
        layoutIsPartial = false
        awaitingTarget = false
        isLayingOut = false
        loadError = null

        // ── TxtFirstPageReady: 首页就绪，结束异步 trace ──
        if (firstPageReadyTraceActive) {
            endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
            firstPageReadyTraceActive = false
            val readyEndNs = SystemClock.elapsedRealtimeNanos()
            AppLog.debug("TxtPerfTrace", "TxtFirstPageReady: ${(readyEndNs - firstPageReadyStartNs) / 1_000_000} ms")
        }

        trimChapterCache(chIdx)
        prefetchNeighbors(chIdx)
    }

    /** 排版目标章，落在首页、末页或 source 偏移所在页。 */
    private fun loadChapter(
        chIdx: Int,
        targetSourceOffset: Int? = null,
        atEnd: Boolean = false,
    ) {
        if (chIdx !in 0 until source.chapterCount) return
        val requestId = ++layoutRequestId
        val prefixDemand = AtomicInteger(0)
        pendingPrefixPage = prefixDemand
        layoutJob?.cancel()
        prefetchJob?.cancel()
        val reusableRead = prefetchRead?.takeIf { it.index == chIdx && chapterCache[chIdx] == null }
        if (reusableRead == null) {
            prefetchRead?.result?.cancel()
            prefetchRead = null
        }
        awaitingTarget = true
        isLayingOut = true
        loadError = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val metricsStore = store?.takeIf {
                    source.chapterLengthsAreEstimated && !persistedMetricsLoaded &&
                        effectivePageIndexContentKey.isNotBlank()
                }
                val metrics = metricsStore?.let { s ->
                    async(Dispatchers.IO) { s.knownCharCounts(effectivePageIndexContentKey) }
                }
                suspend fun restoreMetrics() {
                    if (metrics != null) {
                        for ((index, count) in metrics.await()) actualCharCounts.putIfAbsent(index, count)
                        progressEstimate = null
                    }
                    persistedMetricsLoaded = true
                }
                val isOpen = targetSourceOffset != null
                fun localTarget(projection: BoundedReplaceResult.Exact?): Int = targetSourceOffset?.let { offset ->
                    projection?.globalSourceToLocalDisplay(offset)
                        ?: (offset - source.chapterStartAbs(chIdx))
                } ?: 0

                fun pickPage(chapterLayout: ChapterPaginator.ChapterLayout, target: Int): Int =
                    when {
                        atEnd -> chapterLayout.pages.lastIndex
                        isOpen -> chapterLayout.pageIndexFor(target)
                        else -> 0
                    }

                val cached = chapterCache[chIdx]?.also { restoreMetrics() } ?: run {
                    val loaded = reusableRead?.result?.await()?.getOrNull()
                        ?: withContext(Dispatchers.IO) { readChapter(chIdx, trace = isOpen) }
                    // 查询与读取并行，但发布首批页面前保留原有的进度校准顺序。
                    restoreMetrics()
                    layoutMutex.withLock {
                        val target = localTarget(loaded.projection)
                        val publishPages: ((ChapterPaginator.ChapterLayout) -> Unit)? =
                            if (atEnd) null else { interim ->
                                scope.launch {
                                    if (requestId == layoutRequestId && isLayingOut) {
                                        if (awaitingTarget) {
                                            applyInterim(chIdx, loaded, interim, pickPage(interim, target))
                                        } else if (layoutIsPartial && interim.pages.size > pageCount) {
                                            layout = interim
                                            replayPendingPageTurns()
                                        }
                                    }
                                }
                            }
                        val chapterLayout = withContext(Dispatchers.Default) {
                            val startedNs = if (isOpen) SystemClock.elapsedRealtimeNanos() else 0L
                            if (isOpen) Trace.beginSection("TxtFirstPageLayout")
                            try {
                                ChapterPaginator.paginateProgressive(
                                    loaded.content.blocks,
                                    cfg,
                                    ruler,
                                    oracle,
                                    targetOffset = if (isOpen) target else -1,
                                    initialPageThreshold = 2,
                                    checkActive = { coroutineContext.ensureActive() },
                                    publishGrowingPrefix = true,
                                    shouldPublishEarly = { count ->
                                        val wanted = prefixDemand.get()
                                        wanted in 1..count && prefixDemand.compareAndSet(wanted, 0)
                                    },
                                    onInitialPages = publishPages,
                                )
                            } finally {
                                if (isOpen) {
                                    Trace.endSection()
                                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000
                                    AppLog.debug("TxtPerfTrace", "TxtFirstPageLayout: $elapsedMs ms")
                                }
                            }
                        }
                        CachedChapter(loaded.content.text, loaded.projection, chapterLayout)
                    }
                }
                // 若已经发布过目标章首批页面，用户可能已经在其中连续翻页。
                val targetPage = if (awaitingTarget) {
                    pickPage(cached.layout, localTarget(cached.projection))
                } else {
                    pageIndex
                }
                chapterCache[chIdx] = cached
                cacheRevision++
                val l = cached.layout
                val charCount = cached.text.length
                if (actualCharCounts.put(chIdx, charCount) != charCount) {
                    progressEstimate = null
                }
                applyChapter(chIdx, cached, targetPage)

                // 写通缓存：pageStarts 落库（失败无所谓，见 PageIndexStore 的纪律）
                val s = store
                if (s != null && effectivePageIndexContentKey.isNotBlank()) {
                    launch(Dispatchers.IO) {
                        s.save(
                            effectivePageIndexContentKey,
                            chIdx,
                            cfg.fingerprint,
                            l.pageStarts,
                            charCount,
                        )
                        s.prune(effectivePageIndexContentKey)
                    }
                }
                replayPendingPageTurns()
            } catch (cancelled: CancellationException) {
                // ── 协程取消时结束 TxtFirstPageReady trace ──
                if (requestId == layoutRequestId && firstPageReadyTraceActive) {
                    endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
                    firstPageReadyTraceActive = false
                }
                throw cancelled
            } catch (t: Throwable) {
                if (requestId != layoutRequestId) return@launch
                // ── 加载失败时结束 TxtFirstPageReady trace ──
                if (firstPageReadyTraceActive) {
                    endAsyncTrace("TxtFirstPageReady", firstPageReadyCookie)
                    firstPageReadyTraceActive = false
                }
                prefetchRead?.result?.cancel()
                prefetchRead = null
                loadError = t.message ?: "章节排版失败"
                awaitingTarget = false
                isLayingOut = false
                pendingPageTurns.clear()
            }
        }
        layoutJob = job
        job.start()
    }

    private fun readChapter(index: Int, trace: Boolean = false): LoadedChapter {
        val startedNs = if (trace) SystemClock.elapsedRealtimeNanos() else 0L
        if (trace) Trace.beginSection("TxtChapterRead")
        try {
            return LoadedChapter(
                content = source.loadChapter(index),
                projection = (source as? ProjectedChapterSource)?.projectionForChapter(index),
            )
        } finally {
            if (trace) {
                Trace.endSection()
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000
                AppLog.debug("TxtPerfTrace", "TxtChapterRead: $elapsedMs ms")
            }
        }
    }

    private fun trimChapterCache(center: Int) {
        chapterCache.keys.removeAll { it !in (center - 2)..(center + 2) }
    }

    /**
     * 当前章显示后再顺序预排相邻章。所有排版仍在同一个 controller job 下串行使用
     * ruler，避免 PaintTextRuler 的 scratch/cache 被并发访问。
     */
    private fun prefetchNeighbors(center: Int) {
        prefetchJob?.cancel()
        prefetchRead?.result?.cancel()
        prefetchRead = null
        prefetchJob = scope.launch {
            // 沿最近一次跨章方向预排，同时保留反方向邻章。
            val direction = prefetchDirection
            val prefetchTargets = listOf(center + direction, center - direction, center + 2 * direction)
            for (index in prefetchTargets) {
                if (index !in 0 until source.chapterCount || chapterCache.containsKey(index)) continue
                try {
                    // 读取与排版各有生命周期，取消预排版不会丢掉前台正在接管的读取。
                    val read = PrefetchRead(index, scope.async(Dispatchers.IO) {
                        try {
                            Result.success(prefetchReadMutex.withLock { readChapter(index) })
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Result.failure(failure)
                        }
                    })
                    prefetchRead = read
                    val loaded = read.result.await().getOrThrow()
                    val chapterLayout = layoutMutex.withLock {
                        withContext(Dispatchers.Default) {
                            ChapterPaginator.paginateBlocks(
                                loaded.content.blocks, cfg, ruler, oracle,
                                checkActive = { coroutineContext.ensureActive() },
                            )
                        }
                    }
                    chapterCache[index] = CachedChapter(loaded.content.text, loaded.projection, chapterLayout)
                    prefetchRead = null
                    val charCount = loaded.content.text.length
                    if (actualCharCounts.put(index, charCount) != charCount) {
                        progressEstimate = null
                    }
                    cacheRevision++
                    val s = store
                    if (s != null && effectivePageIndexContentKey.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            s.save(
                                effectivePageIndexContentKey,
                                index,
                                cfg.fingerprint,
                                chapterLayout.pageStarts,
                                charCount,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    prefetchRead = null
                    // 预取失败不影响当前章；真正翻到目标章时 loadChapter 会正常报告错误。
                }
            }
        }
    }

    // Trace async section APIs require API 29; gate them so minSdk 24 stays valid.
    private fun beginAsyncTrace(label: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(label, cookie)
        }
    }

    private fun endAsyncTrace(label: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(label, cookie)
        }
    }
}
