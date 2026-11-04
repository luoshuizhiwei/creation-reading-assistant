package com.creationreadingassistant.ui.screen.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.creationreadingassistant.feature.log.AppLog
import java.util.Locale

/**
 * 句级 TTS 朗读控制器（复用 Android TextToSpeech + 框架 MediaSession）。
 * 按句切分朗读，暴露播放/暂停/上下句/停止/语速与进度；通过 [TtsMediaSession] 在系统媒体
 * 通知/锁屏展示 play/pause/prev/next 控制并回调本播放器。
 *
 * 句级续读（R3）：暂停记忆当前句索引，恢复从当前句续读；跨会话续读通过外部持久化句首偏移实现
 * （见 ReaderScreen 中的 ttsResumeOffset / SettingsStore.saveTtsResume）。
 */
internal class TtsController(
    context: Context,
    private val audioManager: AudioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager,
    private val createTts: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { c, listener ->
        TextToSpeech(c, listener)
    },
    createMedia: (Context, () -> Unit, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> TtsMediaSession =
        ::TtsMediaSession,
    createFocusListener: ((Int) -> Unit) -> AudioManager.OnAudioFocusChangeListener = { callback ->
        AudioManager.OnAudioFocusChangeListener { callback(it) }
    },
) : TtsStatus {
    private val appContext = context.applicationContext
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val statusHolder = TtsStatusHolder()
    var availableVoices: List<Voice> by mutableStateOf(emptyList())

    /** 引擎可用性（Compose 可观察），透传 [TtsStatusHolder]。 */
    override val availability: TtsAvailability get() = statusHolder.availability
    /** 播放态（Compose 可观察）。 */
    override val playback: TtsPlayback get() = statusHolder.playback
    override val errorSeq: Int get() = statusHolder.errorSeq
    override fun consumeError(): TtsFailure? = statusHolder.consumeError()

    /**
     * 用户主动打开听书但引擎不可用：发布一次可消费错误（snackbar 带去设置）。
     * 由 [TtsNoticePolicy] 决策调用（ReinitializeWithNotice 分支）。
     */
    fun notifyUnavailable() {
        statusHolder.notifyUnavailable(MSG_INIT_FAILED)
    }

    /** 兼容旧读取方：引擎是否已就绪。 */
    val isReady: Boolean get() = availability is TtsAvailability.Ready
    /** 兼容旧读取方：idle / playing / paused。 */
    val status: String
        get() = when (playback) {
            TtsPlayback.Idle -> "idle"
            TtsPlayback.Playing -> "playing"
            TtsPlayback.Paused -> "paused"
        }
    var progressPercent by mutableFloatStateOf(0f)
    var rate by mutableFloatStateOf(1f)
    var pitch by mutableFloatStateOf(1f)
    var volume by mutableFloatStateOf(1f) // 0..1
    var voiceId by mutableStateOf("") // 语音名称
    var timedStopMinutes by mutableIntStateOf(0)
    /** 睡眠定时剩余秒数，供界面动态展示倒计时微胶囊与快捷延长（R3+）。 */
    var timedStopRemainingSeconds by mutableIntStateOf(0)
    private var countdownRunnable: Runnable? = null
    /** 当前朗读句在播放文本中的 [start, end) 偏移，供阅读器高亮与滚动（R2）。 */
    var currentSentenceRange by mutableStateOf(0 to 0)
    /** 句变化回调：用于高亮/滚动与跨会话续读持久化（R2/R3）。 */
    var onSentence: ((offset: Int, end: Int) -> Unit)? = null
    /**
     * 末句播完回调（听书连续朗读）：播放文本全部读完时触发一次，调用方据此翻到
     * 下一章继续朗读；不设置或返回后不再播放时保持原「读完即停」行为。
     * 触发时播放态已置 idle，重播需调用方重新 [play]。
     */
    var onFinished: (() -> Unit)? = null
    private var sentences: List<Pair<String, Int>> = emptyList() // (句文本, 句首偏移)
    private var index = 0
    private var originalVolume = -1
    private val media = createMedia(appContext, ::onMediaPlay, ::pause, ::next, ::prev, ::stop)

    /**
     * 睡眠定时宿主：协调器只决定「何时」动作，音量写入与最终停止仍由本控制器执行，
     * 保证抽取前后的播放行为一致。
     */
    private val timedStopHost = object : TtsTimedStopHost {
        override fun isPlaying(): Boolean = status == "playing"
        override fun currentVolume(): Float = volume
        override fun setVolume(volume: Float, apply: Boolean) {
            this@TtsController.volume = volume.coerceIn(0f, 1f)
            if (apply) applyVolume()
        }
        override fun onTimedStop() = stop()
    }

    /** 睡眠定时生命周期协调器：计时 → 淡出 → 停止，全部走真实生产链路。 */
    private val timedStop = TtsTimedStopCoordinator(
        clock = { System.currentTimeMillis() },
        postDelayed = { delayMs, action -> handler.postDelayed(action, delayMs) },
        removeCallbacks = { action -> handler.removeCallbacks(action) },
        host = timedStopHost,
    )

    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var focusGainLegacy: Boolean = false
    private var wasPlayingBeforeFocusLoss: Boolean = false

    private val afChangeListener = createFocusListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeFocusLoss = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                wasPlayingBeforeFocusLoss = (status == "playing")
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    hasAudioFocus = true
                    wasPlayingBeforeFocusLoss = false
                    resume()
                }
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
                            .build(),
                    )
                    // 当前接口只接受立即获准；不排队等待后续 GAIN 自动开始朗读。
                    setAcceptsDelayedFocusGain(false)
                    setOnAudioFocusChangeListener(afChangeListener)
                    build()
                }
            }
            val r = audioManager.requestAudioFocus(focusRequest!!)
            hasAudioFocus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val r = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            focusGainLegacy = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus = focusGainLegacy
            hasAudioFocus
        }
    }

    private fun abandonFocus() {
        hasAudioFocus = false
        wasPlayingBeforeFocusLoss = false
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

    private val initListener = TextToSpeech.OnInitListener { code ->
        handler.post {
            if (code == TextToSpeech.SUCCESS) {
                // 语言梯度兼容回退：CHINA -> SIMPLIFIED_CHINESE -> CHINESE -> 默认语言
                var langSet = false
                val candidates = listOf(Locale.CHINA, Locale.SIMPLIFIED_CHINESE, Locale.CHINESE, Locale.getDefault())
                for (loc in candidates) {
                    val res = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                        langSet = true
                        break
                    }
                }
                if (!langSet) {
                    tts?.language = Locale.getDefault()
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        handler.post {
                            if (!hasAudioFocus) return@post
                            statusHolder.markPlaying()
                            media.updateState(PlaybackState.STATE_PLAYING)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            if (!hasAudioFocus) return@post
                            if (index < sentences.lastIndex) {
                                index += 1
                                speakCurrent()
                            } else {
                                statusHolder.markIdle()
                                progressPercent = 100f
                                media.updateState(PlaybackState.STATE_STOPPED)
                                media.hideNotification()
                                clearTimedStop()
                                restoreVolume()
                                abandonFocus()
                                onFinished?.invoke()
                            }
                        }
                    }

                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        handler.post {
                            abandonFocus()
                            failPlayback(MSG_ENGINE_ERROR)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handler.post {
                            abandonFocus()
                            failPlayback(MSG_ENGINE_ERROR)
                        }
                    }
                })
                availableVoices = tts?.voices?.toList() ?: emptyList()
                statusHolder.onInitSuccess()
            } else {
                statusHolder.onInitFailure(MSG_INIT_FAILED)
            }
        }
    }

    // 拔出耳机/断开蓝牙时自动暂停（AUDIO_BECOMING_NOISY），避免朗读突然外放。
    // 该广播由系统进程派发，在 Android 14+ 需指定 RECEIVER_EXPORTED 接收。
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && status == "playing") {
                pause()
            }
        }
    }

    init {
        tts = createTts(appContext, initListener)
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** 媒体通知「播放」：仅暂停态可续读；空闲态（无文本）不动作。 */
    private fun onMediaPlay() {
        if (status == "paused") resume()
    }

    fun play(text: String, bookTitle: String = "", chapterLabel: String = "朗读", startOffset: Int = 0): TtsPlayResult {
        when (val gate = statusHolder.gatePlay()) {
            is TtsPlayResult.Rejected -> return gate
            is TtsPlayResult.Accepted -> Unit
        }
        sentences = splitSentencesWithOffsets(text)
        if (sentences.isEmpty()) return TtsPlayResult.Rejected("当前没有可朗读的文字。")
        index = sentences.indexOfFirst { it.second >= startOffset }.coerceAtLeast(0)
        if (!requestFocus()) {
            stop()
            return TtsPlayResult.Rejected(MSG_FOCUS_DENIED)
        }
        media.updateMetadata(chapterLabel.ifBlank { "朗读" }, bookTitle.ifBlank { "创作阅读助手" })
        media.updateState(PlaybackState.STATE_PLAYING)
        media.showNotification()
        speakCurrent()
        scheduleTimedStop()
        return TtsPlayResult.Accepted
    }

    private fun applyVolume() {
        try {
            if (volume < 1f) {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (volume * max).toInt().coerceIn(0, max)
                if (originalVolume < 0) originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (e: Exception) {
            AppLog.w("Tts", "applyVolume failed: ${e.message}")
        }
    }

    private fun restoreVolume() {
        try {
            if (originalVolume >= 0) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
                originalVolume = -1
            }
        } catch (e: Exception) {
            AppLog.w("Tts", "restoreVolume failed: ${e.message}")
        }
    }

    private fun speakCurrent() {
        if (!hasAudioFocus) return
        val pair = sentences.getOrNull(index) ?: return
        val (sentence, start) = pair
        currentSentenceRange = start to (start + sentence.length)
        progressPercent = if (sentences.size <= 1) 100f else (index.toFloat() / (sentences.size - 1)) * 100f
        onSentence?.invoke(start, start + sentence.length)
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
        if (voiceId.isNotBlank()) {
            tts?.voices?.firstOrNull { it.name == voiceId }?.let { tts?.setVoice(it) }
        }
        applyVolume()
        media.updateCurrentSentence(sentence)
        val engine = tts
        if (engine == null) {
            failPlayback(MSG_ENGINE_ERROR)
            return
        }
        try {
            engine.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "tts-$index")
        } catch (e: Exception) {
            AppLog.w("Tts", "speak failed: ${e.message}")
            failPlayback(MSG_ENGINE_ERROR)
        }
    }

    /** 引擎错误统一出口：发布一次用户可消费错误并完全停止播放态（同 [stop]）。 */
    private fun failPlayback(message: String) {
        statusHolder.onEngineError(message)
        stop()
    }

    /**
     * 武装睡眠定时：每次 play / resume 调用一次。语义与抽取前一致——
     * 每次都按完整时长从当前时刻重新计时，本方法不感知章节。
     */
    private fun scheduleTimedStop() {
        clearTimedStop()
        if (timedStop.arm(timedStopMinutes)) startCountdown()
    }

    private fun startCountdown() {
        stopCountdown()
        val tick = object : Runnable {
            override fun run() {
                val rem = timedStop.remainingSeconds()
                timedStopRemainingSeconds = rem
                // 103-v1：首次 tick 可能先于异步 onStart 执行，此时 status 仍为 idle；
                // 是否继续只取决于已武装的 deadline/剩余秒，不能把异步播放态耦合进来，
                // 否则刚 play 就会清零倒计时。暂停/停止/关闭/release 仍由 clearTimedStop 统一清理。
                if (rem > 0) {
                    handler.postDelayed(this, 1000L)
                } else {
                    stopCountdown()
                }
            }
        }
        countdownRunnable = tick
        handler.post(tick)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        timedStopRemainingSeconds = 0
    }

    /**
     * 清空睡眠定时：取消尚未到期的计时与进行中的淡出（并恢复淡出前音量），同时停止倒计时。
     * 手动 [stop]、[pause]、末句播完与 [reinitialize] 均走此处。
     */
    private fun clearTimedStop() {
        timedStop.cancel()
        stopCountdown()
    }

    fun setTimedStop(min: Int) {
        timedStopMinutes = min
        if (status != "idle") scheduleTimedStop()
    }

    /**
     * 快捷延长续听：若已有定时则在剩余时间基础上追加 minutes 分钟；若当前未定时则设定 minutes 分钟。
     */
    fun extendTimedStop(additionalMinutes: Int = 15) {
        val currentRemMin = if (timedStopRemainingSeconds > 0) (timedStopRemainingSeconds + 59) / 60 else 0
        val target = (currentRemMin + additionalMinutes).coerceIn(1, 180)
        setTimedStop(target)
    }

    /** 实时调节音量：立即作用到 STREAM_MUSIC（不会等到下一句）。 */
    fun updateVolume(v: Float) {
        timedStop.cancelFade()
        volume = v.coerceIn(0f, 1f)
        if (status != "idle") applyVolume()
    }

    /** 实时调节语速：当前句立即以新语速重读，反馈实时敏捷。 */
    fun updateSpeechRate(v: Float) {
        val target = v.coerceIn(0.5f, 3.0f)
        if (kotlin.math.abs(rate - target) < 0.02f) return
        rate = target
        tts?.setSpeechRate(target)
        if (status == "playing") speakCurrent()
    }

    /** 实时调节音调：当前句立即以新音调重读，反馈更直观。 */
    fun updatePitch(v: Float) {
        val target = v.coerceIn(0.5f, 2f)
        if (kotlin.math.abs(pitch - target) < 0.02f) return
        pitch = target
        if (status == "playing") speakCurrent()
    }

    /** 切换音色：当前句立即以新音色重读。 */
    fun updateVoiceId(id: String) {
        voiceId = id
        if (status == "playing") speakCurrent()
    }

    fun pause() {
        clearTimedStop()
        tts?.stop()
        statusHolder.markPaused()
        media.updateState(PlaybackState.STATE_PAUSED)
        media.showNotification()
    }

    fun resume() {
        if (sentences.isEmpty()) return
        when (val gate = statusHolder.gatePlay()) {
            is TtsPlayResult.Rejected -> {
                statusHolder.publishError(gate.reason)
                return
            }
            is TtsPlayResult.Accepted -> Unit
        }
        if (!requestFocus()) {
            stop()
            statusHolder.publishError(MSG_FOCUS_DENIED)
            return
        }
        media.updateState(PlaybackState.STATE_PLAYING)
        media.showNotification()
        speakCurrent()
        scheduleTimedStop()
    }

    fun stop() {
        clearTimedStop()
        tts?.stop()
        restoreVolume()
        abandonFocus()
        wasPlayingBeforeFocusLoss = false
        statusHolder.markIdle()
        progressPercent = 0f
        currentSentenceRange = 0 to 0
        media.updateState(PlaybackState.STATE_STOPPED)
        media.hideNotification()
    }

    fun next() {
        if (index < sentences.lastIndex) {
            index += 1
            speakCurrent()
        }
    }

    fun prev() {
        if (index > 0) {
            index -= 1
            speakCurrent()
        }
    }

    fun release() {
        timedStop.release()
        stopCountdown()
        tts?.stop()
        restoreVolume()
        abandonFocus()
        tts?.shutdown()
        media.release()
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
    }

    /**
     * 显式重新初始化引擎：init failure / 引擎不可用后的进程内恢复路径（不要求重启 App）。
     * 关闭旧引擎对象 → 回到 Initializing → 重新创建 TextToSpeech，由 [initListener] 决定
     * Ready（成功）或 Unavailable（再次失败，可再调本方法重试）。
     */
    fun reinitialize() {
        clearTimedStop()
        tts?.stop()
        restoreVolume()
        abandonFocus()
        tts?.shutdown()
        statusHolder.reinitialize()
        media.updateState(PlaybackState.STATE_STOPPED)
        tts = createTts(appContext, initListener)
    }

    companion object {
        private const val MSG_INIT_FAILED = "系统语音引擎初始化失败，请到系统设置检查文字转语音。"
        private const val MSG_FOCUS_DENIED = "暂时无法获得音频焦点，请稍后重试朗读。"
        private const val MSG_ENGINE_ERROR = "朗读播放出错，请检查系统语音引擎。"
    }
}

/**
 * 框架原生 MediaSession + MediaStyle 通知（R1）。
 * 在系统媒体通知/锁屏展示 play/pause/prev/next 控制，并回调到 [TtsController]。
 * 使用 Android 框架类（android.media.session.MediaSession + android.app.Notification.MediaStyle），
 * 零额外依赖；通过内部 BroadcastReceiver 把通知按钮意图转派给媒体会话回调。
 */
internal class TtsMediaSession(
    context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mediaSession: MediaSession
    private val channelId = "tts_playback"
    private val notifId = 90210

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> onPlay()
                ACTION_PAUSE -> onPause()
                ACTION_NEXT -> onNext()
                ACTION_PREV -> onPrev()
                ACTION_STOP -> onStop()
            }
        }
    }

    init {
        mediaSession = MediaSession(appContext, "TtsPlayback")
        // 旧 flags 在 API 33+ 已 deprecated 且内部无副作用：媒体按钮与传输控制默认启用。
        @Suppress("DEPRECATION")
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = this@TtsMediaSession.onPlay()
            override fun onPause() = this@TtsMediaSession.onPause()
            override fun onSkipToNext() = this@TtsMediaSession.onNext()
            override fun onSkipToPrevious() = this@TtsMediaSession.onPrev()
            override fun onStop() = this@TtsMediaSession.onStop()

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                } ?: return super.onMediaButtonEvent(mediaButtonIntent)

                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            val state = mediaSession.controller.playbackState?.state
                            if (state == PlaybackState.STATE_PLAYING) {
                                this@TtsMediaSession.onPause()
                            } else {
                                this@TtsMediaSession.onPlay()
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            this@TtsMediaSession.onPlay()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            this@TtsMediaSession.onPause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            this@TtsMediaSession.onNext()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            this@TtsMediaSession.onPrev()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            this@TtsMediaSession.onStop()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY); addAction(ACTION_PAUSE); addAction(ACTION_NEXT)
            addAction(ACTION_PREV); addAction(ACTION_STOP)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        mediaSession.isActive = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "朗读播放控制", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(ch)
        }
    }

    fun updateState(state: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel()
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, 0L, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
                .setActions(actions)
                .build(),
        )
    }

    private var currentTitle: String = "朗读"
    private var currentAuthor: String = "创作阅读助手"

    fun updateMetadata(title: String, subtitle: String) {
        currentTitle = title.ifBlank { "朗读" }
        currentAuthor = subtitle.ifBlank { "创作阅读助手" }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentAuthor)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, currentAuthor)
                .build(),
        )
    }

    /** 实时锁屏歌词式同步：将正在朗读的句子推送到 MediaSession 并刷新通知 */
    fun updateCurrentSentence(sentence: String) {
        if (sentence.isBlank()) return
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentAuthor)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, sentence)
                .build(),
        )
        val state = mediaSession.controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED) {
            showNotification()
        }
    }

    fun showNotification() {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val contentIntent = PendingIntent.getActivity(
            appContext, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val metaTitle = mediaSession.controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "朗读"
        val metaSub = mediaSession.controller.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: mediaSession.controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = mediaSession.controller.playbackState?.state == PlaybackState.STATE_PLAYING

        val style = Notification.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(appContext)
        }
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metaTitle)
            .setContentText(metaSub)
            .setContentIntent(contentIntent)
            .setDeleteIntent(pending(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(style)
            .setOngoing(playing)
        // Notification.Builder.addAction(int, CharSequence, PendingIntent) 在 API 23+ 已 deprecated，
        // 但 AndroidX Media2 / androidx.media.app.NotificationCompat.MediaStyle 会引入额外依赖；
        // 此签名仍被系统兼容，逐处加 @Suppress 避免 compile 警告。
        @Suppress("DEPRECATION")
        val a1: Notification.Builder = builder.addAction(android.R.drawable.ic_media_previous, "上一句", pending(ACTION_PREV))
        @Suppress("DEPRECATION")
        val a2: Notification.Builder = a1.addAction(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "暂停" else "播放",
            pending(if (playing) ACTION_PAUSE else ACTION_PLAY),
        )
        @Suppress("DEPRECATION")
        a2.addAction(android.R.drawable.ic_media_next, "下一句", pending(ACTION_NEXT))

        try { notificationManager.notify(notifId, builder.build()) } catch (e: Exception) {
            AppLog.w("Tts", "showNotification failed: ${e.message}")
        }
    }

    fun hideNotification() {
        try { notificationManager.cancel(notifId) } catch (e: Exception) {
            AppLog.w("Tts", "hideNotification failed: ${e.message}")
        }
    }

    fun release() {
        try { appContext.unregisterReceiver(receiver) } catch (e: Exception) {
            AppLog.w("Tts", "unregisterReceiver failed: ${e.message}")
        }
        hideNotification()
        try { mediaSession.release() } catch (e: Exception) {
            AppLog.w("Tts", "mediaSession.release failed: ${e.message}")
        }
    }

    private fun pending(action: String): PendingIntent {
        val intent = Intent(action).setPackage(appContext.packageName)
        return PendingIntent.getBroadcast(
            appContext, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val ACTION_PLAY = "com.creationreadingassistant.tts.PLAY"
        private const val ACTION_PAUSE = "com.creationreadingassistant.tts.PAUSE"
        private const val ACTION_NEXT = "com.creationreadingassistant.tts.NEXT"
        private const val ACTION_PREV = "com.creationreadingassistant.tts.PREV"
        private const val ACTION_STOP = "com.creationreadingassistant.tts.STOP"
    }
}

/**
 * 将正文按句切分为「(句文本, 句首在原文中的绝对偏移)」列表。
 *
 * 针对中文小说深度优化：
 * 1. 贪婪吸收后置引号/括号：如“你好啊！”末尾的双引号将跟随包含在本句，绝不被截断到下一句；
 * 2. 连续省略号与复合标点合并：如“……”、“？！”作为统一断句边界，杜绝切出只有单个符号的空顿挫句；
 * 3. 过滤纯标点空句，前导空白偏移精确换算，朗读发音与阅读高亮严丝合缝。
 */
internal fun splitSentencesWithOffsets(text: String): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    var offset = 0
    val paragraphs = text.split("\n")
    for (para in paragraphs) {
        val paraLen = para.length
        if (para.isBlank()) {
            offset += paraLen + 1
            continue
        }
        var start = 0
        var i = 0
        while (i < paraLen) {
            val c = para[i]
            if (isPunctuationBreak(c)) {
                // 1. 贪婪跳过连续的断句标点（如多个 !? 或连续 ……）
                var end = i + 1
                while (end < paraLen && isPunctuationBreak(para[end])) {
                    end++
                }
                // 2. 贪婪吸收紧随其后的闭引号/括号（如 ” ’ ） 」 等）
                while (end < paraLen && isTrailingClosingSymbol(para[end])) {
                    end++
                }
                val rawSegment = para.substring(start, end)
                val trimmed = rawSegment.trim()
                if (hasSpokenContent(trimmed)) {
                    val leadingSpaces = rawSegment.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                    result.add(trimmed to (offset + start + leadingSpaces))
                }
                start = end
                i = end
            } else {
                i++
            }
        }
        if (start < paraLen) {
            val rawSegment = para.substring(start)
            val trimmed = rawSegment.trim()
            if (hasSpokenContent(trimmed)) {
                val leadingSpaces = rawSegment.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                result.add(trimmed to (offset + start + leadingSpaces))
            }
        }
        offset += paraLen + 1
    }
    return result
}

private fun isPunctuationBreak(c: Char): Boolean =
    c == '。' || c == '！' || c == '？' || c == '!' || c == '?' ||
    c == '；' || c == ';' || c == '…' || c == '\u2026'

private fun isTrailingClosingSymbol(c: Char): Boolean =
    c == '”' || c == '’' || c == '"' || c == '\'' ||
    c == '）' || c == ')' || c == '】' || c == ']' ||
    c == '》' || c == '」' || c == '』'

private fun hasSpokenContent(text: String): Boolean =
    text.any { !isPunctuationBreak(it) && !isTrailingClosingSymbol(it) && !it.isWhitespace() }

