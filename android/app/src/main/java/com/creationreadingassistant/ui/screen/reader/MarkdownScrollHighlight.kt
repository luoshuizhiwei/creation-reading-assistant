package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderCell
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderSpan
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit

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
 * **章节长度未知（[chapterLength] <= 0）时的降级（S07-F4 定案）**：返回空列表，
 * 即这一次搜索命中不高亮。与持久高亮的降级**刻意不同**，两者不是同一个权衡：
 * - 持久高亮是**用户数据**，静默消失等于丢东西，因此退化为「仅按渲染单元求交」的
 *   最佳努力（见 [MarkdownPersistentHighlights.projectPersistentHighlights]）；
 * - 搜索命中是**瞬时反馈**，代价只是一次没高亮；而错高亮会让用户以为命中在别处。
 *   章长度未知时 [chapterBase] 本身也可能未落定，单元的全局坐标不可信，
 *   此时宁可不高亮。
 * 这条差异是显式裁决，不是遗漏；改动任一侧都必须同时改测试与本报告。
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

// ── S07-F3 / F3.1：整块选择的坐标（Markdown） ──────────────────────────
//
// 为什么本切片采用「整块」而不是细粒度选词：
// 这是**本切片的产品与实现边界**，不是 Compose 的平台上限 —— 同一仓库的 TXT 滚动
// 正文已用 `BasicTextField` 选区做到字符级（`ReaderContentHostPlainTextBranch`），
// 证明没有 `SelectionContainer` 也能实现字符级方案。要做到精确选词，需要专门的
// 输入层、选区手柄和 canonical↔source 映射实现（见报告 SEAM），不在本片范围。
// 因此本片只做整块，报告与 UI 都必须写「整块」，不得伪装成可拖动选词。
//
// 命名（F3.1 修正）：本组函数返回的是 **canonical** 空间的区间，与
// `MarkdownRenderUnit.sourceRange`（解码后源文本空间）**不是**同一个空间。
// 旧名 `markdownUnitSourceRange` 会让后来者把两者混写，故统一改为 canonical 命名。
//
// 坐标契约复用既有产物，不新增第二套展平算法、不改解析语义：
// - `MarkdownRenderUnit.canonicalRange` / `sourceRange` 由 `MarkdownRenderModel.flatten`
//   直接沿用解析产物（MarkdownRenderModel.kt:317-318）；
// - `MarkdownRenderCell.canonicalRange` / `sourceRange` 由 `renderCell` 计算
//   （MarkdownRenderModel.kt:219-220），表格单元格必须使用自己的范围；
// - display offset ≠ source offset：渲染文本去掉了 Markdown 标记，局部高亮只能
//   落在 canonical 切片上。
//
// [blockGlobalBase]：解析坐标 → 全书坐标的基准。小文件整本解析 = 0
// （canonicalRange 已是全书全局）；流式逐章解析 = 章节全书起点。

/**
 * 叶单元的 **canonical** 半开区间；不可选择时返回 null。
 *
 * 坐标空间：Markdown 持久化统一用 canonical —— 章 `startOffset`/`charCount`、
 * `MarkdownChapterSource`、搜索、TTS、既有 `markdownUnitGlobalOffset`、
 * `LocatorBuilder.forPlain` 全是 canonical 坐标。**不要**改成
 * [MarkdownRenderUnit.sourceRange]（那是解码后源文本空间，被 `MarkdownOffsetMap`
 * 消费），两者数值不同。
 *
 * 不可选择的情形（非文本块不得生成伪选区）：分隔线（无渲染文本）、
 * 表格整体（只能用单元格）、空范围、非法基准。
 */
internal fun markdownUnitCanonicalRange(unit: MarkdownRenderUnit, blockGlobalBase: Int): IntRange? {
    if (!markdownUnitIsBlockSelectable(unit)) return null
    return markdownShiftCanonicalRange(unit.canonicalRange, blockGlobalBase)
}

/**
 * **生产唯一的整块选择入口**：把「可见文本 + 该块 canonical 全书区间」打包成
 * `onSelect` 的四个参数，使参数槽位含义由类型固定，而不是靠调用方记顺序。
 *
 * 槽位契约（`ReaderContentHostCallbacks.onSelect`）：
 * - `text`：当前整块的可见 canonical 文本；
 * - `globalOffset`：**-1**（该槽是 EPUB 的 `forEpub` 起点；Markdown 不用它）；
 * - `rangeStart`：canonical 全书起点（`computeLocatorJson` 在 `epubBook == null`
 *   时只认这一槽，写错槽会让 locator 恒为 null —— F3 的 P1 缺陷）；
 * - `sourceEnd`：canonical 全书半开终点。
 */
internal fun markdownBlockSelectionArgs(
    text: String,
    unit: MarkdownRenderUnit,
    blockGlobalBase: Int,
): MarkdownBlockSelection? {
    if (!markdownUnitIsBlockSelectable(unit)) return null
    if (text.isEmpty()) return null
    val range = markdownShiftCanonicalRange(unit.canonicalRange, blockGlobalBase) ?: return null
    return MarkdownBlockSelection(
        text = text,
        globalOffset = -1,
        rangeStart = range.first,
        sourceEnd = range.last + 1,
    )
}

/** [markdownBlockSelectionArgs] 的四字段结果：一次映射产物，文字与坐标同源。 */
data class MarkdownBlockSelection(
    val text: String,
    val globalOffset: Int,
    val rangeStart: Int,
    val sourceEnd: Int,
)

/**
 * 该单元是否可作为「整块选择」的载体。
 *
 * 代码块可以整块选择（canonical 文本就是代码本身；语言标签是纯展示，
 * 不进入 offset 空间 —— 见 `MarkdownUnitVisualPolicy.codeLanguageLabel` 注释）。
 *
 * **S07-F5 D-1：含图片的单元一律不可整块选择。**
 *
 * 图片在 render model 里**不是独立 kind**，而是 `MarkdownRenderSpan.Image`，嵌在
 * `Paragraph` / `Heading` / `ListItem` / `TaskItem` 的行内 spans 里。而 parser 把
 * **alt 文本**写进了 `canonicalText`（`MarkdownDocument.kt` 的 `is MdInline.Image -> alt`），
 * 于是 `unitText()` 原样切片就得到 alt —— 真机上表现为「轻点图片块，摘录显示
 * `示意图`，还能存成持久高亮」。仅按 `unit.kind` 判断无法区分纯图片段落与普通段落。
 *
 * 为什么是「整个单元不可选」而不是「排除图片那段区间」：整块选择提交的是一个
 * **连续** canonical 区间（`markdownBlockSelectionArgs` 的 `rangeStart..sourceEnd`），
 * 而图片 alt 位于该区间**内部**。要只选真实正文就得提交两个不连续区间，或靠文本搜索
 * 定位正文边界 —— 两者都会把一个可伪造的猜测写进持久化坐标。因此本切片取安全契约：
 * **含图片的单元整体不可选**，宁可少一个入口，也不产出伪文字 / 伪坐标。
 *
 * 这与「整块选择是本切片的产品边界」一致：不为图片块发明第二套选区模型。
 */
internal fun markdownUnitIsBlockSelectable(unit: MarkdownRenderUnit): Boolean = when (unit.kind) {
    MarkdownRenderKind.HORIZONTAL_RULE -> false
    MarkdownRenderKind.TABLE -> false
    MarkdownRenderKind.HEADING,
    MarkdownRenderKind.PARAGRAPH,
    MarkdownRenderKind.LIST_ITEM,
    MarkdownRenderKind.TASK_ITEM,
    MarkdownRenderKind.CODE_BLOCK -> !markdownUnitContainsImage(unit) &&
        unit.canonicalRange.last >= unit.canonicalRange.first
}

/**
 * 单元内是否存在图片行内节点（含容器嵌套与表格单元格）。
 *
 * 只读 parser 产出的 span 类型，不做文本搜索、不看 alt 内容 —— alt 是可任意伪造的
 * 用户文本，任何基于它的判断都会被绕过（例如 alt 恰好等于正文时）。
 */
internal fun markdownUnitContainsImage(unit: MarkdownRenderUnit): Boolean =
    markdownUnitSpans(unit).any { it is MarkdownRenderSpan.Image }

/** 单元的全部行内 span（表格取所有单元格的 span；无文本载体时为空）。 */
private fun markdownUnitSpans(unit: MarkdownRenderUnit): List<MarkdownRenderSpan> =
    when (val content = unit.content) {
        is MarkdownRenderContent.Heading -> content.spans
        is MarkdownRenderContent.Paragraph -> content.spans
        is MarkdownRenderContent.ListItem -> content.spans
        is MarkdownRenderContent.TaskItem -> content.spans
        is MarkdownRenderContent.Table ->
            (content.header + content.rows.flatten()).flatMap { it.spans }

        is MarkdownRenderContent.CodeBlock,
        MarkdownRenderContent.HorizontalRule,
        -> emptyList()
    }

private fun markdownShiftCanonicalRange(range: IntRange, blockGlobalBase: Int): IntRange? {
    if (blockGlobalBase < 0) return null
    if (range.isEmpty()) return null
    val start = range.first + blockGlobalBase
    val end = range.last + 1 + blockGlobalBase
    if (end <= start) return null
    return start until end
}

// ── S07-F3 任务 C：持久高亮投影 ────────────────────────────────────────

/**
 * 持久高亮的纯投影入口。
 *
 * 规则：
 * 1. 已保存 canonical 区间与当前渲染单元 canonical 区间**求交**（不用文本搜索）；
 * 2. 交集换算成单元内的局部可见区间；
 * 3. 禁止 selectedText 搜索 / substring 首次命中 / 章节内字符串猜测；
 * 4. 重复文本按 canonical 坐标分别落到正确块；
 * 5. 跨相邻单元时两边各渲染自己的交集；
 * 6. stale book / **落在当前章 span 之外**、空区间、非法坐标安全忽略；
 * 7. 不可渲染时只跳过，**不删除、不改写数据库**（调用方仍持有原记录）；
 * 8. 表格按单元格投影，不生成整表的虚假局部范围。
 *
 * **为什么没有 chapterIndex 参数（F3.1 / Qoder P2-1）**：F3 曾让调用方把每条
 * `HighlightEntity` 包装成 `chapterIndex = 当前章号`，再用「当前章号」过滤它 ——
 * 该判据在生产路径恒成立，防串章实际只靠几何不相交，是伪判据。而且记录里**没有**
 * 真实章身份：`LocatorBuilder.forPlain` 恒写 `ci = 0`。因此本函数只接受
 * [chapterSpan]（当前章在全书 canonical 空间中的半开区间），由几何求交决定是否投影。
 *
 * @param chapterSpan 当前章在全书 canonical 空间中的半开区间
 *   （`chapterBase until chapterBase + chapterLength`）。与高亮区间无交集即不投影。
 * @param blockGlobalBase 解析坐标 → 全书坐标基准（整本解析 = 0，流式逐章 = 章起点）。
 *   **必填**：省略会造成流式分章整体静默错位，默认值 0 正是这种错位的来源。
 *
 * @return 单元 id → 该单元内的局部高亮区间列表（局部区间 + 颜色键）。
 */
internal object MarkdownPersistentHighlights {

    /** 一条已保存高亮的 **canonical 坐标** 投影输入（不含文本，避免文本匹配）。 */
    data class SavedHighlight(
        val bookKey: String,
        val start: Int,
        val length: Int,
        val colorKey: String,
    )

    /**
     * 一个高亮落点：局部区间 + 颜色键 + 所属文本载体。
     *
     * [cellCanonicalStart] 为 null 表示落在单元自身文本上；非 null 表示落在
     * 该 canonical 起点的**表格单元格**上（表格整体不得生成整表局部区间）。
     */
    data class HighlightSpan(
        val localRange: IntRange,
        val colorKey: String,
        val cellCanonicalStart: Int? = null,
    )

    /** 单元 id → 该单元内的高亮落点列表。 */
    fun projectPersistentHighlights(
        units: List<MarkdownRenderUnit>,
        canonicalText: String,
        highlights: List<SavedHighlight>,
        currentBookKey: String,
        chapterSpan: IntRange,
        blockGlobalBase: Int,
    ): Map<String, List<HighlightSpan>> {
        if (units.isEmpty() || highlights.isEmpty()) return emptyMap()
        if (blockGlobalBase < 0) return emptyMap()

        val out = HashMap<String, MutableList<HighlightSpan>>(units.size)
        for (highlight in highlights) {
            // 6. stale 书 / 章：安全忽略
            if (highlight.bookKey != currentBookKey) continue
            if (highlight.length <= 0) continue
            if (highlight.start < 0) continue

            val absEnd = (highlight.start.toLong() + highlight.length.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // 真实章判据：高亮区间必须与当前章 canonical span 相交（半开区间求交）。
            //
            // 空 span 表示「章长度未知」（上游 charCount 未算出时 chapterLength = 0），
            // 此时**不**按 span 过滤 —— 否则会静默吞掉全部用户数据。退化为「仅按渲染
            // 单元求交」的有显式记录的最佳努力。
            //
            // 代价必须写清楚：章长度未知意味着防串章只剩单元求交一道，落在**别的章**
            // 但与本章某个渲染单元坐标重叠的高亮可能被投影到本章（流式分章下单元是
            // 章内局部坐标 + chapterBase，若 chapterBase 同样未落定，重叠概率升高）。
            // 权衡是「可能多画一处旧高亮」对「绝不静默丢掉用户的高亮」——后者是数据
            // 丢失，前者只是一次渲染偏差，且章长度落定后自然收敛。
            if (!chapterSpan.isEmpty() &&
                maxOf(highlight.start, chapterSpan.first) >= minOf(absEnd, chapterSpan.last + 1)
            ) continue

            for (unit in units) {
                when (unit.kind) {
                    // 分隔线无渲染文本；表格整体由下面的单元格分支处理
                    MarkdownRenderKind.HORIZONTAL_RULE -> continue
                    MarkdownRenderKind.TABLE -> {
                        val table = unit.content as? MarkdownRenderContent.Table ?: continue
                        (table.header + table.rows.flatten()).forEach { cell ->
                            val local = intersectLocal(
                                cell.canonicalRange, highlight.start, absEnd, canonicalText,
                                blockGlobalBase,
                            ) ?: return@forEach
                            out.getOrPut(unit.id) { ArrayList() }.add(
                                HighlightSpan(
                                    localRange = local,
                                    colorKey = highlight.colorKey,
                                    cellCanonicalStart = cell.canonicalRange.first,
                                ),
                            )
                        }
                    }

                    else -> {
                        if (!markdownUnitIsBlockSelectable(unit)) continue
                        val local = intersectLocal(
                            unit.canonicalRange, highlight.start, absEnd, canonicalText,
                            blockGlobalBase,
                        ) ?: continue
                        out.getOrPut(unit.id) { ArrayList() }.add(
                            HighlightSpan(localRange = local, colorKey = highlight.colorKey),
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * 全书绝对区间与单元 canonical 区间求交 → 单元内局部区间。
     * 无交集 / 空区间返回 null（安全忽略）。
     */
    private fun intersectLocal(
        unitCanonicalRange: IntRange,
        absStart: Int,
        absEnd: Int,
        canonicalText: String,
        blockGlobalBase: Int,
    ): IntRange? {
        if (unitCanonicalRange.isEmpty()) return null
        // 流式逐章解析时单元 canonicalRange 是章内局部坐标，而已保存高亮是全书坐标，
        // 必须先加基准再求交（与 markdownScrollSearchHits 的 addUnitHit 同一换算）。
        val unitStart = blockGlobalBase + unitCanonicalRange.first
        val unitEnd = blockGlobalBase + unitCanonicalRange.last + 1
        val localStart = maxOf(absStart, unitStart) - unitStart
        val localEnd = minOf(absEnd, unitEnd) - unitStart
        if (localEnd <= localStart) return null
        // display 文本长度就是 canonical 切片长度：钳到实际可见长度，避免越界
        val textLen = (canonicalText.length - (unitStart - blockGlobalBase))
            .coerceIn(0, unitEnd - unitStart)
        val s = localStart.coerceIn(0, textLen)
        val e = localEnd.coerceIn(0, textLen)
        return if (e > s) s until e else null
    }
}
