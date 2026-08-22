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
        assertEquals(AppPalette.CLEAR_BLUE, AppPalette.fromStored(null))
        assertEquals(AppPalette.CLEAR_BLUE, AppPalette.fromStored("future_palette"))
    }
}
