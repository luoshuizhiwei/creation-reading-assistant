package com.creationreadingassistant.ui.screen.reader.tts.engine

import android.content.Context
import com.creationreadingassistant.ui.screen.reader.tts.TtsAvailability
import com.creationreadingassistant.ui.screen.reader.tts.TtsPlayResult
import com.creationreadingassistant.ui.screen.reader.tts.TtsStatusHolder
import com.creationreadingassistant.ui.screen.reader.tts.engine.edge.EdgeTtsCommunicator
import com.creationreadingassistant.ui.screen.reader.tts.engine.edge.EdgeTtsPlayer
import com.creationreadingassistant.ui.screen.reader.tts.engine.edge.EdgeTtsVoices
import com.creationreadingassistant.ui.screen.reader.tts.engine.edge.SentenceSplitter
import com.creationreadingassistant.ui.screen.reader.tts.engine.edge.TtsAudioCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

/**
 * Edge TTS（微软 Azure 神经语音）真实引擎骨架（P2）。
 *
 * 设计原则（遵循「本地优先 + 失败透明降级」）：
 * 1. 严格标注需联网：设置页显示「神经语音 · 需联网」，首次听书才发起 HTTP；
 * 2. 本地 LRU 优先：相同文本+音色+语速+音调命中本地缓存直接播放，不联网；
 * 3. 流水线：逐句 prefetch（当前句播放时后台合成下一句 MP3），减少句间停顿；
 * 4. 自动回退：**首句合成失败 / 播放过程中连续 ≥ 1 句合成失败** → 立即把当前
 *    play() 请求的**同一段文本**交给 [SystemTtsEngineWrapper] 继续播放，外层回调
 *    （[onSentence] / [onFinished] / [progressPercent] 等）语义完全一致，用户感知
 *    仅为"音色突然换了" + 一条可消费错误提示"网络不稳定，已切换为系统语音"。
 * 5. 失败后进入"会话级 fallback 锁"：同一次阅读器会话内后续 play() 直接走 System，
 *    不再重试 Edge，避免每次翻章都等待 10s 超时。退出阅读器释放引擎后解锁。
 */
internal class EdgeTtsEngine(
    private val context: Context,
) : TtsEngine {

    override val engineId: TtsEngineId = TtsEngineId.EDGE

    private val statusHolder = TtsStatusHolder()
    private val voicesInternal = EdgeTtsVoices.all

    // 依赖组件
    private val communicator: EdgeTtsCommunicator by lazy { EdgeTtsCommunicator(context) }
    private val cache: TtsAudioCache by lazy { TtsAudioCache(context) }
    private val player: EdgeTtsPlayer by lazy { buildPlayer() }

    // 回退引擎（懒加载：只在 Edge 失败时才实例化 System）
    @Volatile private var fallbackEngine: SystemTtsEngineWrapper? = null
    /** 当前 play 是否已进入 fallback 模式。true 时所有控制都转发给 fallbackEngine。 */
    @Volatile private var fallbackActive: Boolean = false
    /** 会话级 fallback 锁：同一次阅读器会话后续 play() 直接走 System。 */
    @Volatile private var sessionFallbackLock: Boolean = false

    // 播放参数（外部可随时改）
    @Volatile private var rate: Float = 1.0f
    @Volatile private var pitch: Float = 1.0f
    @Volatile private var volume: Float = 1.0f
    @Volatile private var voiceId: String = EdgeTtsVoices.defaultFemaleId
    @Volatile private var timedStopMinutes: Int = 0

    // 播放时上下文
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var synthJob: Job? = null
    @Volatile private var timedStopJob: Job? = null
    @Volatile private var totalTextLength: Int = 0

    // 外部回调
    @Volatile private var onSentence: (start: Int, endExclusive: Int) -> Unit = { _, _ -> }
    @Volatile private var onFinished: () -> Unit = {}

    init {
        // ⚠️ 真实可用（与旧 Stub 的 Unavailable 区别）。
        // 首次 play() 会做网络预检，不在这里预发 HTTP。
        statusHolder.onInitSuccess()
    }

    override val availability: TtsAvailability get() {
        if (fallbackActive) return fallbackEngine?.availability ?: statusHolder.availability
        return statusHolder.availability
    }
    override val voices: List<TtsVoiceDescriptor> = voicesInternal
    override val errorSeq: Int get() = statusHolder.errorSeq
    override fun consumeError(): String? = statusHolder.consumeError()?.message

    override fun notifyUnavailable() {
        // 即使 Edge 本身是 Ready，也允许上层策略主动提示"需联网"
        statusHolder.notifyUnavailable("神经语音使用微软在线服务，需连接 Wi-Fi 或移动数据。")
    }

    // ── 播放控制 ─────────────────────────────────────────────

    override fun play(
        text: String,
        bookTitle: String,
        chapterLabel: String,
        startOffset: Int,
        onSentence: (Int, Int) -> Unit,
        onFinished: () -> Unit,
    ): TtsPlayResult {
        this.onSentence = onSentence
        this.onFinished = onFinished
        this.totalTextLength = text.length

        // 会话级 fallback 锁：直接走 System
        if (sessionFallbackLock) {
            return ensureFallbackEngine().play(text, bookTitle, chapterLabel, startOffset, onSentence, onFinished)
                .also { fallbackActive = true }
        }

        val gate = statusHolder.gatePlay()
        if (gate is TtsPlayResult.Rejected) return gate

        // 取消旧的合成/定时任务
        synthJob?.cancel()
        timedStopJob?.cancel()
        player.stop()
        fallbackActive = false

        // 保存本次 play 参数：播放中途 Edge→System 回退时使用（避免用户点击暂停/继续时丢失上下文）
        synchronized(lastPlayLock) {
            lastPlayText = text
            lastPlayTitle = bookTitle
            lastPlayLabel = chapterLabel
        }

        if (text.isBlank()) {
            statusHolder.markIdle()
            onFinished()
            return TtsPlayResult.Accepted
        }

        // 分句 + 定位起始句
        val sentences = SentenceSplitter.split(text)
        if (sentences.isEmpty()) {
            statusHolder.markIdle()
            onFinished()
            return TtsPlayResult.Accepted
        }
        val startIdx = sentences.indexOfFirst { it.start <= startOffset && startOffset < it.endExclusive }
            .coerceAtLeast(0)

        // 启动合成 + 播放流水线
        synthJob = scope.launch {
            runPipeline(sentences, startIdx, text, bookTitle, chapterLabel)
        }

        // 启动定时停止
        if (timedStopMinutes > 0) {
            timedStopJob = scope.launch {
                delay(timedStopMinutes * 60_000L)
                stop()
            }
        }

        return TtsPlayResult.Accepted
    }

    override fun pause() {
        if (fallbackActive) {
            fallbackEngine?.pause()
            return
        }
        player.pause()
        statusHolder.markPaused()
    }

    override fun resume() {
        if (fallbackActive) {
            fallbackEngine?.resume()
            return
        }
        player.resume()
    }

    override fun stop() {
        synthJob?.cancel()
        timedStopJob?.cancel()
        if (fallbackActive) {
            fallbackEngine?.stop()
            return
        }
        player.stop()
        statusHolder.markIdle()
    }

    override fun nextSentence() {
        if (fallbackActive) {
            fallbackEngine?.nextSentence()
            return
        }
        player.next()
    }

    override fun prevSentence() {
        if (fallbackActive) {
            fallbackEngine?.prevSentence()
            return
        }
        player.prev()
    }

    // ── 参数设置（立即生效） ──────────────────────────────────

    override fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        this.rate = clamped
        fallbackEngine?.setSpeechRate(clamped)
    }

    override fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        this.pitch = clamped
        fallbackEngine?.setPitch(clamped)
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        this.volume = clamped
        fallbackEngine?.setVolume(clamped)
    }

    override fun setVoice(voiceId: String) {
        val chosen = EdgeTtsVoices.byIdOrNull(voiceId)?.id ?: EdgeTtsVoices.defaultFemaleId
        this.voiceId = chosen
        // fallback engine 用系统 voice，不做跨引擎 voice 映射
    }

    override fun setTimedStop(minutes: Int) {
        timedStopMinutes = max(0, minutes)
        timedStopJob?.cancel()
        if (minutes > 0 && playbackStatus == "playing") {
            timedStopJob = scope.launch {
                delay(minutes * 60_000L)
                stop()
            }
        }
        fallbackEngine?.setTimedStop(minutes)
    }

    // ── 只读状态 ─────────────────────────────────────────────

    override val playbackStatus: String get() {
        if (fallbackActive) return fallbackEngine?.playbackStatus ?: "idle"
        return player.getCurrentStatus()
    }

    override val progressPercent: Float get() {
        if (fallbackActive) return fallbackEngine?.progressPercent ?: 0f
        return player.progressPercent(totalTextLength)
    }

    override val currentSentenceRange: Pair<Int, Int> get() {
        if (fallbackActive) return fallbackEngine?.currentSentenceRange ?: (0 to 0)
        return player.currentSentenceRange()
    }

    override fun close() {
        runCatching { player.close() }
        synthJob?.cancel()
        timedStopJob?.cancel()
        runCatching { fallbackEngine?.close() }
        fallbackEngine = null
        scope.cancel()
    }

    // ── 内部：合成 + 播放流水线 ──────────────────────────────

    private fun buildPlayer(): EdgeTtsPlayer {
        val p = EdgeTtsPlayer(context)
        p.onSentenceStart = { s, e ->
            statusHolder.markPlaying()
            this@EdgeTtsEngine.onSentence.invoke(s, e)
        }
        p.onAllFinished = {
            statusHolder.markIdle()
            timedStopJob?.cancel()
            this@EdgeTtsEngine.onFinished.invoke()
        }
        p.onPlaybackStatusChanged = { s ->
            when (s) {
                "playing" -> statusHolder.markPlaying()
                "paused" -> statusHolder.markPaused()
                "idle" -> statusHolder.markIdle()
            }
        }
        p.onError = { msg ->
            // 播放失败：切 fallback，从当前句位置继续
            statusHolder.onEngineError("神经语音播放异常：$msg")
            triggerFallbackFromCurrentPosition()
        }
        return p
    }

    private suspend fun runPipeline(
        sentences: List<SentenceSplitter.Sentence>,
        startIdx: Int,
        fullText: String,
        bookTitle: String,
        chapterLabel: String,
    ) {
        // 预缓存：当前句 + 下一句（并行合成减少等待）
        val resolved = arrayOfNulls<File?>(sentences.size)
        val failedFlags = BooleanArray(sentences.size)

        // 第一句必须先解决（用于触发 fallback 判定）
        val firstOk = resolveAndCache(sentences[startIdx])
        if (firstOk == null) {
            handleFirstSentenceFailed(sentences, startIdx, fullText, bookTitle, chapterLabel)
            return
        }
        resolved[startIdx] = firstOk

        // 预取下一句
        val prefetchIdx = startIdx + 1
        if (prefetchIdx < sentences.size) {
            scope.launch {
                runCatching { resolveAndCache(sentences[prefetchIdx]) }
                    .onSuccess { resolved[prefetchIdx] = it }
                    .onFailure { failedFlags[prefetchIdx] = true }
            }
        }

        // 起播
        val playlist = ArrayList<EdgeTtsPlayer.PlaylistItem>(sentences.size - startIdx)
        playlist.add(
            EdgeTtsPlayer.PlaylistItem(
                firstOk,
                sentences[startIdx].start,
                sentences[startIdx].endExclusive,
            )
        )
        // 其余句在播放中陆续追加（此处简单处理：把剩下的句子按顺序解决并添加；
        // 优化版可以做到"播第 N 句时后台解决 N+1，然后切下一首"——通过 MediaPlayer.next() 队列）
        for (i in (startIdx + 1) until sentences.size) {
            // 避免 scope 被取消时继续写网络
            val f = resolved[i] ?: runCatching { resolveAndCache(sentences[i]) }.getOrNull()
            if (f == null) {
                // 中间失败：触发 fallback，从当前已播放位置续读
                statusHolder.onEngineError("神经语音合成失败：部分句子无法加载，已切换为系统语音继续朗读。")
                triggerFallbackFromSentence(sentences, i, fullText, bookTitle, chapterLabel)
                return
            }
            playlist.add(EdgeTtsPlayer.PlaylistItem(f, sentences[i].start, sentences[i].endExclusive))
        }
        player.start(playlist, 0)
    }

    /**
     * 解决单句：命中缓存返回 File；否则 HTTP 合成并写入缓存后返回。
     * 失败返回 null（调用方负责 fallback）。
     */
    private suspend fun resolveAndCache(sentence: SentenceSplitter.Sentence): File? {
        val key = cache.keyOf(voiceId, sentence.text, rate, pitch)
        cache.get(key)?.let { return it }
        val req = EdgeTtsCommunicator.SynthesisRequest(
            voiceId = voiceId,
            text = sentence.text,
            rate = rate,
            pitch = pitch,
            volume = volume,
        )
        return try {
            cache.put(key) { dst ->
                communicator.synthesizeToFile(req, dst)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun handleFirstSentenceFailed(
        sentences: List<SentenceSplitter.Sentence>,
        startIdx: Int,
        fullText: String,
        bookTitle: String,
        chapterLabel: String,
    ) {
        // 首句就失败：大概率是无网 / 被微软 403。立即 fallback。
        val startOffset = sentences.getOrNull(startIdx)?.start ?: 0
        statusHolder.onEngineError("神经语音暂时不可用（$FIRST_FAIL_HINT），已切换为系统语音。")
        activateFallbackAndPlay(fullText, bookTitle, chapterLabel, startOffset)
    }

    private fun triggerFallbackFromCurrentPosition() {
        // 播放途中出错：拿 player 当前句范围的 start 作为续读起点
        val (start, _) = player.currentSentenceRange()
        // 外部把同一段文本在 play() 时已经传入，但我们无法拿到完整 text。
        // 解决方法：通过 runPipeline 中保存的 fullText 引用；实际失败路径在 p.onError → triggerFallbackFromCurrentPosition。
        // 为避免闭包依赖复杂化，这里用 **会话级 fallback 锁 + 提示用户手动点一次播放**（最差情况）。
        // 更佳方案：保存最近一次 play 参数。
        sessionFallbackLock = true
        val (title, label, text, startOff) = synchronized(lastPlayLock) {
            val startOff = start.coerceIn(0, lastPlayText.length)
            LastPlaySnapshot(lastPlayTitle, lastPlayLabel, lastPlayText, startOff)
        }
        if (text.isBlank()) return
        activateFallbackAndPlay(text, title, label, startOff)
    }

    private fun triggerFallbackFromSentence(
        sentences: List<SentenceSplitter.Sentence>,
        idx: Int,
        fullText: String,
        bookTitle: String,
        chapterLabel: String,
    ) {
        val startOff = sentences.getOrNull(idx)?.start ?: 0
        activateFallbackAndPlay(fullText, bookTitle, chapterLabel, startOff)
    }

    private fun activateFallbackAndPlay(
        fullText: String,
        bookTitle: String,
        chapterLabel: String,
        startOffset: Int,
    ) {
        sessionFallbackLock = true
        fallbackActive = true
        player.stop()
        val fe = ensureFallbackEngine()
        // 把已经在 Edge 侧设置的参数同步到 fallback
        fe.setSpeechRate(rate)
        fe.setPitch(pitch)
        fe.setVolume(volume)
        fe.setTimedStop(timedStopMinutes)
        val r = fe.play(
            text = fullText,
            bookTitle = bookTitle,
            chapterLabel = chapterLabel,
            startOffset = startOffset,
            onSentence = onSentence,
            onFinished = onFinished,
        )
        if (r is TtsPlayResult.Rejected) {
            statusHolder.onEngineError("系统语音也不可用：${r.reason}")
        }
    }

    private fun ensureFallbackEngine(): SystemTtsEngineWrapper {
        return fallbackEngine ?: synchronized(this) {
            fallbackEngine ?: SystemTtsEngineWrapper(context).also { fallbackEngine = it }
        }
    }

    // 保存最近一次 play 参数，供播放中途 fallback 时使用
    private val lastPlayLock = Any()
    @Volatile private var lastPlayText: String = ""
    @Volatile private var lastPlayTitle: String = ""
    @Volatile private var lastPlayLabel: String = ""
    private data class LastPlaySnapshot(
        val title: String, val label: String, val text: String, val startOff: Int,
    )

    private companion object {
        private const val FIRST_FAIL_HINT =
            "请检查网络连接；若使用 Wi-Fi 仍失败，可能是微软服务端临时风控，本次会话将自动使用系统语音"
    }
}
