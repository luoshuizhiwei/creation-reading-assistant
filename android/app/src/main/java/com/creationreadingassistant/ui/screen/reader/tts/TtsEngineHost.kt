package com.creationreadingassistant.ui.screen.reader.tts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngine
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineProvider
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsVoiceDescriptor

/**
 * TTS 引擎宿主（P2：Edge 真实接入）。
 *
 * 对外暴露与 [TtsController] 完全一致的成员面（ReaderScreen / effects / TtsBar /
 * openTts 均按此编程），内部把播放通道委托给 [TtsEngineProvider] 当前选中的引擎：
 * - [TtsEngineId.SYSTEM] → SystemTtsEngineWrapper（包装既有 TtsController「金标准」行为：
 *   MediaSession 通知、蓝牙媒体键、拔耳机暂停、定时停止全部原样保留）；
 * - [TtsEngineId.EDGE] → EdgeTtsEngine（合成失败自动回退 System，会话级 fallback 锁）。
 *
 * 回调语义保真：TtsController 的 onSentence / onFinished 是可在播放中随时重赋的 var
 * （TtsResumeEffect 切书即重绑）；引擎契约把回调作为 play() 参数，宿主用稳定 trampoline
 * 转发，保证播放中重赋回调立即生效——与旧 var 语义一致。
 *
 * 播放参数（rate/pitch/volume/voiceId/timedStopMinutes）在宿主持有镜像值，写操作同步
 * 推给当前引擎；切换引擎时全部重放到新引擎，设置无感迁移。
 */
internal class TtsEngineHost(private val appContext: Context, initialEngineId: TtsEngineId) : TtsStatus {

    /** 当前引擎 ID（Compose 可观察，TtsBar 显示用）。 */
    var engineId: TtsEngineId by mutableStateOf(initialEngineId)
        private set

    private val lock = Any()

    private var engine: TtsEngine = createEngine(initialEngineId)

    // ── 参数镜像（引擎契约不暴露读端） ────────────────────────
    var rate: Float = 1f
        set(value) {
            field = value
            engine.setSpeechRate(value)
        }
    var pitch: Float = 1f
        set(value) {
            field = value
            engine.setPitch(value)
        }
    var volume: Float = 1f
        set(value) {
            field = value
            engine.setVolume(value)
        }
    var voiceId: String = ""
        set(value) {
            field = value
            engine.setVoice(value)
        }
    var timedStopMinutes: Int = 0
        private set

    // ── 回调 trampoline（播放中重赋立即生效） ─────────────────
    var onSentence: ((offset: Int, end: Int) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    // ── TtsStatus 透传 ───────────────────────────────────────
    override val availability: TtsAvailability get() = engine.availability
    override val playback: TtsPlayback get() = engine.availability.toPlaybackFallback()
    override val errorSeq: Int get() = engine.errorSeq
    override fun consumeError(): TtsFailure? = engine.consumeError()?.let { TtsFailure(it) }

    /** 兼容旧读取方：引擎是否已就绪。 */
    val isReady: Boolean get() = availability is TtsAvailability.Ready

    /** 兼容旧读取方：idle / playing / paused。 */
    val status: String get() = engine.playbackStatus

    val progressPercent: Float get() = engine.progressPercent

    /** 当前朗读句在播放文本中的 [start, end) 偏移。 */
    var currentSentenceRange: Pair<Int, Int>
        get() = engine.currentSentenceRange
        private set(_) {}

    /** 当前引擎支持的音色（引擎选择后 UI 过滤展示）。 */
    val availableVoices: List<TtsVoiceDescriptor> get() = engine.voices

    fun notifyUnavailable() = engine.notifyUnavailable()

    fun play(text: String, bookTitle: String = "", chapterLabel: String = "朗读", startOffset: Int = 0): TtsPlayResult =
        engine.play(
            text = text,
            bookTitle = bookTitle,
            chapterLabel = chapterLabel,
            startOffset = startOffset,
            onSentence = { s, e -> onSentence?.invoke(s, e) },
            onFinished = { onFinished?.invoke() },
        )

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun stop() = engine.stop()

    fun next() = engine.nextSentence()

    fun prev() = engine.prevSentence()

    fun setTimedStop(min: Int) {
        timedStopMinutes = min
        engine.setTimedStop(min)
    }

    /** 实时调节音量：立即作用（不等到下一句）。 */
    fun updateVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    /** 实时调节音调：播放中当前句立即以新音调重读（引擎契约保证）。 */
    fun updatePitch(v: Float) {
        pitch = v.coerceIn(0.5f, 2f)
    }

    /** 切换音色：播放中当前句立即重读（引擎契约保证）。 */
    fun updateVoiceId(id: String) {
        voiceId = id
    }

    fun reinitialize() = engine.reinitialize()

    /**
     * 切换引擎：停掉当前播放、释放旧引擎实例（解锁 Edge 的会话级 fallback 锁）、
     * 创建/复用新引擎，并把宿主镜像参数全部重放过去。
     */
    fun switchEngine(target: TtsEngineId) {
        if (target == engineId) return
        synchronized(lock) {
            runCatching { engine.stop() }
            TtsEngineProvider.release(engineId)
            engine = createEngine(target)
            engineId = target
        }
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        engine.setVolume(volume)
        engine.setVoice(voiceId)
        engine.setTimedStop(timedStopMinutes)
        AppLog.debug("TtsHost", "switched engine -> ${target.key}")
    }

    fun release() {
        synchronized(lock) {
            runCatching { engine.stop() }
            TtsEngineProvider.release(engineId)
        }
    }

    private fun createEngine(id: TtsEngineId): TtsEngine = TtsEngineProvider.get(appContext, id)

    /**
     * TtsStatus.playback 需要 idle/playing/paused 枚举；引擎侧以 playbackStatus 字符串
     * 表达（兼容旧读取方），这里做一次映射。宿主的响应式消费方（TtsBar）实际读
     * [status] 字符串，此映射仅为满足接口。
     */
    private fun TtsAvailability.toPlaybackFallback(): TtsPlayback = when (engine.playbackStatus) {
        "playing" -> TtsPlayback.Playing
        "paused" -> TtsPlayback.Paused
        else -> TtsPlayback.Idle
    }
}
