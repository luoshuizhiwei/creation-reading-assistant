package com.creationreadingassistant.ui.screen.reader.tts.engine

import com.creationreadingassistant.ui.screen.reader.tts.TtsAvailability
import com.creationreadingassistant.ui.screen.reader.tts.TtsPlayResult

/**
 * TTS 引擎唯一标识符（P2：Edge 真实接入）。
 *
 * 「本地优先 + 失败透明降级」策略：
 * - [SYSTEM] 始终可用（用 Android 系统 TextToSpeech），为默认值；
 * - [EDGE] 微软 Azure 神经语音（晓晓/云扬等），**需联网**。合成失败（首句失败或中间连续失败）
 *   时**自动切回 [SYSTEM] 继续朗读**，同时发布一条可消费错误提示用户；
 *   同一次阅读器会话内后续 play() 直接走 System（会话级 fallback 锁，释放引擎后解锁）。
 */
enum class TtsEngineId(val displayLabel: String) {
    /** 系统内置 TextToSpeech（离线可用，随 ROM 提供中文音色）。 */
    SYSTEM("系统语音"),
    /**
     * Edge TTS（微软 Azure 神经语音，晓晓/云扬等）。真实可用，需联网。
     * 合成失败时**自动回退**到 [SYSTEM]，无需用户手动切换。
     */
    EDGE("神经语音 · 需联网"),
    ;

    /** 持久化键（与 SettingsStore.KEY_TTS_ENGINE 配对）。 */
    val key: String get() = name.lowercase()

    companion object {
        /** 从持久化键解析；未知值/空值一律回退 [SYSTEM]（本地优先）。 */
        fun fromKey(raw: String?): TtsEngineId =
            entries.firstOrNull { it.key == raw?.lowercase() } ?: SYSTEM
    }
}

/**
 * 统一音色描述：区别于系统 `android.speech.tts.Voice`，与引擎解耦。
 *
 * 系统引擎直接转发 Voice 字段；Edge 引擎将来从微软 voices API 拉取列表时映射到此结构。
 */
data class TtsVoiceDescriptor(
    val id: String,
    val name: String,
    /** 语言标签，如 "zh-CN"、"yue-HK"。 */
    val locale: String,
    /** 性别标注：male | female | unknown。 */
    val gender: String = "unknown",
    /** 所属引擎；UI 可据此过滤当前引擎下的音色。 */
    val engine: TtsEngineId,
)

/**
 * TTS 引擎统一契约。
 *
 * 实现约定：
 * 1. 构造即触发异步初始化；[availability] 为 Compose 可观察状态，变化顺序：
 *    Initializing → Ready / Unavailable →（播放途中）Ready + onEngineError 发布错误 → 恢复 Ready。
 * 2. [speak] / [pause] / [resume] / [stop] 必须可重入、可在任意线程调用；
 *    播放句回调通过 [onSentence]（句首偏移 [start, end)）和 [onFinished]（全部句子读完）上抛。
 * 3. [release] 只在阅读器会话退出时调用一次，释放资源、注销广播、关闭 MediaSession。
 * 4. 引擎内部不持久化设置：语速/音调/音量/当前音色 ID 全部由外部 [ReaderSettings] 传入，
 *    以便切换引擎时平滑迁移。
 */
interface TtsEngine : AutoCloseable {

    /** 当前引擎类型 ID；用于"设置页当前选中"与 UI 过滤音色列表。 */
    val engineId: TtsEngineId

    /** 引擎可用性（Compose 可观察）。 */
    val availability: TtsAvailability

    /** 当前引擎支持的音色列表；初始化前返回 [emptyList]。 */
    val voices: List<TtsVoiceDescriptor>

    /** 最近一次错误序列号（每次错误 +1）；供 UI 消费 snackbar。 */
    val errorSeq: Int

    /** 消费并清空最近错误；无错误返回 null。 */
    fun consumeError(): String?

    /**
     * 打开听书但引擎不可用时主动提示：由外部策略（用户点击 TTS 才触发）决定调用时机，
     * 避免被动初始化失败打扰用户。
     */
    fun notifyUnavailable()

    /**
     * 显式重新初始化：init failure / 引擎不可用后的进程内恢复路径（不要求重启 App）。
     * 默认无操作（[TtsEngineId.EDGE] 与 [TtsEngineId.SYSTEM] 各自覆写）。
     */
    fun reinitialize() {}

    // ── 播放控制 ─────────────────────────────────────────────

    /**
     * 开始朗读给定全文本，按实现内部分句逻辑切句；[startOffset] 为全书偏移（字符索引），
     * 用于用户中途切章/跨章续读时从目标句附近开始。
     *
     * @return 未就绪时返回 [TtsPlayResult.Rejected]（调用方据此弹 snackbar）。
     */
    fun play(
        text: String,
        bookTitle: String,
        chapterLabel: String,
        startOffset: Int = 0,
        onSentence: (start: Int, endExclusive: Int) -> Unit,
        onFinished: () -> Unit,
    ): TtsPlayResult

    /** 暂停：保留当前句索引，下次 [resume] 从同一句续读。 */
    fun pause()

    /** 从当前句续读（无分句时静默返回，不抛异常）。 */
    fun resume()

    /** 立即停止：清空分句队列、复位进度、隐藏媒体通知。 */
    fun stop()

    /** 跳到下一句（最后一句时无效果）。 */
    fun nextSentence()

    /** 跳回上一句（第一句时无效果）。 */
    fun prevSentence()

    // ── 播放参数（立即生效，播放中调用会打断当前句重读） ──────

    /** 语速：0.5f..2.0f，1.0f = 正常。 */
    fun setSpeechRate(rate: Float)

    /** 语调：0.5f..2.0f，1.0f = 正常。 */
    fun setPitch(pitch: Float)

    /** 音量：0f..1f，1f = 系统媒体当前音量。 */
    fun setVolume(volume: Float)

    /** 切换音色：[TtsVoiceDescriptor.id]；空字符串 = 使用引擎默认音色。 */
    fun setVoice(voiceId: String)

    /** 定时停止（分钟），0 = 关闭；播放中调用会立即重排定时。 */
    fun setTimedStop(minutes: Int)

    /**
     * 兼容旧读取方：idle / playing / paused。
     * 保持 [TtsController.status] 字符串契约避免大规模改动 TtsBar / MediaSession。
     */
    val playbackStatus: String

    /** 当前句进度 0f..100f，供 UI 进度条与"已朗读 X%"显示。 */
    val progressPercent: Float

    /** 当前朗读句的 [start, endExclusive) 字符偏移；未播放时返回 (0, 0)。 */
    val currentSentenceRange: Pair<Int, Int>
}
