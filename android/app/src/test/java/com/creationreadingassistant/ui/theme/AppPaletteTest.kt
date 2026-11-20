package com.creationreadingassistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPaletteTest {
    @Test fun storedPaletteRoundTrips() {
        AppPalette.entries.forEach { palette ->
            assertEquals(palette, AppPalette.fromStored(palette.storageId))
        }
    }

    @Test fun missingOrUnknownPaletteUsesBuiltInDefault() {
        // P2：新用户默认改为知识花园绿（仅 fromStored(null/未知) 生效）。
        assertEquals(AppPalette.LAB_GARDEN, AppPalette.fromStored(null))
        assertEquals(AppPalette.LAB_GARDEN, AppPalette.fromStored("future_palette"))
    }

    @Test fun storedLegacyPaletteIsPreservedNotMigrated() {
        // 老用户已存值原样保留，不被新默认强制迁移。
        assertEquals(AppPalette.CLEAR_BLUE, AppPalette.fromStored("clear_blue"))
        assertEquals(AppPalette.PAPER_INK, AppPalette.fromStored("paper_ink"))
    }
}
