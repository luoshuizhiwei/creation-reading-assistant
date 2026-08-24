package com.creationreadingassistant.ui.navigation

import androidx.compose.ui.unit.Dp
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens

enum class TopLevelNavigationMode { BOTTOM_BAR, RAIL }

fun topLevelNavigationMode(windowWidth: Dp): TopLevelNavigationMode =
    if (windowWidth >= DefaultLayoutTokens.wideScreenBreakpoint) {
        TopLevelNavigationMode.RAIL
    } else {
        TopLevelNavigationMode.BOTTOM_BAR
    }
