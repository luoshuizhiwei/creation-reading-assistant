package com.creationreadingassistant.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.data.security.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 页眉/页脚可显示的条目类型 */
enum class HeaderFooterItem(val label: String) {
    NONE("无"),
    CHAPTER_TITLE("章节标题"),
    BOOK_NAME("书名"),
    TIME("当前时间"),
    BATTERY("电量"),
    PAGE_NUMBER("页码"),
    PROGRESS("进度%");

    companion object {
        fun fromString(value: String): HeaderFooterItem =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

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
private val KEY_COLOR_PALETTE = stringPreferencesKey("appearance_color_palette")
private val KEY_PAPER_TEXTURE = booleanPreferencesKey("appearance_paper_texture")
/** Android 12+ 壁纸动态取色；低于 12 时即使开启也静默回退到配置的 palette。 */
private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("appearance_use_dynamic_color")
/** 仅暗模式下生效：把应用外壳背景/表面收敛为纯黑，AMOLED 屏省电且对比度更高。 */
private val KEY_AMOLED_PURE_BLACK = booleanPreferencesKey("appearance_amoled_pure_black")

// ---- Reader ----
private val KEY_READER_MODE = stringPreferencesKey("reader_mode")              // paged | scroll
private val KEY_PAGER_ENGINE = stringPreferencesKey("pager_engine_mode")       // off | auto | on
// KEY_EPUB_PAGER_ENGINE 定义在 ReaderDefaultsMigration.kt（迁移与读写共用同一 key 实例）
private val KEY_PAGE_TURN_EFFECT = stringPreferencesKey("reader_page_turn_effect")
private val KEY_PAGE_TURN_SPEED = floatPreferencesKey("reader_page_turn_speed")
private val KEY_TAP_ZONE_MODE = stringPreferencesKey("reader_tap_zone_mode")
private val KEY_SCREEN_ORIENTATION = stringPreferencesKey("reader_screen_orientation")
internal val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
private val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("reader_custom_font_path")  // 空 = 系统字体；否则为 filesDir/fonts 下的 .ttf/.otf 绝对路径
private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
private val KEY_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
private val KEY_PAGE_MARGIN = floatPreferencesKey("reader_page_margin")
private val KEY_READER_BG = stringPreferencesKey("reader_background")          // white | warm | green | night | follow
internal val KEY_IMMERSIVE = booleanPreferencesKey("reader_immersive")
private val KEY_SHOW_READER_INFO = booleanPreferencesKey("reader_show_info")
private val KEY_CHINESE_TYPO = booleanPreferencesKey("reader_chinese_typo")
private val KEY_AUTO_HIDE = intPreferencesKey("reader_auto_hide_seconds")
private val KEY_KEEP_AWAKE = booleanPreferencesKey("reader_keep_awake")
private val KEY_SHOW_PROGRESS = booleanPreferencesKey("reader_show_progress")
private val KEY_FONT_BOLD = booleanPreferencesKey("reader_font_bold")
internal val KEY_READER_BRIGHTNESS = intPreferencesKey("reader_brightness")  // -1 = 跟随系统；5..100 = 固定亮度（对照 web reader brightness）
private val KEY_LAST_FIXED_BRIGHTNESS = intPreferencesKey("reader_last_fixed_brightness")  // 上次手动固定的亮度（5..100）
private val KEY_VOLUME_PAGE = booleanPreferencesKey("reader_volume_page")
private val KEY_TRADITIONAL_CHINESE = booleanPreferencesKey("reader_traditional_chinese")
private val KEY_VOLUME_PAGE_DURING_TTS = booleanPreferencesKey("reader_volume_page_during_tts")
private val KEY_AUTO_PAGE_SPEED = intPreferencesKey("reader_auto_page_speed")
// 阅读提醒（对照 web eyeCareReminderMinutes / readingRhythmReminder*）
private val KEY_EYE_CARE_MIN = intPreferencesKey("reader_eye_care_minutes")
private val KEY_EYE_FILTER_ENABLED = booleanPreferencesKey("reader_eye_filter_enabled")
private val KEY_EYE_FILTER_TEMPERATURE = intPreferencesKey("reader_eye_filter_temperature")
private val KEY_EYE_FILTER_INTENSITY = intPreferencesKey("reader_eye_filter_intensity")
private val KEY_EYE_FILTER_SCHEDULE = booleanPreferencesKey("reader_eye_filter_schedule")
private val KEY_EYE_FILTER_START = intPreferencesKey("reader_eye_filter_start")
private val KEY_EYE_FILTER_END = intPreferencesKey("reader_eye_filter_end")
private val KEY_EYE_FILTER_OLED = booleanPreferencesKey("reader_eye_filter_oled")
private val KEY_EYE_FILTER_SYNC_PAPER = booleanPreferencesKey("reader_eye_filter_sync_paper")
private val KEY_RHYTHM_ENABLED = booleanPreferencesKey("reader_rhythm_enabled")
private val KEY_RHYTHM_MIN = intPreferencesKey("reader_rhythm_minutes")
// TTS 高级（对照 web TTSSettings：pitch / volume / voiceId / 定时停止）
private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
private val KEY_TTS_VOLUME = floatPreferencesKey("tts_volume")
private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice_id")
private val KEY_TTS_TIMED_STOP = intPreferencesKey("tts_timed_stop_minutes")
private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
// TTS 跨会话续读：记录最近一次朗读的书 id 与句首偏移（R3）
private val KEY_TTS_RESUME_BOOK = stringPreferencesKey("tts_resume_book")
private val KEY_TTS_RESUME_OFFSET = intPreferencesKey("tts_resume_offset")
private val KEY_TTS_RESUME_CHAPTER = intPreferencesKey("tts_resume_chapter")
// 页眉页脚配置
private val KEY_HEADER_LEFT = stringPreferencesKey("reader_header_left")
private val KEY_HEADER_RIGHT = stringPreferencesKey("reader_header_right")
private val KEY_FOOTER_LEFT = stringPreferencesKey("reader_footer_left")
private val KEY_FOOTER_RIGHT = stringPreferencesKey("reader_footer_right")

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
 * 旧版阅读背景 key 迁移到统一 4 档纸张体系（white / warm / green / night）+ 跟随外观（follow）。
 * 原「网页版 7 色」(warm-yellow / green-bean / oled-black) 与更旧的 (paper / plain / eye)
 * 在此收敛为 4 档，避免跨端同步时出现未定义纸张；「follow」由阅读器按外壳明暗映射白纸 / 夜读。
 */
private fun migrateReaderBg(v: String?): String = when (v) {
    null -> "follow"
    "paper" -> "warm"
    "plain" -> "white"
    "eye" -> "green"
    "warm-yellow" -> "warm"
    "green-bean" -> "green"
    "oled-black" -> "night"
    else -> v   // white | warm | green | night | follow 等已合法值原样保留
}

data class AppearanceSettings(
    val themeMode: String = "system",   // system | light | dark
    val colorPalette: String = "clear_blue",
    /** 纸张噪点纹理开关（阅读器 PaperNoise 层）；默认开 = 纸墨视觉基线。 */
    val paperTexture: Boolean = true,
    /** Android 12+ 壁纸动态取色；低于 12 时即使开启也静默回退到配置的 palette。 */
    val useDynamicColor: Boolean = false,
    /** 仅暗模式下生效：把应用外壳背景/表面收敛为纯黑，AMOLED 屏省电且对比度更高。 */
    val amoledPureBlack: Boolean = false,
)

data class ReaderSettings(
    val readerMode: String = "paged",            // paged | scroll
    /**
     * 自研分页引擎开关：off | auto | on（仅影响 TXT / Markdown；EPUB 由 epubPagerEngineMode 单独控制）。
     * 默认 on —— TXT 默认走自研逐页引擎（SIDECAR-ZH P6 已默认开启）。
     * 出问题可临时切回 auto（健康自愈）或 off（回退旧滚动视图）；TXT 与 EPUB 分开灰度，
     * 避免其中一种格式的问题迫使另一种一起回退。
     */
    val pagerEngineMode: String = "on",
    val epubPagerEngineMode: String = "auto",
    val pageTurnEffect: String = "none",       // none | fade | slide | cover | reveal（默认 none：60Hz 设备上独立复验 slide 每帧需 GPU 合成 3 张全屏位图、帧时 25-34ms 超预算有顿挫，none 约 13ms 顺滑；产品决策改以 none 为出货默认，修复后的 slide 仍为可选项）
    /**
     * 翻页动画基础时长系数（ms）：单页满位移时的基础时长，实际时长 = speed × 剩余位移比例，
     * 值越小翻页越快。默认对齐 PageTurnAnimConfig().speed（400f）。
     * 由 PagedReaderPageSurface 透传给 PageTurner(speed = ...)，不触及 PageTurner 内部逻辑。
     * 可调区间 150..800ms（设置面板滑块同区间）。
     */
    val pageTurnSpeed: Float = 400f,
    val tapZoneMode: String = "three-zone",      // three-zone | five-zone
    /** 屏幕方向锁定：system 跟随系统 | portrait 竖屏 | landscape 横屏（仅阅读器内生效）。 */
    val screenOrientation: String = "system",
    val fontSize: Float = 25f,
    val customFontPath: String = "",     // 空 = 系统字体；否则为 filesDir/fonts 下的 .ttf/.otf 绝对路径
    val lineHeight: Float = 1.85f,
    val paragraphSpacing: Float = 1.15f,
    val pageMargin: Float = 22f,
    val background: String = "follow",           // white | warm | green | night | follow
    val immersiveMode: Boolean = true,
    val showReaderInfo: Boolean = true,
    val chineseTypography: Boolean = true,
    /** 繁体显示：仅渲染层 1:1 字形转换，底层文本/书签/高亮/进度保持原文（HanConvert 契约）。 */
    val traditionalChinese: Boolean = false,
    val autoHideSeconds: Int = 4,
    val keepAwake: Boolean = false,
    val showProgressBar: Boolean = true,
    val fontWeightBold: Boolean = false,
    val brightness: Int = -1,               // -1 = 跟随系统亮度；0..100 = 固定亮度（<5% 靠额外压暗遮罩实现）
    val lastFixedBrightness: Int = 65,      // 关闭「跟随系统」时回退到的上次固定亮度（0..100）
    val volumeKeyPaging: Boolean = true,
    val volumeKeyPagingDuringTts: Boolean = false,
    val autoPageSpeed: Int = 5,            // 1..10；是否正在自动翻页仅为会话态，不持久化
    // 阅读提醒
    val eyeCareReminderMinutes: Int = 30,
    val eyeCareFilterEnabled: Boolean = false,
    val eyeCareTemperature: Int = 3400,
    val eyeCareIntensity: Int = 60,
    val eyeCareScheduleEnabled: Boolean = false,
    val eyeCareStartMinute: Int = 1320,
    val eyeCareEndMinute: Int = 420,
    val eyeCareOledBlackCompat: Boolean = true,
    val eyeCareSyncPaperPreview: Boolean = true,
    val readingRhythmReminderEnabled: Boolean = true,
    val readingRhythmReminderMinutes: Int = 30,
    // TTS 高级
    val ttsPitch: Float = 1f,
    val ttsVolume: Float = 1f,
    val ttsVoiceId: String = "",
    val ttsTimedStopMinutes: Int = 0,
    /** TTS 引擎："system" | "edge"（见 TtsEngineId.key）。 */
    val ttsEngine: String = "system",
    // 页眉页脚：页眉显示书名、页脚显示章节+进度，避免章节名上下重复
    // （对照起点/番茄等主流：页眉信息栏、页脚进度栏）。
    val headerLeft: HeaderFooterItem = HeaderFooterItem.BOOK_NAME,
    val headerRight: HeaderFooterItem = HeaderFooterItem.NONE,
    val footerLeft: HeaderFooterItem = HeaderFooterItem.CHAPTER_TITLE,
    val footerRight: HeaderFooterItem = HeaderFooterItem.PROGRESS,
)

data class AISettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val apiKey: String = "",
    val prompt: String = "",
)

data class TtsResume(val bookId: String, val chapterIndex: Int, val offset: Int)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.dataStore

    /** 暴露底层 DataStore（与生产读写同一实例），供迁移/集成测试预置旧值。 */
    internal val preferencesDataStore: DataStore<Preferences> get() = ds

    init {
        // 一次性旧默认迁移（marker 幂等，DataStore 单事务原子）；异步执行不阻塞启动。
        scope.launch { migrateLegacyReaderDefaultsOnce() }
    }

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
            colorPalette = prefs[KEY_COLOR_PALETTE] ?: "clear_blue",
            paperTexture = prefs[KEY_PAPER_TEXTURE] ?: true,
            useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: false,
            amoledPureBlack = prefs[KEY_AMOLED_PURE_BLACK] ?: false,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), AppearanceSettings())

    val reader: StateFlow<ReaderSettings> = ds.data.map { prefs ->
        ReaderSettings(
            readerMode = prefs[KEY_READER_MODE] ?: "paged",
            pagerEngineMode = prefs[KEY_PAGER_ENGINE] ?: "on",
            epubPagerEngineMode = prefs[KEY_EPUB_PAGER_ENGINE] ?: "auto",
            pageTurnEffect = when (val effect = prefs[KEY_PAGE_TURN_EFFECT] ?: "none") {
                "curl" -> "cover"
                else -> effect
            },
            pageTurnSpeed = (prefs[KEY_PAGE_TURN_SPEED] ?: 400f).coerceIn(150f, 800f),
            tapZoneMode = prefs[KEY_TAP_ZONE_MODE] ?: "three-zone",
            screenOrientation = prefs[KEY_SCREEN_ORIENTATION] ?: "system",
            fontSize = prefs[KEY_FONT_SIZE] ?: 25f,
            background = migrateReaderBg(prefs[KEY_READER_BG]),
            customFontPath = prefs[KEY_CUSTOM_FONT_PATH] ?: "",
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.85f,
            paragraphSpacing = prefs[KEY_PARAGRAPH_SPACING] ?: 1.15f,
            pageMargin = prefs[KEY_PAGE_MARGIN] ?: 22f,
            immersiveMode = prefs[KEY_IMMERSIVE] ?: true,
            showReaderInfo = prefs[KEY_SHOW_READER_INFO] ?: true,
            chineseTypography = prefs[KEY_CHINESE_TYPO] ?: true,
            traditionalChinese = prefs[KEY_TRADITIONAL_CHINESE] ?: false,
            autoHideSeconds = prefs[KEY_AUTO_HIDE] ?: 4,
            keepAwake = prefs[KEY_KEEP_AWAKE] ?: false,
            showProgressBar = prefs[KEY_SHOW_PROGRESS] ?: true,
            fontWeightBold = prefs[KEY_FONT_BOLD] ?: false,
            brightness = prefs[KEY_READER_BRIGHTNESS] ?: -1,
            lastFixedBrightness = (prefs[KEY_LAST_FIXED_BRIGHTNESS] ?: 65).coerceIn(0, 100),
            volumeKeyPaging = prefs[KEY_VOLUME_PAGE] ?: true,
            volumeKeyPagingDuringTts = prefs[KEY_VOLUME_PAGE_DURING_TTS] ?: false,
            autoPageSpeed = (prefs[KEY_AUTO_PAGE_SPEED] ?: 5).coerceIn(1, 10),
            eyeCareReminderMinutes = prefs[KEY_EYE_CARE_MIN] ?: 30,
            eyeCareFilterEnabled = prefs[KEY_EYE_FILTER_ENABLED] ?: false,
            eyeCareTemperature = prefs[KEY_EYE_FILTER_TEMPERATURE] ?: 3400,
            eyeCareIntensity = prefs[KEY_EYE_FILTER_INTENSITY] ?: 60,
            eyeCareScheduleEnabled = prefs[KEY_EYE_FILTER_SCHEDULE] ?: false,
            eyeCareStartMinute = prefs[KEY_EYE_FILTER_START] ?: 1320,
            eyeCareEndMinute = prefs[KEY_EYE_FILTER_END] ?: 420,
            eyeCareOledBlackCompat = prefs[KEY_EYE_FILTER_OLED] ?: true,
            eyeCareSyncPaperPreview = prefs[KEY_EYE_FILTER_SYNC_PAPER] ?: true,
            readingRhythmReminderEnabled = prefs[KEY_RHYTHM_ENABLED] ?: true,
            readingRhythmReminderMinutes = prefs[KEY_RHYTHM_MIN] ?: 30,
            ttsPitch = prefs[KEY_TTS_PITCH] ?: 1f,
            ttsVolume = prefs[KEY_TTS_VOLUME] ?: 1f,
            ttsVoiceId = prefs[KEY_TTS_VOICE] ?: "",
            ttsTimedStopMinutes = prefs[KEY_TTS_TIMED_STOP] ?: 0,
            ttsEngine = prefs[KEY_TTS_ENGINE] ?: "system",
            headerLeft = HeaderFooterItem.fromString(prefs[KEY_HEADER_LEFT] ?: HeaderFooterItem.BOOK_NAME.name),
            headerRight = HeaderFooterItem.fromString(prefs[KEY_HEADER_RIGHT] ?: HeaderFooterItem.NONE.name),
            footerLeft = HeaderFooterItem.fromString(prefs[KEY_FOOTER_LEFT] ?: HeaderFooterItem.CHAPTER_TITLE.name),
            footerRight = HeaderFooterItem.fromString(prefs[KEY_FOOTER_RIGHT] ?: HeaderFooterItem.PROGRESS.name),
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
    suspend fun saveTtsResume(bookId: String, chapterIndex: Int, offset: Int) {
        ds.edit {
            it[KEY_TTS_RESUME_BOOK] = bookId
            it[KEY_TTS_RESUME_CHAPTER] = chapterIndex
            it[KEY_TTS_RESUME_OFFSET] = offset
        }
    }

    suspend fun loadTtsResume(): TtsResume? {
        val prefs = ds.data.first()
        val book = prefs[KEY_TTS_RESUME_BOOK] ?: return null
        val chapter = prefs[KEY_TTS_RESUME_CHAPTER] ?: -1
        val offset = prefs[KEY_TTS_RESUME_OFFSET] ?: 0
        return TtsResume(book, chapter, offset)
    }

    suspend fun setInspirationSort(value: String) {
        if (value !in setOf("updated", "created", "title", "source")) return
        ds.edit { it[KEY_INSPIRATION_SORT] = value }
    }

    suspend fun saveTxtTocRule(bookId: String, ruleId: String) {
        if (bookId.isBlank() || TxtChapterRuleIds.allowed.none { it == ruleId }) return
        ds.edit { it[stringPreferencesKey("txt_toc_rule_$bookId")] = ruleId }
    }

    suspend fun loadTxtTocRule(bookId: String): String {
        if (bookId.isBlank()) return "builtin"
        val value = ds.data.first()[stringPreferencesKey("txt_toc_rule_$bookId")]
        return value?.takeIf { it in TxtChapterRuleIds.allowed } ?: "builtin"
    }

    /**
     * 旧 txt_toc_rule_<bookId> 是否已迁移到 Room 规则表（P1-A）。
     * 迁移只执行一次：标记后不再重放旧值，避免覆盖用户后续的规则管理
     * （如禁用宽松内置、新增自定义规则）。
     */
    suspend fun isTxtTocRuleMigrated(bookId: String): Boolean {
        if (bookId.isBlank()) return true
        return ds.data.first()[booleanPreferencesKey("txt_toc_rule_migrated_$bookId")] == true
    }

    /** 标记旧 txt_toc_rule_<bookId> 已迁移。 */
    suspend fun markTxtTocRuleMigrated(bookId: String) {
        if (bookId.isBlank()) return
        ds.edit { it[booleanPreferencesKey("txt_toc_rule_migrated_$bookId")] = true }
    }

    suspend fun updateAppearance(block: AppearanceSettings.() -> AppearanceSettings) {
        val next = appearance.value.block()
        ds.edit { prefs ->
            prefs[KEY_THEME] = next.themeMode
            prefs[KEY_COLOR_PALETTE] = next.colorPalette
            prefs[KEY_PAPER_TEXTURE] = next.paperTexture
            prefs[KEY_USE_DYNAMIC_COLOR] = next.useDynamicColor
            prefs[KEY_AMOLED_PURE_BLACK] = next.amoledPureBlack
        }
    }

    suspend fun updateReader(block: ReaderSettings.() -> ReaderSettings) {
        val next = reader.value.block()
        ds.edit { prefs ->
            prefs[KEY_READER_MODE] = next.readerMode
            prefs[KEY_PAGER_ENGINE] = next.pagerEngineMode
            prefs[KEY_EPUB_PAGER_ENGINE] = next.epubPagerEngineMode
            prefs[KEY_PAGE_TURN_EFFECT] = next.pageTurnEffect
            prefs[KEY_PAGE_TURN_SPEED] = next.pageTurnSpeed.coerceIn(150f, 800f)
            prefs[KEY_TAP_ZONE_MODE] = next.tapZoneMode
            prefs[KEY_SCREEN_ORIENTATION] = next.screenOrientation
            prefs[KEY_FONT_SIZE] = next.fontSize
            prefs[KEY_CUSTOM_FONT_PATH] = next.customFontPath
            prefs[KEY_LINE_HEIGHT] = next.lineHeight
            prefs[KEY_PARAGRAPH_SPACING] = next.paragraphSpacing
            prefs[KEY_PAGE_MARGIN] = next.pageMargin
            prefs[KEY_READER_BG] = next.background
            prefs[KEY_IMMERSIVE] = next.immersiveMode
            prefs[KEY_SHOW_READER_INFO] = next.showReaderInfo
            prefs[KEY_CHINESE_TYPO] = next.chineseTypography
            prefs[KEY_TRADITIONAL_CHINESE] = next.traditionalChinese
            prefs[KEY_AUTO_HIDE] = next.autoHideSeconds
            prefs[KEY_KEEP_AWAKE] = next.keepAwake
            prefs[KEY_SHOW_PROGRESS] = next.showProgressBar
            prefs[KEY_FONT_BOLD] = next.fontWeightBold
            prefs[KEY_READER_BRIGHTNESS] = next.brightness
            if (next.brightness >= 0) {
                prefs[KEY_LAST_FIXED_BRIGHTNESS] = next.brightness.coerceIn(0, 100)
            }
            prefs[KEY_VOLUME_PAGE] = next.volumeKeyPaging
            prefs[KEY_VOLUME_PAGE_DURING_TTS] = next.volumeKeyPagingDuringTts
            prefs[KEY_AUTO_PAGE_SPEED] = next.autoPageSpeed.coerceIn(1, 10)
            prefs[KEY_EYE_CARE_MIN] = next.eyeCareReminderMinutes
            prefs[KEY_EYE_FILTER_ENABLED] = next.eyeCareFilterEnabled
            prefs[KEY_EYE_FILTER_TEMPERATURE] = next.eyeCareTemperature
            prefs[KEY_EYE_FILTER_INTENSITY] = next.eyeCareIntensity
            prefs[KEY_EYE_FILTER_SCHEDULE] = next.eyeCareScheduleEnabled
            prefs[KEY_EYE_FILTER_START] = next.eyeCareStartMinute
            prefs[KEY_EYE_FILTER_END] = next.eyeCareEndMinute
            prefs[KEY_EYE_FILTER_OLED] = next.eyeCareOledBlackCompat
            prefs[KEY_EYE_FILTER_SYNC_PAPER] = next.eyeCareSyncPaperPreview
            prefs[KEY_RHYTHM_ENABLED] = next.readingRhythmReminderEnabled
            prefs[KEY_RHYTHM_MIN] = next.readingRhythmReminderMinutes
            prefs[KEY_TTS_PITCH] = next.ttsPitch
            prefs[KEY_TTS_VOLUME] = next.ttsVolume
            prefs[KEY_TTS_VOICE] = next.ttsVoiceId
            prefs[KEY_TTS_TIMED_STOP] = next.ttsTimedStopMinutes
            prefs[KEY_TTS_ENGINE] = next.ttsEngine
            prefs[KEY_HEADER_LEFT] = next.headerLeft.name
            prefs[KEY_HEADER_RIGHT] = next.headerRight.name
            prefs[KEY_FOOTER_LEFT] = next.footerLeft.name
            prefs[KEY_FOOTER_RIGHT] = next.footerRight.name
        }
    }

    /**
     * 一次性旧默认迁移：marker 未设置时迁移特征值（brightness=100→-1、fontSize=18→25、
     * immersive=false→true），其他自定义亮度/字号保留；写 marker 后幂等。
     * 判断与写入在同一 [androidx.datastore.preferences.core.edit] 事务内，并发原子。
     */
    suspend fun migrateLegacyReaderDefaultsOnce() {
        ds.edit { prefs ->
            migrateLegacyReaderDefaults(prefs)
            migrateEpubEngineReenable(prefs)
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

private object TxtChapterRuleIds {
    val allowed = setOf("builtin", "num-dot", "num-bare", "cn-num-dot", "bracketed", "en-extended", "md-heading")
}
