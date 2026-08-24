package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import kotlin.math.floor

private val TabletShelfTileMinWidth = 112.dp

internal fun shelfGridColumnCount(
    windowWidth: Dp,
    tokens: LayoutTokens = DefaultLayoutTokens,
): Int {
    val metrics = adaptivePageMetrics(windowWidth, tokens)
    if (!metrics.isWide) return 3

    val usableWidth = (
        metrics.contentWidth - metrics.horizontalPadding * 2 + tokens.gridGap
    ).coerceAtLeast(TabletShelfTileMinWidth)
    val columns = floor(
        (usableWidth / (TabletShelfTileMinWidth + tokens.gridGap)).toDouble(),
    ).toInt()
    return columns.coerceIn(4, 5)
}
