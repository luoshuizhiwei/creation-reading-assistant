package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.ui.screen.home.components.formatArchiveDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReadingArchiveSectionTest {
    @Test fun durationUsesCompactChineseUnits() {
        assertEquals("0 分钟", formatArchiveDuration(0))
        assertEquals("1 分钟", formatArchiveDuration(30_000))
        assertEquals("1 小时", formatArchiveDuration(3_600_000))
        assertEquals("1 小时 31 分钟", formatArchiveDuration(5_430_000))
    }
}
