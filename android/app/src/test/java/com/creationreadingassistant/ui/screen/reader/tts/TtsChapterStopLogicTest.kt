package com.creationreadingassistant.ui.screen.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 105-v1：生产章末自动接续判定 [ttsAutoNextTarget] 的定向回归。
 *
 * `TtsAutoNextChapterEffect` 的 onFinished 直接调用该函数，并在回调时读取
 * 宿主当前 `timedStopMinutes`；因此这里验证的是生产 effect 的真实判定入口，
 * 而不是另写一份同形策略。
 */
class TtsChapterStopLogicTest {

    @Test
    fun `finish chapter option stops without advancing in mid book`() {
        assertNull(
            "选择播完本章(-1)时，本章结束不得翻到下一章",
            ttsAutoNextTarget(chapterIndex = 0, chapterCount = 3, timedStopMinutes = -1),
        )
        assertNull(ttsAutoNextTarget(chapterIndex = 1, chapterCount = 3, timedStopMinutes = -1))
    }

    @Test
    fun `timer off and positive timers keep existing auto advance`() {
        assertEquals(
            "关闭定时(0)仍按既有语义自动续章",
            1,
            ttsAutoNextTarget(chapterIndex = 0, chapterCount = 3, timedStopMinutes = 0),
        )
        assertEquals(
            "正分钟定时不改变既有自动续章语义",
            2,
            ttsAutoNextTarget(chapterIndex = 1, chapterCount = 3, timedStopMinutes = 15),
        )
    }

    @Test
    fun `last chapter and texts without chapters never advance`() {
        assertNull(ttsAutoNextTarget(chapterIndex = 2, chapterCount = 3, timedStopMinutes = 0))
        assertNull(ttsAutoNextTarget(chapterIndex = 2, chapterCount = 3, timedStopMinutes = 15))
        assertNull(ttsAutoNextTarget(chapterIndex = 0, chapterCount = 0, timedStopMinutes = 0))
        assertNull(ttsAutoNextTarget(chapterIndex = 0, chapterCount = 0, timedStopMinutes = -1))
    }
}
