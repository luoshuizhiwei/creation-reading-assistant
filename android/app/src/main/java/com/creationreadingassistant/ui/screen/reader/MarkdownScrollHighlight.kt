package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel

/**
 * Markdown 滚动渲染中的一个「文本单元」的搜索命中局部高亮区间。
 *
 * [canonicalStart] 是文本单元（叶块或表格单元格）在解析产物坐标空间中的规范起点，
 * 渲染层用它定位单元（叶块 = [MarkdownRenderUnit.canonicalRange.first]，表格单元格 =
 * [MarkdownRenderCell.canonicalRange.first]）；[localRange] 是单元渲染文本内的局部区间
 * （含首不含尾）。坐标空间由解析产物决定（流式章节=章内局部；小文件整本解析=全书全局），
 * 渲染层查找键与 seam 输出保持同一空间，因此两种模式都精确。
 *
 * 本文件不再维护第二套展平逻辑：单元列表与 range 均来自唯一的
 * [MarkdownRenderModel.flatten]（doc 包统一语义模型），滚动与分页共享同一份
 * 阅读顺序 / 结构 identity / canonical ranges。
 */
internal data class MarkdownScrollHit(
    val canonicalStart: Int,
    val localRange: IntRange,
)

/**
 * Converts a rendered Markdown unit's canonical position to the owning document position.
 * Invalid coordinates must stay invalid: persisting a guessed zero would corrupt restore anchors.
 */
internal fun markdownUnitGlobalOffset(blockGlobalOffset: Int, canonicalStart: Int): Int =
    if (blockGlobalOffset < 0 || canonicalStart < 0) -1 else blockGlobalOffset + canonicalStart

/**
 * 找到包含命中（或紧邻其前）的 Markdown 渲染单元索引（P1-A 精确滚动定位）。
 *
 * 与 [markdownScrollSearchHits] 使用同一 parser canonical mapping：
 * - [inChapter] 是章内规范文本偏移；[chapterBase] 是章节全书起点；
 * - [blocksGlobal] = 小文件整本解析（canonicalRange 已是全书全局坐标）时为 true，
 *   否则块为章内局部坐标，需加 [chapterBase] 换算。
 *
 * 语义与 [blockIndexForChapterOffset] 一致：返回最后一个「起点 ≤ 目标偏移」的渲染单元；
 * 目标落在首个单元之前时返回 null（安全 no-op）。
 *
 * 特殊回退：parser 曾会把表格单元格文本先以「幻影副本」写入 canonical 文本（位置先于
 * 表格自身 canonicalRange），这类偏移不属于任何渲染单元。此时回退到第一个
 * 「起点 ≥ 目标偏移」的单元（即该幻影文本的可见副本所在单元），保证单字符命中
 * 表格文本时仍能滚到表格；负偏移（非法）保持 null。
 */
internal fun markdownRenderUnitIndexForChapterOffset(
    chapter: MarkdownParser.MarkdownChapter,
    inChapter: Int,
    chapterBase: Int,
    blocksGlobal: Boolean,
): Int? {
    val absolute = chapterBase + inChapter
    val blockGlobalBase = if (blocksGlobal) 0 else chapterBase
    var match: Int? = null
    var firstAfter: Int? = null
    for (unit in MarkdownRenderModel.flatten(chapter)) {
        val unitStart = blockGlobalBase + unit.canonicalRange.first
        if (unitStart <= absolute) {
            match = unit.index
        } else if (firstAfter == null) {
            firstAfter = unit.index
        }
    }
    return match ?: if (absolute >= 0) firstAfter else null
}

/**
 * Markdown 滚动模式搜索命中的公共换算 seam：
 * 输入 [SearchHitTarget] 的全书/章节绝对区间 + 当前章节 Markdown 解析产物
 * （块级 canonicalRange 即「原文 → 可见文本」的 offset mapping），
 * 输出当前滚动渲染文本单元上的精确局部高亮区间。
 *
 * 守卫（满足即不高亮，安全降级为空）：
 * - target 属于其他书 / 其他章（stale 目标）；
 * - 绝对区间为空或与当前章节 span（[chapterBase]..[chapterBase]+[chapterLength]）无交集；
 * - 命中落在没有渲染文本的单元（分隔线）或格式化文本中的非可见副本（表格重复区）。
 *
 * 坐标空间约定：
 * - [chapterBase]：当前章节在全书规范文本中的起始偏移；
 * - [chapterLength]：当前章节规范长度（[DocChapter.charCount]）；
 * - [blockGlobalBase]：块 canonicalRange 换算为全书坐标的基准——流式章节 =
 *   [chapterBase]（块为章内局部），小文件整本解析 = 0（块已是全书全局）。
 *   两种模式下输出的 [MarkdownScrollHit.canonicalStart] 都保持解析产物自身坐标空间。
 *
 * 单元集合与 range 均取自 [MarkdownRenderModel.flatten]，不使用 substring 猜位置：
 * 所有区间换算只依赖 parser 产出的 canonicalRange。
 */
internal fun markdownScrollSearchHits(
    chapter: MarkdownParser.MarkdownChapter,
    chapterBase: Int,
    chapterLength: Int,
    blockGlobalBase: Int,
    target: SearchHitTarget,
    currentBookKey: String,
    currentChapterIndex: Int,
): List<MarkdownScrollHit> {
    if (target.bookKey != currentBookKey) return emptyList()
    if (target.chapterIndex != currentChapterIndex) return emptyList()
    val abs = target.absoluteRange
    if (abs.isEmpty()) return emptyList()
    if (chapterLength <= 0) return emptyList()

    // 命中与当前章节 span 求交（全书规范坐标）
    val hitStart = maxOf(abs.first, chapterBase)
    val hitEnd = minOf(abs.last + 1, chapterBase + chapterLength)
    if (hitEnd <= hitStart) return emptyList()

    val hits = mutableListOf<MarkdownScrollHit>()
    for (unit in MarkdownRenderModel.flatten(chapter)) {
        when (unit.kind) {
            MarkdownRenderKind.HORIZONTAL_RULE -> Unit // 无渲染文本，命中安全跳过

            MarkdownRenderKind.TABLE -> {
                // 表格作为单个渲染单元保留：命中逐单元格换算，与滚动端表格渲染一一对应。
                val table = unit.content as MarkdownRenderContent.Table
                (table.header + table.rows.flatten()).forEach { cell ->
                    addUnitHit(cell.canonicalRange, hitStart, hitEnd, blockGlobalBase, hits)
                }
            }

            else -> addUnitHit(unit.canonicalRange, hitStart, hitEnd, blockGlobalBase, hits)
        }
    }
    return hits
}

private fun addUnitHit(
    unitCanonicalRange: IntRange,
    hitStart: Int,
    hitEnd: Int,
    blockGlobalBase: Int,
    out: MutableList<MarkdownScrollHit>,
) {
    if (unitCanonicalRange.isEmpty()) return
    val unitStart = blockGlobalBase + unitCanonicalRange.first
    val unitEnd = blockGlobalBase + unitCanonicalRange.last + 1
    val localStart = maxOf(hitStart, unitStart) - unitStart
    val localEnd = minOf(hitEnd, unitEnd) - unitStart
    if (localEnd > localStart) {
        out.add(
            MarkdownScrollHit(
                canonicalStart = unitCanonicalRange.first,
                localRange = localStart until localEnd,
            ),
        )
    }
}
