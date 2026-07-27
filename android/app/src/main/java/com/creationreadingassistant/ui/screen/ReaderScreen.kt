package com.creationreadingassistant.ui.screen

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.creationreadingassistant.data.settings.SettingsStore
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.pager.EpubChapterSource
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedReaderHost
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * 阅读器全屏页（1:1 复刻 mobile/ 的 MobileReaderView 布局）。
 *
 * 分区：顶部栏（可收起）/ 正文区（EPUB 按章节、TXT 降级滚动文本）/ 底部 TTS 播放条 /
 * 底部弹层（目录、笔记与标注、AI 助手、AI 解读、灵感速记、设置、进度）。
 *
 * 复用：EpubParser（经 EpubRepository.openEpub）、BookRepository、BookContentDao、
 * HighlightDao / NoteDao / InspirationDao / ReadingProgressDao、TextToSpeech。
 * 不重写解析与朗读逻辑；helper 均在本文件内。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderEntryPoint {
    fun epubRepository(): EpubRepository
    fun bookRepository(): BookRepository
    fun highlightDao(): HighlightDao
    fun noteDao(): NoteDao
    fun inspirationDao(): InspirationDao
    fun readingProgressDao(): ReadingProgressDao
    fun readingSessionDao(): ReadingSessionDao
    fun bookContentDao(): BookContentDao
    fun categoryDao(): CategoryDao
    fun tagDao(): TagDao
    fun aiClient(): AiClient
    fun settingsStore(): SettingsStore
    fun pageIndexStore(): PageIndexStore
}

/** 底部弹层类型（不新建路由，仅切换状态）。 */
private enum class SheetType {
    TOC, NOTES, AI_ASSIST, AI_EXPLAIN, INSPIRATION, SETTINGS, PROGRESS, SEARCH, BOOK_INFO, THEME
}

private val HIGHLIGHT_COLORS = listOf("yellow", "red", "green", "blue", "purple")

private fun highlightColor(c: String): Color = when (c) {
    "yellow" -> Color(0xFFFFF176)
    "red" -> Color(0xFFFF8A80)
    "green" -> Color(0xFFB9F6CA)
    "blue" -> Color(0xFF90CAF9)
    "purple" -> Color(0xFFE1BEE7)
    else -> Color(0xFFFFF176)
}

/** 阅读器纸色背景（对照 web readerBackground）。 */
private fun paperColors(bg: String): Pair<Color, Color> = when (bg) {
    "white" -> Color(0xFFFAFAF8) to Color(0xFF1A1A1A)
    "warm" -> Color(0xFFF3E8D8) to Color(0xFF2B2118)
    "green" -> Color(0xFFE8F0DF) to Color(0xFF1F291A)
    "night" -> Color(0xFF1A1614) to Color(0xFFE8DDD0)
    "warm-yellow" -> Color(0xFFF7F0D8) to Color(0xFF3D2B1F)
    "green-bean" -> Color(0xFFE8F0E0) to Color(0xFF2D332B)
    "oled-black" -> Color(0xFF000000) to Color(0xFFB8B0A8)
    else -> Color(0xFFF3E8D8) to Color(0xFF2B2118)
}

private fun nowIso(): String = Instant.now().toString()

/** 按阅读进度百分比推算最接近的章节索引（SE4 兜底定位用）。 */
private fun progressToChapterIndex(book: EpubBook, progressPercent: Float?): Int {
    val size = book.chapters.size
    if (size <= 1) return 0
    if (progressPercent == null) return 0
    return ((progressPercent / 100f * size - 1).toInt()).coerceIn(0, size - 1)
}

/** 毫秒格式化为「X 小时 Y 分」（对照 web formatDuration）。 */
private fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    if (totalMin <= 0) return "不到 1 分钟"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h} 小时 ${m} 分" else "${m} 分"
}

/** 构造标准灵感 payload，确保 InspirationViewModel 能正确解析来源（全字段对齐网页 I5）。 */
private fun buildInspirationPayload(
    bid: String,
    bookTitle: String,
    chapterTitle: String,
    excerpt: String?,
    progressPercent: Float,
    tags: List<String> = emptyList(),
    categoryIds: List<String> = emptyList(),
    bookAuthor: String? = null,
): String {
    val payload = InspirationPayloadData(
        tags = tags,
        categoryIds = categoryIds,
        source = InspirationSourceInfo(
            bookId = bid.ifBlank { null },
            bookTitle = bookTitle.ifBlank { null },
            bookAuthor = bookAuthor?.takeIf { it.isNotBlank() },
            chapterTitle = chapterTitle.ifBlank { null },
            locationLabel = null,
            progressPercent = if (progressPercent > 0f) progressPercent else null,
            excerpt = excerpt?.takeIf { it.isNotBlank() },
        ),
    )
    return Json.encodeToString(InspirationPayloadData.serializer(), payload)
}

/**
 * 句级 TTS 朗读控制器（复用 Android TextToSpeech + 框架 MediaSession）。
 * 按句切分朗读，暴露播放/暂停/上下句/停止/语速与进度；通过 [TtsMediaSession] 在系统媒体
 * 通知/锁屏展示 play/pause/prev/next 控制并回调本播放器。
 *
 * 句级续读（R3）：暂停记忆当前句索引，恢复从当前句续读；跨会话续读通过外部持久化句首偏移实现
 * （见 ReaderScreen 中的 ttsResumeOffset / SettingsStore.saveTtsResume）。
 */
private class TtsController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    var isReady by mutableStateOf(false)
    var status by mutableStateOf("idle") // idle / playing / paused
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
                    status = "playing"
                    media.updateState(PlaybackState.STATE_PLAYING)
                }

                override fun onDone(utteranceId: String?) {
                    if (index < sentences.lastIndex) {
                        index += 1
                        speakCurrent()
                    } else {
                        status = "idle"
                        progressPercent = 100f
                        media.updateState(PlaybackState.STATE_STOPPED)
                        media.hideNotification()
                        clearTimedStop()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    status = "idle"
                    media.updateState(PlaybackState.STATE_STOPPED)
                    media.hideNotification()
                }
            })
            availableVoices = tts?.voices?.toList() ?: emptyList()
            isReady = true
        }
    }

    init {
        tts = TextToSpeech(appContext, initListener)
    }

    /** 媒体通知「播放」：仅暂停态可续读；空闲态（无文本）不动作。 */
    private fun onMediaPlay() {
        if (status == "paused") resume()
    }

    fun play(text: String, bookTitle: String = "", chapterLabel: String = "朗读", startOffset: Int = 0) {
        sentences = splitSentencesWithOffsets(text)
        if (sentences.isEmpty()) return
        index = sentences.indexOfFirst { it.second >= startOffset }.coerceAtLeast(0)
        media.updateMetadata(chapterLabel.ifBlank { "朗读" }, bookTitle.ifBlank { "创作阅读助手" })
        media.updateState(PlaybackState.STATE_PLAYING)
        media.showNotification()
        speakCurrent()
        scheduleTimedStop()
    }

    private fun applyVolume() {
        try {
            if (volume < 1f) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (volume * max).toInt().coerceIn(0, max)
                if (originalVolume < 0) originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (_: Exception) { }
    }

    private fun restoreVolume() {
        try {
            if (originalVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                originalVolume = -1
            }
        } catch (_: Exception) { }
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
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "tts-$index")
    }

    private fun scheduleTimedStop() {
        clearTimedStop()
        if (timedStopMinutes > 0) {
            timedStopRunnable = Runnable { stop() }
            handler.postDelayed(timedStopRunnable!!, timedStopMinutes * 60_000L)
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
        status = "paused"
        media.updateState(PlaybackState.STATE_PAUSED)
        media.showNotification()
    }

    fun resume() {
        if (sentences.isNotEmpty()) {
            media.updateState(PlaybackState.STATE_PLAYING)
            media.showNotification()
            speakCurrent()
            scheduleTimedStop()
        }
    }

    fun stop() {
        clearTimedStop()
        tts?.stop()
        restoreVolume()
        status = "idle"
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
    }
}

/**
 * 框架原生 MediaSession + MediaStyle 通知（R1）。
 * 在系统媒体通知/锁屏展示 play/pause/prev/next 控制，并回调到 [TtsController]。
 * 使用 Android 框架类（android.media.session.MediaSession + android.app.Notification.MediaStyle），
 * 零额外依赖；通过内部 BroadcastReceiver 把通知按钮意图转派给媒体会话回调。
 */
private class TtsMediaSession(
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
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
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
            .addAction(android.R.drawable.ic_media_previous, "上一句", pending(ACTION_PREV))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放",
                pending(if (playing) ACTION_PAUSE else ACTION_PLAY),
            )
            .addAction(android.R.drawable.ic_media_next, "下一句", pending(ACTION_NEXT))

        try { notificationManager.notify(notifId, builder.build()) } catch (_: Exception) { }
    }

    fun hideNotification() {
        try { notificationManager.cancel(notifId) } catch (_: Exception) { }
    }

    fun release() {
        try { appContext.unregisterReceiver(receiver) } catch (_: Exception) { }
        hideNotification()
        try { mediaSession.release() } catch (_: Exception) { }
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
private fun splitSentencesWithOffsets(text: String): List<Pair<String, Int>> {
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

/** 构造高亮/笔记的 locator_json，记录选中文字起点在全书文本中的全局字符偏移。 */
private fun makeOffsetLocator(offset: Int): String = LegacyOffsetCodec.encodeLocator(offset)

/** 从 locator_json 解析全局字符偏移（容错：不依赖完整 JSON 解析）。 */
private fun parseLocatorOffset(json: String?): Int? = LegacyOffsetCodec.decodeLocator(json)

/**
 * 为单个文本块构造带「当前朗读句」高亮背景的 AnnotatedString（EPUB 逐句高亮，对照 TXT 机制）。
 * [blockGlobalOffset] 为该块在全书文本中的全局偏移；[chapterBase] 为所在章节在全书中的起始偏移，
 * 二者之差即为块在章节内（= TTS contentText）的偏移。
 */
private fun buildSentenceHighlighted(
    text: String,
    blockGlobalOffset: Int,
    chapterBase: Int,
    ttsSentenceRange: Pair<Int, Int>?,
    bg: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.text.AnnotatedString {
    if (ttsSentenceRange == null || blockGlobalOffset < 0) return androidx.compose.ui.text.AnnotatedString(text)
    val s = ttsSentenceRange.first - (blockGlobalOffset - chapterBase)
    val e = ttsSentenceRange.second - (blockGlobalOffset - chapterBase)
    if (s < 0 || s >= text.length || e <= s) return androidx.compose.ui.text.AnnotatedString(text)
    return androidx.compose.ui.text.AnnotatedString.Builder(text).apply {
        addStyle(androidx.compose.ui.text.SpanStyle(background = bg), s, e.coerceAtMost(text.length))
    }.toAnnotatedString()
}

/**
 * 计算每章各渲染块（含图片，图片记为 -1）在全书文本中的全局字符偏移。
 * 与 [splitSentencesWithOffsets] 对 contentText 的切分一致：文本块按 "\n" 拼接。
 */
private fun computeBlockGlobalOffsets(blocks: List<DocBlock>, chapterBase: Int): List<Int> =
    LegacyOffsetCodec.blockOffsets(
        blocks.map { (it as? DocBlock.Text)?.text?.length },
        chapterBase,
    )

/** 根据章节内偏移，返回包含该偏移的「渲染块」索引（用于导航精确滚动）。 */
private fun blockIndexForChapterOffset(blocks: List<DocBlock>, inChapter: Int): Int? {
    var acc = 0
    var idx = -1
    blocks.forEachIndexed { i, block ->
        if (block is DocBlock.Text) {
            if (acc <= inChapter) idx = i
            acc += block.text.length + 1
        }
    }
    return idx.takeIf { it >= 0 }
}

@Composable
private fun rememberTts(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * 翻页模式（对照 web readerMode=paged）：按章整屏展示，左右边缘点击翻章，
 * 支持 fade / slide / curl 翻页动效。中间区域保留滚动与整段选中（避免与选择冲突）。
 * 说明：原生自研引擎按章解析，未做章内逐页分页（epub.js 能力），故「页」= 一章。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedEpubView(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    pageMargin: Float,
    paperFg: Color,
    tapZoneMode: String,
    pageTurnEffect: String,
    chapterIndex: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(horizontal = pageMargin.dp, vertical = 8.dp)) {
            val content: @Composable () -> Unit = {
                PagedChapterContent(
                    blocks = blocks,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeightBold = fontWeightBold,
                    paperFg = paperFg,
                    onSelectBlock = onSelectBlock,
                    blockGlobalOffsets = blockGlobalOffsets,
                    chapterBase = chapterBase,
                    ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                    focusBlockIndex = focusBlockIndex,
                    sentenceHighlightBg = sentenceHighlightBg,
                    bringRequester = bringRequester,
                )
            }
            // 不在这里同时保留新旧整章 Composition。旧 AnimatedContent/Crossfade
            // 会在大章节翻页时让两章文本布局同时驻留，显著放大峰值内存。
            // 翻页动效后续应基于轻量截图/页面缓存实现，而不是复制整章组件树。
            @Suppress("UNUSED_VARIABLE")
            val configuredEffect = pageTurnEffect
            @Suppress("UNUSED_VARIABLE")
            val currentChapter = chapterIndex
            content()
        }
        // 点击翻页分区：three-zone=左右边缘；five-zone=再加上下边缘（对照 web tapZoneMode）
        if (tapZoneMode == "five-zone") {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canPrev) { onPrev() },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Row(Modifier.weight(0.76f).fillMaxWidth()) {
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrev() },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                    Spacer(Modifier.weight(0.68f))
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNext() },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                }
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canNext) { onNext() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrev() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Spacer(Modifier.weight(0.68f))
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNext() },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedChapterContent(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    paperFg: Color,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = blocks,
            key = { index, block ->
                when (block) {
                    is DocBlock.Text -> "text-$index"
                    is DocBlock.Image -> "image-$index-${block.path}"
                }
            },
        ) { idx, block ->
            when (block) {
                is DocBlock.Text -> {
                    val gOff = blockGlobalOffsets.getOrElse(idx) { -1 }
                    val ann = buildSentenceHighlighted(block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg)
                    Text(
                        text = ann,
                        style = TextStyle(
                            textAlign = TextAlign.Justify,
                            lineHeight = (fontSize * lineHeight).sp,
                            textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (fontSize * 2).sp),
                        ),
                        fontSize = fontSize.sp,
                        fontWeight = if (block.isHeading || fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                        color = paperFg,
                        modifier = Modifier.fillMaxWidth().clickable { onSelectBlock(block.text, gOff) },
                    )
                }
                is DocBlock.Image -> AsyncImage(
                    model = java.io.File(block.path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                )
            }
        }
    }
}

/**
 * 全书索引（在 IO 线程由 [buildBookIndex] 预构建）：
 * - [chapterStartOffsets] 各章在全文中的起始字符偏移（定位 / 跳章 / 搜索映射用）
 * - [chapterTitles] 各章标题（目录 / 搜索结果展示用）
 * - [totalChars] 全本纯文本字符总数（字数统计用）
 *
 * 内存纪律：索引**只保留偏移与标题，不拼接全本文本**。整本 String 会随书变大逼近
 * 256MB 堆上限触发 OOM；搜索时改为按需逐章流式抽取（见 [computeEpubSearch]），
 * 内存只保留当前章文本。
 */
private data class BookIndex(
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val totalChars: Int,
)

/**
 * 纯文本仍会一次读入内存，先限制原始文件大小；渲染阶段必须使用 [PlainTextChunk]
 * 分块懒加载，禁止再把整本正文交给单个 TextLayout。
 */
private const val MAX_IN_MEMORY_TEXT_BYTES = 8 * 1024 * 1024
private const val PLAIN_TEXT_CHUNK_CHARS = 3_000

private data class PlainTextChunk(
    val startOffset: Int,
    val text: String,
)

/**
 * 按接近自然段的位置切分长文本。每个块单独排版，LazyColumn 只保留视口附近的
 * TextLayout，避免数 MB 小说在打开时生成数十万行的单一布局而触发 ANR/OOM。
 */
private fun chunkPlainText(
    content: String,
    targetChars: Int = PLAIN_TEXT_CHUNK_CHARS,
): List<PlainTextChunk> {
    if (content.isEmpty()) return emptyList()
    val chunks = ArrayList<PlainTextChunk>((content.length / targetChars) + 1)
    var start = 0
    while (start < content.length) {
        var end = (start + targetChars).coerceAtMost(content.length)
        if (end < content.length) {
            val searchEnd = (end + 512).coerceAtMost(content.length)
            val newline = content.indexOf('\n', startIndex = end)
            if (newline in end until searchEnd) end = newline + 1
        }
        if (end <= start) end = (start + targetChars).coerceAtMost(content.length)
        chunks += PlainTextChunk(startOffset = start, text = content.substring(start, end))
        start = end
    }
    return chunks
}

private fun chunkIndexForOffset(chunks: List<PlainTextChunk>, offset: Int): Int {
    if (chunks.isEmpty()) return 0
    var low = 0
    var high = chunks.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (chunks[mid].startOffset <= offset) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/**
 * 只使用 EPUB 目录阶段已取得的 ZIP 条目大小构建轻量索引。
 *
 * 旧实现会在打开书籍时逐章解压并抽取全书文本。大书会长时间占用 IO，
 * 多次进出阅读器还可能叠加多个不可及时取消的扫描任务，最终触发 ANR/OOM。
 * ZIP 解压后字节数通常大于可见字符数，适合作为单调递增的定位偏移估算。
 */
private fun buildBookIndex(book: EpubBook): BookIndex {
    // 公式已抽到 LegacyOffsetCodec 并由单测逐值锁死。这里只保留一处调用：
    // 所有历史高亮/笔记的 locator 都按这套公式算出，线上必须只有一份实现，
    // 否则将来换偏移基准时无从比对。
    val lengths = book.chapters.map { it.estimatedTextLength }
    return BookIndex(
        chapterStartOffsets = LegacyOffsetCodec.chapterStartOffsets(lengths),
        chapterTitles = book.chapters.map { it.title },
        totalChars = LegacyOffsetCodec.totalChars(lengths),
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    navController: NavHostController,
    bookId: String?,
    highlightId: String? = null,
    @Suppress("unused") viewModel: com.creationreadingassistant.ui.viewmodel.ReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settingsVm: SettingsViewModel = hiltViewModel()
    val readerSettings by settingsVm.reader.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val entry = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ReaderEntryPoint::class.java)
    }
    val tts = rememberTts()
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── 阅读状态（本文件内管理，复用 EpubRepository 解析与持久化） ──────────────
    var epubBook by remember { mutableStateOf<EpubBook?>(null) }
    var plainContent by remember { mutableStateOf("") }
    var bookTitle by remember { mutableStateOf("未命名书籍") }
    var chapterIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedPlainPercent by remember { mutableFloatStateOf(0f) }
    var savedEpubOffsetInChapter by remember { mutableIntStateOf(0) }

    // 预加载状态（IO 线程填充，主线程只读，杜绝主线程 Zip I/O 导致的 ANR / OOM）
    var chapterBlocks by remember { mutableStateOf<List<DocBlock>>(emptyList()) }
    var bookIndex by remember { mutableStateOf<BookIndex?>(null) }
    var chapterLoadJob by remember { mutableStateOf<Job?>(null) }
    var epubDocument by remember { mutableStateOf<EpubDocument?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }

    // ── 让「摆设开关」真正生效（此前这些设置存了值但没有任何消费者）──────────
    val hostView = LocalView.current

    // 常亮显示：阅读器在前台期间保持屏幕不熄灭，离开页面必须撤掉
    DisposableEffect(readerSettings.keepAwake) {
        val window = (context as? Activity)?.window
        if (readerSettings.keepAwake) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // 沉浸模式：菜单收起时连系统状态栏/导航栏一起藏；离开阅读器恢复。
    // 必须同时允许内容进刘海区（SHORT_EDGES），否则藏掉状态栏后系统会在
    // 打孔摄像头那一条补黑边 —— 真机上就是一条黑带，比不沉浸还难看。
    DisposableEffect(readerSettings.immersiveMode, controlsVisible) {
        val window = (context as? Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView) }
        val hide = readerSettings.immersiveMode && !controlsVisible
        if (window != null && Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (hide) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
        if (controller != null) {
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hide) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (window != null && Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    // 菜单自动隐藏：呼出菜单 N 秒后自动收起（0 = 从不）。弹层打开时不倒计时。
    var sheetOpenGuard by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, readerSettings.autoHideSeconds, sheetOpenGuard) {
        val secs = readerSettings.autoHideSeconds
        if (controlsVisible && secs > 0 && !sheetOpenGuard) {
            delay(secs * 1000L)
            controlsVisible = false
        }
    }
    var selectedText by remember { mutableStateOf("") }
    // T1：记录选区起点在本书全局文本中的偏移，用于写入 locator_json（TXT=plainContent 偏移，EPUB=block 全局偏移）
    var selectedRangeStart by remember { mutableStateOf(-1) }
    var selectedGlobalOffset by remember { mutableStateOf(-1) }
    var sheet by remember { mutableStateOf<SheetType?>(null) }
    // 弹层打开时暂停「菜单自动隐藏」倒计时（否则调设置调到一半菜单没了）
    LaunchedEffect(sheet) { sheetOpenGuard = sheet != null }
    var showTts by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // R5：最近浏览章节（本会话记录，置顶于目录）；R8：顶栏「更多」菜单
    var recentChapters = remember { mutableStateListOf<Int>() }
    var showReaderOverflow by remember { mutableStateOf(false) }
    // R3：跨会话 TTS 续读句偏移
    var ttsResumeOffset by remember { mutableStateOf(0) }

    // 阅读设置（来自持久化 SettingsStore，见 readerSettings）

    // 笔记对话框
    var noteOpen by remember { mutableStateOf(false) }
    var noteBody by remember { mutableStateOf("") }
    // 高亮颜色选择
    var showColorRow by remember { mutableStateOf(false) }

    // 阅读提醒 / 本次阅读计时（对照 web useReaderReminders + useReaderSession）
    var activeReadingMs by remember { mutableStateOf(0L) }
    var savedTotalReadingMs by remember { mutableStateOf(0L) }
    var sessionStartProgress by remember { mutableStateOf(0f) }
    var bookAuthor by remember { mutableStateOf<String?>(null) }
    var bookOriginalFile by remember { mutableStateOf<String?>(null) }
    var bookSize by remember { mutableStateOf(0) }
    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    val settingsRef = remember { mutableStateOf(readerSettings) }
    LaunchedEffect(readerSettings) { settingsRef.value = readerSettings }

    // TTS 高级项：首次将持久化的音调/音量/音色/定时停止载入控制器（仅一次，避免播放中回灌导致重读）
    var ttsSynced by remember { mutableStateOf(false) }
    LaunchedEffect(readerSettings) {
        if (!ttsSynced) {
            tts.pitch = readerSettings.ttsPitch
            tts.volume = readerSettings.ttsVolume
            tts.voiceId = readerSettings.ttsVoiceId
            tts.setTimedStop(readerSettings.ttsTimedStopMinutes)
            ttsSynced = true
        }
    }

    val bid = bookId ?: ""

    // R3：跨会话 TTS 续读 —— 加载本书上次朗读句偏移；并把句变化持久化（含本会话续读偏移）。
    LaunchedEffect(bid) {
        val r = runCatching { entry.settingsStore().loadTtsResume() }.getOrNull()
        ttsResumeOffset = if (r?.first == bid) r.second else 0
        tts.onSentence = { start, _ ->
            ttsResumeOffset = start
            scope.launch(Dispatchers.IO) { runCatching { entry.settingsStore().saveTtsResume(bid, start) } }
        }
    }

    // SE4：从搜索结果跳转时携带的 highlightId（高亮或笔记），消费后置空避免重复触发
    var pendingHighlightId by remember { mutableStateOf(highlightId) }

    // 数据观察（复用 DAO，仅读取已存在字段）
    val highlights by entry.highlightDao().observeByBook(bid).collectAsStateWithLifecycle(emptyList())
    val notesAll by entry.noteDao().observeAllActive().collectAsStateWithLifecycle(emptyList())
    val notes = notesAll.filter { note -> note.book_id == bid }
    val inspirationsAll by entry.inspirationDao().observeAllActive().collectAsStateWithLifecycle(emptyList())
    val inspirations = inspirationsAll.filter { ins -> ins.source_book_id == bid }
    val allCategories by entry.categoryDao().observeAllActive().collectAsStateWithLifecycle(emptyList())
    val allTags by entry.tagDao().observeAllActive().collectAsStateWithLifecycle(emptyList())
    val sessions by entry.readingSessionDao().observeByBook(bid).collectAsStateWithLifecycle(emptyList())

    // 正文文本（TTS / 选择用）：EPUB 取当前章节（已预加载到 chapterBlocks），TXT 取全文
    val contentText = if (epubBook != null) {
        chapterBlocks.filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
    } else {
        plainContent
    }

    // 书内搜索：EPUB 不再常驻全本文本（会 OOM），改为搜索时按需逐章流式抽取（见 computeEpubSearch）；
    // 这里只暴露各章偏移与标题，供跳章 / 命中映射使用。
    val isTxt = epubBook == null
    val chapterStartOffsets = bookIndex?.chapterStartOffsets ?: emptyList()
    val chapterTitles = bookIndex?.chapterTitles ?: emptyList()
    val plainChunks = remember(plainContent) { chunkPlainText(plainContent) }

    // ── 自研分页引擎（pagerEngineMode=on 时 TXT/EPUB 都走真正的章内逐页翻页）──
    val pagerEngineOn = if (epubBook != null) {
        readerSettings.epubPagerEngineMode == "on"
    } else {
        readerSettings.pagerEngineMode == "on"
    }
    // 外部跳转请求（进度条 / 目录 / 高亮定位），宿主消费后置回 null
    val pagedJumpRequest = remember { mutableStateOf<Int?>(null) }
    // 分页引擎上报的当前位置（全书字符偏移 + 百分比），-1 表示尚未上报
    var pagedAbsOffset by remember { mutableIntStateOf(-1) }
    var pagedPercent by remember { mutableFloatStateOf(0f) }

    // TXT 章节识别：此前 TXT 完全没有章节概念，目录永远是「暂未识别到目录」。
    // 只在正文变化时算一次，识别不出章节时 TxtChapterDetector 会返回单章「全文」。
    val txtChapters = remember(plainContent) {
        if (epubBook == null && plainContent.isNotBlank()) {
            PlainTextDocument(plainContent).chapters
        } else {
            emptyList()
        }
    }
    // EPUB 分页只在试验引擎开启时消费它；缓存让同章重排/往返不再重复解压。
    DisposableEffect(epubDocument) {
        val document = epubDocument
        onDispose { document?.close() }
    }
    val pagedSource: PagedChapterSource? = remember(
        epubBook,
        epubDocument,
        bookIndex,
        plainContent,
        txtChapters,
    ) {
        val index = bookIndex
        val document = epubDocument
        when {
            epubBook != null && index != null && document != null ->
                EpubChapterSource(
                    titles = index.chapterTitles,
                    chapterStartOffsets = index.chapterStartOffsets,
                    totalChars = index.totalChars,
                    loadBlocks = document::blocks,
                )

            plainContent.isNotBlank() && txtChapters.isNotEmpty() ->
                TxtChapterSource(plainContent, txtChapters)

            else -> null
        }
    }

    // 进度计算
    val epubPercent = if (epubBook != null) {
        val size = epubBook!!.chapters.size
        if (size <= 1) if (chapterIndex == 0) 100f else 0f else (chapterIndex.toFloat() / (size - 1)) * 100f
    } else 0f

    val plainListState = rememberLazyListState()
    val firstPlainItem = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
    val firstPlainChunk = plainChunks.getOrNull(plainListState.firstVisibleItemIndex)
    val firstPlainFraction = if (firstPlainItem != null && firstPlainItem.size > 0) {
        (-firstPlainItem.offset).coerceAtLeast(0).toFloat() / firstPlainItem.size
    } else {
        0f
    }
    val visiblePlainOffset = when {
        // 分页引擎开启时，位置的真源是引擎上报的页首偏移，滚动列表根本不在屏上
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedAbsOffset
        firstPlainChunk != null ->
            firstPlainChunk.startOffset + (firstPlainChunk.text.length * firstPlainFraction).toInt()
        else -> 0
    }
    // TXT 当前所在章：按当前可见偏移反查。必须放在 visiblePlainOffset 之后。
    val txtChapterIndex = if (txtChapters.isEmpty()) {
        0
    } else {
        txtChapters.indexOfLast { it.startOffset <= visiblePlainOffset }.coerceAtLeast(0)
    }
    // 顶栏副行与 TTS、书签都用它。TXT 此前恒为空串只能显示「正文」，
    // 现在有章节识别了就跟着滚动位置走。
    val currentChapterTitle = if (epubBook != null) {
        epubBook!!.chapters.getOrNull(chapterIndex)?.title ?: ""
    } else {
        txtChapters.getOrNull(txtChapterIndex)?.title ?: ""
    }
    val plainPercent = when {
        plainContent.isEmpty() -> 0f
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        !plainListState.canScrollForward && plainListState.firstVisibleItemIndex > 0 -> 100f
        else -> (visiblePlainOffset * 100f / plainContent.length).coerceIn(0f, 100f)
    }
    val progressPercent = when {
        // 分页引擎的进度按全书字符偏移算（EPUB 分母为估算值，够显示与存档用）
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        epubBook != null -> epubPercent
        else -> plainPercent
    }

    // 阅读统计派生值（对照 web：bookReadingTimeMs / estimateBookReadingSpeed）
    val plainWordCount = remember(plainContent) { plainContent.count { !it.isWhitespace() } }
    val documentWordCount = if (epubBook != null) bookIndex?.totalChars ?: 0 else plainWordCount
    val savedBookReadingMs = kotlin.math.max(savedTotalReadingMs, sessions.sumOf { it.duration_ms }.coerceAtLeast(0L)).coerceAtLeast(0L)
    val effectiveWordCount = if (documentWordCount > 0) documentWordCount else kotlin.math.max(1, bookSize / 3)
    val currentWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * kotlin.math.max(0f, progressPercent - sessionStartProgress) / 100f).toLong())
    val readerSpeed: Int = run {
        val cur = if (activeReadingMs >= 10_000L && currentWords > 0)
            kotlin.math.round(currentWords / (activeReadingMs / 60_000.0)).toInt() else 0
        if (cur > 0) cur
        else if (savedBookReadingMs > 0L && progressPercent > 0f)
            kotlin.math.round((effectiveWordCount * progressPercent / 100f) / (savedBookReadingMs / 60_000.0)).toInt()
        else 300
    }
    val remainingWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * (1f - progressPercent / 100f)).toLong())
    val estimatedRemainingMs = if (readerSpeed > 0) kotlin.math.round(remainingWords / readerSpeed.toDouble() * 60_000.0).toLong() else 0L
    val bookmarksCount = notes.count { it.kind == "bookmark" }
    val inspirationsCount = inspirations.size

    val (paperBg, paperFg) = paperColors(readerSettings.background)
    val readerBrightness = readerSettings.brightness.coerceIn(45, 100)
    val dimAlpha = ((100 - readerBrightness) / 100f).coerceAtMost(0.58f)
    val effectivePaperBg = if (dimAlpha > 0.001f) lerp(paperBg, Color.Black, dimAlpha) else paperBg

    // 窗口底色刷成纸色：沉浸模式藏掉系统栏后腾出的区域在 Compose 画布之外，
    // 露出的是窗口底色 —— 不刷的话那里是一条黑带。离开阅读器恢复原样。
    val originalWindowBg = remember { (context as? Activity)?.window?.decorView?.background }
    DisposableEffect(effectivePaperBg) {
        val window = (context as? Activity)?.window
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(effectivePaperBg.toArgb()))
        onDispose { window?.setBackgroundDrawable(originalWindowBg) }
    }

    // T2：朗读句高亮背景色（与 TXT 保持一致）
    val sentenceHighlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)

    // T1/T2：当前章节各渲染块在全书文本中的全局偏移；以及 TTS 当前句在章节内的定位
    val chapterBase = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
    val blockGlobalOffsets = remember(epubBook, chapterStartOffsets, chapterIndex, chapterBlocks) {
        if (epubBook != null) computeBlockGlobalOffsets(chapterBlocks, chapterBase) else emptyList()
    }
    val ttsSentenceRangeInChapter = if (showTts && tts.status != "idle" && epubBook != null && contentText.isNotBlank()) {
        tts.currentSentenceRange
    } else null
    val ttsSentenceBlockIndex = remember(blockGlobalOffsets, ttsSentenceRangeInChapter) {
        if (ttsSentenceRangeInChapter != null) {
            val s = ttsSentenceRangeInChapter.first
            var idx = -1
            for (i in blockGlobalOffsets.indices) {
                val o = blockGlobalOffsets[i]
                if (o >= 0 && o <= s) idx = i else if (o > s) break
            }
            idx
        } else null
    }
    // 滚动聚焦块：优先 TTS 当前句，否则导航精准定位（T1 跳转用）
    var navFocusBlockIndex by remember { mutableStateOf<Int?>(null) }
    val focusBlockIndex = ttsSentenceBlockIndex ?: navFocusBlockIndex
    val epubBringRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusBlockIndex) {
        if (focusBlockIndex != null) epubBringRequester.bringIntoView()
    }

    /** 依据当前选区生成 locator_json（T1）。 */
    fun computeLocatorJson(): String? = when {
        epubBook != null && selectedGlobalOffset >= 0 -> makeOffsetLocator(selectedGlobalOffset)
        epubBook == null && selectedRangeStart >= 0 -> makeOffsetLocator(selectedRangeStart)
        else -> null
    }

    fun showNotice(msg: String) {
        scope.launch { snackbarHost.showSnackbar(msg) }
    }

    fun goToChapter(i: Int) {
        val book = epubBook ?: return
        val clamped = i.coerceIn(0, book.chapters.lastIndex)
        if (pagerEngineOn) {
            pagedJumpRequest.value = chapterStartOffsets.getOrElse(clamped) { 0 }
        }
        chapterIndex = clamped
        tts.stop()
        // 先清空，避免上一章内容残留闪现；随后在 IO 线程预加载本章块
        chapterBlocks = emptyList()
        chapterLoadJob?.cancel()
        chapterLoadJob = scope.launch {
            try {
                val blocks = withContext(Dispatchers.IO) {
                    epubDocument?.blocks(clamped) ?: emptyList()
                }
                if (chapterIndex != clamped) return@launch
                chapterBlocks = blocks
                val percent = if (book.chapters.isEmpty()) {
                    0f
                } else {
                    ((clamped + 1).toFloat() / book.chapters.size) * 100f
                }
                withContext(Dispatchers.IO) {
                    entry.epubRepository().saveProgress(book.id, clamped, percent)
                }
            } catch (_: CancellationException) {
                // 快速连续翻章时只保留最后一次请求。
            } catch (e: Exception) {
                if (chapterIndex == clamped) {
                    error = e.message ?: "章节加载失败"
                }
            }
        }
    }

    /** TXT 跳转统一入口：分页引擎开着走翻页定位，否则滚动列表。两条路都以全书字符偏移为准。 */
    fun jumpToPlainOffset(offset: Int) {
        if (pagerEngineOn) {
            pagedJumpRequest.value = offset
        } else if (plainChunks.isNotEmpty()) {
            scope.launch { plainListState.scrollToItem(chunkIndexForOffset(plainChunks, offset)) }
        }
    }

    // R6：进度滑块跳转（TXT 定位到百分比；EPUB 跳到对应章节；分页引擎按全书偏移精确定位）
    fun seekToPercent(p: Float) {
        if (epubBook != null) {
            if (pagerEngineOn) {
                val total = bookIndex?.totalChars ?: 0
                if (total > 0) pagedJumpRequest.value = (p.coerceIn(0f, 100f) / 100f * total).toInt()
            } else {
                val sz = epubBook!!.chapters.size
                if (sz > 0) goToChapter((p / 100f * sz).toInt().coerceIn(0, sz - 1))
            }
        } else if (plainContent.isNotEmpty()) {
            jumpToPlainOffset((p.coerceIn(0f, 100f) / 100f * plainContent.length).toInt())
        }
    }

    // 本次阅读计时（对照 web useReaderSession.activeReadingMs）
    androidx.compose.runtime.LaunchedEffect(bid) {
        while (true) {
            delay(1000)
            if (!isLoading && error == null) activeReadingMs += 1000
        }
    }

    // 阅读提醒：护眼提醒 + 阅读节奏提示（对照 web useReaderReminders）
    androidx.compose.runtime.LaunchedEffect(bid) {
        var eyeLast = 0L
        var rhythmLast = 0L
        while (true) {
            delay(1000)
            val st = settingsRef.value
            val eyeMin = st.eyeCareReminderMinutes.coerceAtLeast(1)
            val eyeThreshold = eyeMin * 60_000L
            if (activeReadingMs >= eyeLast + eyeThreshold) {
                eyeLast = (activeReadingMs / eyeThreshold) * eyeThreshold
                showNotice("已连续阅读 ${eyeMin} 分钟，建议休息一下眼睛。")
            }
            if (st.readingRhythmReminderEnabled) {
                val rMin = st.readingRhythmReminderMinutes.coerceAtLeast(1)
                val rThreshold = rMin * 60_000L
                if (activeReadingMs >= rhythmLast + rThreshold) {
                    rhythmLast = (activeReadingMs / rThreshold) * rThreshold
                    showNotice("已读 ${rMin} 分钟，注意休息。")
                }
            }
        }
    }

    // 加载书籍（路由 reader/{bookId} 驱动；复用 EpubRepository / BookRepository）
    androidx.compose.runtime.LaunchedEffect(bid) {
        if (bid.isBlank()) {
            error = "未指定书籍"
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        try {
            withContext(Dispatchers.IO) {
                val meta = entry.bookRepository().getById(bid)
                bookTitle = meta?.title ?: "未命名书籍"
                bookAuthor = meta?.author
                bookOriginalFile = meta?.original_file_name
                bookSize = meta?.size ?: 0
                when (meta?.format) {
                    "epub" -> {
                        val cached = entry.epubRepository().cached(bid)
                        val book = cached ?: meta.local_uri?.let { uri: String ->
                            runCatching { entry.epubRepository().openEpub(Uri.parse(uri)) }.getOrNull()
                        }
                        if (book == null) {
                            error = "本书暂无可离线打开的 EPUB 正文（需重新导入或同步下载）。"
                        } else {
                            val idx = entry.epubRepository().loadProgress(bid)
                                .coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                            savedEpubOffsetInChapter = entry.epubRepository().loadProgressOffset(bid)
                            // 预加载：当前章块 + 全书索引（搜索 / 字数 / 偏移），均在 IO 线程完成
                            val document = EpubDocument(book)
                            val blocks = document.blocks(idx)
                            val index = buildBookIndex(book)
                            epubDocument?.close()
                            epubDocument = document
                            chapterBlocks = blocks
                            bookIndex = index
                            epubBook = book
                            chapterIndex = idx
                            savedTotalReadingMs = entry.readingProgressDao().getByBook(bid)?.total_reading_time_ms ?: 0L
                            sessionStartProgress = if (book.chapters.isEmpty()) 0f else ((chapterIndex + 1).toFloat() / book.chapters.size) * 100f
                        }
                    }

                    else -> {
                        if ((meta?.size ?: 0) > MAX_IN_MEMORY_TEXT_BYTES) {
                            throw IllegalArgumentException(
                                "TXT/Markdown 文件过大（超过 8 MB），当前版本为避免内存溢出暂不整本载入。" +
                                    "请先分割文件，后续版本将支持分块阅读。",
                            )
                        }
                        val fromUri = meta?.local_uri?.let { uri: String ->
                            runCatching {
                                context.contentResolver.openInputStream(Uri.parse(uri))
                                    ?.use { PlainTextDecoder.decode(it.readBytes()).text }
                            }.getOrNull()
                        }
                        plainContent = fromUri ?: entry.bookContentDao().getByBook(bid)?.reader_preview ?: ""
                        savedPlainPercent = entry.readingProgressDao().getByBook(bid)?.progress_percent ?: 0f
                        savedTotalReadingMs = entry.readingProgressDao().getByBook(bid)?.total_reading_time_ms ?: 0L
                        sessionStartProgress = savedPlainPercent
                        if (plainContent.isBlank()) error = "本书暂无可阅读的正文（需重新导入或同步下载）。"
                    }
                }
                // R5：用当前章节（EPUB 进度章节 / TXT 为 0）初始化「最近浏览」置顶项
                recentChapters.clear()
                recentChapters.add(chapterIndex)
            }
        } catch (e: Exception) {
            error = e.message ?: "打开失败"
        } finally {
            isLoading = false
        }
    }

    // 纯文本分块滚动进度落库，500ms 防抖避免频繁写盘（分页引擎开启时由下面的翻页持久化接管）
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, plainChunks, pagerEngineOn) {
        if (bid.isBlank() || epubBook != null || plainChunks.isEmpty() || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            Triple(
                plainListState.firstVisibleItemIndex,
                plainListState.firstVisibleItemScrollOffset,
                plainListState.canScrollForward,
            )
        }
            .debounce(500)
            .collect {
                val index = plainListState.firstVisibleItemIndex
                val chunk = plainChunks.getOrNull(index)
                val item = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (item != null && item.size > 0) {
                    plainListState.firstVisibleItemScrollOffset.toFloat() / item.size
                } else {
                    0f
                }
                val offset = if (chunk != null) {
                    chunk.startOffset + (chunk.text.length * fraction).toInt()
                } else {
                    0
                }
                val percent = when {
                    !plainListState.canScrollForward && index > 0 -> 100f
                    plainContent.isEmpty() -> 0f
                    else -> (offset * 100f / plainContent.length).coerceIn(0f, 100f)
                }
                entry.readingProgressDao().upsert(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = null,
                        updated_at = nowIso(),
                    ),
                )
            }
    }

    // 翻页进度落库（分页引擎侧），与滚动侧同样 500ms 防抖、同一张表同一套字段
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, pagerEngineOn, pagedSource) {
        if (bid.isBlank() || !pagerEngineOn) return@LaunchedEffect
        snapshotFlow { pagedAbsOffset to pagedPercent }
            .debounce(500)
            .collect { (off, pct) ->
                if (off < 0) return@collect
                val source = pagedSource ?: return@collect
                if (epubBook != null) {
                    val ci = source.chapterIndexFor(off)
                    val chapterOffset = off - source.chapterStartAbs(ci)
                    entry.epubRepository().saveProgress(bid, ci, pct, chapterOffset)
                } else {
                    entry.readingProgressDao().upsert(
                        ReadingProgressEntity(
                            book_id = bid,
                            progress_percent = pct,
                            completion_state = if (pct >= 99.9f) "finished" else "reading",
                            current_location_json = null,
                            updated_at = nowIso(),
                        ),
                    )
                }
            }
    }

    // 纯文本恢复上次滚动位置（分页引擎自己按 initialOffset 恢复；从翻页切回滚动时接上当前页位置）
    androidx.compose.runtime.LaunchedEffect(plainChunks, savedPlainPercent, pagerEngineOn) {
        if (epubBook == null && plainChunks.isNotEmpty() && !pagerEngineOn) {
            val targetOffset = if (pagedAbsOffset >= 0) {
                pagedAbsOffset
            } else if (savedPlainPercent > 0f) {
                (savedPlainPercent.coerceIn(0f, 100f) / 100f * plainContent.length).toInt()
            } else {
                return@LaunchedEffect
            }
            plainListState.scrollToItem(chunkIndexForOffset(plainChunks, targetOffset))
        }
    }

    // SE4：精确跳转 —— 打开阅读器并定位到该高亮/笔记所在位置（优先 locator_json 行内偏移，兜底 chapter_title / progress_percent）。
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, plainContent, highlights, notes, pendingHighlightId) {
        val hid = pendingHighlightId ?: return@LaunchedEffect
        if (isLoading || error != null) return@LaunchedEffect
        val target = highlights.firstOrNull { it.id == hid }
            ?: notes.firstOrNull { it.id == hid }
            ?: run {
                pendingHighlightId = null
                return@LaunchedEffect
            }
        val hl = target as? HighlightEntity
        val nt = target as? NoteEntity
        val chapterTitle = hl?.chapter_title ?: nt?.chapter_title
        val targetProgress = hl?.progress_percent ?: nt?.progress_percent
        val locOffset = parseLocatorOffset(hl?.locator_json ?: nt?.locator_json)
        if (epubBook != null) {
            val book = epubBook!!
            if (locOffset != null && bookIndex != null) {
                // T1：按全局偏移精确定位到所属章节，并进一步滚动到章内偏移所在块
                val ci = chapterStartOffsets.indexOfLast { it <= locOffset }.coerceIn(0, book.chapters.lastIndex)
                goToChapter(ci)
                if (pagerEngineOn) {
                    pagedJumpRequest.value = locOffset
                    navFocusBlockIndex = null
                } else {
                    val inChapter = locOffset - chapterStartOffsets.getOrElse(ci) { 0 }
                    // 在 IO 线程加载目标章块，避免主线程 Zip I/O 造成 ANR
                    val blocks = withContext(Dispatchers.IO) {
                        epubDocument?.blocks(ci) ?: emptyList()
                    }
                    navFocusBlockIndex = blockIndexForChapterOffset(blocks, inChapter)
                }
            } else {
                val idx = if (chapterTitle != null) {
                    val exact = book.chapters.indexOfFirst { it.title == chapterTitle }.takeIf { it >= 0 }
                    exact ?: progressToChapterIndex(book, targetProgress)
                } else {
                    progressToChapterIndex(book, targetProgress)
                }
                goToChapter(idx)
                navFocusBlockIndex = null
            }
        } else if (plainContent.isNotBlank()) {
            if (locOffset != null && plainContent.isNotEmpty()) {
                jumpToPlainOffset(locOffset)
            } else if (targetProgress != null) {
                jumpToPlainOffset((targetProgress.coerceIn(0f, 100f) / 100f * plainContent.length).toInt())
            }
        }
        pendingHighlightId = null
    }

    // 打开 TTS：从当前正文（或跨会话续读位置）开始
    fun openTts() {
        if (contentText.isBlank()) {
            showNotice("当前没有可朗读的文字。")
            return
        }
        // R1：API 33+ 运行时申请通知权限，否则锁屏媒体控制无法显示
        if (Build.VERSION.SDK_INT >= 33) {
            val act = context as? Activity
            if (act != null && ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        tts.play(contentText, bookTitle, currentChapterTitle.ifBlank { "正文" }, ttsResumeOffset)
        showTts = true
    }

    // R2：朗读时把正文跟到当前句（TXT），与网页 ttsSyncToReader 对齐。
    // 分页引擎侧同样生效：翻到句子所在页。
    LaunchedEffect(tts.status, tts.currentSentenceRange) {
        if (showTts && tts.status != "idle") {
            val start = tts.currentSentenceRange.first
            if (isTxt && plainContent.isNotEmpty()) {
                if (start in 0 until plainContent.length) {
                    jumpToPlainOffset(start)
                }
            } else if (pagerEngineOn && epubBook != null) {
                pagedJumpRequest.value = chapterStartOffsets.getOrElse(chapterIndex) { 0 } + start
            }
        }
    }

    // ── 笔记对话框 ──────────────────────────────────────────────
    if (noteOpen) {
        AlertDialog(
            onDismissRequest = { noteOpen = false },
            title = { Text("新建笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedText.isNotBlank()) {
                        Text("摘录：${selectedText.take(60)}${if (selectedText.length > 60) "…" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("笔记内容") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 同高亮保存：先快照再进协程，否则下面同步清空后 IO 线程读到空串
                    val snapshotText = selectedText
                    val snapshotBody = noteBody
                    val snapshotLocator = computeLocatorJson()
                    scope.launch(Dispatchers.IO) {
                        entry.noteDao().upsert(
                            NoteEntity(
                                id = UUID.randomUUID().toString(),
                                book_id = bid.ifBlank { null },
                                title = (snapshotBody.ifBlank { snapshotText }).take(40),
                                body = snapshotBody,
                                excerpt = snapshotText.takeIf { it.isNotBlank() },
                                chapter_title = currentChapterTitle.ifBlank { null },
                                progress_percent = progressPercent,
                                kind = "note",
                                locator_json = snapshotLocator,
                                payload = "{}",
                                created_at = nowIso(),
                                device_id = null,
                                revision = 1,
                                updated_at = nowIso(),
                                deleted_at = null,
                            ),
                        )
                    }
                    noteBody = ""
                    noteOpen = false
                    selectedText = ""
                    showNotice("已保存笔记")
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteOpen = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (controlsVisible) {
                TopAppBar(
                    title = {
                        // 顶栏此前挤了 7 个操作图标，标题只剩一个字的宽度，
                        // 书名被压成竖排单字。图标已精简，这里再补上截断兜底。
                        Column {
                            Text(
                                bookTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${if (epubBook != null) "EPUB" else "TXT"} · ${currentChapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    // 「目录」与「设置」不再放在顶栏：底栏已经有这两个入口，且底栏同时可见，
                    // 重复占位只会把标题挤没。次要动作收进「更多」。
                    actions = {
                        IconButton(onClick = { if (showTts) { tts.stop(); showTts = false } else openTts() }) {
                            Icon(Icons.Filled.Headphones, contentDescription = "听书")
                        }
                        IconButton(onClick = { sheet = SheetType.AI_ASSIST }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 助手")
                        }
                        Box {
                            IconButton(onClick = { showReaderOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showReaderOverflow,
                                onDismissRequest = { showReaderOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("笔记与标注") },
                                    leadingIcon = { Icon(Icons.Filled.BorderColor, contentDescription = null) },
                                    onClick = {
                                        showReaderOverflow = false
                                        sheet = SheetType.NOTES
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("书内搜索") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    onClick = {
                                        showReaderOverflow = false
                                        searchQuery = ""
                                        sheet = SheetType.SEARCH
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("隐藏工具栏") },
                                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                                    onClick = {
                                        showReaderOverflow = false
                                        controlsVisible = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (controlsVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    if (showTts) {
                        TtsBar(
                            tts = tts,
                            chapterLabel = currentChapterTitle.ifBlank { "正文" },
                            onPersistTts = { p, v, id, t ->
                                settingsVm.updateReader {
                                    copy(
                                        ttsPitch = p,
                                        ttsVolume = v,
                                        ttsVoiceId = id,
                                        ttsTimedStopMinutes = t,
                                    )
                                }
                            },
                        ) {
                            tts.stop()
                            showTts = false
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FooterBtn(Icons.Filled.Menu, "目录") { sheet = SheetType.TOC }
                            FooterBtn(Icons.Filled.BarChart, "进度") { sheet = SheetType.PROGRESS }
                            FooterBtn(Icons.Filled.Lightbulb, "灵感") { sheet = SheetType.INSPIRATION }
                            FooterBtn(Icons.Filled.Palette, "主题") { sheet = SheetType.THEME }
                            FooterBtn(Icons.Filled.Settings, "设置") { sheet = SheetType.SETTINGS }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // 翻页引擎下点屏幕中央即可呼出菜单，悬浮按钮纯属多余还压在正文上；
            // 滚动模式没有中央点按手势，仍需要它。
            if (!controlsVisible && !pagerEngineOn) {
                FloatingActionButton(onClick = { controlsVisible = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "展开菜单")
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                // 背景必须在 padding 之前铺：沉浸模式藏掉系统栏后，腾出的区域
                // 也要是纸色，否则那里露出的是窗口底色（黑条）
                .background(effectivePaperBg)
                .padding(padding)
                // 沉浸时内容延伸进了刘海区（SHORT_EDGES），正文要让开打孔摄像头那一条；
                // 纸色背景仍然铺满整屏（在 padding 之前），所以让出来的部分不是黑边
                .then(
                    if (readerSettings.immersiveMode && !controlsVisible) {
                        Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                    } else {
                        Modifier
                    },
                ),
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(error!!, color = paperFg)
                }

                pagerEngineOn && pagedSource != null -> {
                    // 试验分页引擎：TXT 与 EPUB 共用排版/手势/高亮链路。
                    // EPUB 首版只排文字块；图片分页与四档动画仍按 P4 后续刀次推进。
                    PagedReaderHost(
                        source = pagedSource,
                        fontSizeSp = readerSettings.fontSize,
                        lineHeightMultiplier = readerSettings.lineHeight,
                        paragraphSpacing = readerSettings.paragraphSpacing,
                        pageMarginDp = readerSettings.pageMargin,
                        fontWeightBold = readerSettings.fontWeightBold,
                        showReaderInfo = readerSettings.showReaderInfo,
                        chineseTypography = readerSettings.chineseTypography,
                        tapZoneMode = readerSettings.tapZoneMode,
                        pageTurnEffect = readerSettings.pageTurnEffect,
                        textColor = paperFg,
                        initialOffset = when {
                            pagedAbsOffset >= 0 -> pagedAbsOffset
                            epubBook != null ->
                                chapterStartOffsets.getOrElse(chapterIndex) { 0 } + savedEpubOffsetInChapter
                            visiblePlainOffset > 0 -> visiblePlainOffset
                            else -> (savedPlainPercent.coerceIn(0f, 100f) / 100f * plainContent.length).toInt()
                        },
                        jumpRequest = pagedJumpRequest,
                        onPositionChanged = { off, pct ->
                            pagedAbsOffset = off
                            pagedPercent = pct
                            if (epubBook != null) {
                                val ci = pagedSource.chapterIndexFor(off)
                                if (ci != chapterIndex) goToChapter(ci)
                            }
                        },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        store = entry.pageIndexStore(),
                        contentKey = bid,
                        ttsRangeAbs = if (showTts && tts.status != "idle") {
                            if (epubBook != null) {
                                val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
                                (base + tts.currentSentenceRange.first) to
                                    (base + tts.currentSentenceRange.second)
                            } else {
                                tts.currentSentenceRange
                            }
                        } else {
                            null
                        },
                        onSelect = { text, absStart ->
                            selectedText = text
                            if (epubBook != null) {
                                selectedGlobalOffset = absStart
                                selectedRangeStart = -1
                            } else {
                                selectedRangeStart = absStart
                                selectedGlobalOffset = -1
                            }
                        },
                        selectionCleared = selectedText.isBlank(),
                        selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        ttsHighlightColor = sentenceHighlightBg,
                        persistentHighlights = remember(highlights) {
                            highlights.mapNotNull { h ->
                                val start = parseLocatorOffset(h.locator_json) ?: return@mapNotNull null
                                val len = h.text.length
                                if (len <= 0) return@mapNotNull null
                                (start until start + len) to
                                    highlightColor(h.color ?: "yellow").copy(alpha = 0.42f)
                            }
                        },
                    )
                }

                epubBook != null -> {
                    val book = epubBook!!
                    val chapter = book.chapters.getOrNull(chapterIndex)
                    if (chapter == null || chapterBlocks.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("本章暂无可读内容。", color = paperFg)
                        }
                    } else if (readerSettings.readerMode == "paged") {
                        PagedEpubView(
                            blocks = chapterBlocks,
                            fontSize = readerSettings.fontSize,
                            lineHeight = readerSettings.lineHeight,
                            fontWeightBold = readerSettings.fontWeightBold,
                            pageMargin = readerSettings.pageMargin,
                            paperFg = paperFg,
                            tapZoneMode = readerSettings.tapZoneMode,
                            pageTurnEffect = readerSettings.pageTurnEffect,
                            chapterIndex = chapterIndex,
                            canPrev = chapterIndex > 0,
                            canNext = chapterIndex < book.chapters.lastIndex,
                            onPrev = { goToChapter(chapterIndex - 1) },
                            onNext = { goToChapter(chapterIndex + 1) },
                            onSelectBlock = { text, off -> selectedText = text; selectedGlobalOffset = off },
                            blockGlobalOffsets = blockGlobalOffsets,
                            chapterBase = chapterBase,
                            ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                            focusBlockIndex = focusBlockIndex,
                            sentenceHighlightBg = sentenceHighlightBg,
                            bringRequester = epubBringRequester,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(chapterBlocks) { idx, block ->
                                when (block) {
                                    is DocBlock.Text -> {
                                        val gOff = blockGlobalOffsets.getOrElse(idx) { -1 }
                                        val ann = buildSentenceHighlighted(
                                            block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg,
                                        )
                                        Text(
                                            text = ann,
                                            style = TextStyle(
                                                textAlign = TextAlign.Justify,
                                                lineHeight = (readerSettings.fontSize * readerSettings.lineHeight).sp,
                                                textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (readerSettings.fontSize * 2).sp),
                                            ),
                                            fontSize = readerSettings.fontSize.sp,
                                            fontWeight = if (block.isHeading || readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                            color = paperFg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (idx == focusBlockIndex) Modifier.bringIntoViewRequester(epubBringRequester) else Modifier)
                                                .clickable {
                                                    selectedText = block.text
                                                    selectedGlobalOffset = gOff
                                                    selectedRangeStart = -1
                                                },
                                        )
                                    }

                                    is DocBlock.Image -> AsyncImage(
                                        model = java.io.File(block.path),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = plainListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = plainChunks,
                            key = { _, chunk -> chunk.startOffset },
                        ) { _, chunk ->
                            val ttsRange = if (showTts && tts.status != "idle" && isTxt) {
                                tts.currentSentenceRange
                            } else {
                                0 to 0
                            }
                            val localStart = (ttsRange.first - chunk.startOffset).coerceIn(0, chunk.text.length)
                            val localEnd = (ttsRange.second - chunk.startOffset).coerceIn(0, chunk.text.length)
                            val annotated = remember(chunk.text, localStart, localEnd, sentenceHighlightBg) {
                                if (localEnd > localStart) {
                                    AnnotatedString.Builder(chunk.text).apply {
                                        addStyle(
                                            SpanStyle(background = sentenceHighlightBg),
                                            localStart,
                                            localEnd,
                                        )
                                    }.toAnnotatedString()
                                } else {
                                    AnnotatedString(chunk.text)
                                }
                            }
                            var selection by remember(chunk.startOffset) { mutableStateOf(TextRange.Zero) }
                            BasicTextField(
                                value = TextFieldValue(annotatedString = annotated, selection = selection),
                                onValueChange = { value ->
                                    selection = value.selection
                                    if (value.selection != TextRange.Zero && value.selection.length > 0) {
                                        selectedText = chunk.text.substring(value.selection.start, value.selection.end)
                                        selectedRangeStart = chunk.startOffset + value.selection.start
                                        selectedGlobalOffset = -1
                                    } else if (selectedRangeStart in chunk.startOffset until (chunk.startOffset + chunk.text.length)) {
                                        selectedText = ""
                                        selectedRangeStart = -1
                                    }
                                },
                                readOnly = true,
                                textStyle = TextStyle(
                                    fontSize = readerSettings.fontSize.sp,
                                    lineHeight = (readerSettings.fontSize * readerSettings.lineHeight).sp,
                                    color = paperFg,
                                    textAlign = TextAlign.Justify,
                                    fontWeight = if (readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 显示进度条：正文底部 2dp 细线（此前该开关是摆设）
            if (readerSettings.showProgressBar && !isLoading && error == null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                    trackColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            }

            // 选中文字工具条（对照 web 选中工具栏）
            if (selectedText.isNotBlank()) {
                SelectionToolbar(
                    selectedText = selectedText,
                    showColorRow = showColorRow,
                    onToggleColor = { showColorRow = !showColorRow },
                    onPickColor = { color ->
                        val locator = computeLocatorJson()
                        // 必须先落到局部变量再进协程：下面马上把 selectedText 清空，
                        // IO 协程晚一步才读的话高亮就存成空串（真机翻页模式实测踩过）。
                        val snapshotText = selectedText
                        scope.launch(Dispatchers.IO) {
                            entry.highlightDao().upsert(
                                HighlightEntity(
                                    id = UUID.randomUUID().toString(),
                                    book_id = bid,
                                    text = snapshotText,
                                    note = null,
                                    color = color,
                                    chapter_title = currentChapterTitle.ifBlank { null },
                                    progress_percent = progressPercent,
                                    locator_json = locator,
                                    payload = "{}",
                                    created_at = nowIso(),
                                    device_id = null,
                                    revision = 1,
                                    updated_at = nowIso(),
                                    deleted_at = null,
                                ),
                            )
                        }
                        showColorRow = false
                        selectedText = ""
                        selectedGlobalOffset = -1
                        selectedRangeStart = -1
                        showNotice("已高亮")
                    },
                    onAiExplain = { sheet = SheetType.AI_EXPLAIN },
                    onInspiration = { sheet = SheetType.INSPIRATION },
                    onNote = { noteOpen = true },
                    onCopy = { clipboard.setText(AnnotatedString(selectedText)); showNotice("已复制") },
                    onSearch = { searchQuery = selectedText; sheet = SheetType.SEARCH },
                    onClear = { selectedText = ""; showColorRow = false; selectedGlobalOffset = -1; selectedRangeStart = -1 },
                )
            }

            // 底部弹层（不新建路由，内部状态切换）
            sheet?.let { type ->
                ModalBottomSheet(
                    onDismissRequest = { sheet = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    when (type) {
                        SheetType.TOC -> TocSheet(
                            titles = epubBook?.chapters?.map { it.title }
                                ?: txtChapters.map { it.title },
                            current = if (epubBook != null) chapterIndex else txtChapterIndex,
                            recent = recentChapters.toList(),
                            onPick = {
                                if (!recentChapters.contains(it)) {
                                    recentChapters.add(0, it)
                                    if (recentChapters.size > 5) recentChapters.removeAt(recentChapters.lastIndex)
                                }
                                if (epubBook != null) {
                                    goToChapter(it)
                                } else {
                                    // TXT 跳章 = 定位到该章起始偏移（翻页/滚动两种视图都认它）
                                    txtChapters.getOrNull(it)?.let { c -> jumpToPlainOffset(c.startOffset) }
                                }
                                sheet = null
                            },
                        )

                        SheetType.NOTES -> NotesSheet(
                            highlights = highlights,
                            notes = notes,
                            inspirations = inspirations,
                            onAddBookmark = {
                                scope.launch(Dispatchers.IO) {
                                    entry.noteDao().upsert(
                                        NoteEntity(
                                            id = UUID.randomUUID().toString(),
                                            book_id = bid.ifBlank { null },
                                            title = "书签 · ${currentChapterTitle.ifBlank { "正文" }}",
                                            body = "",
                                            excerpt = null,
                                            chapter_title = currentChapterTitle.ifBlank { null },
                                            progress_percent = progressPercent,
                                            kind = "bookmark",
                                            locator_json = null,
                                            payload = "{}",
                                            created_at = nowIso(),
                                            device_id = null,
                                            revision = 1,
                                            updated_at = nowIso(),
                                            deleted_at = null,
                                        ),
                                    )
                                }
                                showNotice("已添加书签")
                            },
                            onDeleteHighlight = { h ->
                                scope.launch(Dispatchers.IO) {
                                    entry.highlightDao().upsert(h.copy(deleted_at = nowIso()))
                                }
                            },
                            onDeleteNote = { n ->
                                scope.launch(Dispatchers.IO) {
                                    entry.noteDao().upsert(n.copy(deleted_at = nowIso()))
                                }
                            },
                            onChangeHighlightColor = { h, c ->
                                scope.launch(Dispatchers.IO) {
                                    entry.highlightDao().upsert(h.copy(color = c, updated_at = nowIso(), revision = h.revision + 1))
                                }
                            },
                            onEditHighlightNote = { h, note ->
                                scope.launch(Dispatchers.IO) {
                                    entry.highlightDao().upsert(h.copy(note = note.ifBlank { null }, updated_at = nowIso(), revision = h.revision + 1))
                                }
                            },
                            onHighlightToNote = { h ->
                                scope.launch(Dispatchers.IO) {
                                    entry.noteDao().upsert(
                                        NoteEntity(
                                            id = UUID.randomUUID().toString(),
                                            book_id = bid.ifBlank { null },
                                            title = "笔记：${h.chapter_title ?: bookTitle}",
                                            body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                                            excerpt = h.text,
                                            chapter_title = h.chapter_title,
                                            progress_percent = h.progress_percent ?: 0f,
                                            kind = "note",
                                            locator_json = h.locator_json,
                                            payload = "{}",
                                            created_at = nowIso(),
                                            device_id = null,
                                            revision = 1,
                                            updated_at = nowIso(),
                                            deleted_at = null,
                                        ),
                                    )
                                }
                                showNotice("已转为笔记")
                            },
                            onHighlightToInspiration = { h ->
                                scope.launch(Dispatchers.IO) {
                                    entry.inspirationDao().upsert(
                                        InspirationEntity(
                                            id = UUID.randomUUID().toString(),
                                            title = "高亮灵感：${h.text.take(24)}",
                                            body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                                            type = "note",
                                            status = "inbox",
                                            source_book_id = bid.ifBlank { null },
                                            payload = buildInspirationPayload(bid, bookTitle, h.chapter_title ?: "", h.text, h.progress_percent ?: 0f, bookAuthor = bookAuthor),
                                            created_at = nowIso(),
                                            device_id = null,
                                            revision = 1,
                                            updated_at = nowIso(),
                                            deleted_at = null,
                                        ),
                                    )
                                }
                                showNotice("已转为灵感")
                            },
                            onJumpToHighlight = { h ->
                                // R4：复用 SE4「按高亮 id 精确定位」逻辑（pendingHighlightId 驱动 LaunchedEffect）
                                pendingHighlightId = h.id
                                sheet = null
                            },
                            onExportHighlights = {
                                val sb = StringBuilder()
                                sb.appendLine("# 《${bookTitle}》书摘")
                                highlights.groupBy { it.chapter_title ?: "" }.forEach { (chapter, items) ->
                                    sb.appendLine()
                                    sb.appendLine("## ${if (chapter.isBlank()) "未分类" else chapter}")
                                    items.forEachIndexed { i, h ->
                                        sb.appendLine("${i + 1}. ${h.text}")
                                        h.note?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   批注：$it") }
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND)
                                intent.type = "text/plain"
                                intent.putExtra(Intent.EXTRA_TITLE, "《${bookTitle}》书摘")
                                intent.putExtra(Intent.EXTRA_TEXT, sb.toString())
                                context.startActivity(Intent.createChooser(intent, "导出书摘"))
                            },
                        )

                        SheetType.AI_ASSIST -> AiAssistSheet(
                            aiClient = entry.aiClient(),
                            bookTitle = bookTitle,
                            chapterTitle = currentChapterTitle,
                            contextText = selectedText.ifBlank { contentText },
                        )

                        SheetType.AI_EXPLAIN -> AiExplainSheet(
                            aiClient = entry.aiClient(),
                            selectedText = selectedText,
                            bookTitle = bookTitle,
                            categories = allCategories,
                            tags = allTags,
                            onCreateCategory = { name ->
                                val id = "mobile-category-${UUID.randomUUID()}"
                                scope.launch(Dispatchers.IO) {
                                    entry.categoryDao().upsert(CategoryEntity(id = id, name = name, created_at = nowIso(), updated_at = nowIso()))
                                }
                                id
                            },
                            onCreateTag = { name ->
                                val id = "mobile-tag-${UUID.randomUUID()}"
                                scope.launch(Dispatchers.IO) {
                                    entry.tagDao().upsert(TagEntity(id = id, name = name, type = "inspiration", created_at = nowIso(), updated_at = nowIso()))
                                }
                                id
                            },
                            onSaveInspiration = { body, tags, categoryIds ->
                                val snapshotText = selectedText // 进协程前快照，防止随后清空导致存空串
                                scope.launch(Dispatchers.IO) {
                                    entry.inspirationDao().upsert(
                                        InspirationEntity(
                                            id = UUID.randomUUID().toString(),
                                            title = "AI 解读：${snapshotText.take(24)}",
                                            body = body,
                                            type = "note",
                                            status = "inbox",
                                            source_book_id = bid.ifBlank { null },
                                            payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                                            created_at = nowIso(),
                                            device_id = null,
                                            revision = 1,
                                            updated_at = nowIso(),
                                            deleted_at = null,
                                        ),
                                    )
                                }
                                sheet = null
                                showNotice("已存入灵感")
                            },
                        )

                        SheetType.INSPIRATION -> InspirationSheet(
                            bookTitle = bookTitle,
                            chapterTitle = currentChapterTitle,
                            excerpt = selectedText,
                            progressPercent = progressPercent,
                            categories = allCategories,
                            tags = allTags,
                            onCreateCategory = { name ->
                                val id = "mobile-category-${UUID.randomUUID()}"
                                scope.launch(Dispatchers.IO) {
                                    entry.categoryDao().upsert(CategoryEntity(id = id, name = name, created_at = nowIso(), updated_at = nowIso()))
                                }
                                id
                            },
                            onCreateTag = { name ->
                                val id = "mobile-tag-${UUID.randomUUID()}"
                                scope.launch(Dispatchers.IO) {
                                    entry.tagDao().upsert(TagEntity(id = id, name = name, type = "inspiration", created_at = nowIso(), updated_at = nowIso()))
                                }
                                id
                            },
                            onSave = { title, body, tags, categoryIds ->
                                val snapshotText = selectedText // 进协程前快照，防止随后清空导致存空串
                                scope.launch(Dispatchers.IO) {
                                    entry.inspirationDao().upsert(
                                        InspirationEntity(
                                            id = UUID.randomUUID().toString(),
                                            title = title,
                                            body = body,
                                            type = "note",
                                            status = "inbox",
                                            source_book_id = bid.ifBlank { null },
                                            payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                                            created_at = nowIso(),
                                            device_id = null,
                                            revision = 1,
                                            updated_at = nowIso(),
                                            deleted_at = null,
                                        ),
                                    )
                                }
                                selectedText = ""
                                sheet = null
                                showNotice("已保存灵感，并记录来源阅读位置")
                            },
                        )

                        SheetType.SETTINGS -> SettingsSheet(
                            fontSize = readerSettings.fontSize,
                            lineHeight = readerSettings.lineHeight,
                            background = readerSettings.background,
                            bold = readerSettings.fontWeightBold,
                            brightness = readerSettings.brightness,
                            readerMode = readerSettings.readerMode,
                            pagerEngineMode = readerSettings.pagerEngineMode,
                            epubPagerEngineMode = readerSettings.epubPagerEngineMode,
                            pageTurnEffect = readerSettings.pageTurnEffect,
                            tapZoneMode = readerSettings.tapZoneMode,
                            pageMargin = readerSettings.pageMargin,
                            paragraphSpacing = readerSettings.paragraphSpacing,
                            eyeCareMin = readerSettings.eyeCareReminderMinutes,
                            rhythmEnabled = readerSettings.readingRhythmReminderEnabled,
                            rhythmMin = readerSettings.readingRhythmReminderMinutes,
                            onFontSize = { settingsVm.updateReader { copy(fontSize = it) } },
                            onLineHeight = { settingsVm.updateReader { copy(lineHeight = it) } },
                            onBackground = { settingsVm.updateReader { copy(background = it) } },
                            onBrightness = { settingsVm.updateReader { copy(brightness = it) } },
                            onBold = { settingsVm.updateReader { copy(fontWeightBold = it) } },
                            onReaderMode = { settingsVm.updateReader { copy(readerMode = it) } },
                            onPagerEngineMode = { settingsVm.updateReader { copy(pagerEngineMode = it) } },
                            onEpubPagerEngineMode = { settingsVm.updateReader { copy(epubPagerEngineMode = it) } },
                            onPageTurnEffect = { settingsVm.updateReader { copy(pageTurnEffect = it) } },
                            onTapZoneMode = { settingsVm.updateReader { copy(tapZoneMode = it) } },
                            onPageMargin = { settingsVm.updateReader { copy(pageMargin = it) } },
                            onParagraphSpacing = { settingsVm.updateReader { copy(paragraphSpacing = it) } },
                            onEyeCareMin = { settingsVm.updateReader { copy(eyeCareReminderMinutes = it) } },
                            onRhythmEnabled = { settingsVm.updateReader { copy(readingRhythmReminderEnabled = it) } },
                            onRhythmMin = { settingsVm.updateReader { copy(readingRhythmReminderMinutes = it) } },
                            immersiveMode = readerSettings.immersiveMode,
                            showReaderInfo = readerSettings.showReaderInfo,
                            chineseTypography = readerSettings.chineseTypography,
                            keepAwake = readerSettings.keepAwake,
                            showProgressBar = readerSettings.showProgressBar,
                            autoHideSeconds = readerSettings.autoHideSeconds,
                            onImmersive = { settingsVm.updateReader { copy(immersiveMode = it) } },
                            onShowInfo = { settingsVm.updateReader { copy(showReaderInfo = it) } },
                            onChineseTypo = { settingsVm.updateReader { copy(chineseTypography = it) } },
                            onKeepAwake = { settingsVm.updateReader { copy(keepAwake = it) } },
                            onShowProgress = { settingsVm.updateReader { copy(showProgressBar = it) } },
                            onAutoHide = { settingsVm.updateReader { copy(autoHideSeconds = it) } },
                            onBookInfo = { sheet = SheetType.BOOK_INFO },
                        )

                        SheetType.THEME -> ThemeSheet(
                            background = readerSettings.background,
                            onBackground = { settingsVm.updateReader { copy(background = it) } },
                        )

                        SheetType.PROGRESS -> ProgressSheet(
                            epubBook = epubBook,
                            chapterIndex = chapterIndex,
                            currentChapterTitle = currentChapterTitle,
                            progressPercent = progressPercent,
                            activeReadingMs = activeReadingMs,
                            readerSpeed = readerSpeed,
                            estimatedRemainingMs = estimatedRemainingMs,
                            savedBookReadingMs = savedBookReadingMs,
                            inspirationsCount = inspirationsCount,
                            bookmarksCount = bookmarksCount,
                            onChapter = { goToChapter(it) },
                            onSeekPercent = { seekToPercent(it) },
                            isTxt = isTxt,
                        )

                        SheetType.SEARCH -> SearchSheet(
                            book = epubBook,
                            plainContent = plainContent,
                            chapterStartOffsets = chapterStartOffsets,
                            chapterTitles = chapterTitles,
                            totalChars = bookIndex?.totalChars ?: 0,
                            isTxt = isTxt,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onJump = { result ->
                                if (result.chapterIndex >= 0) {
                                    goToChapter(result.chapterIndex)
                                } else if (plainContent.isNotEmpty()) {
                                    jumpToPlainOffset(
                                        (result.progressPercent.coerceIn(0f, 100f) / 100f * plainContent.length).toInt(),
                                    )
                                }
                                sheet = null
                            },
                        )

                        SheetType.BOOK_INFO -> BookInfoSheet(
                            bookTitle = bookTitle,
                            bookAuthor = bookAuthor,
                            bookFormat = if (epubBook != null) "EPUB" else "TXT",
                            chapterCount = epubBook?.chapters?.size ?: 0,
                            wordCount = documentWordCount,
                            currentChapterTitle = currentChapterTitle,
                            progressPercent = progressPercent,
                            activeReadingMs = activeReadingMs,
                            savedReadingMs = savedBookReadingMs,
                            sessionsCount = sessions.size,
                            sourceFile = bookOriginalFile,
                            onOpenSettings = { sheet = SheetType.SETTINGS },
                            onDelete = {
                                scope.launch(Dispatchers.IO) { entry.bookRepository().deleteBook(bid) }
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── 底部栏 / 工具条 / 弹层 组件（helper，均在本文件） ──────────────────────────

@Composable
private fun FooterBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TtsBar(
    tts: TtsController,
    chapterLabel: String,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int) -> Unit,
    onClose: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    var showSettings by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.weight(1f)) {
                Text(chapterLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "段落进度 ${tts.progressPercent.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = tts::prev, enabled = tts.status != "idle") {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一段")
            }
            IconButton(
                onClick = {
                    when (tts.status) {
                        "playing" -> tts.pause()
                        "paused" -> tts.resume()
                        else -> tts.resume()
                    }
                },
            ) {
                Icon(
                    if (tts.status == "playing") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (tts.status == "playing") "暂停" else "播放",
                )
            }
            IconButton(onClick = tts::next, enabled = tts.status != "idle") {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一段")
            }
            IconButton(onClick = tts::stop, enabled = tts.status != "idle") {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "朗读设置")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭朗读")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("语速", style = MaterialTheme.typography.labelSmall)
            speeds.forEach { s ->
                Button(
                    onClick = { tts.rate = s },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = if (tts.rate == s) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
                ) {
                    Text("${s}x", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            TtsSettingsContent(
                tts = tts,
                onPersistTts = onPersistTts,
                onClose = { showSettings = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsSettingsContent(
    tts: TtsController,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int) -> Unit,
    onClose: () -> Unit,
) {
    fun persist() = onPersistTts(tts.pitch, tts.volume, tts.voiceId, tts.timedStopMinutes)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("朗读设置", style = MaterialTheme.typography.titleMedium)
        Text(
            "说明：原生仅支持设备本地 TTS 引擎，暂不支持联网云端音色（web 端的 online 引擎）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        SettingLabel("音调（Pitch）")
        Slider(
            value = tts.pitch,
            onValueChange = { tts.updatePitch(it) },
            onValueChangeFinished = { persist() },
            valueRange = 0.5f..2f,
            steps = 15,
        )
        Text("${"%.2f".format(tts.pitch)}x", style = MaterialTheme.typography.bodySmall)

        SettingLabel("音量（Volume）")
        Slider(
            value = tts.volume,
            onValueChange = { tts.updateVolume(it) },
            onValueChangeFinished = { persist() },
            valueRange = 0f..1f,
            steps = 10,
        )
        Text("${(tts.volume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)

        val voices = tts.availableVoices
            .filter { it.locale.language == "zh" }
            .ifEmpty { tts.availableVoices }
        var voiceMenu by remember { mutableStateOf(false) }
        SettingLabel("音色（Voice）")
        OutlinedButton(onClick = { voiceMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                voices.firstOrNull { it.name == tts.voiceId }
                    ?.let { "${it.name} (${it.locale})" } ?: "默认（系统）",
            )
        }
        DropdownMenu(
            expanded = voiceMenu,
            onDismissRequest = { voiceMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            DropdownMenuItem(
                text = { Text("默认（系统）") },
                onClick = { tts.updateVoiceId(""); voiceMenu = false; persist() },
            )
            voices.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.name} (${v.locale})") },
                    onClick = { tts.updateVoiceId(v.name); voiceMenu = false; persist() },
                )
            }
        }

        val stops = listOf(0, 15, 30, 45, 60)
        var stopMenu by remember { mutableStateOf(false) }
        SettingLabel("定时停止")
        OutlinedButton(onClick = { stopMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (tts.timedStopMinutes == 0) "关闭" else "${tts.timedStopMinutes} 分钟")
        }
        DropdownMenu(
            expanded = stopMenu,
            onDismissRequest = { stopMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            stops.forEach { m ->
                DropdownMenuItem(
                    text = { Text(if (m == 0) "关闭" else "$m 分钟") },
                    onClick = { tts.setTimedStop(m); stopMenu = false; persist() },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
    }
}

/** 阅读设置/主题中的选项胶囊（对齐 web .reader-settings-options button / .reader-theme-btn）。
 *  选中态：secondaryContainer 底 + 主色描边 + 加粗；未选：surfaceContainerLow 底 + 发丝线。 */
@Composable
private fun OptionPill(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** 阅读设置中的开关行（R7 阅读内快捷开关）。 */
@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SelectionToolbar(
    selectedText: String,
    showColorRow: Boolean,
    onToggleColor: () -> Unit,
    onPickColor: (String) -> Unit,
    onAiExplain: () -> Unit,
    onInspiration: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    selectedText.take(42) + if (selectedText.length > 42) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showColorRow) {
                        HIGHLIGHT_COLORS.forEach { c ->
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .size(28.dp)
                                    .background(highlightColor(c), shape = androidx.compose.foundation.shape.CircleShape)
                                    .clickable { onPickColor(c) },
                            )
                        }
                        TextButton(onClick = onToggleColor) { Text("取消") }
                    } else {
                        TextButton(onClick = onToggleColor) { Text("高亮") }
                        TextButton(onClick = onAiExplain) { Text("AI 解读") }
                        TextButton(onClick = onInspiration) { Text("记为灵感") }
                        TextButton(onClick = onNote) { Text("存笔记") }
                        TextButton(onClick = onCopy) { Text("复制") }
                        TextButton(onClick = onSearch) { Text("搜索") }
                        TextButton(onClick = onClear) { Text("清除") }
                    }
                }
            }
        }
    }
}

/** 书内全文搜索结果（对照 web ReaderSearchResult）。 */
private data class BookSearchResult(
    val occurrenceIndex: Int,
    val snippet: String,
    val progressPercent: Float,
    val chapterIndex: Int, // -1 表示纯文本全书
    val chapterTitle: String,
)

/** 对照 web createReaderSearchResults：全本拼接文本上做不区分大小写检索，最多 80 处，片断取 28 前 +42 后。 */
private fun computeBookSearch(
    fullText: String,
    query: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    isTxt: Boolean,
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val text = fullText.replace(Regex("\\s+"), " ")
    val lower = text.lowercase()
    val lowerKw = keyword.lowercase()
    val results = mutableListOf<BookSearchResult>()
    var from = 0
    var occurrence = 0
    while (results.size < 80) {
        val hit = lower.indexOf(lowerKw, from)
        if (hit < 0) break
        val start = (hit - 28).coerceAtLeast(0)
        val end = (hit + keyword.length + 42).coerceAtMost(text.length)
        val snippet = "${if (start > 0) "…" else ""}${text.substring(start, end)}${if (end < text.length) "…" else ""}"
        val progress = if (text.isEmpty()) 0f else (hit.toFloat() / text.length) * 100f
        val chIdx = if (isTxt || chapterStartOffsets.isEmpty()) {
            -1
        } else {
            chapterStartOffsets.indexOfLast { it <= hit }.coerceIn(0, chapterTitles.lastIndex)
        }
        val chTitle = if (isTxt || chIdx < 0) "全文" else chapterTitles.getOrNull(chIdx) ?: "正文"
        results.add(BookSearchResult(occurrence, snippet, progress, chIdx, chTitle))
        occurrence += 1
        from = hit + lowerKw.length
    }
    return results
}

/**
 * EPUB 搜索：逐章经 [EpubParser.loadChapterText] 流式抽取当前章文本并检索，
 * 内存只保留当前章文本（不拼接全本），命中后用 [chapterStartOffsets] 映射回全局偏移与所属章。
 * 必须在 IO 线程调用（[SearchSheet] 内已 withContext(Dispatchers.IO)）。
 */
private suspend fun computeEpubSearch(
    book: EpubBook,
    query: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    totalChars: Int,
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val lowerKw = keyword.lowercase()
    val results = mutableListOf<BookSearchResult>()
    val denom = totalChars.coerceAtLeast(1)
    for ((ci, ch) in book.chapters.withIndex()) {
        if (results.size >= 80) break
        val ct = EpubParser.loadChapterText(ch.cachedEpubPath, ch.entryPath, ch.chapterDir)
        if (ct.isBlank()) continue
        val text = ct.replace(Regex("\\s+"), " ")
        val lower = text.lowercase()
        val base = chapterStartOffsets.getOrElse(ci) { 0 }
        var from = 0
        while (results.size < 80) {
            val hit = lower.indexOf(lowerKw, from)
            if (hit < 0) break
            val start = (hit - 28).coerceAtLeast(0)
            val end = (hit + keyword.length + 42).coerceAtMost(text.length)
            val snippet = "${if (start > 0) "…" else ""}${text.substring(start, end)}${if (end < text.length) "…" else ""}"
            val globalHit = base + hit
            val progress = (globalHit.toFloat() / denom) * 100f
            val chTitle = chapterTitles.getOrNull(ci) ?: "正文"
            results.add(BookSearchResult(results.size, snippet, progress, ci, chTitle))
            from = hit + lowerKw.length
        }
    }
    return results
}

@Composable
private fun SearchSheet(
    book: EpubBook?,
    plainContent: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    totalChars: Int,
    isTxt: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onJump: (BookSearchResult) -> Unit,
) {
    var results by remember { mutableStateOf(emptyList<BookSearchResult>()) }
    LaunchedEffect(query, book, plainContent) {
        delay(250)
        results = withContext(Dispatchers.IO) {
            if (isTxt) {
                computeBookSearch(plainContent, query, chapterStartOffsets, chapterTitles, isTxt)
            } else {
                book?.let { computeEpubSearch(it, query, chapterStartOffsets, chapterTitles, totalChars) }
                    ?: emptyList()
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 560.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索本书") },
            placeholder = { Text("输入人名、设定或句子片段") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            Text(
                "找到 ${results.size} 处，最多显示前 80 条。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(results) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJump(r) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(r.snippet, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${if (r.chapterIndex >= 0) r.chapterTitle else "全文"} · ${r.progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
// 只依赖标题，不依赖 EpubChapter —— TXT 现在也有章节（TxtChapterDetector），
// 目录必须能同时服务两种格式。
private fun TocSheet(
    titles: List<String>,
    current: Int,
    recent: List<Int>,
    onPick: (Int) -> Unit,
) {
    val collapsed = remember { mutableStateOf<Set<String>>(emptySet()) }
    val groups = remember(titles) { groupChaptersByVolume(titles) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("目录", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        if (titles.isEmpty()) {
            Text("这本书暂未识别到目录。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
                // R5：最近浏览章节置顶
                if (recent.isNotEmpty()) {
                    item {
                        Text(
                            "最近浏览",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(recent) { i ->
                        val t = titles.getOrNull(i) ?: return@items
                        TocRow(i, t, i == current) { onPick(i) }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                }
                // R5：按「卷/部」分组，可折叠
                groups.forEach { (volume, idxs) ->
                    val isCollapsed = collapsed.value.contains(volume)
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Text(volume, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (!isCollapsed) {
                        items(idxs) { i ->
                            TocRow(i, titles[i], i == current) { onPick(i) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TocRow(index: Int, title: String, isCurrent: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.outline)
        Text(title, Modifier.weight(1f), fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
        if (isCurrent) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** R5：按「卷/部」标题聚合章节；检测不到卷时归入「正文」。 */
private fun groupChaptersByVolume(titles: List<String>): List<Pair<String, List<Int>>> {
    val result = mutableListOf<Pair<String, MutableList<Int>>>()
    for ((i, title) in titles.withIndex()) {
        if (isVolumeHeader(title)) {
            result.add(title to mutableListOf())
        } else {
            if (result.isEmpty()) result.add("正文" to mutableListOf())
            result.last().second.add(i)
        }
    }
    return result
}

private fun isVolumeHeader(title: String): Boolean {
    if (title.isBlank()) return false
    return title.contains("卷") || title.contains("部") ||
        title.contains("Part", ignoreCase = true) || title.contains("Volume", ignoreCase = true) ||
        Regex("^第[一二三四五六七八九十\\d]+[卷部]").containsMatchIn(title)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesSheet(
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    inspirations: List<InspirationEntity>,
    onAddBookmark: () -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onChangeHighlightColor: (HighlightEntity, String) -> Unit,
    onEditHighlightNote: (HighlightEntity, String) -> Unit,
    onHighlightToNote: (HighlightEntity) -> Unit,
    onHighlightToInspiration: (HighlightEntity) -> Unit,
    onJumpToHighlight: (HighlightEntity) -> Unit,
    onExportHighlights: () -> Unit,
) {
    var editingNote by remember { mutableStateOf<HighlightEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }

    if (editingNote != null) {
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("编辑高亮笔记") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("笔记内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingNote?.let { onEditHighlightNote(it, noteDraft) }
                    editingNote = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("取消") } },
        )
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("笔记与标注", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (highlights.isNotEmpty()) {
                TextButton(onClick = onExportHighlights) { Text("导出书摘") }
            }
            Button(onClick = onAddBookmark) { Text("添加书签") }
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
            if (highlights.isNotEmpty()) {
                val grouped = highlights.groupBy { it.chapter_title ?: "" }.toSortedMap()
                grouped.forEach { (chapter, items) ->
                    item {
                        Text(
                            if (chapter.isBlank()) "未分类" else chapter,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(items) { h ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(14.dp).background(highlightColor(h.color ?: "yellow"), shape = androidx.compose.foundation.shape.CircleShape),
                                )
                                Text(h.text.take(60), Modifier.weight(1f).padding(horizontal = 8.dp))
                                IconButton(onClick = { onDeleteHighlight(h) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                            }
                            h.note?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 22.dp, top = 2.dp),
                                )
                            }
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                HIGHLIGHT_COLORS.forEach { c ->
                                    androidx.compose.foundation.layout.Box(
                                        Modifier
                                            .size(22.dp)
                                            .background(highlightColor(c), shape = androidx.compose.foundation.shape.CircleShape)
                                            .border(
                                                if (h.color == c) 2.dp else 0.dp,
                                                MaterialTheme.colorScheme.primary,
                                                androidx.compose.foundation.shape.CircleShape,
                                            )
                                            .clickable { onChangeHighlightColor(h, c) },
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onJumpToHighlight(h) }) { Text("跳转") }
                                TextButton(onClick = { noteDraft = h.note ?: ""; editingNote = h }) { Text("笔记") }
                                TextButton(onClick = { onHighlightToNote(h) }) { Text("转笔记") }
                                TextButton(onClick = { onHighlightToInspiration(h) }) { Text("转灵感") }
                            }
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) {
                item { Text("笔记 / 书签", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                items(notes) { n ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (n.kind == "bookmark") Icons.Filled.Bookmark else Icons.Filled.FormatQuote,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontWeight = FontWeight.Bold)
                            n.excerpt?.let { Text(it.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                        }
                        IconButton(onClick = { onDeleteNote(n) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                    }
                }
            }
            if (inspirations.isNotEmpty()) {
                item { Text("灵感记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                items(inspirations) { ins ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ins.title, fontWeight = FontWeight.Bold)
                            if (ins.body.isNotBlank()) Text(ins.body.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            if (highlights.isEmpty() && notes.isEmpty() && inspirations.isEmpty()) {
                item { Text("还没有笔记、标注或灵感。选中正文即可高亮、存笔记或记为灵感。", color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable
private fun AiAssistSheet(
    aiClient: AiClient,
    @Suppress("unused") bookTitle: String,
    chapterTitle: String,
    contextText: String,
) {
    var tab by remember { mutableStateOf("summary") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val hasContext = contextText.isNotBlank()

    fun callAi() {
        if (!hasContext) return
        loading = true; error = null; result = ""
        scope.launch(Dispatchers.IO) {
            val r = when (tab) {
                "summary" -> aiClient.summarize(contextText.take(4000))
                "qa" -> aiClient.askQuestion(contextText.take(4000), question)
                else -> aiClient.extractKeyPoints(contextText.take(4000))
            }
            r.onSuccess { result = it; loading = false }
             .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("AI 阅读辅助", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OptionPill(selected = tab == "summary", label = "章节摘要", onClick = { tab = "summary" })
            OptionPill(selected = tab == "qa", label = "内容问答", onClick = { tab = "qa" })
            OptionPill(selected = tab == "keypoints", label = "要点提取", onClick = { tab = "keypoints" })
        }
        Text(
            if (selectedTextSafe(contextText).isNotBlank()) "已选中 ${contextText.length} 字作为上下文" else "当前章节：$chapterTitle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (tab == "qa") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("对选中文本或当前章节提问…") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Button(
            onClick = { callAi() },
            enabled = hasContext && !loading,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("思考中…")
            } else {
                Text(
                    when (tab) {
                        "summary" -> "生成章节摘要"
                        "qa" -> if (question.isBlank()) "请先输入问题" else "提问"
                        else -> "提取关键要点"
                    },
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (result.isNotBlank()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Text(result, Modifier.padding(12.dp))
            }
        }
        if (!hasContext) {
            Text("当前没有可分析的文本内容。", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun selectedTextSafe(s: String): String = s

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AiExplainSheet(
    aiClient: AiClient,
    selectedText: String,
    bookTitle: String,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSaveInspiration: (String, List<String>, List<String>) -> Unit,
) {
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }

    fun callExplain() {
        if (selectedText.isBlank()) return
        loading = true; error = null; result = ""
        scope.launch(Dispatchers.IO) {
            aiClient.explain(selectedText.take(4000))
                .onSuccess { result = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("AI 解读", style = MaterialTheme.typography.titleLarge)
        if (selectedText.isNotBlank()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("来源摘录：$selectedText", Modifier.padding(12.dp))
            }
        } else {
            Text("请先选中一段正文，再使用 AI 解读。", color = MaterialTheme.colorScheme.outline)
        }
        Button(
            onClick = { callExplain() },
            enabled = selectedText.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (loading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text("思考中…") }
            else Text("解读选中文本")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (result.isNotBlank()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Text(result, Modifier.padding(12.dp))
            }
            Text("分类", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { c ->
                    val active = selectedCategoryIds.contains(c.id)
                    FilterChip(
                        selected = active,
                        onClick = { selectedCategoryIds = if (active) selectedCategoryIds - c.id else selectedCategoryIds + c.id },
                        label = { Text(c.name) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(value = newCategoryInput, onValueChange = { newCategoryInput = it }, label = { Text("新建分类") }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val n = newCategoryInput.trim()
                    if (n.isNotBlank()) { val id = onCreateCategory(n); selectedCategoryIds = selectedCategoryIds + id; newCategoryInput = "" }
                }) { Text("添加") }
            }
            Text("标签（默认带书名）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                inspirationTags.forEach { t ->
                    val active = selectedTagNames.contains(t.name)
                    FilterChip(
                        selected = active,
                        onClick = { selectedTagNames = if (active) selectedTagNames - t.name else selectedTagNames + t.name },
                        label = { Text(t.name) },
                    )
                }
                selectedTagNames.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                    FilterChip(selected = true, onClick = { selectedTagNames = selectedTagNames - name }, label = { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(value = newTagInput, onValueChange = { newTagInput = it }, label = { Text("新标签，逗号分隔") }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                    if (names.isNotEmpty()) {
                        names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                        selectedTagNames = (selectedTagNames + names).distinct()
                        newTagInput = ""
                    }
                }) { Text("添加") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSaveInspiration(result, selectedTagNames, selectedCategoryIds) }) { Text("存入灵感") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspirationSheet(
    bookTitle: String,
    chapterTitle: String,
    excerpt: String,
    progressPercent: Float,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSave: (String, String, List<String>, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("阅读灵感：$bookTitle") }
    var body by remember { mutableStateOf("") }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    val canSave = title.isNotBlank() || body.isNotBlank() || excerpt.isNotBlank()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }

    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("记录灵感", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("我的想法") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(top = 8.dp),
        )
        if (excerpt.isNotBlank()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("来源摘录：$excerpt", Modifier.padding(12.dp))
            }
        }

        Text("分类", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEach { c ->
                val active = selectedCategoryIds.contains(c.id)
                FilterChip(
                    selected = active,
                    onClick = { selectedCategoryIds = if (active) selectedCategoryIds - c.id else selectedCategoryIds + c.id },
                    label = { Text(c.name) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(value = newCategoryInput, onValueChange = { newCategoryInput = it }, label = { Text("新建分类") }, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val n = newCategoryInput.trim()
                if (n.isNotBlank()) { val id = onCreateCategory(n); selectedCategoryIds = selectedCategoryIds + id; newCategoryInput = "" }
            }) { Text("添加") }
        }

        Text("标签（默认带书名）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            inspirationTags.forEach { t ->
                val active = selectedTagNames.contains(t.name)
                FilterChip(
                    selected = active,
                    onClick = { selectedTagNames = if (active) selectedTagNames - t.name else selectedTagNames + t.name },
                    label = { Text(t.name) },
                )
            }
            selectedTagNames.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                FilterChip(selected = true, onClick = { selectedTagNames = selectedTagNames - name }, label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(value = newTagInput, onValueChange = { newTagInput = it }, label = { Text("新标签，逗号分隔") }, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                if (names.isNotEmpty()) {
                    names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                    selectedTagNames = (selectedTagNames + names).distinct()
                    newTagInput = ""
                }
            }) { Text("添加") }
        }

        Text(
            "来源：$bookTitle · ${chapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = canSave, onClick = { onSave(title.trim(), body.trim(), selectedTagNames, selectedCategoryIds) }) { Text("保存灵感") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
// R8：独立「主题外观」底部 sheet（对照 web theme-sheet，仅承载纸色背景选择）
private fun ThemeSheet(
    background: String,
    onBackground: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("主题外观", style = MaterialTheme.typography.titleLarge)
        Text("纸张背景", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "white" to "白纸", "warm" to "暖纸", "green" to "护眼", "night" to "夜间",
                "warm-yellow" to "暖黄纸感", "green-bean" to "绿豆沙", "oled-black" to "夜间 OLED",
            ).forEach { (v, label) ->
                OptionPill(selected = background == v, label = label, onClick = { onBackground(v) })
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    fontSize: Float,
    lineHeight: Float,
    background: String,
    bold: Boolean,
    brightness: Int,
    readerMode: String,
    pagerEngineMode: String,
    epubPagerEngineMode: String,
    pageTurnEffect: String,
    tapZoneMode: String,
    pageMargin: Float,
    paragraphSpacing: Float,
    eyeCareMin: Int,
    rhythmEnabled: Boolean,
    rhythmMin: Int,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onBackground: (String) -> Unit,
    onBrightness: (Int) -> Unit,
    onBold: (Boolean) -> Unit,
    onReaderMode: (String) -> Unit,
    onPagerEngineMode: (String) -> Unit,
    onEpubPagerEngineMode: (String) -> Unit,
    onPageTurnEffect: (String) -> Unit,
    onTapZoneMode: (String) -> Unit,
    onPageMargin: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onEyeCareMin: (Int) -> Unit,
    onRhythmEnabled: (Boolean) -> Unit,
    onRhythmMin: (Int) -> Unit,
    immersiveMode: Boolean = false,
    showReaderInfo: Boolean = true,
    chineseTypography: Boolean = true,
    keepAwake: Boolean = false,
    showProgressBar: Boolean = true,
    autoHideSeconds: Int = 4,
    onImmersive: (Boolean) -> Unit = {},
    onShowInfo: (Boolean) -> Unit = {},
    onChineseTypo: (Boolean) -> Unit = {},
    onKeepAwake: (Boolean) -> Unit = {},
    onShowProgress: (Boolean) -> Unit = {},
    onAutoHide: (Int) -> Unit = {},
    onBookInfo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("阅读设置", style = MaterialTheme.typography.titleLarge)

        Text("阅读模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OptionPill(selected = readerMode == "paged", label = "左右翻页", onClick = { onReaderMode("paged") })
            OptionPill(selected = readerMode == "scroll", label = "上下滚动", onClick = { onReaderMode("scroll") })
        }
        SettingsSwitchRow("TXT 新分页引擎（试验）", pagerEngineMode == "on") {
            onPagerEngineMode(if (it) "on" else "off")
        }
        SettingsSwitchRow("EPUB 新分页引擎（试验）", epubPagerEngineMode == "on") {
            onEpubPagerEngineMode(if (it) "on" else "off")
        }

        Text("翻页与点击", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("none" to "无动画", "fade" to "柔和淡入").forEach { (v, label) ->
                OptionPill(selected = pageTurnEffect == v, label = label, onClick = { onPageTurnEffect(v) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            OptionPill(selected = tapZoneMode == "three-zone", label = "左中右三区", onClick = { onTapZoneMode("three-zone") })
            OptionPill(selected = tapZoneMode == "five-zone", label = "上下扩展五区", onClick = { onTapZoneMode("five-zone") })
        }

        Text("字号", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFontSize((fontSize - 1f).coerceAtLeast(12f)) }) { Text("A-") }
            Text(fontSize.toInt().toString(), Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onFontSize((fontSize + 1f).coerceAtMost(32f)) }) { Text("A+") }
        }

        Text("行距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松").forEach { (v, label) ->
                OptionPill(selected = kotlin.math.abs(lineHeight - v) < 0.01f, label = label, onClick = { onLineHeight(v) })
            }
        }

        Text("段距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.8f to "小", 1.1f to "中", 1.5f to "大").forEach { (v, label) ->
                OptionPill(selected = kotlin.math.abs(paragraphSpacing - v) < 0.01f, label = label, onClick = { onParagraphSpacing(v) })
            }
        }

        Text("页边距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = pageMargin,
                onValueChange = { onPageMargin(it) },
                valueRange = 10f..42f,
                steps = 32,
                modifier = Modifier.weight(1f),
            )
            Text("${pageMargin.toInt()}", Modifier.padding(start = 8.dp))
        }

        Text("亮度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightness(it.toInt()) },
                valueRange = 45f..100f,
                steps = 55,
                modifier = Modifier.weight(1f),
            )
            Text("${brightness}%", Modifier.padding(start = 8.dp))
        }

        // R7：阅读内快捷开关（沉浸 / 安静信息 / 中文排版 / 常亮 / 进度条 / 自动隐藏）
        Text("阅读辅助", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SettingsSwitchRow("沉浸模式", immersiveMode) { onImmersive(it) }
        SettingsSwitchRow("安静阅读信息", showReaderInfo) { onShowInfo(it) }
        SettingsSwitchRow("中文排版优化", chineseTypography) { onChineseTypo(it) }
        SettingsSwitchRow("常亮显示", keepAwake) { onKeepAwake(it) }
        SettingsSwitchRow("显示进度条", showProgressBar) { onShowProgress(it) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("菜单自动隐藏（秒）", Modifier.weight(1f))
            androidx.compose.material3.Slider(
                value = autoHideSeconds.toFloat(),
                onValueChange = { onAutoHide(it.toInt()) },
                valueRange = 0f..8f,
                steps = 8,
                modifier = Modifier.weight(1f),
            )
            Text("${autoHideSeconds}", Modifier.padding(start = 8.dp))
        }

        Text("阅读提醒", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("护眼提醒（分钟）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$eyeCareMin", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = eyeCareMin.toFloat(),
                onValueChange = { onEyeCareMin(it.toInt()) },
                valueRange = 5f..60f,
                steps = 55,
                modifier = Modifier.weight(1f),
            )
        }
        ListItem(
            headlineContent = { Text("阅读节奏提示") },
            supportingContent = { Text("每 ${rhythmMin} 分钟轻提示休息") },
            trailingContent = { Switch(checked = rhythmEnabled, onCheckedChange = onRhythmEnabled) },
        )
        if (rhythmEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Slider(
                    value = rhythmMin.toFloat(),
                    onValueChange = { onRhythmMin(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 55,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ListItem(
            headlineContent = { Text("粗体文字") },
            trailingContent = { Switch(checked = bold, onCheckedChange = onBold) },
        )

        Button(onClick = onBookInfo, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("书籍信息")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BookInfoSheet(
    bookTitle: String,
    bookAuthor: String?,
    bookFormat: String,
    chapterCount: Int,
    wordCount: Int,
    currentChapterTitle: String,
    progressPercent: Float,
    activeReadingMs: Long,
    savedReadingMs: Long,
    sessionsCount: Int,
    sourceFile: String?,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除本书") },
            text = { Text("确定从书架移除《${bookTitle}》吗？本地正文文件、阅读进度和笔记将一并移除，此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("书籍信息", style = MaterialTheme.typography.titleLarge)
        Text(bookTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${bookAuthor ?: "作者未知"} · ${bookFormat.uppercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text("阅读统计", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell("本次已读", formatDuration(activeReadingMs))
            StatCell("累计阅读", formatDuration(savedReadingMs))
            StatCell("阅读次数", "$sessionsCount")
            StatCell("进度", "${progressPercent.toInt()}%")
        }
        Spacer(Modifier.height(12.dp))
        Text("正文信息", style = MaterialTheme.typography.titleSmall)
        InfoRow("章节数", "$chapterCount 章")
        InfoRow("总字数", "${wordCount} 字")
        InfoRow("当前章节", currentChapterTitle.ifBlank { "正文" })
        InfoRow("来源文件", sourceFile ?: "本地导入")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("阅读设置") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("删除本书") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressSheet(
    epubBook: EpubBook?,
    chapterIndex: Int,
    currentChapterTitle: String,
    progressPercent: Float,
    activeReadingMs: Long,
    readerSpeed: Int,
    estimatedRemainingMs: Long,
    savedBookReadingMs: Long,
    inspirationsCount: Int,
    bookmarksCount: Int,
    onChapter: (Int) -> Unit,
    onSeekPercent: (Float) -> Unit = {},
    isTxt: Boolean = false,
) {
    var slider by remember { mutableFloatStateOf(progressPercent) }
    val size = epubBook?.chapters?.size ?: 0
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("阅读进度", style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("阅读", formatDuration(savedBookReadingMs + activeReadingMs))
            StatCell("字/分", if (readerSpeed > 0) "$readerSpeed" else "—")
            StatCell("读完", if (estimatedRemainingMs > 0) formatDuration(estimatedRemainingMs) else "—")
            StatCell("灵感", "$inspirationsCount")
            StatCell("书签", "$bookmarksCount")
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChapter(chapterIndex - 1) }, enabled = chapterIndex > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentChapterTitle.ifBlank { "正文" }, fontWeight = FontWeight.Bold)
                Text("${progressPercent.toInt()}%", color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = { onChapter(chapterIndex + 1) }, enabled = chapterIndex < size - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章")
            }
        }
        if (size > 0 || isTxt) {
            androidx.compose.material3.Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = 0f..100f,
                onValueChangeFinished = {
                    if (size > 0) onChapter((slider / 100f * size).toInt().coerceIn(0, size - 1))
                    else onSeekPercent(slider)
                },
            )
        }
    }
}
