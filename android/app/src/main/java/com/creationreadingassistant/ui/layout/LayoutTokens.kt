package com.creationreadingassistant.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Theme-independent spatial contract.
 *
 * A visual theme may replace color, shape, type and material treatment without
 * changing the information density of every screen.
 *
 * ### 语义划分 —— 哪些 token 用于 viewport 避让，哪些用于内容边距
 *
 * | 用途 | Token 示例 | 放在哪里 |
 * |------|-----------|----------|
 * | 视口/系统栏避让（viewport padding） | [topBarHeight], [bottomNavHeight] | `Modifier.padding()`，或 Scaffold content lambda 的 PaddingValues |
 * | 页面级内容边距（content padding） | [pageHorizontal], [pageVertical] | `LazyColumn.contentPadding`、Column/Row 的 `.padding` |
 * | 内容间距 | [sectionGap], [contentGap], [relatedGap], [microGap] | `Arrangement.spacedBy` |
 * | 卡片内部 padding | [cardPadding], [compactCardPadding] | 卡片组件内部 `.padding` |
 * | 可点击最小尺寸 | [minimumTouchTarget] | `Modifier.heightIn(min = ...)`, Row 最小高度 |
 *
 * **严禁**把同一 token 同时用于 viewport padding 与 content padding，否则同一数值被叠加两次。
 * 具体防呆见 `PageLazyColumn` 的 `scaffoldPadding` / `contentPadding` 参数检查。
 */
data class LayoutTokens(
    /** 页面水平内容边距。用于 `LazyColumn.contentPadding` / 非懒加载内容的 `.padding(horizontal)`。非视口避让。 */
    val pageHorizontal: Dp = 16.dp,
    /** 宽屏下（>600dp）水平内容边距。 */
    val pageHorizontalWide: Dp = 24.dp,
    /** 页面垂直内容边距。用于 `LazyColumn.contentPadding` 首末 item 偏移。非视口避让。 */
    val pageVertical: Dp = 12.dp,
    /** 章节级（Section）区块之间的间距。`Arrangement.spacedBy`。 */
    val sectionGap: Dp = 20.dp,
    /** 内容条目之间的间距（同 Section 内两个卡片/控件之间）。 */
    val contentGap: Dp = 12.dp,
    /** 强相关控件之间的间距（图标+文字、开关+说明）。 */
    val relatedGap: Dp = 8.dp,
    /** 极小间距（徽章偏移、对齐微调）。 */
    val microGap: Dp = 4.dp,
    /** SectionCard 内部 padding。 */
    val cardPadding: Dp = 14.dp,
    /** 紧凑卡片内部 padding（如设置行的内嵌子控件）。 */
    val compactCardPadding: Dp = 12.dp,
    /** 网格（Shelf 等）格子之间的间距。 */
    val gridGap: Dp = 12.dp,
    /** 所有可点击/可交互行的最小高度，满足 48dp 触摸目标。 */
    val minimumTouchTarget: Dp = 48.dp,
    /** 单行控件（标题+右侧开关）行高。≥ minimumTouchTarget。 */
    val singleLineRowHeight: Dp = 52.dp,
    /** 带辅助行的设置行高（两行文字+右侧控件）。≥ minimumTouchTarget。 */
    val supportingRowHeight: Dp = 64.dp,
    /** AppTopBar 内容区最小高度；状态栏 inset 会在此基础上额外撑高整体顶栏。 */
    val topBarHeight: Dp = 64.dp,
    /**
     * Material3 NavigationBar 容器高度（不含上方发丝分隔线）。
     * 仅用于估算/断言；实际高度来自 AppNavigation 外层 Scaffold 的 innerPadding。
     * 视口避让用，不得加到内容边距里。
     */
    val bottomNavHeight: Dp = 80.dp,
    /** 窄屏断点宽度；< 此值时使用 [pageHorizontal]，≥ 此值时使用 [pageHorizontalWide]。 */
    val wideScreenBreakpoint: Dp = 600.dp,
    /** 断言 320dp 小屏时仍应显示的内容宽度（水平 padding × 2 的余量）。 */
    val compactScreenMinWidth: Dp = 320.dp,
    /**
     * 内容最大宽度（宽屏居中用）。≥ [wideScreenBreakpoint] 时，页面主体内容约束在此宽度内并水平居中，
     * 避免平板/折叠屏上文字与控件被拉得过宽。窄屏不约束。
     */
    val contentMaxWidth: Dp = 720.dp,
)

val DefaultLayoutTokens = LayoutTokens()

val LocalLayoutTokens = staticCompositionLocalOf { DefaultLayoutTokens }

data class AdaptivePageMetrics(
    val isWide: Boolean,
    val contentWidth: Dp,
    val horizontalPadding: Dp,
)

/** 手机保持满宽；平板/折叠屏将正文栏限制在可读宽度并增加页面边距。 */
fun adaptivePageMetrics(
    windowWidth: Dp,
    tokens: LayoutTokens = DefaultLayoutTokens,
): AdaptivePageMetrics {
    val safeWidth = windowWidth.coerceAtLeast(0.dp)
    val isWide = safeWidth >= tokens.wideScreenBreakpoint
    return AdaptivePageMetrics(
        isWide = isWide,
        contentWidth = if (isWide) minOf(safeWidth, tokens.contentMaxWidth) else safeWidth,
        horizontalPadding = if (isWide) tokens.pageHorizontalWide else tokens.pageHorizontal,
    )
}

/**
 * 宽屏居中：把内容约束在 [contentMaxWidth] 内并水平居中。
 *
 * 用法：在页面主体内容（Column / 卡片列表）的最外层 `Modifier` 上调用，
 * 配合满宽父容器即可在平板 / 折叠屏上获得居中的阅读栏宽，窄屏下退化为满宽。
 *
 * ```kotlin
 * Column(Modifier.fillMaxWidth().maxContentWidth()) { ... }
 * ```
 */
fun Modifier.maxContentWidth(
    maxWidth: Dp = DefaultLayoutTokens.contentMaxWidth,
): Modifier = this
    .widthIn(max = maxWidth)
    .wrapContentWidth(Alignment.CenterHorizontally)

// —————————————————————————————————————————————————————————————————
// PaddingValues 纯函数工具（可在 JVM 单测中直接调用）
// —————————————————————————————————————————————————————————————————

/** 各方向相加；避免把同一 PaddingValues 对象加两次造成重复 inset。 */
fun PaddingValues.plusTokens(
    other: PaddingValues,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): PaddingValues {
    val start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection)
    val top = calculateTopPadding() + other.calculateTopPadding()
    val end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection)
    val bottom = calculateBottomPadding() + other.calculateBottomPadding()
    return PaddingValues(start = start, top = top, end = end, bottom = bottom)
}

/** 四个方向是否完全相等（用于 PageLazyColumn 防重复判定）。 */
fun PaddingValues.structurallyEquals(
    other: PaddingValues,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Boolean {
    return calculateStartPadding(layoutDirection) == other.calculateStartPadding(layoutDirection) &&
        calculateTopPadding() == other.calculateTopPadding() &&
        calculateEndPadding(layoutDirection) == other.calculateEndPadding(layoutDirection) &&
        calculateBottomPadding() == other.calculateBottomPadding()
}

/** 调试描述字符串。 */
fun PaddingValues.describe(layoutDirection: LayoutDirection = LayoutDirection.Ltr): String {
    return "start=${calculateStartPadding(layoutDirection)}, " +
        "top=${calculateTopPadding()}, " +
        "end=${calculateEndPadding(layoutDirection)}, " +
        "bottom=${calculateBottomPadding()}"
}
