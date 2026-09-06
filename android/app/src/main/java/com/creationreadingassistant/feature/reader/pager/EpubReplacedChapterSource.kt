package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import com.creationreadingassistant.feature.reader.rules.EpubReplaceProjector
import com.creationreadingassistant.feature.reader.rules.ReplaceProfile
import com.creationreadingassistant.feature.reader.rules.ReplaceProjection
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EPUB 结构保真替换投影 source（[EpubChapterSource] 的净化装饰器）。
 *
 * 与 TXT 的 [ReplacedChapterSource]（整章文本投影）本质差异：EPUB 章块序列含
 * [DocBlock.Image] 与标题标记，必须经 [EpubReplaceProjector] 逐 Text 块保真投影，
 * 重切段落会丢图片——本类只负责把投影器产出的 display 块序装配成
 * [PagedChapterContent]，并按 TXT 路径同款契约暴露章级映射：
 *
 * - [projectionForChapter] 返回 [BoundedReplaceResult.Exact]：
 *   `scopeSourceBase = delegate.chapterStartAbs(index)`（估算章基址），
 *   `offsetMap = ChapterBlockOffsetMap`（章内真实字符坐标）——与 EPUB 分页引擎
 *   既有的「估算章基址 + 章内真实偏移」混合空间完全一致，持久化 locator 坐标系不变；
 * - 超大章（章文本超过 [maxSourceLength]）整章保留原文，`projectionForChapter`
 *   返回 null（消费方走 `base + local` 恒等映射），超限回调整书只发一次；
 * - LRU 缓存容量 3 章（当前+前+后）；同章并发加载经条纹锁去重，
 *   昂贵的块解析与投影在锁外语义下仅同 key 请求者互等；
 * - `replacementCoordinateSpace` 仍是 ESTIMATED：全书层偏移从来不是精确 source，
 *   章内偏移才是——这由 ChapterBlockOffsetMap 的章内口径保证。
 */
class EpubReplacedChapterSource(
    private val delegate: EpubChapterSource,
    private val bookId: String,
    private val rules: List<ReplaceRule>,
    private val maxSourceLength: Int = EpubReplaceProjector.DEFAULT_MAX_BLOCK_CHARS,
    private val onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
) : ProjectedChapterSource {

    init {
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(maxSourceLength > 0) { "maxSourceLength 必须大于 0" }
    }

    private data class CachedChapter(
        val content: PagedChapterContent,
        /** 超大章不投影：null = 消费方按 `chapterStartAbs + local` 恒等映射。 */
        val projection: BoundedReplaceResult.Exact?,
    )

    override val replaceProfileKey: String = ReplaceProfile.key(bookId, rules)
    override val chapterCount: Int get() = delegate.chapterCount
    override val totalChars: Int get() = delegate.totalChars
    override val chapterLengthsAreEstimated: Boolean get() = true
    override val replacementCoordinateSpace: ReplacementCoordinateSpace
        get() = ReplacementCoordinateSpace.ESTIMATED

    override fun chapterTitle(index: Int): String = delegate.chapterTitle(index)
    override fun chapterStartAbs(index: Int): Int = delegate.chapterStartAbs(index)

    private val cache = BoundedLruCache<Int, CachedChapter>(maxSize = 3)
    private val keyedProjectionLocks = KeyedStripedLocks()
    private val oversizedReported = AtomicBoolean(false)

    internal fun inspectionCacheSize(): Int = cache.size
    internal fun inspectionCacheKeysForTest(): List<Int> = cache.snapshotKeysForTest()

    override fun loadChapter(index: Int): PagedChapterContent {
        if (index !in 0 until chapterCount) return delegate.loadChapter(index)
        return chapter(index).content
    }

    override fun projectionForChapter(index: Int): BoundedReplaceResult.Exact? {
        if (index !in 0 until chapterCount) return null
        return chapter(index).projection
    }

    private fun chapter(index: Int): CachedChapter {
        cache.get(index)?.let { return it }
        // 同章去重：同 key 条纹内二次确认；块解析 + 逐块投影只执行一次
        return keyedProjectionLocks.withLockFor(index) {
            cache.get(index)?.let { return it }
            val docBlocks = delegate.blocksOf(index)
            val sourceText = EpubPageSource.chapterTextOf(docBlocks)
            com.creationreadingassistant.feature.log.AppLog.debug(
                "EpubReplace",
                "chapter=$index sourceLen=${sourceText.length} max=$maxSourceLength",
            )
            val cached = if (sourceText.length > maxSourceLength) {
                // 超大章显式拒绝：整章保留原文（恒等映射），整书只提示一次
                if (oversizedReported.compareAndSet(false, true)) {
                    com.creationreadingassistant.feature.log.AppLog.debug(
                        "EpubReplace",
                        "当前章节过大，已保留原文，暂不执行替换净化。(chapter=$index len=${sourceText.length})",
                    )
                    onUnsupportedTooLarge(
                        BoundedReplaceResult.UnsupportedTooLarge(sourceText.length, maxSourceLength),
                    )
                }
                CachedChapter(
                    content = PagedChapterContent(sourceText, EpubPageSource.layoutBlocksOf(docBlocks)),
                    projection = null,
                )
            } else {
                val result = EpubReplaceProjector.project(docBlocks, rules, bookId)
                val displayText = EpubPageSource.chapterTextOf(result.displayBlocks)
                CachedChapter(
                    content = PagedChapterContent(displayText, EpubPageSource.layoutBlocksOf(result.displayBlocks)),
                    projection = BoundedReplaceResult.Exact(
                        projection = ReplaceProjection.ofParts(
                            sourceText = sourceText,
                            displayText = displayText,
                            offsetMap = result.chapterOffsetMap(),
                            hitCount = result.hitCount,
                            profileKey = replaceProfileKey,
                        ),
                        scopeSourceBase = delegate.chapterStartAbs(index),
                    ),
                )
            }
            cache.put(index, cached)
            cached
        }
    }
}
