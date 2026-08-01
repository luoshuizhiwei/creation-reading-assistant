package com.creationreadingassistant.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme-independent spatial contract.
 *
 * A visual theme may replace color, shape, type and material treatment without
 * changing the information density of every screen.
 */
data class LayoutTokens(
    val pageHorizontal: Dp = 16.dp,
    val pageHorizontalWide: Dp = 24.dp,
    val pageVertical: Dp = 12.dp,
    val sectionGap: Dp = 20.dp,
    val contentGap: Dp = 12.dp,
    val relatedGap: Dp = 8.dp,
    val microGap: Dp = 4.dp,
    val cardPadding: Dp = 14.dp,
    val compactCardPadding: Dp = 12.dp,
    val gridGap: Dp = 12.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val singleLineRowHeight: Dp = 52.dp,
    val supportingRowHeight: Dp = 64.dp,
    val topBarHeight: Dp = 64.dp,
)

val DefaultLayoutTokens = LayoutTokens()

val LocalLayoutTokens = staticCompositionLocalOf { DefaultLayoutTokens }
