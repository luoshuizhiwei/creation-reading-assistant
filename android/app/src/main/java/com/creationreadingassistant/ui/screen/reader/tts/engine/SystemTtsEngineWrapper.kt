package com.creationreadingassistant.ui.screen.reader.tts.engine

import android.content.Context
import android.speech.tts.Voice
import com.creationreadingassistant.ui.screen.reader.tts.TtsAvailability
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.screen.reader.tts.TtsPlayResult

/**
 * 包装现有 [TtsController] 的 [TtsEngine] 兼容层（System 引擎薄包装）。
 *
 * 设计意图：保持现有 [TtsController] 所有已验证行为（MediaSession/通知、蓝牙媒体键、
 * AUDIO_BECOMING_NOISY 自动暂停、定时停止、句级高亮、跨章续读持久化）原封不动，
 * 仅把外部交互通过 [TtsEngine] 接口暴露。
 *
 * 后续渐进式重构时，将内部委托的 [delegate] 逐步从 `TtsController` 替换成直接的
 * TextToSpeech + ExoPlayer/MediaPlayer 双通道分发，再最终接入 Edge WebSocket 流。
 */
internal class SystemTtsEngineWrapper(
    context: Context,
) : TtsEngine {

    override val engineId = TtsEngineId.SYSTEM

    /** 真正承担播放任务的现有控制器（P0-R2/R3 验证过的"金标准"行为）。 */
    val delegate: TtsController = TtsController(context)

    override val availability: TtsAvailability get() = delegate.availability
    override val errorSeq: Int get() = delegate.errorSeq
    override fun consumeError(): String? = delegate.consumeError()?.message
    override fun notifyUnavailable() = delegate.notifyUnavailable()

    override val voices: List<TtsVoiceDescriptor> by lazy {
        // 系统 Voice → TtsVoiceDescriptor 映射。注意系统 Voice 不保证 gender/quality 字段，
        // 一律标注 unknown；UI 只展示 name + locale，不依赖 gender。
        val raw: List<Voice> = runCatching { delegate.availableVoices }.getOrDefault(emptyList())
        raw.mapNotNull { v ->
            val loc = v.locale?.toLanguageTag() ?: runCatching { v.locale.toString() }.getOrDefault("")
            if (loc.isBlank()) return@mapNotNull null
            TtsVoiceDescriptor(
                id = v.name,
                name = v.name,
                locale = loc,
                gender = "unknown",
                engine = TtsEngineId.SYSTEM,
            )
        }
    }

    override fun play(
        text: String,
        bookTitle: String,
        chapterLabel: String,
        startOffset: Int,
        onSentence: (Int, Int) -> Unit,
        onFinished: () -> Unit,
    ): TtsPlayResult {
        delegate.onSentence = onSentence
        delegate.onFinished = onFinished
        return delegate.play(text, bookTitle, chapterLabel, startOffset)
    }

    override fun pause() = delegate.pause()
    override fun resume() = delegate.resume()
    override fun stop() = delegate.stop()
    override fun nextSentence() = delegate.next()
    override fun prevSentence() = delegate.prev()

    override fun setSpeechRate(rate: Float) { delegate.rate = rate; if (delegate.status == "playing") delegate.resume() }
    override fun setPitch(pitch: Float) = delegate.updatePitch(pitch)
    override fun setVolume(volume: Float) = delegate.updateVolume(volume)
    override fun setVoice(voiceId: String) = delegate.updateVoiceId(voiceId)
    override fun setTimedStop(minutes: Int) = delegate.setTimedStop(minutes)

    override val playbackStatus: String get() = delegate.status
    override val progressPercent: Float get() = delegate.progressPercent
    override val currentSentenceRange: Pair<Int, Int> get() = delegate.currentSentenceRange

    override fun close() = delegate.release()
}
