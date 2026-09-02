package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderVolumeKeyTurnPolicyTest {

    @Test
    fun `disabled paging and TTS policy keep system volume behavior`() {
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(volumeKeyPaging = false),
        )
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(ttsVisible = true, allowDuringTts = false),
        )
    }

    @Test
    fun `paged engine consumes a valid direction without scroll layout`() {
        assertEquals(
            ReaderVolumeTurnPlan.Paged(1),
            plan(pagerEngineOn = true, scrollLayoutReady = false),
        )
    }

    @Test
    fun `unlaid scroll reader keeps volume key for system`() {
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(scrollLayoutReady = false),
        )
    }

    @Test
    fun `scrollable reader consumes the key for an in chapter page turn`() {
        assertEquals(
            ReaderVolumeTurnPlan.Scroll(1),
            plan(canScroll = true),
        )
    }

    @Test
    fun `chapter boundary moves only when an adjacent chapter exists`() {
        assertEquals(
            ReaderVolumeTurnPlan.Chapter(2),
            plan(canScroll = false, chapterIndex = 1, chapterCount = 3),
        )
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(canScroll = false, chapterIndex = 2, chapterCount = 3),
        )
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(direction = -1, canScroll = false, chapterIndex = 0, chapterCount = 3),
        )
    }

    @Test
    fun `invalid direction never consumes a volume key`() {
        assertEquals(ReaderVolumeTurnPlan.UseSystemVolume, plan(direction = 0))
    }

    private fun plan(
        direction: Int = 1,
        volumeKeyPaging: Boolean = true,
        allowDuringTts: Boolean = true,
        ttsVisible: Boolean = false,
        pagerEngineOn: Boolean = false,
        scrollLayoutReady: Boolean = true,
        canScroll: Boolean = false,
        chapterIndex: Int = 0,
        chapterCount: Int = 0,
    ): ReaderVolumeTurnPlan = readerVolumeTurnPlan(
        direction = direction,
        volumeKeyPaging = volumeKeyPaging,
        allowDuringTts = allowDuringTts,
        ttsVisible = ttsVisible,
        pagerEngineOn = pagerEngineOn,
        scrollLayoutReady = scrollLayoutReady,
        canScroll = canScroll,
        chapterIndex = chapterIndex,
        chapterCount = chapterCount,
    )
}
