package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.screen.reader.content.LegacyEpubPageTurnEffect
import com.creationreadingassistant.ui.screen.reader.content.legacyEpubPageTurnEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyEpubPageTurnEffectTest {
    @Test
    fun `legacy epub maps every reader setting to a real transition`() {
        assertEquals(LegacyEpubPageTurnEffect.NONE, legacyEpubPageTurnEffect("none"))
        assertEquals(LegacyEpubPageTurnEffect.FADE, legacyEpubPageTurnEffect("fade"))
        assertEquals(LegacyEpubPageTurnEffect.SLIDE, legacyEpubPageTurnEffect("slide"))
        assertEquals(LegacyEpubPageTurnEffect.COVER, legacyEpubPageTurnEffect("cover"))
    }

    @Test
    fun `unknown persisted effect falls back to no animation`() {
        assertEquals(LegacyEpubPageTurnEffect.NONE, legacyEpubPageTurnEffect("future-effect"))
    }
}
