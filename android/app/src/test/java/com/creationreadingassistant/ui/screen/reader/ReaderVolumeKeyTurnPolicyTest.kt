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

    @Test
    fun `effectiveVolumeTurnDirection maps directions according to invert setting`() {
        assertEquals(-1, effectiveVolumeTurnDirection(-1, invert = false))
        assertEquals(1, effectiveVolumeTurnDirection(1, invert = false))
        assertEquals(1, effectiveVolumeTurnDirection(-1, invert = true))
        assertEquals(-1, effectiveVolumeTurnDirection(1, invert = true))
        assertEquals(0, effectiveVolumeTurnDirection(0, invert = true))
    }

    @Test
    fun `inverted volume keys reverse paged turn direction`() {
        // 音量上键 (-1) 开启反转后应当触发下一页 (+1)
        assertEquals(
            ReaderVolumeTurnPlan.Paged(1),
            plan(direction = -1, pagerEngineOn = true, volumeKeyInvert = true),
        )
        // 音量下键 (1) 开启反转后应当触发上一页 (-1)
        assertEquals(
            ReaderVolumeTurnPlan.Paged(-1),
            plan(direction = 1, pagerEngineOn = true, volumeKeyInvert = true),
        )
    }

    @Test
    fun `inverted volume keys reverse scroll and chapter navigation`() {
        // 滚动模式：音量上键在反转下向下滚动 (Scroll(1))
        assertEquals(
            ReaderVolumeTurnPlan.Scroll(1),
            plan(direction = -1, canScroll = true, volumeKeyInvert = true),
        )
        // 跨章导航：音量上键在反转下跨向下一章 (Chapter(2))
        assertEquals(
            ReaderVolumeTurnPlan.Chapter(2),
            plan(direction = -1, canScroll = false, chapterIndex = 1, chapterCount = 3, volumeKeyInvert = true),
        )
        // 跨章边界：末章按音量上键（反转下试图向后）无下一章，回退系统音量
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(direction = -1, canScroll = false, chapterIndex = 2, chapterCount = 3, volumeKeyInvert = true),
        )
        // 首章按音量下键（反转下试图向前）无上一章，回退系统音量
        assertEquals(
            ReaderVolumeTurnPlan.UseSystemVolume,
            plan(direction = 1, canScroll = false, chapterIndex = 0, chapterCount = 3, volumeKeyInvert = true),
        )
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
        volumeKeyInvert: Boolean = false,
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
        volumeKeyInvert = volumeKeyInvert,
    )
}
