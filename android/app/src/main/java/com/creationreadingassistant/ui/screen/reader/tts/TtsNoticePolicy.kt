package com.creationreadingassistant.ui.screen.reader.tts

import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId

/** openTts 失败 / 初始化失败后的提示决策。 */
internal sealed interface TtsNoticeAction {
    /** 静默：不打扰用户（被动 init failure）。 */
    data object None : TtsNoticeAction

    /** 引擎不可用：发布一次「去设置」错误并重新初始化引擎（恰好提示一次）。 */
    data object ReinitializeWithNotice : TtsNoticeAction

    /** 直接显示一条消息（无去设置动作）。 */
    data class ShowMessage(val message: String) : TtsNoticeAction
}

/**
 * TTS 提示策略（纯决策，JVM 可测）。
 *
 * - 被动 onInit failure（进书即初始化失败）：静默，状态置 Unavailable，不弹提示；
 * - 用户点击听书/openTts 且引擎不可用：恰好提示一次（保留「去设置」动作）并 reinitialize；
 * - 未就绪（Initializing）：显示可解释原因；播放中主动错误不被吞（经 errorSeq 发布）；
 * - 系统引擎不可用（ROM 缺失/损坏 TTS 引擎，HyperOS 常见）：自动改用神经语音重试一次，
 *   打破「错误态够不到引擎选择器」的死锁（见 [autoSwitchEngineOnUnavailable]）。
 */
internal object TtsNoticePolicy {
    /** 被动初始化失败：静默。 */
    fun onInitFailure(): TtsNoticeAction = TtsNoticeAction.None

    /** openTts 被拒（未 Ready）后的提示决策。 */
    fun onOpenTtsRejected(availability: TtsAvailability): TtsNoticeAction = when (availability) {
        TtsAvailability.Unavailable -> TtsNoticeAction.ReinitializeWithNotice
        TtsAvailability.Initializing ->
            TtsNoticeAction.ShowMessage("语音引擎正在初始化，请稍后再试。")
        else ->
            TtsNoticeAction.ShowMessage("系统语音引擎不可用，请到系统设置检查文字转语音。")
    }

    /**
     * 自动切换决策：当前引擎不可用时是否应改用另一引擎重试。
     * - SYSTEM 不可用 → 切 EDGE（云端合成，不依赖系统 TTS 引擎）；
     * - EDGE 不可用 → 不切换（EDGE 失败已自动回退 SYSTEM，两端都坏时避免来回循环）。
     */
    fun autoSwitchEngineOnUnavailable(current: TtsEngineId): TtsEngineId? = when (current) {
        TtsEngineId.SYSTEM -> TtsEngineId.EDGE
        TtsEngineId.EDGE -> null
    }

    /** 自动切换的准入：仅「引擎已判定不可用」这一种拒绝形态触发，初始化中不抢跑。 */
    fun shouldAutoSwitchOnReject(availability: TtsAvailability): Boolean =
        availability == TtsAvailability.Unavailable
}
