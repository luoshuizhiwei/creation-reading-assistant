package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens

/**
 * 阅读器视口的响应式尺度。
 *
 * [ReaderSettings] 中的字号/页边距是用户偏好，不应被写回 DataStore；这里仅在当前
 * 窗口生成一份派生值。正文仍以 sp 绘制（继续跟随系统 density/fontScale），本层只
 * 对极窄手机和宽屏窗口做有限幅度的视觉补偿，避免同一套偏好在 320dp 小屏过挤、在
 * 平板上又显得过小。
 */
internal data class ReaderViewportProfile(
    val scale: Float,
    val fontSizeSp: Float,
    val pageMarginDp: Float,
    val contentTopPaddingDp: Float,
)

private const val REFERENCE_SHORTEST_SIDE_DP = 411f
private const val MIN_VIEWPORT_SCALE = 0.88f
private const val MAX_VIEWPORT_SCALE = 1.08f
private const val MIN_READER_FONT_SP = 12f
private const val MAX_READER_FONT_SP = 40f
private const val MIN_READER_MARGIN_DP = 10f
private const val MAX_READER_MARGIN_DP = 42f

private const val PAGED_READER_HEADER_HEIGHT_DP = 24f
private const val PAGED_READER_FOOTER_HEIGHT_DP = 28f
private const val PAGED_READER_HEADER_GAP_DP = 8f
private const val PAGED_READER_META_TEXT_SIZE_SP = 11f
private const val PAGED_READER_META_LINE_HEIGHT_MULTIPLIER = 1.5f

/**
 * 以窗口最短边计算阅读尺度，而不是使用长边；因此横屏不会因为宽度很大而把正文
 * 放大到不适合阅读的程度。上下限避免极端折叠/分屏尺寸造成跳变。
 */
internal fun readerViewportProfile(
    widthDp: Float,
    heightDp: Float,
    preferredFontSizeSp: Float,
    preferredPageMarginDp: Float,
): ReaderViewportProfile {
    val shortestSideDp = minOf(widthDp, heightDp).coerceAtLeast(1f)
    val scale = (shortestSideDp / REFERENCE_SHORTEST_SIDE_DP)
        .coerceIn(MIN_VIEWPORT_SCALE, MAX_VIEWPORT_SCALE)
    return ReaderViewportProfile(
        scale = scale,
        fontSizeSp = (preferredFontSizeSp * scale).coerceIn(MIN_READER_FONT_SP, MAX_READER_FONT_SP),
        pageMarginDp = (preferredPageMarginDp * scale).coerceIn(MIN_READER_MARGIN_DP, MAX_READER_MARGIN_DP),
        contentTopPaddingDp = (32f * scale).coerceIn(24f, 40f),
    )
}

/** 当前窗口的派生阅读设置；不改变用户保存的 ReaderSettings。 */
internal fun ReaderSettings.forReaderViewport(widthDp: Float, heightDp: Float): ReaderSettings {
    val profile = readerViewportProfile(widthDp, heightDp, fontSize, pageMargin)
    return copy(
        fontSize = profile.fontSizeSp,
        pageMargin = profile.pageMarginDp,
    )
}

/** 分页宿主使用与正文字号同源的顶端安全区，避免首行在小屏/大字体时被裁切。 */
internal fun adaptiveReaderContentTopPaddingDp(fontSizeSp: Float): Float =
    (fontSizeSp * 1.28f).coerceIn(24f, 40f)

/** 页眉/页脚的最小高度随系统字体放大，避免 11sp 元信息在无障碍字号下被裁切。 */
internal fun pagedReaderHeaderHeightDp(fontScale: Float = 1f): Float =
    maxOf(
        PAGED_READER_HEADER_HEIGHT_DP,
        PAGED_READER_META_TEXT_SIZE_SP * PAGED_READER_META_LINE_HEIGHT_MULTIPLIER * fontScale.coerceAtLeast(1f),
    )

internal fun pagedReaderFooterHeightDp(fontScale: Float = 1f): Float =
    maxOf(
        PAGED_READER_FOOTER_HEIGHT_DP,
        PAGED_READER_META_TEXT_SIZE_SP * PAGED_READER_META_LINE_HEIGHT_MULTIPLIER * fontScale.coerceAtLeast(1f),
    )

/**
 * 计算新分页宿主的首行安全区。
 *
 * 分页宿主自身会绘制可选的 24dp 页眉，并在正文前留 8dp 间距；当页眉被用户关闭时，
 * 不能继续沿用“有页眉”的 32dp 假设，否则 ReaderTopChrome 显示时会覆盖首行。这里
 * 同时满足两条约束：字号需要的字形安全区，以及顶部覆盖栏扣除实际页眉/间距后的剩余区。
 */
internal fun pagedReaderContentTopPaddingDp(
    fontSizeSp: Float,
    headerVisible: Boolean,
    fontScale: Float = 1f,
): Float {
    val headerHeight = if (headerVisible) pagedReaderHeaderHeightDp(fontScale) else 0f
    val overlayReserve = (
        DefaultLayoutTokens.topBarHeight.value - headerHeight - PAGED_READER_HEADER_GAP_DP
    ).coerceAtLeast(0f)
    return maxOf(adaptiveReaderContentTopPaddingDp(fontSizeSp * fontScale.coerceAtLeast(1f)), overlayReserve)
}
