package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceProjector
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import com.creationreadingassistant.feature.reader.rules.ReplaceProfile
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 按 key 哈希的条纹锁：同 key 串行、异 key 并行，锁数量固定无泄漏。
 * 用于「同一章并发加载只执行一次投影」的去重语义（P3.1 并发收紧）：
 * 昂贵 IO/投影在各自 key 的条纹内执行，绝不持有 [BoundedLruCache] 的 map 锁。
 */
internal class KeyedStripedLocks(stripes: Int = 16) {
    private val locks = Array(stripes) { ReentrantLock() }

    inline fun <T> withLockFor(key: Int, block: () -> T): T {
        val lock = locks[Math.floorMod(key, locks.size)]
        return lock.withLock { block() }
    }
}

/**
 * 可查询章节投影的 source seam。持久化坐标始终是 source，display 只用于渲染。
 */
interface ProjectedChapterSource : PagedChapterSource {
    val replaceProfileKey: String

    fun projectionForChapter(index: Int): BoundedReplaceResult.Exact?
}

enum class PagedReplacementAvailability {
    NO_EFFECTIVE_RULES,
    APPLIED,
    ESTIMATED_COORDINATES,
    INCOMPLETE_SCOPE,
    OVERSIZED_CURRENT_CHAPTER,
    SOURCE_UNAVAILABLE,
}

data class PreparedPagedReplacement(
    val source: PagedChapterSource,
    val availability: PagedReplacementAvailability,
)

/**
 * 生产接线的单一裁决点：不支持的 source 保持原样并返回明确原因，禁止近似投影。
 */
fun preparePagedReplacement(
    delegate: PagedChapterSource,
    bookId: String,
    rules: List<ReplaceRule>,
    maxSourceLength: Int = BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
    onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
): PreparedPagedReplacement {
    if (rules.none(ReplaceRule::enabled)) {
        return PreparedPagedReplacement(delegate, PagedReplacementAvailability.NO_EFFECTIVE_RULES)
    }
    if (delegate.chapterLengthsAreEstimated) {
        return PreparedPagedReplacement(delegate, PagedReplacementAvailability.ESTIMATED_COORDINATES)
    }
    // 旧路径：小文件 1:1 模式直接走 complete flag
    if (delegate.replaceProjectionScopeIsComplete) {
        return PreparedPagedReplacement(
            source = ReplacedChapterSource(
                delegate = delegate,
                bookId = bookId,
                rules = rules,
                maxSourceLength = maxSourceLength,
                onUnsupportedTooLarge = onUnsupportedTooLarge,
            ),
            availability = PagedReplacementAvailability.APPLIED,
        )
    }
    // 新路径：流式 segment 模式，必须提供 scope provider
    val provider = (delegate as? TxtChapterSource)?.asReplaceProjectionScopeProvider()
        ?: delegate as? ReplaceProjectionScopeProvider
    if (provider == null) {
        return PreparedPagedReplacement(delegate, PagedReplacementAvailability.INCOMPLETE_SCOPE)
    }
    return PreparedPagedReplacement(
        source = ReplacedSegmentedChapterSource(
            delegate = delegate,
            scopeProvider = provider,
            bookId = bookId,
            rules = rules,
            maxSourceLength = maxSourceLength,
            onUnsupportedTooLarge = onUnsupportedTooLarge,
        ),
        availability = PagedReplacementAvailability.APPLIED,
    )
}

/**
 * 有界容量 LRU cache：最多 [maxSize] 项，超员淘汰最久未访问项。
 * 线程安全：所有读写都经 ReentrantLock 保护；调用方务必把昂贵 IO/投影放在锁外，
 * 仅在对缓存 map 读写时进入锁（避免长时间持锁阻塞其他线程）。
 */
internal class BoundedLruCache<K : Any, V : Any>(
    private val maxSize: Int,
) {
    init { require(maxSize > 0) { "maxSize 必须 > 0，实际=$maxSize" } }

    private val lock = ReentrantLock()
    private val map = LinkedHashMap<K, V>(maxSize, 0.75f, true) // accessOrder=true => LRU

    val size: Int get() = lock.withLock { map.size }

    fun get(key: K): V? = lock.withLock { map[key] }

    fun put(key: K, value: V): V? {
        lock.withLock {
            val previous = map.put(key, value)
            while (map.size > maxSize) {
                // 最早插入且 accessOrder 最后访问的条目在首
                val eldest = map.entries.first()
                map.remove(eldest.key)
            }
            return previous
        }
    }

    fun contains(key: K): Boolean = lock.withLock { map.containsKey(key) }

    fun clear() = lock.withLock { map.clear() }

    /** 仅限测试：返回 snapshot keys（按访问序，最新在尾）。 */
    internal fun snapshotKeysForTest(): List<K> =
        lock.withLock { map.keys.toList() }
}

/**
 * 旧的 1:1 模式装饰器：delegate 的每一章就是完整可投影作用域
 * （小文件 TxtChapterSource 或未来其他 format）。
 */
class ReplacedChapterSource(
    private val delegate: PagedChapterSource,
    private val bookId: String,
    private val rules: List<ReplaceRule>,
    private val maxSourceLength: Int = BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
    private val onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
) : ProjectedChapterSource {

    init {
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(!delegate.chapterLengthsAreEstimated) { "替换净化暂不支持估算章节坐标" }
        require(delegate.replaceProjectionScopeIsComplete) { "替换净化要求完整章节作用域" }
        require(maxSourceLength > 0) { "maxSourceLength 必须大于 0" }
    }

    private data class CachedChapter(
        val content: PagedChapterContent,
        val result: BoundedReplaceResult,
    )

    /** 最大 3 个章级投影（当前+前+后）；超员 LRU 淘汰。 */
    private val cache = BoundedLruCache<Int, CachedChapter>(maxSize = 3)

    internal fun inspectionCacheSize(): Int = cache.size
    internal fun inspectionCacheKeysForTest(): List<Int> = cache.snapshotKeysForTest()
    private val unsupportedReported = AtomicBoolean(false)

    override val replaceProfileKey: String = ReplaceProfile.key(bookId, rules)
    override val chapterCount: Int get() = delegate.chapterCount
    override val totalChars: Int get() = delegate.totalChars
    override val chapterLengthsAreEstimated: Boolean get() = delegate.chapterLengthsAreEstimated

    override fun chapterTitle(index: Int): String = delegate.chapterTitle(index)
    override fun chapterStartAbs(index: Int): Int = delegate.chapterStartAbs(index)

    override fun loadChapter(index: Int): PagedChapterContent {
        if (index !in 0 until chapterCount) return delegate.loadChapter(index)
        return chapter(index).content
    }

    override fun projectionForChapter(index: Int): BoundedReplaceResult.Exact? {
        if (index !in 0 until chapterCount) return null
        return chapter(index).result as? BoundedReplaceResult.Exact
    }

    private val keyedProjectionLocks = KeyedStripedLocks()

    private fun chapter(index: Int): CachedChapter {
        cache.get(index)?.let { return it }
        // 同章去重：同 key 条纹内二次确认 + 投影，保证并发下每章只投影一次；
        // 异 key 条纹互不阻塞（不持有 cache 的 map 锁）。
        return keyedProjectionLocks.withLockFor(index) {
            cache.get(index)?.let { return it }
            // 昂贵 IO + 投影（仅同 key 请求者互等）
            val source = delegate.loadChapter(index)
            val result = BoundedReplaceProjector.project(
                scopeSource = source.text,
                rules = rules,
                bookId = bookId,
                scopeSourceBase = delegate.chapterStartAbs(index),
                maxSourceLength = maxSourceLength,
            )
            val content = when (result) {
                is BoundedReplaceResult.Exact -> {
                    val display = result.projection.displayText
                    PagedChapterContent(
                        text = display,
                        blocks = TxtPageSource.paragraphsOf(display, delegate.chapterTitle(index))
                            .map(LayoutBlock::Text),
                    )
                }
                is BoundedReplaceResult.InvalidLimit -> source
                is BoundedReplaceResult.UnsupportedTooLarge -> {
                    if (unsupportedReported.compareAndSet(false, true)) {
                        onUnsupportedTooLarge(result)
                    }
                    source
                }
            }
            val cached = CachedChapter(content, result)
            cache.put(index, cached)
            cached
        }
    }
}

/**
 * 流式多 segment 装饰器：分页单元是 ReadingUnit（有界），但投影/正则匹配以完整
 * 逻辑章为作用域。核心不变式：
 *  - 相邻 segment 若属于同一逻辑章，共享同一个章级投影。
 *  - 逻辑章超过 [maxSourceLength] 时，不读整章、不分配巨串，所有 segment 按原文分页。
 *  - LRU 缓存容量 = 3 个「完整逻辑章」投影。
 *  - 锁仅保护 cache map 读写；文件 IO、整章分配、正则投影都在锁外执行。
 */
class ReplacedSegmentedChapterSource(
    private val delegate: PagedChapterSource,
    private val scopeProvider: ReplaceProjectionScopeProvider,
    private val bookId: String,
    private val rules: List<ReplaceRule>,
    private val maxSourceLength: Int = BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
    private val onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
) : ProjectedChapterSource {

    init {
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(!delegate.chapterLengthsAreEstimated) { "替换净化暂不支持估算章节坐标" }
        require(maxSourceLength > 0) { "maxSourceLength 必须大于 0" }
    }

    /**
     * 每个逻辑章缓存一份完整投影结果（含 display + source↔display 映射）。
     * capacity=3：当前章 + 前一章 + 后一章。
     */
    private data class CachedChapterProjection(
        /** 结果本身；对于超限章，投影为 null，所有 segment 走 delegate 原文分页。 */
        val result: BoundedReplaceResult,
        /** 逻辑章全局 source 起点，方便 segment 切片时相对偏移计算。 */
        val scopeSourceBase: Int,
    )

    /** 每逻辑章一个 slot 的 LRU。 */
    private val chapterProjectionCache =
        BoundedLruCache<Int, CachedChapterProjection>(maxSize = 3)

    internal fun inspectionProjectionCacheSize(): Int = chapterProjectionCache.size
    internal fun inspectionProjectionCacheKeysForTest(): List<Int> = chapterProjectionCache.snapshotKeysForTest()

    private val oversizedReportedByChapter = hashSetOf<Int>()
    private val oversizedAnyReported = AtomicBoolean(false)
    private val reportLock = Any()

    override val replaceProfileKey: String = ReplaceProfile.key(bookId, rules)
    override val chapterCount: Int get() = delegate.chapterCount
    override val totalChars: Int get() = delegate.totalChars
    override val chapterLengthsAreEstimated: Boolean get() = delegate.chapterLengthsAreEstimated

    override fun chapterTitle(index: Int): String = delegate.chapterTitle(index)
    override fun chapterStartAbs(index: Int): Int = delegate.chapterStartAbs(index)

    override fun loadChapter(segmentIndex: Int): PagedChapterContent {
        if (segmentIndex !in 0 until chapterCount) return delegate.loadChapter(segmentIndex)
        val scope = scopeProvider.scopeForSegment(segmentIndex)
        when (scope) {
            is ReplaceProjectionScope.Exact -> {
                val cached = ensureChapterProjection(scope)
                // 基于整章投影，切出当前 segment 在 source 内的局部区间，
                // 映射到 display，取 substring → 生成 blocks。
                val segStartAbs = delegate.chapterStartAbs(segmentIndex)
                val segContent = delegate.loadChapter(segmentIndex) // 有界原文，用于 fallback
                val sourceLen = segContent.text.length
                val scopeBase = cached.scopeSourceBase
                val localSourceStart = (segStartAbs - scopeBase).coerceAtLeast(0)
                val nextStartAbs = if (segmentIndex + 1 < chapterCount)
                    delegate.chapterStartAbs(segmentIndex + 1) else totalChars
                val sourceChars = (nextStartAbs - segStartAbs).coerceAtLeast(0)
                val result = cached.result
                if (result !is BoundedReplaceResult.Exact) {
                    // 理论不会发生（Exact scope 对应 Exact 投影），兜底原文
                    return segContent
                }
                return try {
                    val localDispStart = result.globalSourceToLocalDisplay(segStartAbs)
                    val localDispEnd = if (sourceChars == 0) localDispStart
                    else result.globalSourceToLocalDisplay(segStartAbs + sourceLen)
                    val projectionDisplay = result.projection.displayText
                    val displayText = projectionDisplay.substring(
                        localDispStart.coerceAtMost(projectionDisplay.length),
                        localDispEnd.coerceAtMost(projectionDisplay.length),
                    )
                    PagedChapterContent(
                        text = displayText,
                        blocks = TxtPageSource.paragraphsOf(
                            displayText,
                            delegate.chapterTitle(segmentIndex),
                        ).map(LayoutBlock::Text),
                    )
                } catch (_: Throwable) {
                    // 任何映射异常：退回原文段，避免整段空白
                    segContent
                }
            }
            is ReplaceProjectionScope.UnsupportedTooLarge -> {
                // 元数据已判超限：整章不投影，segment 原文分页，超限提示每逻辑章最多一次
                reportOversizedOnce(scope)
                return delegate.loadChapter(segmentIndex)
            }
            is ReplaceProjectionScope.Incomplete -> return delegate.loadChapter(segmentIndex)
        }
    }

    override fun projectionForChapter(segmentIndex: Int): BoundedReplaceResult.Exact? {
        if (segmentIndex !in 0 until chapterCount) return null
        val scope = scopeProvider.scopeForSegment(segmentIndex)
        if (scope is ReplaceProjectionScope.Exact) {
            val cached = ensureChapterProjection(scope)
            return cached.result as? BoundedReplaceResult.Exact
        }
        return null
    }

    /**
     * 取或按需计算「逻辑章级投影」。保证：
     *  - 同逻辑章多个 segment 并发访问时只有一个线程实际投影，
     *    其它线程通过 cache.get 返回已算好结果，避免重复工作。
     *  - 昂贵 IO（readChapterRawText）和正则投影在锁外执行。
     */
    private val keyedProjectionLocks = KeyedStripedLocks()

    private fun ensureChapterProjection(
        scope: ReplaceProjectionScope.Exact,
    ): CachedChapterProjection {
        val chapterIdx = scope.logicalChapterIndex
        chapterProjectionCache.get(chapterIdx)?.let { return it }
        // 同逻辑章去重：同 key 条纹内只有一个线程执行整章读取 + 正则投影，
        // 其余请求者在二次确认处命中缓存；异逻辑章互不阻塞。
        return keyedProjectionLocks.withLockFor(chapterIdx) {
            chapterProjectionCache.get(chapterIdx)?.let { return it }
            ensureChapterProjectionLocked(scope, chapterIdx)
        }
    }

    private fun ensureChapterProjectionLocked(
        scope: ReplaceProjectionScope.Exact,
        chapterIdx: Int,
    ): CachedChapterProjection {
        // 锁外执行：整章读取 + 正则投影（可能耗时且分配完整章字符串，仅 scope<=256K）
        val fullChapterText = scope.loadFullChapterText()
        val scopeBase = (scope.firstSegmentIndex.takeIf { it in 0 until chapterCount }
            ?.let { delegate.chapterStartAbs(it) })
            ?: delegate.chapterStartAbs((0 until chapterCount).firstOrNull {
                delegate.logicalChapterIndex(it) == chapterIdx
            } ?: 0)
        val result = BoundedReplaceProjector.project(
            scopeSource = fullChapterText,
            rules = rules,
            bookId = bookId,
            scopeSourceBase = scopeBase,
            maxSourceLength = minOf(maxSourceLength, scope.charCount + 1).coerceAtLeast(1),
        )
        val cached = CachedChapterProjection(result = result, scopeSourceBase = scopeBase)
        chapterProjectionCache.put(chapterIdx, cached)
        return cached
    }

    private fun reportOversizedOnce(scope: ReplaceProjectionScope.UnsupportedTooLarge) {
        val alreadyChapter = synchronized(reportLock) {
            !oversizedReportedByChapter.add(scope.logicalChapterIndex)
        }
        if (!alreadyChapter && oversizedAnyReported.compareAndSet(false, true)) {
            // 整书只真正回调一次（用户级提示），但按逻辑章去重（测试依赖）。
            onUnsupportedTooLarge(
                BoundedReplaceResult.UnsupportedTooLarge(
                    scope.actualSourceLength,
                    scope.maxSourceLength,
                )
            )
        }
    }
}




