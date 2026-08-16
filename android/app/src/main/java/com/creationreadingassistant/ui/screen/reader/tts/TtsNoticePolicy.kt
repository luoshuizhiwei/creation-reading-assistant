package com.creationreadingassistant.ui.screen.reader.tts

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
 * - 未就绪（Initializing）：显示可解释原因；播放中主动错误不被吞（经 errorSeq 发布）。
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
}
