package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.reader.layout.BreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.TextRuler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) {
    data class ReaderPageFrame(
        val page: ChapterPaginator.Page,
        val chapterText: String,
        val chapterIndex: Int,
    )

    private data class CachedChapter(
        val content: PagedChapterContent,
        val layout: ChapterPaginator.ChapterLayout,
    )

    var chapterIndex by mutableIntStateOf(0)
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var layout by mutableStateOf<ChapterPaginator.ChapterLayout?>(null)
        private set

    /** 排版进行中（切章/首开时短暂为 true；章内翻页恒为 false）。 */
    var isLayingOut by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    val pageCount: Int get() = layout?.pages?.size ?: 0
    val currentPage: ChapterPaginator.Page? get() = layout?.pages?.getOrNull(pageIndex)

    /** 当前章文本（绘制层按 lineParaOffsets 从中切簇）。 */
    var chapterText: String = ""
        private set

    /** 当前页首字符的全书偏移。进度持久化以它为准。 */
    val currentPageStartAbs: Int
        get() {
            val chStart = source.chapterStartAbs(chapterIndex)
            val l = layout ?: return chStart
            return chStart + (l.pageStarts.getOrNull(pageIndex) ?: 0)
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
            val chStart = source.chapterStartAbs(chapterIndex)
            val start = chStart + page.startCharOffset
            val end = chStart + page.endCharOffset
            return start until (if (end > start) end else start + 1)
        }

    /** 当前章起始的全书偏移（选区/TTS 的章内↔全书换算用）。 */
    val currentChapterStartAbs: Int
        get() = source.chapterStartAbs(chapterIndex)

    val canGoPrev: Boolean get() = pageIndex > 0 || chapterIndex > 0
    val canGoNext: Boolean
        get() = pageIndex < pageCount - 1 || chapterIndex < source.chapterCount - 1

    /** 全书进度百分比。末章末页视为 100。EPUB 分母是估算值，够显示用。 */
    val progressPercent: Float
        get() {
            val total = source.totalChars
            if (total <= 0) return 0f
            if (chapterIndex == source.chapterCount - 1 && pageCount > 0 && pageIndex == pageCount - 1) return 100f
            return (currentPageStartAbs * 100f / total).coerceIn(0f, 100f)
        }

    private var layoutJob: Job? = null
    private var prefetchJob: Job? = null
    private val layoutMutex = Mutex()
    private val chapterCache = LinkedHashMap<Int, CachedChapter>(3, 0.75f, true)
    var cacheRevision by mutableIntStateOf(0)
        private set

    /** 相邻页快照；章内直接取，跨章仅在预排版完成后提供。 */
    fun frameAt(delta: Int): ReaderPageFrame? {
        if (delta == 0) {
            val page = currentPage ?: return null
            return ReaderPageFrame(page, chapterText, chapterIndex)
        }
        val currentLayout = layout ?: return null
        val candidate = pageIndex + delta
        if (candidate in currentLayout.pages.indices) {
            return ReaderPageFrame(currentLayout.pages[candidate], chapterText, chapterIndex)
        }
        val targetChapter = chapterIndex + if (delta < 0) -1 else 1
        val cached = chapterCache[targetChapter] ?: return null
        val targetPage = if (delta < 0) cached.layout.pages.lastOrNull() else cached.layout.pages.firstOrNull()
        return targetPage?.let { ReaderPageFrame(it, cached.content.text, targetChapter) }
    }

    /** 打开到全书偏移 [absOffset] 所在的页。幂等，可反复调。 */
    fun open(absOffset: Int) {
        val target = absOffset.coerceIn(0, (source.totalChars - 1).coerceAtLeast(0))
        val chIdx = source.chapterIndexFor(target)
        loadChapter(chIdx) { l ->
            val inChapter = target - source.chapterStartAbs(chIdx)
            l.pageIndexFor(inChapter)
        }
    }

    fun nextPage() {
        val l = layout ?: return
        when {
            pageIndex < l.pages.size - 1 -> pageIndex++
            chapterIndex < source.chapterCount - 1 -> {
                val next = chapterCache[chapterIndex + 1]
                if (next != null) applyChapter(chapterIndex + 1, next, 0)
                else loadChapter(chapterIndex + 1) { 0 }
            }
        }
    }

    fun prevPage() {
        when {
            pageIndex > 0 -> pageIndex--
            chapterIndex > 0 -> {
                val previous = chapterCache[chapterIndex - 1]
                if (previous != null) applyChapter(chapterIndex - 1, previous, previous.layout.pages.lastIndex)
                else loadChapter(chapterIndex - 1) { it.pages.size - 1 }
            }
        }
    }

    private fun applyChapter(chIdx: Int, cached: CachedChapter, targetPage: Int) {
        chapterText = cached.content.text
        chapterIndex = chIdx
        layout = cached.layout
        pageIndex = targetPage.coerceIn(0, cached.layout.pages.lastIndex.coerceAtLeast(0))
        isLayingOut = false
        loadError = null
        prefetchNeighbors(chIdx)
    }

    /**
     * 排版 [chIdx] 章，完成后用 [pickPage] 决定停在哪页。
     * 并发纪律：新请求取消旧请求（快速连续翻章只保留最后一次）。
     */
    private fun loadChapter(chIdx: Int, pickPage: (ChapterPaginator.ChapterLayout) -> Int) {
        if (chIdx !in 0 until source.chapterCount) return
        layoutJob?.cancel()
        prefetchJob?.cancel()
        isLayingOut = true
        loadError = null
        layoutJob = scope.launch {
            try {
                // 章文本加载走 IO（EPUB 解压），排版走 Default（纯计算）
                val cached = chapterCache[chIdx] ?: run {
                    val (content, chapterLayout) = layoutMutex.withLock {
                        val loaded = withContext(Dispatchers.IO) { source.loadChapter(chIdx) }
                        val laidOut = withContext(Dispatchers.Default) {
                            ChapterPaginator.paginateBlocks(loaded.blocks, cfg, ruler, oracle)
                        }
                        loaded to laidOut
                    }
                    CachedChapter(content, chapterLayout).also {
                        chapterCache[chIdx] = it
                        cacheRevision++
                    }
                }
                val l = cached.layout
                applyChapter(chIdx, cached, pickPage(l))

                // 写通缓存：pageStarts 落库（失败无所谓，见 PageIndexStore 的纪律）
                val s = store
                if (s != null && contentKey.isNotBlank()) {
                    launch(Dispatchers.IO) {
                        s.save(contentKey, chIdx, cfg.fingerprint, l.pageStarts, l.charCount)
                        s.prune(contentKey)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loadError = t.message ?: "章节排版失败"
                isLayingOut = false
            }
        }
    }

    /**
     * 当前章显示后再顺序预排相邻章。所有排版仍在同一个 controller job 下串行使用
     * ruler，避免 PaintTextRuler 的 scratch/cache 被并发访问。
     */
    private fun prefetchNeighbors(center: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            for (index in listOf(center - 1, center + 1)) {
                if (index !in 0 until source.chapterCount || chapterCache.containsKey(index)) continue
                try {
                    val (content, chapterLayout) = layoutMutex.withLock {
                        val loaded = withContext(Dispatchers.IO) { source.loadChapter(index) }
                        val laidOut = withContext(Dispatchers.Default) {
                            ChapterPaginator.paginateBlocks(loaded.blocks, cfg, ruler, oracle)
                        }
                        loaded to laidOut
                    }
                    chapterCache[index] = CachedChapter(content, chapterLayout)
                    val keep = setOf(center - 1, center, center + 1)
                    chapterCache.keys.toList().filterNot { it in keep }.forEach(chapterCache::remove)
                    cacheRevision++
                    val s = store
                    if (s != null && contentKey.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            s.save(contentKey, index, cfg.fingerprint, chapterLayout.pageStarts, chapterLayout.charCount)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // 预取失败不影响当前章；真正翻到目标章时 loadChapter 会正常报告错误。
                }
            }
        }
    }
}
