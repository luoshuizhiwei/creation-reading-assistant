package com.creationreadingassistant.ui.screen.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
internal class TtsController(context: Context) : TtsStatus {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val statusHolder = TtsStatusHolder()

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
    var availableVoices by mutableStateOf(emptyList<Voice>())
    var timedStopMinutes by mutableIntStateOf(0)
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
    private var timedStopRunnable: Runnable? = null
    private val media = TtsMediaSession(appContext, ::onMediaPlay, ::pause, ::next, ::prev, ::stop)

    private val initListener = TextToSpeech.OnInitListener { code ->
        if (code == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    statusHolder.markPlaying()
                    media.updateState(PlaybackState.STATE_PLAYING)
                }

                override fun onDone(utteranceId: String?) {
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
                        onFinished?.invoke()
                    }
                }

                @Suppress("DEPRECATION")
                override fun onError(utteranceId: String?) {
                    failPlayback(MSG_ENGINE_ERROR)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    failPlayback(MSG_ENGINE_ERROR)
                }
            })
            availableVoices = tts?.voices?.toList() ?: emptyList()
            statusHolder.onInitSuccess()
        } else {
            statusHolder.onInitFailure(MSG_INIT_FAILED)
        }
    }

    // 拔出耳机/断开蓝牙时自动暂停（AUDIO_BECOMING_NOISY），避免朗读突然外放。
    // 系统广播不受 exported 标志影响；NOT_EXPORTED 拒绝其他 App 伪造同名广播。
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY && status == "playing") {
                pause()
            }
        }
    }

    init {
        tts = TextToSpeech(appContext, initListener)
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
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

    private fun scheduleTimedStop() {
        clearTimedStop()
        if (timedStopMinutes > 0) {
            val runnable = Runnable { stop() }
            timedStopRunnable = runnable
            handler.postDelayed(runnable, timedStopMinutes * 60_000L)
        }
    }

    private fun clearTimedStop() {
        timedStopRunnable?.let { handler.removeCallbacks(it) }
        timedStopRunnable = null
    }

    fun setTimedStop(min: Int) {
        timedStopMinutes = min
        if (status != "idle") scheduleTimedStop()
    }

    /** 实时调节音量：立即作用到 STREAM_MUSIC（不会等到下一句）。 */
    fun updateVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (status != "idle") applyVolume()
    }

    /** 实时调节音调：当前句立即以新音调重读，反馈更直观。 */
    fun updatePitch(v: Float) {
        pitch = v.coerceIn(0.5f, 2f)
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
        media.updateState(PlaybackState.STATE_PLAYING)
        media.showNotification()
        speakCurrent()
        scheduleTimedStop()
    }

    fun stop() {
        clearTimedStop()
        tts?.stop()
        restoreVolume()
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
        clearTimedStop()
        tts?.stop()
        restoreVolume()
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
        tts?.shutdown()
        statusHolder.reinitialize()
        media.updateState(PlaybackState.STATE_STOPPED)
        tts = TextToSpeech(appContext, initListener)
    }

    companion object {
        private const val MSG_INIT_FAILED = "系统语音引擎初始化失败，请到系统设置检查文字转语音。"
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

    fun updateMetadata(title: String, subtitle: String) {
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .build(),
        )
    }

    fun showNotification() {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val contentIntent = PendingIntent.getActivity(
            appContext, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val metaTitle = mediaSession.controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "朗读"
        val metaSub = mediaSession.controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = mediaSession.controller.playbackState?.state == PlaybackState.STATE_PLAYING

        val style = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(appContext)
        }
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metaTitle)
            .setContentText(metaSub)
            .setContentIntent(contentIntent)
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

/** 将正文切分为「句（句文本, 句首在原文中的偏移）」列表，保留标点。 */
internal fun splitSentencesWithOffsets(text: String): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    var offset = 0
    for (para in text.split("\n")) {
        if (para.isBlank()) { offset += para.length + 1; continue }
        var start = 0
        for (i in para.indices) {
            val c = para[i]
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '；' || c == ';' || c == '…') {
                val sentence = para.substring(start, i + 1).trim()
                if (sentence.isNotBlank()) result.add(sentence to (offset + start))
                start = i + 1
            }
        }
        if (start < para.length) {
            val sentence = para.substring(start).trim()
            if (sentence.isNotBlank()) result.add(sentence to (offset + start))
        }
        offset += para.length + 1
    }
    return result
}
