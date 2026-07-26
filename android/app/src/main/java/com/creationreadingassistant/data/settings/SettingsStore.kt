package com.creationreadingassistant.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.security.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 应用设置持久层（DataStore Preferences，单事实源）。
 * 取代此前 ProfileScreen / ReaderScreen 内的会话态设置，做到重启保持。
 *
 * 三类设置：
 * - AppearanceSettings：应用外观（主题模式 / 纸张纹理）
 * - ReaderSettings：阅读器排版与显示（字号 / 行距 / 背景 / 粗体 等）
 * - AISettings：AI 助手配置（启用 / 接口地址 / 模型 / Key / 提示词）
 */
private val Context.dataStore by preferencesDataStore(name = "app_settings")

// ---- Appearance ----
private val KEY_THEME = stringPreferencesKey("appearance_theme_mode")          // system | light | dark
private val KEY_PAPER_TEXTURE = booleanPreferencesKey("appearance_paper_texture")

// ---- Reader ----
private val KEY_READER_MODE = stringPreferencesKey("reader_mode")              // paged | scroll
private val KEY_PAGE_TURN_EFFECT = stringPreferencesKey("reader_page_turn_effect")
private val KEY_TAP_ZONE_MODE = stringPreferencesKey("reader_tap_zone_mode")
private val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
private val KEY_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
private val KEY_PAGE_MARGIN = floatPreferencesKey("reader_page_margin")
private val KEY_READER_BG = stringPreferencesKey("reader_background")          // white | warm | green | night | warm-yellow | green-bean | oled-black
private val KEY_IMMERSIVE = booleanPreferencesKey("reader_immersive")
private val KEY_SHOW_READER_INFO = booleanPreferencesKey("reader_show_info")
private val KEY_CHINESE_TYPO = booleanPreferencesKey("reader_chinese_typo")
private val KEY_AUTO_HIDE = intPreferencesKey("reader_auto_hide_seconds")
private val KEY_KEEP_AWAKE = booleanPreferencesKey("reader_keep_awake")
private val KEY_SHOW_PROGRESS = booleanPreferencesKey("reader_show_progress")
private val KEY_FONT_BOLD = booleanPreferencesKey("reader_font_bold")
private val KEY_READER_BRIGHTNESS = intPreferencesKey("reader_brightness")  // 45..100，对照 web reader brightness（压暗遮罩）
// 阅读提醒（对照 web eyeCareReminderMinutes / readingRhythmReminder*）
private val KEY_EYE_CARE_MIN = intPreferencesKey("reader_eye_care_minutes")
private val KEY_RHYTHM_ENABLED = booleanPreferencesKey("reader_rhythm_enabled")
private val KEY_RHYTHM_MIN = intPreferencesKey("reader_rhythm_minutes")
// TTS 高级（对照 web TTSSettings：pitch / volume / voiceId / 定时停止）
private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
private val KEY_TTS_VOLUME = floatPreferencesKey("tts_volume")
private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice_id")
private val KEY_TTS_TIMED_STOP = intPreferencesKey("tts_timed_stop_minutes")
// TTS 跨会话续读：记录最近一次朗读的书 id 与句首偏移（R3）
private val KEY_TTS_RESUME_BOOK = stringPreferencesKey("tts_resume_book")
private val KEY_TTS_RESUME_OFFSET = intPreferencesKey("tts_resume_offset")

// ---- 灵感 ----
private val KEY_INSPIRATION_SORT = stringPreferencesKey("inspiration_sort")   // updated | created | title | source

// ---- AI ----
private val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
private val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
private val KEY_AI_MODEL = stringPreferencesKey("ai_model")
private val KEY_AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
private val KEY_AI_PROMPT = stringPreferencesKey("ai_prompt")

// AI API Key 单独加密存储（EncryptedSharedPreferences），与 SyncConfigStore 同机制。
private const val AI_SECRETS_PREFS_NAME = "ai_secrets"
private const val KEY_AI_API_KEY_ENC = "ai_api_key"

/**
 * 旧版阅读背景 key（paper/plain/eye）迁移到网页版 7 色体系，
 * 保证 WebDAV / 局域网同步的书目背景值在跨端不回退为默认。
 */
private fun migrateReaderBg(v: String?): String = when (v) {
    null -> "warm"
    "paper" -> "warm"
    "plain" -> "white"
    "eye" -> "green"
    else -> v
}

data class AppearanceSettings(
    val themeMode: String = "system",   // system | light | dark
    val paperTexture: Boolean = true,
)

data class ReaderSettings(
    val readerMode: String = "paged",            // paged | scroll
    val pageTurnEffect: String = "none",         // none | fade | slide | curl
    val tapZoneMode: String = "three-zone",      // three-zone | five-zone
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.85f,
    val paragraphSpacing: Float = 1.15f,
    val pageMargin: Float = 22f,
    val background: String = "warm",             // white | warm | green | night | warm-yellow | green-bean | oled-black
    val immersiveMode: Boolean = false,
    val showReaderInfo: Boolean = true,
    val chineseTypography: Boolean = true,
    val autoHideSeconds: Int = 4,
    val keepAwake: Boolean = false,
    val showProgressBar: Boolean = true,
    val fontWeightBold: Boolean = false,
    val brightness: Int = 100,             // 45..100，对照 web reader brightness（压暗遮罩）
    // 阅读提醒
    val eyeCareReminderMinutes: Int = 30,
    val readingRhythmReminderEnabled: Boolean = true,
    val readingRhythmReminderMinutes: Int = 30,
    // TTS 高级
    val ttsPitch: Float = 1f,
    val ttsVolume: Float = 1f,
    val ttsVoiceId: String = "",
    val ttsTimedStopMinutes: Int = 0,
)

data class AISettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val apiKey: String = "",
    val prompt: String = "",
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** AI API Key 加密存储，与 SyncConfigStore 同机制（EncryptedSharedPreferences + AES256）。 */
    private val aiSecretsPrefs: SharedPreferences by lazy {
        SecurePrefs.open(context, AI_SECRETS_PREFS_NAME)
    }

    private fun readAiApiKey(): String =
        aiSecretsPrefs.getString(KEY_AI_API_KEY_ENC, "") ?: ""

    private fun writeAiApiKey(key: String) {
        aiSecretsPrefs.edit().putString(KEY_AI_API_KEY_ENC, key).apply()
    }

    val appearance: StateFlow<AppearanceSettings> = ds.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[KEY_THEME] ?: "system",
            paperTexture = prefs[KEY_PAPER_TEXTURE] ?: true,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), AppearanceSettings())

    val reader: StateFlow<ReaderSettings> = ds.data.map { prefs ->
        ReaderSettings(
            readerMode = prefs[KEY_READER_MODE] ?: "paged",
            pageTurnEffect = prefs[KEY_PAGE_TURN_EFFECT] ?: "none",
            tapZoneMode = prefs[KEY_TAP_ZONE_MODE] ?: "three-zone",
            fontSize = prefs[KEY_FONT_SIZE] ?: 18f,
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.85f,
            paragraphSpacing = prefs[KEY_PARAGRAPH_SPACING] ?: 1.15f,
            pageMargin = prefs[KEY_PAGE_MARGIN] ?: 22f,
            background = migrateReaderBg(prefs[KEY_READER_BG]),
            immersiveMode = prefs[KEY_IMMERSIVE] ?: false,
            showReaderInfo = prefs[KEY_SHOW_READER_INFO] ?: true,
            chineseTypography = prefs[KEY_CHINESE_TYPO] ?: true,
            autoHideSeconds = prefs[KEY_AUTO_HIDE] ?: 4,
            keepAwake = prefs[KEY_KEEP_AWAKE] ?: false,
            showProgressBar = prefs[KEY_SHOW_PROGRESS] ?: true,
            fontWeightBold = prefs[KEY_FONT_BOLD] ?: false,
            brightness = prefs[KEY_READER_BRIGHTNESS] ?: 100,
            eyeCareReminderMinutes = prefs[KEY_EYE_CARE_MIN] ?: 30,
            readingRhythmReminderEnabled = prefs[KEY_RHYTHM_ENABLED] ?: true,
            readingRhythmReminderMinutes = prefs[KEY_RHYTHM_MIN] ?: 30,
            ttsPitch = prefs[KEY_TTS_PITCH] ?: 1f,
            ttsVolume = prefs[KEY_TTS_VOLUME] ?: 1f,
            ttsVoiceId = prefs[KEY_TTS_VOICE] ?: "",
            ttsTimedStopMinutes = prefs[KEY_TTS_TIMED_STOP] ?: 0,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val ai: StateFlow<AISettings> = ds.data.map { prefs ->
        AISettings(
            enabled = prefs[KEY_AI_ENABLED] ?: false,
            baseUrl = prefs[KEY_AI_BASE_URL] ?: "",
            model = prefs[KEY_AI_MODEL] ?: "",
            temperature = prefs[KEY_AI_TEMPERATURE] ?: 0.7f,
            apiKey = readAiApiKey(),
            prompt = prefs[KEY_AI_PROMPT] ?: "",
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), AISettings())

    /** 灵感列表排序方式（对照网页 INSPIRATION_SORT_KEY）。 */
    val inspirationSort: StateFlow<String> = ds.data.map { prefs ->
        val v = prefs[KEY_INSPIRATION_SORT]
        if (v != null && v in setOf("updated", "created", "title", "source")) v else "updated"
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "updated")

    /** TTS 跨会话续读：保存「书 id + 句首偏移」（R3）。 */
    suspend fun saveTtsResume(bookId: String, offset: Int) {
        ds.edit {
            it[KEY_TTS_RESUME_BOOK] = bookId
            it[KEY_TTS_RESUME_OFFSET] = offset
        }
    }

    /** 读取 TTS 续读信息，返回 (bookId, offset) 或 null。 */
    suspend fun loadTtsResume(): Pair<String, Int>? {
        val prefs = ds.data.first()
        val book = prefs[KEY_TTS_RESUME_BOOK] ?: return null
        val offset = prefs[KEY_TTS_RESUME_OFFSET] ?: 0
        return book to offset
    }

    suspend fun setInspirationSort(value: String) {
        if (value !in setOf("updated", "created", "title", "source")) return
        ds.edit { it[KEY_INSPIRATION_SORT] = value }
    }

    suspend fun updateAppearance(block: AppearanceSettings.() -> AppearanceSettings) {
        val next = appearance.value.block()
        ds.edit { prefs ->
            prefs[KEY_THEME] = next.themeMode
            prefs[KEY_PAPER_TEXTURE] = next.paperTexture
        }
    }

    suspend fun updateReader(block: ReaderSettings.() -> ReaderSettings) {
        val next = reader.value.block()
        ds.edit { prefs ->
            prefs[KEY_READER_MODE] = next.readerMode
            prefs[KEY_PAGE_TURN_EFFECT] = next.pageTurnEffect
            prefs[KEY_TAP_ZONE_MODE] = next.tapZoneMode
            prefs[KEY_FONT_SIZE] = next.fontSize
            prefs[KEY_LINE_HEIGHT] = next.lineHeight
            prefs[KEY_PARAGRAPH_SPACING] = next.paragraphSpacing
            prefs[KEY_PAGE_MARGIN] = next.pageMargin
            prefs[KEY_READER_BG] = next.background
            prefs[KEY_IMMERSIVE] = next.immersiveMode
            prefs[KEY_SHOW_READER_INFO] = next.showReaderInfo
            prefs[KEY_CHINESE_TYPO] = next.chineseTypography
            prefs[KEY_AUTO_HIDE] = next.autoHideSeconds
            prefs[KEY_KEEP_AWAKE] = next.keepAwake
            prefs[KEY_SHOW_PROGRESS] = next.showProgressBar
            prefs[KEY_FONT_BOLD] = next.fontWeightBold
            prefs[KEY_READER_BRIGHTNESS] = next.brightness
            prefs[KEY_EYE_CARE_MIN] = next.eyeCareReminderMinutes
            prefs[KEY_RHYTHM_ENABLED] = next.readingRhythmReminderEnabled
            prefs[KEY_RHYTHM_MIN] = next.readingRhythmReminderMinutes
            prefs[KEY_TTS_PITCH] = next.ttsPitch
            prefs[KEY_TTS_VOLUME] = next.ttsVolume
            prefs[KEY_TTS_VOICE] = next.ttsVoiceId
            prefs[KEY_TTS_TIMED_STOP] = next.ttsTimedStopMinutes
        }
    }

    suspend fun updateAi(block: AISettings.() -> AISettings) {
        val next = ai.value.block()
        ds.edit { prefs ->
            prefs[KEY_AI_ENABLED] = next.enabled
            prefs[KEY_AI_BASE_URL] = next.baseUrl
            prefs[KEY_AI_MODEL] = next.model
            prefs[KEY_AI_TEMPERATURE] = next.temperature
            prefs[KEY_AI_PROMPT] = next.prompt
        }
        // API Key 单独写入加密存储，不落 DataStore 明文。
        writeAiApiKey(next.apiKey)
    }
}
