package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * TTS 引擎可用性。调用方只消费 [TtsStatus]，无需了解 TextToSpeech 初始化时序。
 */
sealed interface TtsAvailability {
    /** 引擎仍在初始化（TextToSpeech 初始化回调尚未返回）。 */
    data object Initializing : TtsAvailability

    /** 引擎可用，可以开始朗读。 */
    data object Ready : TtsAvailability

    /** 初始化失败 / 引擎缺失，不可用。 */
    data object Unavailable : TtsAvailability

    /** 播放过程中引擎出错。 */
    data class Error(val message: String) : TtsAvailability
}

/** TTS 播放状态（与既有 status 字符串语义一一对应）。 */
enum class TtsPlayback { Idle, Playing, Paused }

/** [play] 请求的结果：未就绪时必须拒绝并给出可解释原因，绝不进入 Playing。 */
sealed interface TtsPlayResult {
    data object Accepted : TtsPlayResult
    data class Rejected(val reason: String) : TtsPlayResult
}

/** 一次用户可消费的 TTS 错误。 */
data class TtsFailure(val message: String)

/**
 * 可观察的 TTS availability/playback 状态接口。
 *
 * 实现保证：
 * - 未 Ready 时 [gatePlay] 返回 [TtsPlayResult.Rejected] 且播放态保持不变；
 * - 每次引擎错误通过 [errorSeq] 递增发布一次，且只能被 [consumeError] 消费一次。
 */
interface TtsStatus {
    val availability: TtsAvailability
    val playback: TtsPlayback
    /** 已发布但可能尚未被消费的错误序列号；每次错误 +1。 */
    val errorSeq: Int
    /** 消费并清空当前错误；无错误返回 null。 */
    fun consumeError(): TtsFailure?
}

/**
 * 纯 Kotlin 状态持有者：TTS 可用性 / 播放态 / 错误队列的唯一真源。
 * JVM 单测直接覆盖；[TtsController] 仅做薄转发。
 */
internal class TtsStatusHolder : TtsStatus {
    override var availability: TtsAvailability by mutableStateOf(TtsAvailability.Initializing)
        private set
    override var playback: TtsPlayback by mutableStateOf(TtsPlayback.Idle)
        private set
    override var errorSeq: Int by mutableIntStateOf(0)
        private set
    private var pendingError: TtsFailure? = null

    /** TextToSpeech 初始化成功（SUCCESS）。 */
    fun onInitSuccess() {
        availability = TtsAvailability.Ready
    }

    /**
     * TextToSpeech 初始化失败（被动：进书即初始化，用户未主动操作）：进入 Unavailable，
     * 但不发布用户提示；是否提示由 [TtsNoticePolicy] 在用户主动 openTts 时决定。
     */
    fun onInitFailure(message: String) {
        availability = TtsAvailability.Unavailable
    }

    /**
     * 用户主动操作（点击听书/openTts）发现引擎不可用：发布一次可消费错误
     * （snackbar 带去设置动作）。availability 保持 Unavailable。
     */
    fun notifyUnavailable(message: String) {
        publishError(message)
    }

    /**
     * 播放中引擎错误：停止播放态并发布一次用户可消费错误。
     * 引擎对象仍可用（区别于 init failure），立即回到 [TtsAvailability.Ready]，
     * 后续 play/resume 必须可重试，不得永久锁在 Error。
     */
    fun onEngineError(message: String) {
        availability = TtsAvailability.Ready
        playback = TtsPlayback.Idle
        publishError(message)
    }

    /**
     * 显式/生命周期重新初始化：init failure 或无引擎后回到 [TtsAvailability.Initializing]，
     * 等待新的 TextToSpeech init 回调（成功 → Ready，失败 → Unavailable）。
     * 不发布新错误；播放态重置为 Idle。
     */
    fun reinitialize() {
        availability = TtsAvailability.Initializing
        playback = TtsPlayback.Idle
    }

    fun markPlaying() {
        playback = TtsPlayback.Playing
    }

    fun markPaused() {
        playback = TtsPlayback.Paused
    }

    fun markIdle() {
        playback = TtsPlayback.Idle
    }

    /** 发布一次用户可消费错误（单槽：仅保留最近一次；[consumeError] 消费后清空）。 */
    fun publishError(message: String) {
        pendingError = TtsFailure(message)
        errorSeq += 1
    }

    /**
     * 播放门控：仅 Ready 放行；未就绪返回可解释的拒绝原因且不改动播放态。
     * 播放态由引擎 onStart 回调驱动（[markPlaying]），此处不提前翻转。
     */
    fun gatePlay(): TtsPlayResult = when (availability) {
        is TtsAvailability.Ready -> TtsPlayResult.Accepted
        is TtsAvailability.Initializing ->
            TtsPlayResult.Rejected("语音引擎正在初始化，请稍后再试。")
        is TtsAvailability.Unavailable, is TtsAvailability.Error ->
            TtsPlayResult.Rejected("系统语音引擎不可用，请到系统设置检查文字转语音。")
    }

    override fun consumeError(): TtsFailure? = pendingError.also { pendingError = null }
}
