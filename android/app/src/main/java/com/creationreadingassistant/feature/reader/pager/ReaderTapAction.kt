package com.creationreadingassistant.feature.reader.pager

/** A single, testable interpretation of taps on the reading canvas. */
enum class ReaderTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_CONTROLS,
    NONE,
}

/** 屏幕点击翻页热区动作映射预设。 */
object TapActionPreset {
    /** 标准模式：左退右进（左=上一页，右=下一页，中=唤起控制栏）。 */
    const val STANDARD = "standard"

    /** 左手单手模式：左进右退（左=下一页，右=上一页，中=唤起控制栏）。 */
    const val LEFT_HAND = "left_hand"

    /** 双向推进模式：两侧均前进（左=下一页，右=下一页，中=唤起控制栏；五分区顶部或手势负责回退）。 */
    const val BOTH_NEXT = "both_next"
}

/**
 * 阅读页点击热区几何的**唯一生产事实源**（S09-B）。
 *
 * 修复前三处各写一套比例，同一个设置在三个地方画成不同大小的区域：
 * - [resolveReaderTapAction] 内联 `width / 3f`、`height * 0.12f`；
 * - legacy 分页 EPUB `PagedEpubContent` 用 `weight(0.16f) / 0.68f / 0.16f`；
 * - 设置预览卡 `TapZonePreviewCard` 用 `0.32/0.36/0.32`、`0.24/0.52/0.24`、`0.30/0.40/0.30`。
 * 现在三处都从这里取值：横向左/中/右各 1/3，五分区纵向顶 12% / 中 76% / 底 12%。
 *
 * 所有字段都是**占比**，横向三项之和 = 1，纵向三项之和 = 1，且均为正数（由
 * `ReaderTapZoneGeometryTest` 断言）。改这里等于同时改引擎、legacy EPUB 与预览卡，
 * 不要再在任何消费端复制一份字面量。
 *
 * 声明位置有约束：必须在 [resolveReaderTapAction] **之前**。
 * `ReaderTapZoneGeometryTest.engineDoesNotInlineItsOwnCopyOfTheRatios` 只截取本函数
 * 声明结尾之后的文本段作为"函数体"做守卫，把常量对象写在函数之后会让守卫把常量
 * 自身的字面量算进函数体，从而误报。
 */
object ReaderTapZoneGeometry {
    /** 左区宽度占比；与 [CENTER_WIDTH] 等宽，即三分区各占 1/3。 */
    const val SIDE_WIDTH: Float = 1f / 3f

    /** 中央区（唤出/收起控制栏）宽度占比。 */
    const val CENTER_WIDTH: Float = 1f / 3f

    /** 五分区顶部带（上一页）高度占比。 */
    const val TOP_HEIGHT: Float = 0.12f

    /** 五分区底部带（下一页）高度占比。 */
    const val BOTTOM_HEIGHT: Float = 0.12f

    /** 五分区中带占比；中带内部仍按 [SIDE_WIDTH] / [CENTER_WIDTH] 横向切分。 */
    const val MIDDLE_HEIGHT: Float = 1f - TOP_HEIGHT - BOTTOM_HEIGHT

    /** 直接喂给 `RowScope.weight()` 的横向三段：左 / 中 / 右。 */
    val horizontalWeights: Triple<Float, Float, Float>
        get() = Triple(SIDE_WIDTH, CENTER_WIDTH, SIDE_WIDTH)

    /** 直接喂给 `ColumnScope.weight()` 的纵向三段：顶带 / 中带 / 底带。 */
    val verticalWeights: Triple<Float, Float, Float>
        get() = Triple(TOP_HEIGHT, MIDDLE_HEIGHT, BOTTOM_HEIGHT)

    /** 左区与中央区的分界 x（px）；中央区从这条线开始。 */
    fun centerLeftEdge(width: Float): Float = width * SIDE_WIDTH

    /** 中央区与右区的分界 x（px）。 */
    fun centerRightEdge(width: Float): Float = width * (SIDE_WIDTH + CENTER_WIDTH)

    /** 顶部带与中带的分界 y（px）。 */
    fun topBandBottomEdge(height: Float): Float = height * TOP_HEIGHT

    /** 中带与底部带的分界 y（px）。 */
    fun bottomBandTopEdge(height: Float): Float = height * (1f - BOTTOM_HEIGHT)
}

fun resolveReaderTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tapZoneMode: String,
    canPrevious: Boolean,
    canNext: Boolean,
    preset: String = TapActionPreset.STANDARD,
    edgeDeadzonePx: Float = 0f,
): ReaderTapAction {
    if (width <= 0f || height <= 0f || x !in 0f..width || y !in 0f..height) {
        return ReaderTapAction.NONE
    }
    // 屏幕左右边缘防误触死区过滤（手掌大鱼际/指根贴边微触碰不触发翻页）
    if (edgeDeadzonePx > 0f && (x < edgeDeadzonePx || x > width - edgeDeadzonePx)) {
        return ReaderTapAction.NONE
    }
    // 分区边界一律取自 ReaderTapZoneGeometry：本函数不得再持有自己的比例。
    // 注意声明顺序 —— ReaderTapZoneGeometry 必须在本函数之前定义。
    val requested = when {
        tapZoneMode == "five-zone" && y < ReaderTapZoneGeometry.topBandBottomEdge(height) ->
            ReaderTapAction.PREVIOUS_PAGE
        tapZoneMode == "five-zone" && y > ReaderTapZoneGeometry.bottomBandTopEdge(height) ->
            ReaderTapAction.NEXT_PAGE
        x < ReaderTapZoneGeometry.centerLeftEdge(width) -> when (preset) {
            TapActionPreset.LEFT_HAND -> ReaderTapAction.NEXT_PAGE
            TapActionPreset.BOTH_NEXT -> ReaderTapAction.NEXT_PAGE
            else -> ReaderTapAction.PREVIOUS_PAGE
        }
        x > ReaderTapZoneGeometry.centerRightEdge(width) -> when (preset) {
            TapActionPreset.LEFT_HAND -> ReaderTapAction.PREVIOUS_PAGE
            TapActionPreset.BOTH_NEXT -> ReaderTapAction.NEXT_PAGE
            else -> ReaderTapAction.NEXT_PAGE
        }
        else -> ReaderTapAction.TOGGLE_CONTROLS
    }
    return when (requested) {
        ReaderTapAction.PREVIOUS_PAGE -> if (canPrevious) requested else ReaderTapAction.NONE
        ReaderTapAction.NEXT_PAGE -> if (canNext) requested else ReaderTapAction.NONE
        else -> requested
    }
}
