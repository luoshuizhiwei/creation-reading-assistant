package com.creationreadingassistant.feature.search

/**
 * 命中在一段文本中的区间（字符单位）。
 *
 * 坐标是**相对所在文本**的：逐章命中相对该章正文，预览命中相对正文预览，
 * 都不是全书偏移（详见 [SearchHit] 的坐标口径说明）。
 */
data class SearchMatchSpan(
    val start: Int,
    val length: Int,
) {
    val endExclusive: Int get() = start + length
}

/**
 * 一次全局搜索命中（R2-S1.2）。
 *
 * 纯 Kotlin，无 Android / Room 依赖 —— 这样命中语义可以脱离设备做 JVM 断言。
 *
 * ### 坐标口径（**与阅读器批注/书签完全一致**，请勿另发明一套换算）
 *
 * 命中携带的是 `(阅读器章节序号, 章内字符偏移)`，与 `SourceNavigationContract`
 * 消费的 `ReaderLocator(chapterIndex, charOffset)` 是同一组坐标。
 * 因此搜索结果可以直接交给阅读器导航，定位精度与现有笔记/书签**完全相同**。
 *
 * 索引侧把 `chapter_index = 0` 留给了「元数据 + 正文预览」，逐章从 1 开始；
 * 而阅读器的章节是 0 基的。两者换算统一走 [readerChapterIndex]，
 * **调用方不要就地 `-1`**，否则以后改编号会静默错位。
 */
data class SearchHit(
    val bookId: String,
    /** 加权分：命中的查询词越多、词频越高，分越高。 */
    val score: Int,
    /** 命中所在的文本基准（原文 / 替换显示文）。 */
    val textBasis: SearchTextBasis,
    /** **索引口径**的章节号：0 = 元数据或正文预览，≥1 = 逐章正文。 */
    val indexChapterIndex: Int,
    /** 命中在「该章正文」或「正文预览」中的起始字符偏移；纯元数据命中为 null。 */
    val charOffset: Int?,
    /** 命中的字符长度；无法解析（元数据命中）时为 null。 */
    val matchLength: Int?,
    /** 该书在该基准上的索引覆盖状态 —— UI 用它说明「搜索是否覆盖全文」。 */
    val coverage: SearchCoverageState?,
    /** 覆盖率原因码，见 [SearchCoverageReason]。 */
    val coverageReason: String?,
) {

    /**
     * 阅读器章节序号（0 基）。
     *
     * 逐章命中 = 索引章节号 - 1；预览/元数据命中（索引章节号 0）没有对应的读者章节，返回 null。
     */
    val readerChapterIndex: Int?
        get() = if (indexChapterIndex >= 1) indexChapterIndex - 1 else null

    /** 元数据命中（书名/作者/描述）：**没有正文位置可跳**，只能打开书。 */
    val isMetadataHit: Boolean
        get() = indexChapterIndex == 0 && charOffset == null

    /** 正文预览命中：索引章节号 0 但有正文偏移。 */
    val isPreviewHit: Boolean
        get() = indexChapterIndex == 0 && charOffset != null

    /** 能否精确跳到正文位置（有章节号 + 章内偏移）。 */
    val hasSourcePosition: Boolean
        get() = readerChapterIndex != null && charOffset != null
}

/** 命中附近抽出的上下文片段。 */
data class SearchHitContext(
    val text: String,
    /** 命中在 [text] 中的起始下标（用于高亮）。 */
    val matchStart: Int,
    /** 命中在 [text] 中的结束下标（不含）。 */
    val matchEndExclusive: Int,
    /** true = 上下文被文本边界截断，命中不一定落在窗口正中。 */
    val clipped: Boolean,
)

/**
 * 上下文抽取（纯函数）。
 *
 * 与磁盘读取分离：仓储负责把「命中所在的那段文本」取出来，
 * 这里只负责切窗口，便于对边界条件（命中在开头/末尾/超长命中）做断言。
 */
object SearchContext {

    /**
     * 在 [body] 中围绕 [offset] 抽取上下文。
     *
     * @return null 表示偏移越界（调用方应放弃显示片段，而不是显示错位的文本）。
     */
    fun extract(
        body: String,
        offset: Int,
        matchLength: Int,
        radius: Int = DEFAULT_RADIUS,
    ): SearchHitContext? {
        if (offset < 0 || offset > body.length) return null
        val safeRadius = radius.coerceAtLeast(0)
        val length = matchLength.coerceAtLeast(0).coerceAtMost(body.length - offset)
        val start = (offset - safeRadius).coerceAtLeast(0)
        val end = (offset + length + safeRadius).coerceAtMost(body.length)
        val relativeStart = offset - start
        return SearchHitContext(
            text = body.substring(start, end),
            matchStart = relativeStart,
            matchEndExclusive = relativeStart + length,
            clipped = start > 0 || end < body.length,
        )
    }

    const val DEFAULT_RADIUS = 40
}
