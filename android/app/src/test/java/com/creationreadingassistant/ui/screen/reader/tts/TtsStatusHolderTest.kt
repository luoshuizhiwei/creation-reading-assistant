package com.creationreadingassistant.ui.screen.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 授权 seam 验收：可观察的 TTS availability/playback 状态。
 * - 初始化中 / Ready / Unavailable / Error 状态；
 * - 未 Ready 时 play 不得进入 Playing，返回可解释错误；
 * - onError 必须停止播放态并产生一次用户可消费错误；
 * - Ready 后正常播放行为不退化。
 */
class TtsStatusHolderTest {

    @Test
    fun `initially reports initializing and idle without error`() {
        val holder = TtsStatusHolder()
        assertEquals(TtsAvailability.Initializing, holder.availability)
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals(0, holder.errorSeq)
        assertNull(holder.consumeError())
    }

    @Test
    fun `init success transitions to ready without error`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        assertEquals(TtsAvailability.Ready, holder.availability)
        assertNull(holder.consumeError())
    }

    @Test
    fun `init failure becomes unavailable silently without publishing error`() {
        val holder = TtsStatusHolder()
        holder.onInitFailure("系统语音引擎初始化失败，请到系统设置检查文字转语音。")
        assertEquals(TtsAvailability.Unavailable, holder.availability)
        // 被动 init failure 不弹提示：错误只在用户主动操作后由 notifyUnavailable 发布
        assertEquals(0, holder.errorSeq)
        assertNull(holder.consumeError())
    }

    @Test
    fun `notifyUnavailable publishes exactly one consumable error`() {
        val holder = TtsStatusHolder()
        holder.onInitFailure("系统语音引擎初始化失败")
        holder.notifyUnavailable("系统语音引擎初始化失败，请到系统设置检查文字转语音。")
        assertEquals(TtsAvailability.Unavailable, holder.availability)
        assertEquals(1, holder.errorSeq)
        assertEquals("系统语音引擎初始化失败，请到系统设置检查文字转语音。", holder.consumeError()?.message)
        assertNull(holder.consumeError())
    }

    @Test
    fun `play gate while initializing rejects with explainable reason and keeps idle`() {
        val holder = TtsStatusHolder()
        val result = holder.gatePlay()
        assertTrue(result is TtsPlayResult.Rejected)
        val reason = (result as TtsPlayResult.Rejected).reason
        assertTrue(reason.isNotBlank())
        assertTrue(reason.contains("初始化"))
        assertEquals(TtsPlayback.Idle, holder.playback)
    }

    @Test
    fun `play gate while unavailable rejects with explainable reason and keeps idle`() {
        val holder = TtsStatusHolder()
        holder.onInitFailure("系统语音引擎初始化失败")
        val result = holder.gatePlay()
        assertTrue(result is TtsPlayResult.Rejected)
        val reason = (result as TtsPlayResult.Rejected).reason
        assertTrue(reason.isNotBlank())
        assertTrue(reason.contains("语音引擎"))
        assertEquals(TtsPlayback.Idle, holder.playback)
    }

    @Test
    fun `play gate while ready accepts without flipping playback`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        assertEquals(TtsPlayResult.Accepted, holder.gatePlay())
        assertEquals(TtsPlayback.Idle, holder.playback)
    }

    @Test
    fun `normal playback transitions produce no spurious errors`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        holder.markPlaying()
        holder.markPaused()
        holder.markPlaying()
        holder.markIdle()
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals(0, holder.errorSeq)
        assertNull(holder.consumeError())
    }

    @Test
    fun `engine error stops playing publishes one error and stays retryable`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        holder.markPlaying()
        holder.onEngineError("朗读播放出错，请检查系统语音引擎。")
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals(1, holder.errorSeq)
        assertEquals("朗读播放出错，请检查系统语音引擎。", holder.consumeError()?.message)
        assertNull(holder.consumeError())
        // 引擎对象仍可用：播放中错误不得永久锁 Error，后续 play/resume 必须可重试
        assertEquals(TtsAvailability.Ready, holder.availability)
        assertEquals(TtsPlayResult.Accepted, holder.gatePlay())
    }

    @Test
    fun `engine error does not lock playback and normal play transitions continue`() {
        val holder = TtsStatusHolder()
        holder.onInitSuccess()
        holder.markPlaying()
        holder.onEngineError("播放出错")
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals("播放出错", holder.consumeError()?.message)

        // 错误后再次正常 play：Playing → Paused → Playing → Idle 全部可用
        holder.markPlaying()
        holder.markPaused()
        holder.markPlaying()
        holder.markIdle()
        assertEquals(TtsPlayback.Idle, holder.playback)
        assertEquals(TtsAvailability.Ready, holder.availability)
        // 仅播放错误那一次发布错误
        assertEquals(1, holder.errorSeq)
        assertNull(holder.consumeError())
    }

    @Test
    fun `init failure stays unavailable until explicit reinitialize`() {
        val holder = TtsStatusHolder()
        holder.onInitFailure("系统语音引擎初始化失败")
        assertEquals(TtsAvailability.Unavailable, holder.availability)
        assertTrue(holder.gatePlay() is TtsPlayResult.Rejected)

        // 显式/生命周期重初始化：回到 Initializing，等待新的 init 回调
        holder.reinitialize()
        assertEquals(TtsAvailability.Initializing, holder.availability)
        assertTrue(holder.gatePlay() is TtsPlayResult.Rejected)
        assertEquals(0, holder.errorSeq) // 被动失败 + 重初始化本身都不发布新错误

        holder.onInitSuccess()
        assertEquals(TtsAvailability.Ready, holder.availability)
        assertEquals(TtsPlayResult.Accepted, holder.gatePlay())
        assertEquals(0, holder.errorSeq)
    }

    @Test
    fun `error slot keeps only the latest pending error`() {
        val holder = TtsStatusHolder()
        holder.notifyUnavailable("first")
        holder.onEngineError("second")
        assertEquals("second", holder.consumeError()?.message)
        assertNull(holder.consumeError())
    }
}
