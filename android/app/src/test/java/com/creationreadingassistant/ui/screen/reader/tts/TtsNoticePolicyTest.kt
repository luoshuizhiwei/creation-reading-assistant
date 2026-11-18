package com.creationreadingassistant.ui.screen.reader.tts

import android.content.Context
import com.creationreadingassistant.ui.screen.reader.openTts
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTS 提示策略验收（问题 3）：
 * - 被动 onInit failure 不弹（进书即初始化失败不打扰用户）；
 * - 用户点击听书/openTts 后若引擎不可用：恰好提示一次（保留「去设置」动作）并 reinitialize；
 * - 主动错误（播放中引擎错误 / 未就绪拒绝）不被吞。
 */
class TtsNoticePolicyTest {

    @Test
    fun `被动初始化失败静默不弹`() {
        assertEquals(TtsNoticeAction.None, TtsNoticePolicy.onInitFailure())
    }

    @Test
    fun `openTts 引擎不可用提示一次并重初始化`() {
        assertEquals(
            TtsNoticeAction.ReinitializeWithNotice,
            TtsNoticePolicy.onOpenTtsRejected(TtsAvailability.Unavailable),
        )
    }

    @Test
    fun `openTts 引擎初始化中显示可解释消息`() {
        val action = TtsNoticePolicy.onOpenTtsRejected(TtsAvailability.Initializing)
        assertTrue(action is TtsNoticeAction.ShowMessage)
        assertTrue((action as TtsNoticeAction.ShowMessage).message.contains("初始化"))
    }

    @Test
    fun `播放中主动引擎错误不被吞`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        holder.markPlaying()
        holder.onEngineError("朗读播放出错，请检查系统语音引擎。")
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals(1, holder.errorSeq)
        assertEquals("朗读播放出错，请检查系统语音引擎。", holder.consumeError()?.message)
        assertTrue(holder.gatePlay() is TtsPlayResult.Accepted)
    }

    @Test
    fun `unavailable system TTS leaves online output an explicit choice`() {
        val host = mockk<TtsEngineHost>(relaxed = true)
        every { host.engineId } returns TtsEngineId.SYSTEM
        every { host.availability } returns TtsAvailability.Unavailable
        every { host.play(any(), any(), any(), any()) } returns TtsPlayResult.Rejected("本地语音不可用")
        var controlsVisible = false
        openTts(
            contentText = "测试正文",
            epubBook = null,
            isMarkdown = false,
            ttsResumeChapter = 0,
            chapterIndex = 0,
            ttsResumeOffset = 0,
            tts = host,
            bookTitle = "测试 TXT",
            currentChapterTitle = "测试章",
            context = mockk<Context>(),
            onShowTtsChange = { controlsVisible = it },
            showNotice = {},
        )
        assertTrue(controlsVisible)
        verify(exactly = 1) { host.play(any(), any(), any(), any()) }
        verify(exactly = 0) { host.switchEngine(TtsEngineId.EDGE) }
    }
}
