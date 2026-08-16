package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit

/**
 * Markdown 滚动渲染的纯视觉/身份策略（无 Compose、无 I/O，纯 JVM 可测）。
 *
 * 滚动 LazyColumn 与 EPUB 遗留 Column 两条路径共用：
 * 1. **稳定 item key**：LazyColumn 项与 Column 子项必须用 [MarkdownRenderUnit.id]
 *    （结构路径）做 Compose key，不能用下标 —— 下标会随阅读顺序/列表重组漂移，
 *    导致滚动状态、搜索高亮与选中态挂到错误单元。
 * 2. **引用轨道（quote rail）**：blockquote 嵌套层数 → 竖向轨道几何。
 *    轨道颜色由调用方用 paperFg 叠加 [QUOTE_RAIL_ALPHA] 生成（随纸色、不新增主题）；
 *    轨道画在单元内容左缘之外，最内层轨道右缘严格小于文本首层缩进
 *    （[TEXT_INDENT_PER_DEPTH_DP] × 总容器深度），因此永不遮字。
 * 3. **代码语言标签**：代码面板顶部低对比小标签文案；标签只作展示，
 *    不得进入 canonical 文本 / 搜索 / TTS / 选区偏移。
 */
internal object MarkdownUnitVisualPolicy {

    /** 单元稳定 key：结构路径 id。 */
    fun unitKey(unit: MarkdownRenderUnit): String = unit.id

    /** 全部单元的 key 列表，顺序与 flatten 阅读顺序一致。 */
    fun unitKeys(units: List<MarkdownRenderUnit>): List<String> = units.map { it.id }

    /** 引用轨道：单层宽度（dp）。 */
    const val QUOTE_RAIL_WIDTH_DP: Float = 3f

    /** 引用轨道：相邻两层间距（dp）。 */
    const val QUOTE_RAIL_GAP_DP: Float = 3f

    /** 引用轨道：叠加在 paperFg 上的不透明度（低对比）。 */
    const val QUOTE_RAIL_ALPHA: Float = 0.18f

    /** 渲染层每层容器缩进（dp）——与现有 depth 缩进一致，轨道必须留在它左侧。 */
    const val TEXT_INDENT_PER_DEPTH_DP: Float = 16f

    /**
     * 引用轨道左边缘相对单元内容左缘的偏移（dp），每层一条。
     * depth ≤ 0 无轨道；depth n 共 n 条，最内层贴文本方向。
     */
    fun quoteRailOffsetsDp(blockquoteDepth: Int): List<Float> {
        if (blockquoteDepth <= 0) return emptyList()
        return List(blockquoteDepth) { level ->
            level * (QUOTE_RAIL_WIDTH_DP + QUOTE_RAIL_GAP_DP)
        }
    }

    /** 最内层轨道右缘（dp）；无轨道时为 0。 */
    fun quoteRailRightEdgeDp(blockquoteDepth: Int): Float {
        val offsets = quoteRailOffsetsDp(blockquoteDepth)
        if (offsets.isEmpty()) return 0f
        return offsets.last() + QUOTE_RAIL_WIDTH_DP
    }

    /**
     * 轨道不遮字不变式：最内层轨道右缘必须严格小于文本左缘。
     * 文本左缘 = [TEXT_INDENT_PER_DEPTH_DP] × 总容器深度（引用 + 列表逐层 +1），
     * 而 [totalDepth] 恒 ≥ [blockquoteDepth]，因此取两者中较小者校验即可。
     */
    fun quoteRailsStayLeftOfText(blockquoteDepth: Int, totalDepth: Int): Boolean {
        if (blockquoteDepth <= 0) return true
        val textLeft = TEXT_INDENT_PER_DEPTH_DP * totalDepth.coerceAtLeast(blockquoteDepth)
        return quoteRailRightEdgeDp(blockquoteDepth) < textLeft
    }

    /**
     * 代码面板顶部语言标签；null / 空白 → 不显示。
     * 只影响展示，不进入任何 offset 空间。
     */
    fun codeLanguageLabel(language: String?): String? =
        language?.trim()?.takeIf { it.isNotEmpty() }
}
