package com.creationreadingassistant.ui.screen.profile

import androidx.compose.runtime.Immutable
import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.AppearanceSettings
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import com.creationreadingassistant.ui.viewmodel.ProfileHomeSummary
import com.creationreadingassistant.ui.viewmodel.ProfileLibraryState
import com.creationreadingassistant.ui.viewmodel.SyncFailedItem
import com.creationreadingassistant.ui.viewmodel.SyncResultDetail

// ============================== 枚举与常量 ==============================

internal enum class ProfileSubPage {
    SYNC, WEBDAV, APPEARANCE, READER, AI, GOAL,
    TAGS, CATEGORIES, SHELVES, READING, NOTES,
    STORAGE, PRIVACY, ABOUT, DIAGNOSTICS
}

internal val ProfileSubPage.routeSegment: String
    get() = name.lowercase()

internal fun profileSubPageFromRoute(value: String?): ProfileSubPage? =
    ProfileSubPage.entries.firstOrNull { it.routeSegment == value }

// releases 仓库同时承载桌面端与 Android 发布：Android 用 android-v 前缀 tag。
// 检查更新走列表接口后按前缀过滤（/releases/latest 会命中桌面端发布）。
internal const val MOBILE_RELEASES_URL = "https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases"
internal const val MOBILE_RELEASE_API_URL = "https://api.github.com/repos/luoshuizhiwei/creation-reading-assistant-releases/releases?per_page=30"

// ============================== 辅助数据类 ==============================

internal data class ConfirmSpec(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

// ============================== 辅助函数 ==============================

internal fun themeLabel(theme: String): String = when (theme) {
    "system" -> "跟随系统"
    "dark" -> "深色"
    else -> "浅色"
}

internal fun subPageTitle(page: ProfileSubPage?): String = when (page) {
    ProfileSubPage.SYNC -> "同步状态"
    ProfileSubPage.WEBDAV -> "WebDAV 设置"
    ProfileSubPage.APPEARANCE -> "应用外观"
    ProfileSubPage.READER -> "阅读设置"
    ProfileSubPage.GOAL -> "阅读目标"
    ProfileSubPage.AI -> "AI 助手"
    ProfileSubPage.TAGS -> "标签管理"
    ProfileSubPage.CATEGORIES -> "分类管理"
    ProfileSubPage.SHELVES -> "书单管理"
    ProfileSubPage.READING -> "我的阅读"
    ProfileSubPage.NOTES -> "我的书评 / 笔记"
    ProfileSubPage.STORAGE -> "存储管理"
    ProfileSubPage.PRIVACY -> "隐私安全"
    ProfileSubPage.ABOUT -> "关于"
    ProfileSubPage.DIAGNOSTICS -> "日志与诊断"
    null -> "我的"
}

internal fun subPageEyebrow(page: ProfileSubPage?): String = when (page) {
    ProfileSubPage.SYNC -> "局域网同步"
    ProfileSubPage.WEBDAV -> "跨设备备份"
    ProfileSubPage.APPEARANCE -> "外观"
    ProfileSubPage.READER -> "阅读体验"
    ProfileSubPage.GOAL -> "目标"
    ProfileSubPage.AI -> "工具"
    ProfileSubPage.TAGS -> "数据管理"
    ProfileSubPage.CATEGORIES -> "数据管理"
    ProfileSubPage.SHELVES -> "数据管理"
    ProfileSubPage.READING -> "阅读档案"
    ProfileSubPage.NOTES -> "阅读沉淀"
    ProfileSubPage.STORAGE -> "数据与存储"
    ProfileSubPage.PRIVACY -> "隐私安全"
    ProfileSubPage.ABOUT -> "关于"
    ProfileSubPage.DIAGNOSTICS -> "帮助"
    null -> "本地档案"
}

// ============================== UI 状态 ==============================

@Immutable
/** 阅读目标子页状态（P3.2 片 2/3）：目标、提醒与 streak/今日进度的一站式投影。 */
internal data class GoalPageState(
    val dailyMinutes: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 21 * 60,
    val streakDays: Int = 0,
    val todayReadingMs: Long = 0L,
) {
    val goalEnabled: Boolean get() = dailyMinutes > 0
    val todayMinutes: Int get() = (todayReadingMs / 60_000L).toInt()
}

internal data class ProfileUiState(
    val currentSubPage: ProfileSubPage? = null,
    val paired: Boolean = false,
    val webDavConfigured: Boolean = false,
    val aiConfigured: Boolean = false,
    val appThemeLabel: String = "浅色",
    val homeSummary: ProfileHomeSummary = ProfileHomeSummary(),
    val pairing: Boolean = false,
    val syncing: Boolean = false,
    val baseUrl: String? = null,
    val lastSyncResult: SyncResultDetail? = null,
    val syncLogs: List<String> = emptyList(),
    val webDavConfig: WebDavConfigStore.Config? = null,
    val webDavBackups: List<WebDavBackup.BackupFile> = emptyList(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val reader: ReaderSettings = ReaderSettings(),
    val ai: AISettings = AISettings(),
    /** 非加密 http AI 接口的一次性警示文案（null 表示无需警示）。 */
    val aiHttpWarning: String? = null,
    val aiKeyDraft: String = "",
    val libraryState: ProfileLibraryState = ProfileLibraryState(),
    val pendingDownloadCount: Int = 0,
    val indexBytes: Long = 0L,
    val goal: GoalPageState = GoalPageState(),
)

// ============================== Action ==============================

internal sealed interface ProfileAction {
    // Navigation
    data class OpenSubPage(val page: ProfileSubPage) : ProfileAction
    data class OpenBook(val bookId: String) : ProfileAction
    data object GoBack : ProfileAction

    // Sync
    data class StartPairing(val rawQr: String) : ProfileAction
    data object SyncNow : ProfileAction
    data object Unpair : ProfileAction
    data class RetryItem(val item: SyncFailedItem) : ProfileAction
    data object RetryFailed : ProfileAction
    data object ScanQr : ProfileAction

    // WebDAV
    data class SaveWebDav(val url: String, val user: String, val pass: String) : ProfileAction
    data object UploadBackup : ProfileAction
    data object TestWebDav : ProfileAction
    data object RefreshBackups : ProfileAction
    data class DownloadRestore(val filename: String) : ProfileAction
    data object ClearWebDav : ProfileAction

    // Settings
    data class UpdateAppearance(val block: AppearanceSettings.() -> AppearanceSettings) : ProfileAction
    data class UpdateReader(val block: ReaderSettings.() -> ReaderSettings) : ProfileAction
    data object ResetReader : ProfileAction
    data class UpdateAi(val block: AISettings.() -> AISettings) : ProfileAction
    data object SaveAiKey : ProfileAction
    data object ClearAiKey : ProfileAction
    data class UpdateAiKeyDraft(val value: String) : ProfileAction

    // Reading goal (P3.2)
    data class UpdateGoalMinutes(val minutes: Int) : ProfileAction
    data class UpdateGoalReminderEnabled(val enabled: Boolean) : ProfileAction
    data class UpdateGoalReminderMinuteOfDay(val minute: Int) : ProfileAction

    // Storage
    data object Export : ProfileAction
    data object Import : ProfileAction
    data object ExportZip : ProfileAction
    data object ImportZip : ProfileAction
    data object ClearReaderCache : ProfileAction

    // AI
    data object TestAi : ProfileAction

    // Snackbar
    data class ShowMessage(val text: String) : ProfileAction
}
