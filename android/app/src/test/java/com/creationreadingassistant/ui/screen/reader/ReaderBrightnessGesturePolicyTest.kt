package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBrightnessGesturePolicyTest {

    @Test
    fun `fixed brightness unchanged by an edge touch is not persisted again`() {
        assertFalse(shouldCommitBrightnessGesture(startBrightness = 60, finalBrightness = 60))
    }

    @Test
    fun `leaving follow-system mode and changing brightness are persisted`() {
        assertTrue(shouldCommitBrightnessGesture(startBrightness = -1, finalBrightness = 65))
        assertTrue(shouldCommitBrightnessGesture(startBrightness = 65, finalBrightness = 42))
    }

    @Test
    fun `follow-system drag starts from the remembered fixed brightness`() {
        assertEquals(65, brightnessGestureInitialValue(readerBrightness = -1, lastFixedBrightness = 65))
        assertEquals(100, brightnessGestureInitialValue(readerBrightness = -1, lastFixedBrightness = 120))
        assertEquals(42, brightnessGestureInitialValue(readerBrightness = 42, lastFixedBrightness = 65))
    }

    @Test
    fun `gesture commit keeps current and remembered fixed brightness aligned`() {
        val fromFollowSystem = com.creationreadingassistant.data.settings.ReaderSettings(
            brightness = -1,
            lastFixedBrightness = 35,
        ).withCommittedGestureBrightness(65)
        assertEquals(65, fromFollowSystem.brightness)
        assertEquals(65, fromFollowSystem.lastFixedBrightness)

        val clamped = fromFollowSystem.withCommittedGestureBrightness(140)
        assertEquals(100, clamped.brightness)
        assertEquals(100, clamped.lastFixedBrightness)
    }
}
