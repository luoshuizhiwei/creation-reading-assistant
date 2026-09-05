package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

/**
 * Edge TTS 专属播放器：逐句 MP3 顺序播放 + 音频焦点管理 + 句间回调。
 *
 * 所有控制方法均线程安全（通过 [LinkedBlockingQueue] 串行化到内部单线程事件循环）。
 */
internal class EdgeTtsPlayer(
    private val context: Context,
) : AutoCloseable {

    enum class Command { PLAY, PAUSE, RESUME, STOP, NEXT, PREV }

    data class PlaylistItem(
        val file: File,
        /** 原文本中的 [start, endExclusive)，供外层高亮。 */
        val rangeStart: Int,
        val rangeEndExclusive: Int,
    )

    // 外部可观察回调
    @Volatile var onSentenceStart: ((start: Int, endExclusive: Int) -> Unit)? = null
    @Volatile var onAllFinished: (() -> Unit)? = null
    @Volatile var onPlaybackStatusChanged: ((status: String) -> Unit)? = null  // idle / playing / paused
    @Volatile var onError: ((msg: String) -> Unit)? = null

    private val cmdQueue = LinkedBlockingQueue<Pair<Command, Any?>>()

    private var playlist: List<PlaylistItem> = emptyList()
    private var cursor: Int = 0
    private var player: MediaPlayer? = null
    private var pausedOnPrepared: Boolean = false

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var focusRequest: AudioFocusRequest? = null
    private var focusGainLegacy: Boolean = false
    private var wasPlayingBeforeFocusLoss: Boolean = false

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = false
                sendCmd(Command.PAUSE, true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = (getCurrentStatus() == "playing")
                sendCmd(Command.PAUSE, false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    sendCmd(Command.RESUME)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂提示音：保持播放，稍微降一点音量（MediaPlayer 自动 duck 需要 SDK 26+）
                player?.setVolume(0.5f, 0.5f)
            }
        }
    }

    suspend fun start(playlist: List<PlaylistItem>, startIndex: Int = 0) = withContext(Dispatchers.IO) {
        this@EdgeTtsPlayer.playlist = playlist
        this@EdgeTtsPlayer.cursor = startIndex.coerceIn(0, playlist.size)
        sendCmd(Command.PLAY)
    }

    fun pause() = sendCmd(Command.PAUSE)
    fun resume() = sendCmd(Command.RESUME)
    fun stop() = sendCmd(Command.STOP)
    fun next() = sendCmd(Command.NEXT)
    fun prev() = sendCmd(Command.PREV)

    /** 当前句进度 0f..1f（MediaPlayer currentPosition / duration）。 */
    fun currentSentenceProgress(): Float {
        val p = player ?: return 0f
        val d = runCatching { p.duration }.getOrDefault(0)
        if (d <= 0) return 0f
        val c = runCatching { p.currentPosition }.getOrDefault(0)
        return (c.toFloat() / d.toFloat()).coerceIn(0f, 1f)
    }

    fun getCurrentStatus(): String = _playbackStatus
    fun currentSentenceRange(): Pair<Int, Int> {
        val it = playlist.getOrNull(cursor) ?: return (0 to 0)
        return it.rangeStart to it.rangeEndExclusive
    }
    fun progressPercent(totalChars: Int): Float {
        val r = currentSentenceRange()
        val base = if (totalChars > 0) (r.first.toFloat() / totalChars.toFloat() * 100f) else 0f
        val span = (r.second - r.first).toFloat()
        val within = currentSentenceProgress()
        return if (totalChars > 0) (base + span / totalChars.toFloat() * 100f * within).coerceIn(0f, 100f) else 0f
    }

    // ── 事件循环 ─────────────────────────────────────────────

    private var loopStarted = false

    private fun sendCmd(cmd: Command, extra: Any? = null) {
        ensureLoopStarted()
        // LinkedBlockingQueue 无界（Integer.MAX_VALUE），offer 永不阻塞/永不失败
        cmdQueue.offer(cmd to extra)
    }

    @Volatile
    private var _playbackStatus: String = "idle"

    private fun setStatus(s: String) {
        if (_playbackStatus != s) {
            _playbackStatus = s
            onPlaybackStatusChanged?.invoke(s)
        }
    }

    private fun ensureLoopStarted() {
        if (loopStarted) return
        synchronized(this) {
            if (loopStarted) return
            loopStarted = true
            // 非协程：用专用线程跑事件循环；播放器 API 全是阻塞的
            Thread({ runLoop() }, "EdgeTtsPlayer-Loop").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun runLoop() {
        while (true) {
            val raw = try {
                cmdQueue.take() // 阻塞直到有命令
            } catch (ie: InterruptedException) {
                return
            }
            val cmd = raw.first
            val extra = raw.second
            try {
                when (cmd) {
                    Command.PLAY -> handlePlay()
                    Command.PAUSE -> handlePause(permanent = extra as? Boolean ?: false)
                    Command.RESUME -> handleResume()
                    Command.STOP -> handleStop()
                    Command.NEXT -> handleNext()
                    Command.PREV -> handlePrev()
                }
            } catch (t: Throwable) {
                onError?.invoke("播放器异常：${t.message ?: t.javaClass.simpleName}")
                handleStop()
            }
        }
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setAcceptsDelayedFocusGain(true)
                    setOnAudioFocusChangeListener(afChangeListener)
                    build()
                }
            }
            val r = audioManager.requestAudioFocus(focusRequest!!)
            r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val r = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            focusGainLegacy = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            focusGainLegacy
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            if (focusGainLegacy) {
                @Suppress("DEPRECATION")
                runCatching { audioManager.abandonAudioFocus(afChangeListener) }
                focusGainLegacy = false
            }
        }
    }

    private fun handlePlay() {
        if (playlist.isEmpty()) {
            setStatus("idle")
            onAllFinished?.invoke()
            return
        }
        if (!requestFocus()) {
            onError?.invoke("无法获取音频焦点，可能被其他应用占用。")
            return
        }
        playCurrent()
    }

    private fun playCurrent() {
        val item = playlist.getOrNull(cursor) ?: run {
            // 读完了
            releasePlayer()
            setStatus("idle")
            abandonFocus()
            onAllFinished?.invoke()
            return
        }
        setStatus("playing")
        onSentenceStart?.invoke(item.rangeStart, item.rangeEndExclusive)
        try {
            releasePlayer()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(item.file.absolutePath)
                setOnCompletionListener {
                    // 自动下一句
                    cursor += 1
                    playCurrent()
                }
                setOnErrorListener { _, what, extra ->
                    onError?.invoke("播放失败（what=$what, extra=$extra）")
                    handleStop()
                    true
                }
                setVolume(1f, 1f)
                prepare()
                if (pausedOnPrepared) {
                    pause()
                } else {
                    start()
                }
            }
        } catch (t: Throwable) {
            onError?.invoke("读取音频失败：${t.message}")
            handleStop()
        }
    }

    private fun handlePause(permanent: Boolean) {
        val p = player ?: run {
            pausedOnPrepared = true
            setStatus("paused")
            return
        }
        if (p.isPlaying) {
            runCatching { p.pause() }
        }
        setStatus("paused")
        if (permanent) abandonFocus()
    }

    private fun handleResume() {
        val p = player
        if (p == null) {
            pausedOnPrepared = false
            // 尚未起播：从头 playCurrent
            if (requestFocus()) playCurrent()
            return
        }
        if (!p.isPlaying) {
            if (requestFocus()) runCatching { p.start() }
        }
        setStatus("playing")
    }

    private fun handleStop() {
        releasePlayer()
        cursor = 0
        playlist = emptyList()
        pausedOnPrepared = false
        setStatus("idle")
        abandonFocus()
    }

    private fun handleNext() {
        if (playlist.isEmpty()) return
        cursor = (cursor + 1).coerceAtMost(playlist.size) // 超过则 playCurrent 判空走 finished
        playCurrent()
    }

    private fun handlePrev() {
        if (playlist.isEmpty()) return
        cursor = (cursor - 1).coerceAtLeast(0)
        playCurrent()
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.reset() }
            runCatching { p.release() }
        }
        player = null
    }

    override fun close() {
        handleStop()
    }
}
