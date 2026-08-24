package com.creationreadingassistant.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveNavigationPolicyTest {
    @Test
    fun `phones use bottom bar and tablets use navigation rail`() {
        assertEquals(TopLevelNavigationMode.BOTTOM_BAR, topLevelNavigationMode(320.dp))
        assertEquals(TopLevelNavigationMode.BOTTOM_BAR, topLevelNavigationMode(599.dp))
        assertEquals(TopLevelNavigationMode.RAIL, topLevelNavigationMode(600.dp))
        assertEquals(TopLevelNavigationMode.RAIL, topLevelNavigationMode(1200.dp))
    }
}
