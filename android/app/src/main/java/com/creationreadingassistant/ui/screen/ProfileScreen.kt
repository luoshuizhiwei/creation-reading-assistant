package com.creationreadingassistant.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import com.creationreadingassistant.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.ui.viewmodel.ProfileLibraryState
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.StatsViewModel
import com.creationreadingassistant.ui.viewmodel.SyncFailedItem
import com.creationreadingassistant.ui.viewmodel.SyncResultDetail
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.components.*
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 与网页版 mobile-updates.ts 保持一致的发布链接
private const val MOBILE_RELEASES_URL = "https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases"
private const val MOBILE_RELEASE_API_URL = "https://api.github.com/repos/luoshuizhiwei/creation-reading-assistant-releases/releases/latest"

/**
 * 「我的」页（P3）：1:1 复刻网页版 mobile/ ProfilePage/ProfileHome 布局。
 *
 * 实现说明（与原生数据层接线 / 降级策略，详见文件末尾汇报）：
 * - 同步 / WebDAV / 导出导入：真实接线现有 ProfileViewModel（SyncConfigStore、WebDavConfigStore、
 *   PairingManager、SyncRepository、JsonBridge、WebDavBackup）。
 * - 外观 / 阅读 / AI 设置：原生暂无对应持久化仓库，使用本地状态完整呈现 UI（会话内有效），待接入后落盘。
 * - 标签 / 分类 / 书单 / 阅读 / 笔记 / 存储明细：原生数据层暂未向本页暴露，渲染页面结构 + 空态占位。
 * - 关于 / 诊断：用 PackageManager / android.os.Build 展示真实设备与版本信息。
 *
 * 所有子页通过内部 currentSubPage 切换，不新增导航路由、不改动 AppNavigation.kt / Theme.kt。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    statsViewModel: StatsViewModel = hiltViewModel(),
    navController: NavHostController? = null,
) {
    val layout = LocalLayoutTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()

    val config by viewModel.config.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val pairMsg by viewModel.pairMsg.collectAsStateWithLifecycle()
    val syncMsg by viewModel.syncMsg.collectAsStateWithLifecycle()
    val webDavConfig by viewModel.webDavConfig.collectAsStateWithLifecycle()
    val webDavMsg by viewModel.webDavMsg.collectAsStateWithLifecycle()
    val webDavBackups by viewModel.webDavBackups.collectAsStateWithLifecycle()
    val aiMsg by viewModel.aiMsg.collectAsStateWithLifecycle()
    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
    val stats by statsViewModel.stats.collectAsStateWithLifecycle()
    val lastSyncResult by viewModel.lastSyncResult.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val books = libraryState.books

    var currentSubPage by remember { mutableStateOf<ProfileSubPage?>(null) }
    var moreExpanded by remember { mutableStateOf(false) }
    var confirmDialog by remember { mutableStateOf<ConfirmSpec?>(null) }

    // ---- 设置来源：持久化 SettingsStore（重启保持） ----
    val settingsVm: SettingsViewModel = hiltViewModel()
    val taxonomyVm: com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel = hiltViewModel()
    val appearance by settingsVm.appearance.collectAsStateWithLifecycle()
    val reader by settingsVm.reader.collectAsStateWithLifecycle()
    val ai by settingsVm.ai.collectAsStateWithLifecycle()
    // AI Key 用本地草稿编辑，保存时写回持久化（避免每次按键都写盘）
    var aiKeyDraft by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }

    // ---- 文件导出 / 导入 ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let { viewModel.export(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.import(context, it) } }

    val showMsg: (String) -> Unit = { scope.launch { snackbarHostState.showSnackbar(it) } }

    // ViewModel 状态消息透传为 Snackbar
    LaunchedEffect(pairMsg) { pairMsg?.let { showMsg(it) } }
    LaunchedEffect(syncMsg) { syncMsg?.let { showMsg(it) } }
    LaunchedEffect(webDavMsg) { webDavMsg?.let { showMsg(it) } }
    LaunchedEffect(aiMsg) { aiMsg?.let { showMsg(it) } }
    LaunchedEffect(bridgeStatus) { bridgeStatus?.let { showMsg(it) } }

    val webDavConfigured = !webDavConfig?.url.isNullOrBlank()
    val paired = config != null

    Scaffold(
        topBar = {
            if (currentSubPage == null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "本地档案",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = "我的", style = MaterialTheme.typography.headlineLarge)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { moreExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("数据备份") },
                                    onClick = { moreExpanded = false; currentSubPage = ProfileSubPage.STORAGE },
                                )
                                DropdownMenuItem(
                                    text = { Text("隐私安全") },
                                    onClick = { moreExpanded = false; currentSubPage = ProfileSubPage.PRIVACY },
                                )
                                DropdownMenuItem(
                                    text = { Text("关于应用") },
                                    onClick = { moreExpanded = false; currentSubPage = ProfileSubPage.ABOUT },
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = subPageEyebrow(currentSubPage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = subPageTitle(currentSubPage), style = MaterialTheme.typography.headlineLarge)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentSubPage = null }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = layout.pageHorizontal,
                vertical = layout.pageVertical,
            )

        when (val page = currentSubPage) {
            null -> ProfileHomeContent(
                modifier = modifier,
                paired = paired,
                webDavConfigured = webDavConfigured,
                aiConfigured = ai.enabled && ai.apiKey.isNotBlank(),
                appThemeLabel = themeLabel(appearance.themeMode),
                totalDurationMs = stats?.totalDurationMs ?: 0L,
                completedBookCount = stats?.completedBookCount ?: 0,
                inspirationCount = stats?.inspirationCount ?: 0,
                onNavigate = { currentSubPage = it },
                onClearCache = {
                    confirmDialog = ConfirmSpec(
                        title = "清理缓存",
                        message = "确认清理阅读器正文缓存？将删除本地缓存条目（含 EPUB 解压缓存、图片缓存），释放后可重新生成。不会删除正式书籍、进度、书签和笔记。",
                        onConfirm = {
                            val freed = clearReaderCache(context)
                            showMsg("已清理缓存，释放 ${formatBytes(freed)}")
                        },
                    )
                },
            )
            ProfileSubPage.SYNC -> SyncSubPage(
                modifier = modifier,
                paired = paired,
                pairing = pairing,
                syncing = syncing,
                baseUrl = config?.baseUrl,
                pendingDownloadCount = books.count { !isBookDownloaded(it) },
                lastSyncResult = lastSyncResult,
                syncLogs = syncLogs,
                onPairingTextChange = { viewModel.startPairing(it) },
                onScanQr = { navController?.navigate("pairing") },
                onSyncDesktop = { viewModel.syncNow() },
                onUnpair = { viewModel.unpair() },
                onRetryItem = { viewModel.retryItem(it) },
                onRetryFailed = { viewModel.retryFailed() },
            )
            ProfileSubPage.WEBDAV -> WebDavSubPage(
                modifier = modifier,
                initialUrl = webDavConfig?.url ?: "",
                initialUser = webDavConfig?.user ?: "",
                initialPass = webDavConfig?.pass ?: "",
                backups = webDavBackups,
                onSave = { url, user, pass -> viewModel.saveWebDav(url, user, pass); showMsg("WebDAV 配置已保存") },
                onUpload = { viewModel.backupNow(context) },
                onTest = { viewModel.testWebDav() },
                onRefreshList = { viewModel.loadWebDavBackups() },
                onDownload = { viewModel.downloadRestore(context, it) },
                onClear = { viewModel.clearWebDav(); showMsg("WebDAV 配置已清除") },
            )
            ProfileSubPage.APPEARANCE -> AppearanceSubPage(
                modifier = modifier,
                appTheme = appearance.themeMode,
                onThemeChange = { settingsVm.updateAppearance { copy(themeMode = it) } },
                paperTexture = appearance.paperTexture,
                onPaperTextureChange = { settingsVm.updateAppearance { copy(paperTexture = it) } },
            )
            ProfileSubPage.READER -> ReaderSettingsSubPage(
                modifier = modifier,
                readerMode = reader.readerMode, onReaderModeChange = { settingsVm.updateReader { copy(readerMode = it) } },
                pagerEngineMode = reader.pagerEngineMode, onPagerEngineModeChange = { settingsVm.updateReader { copy(pagerEngineMode = it) } },
                epubPagerEngineMode = reader.epubPagerEngineMode, onEpubPagerEngineModeChange = { settingsVm.updateReader { copy(epubPagerEngineMode = it) } },
                pageTurnEffect = reader.pageTurnEffect, onPageTurnEffectChange = { settingsVm.updateReader { copy(pageTurnEffect = it) } },
                tapZoneMode = reader.tapZoneMode, onTapZoneModeChange = { settingsVm.updateReader { copy(tapZoneMode = it) } },
                fontSize = reader.fontSize, onFontSizeChange = { settingsVm.updateReader { copy(fontSize = it) } },
                lineHeight = reader.lineHeight, onLineHeightChange = { settingsVm.updateReader { copy(lineHeight = it) } },
                paragraphSpacing = reader.paragraphSpacing, onParagraphSpacingChange = { settingsVm.updateReader { copy(paragraphSpacing = it) } },
                pageMargin = reader.pageMargin, onPageMarginChange = { settingsVm.updateReader { copy(pageMargin = it) } },
                readerBackground = reader.background, onReaderBackgroundChange = { settingsVm.updateReader { copy(background = it) } },
                immersiveMode = reader.immersiveMode, onImmersiveModeChange = { settingsVm.updateReader { copy(immersiveMode = it) } },
                showReaderInfo = reader.showReaderInfo, onShowReaderInfoChange = { settingsVm.updateReader { copy(showReaderInfo = it) } },
                chineseTypography = reader.chineseTypography, onChineseTypographyChange = { settingsVm.updateReader { copy(chineseTypography = it) } },
                autoHideSeconds = reader.autoHideSeconds, onAutoHideSecondsChange = { settingsVm.updateReader { copy(autoHideSeconds = it) } },
                keepAwake = reader.keepAwake, onKeepAwakeChange = { settingsVm.updateReader { copy(keepAwake = it) } },
                showProgressBar = reader.showProgressBar, onShowProgressBarChange = { settingsVm.updateReader { copy(showProgressBar = it) } },
                fontWeightBold = reader.fontWeightBold, onFontWeightBoldChange = { settingsVm.updateReader { copy(fontWeightBold = it) } },
                onReset = {
                    confirmDialog = ConfirmSpec(
                        title = "重置阅读设置",
                        message = "确认将字号、行距、段距、边距、主题、翻页模式等恢复为默认值？阅读进度、书籍、书签和笔记不会受影响。",
                        onConfirm = { settingsVm.updateReader { ReaderSettings() } },
                    )
                },
            )
            ProfileSubPage.AI -> AiSettingsSubPage(
                modifier = modifier,
                enabled = ai.enabled, onEnabledChange = { settingsVm.updateAi { copy(enabled = it) } },
                baseUrl = ai.baseUrl, onBaseUrlChange = { settingsVm.updateAi { copy(baseUrl = it) } },
                model = ai.model, onModelChange = { settingsVm.updateAi { copy(model = it) } },
                temperature = ai.temperature, onTemperatureChange = { settingsVm.updateAi { copy(temperature = it) } },
                keyDraft = aiKeyDraft, onKeyDraftChange = { aiKeyDraft = it },
                keySaved = ai.apiKey.isNotBlank(),
                prompt = ai.prompt, onPromptChange = { settingsVm.updateAi { copy(prompt = it) } },
                onSave = { settingsVm.updateAi { copy(apiKey = aiKeyDraft) }; showMsg("AI 设置已保存") },
                onTest = { viewModel.testAi() },
                onClearKey = { aiKeyDraft = ""; settingsVm.updateAi { copy(apiKey = "") }; showMsg("已清除 API Key") },
            )
            ProfileSubPage.TAGS, ProfileSubPage.CATEGORIES, ProfileSubPage.SHELVES ->
                LibrarySubPage(
                    modifier = modifier,
                    page = page,
                    taxonomyVm = taxonomyVm,
                    books = books,
                    onOpenBook = { navController?.navigate("reader/${it.id}") },
                    onMessage = showMsg,
                )
            ProfileSubPage.READING, ProfileSubPage.NOTES ->
                ReadingNotesSubPage(
                    modifier = modifier,
                    page = page,
                    libraryState = libraryState,
                )
            ProfileSubPage.STORAGE -> StorageSubPage(
                modifier = modifier,
                totalBooks = books.size,
                downloadedCount = books.count { isBookDownloaded(it) },
                cachedCount = libraryState.cachedCount,
                cacheBytes = libraryState.cacheBytes,
                indexBytes = books.sumOf { it.size.toLong() },
                formatCounts = remember(books) { books.groupingBy { it.format }.eachCount() },
                onExport = { exportLauncher.launch("cra-export-${System.currentTimeMillis()}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onClearCache = {
                    confirmDialog = ConfirmSpec(
                        title = "清理缓存",
                        message = "确认清理阅读器正文缓存？不会删除正式数据。",
                        onConfirm = {
                            val freed = clearReaderCache(context)
                            showMsg("已清理缓存，释放 ${formatBytes(freed)}")
                        },
                    )
                },
            )
            ProfileSubPage.PRIVACY -> PrivacySubPage(modifier = modifier)
            ProfileSubPage.ABOUT -> AboutSubPage(modifier = modifier, context = context)
            ProfileSubPage.DIAGNOSTICS -> DiagnosticsSubPage(modifier = modifier)
        }
    }

    confirmDialog?.let { spec ->
        GlassAlertDialog(
            onDismissRequest = { confirmDialog = null },
            confirmButton = {
                TextButton(onClick = { spec.onConfirm(); confirmDialog = null }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) { Text("取消") }
            },
            title = { Text(spec.title) },
            text = { Text(spec.message) },
        )
    }
}

// ============================== 类型与文案 ==============================

private enum class ProfileSubPage {
    SYNC, WEBDAV, APPEARANCE, READER, AI,
    TAGS, CATEGORIES, SHELVES, READING, NOTES,
    STORAGE, PRIVACY, ABOUT, DIAGNOSTICS
}

private data class ConfirmSpec(val title: String, val message: String, val onConfirm: () -> Unit)

private fun themeLabel(theme: String): String = when (theme) {
    "system" -> "跟随系统"
    "dark" -> "深色"
    else -> "浅色"
}

private fun subPageTitle(page: ProfileSubPage?): String = when (page) {
    ProfileSubPage.SYNC -> "同步状态"
    ProfileSubPage.WEBDAV -> "WebDAV 设置"
    ProfileSubPage.APPEARANCE -> "应用外观"
    ProfileSubPage.READER -> "阅读设置"
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

private fun subPageEyebrow(page: ProfileSubPage?): String = when (page) {
    ProfileSubPage.SYNC -> "局域网同步"
    ProfileSubPage.WEBDAV -> "跨设备备份"
    ProfileSubPage.APPEARANCE -> "外观"
    ProfileSubPage.READER -> "阅读体验"
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

// ============================== 主页 ==============================

@Composable
private fun ProfileHomeContent(
    modifier: Modifier,
    paired: Boolean,
    webDavConfigured: Boolean,
    aiConfigured: Boolean,
    appThemeLabel: String,
    totalDurationMs: Long,
    completedBookCount: Int,
    inspirationCount: Int,
    onNavigate: (ProfileSubPage) -> Unit,
    onClearCache: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(layout.contentGap)) {
        // 顶部同步状态卡（对应 ProfileHome 的 compact-profile-card + profile-grid）
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("创作阅读助手", style = MaterialTheme.typography.titleMedium)
                    Text("本地优先", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HomeStat("阅读时长", formatDuration(totalDurationMs))
                HomeStat("累计读完", completedBookCount.toString())
                HomeStat("灵感数量", inspirationCount.toString())
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(label = "同步", value = if (paired) "已连接电脑" else "从未同步", onClick = { onNavigate(ProfileSubPage.SYNC) })
            QuickTile(label = "笔记", value = "查看", onClick = { onNavigate(ProfileSubPage.NOTES) })
        }

        MenuGroup(title = "阅读与外观") {
            MenuItem(Icons.Filled.TextFields, "阅读设置", "字号、行距、主题、翻页模式", onClick = { onNavigate(ProfileSubPage.READER) })
            MenuItem(Icons.Filled.DarkMode, "应用外观", appThemeLabel, onClick = { onNavigate(ProfileSubPage.APPEARANCE) })
            MenuItem(Icons.AutoMirrored.Filled.MenuBook, "我的阅读", "进度、时长、书籍状态", onClick = { onNavigate(ProfileSubPage.READING) })
        }

        MenuGroup(title = "数据与存储") {
            MenuItem(Icons.Filled.Storage, "存储管理", "导出 / 导入数据快照", onClick = { onNavigate(ProfileSubPage.STORAGE) })
            MenuItem(Icons.Filled.Delete, "清理缓存", "清理阅读器正文缓存", danger = true, onClick = onClearCache)
            MenuItem(Icons.Filled.Sell, "标签管理", "书籍 / 灵感 / 笔记标签", onClick = { onNavigate(ProfileSubPage.TAGS) })
            MenuItem(Icons.Filled.Folder, "分类管理", "整理书籍分类", onClick = { onNavigate(ProfileSubPage.CATEGORIES) })
            MenuItem(Icons.Filled.Book, "书单管理", "自定义书单", onClick = { onNavigate(ProfileSubPage.SHELVES) })
        }

        MenuGroup(title = "我的书评与笔记") {
            MenuItem(Icons.Filled.Description, "我的书评 / 笔记", "书签与读书笔记", onClick = { onNavigate(ProfileSubPage.NOTES) })
        }

        MenuGroup(title = "同步与工具") {
            MenuItem(Icons.Filled.Refresh, "局域网同步", if (paired) "已连接电脑" else "从未同步", onClick = { onNavigate(ProfileSubPage.SYNC) })
            MenuItem(Icons.Filled.Cloud, "WebDAV 设置", if (webDavConfigured) "已配置" else "未配置", onClick = { onNavigate(ProfileSubPage.WEBDAV) })
            MenuItem(Icons.Filled.AutoAwesome, "AI 助手", if (aiConfigured) "已配置 Key" else "未配置", onClick = { onNavigate(ProfileSubPage.AI) })
        }

        MenuGroup(title = "帮助与关于") {
            MenuItem(Icons.Filled.BugReport, "日志与诊断", "运行环境与问题记录", onClick = { onNavigate(ProfileSubPage.DIAGNOSTICS) })
            MenuItem(Icons.Filled.Security, "隐私安全", "本地优先", onClick = { onNavigate(ProfileSubPage.PRIVACY) })
            MenuItem(Icons.Filled.Info, "关于", "版本与开源许可", onClick = { onNavigate(ProfileSubPage.ABOUT) })
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.QuickTile(label: String, value: String, onClick: () -> Unit) {
    SectionCard(
        modifier = Modifier.weight(1f),
        onClick = onClick,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.relatedGap)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(contentPadding = 0.dp, content = content)
    }
}

@Composable
private fun ColumnScope.MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    desc: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    SettingRow(
        title = label,
        subtitle = desc,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

// ============================== 同步页 ==============================

@Composable
private fun SyncSubPage(
    modifier: Modifier,
    paired: Boolean,
    pairing: Boolean,
    syncing: Boolean,
    baseUrl: String?,
    pendingDownloadCount: Int,
    lastSyncResult: SyncResultDetail?,
    syncLogs: List<String>,
    onPairingTextChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onSyncDesktop: () -> Unit,
    onUnpair: () -> Unit,
    onRetryItem: (SyncFailedItem) -> Unit,
    onRetryFailed: () -> Unit,
) {
    var pairingText by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = if (paired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (paired) "已连接电脑" else "未连接电脑", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$pendingDownloadCount 本待下载正文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("电脑端开启同步服务后，可以扫码或粘贴配对 URL。同步只更新书架、灵感和进度，书籍正文可在书架按需下载。每次同步前会自动备份本地数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pairingText,
                    onValueChange = { pairingText = it; onPairingTextChange(it) },
                    placeholder = { Text("粘贴电脑端配对 URL 或二维码载荷") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScanQr) { Icon(Icons.Filled.QrCode, contentDescription = null); Text("扫码", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = { onPairingTextChange(pairingText) }, enabled = !pairing) {
                        if (pairing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Icon(Icons.Filled.Wifi, contentDescription = null)
                        Text(if (pairing) "连接中" else "连接电脑", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(onClick = onSyncDesktop, enabled = paired && !syncing) {
                        if (syncing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Icon(Icons.Filled.Sync, contentDescription = null)
                        Text(if (syncing) "同步中" else "立即同步", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (paired) {
                    TextButton(onClick = onUnpair) { Icon(Icons.Filled.LinkOff, contentDescription = null); Text("解除配对", modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
        if (paired && !baseUrl.isNullOrBlank()) {
            Text("已配对服务：$baseUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("（尚未配对电脑端同步服务；扫码或粘贴配对 URL 后即可同步。）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        lastSyncResult?.let { result ->
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Column {
                            Text(
                                if (result.success) "最近一次同步成功" else "最近一次同步失败",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${formatDateTime(result.timestamp)} · 耗时 ${formatSyncDuration(result.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (result.success) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SyncStatColumn(
                                icon = Icons.Outlined.Upload,
                                title = "上传",
                                items = listOf(
                                    "${result.uploaded.inspirations} 条灵感",
                                    "${result.uploaded.books} 本书",
                                    "${result.uploaded.progress} 条进度",
                                    "${result.uploaded.bookFiles} 个正文文件",
                                ),
                            )
                            SyncStatColumn(
                                icon = Icons.Filled.Download,
                                title = "下载",
                                items = listOf(
                                    "${result.downloaded.inspirations} 条灵感",
                                    "${result.downloaded.books} 本书",
                                    "${result.downloaded.progress} 条进度",
                                    "${result.downloaded.sessions} 条阅读会话",
                                ),
                            )
                            SyncStatColumn(
                                icon = Icons.Filled.Description,
                                title = "待处理",
                                items = listOf("${result.pendingDownloadCount} 本待下载正文"),
                            )
                        }
                    }
                    if (result.failedItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Text("${result.failedItems.size} 项失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            result.failedItems.take(10).forEach { item ->
                                val typeLabel = when (item.type) {
                                    "book" -> "书籍"
                                    "inspiration" -> "灵感"
                                    "progress" -> "进度"
                                    "session" -> "会话"
                                    "book_file" -> "正文下载"
                                    "sync" -> "同步"
                                    else -> item.type
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "· $typeLabel${item.title?.let { "《$it》" } ?: ""}：${item.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                    )
                                    TextButton(onClick = { onRetryItem(item) }, enabled = !syncing) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Text("重试", modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                            if (result.failedItems.size > 10) {
                                Text(
                                    "…以及其余 ${result.failedItems.size - 10} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = onRetryFailed, enabled = !syncing) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("重试失败项", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    if (result.conflicts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Text("${result.conflicts.size} 条冲突（服务端保留）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            result.conflicts.take(5).forEach { c ->
                                val typeLabel = when (c.type) {
                                    "book" -> "书籍"
                                    "inspiration" -> "灵感"
                                    "progress" -> "进度"
                                    "session" -> "会话"
                                    else -> c.type
                                }
                                Text(
                                    "· $typeLabel${c.title?.let { "《$it》" } ?: ""}：本地 ${formatDateTimeShort(c.localUpdatedAt)} vs 远端 ${formatDateTimeShort(c.remoteUpdatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (result.conflicts.size > 5) {
                                Text(
                                    "…以及其余 ${result.conflicts.size - 5} 条",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showLogs = !showLogs },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("同步日志 / 最近一次错误", style = MaterialTheme.typography.titleSmall)
                    Icon(
                        if (showLogs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showLogs) "收起" else "展开",
                    )
                }
                if (showLogs) {
                    if (syncLogs.isEmpty()) {
                        Text("暂无同步日志。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            syncLogs.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<String>,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        items.forEach { item ->
            Text(item, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================== WebDAV 页 ==============================

@Composable
private fun WebDavSubPage(
    modifier: Modifier,
    initialUrl: String,
    initialUser: String,
    initialPass: String,
    backups: List<com.creationreadingassistant.feature.sync.WebDavBackup.BackupFile>,
    onSave: (String, String, String) -> Unit,
    onUpload: () -> Unit,
    onTest: () -> Unit,
    onRefreshList: () -> Unit,
    onDownload: (String) -> Unit,
    onClear: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }
    val hasSavedPass = initialPass.isNotBlank()

    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) onRefreshList()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("同步目录固定为 .creation-reading-assistant/，会上传 manifest、records 和 books。WebDAV 密码 / token 仅保存在应用本地沙箱，AI Key 不参与同步。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = url, onValueChange = { url = it }, placeholder = { Text("https://example.com/dav") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                OutlinedTextField(value = user, onValueChange = { user = it }, placeholder = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                OutlinedTextField(value = pass, onValueChange = { pass = it }, placeholder = { Text(if (hasSavedPass) "已保存密码 / token；留空则继续使用" else "密码或 token") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(if (hasSavedPass) "已在应用本地沙箱保存 WebDAV 密码 / token；不会导出或参与同步。" else "还没有保存 WebDAV 密码 / token。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTest, enabled = url.isNotBlank()) { Icon(Icons.Filled.Wifi, contentDescription = null); Text("测试", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onUpload, enabled = url.isNotBlank()) { Icon(Icons.Filled.Upload, contentDescription = null); Text("上传", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onRefreshList, enabled = url.isNotBlank()) { Icon(Icons.Filled.Refresh, contentDescription = null); Text("刷新列表", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onClear) { Text("清除凭证") }
                }
                Button(onClick = { onSave(url.trim(), user.trim(), pass) }, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("远程备份列表", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${backups.size} 个文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (backups.isEmpty()) {
                    Text("暂无备份文件。点击上方「上传」或「刷新列表」获取。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        backups.take(8).forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatBytes(file.size)} · ${file.lastModified}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onDownload(file.name) }) { Text("恢复") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================== 外观页 ==============================

@Composable
private fun AppearanceSubPage(
    modifier: Modifier,
    appTheme: String,
    onThemeChange: (String) -> Unit,
    paperTexture: Boolean,
    onPaperTextureChange: (Boolean) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Text("应用外观影响首页、书架、灵感、统计和设置；阅读页正文背景仍在阅读器设置里单独控制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            ThemeSwitchButton(
                currentMode = appTheme,
                onModeChange = onThemeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard {
            SettingRow(
                title = "纸张纹理",
                subtitle = "开启后界面叠加纸感底纹",
                trailing = { Switch(checked = paperTexture, onCheckedChange = onPaperTextureChange) },
            )
        }
    }
}

// ============================== 阅读设置页 ==============================

@Composable
private fun ReaderSettingsSubPage(
    modifier: Modifier,
    readerMode: String, onReaderModeChange: (String) -> Unit,
    pagerEngineMode: String, onPagerEngineModeChange: (String) -> Unit,
    epubPagerEngineMode: String, onEpubPagerEngineModeChange: (String) -> Unit,
    pageTurnEffect: String, onPageTurnEffectChange: (String) -> Unit,
    tapZoneMode: String, onTapZoneModeChange: (String) -> Unit,
    fontSize: Float, onFontSizeChange: (Float) -> Unit,
    lineHeight: Float, onLineHeightChange: (Float) -> Unit,
    paragraphSpacing: Float, onParagraphSpacingChange: (Float) -> Unit,
    pageMargin: Float, onPageMarginChange: (Float) -> Unit,
    readerBackground: String, onReaderBackgroundChange: (String) -> Unit,
    immersiveMode: Boolean, onImmersiveModeChange: (Boolean) -> Unit,
    showReaderInfo: Boolean, onShowReaderInfoChange: (Boolean) -> Unit,
    chineseTypography: Boolean, onChineseTypographyChange: (Boolean) -> Unit,
    autoHideSeconds: Int, onAutoHideSecondsChange: (Int) -> Unit,
    keepAwake: Boolean, onKeepAwakeChange: (Boolean) -> Unit,
    showProgressBar: Boolean, onShowProgressBarChange: (Boolean) -> Unit,
    fontWeightBold: Boolean, onFontWeightBoldChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("阅读模式")
                SegmentedRow(
                    options = listOf("paged" to "左右翻页", "scroll" to "上下滚动"),
                    selected = readerMode,
                    onSelect = onReaderModeChange,
                )
                SegmentedRow(
                    options = listOf("off" to "关闭", "auto" to "自动", "on" to "强制开启"),
                    selected = pagerEngineMode,
                    onSelect = onPagerEngineModeChange,
                )
                Text("上方：TXT 新分页引擎", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedRow(
                    options = listOf("off" to "关闭", "auto" to "自动", "on" to "强制开启"),
                    selected = epubPagerEngineMode,
                    onSelect = onEpubPagerEngineModeChange,
                )
                Text("上方：EPUB 新分页引擎", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionTitle("翻页与点击")
                SegmentedRow(
                    options = listOf(
                        "none" to "无动画",
                        "fade" to "柔和淡入",
                        "slide" to "左右滑动",
                        "cover" to "覆盖翻页",
                    ),
                    selected = pageTurnEffect,
                    onSelect = onPageTurnEffectChange,
                )
                SegmentedRow(
                    options = listOf("three-zone" to "左中右三区", "five-zone" to "上下扩展五区"),
                    selected = tapZoneMode,
                    onSelect = onTapZoneModeChange,
                )
                Text("四档动效都直接移动轻量页面层，不创建页面截图。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("排版")
                RangeRow("字号", fontSize, 12f, 32f, 1f, valueLabel = { "${it.toInt()}" }, onValueChange = onFontSizeChange)
                RangeRow("行距", lineHeight, 1.2f, 2.5f, 0.05f, valueLabel = { "%.2f".format(it) }, onValueChange = onLineHeightChange)
                RangeRow("段距", paragraphSpacing, 0.5f, 2f, 0.05f, valueLabel = { "%.2f".format(it) + "em" }, onValueChange = onParagraphSpacingChange)
                RangeRow("边距", pageMargin, 10f, 42f, 1f, valueLabel = { "${it.toInt()}px" }, onValueChange = onPageMarginChange)
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("阅读主题")
                // 收敛为定稿 4 档 + 跟随外观（旧 7 色 key 已由 SettingsStore.migrateReaderBg 迁移，不再暴露入口）
                SegmentedRow(
                    options = listOf(
                        "follow" to "跟随外观",
                        "white" to "白纸",
                        "warm" to "暖纸",
                        "green" to "护眼",
                        "night" to "夜读",
                    ),
                    selected = readerBackground,
                    onSelect = onReaderBackgroundChange,
                )
            }
        }
        SectionCard {
            Column {
                SectionTitle("显示与辅助", modifier = Modifier.padding(8.dp))
                ToggleRow("沉浸模式", immersiveMode, onImmersiveModeChange)
                ToggleRow("安静阅读信息", showReaderInfo, onShowReaderInfoChange)
                ToggleRow("中文排版优化", chineseTypography, onChineseTypographyChange)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("菜单自动隐藏")
                    SegmentedRow(
                        options = listOf(0 to "不自动隐藏", 3 to "3 秒", 5 to "5 秒", 8 to "8 秒"),
                        selected = autoHideSeconds,
                        onSelect = onAutoHideSecondsChange,
                        enabled = immersiveMode,
                    )
                }
                ToggleRow("屏幕常亮", keepAwake, onKeepAwakeChange)
                ToggleRow("显示进度条", showProgressBar, onShowProgressBarChange)
                ToggleRow("粗体文字", fontWeightBold, onFontWeightBoldChange)
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("危险操作")
                Text("重置阅读设置只会恢复字号、行距、主题等默认值，不会删除阅读进度、书籍、书签和笔记。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("重置阅读设置", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

// ============================== AI 设置页 ==============================

@Composable
private fun AiSettingsSubPage(
    modifier: Modifier,
    enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    baseUrl: String, onBaseUrlChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    temperature: Float, onTemperatureChange: (Float) -> Unit,
    keyDraft: String, onKeyDraftChange: (String) -> Unit,
    keySaved: Boolean,
    prompt: String, onPromptChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClearKey: () -> Unit,
) {
    var showKey by remember { mutableStateOf(keyDraft.isBlank()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DegradedNote("手机端 AI Key 已持久化在应用本地沙箱，不会跨设备同步，也不参与 WebDAV 同步或数据导出；配置 OpenAI-compatible 接口后，灵感详情页可一键生成 AI 候选版本。")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(
                    title = "启用 AI 助手",
                    trailing = { Switch(checked = enabled, onCheckedChange = onEnabledChange) },
                )
                OutlinedTextField(value = baseUrl, onValueChange = onBaseUrlChange, placeholder = { Text("https://dashscope.aliyuncs.com/compatible-mode/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                OutlinedTextField(value = model, onValueChange = onModelChange, placeholder = { Text("qwen-plus") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                Column {
                    Text("创造性 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = temperature,
                        onValueChange = onTemperatureChange,
                        valueRange = 0f..1.5f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = onKeyDraftChange,
                    placeholder = { Text(if (keySaved) "已保存 API Key；留空则不修改" else "粘贴手机端 API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(if (keySaved) "已保存 API Key；不会同步、导出或上传到 WebDAV。" else "还没有保存手机端 API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("个性化提示词（可选）") },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave) { Text("保存设置") }
                    OutlinedButton(onClick = onTest) { Icon(Icons.Filled.Wifi, contentDescription = null); Text("测试", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onClearKey) { Text("清除 Key") }
                }
            }
        }
    }
}

// ============================== 书库管理（标签/分类/书单） ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySubPage(
    modifier: Modifier,
    page: ProfileSubPage,
    taxonomyVm: com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel,
    books: List<BookEntity>,
    onOpenBook: (BookEntity) -> Unit,
    onMessage: (String) -> Unit,
) {
    val allTags by taxonomyVm.allTags.collectAsStateWithLifecycle()
    val allCategories by taxonomyVm.allCategories.collectAsStateWithLifecycle()
    val allShelves by taxonomyVm.allShelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editName by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkedBookIds by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val bookMap = remember(books) { books.associateBy { it.id } }

    fun loadLinkedBooks(id: String) {
        scope.launch(Dispatchers.IO) {
            val ids = when (page) {
                ProfileSubPage.TAGS -> taxonomyVm.getBookIdsByTag(id)
                ProfileSubPage.CATEGORIES -> taxonomyVm.getBookIdsByCategory(id)
                ProfileSubPage.SHELVES -> taxonomyVm.getBookIdsByShelf(id)
                else -> emptyList()
            }
            linkedBookIds = linkedBookIds + (id to ids)
        }
    }

    fun toggleExpanded(id: String) {
        if (expandedIds.contains(id)) {
            expandedIds = expandedIds - id
        } else {
            expandedIds = expandedIds + id
            if (!linkedBookIds.containsKey(id)) loadLinkedBooks(id)
        }
    }

    val items: List<Pair<String, String>> = when (page) {
        ProfileSubPage.TAGS -> allTags.map { it.id to it.name }
        ProfileSubPage.CATEGORIES -> allCategories.map { it.id to it.name }
        ProfileSubPage.SHELVES -> allShelves.map { it.id to it.name }
        else -> emptyList()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isNotEmpty()) {
            items.forEach { (id, name) ->
                val expanded = expandedIds.contains(id)
                val entityTag = if (page == ProfileSubPage.TAGS) allTags.find { it.id == id } else null
                val entityCategory = if (page == ProfileSubPage.CATEGORIES) allCategories.find { it.id == id } else null
                SectionCard(
                    onClick = { toggleExpanded(id) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (page) {
                                    ProfileSubPage.TAGS -> Icons.Filled.Sell
                                    ProfileSubPage.CATEGORIES -> Icons.Filled.Folder
                                    else -> Icons.Filled.Book
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                if (page == ProfileSubPage.TAGS && !entityTag?.type.isNullOrBlank()) {
                                    Text("类型：${entityTag?.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { editingItem = id to name; editName = name }) {
                                Icon(Icons.Filled.BorderColor, contentDescription = "编辑", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                when (page) {
                                    ProfileSubPage.TAGS -> taxonomyVm.deleteTag(id)
                                    ProfileSubPage.CATEGORIES -> taxonomyVm.deleteCategory(id)
                                    ProfileSubPage.SHELVES -> taxonomyVm.deleteShelf(id)
                                    else -> {}
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (page == ProfileSubPage.CATEGORIES) {
                                Text("分类色调", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "default" to "默认",
                                        "warm" to "暖色",
                                        "cool" to "冷色",
                                        "green" to "绿色",
                                        "night" to "夜间",
                                    ).forEach { (tone, label) ->
                                        val selected = entityCategory?.cover_tone == tone
                                        SelectablePill(
                                            text = label,
                                            selected = selected,
                                            onClick = { taxonomyVm.updateCategoryTone(id, tone) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            val linkedIds = linkedBookIds[id] ?: emptyList()
                            val linkedBooks = remember(linkedIds, books) { linkedIds.mapNotNull { bookMap[it] } }
                            if (linkedBooks.isEmpty()) {
                                Text("暂无关联书籍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("关联书籍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    linkedBooks.forEach { book ->
                                        Card(
                                            modifier = Modifier.clickable { onOpenBook(book) },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        ) {
                                            Text(
                                                book.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        when (page) { ProfileSubPage.TAGS -> Icons.Filled.Sell; ProfileSubPage.CATEGORIES -> Icons.Filled.Folder; else -> Icons.Filled.Book },
                        contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(when (page) { ProfileSubPage.TAGS -> "还没有标签"; ProfileSubPage.CATEGORIES -> "还没有分类"; else -> "还没有书单" }, style = MaterialTheme.typography.titleMedium)
                    Text("点击下方按钮创建。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SectionDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (showCreate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("输入名称") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { if (newName.isNotBlank()) { when (page) { ProfileSubPage.TAGS -> taxonomyVm.createTag(newName.trim()); ProfileSubPage.CATEGORIES -> taxonomyVm.createCategory(newName.trim()); ProfileSubPage.SHELVES -> taxonomyVm.createShelf(newName.trim()); else -> {} }; showCreate = false; newName = "" } }) { Text("创建") }
                TextButton(onClick = { showCreate = false; newName = "" }) { Text("取消") }
            }
        } else {
            TextButton(onClick = { showCreate = true }) { Text("+ 创建新的") }
        }
    }

    // 重命名对话框
    editingItem?.let { (id, name) ->
        GlassAlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("重命名") },
            text = { OutlinedTextField(value = editName, onValueChange = { editName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank() && editName != name) {
                        when (page) {
                            ProfileSubPage.TAGS -> taxonomyVm.renameTag(id, editName.trim())
                            ProfileSubPage.CATEGORIES -> taxonomyVm.renameCategory(id, editName.trim())
                            ProfileSubPage.SHELVES -> taxonomyVm.renameShelf(id, editName.trim())
                            else -> {}
                        }
                    }
                    editingItem = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("取消") } },
        )
    }
}

// ============================== 阅读与笔记 ==============================

@Composable
private fun ReadingNotesSubPage(
    modifier: Modifier,
    page: ProfileSubPage,
    libraryState: ProfileLibraryState,
) {
    val books = libraryState.books
    val notes = libraryState.notes
    val bookMap = remember(books) { books.associateBy { it.id } }
    val progressMap = libraryState.progressByBook
    val sessionsByBook = libraryState.readingDurationByBook

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (page == ProfileSubPage.READING) {
            val readingBooks = remember(books, progressMap) {
                books.sortedByDescending { progressMap[it.id]?.last_read_at ?: it.updated_at }
            }
            if (readingBooks.isEmpty()) {
                EmptyCard(icon = Icons.AutoMirrored.Filled.MenuBook, title = "还没有阅读记录", body = "打开任意书籍开始阅读后，这里会按最近阅读时间展示档案。")
            } else {
                readingBooks.forEach { book ->
                    val p = progressMap[book.id]
                    ReadingBookItem(
                        book = book,
                        progress = p,
                        totalMs = sessionsByBook[book.id] ?: 0L,
                    )
                }
            }
        } else {
            if (notes.isEmpty()) {
                EmptyCard(icon = Icons.Filled.Description, title = "还没有笔记", body = "在阅读页选中文字添加笔记或书签后，它们会出现在这里。")
            } else {
                notes.forEach { note ->
                    NoteItem(note = note, book = note.book_id?.let { bookMap[it] })
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ReadingBookItem(book: BookEntity, progress: ReadingProgressEntity?, totalMs: Long) {
    val pct = (progress?.progress_percent ?: 0f).roundToInt()
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!book.author.isNullOrBlank()) {
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("进度 ${pct}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("累计 ${formatDuration(totalMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            progress?.last_read_at?.let {
                Text("最近阅读：${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteItem(note: NoteEntity, book: BookEntity?) {
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                note.title.ifBlank { "笔记" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val body = note.body.takeIf { it.isNotBlank() } ?: note.excerpt ?: ""
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    book?.let { "《${it.title}》" } ?: (note.chapter_title ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDateTime(note.created_at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================== 存储管理 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageSubPage(
    modifier: Modifier,
    totalBooks: Int,
    downloadedCount: Int,
    cachedCount: Int,
    cacheBytes: Long,
    indexBytes: Long,
    formatCounts: Map<String, Int>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearCache: () -> Unit,
) {
    val cacheBudget = 100 * 1024 * 1024L
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("导出会保存灵感、书库元数据、进度、阅读记录、笔记、标签、分类和同步账号信息；不会导出 AI Key、WebDAV 密码 / token 或设备私有路径。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Icon(Icons.Filled.Download, contentDescription = null); Text("导出数据", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onImport) { Icon(Icons.Filled.Upload, contentDescription = null); Text("导入数据", modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
        SectionCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("存储概览", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StorageStat(icon = Icons.Filled.Book, value = totalBooks.toString(), label = "总书籍")
                    StorageStat(icon = Icons.Filled.Download, value = "$downloadedCount 本", label = "已下载")
                    StorageStat(icon = Icons.Filled.Storage, value = formatBytes(cacheBytes), label = "正文缓存")
                    StorageStat(icon = Icons.Filled.Search, value = formatBytes(indexBytes), label = "索引大小")
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("正文缓存占用", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(cacheBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { (cacheBytes.toFloat() / cacheBudget).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "参考预算 100 MB，超出后建议清理缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (formatCounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("格式分布", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            formatCounts.toSortedMap().forEach { (fmt, count) ->
                                val label = when (fmt.lowercase()) {
                                    "txt" -> "TXT"
                                    "md" -> "Markdown"
                                    "epub" -> "EPUB"
                                    else -> fmt.uppercase()
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SectionCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("阅读器正文缓存")
                    Text("$cachedCount 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("缓存可在重新打开书籍时重新生成；清理不影响正式数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onClearCache) { Icon(Icons.Filled.Delete, contentDescription = null); Text("清理缓存", modifier = Modifier.padding(start = 6.dp)) }
            }
        }
    }
}

@Composable
private fun StorageStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 72.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================== 隐私安全 ==============================

@Composable
private fun PrivacySubPage(modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column {
                PrivacyItem(Icons.Filled.Security, "本地优先", "没有账号服务器。书籍、灵感、进度和笔记默认保存在手机本地。")
                PrivacyItem(Icons.Filled.Wifi, "同步可控", "局域网同步需要你手动连接电脑；WebDAV 需要你主动配置地址。")
                PrivacyItem(Icons.Filled.AutoAwesome, "密钥隔离", "AI Key 和 WebDAV 密码 / token 仅保存在应用本地沙箱，不参与电脑同步、WebDAV 同步或数据导出。")
                PrivacyItem(Icons.Filled.Storage, "路径隔离", "本地文件路径、readerPreview、临时 URI 等设备私有字段不会进入同步 payload。")
            }
        }
    }
}

@Composable
private fun PrivacyItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

// ============================== 关于 ==============================

@Composable
private fun AboutSubPage(modifier: Modifier, context: Context) {
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("未知")
    }
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var hasUpdate by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var lastCheckAt by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { AppLog.e("About", "打开链接失败：$url") }
    }

    fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    fun checkUpdate() {
        checking = true
        updateStatus = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL(MOBILE_RELEASE_API_URL).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.getOrNull(1) ?: ""
                val bodyRaw = Regex("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(text)?.groupValues?.getOrNull(1)
                val current = versionName.toString().trimStart('v', 'V')
                val body = bodyRaw?.let { unescapeJsonString(it).trim().takeIf { s -> s.isNotBlank() } }
                when {
                    tag.isBlank() -> "未获取到版本信息"
                    tag.trimStart('v', 'V') == current -> {
                        hasUpdate = false
                        latestVersion = null
                        releaseNotes = null
                        "已是最新版本（当前 $versionName）"
                    }
                    else -> {
                        hasUpdate = true
                        latestVersion = tag
                        releaseNotes = body
                        "发现新版本 $tag（当前 $versionName）"
                    }
                }
            }.onSuccess {
                checking = false
                lastCheckAt = Instant.now().toString()
                updateStatus = it
                AppLog.event("About", "检查更新：$it")
            }.onFailure {
                checking = false
                updateStatus = "更新检查失败：${it.message}"
                AppLog.e("About", "更新检查失败：${it.message}")
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Text("创作阅读助手", style = MaterialTheme.typography.titleLarge)
                Text("Android 端 · 本地优先 · 灵感中心特色版", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("版本：$versionName", style = MaterialTheme.typography.bodyMedium)
                Text("支持：TXT / Markdown / EPUB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("同步：电脑局域网 / WebDAV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionCard {
            Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开源与致谢", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("个人自用构建，不对外分发", style = MaterialTheme.typography.titleMedium)
                Text("阅读内核为自研实现，设计上参考了 Legado（开源阅读应用，GPL-3.0）等项目。若未来对外分发，将依 GPL-3.0 要求提供完整源代码与修改说明。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasUpdate) {
            Card(
                shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("发现新版本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("最新版本：$latestVersion（当前 $versionName）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    releaseNotes?.let { notes ->
                        Text(
                            notes.lines().take(12).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(onClick = { openUrl(MOBILE_RELEASES_URL) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("前往下载", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("应用更新", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("检查新版本", style = MaterialTheme.typography.titleMedium)
                Text("发布新版后可在此检查并前往安装包下载页；Android 仍会要求你确认安装。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { if (hasUpdate) openUrl(MOBILE_RELEASES_URL) else checkUpdate() },
                    enabled = !checking,
                ) {
                    if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        if (hasUpdate) "重新检查" else "检查更新",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (updateStatus != null) {
                    Text(updateStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (lastCheckAt != null) {
                    Text("上次检查：${formatDateTime(lastCheckAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================== 诊断 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsSubPage(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by AppLog.entries.collectAsStateWithLifecycle()
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var selectedLevel by remember { mutableStateOf<AppLog.Level?>(null) }
    var selectedModule by remember { mutableStateOf<String?>(null) }

    val modules = remember(logs) { logs.map { it.module }.distinct().sorted() }
    val filtered = remember(logs, selectedLevel, selectedModule) {
        logs.filter {
            (selectedLevel == null || it.level == selectedLevel) &&
                (selectedModule == null || it.module == selectedModule)
        }
    }

    val deviceId = remember {
        runCatching {
            val prefs = context.getSharedPreferences("sync_config", 0)
            prefs.getString("device_id", null) ?: "未知"
        }.getOrDefault("未知")
    }
    val info = listOf(
        "应用版本" to (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("未知")),
        "设备标识" to "${deviceId.take(8)}…",
        "厂商 / 型号" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "系统版本" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "语言" to (context.resources.configuration.locales.get(0)?.toString() ?: "未知"),
    )

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("错误码", text))
        exportMsg = "错误码 $text 已复制"
    }

    fun exportLog() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                // 写进专用子目录：FileProvider 只暴露 cache/diagnostics，
                // 不再把整个 cacheDir（含缓存的书籍原文）纳入可分享范围。
                val dir = java.io.File(context.cacheDir, "diagnostics").apply { mkdirs() }
                val file = java.io.File(dir, "diagnostics-${System.currentTimeMillis()}.log")
                file.writeText(AppLog.snapshot().ifBlank { "（暂无日志）" })
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "导出诊断日志"))
                AppLog.event("Diagnostics", "导出日志")
            }.onFailure {
                exportMsg = "导出失败：${it.message}"
                AppLog.e("Diagnostics", "导出失败：${it.message}")
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column {
                Text("运行环境", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("日志", style = MaterialTheme.typography.titleMedium)
                Text("运行中的导入、同步、备份与未捕获异常会自动记录到这里（最多保留 500 条）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLog() }) { Icon(Icons.Filled.Download, contentDescription = null); Text("导出日志", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = { AppLog.clear(); AppLog.event("Diagnostics", "已清空日志") }) { Icon(Icons.Filled.Delete, contentDescription = null); Text("清空日志", modifier = Modifier.padding(start = 6.dp)) }
                }
                if (exportMsg != null) {
                    Text(exportMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                SectionDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("级别过滤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilterChip(label = "全部", selected = selectedLevel == null) { selectedLevel = null }
                    AppLog.Level.entries.forEach { level ->
                        LogFilterChip(
                            label = logLevelLabel(level),
                            selected = selectedLevel == level,
                        ) { selectedLevel = level }
                    }
                }
                if (modules.isNotEmpty()) {
                    Text("模块过滤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogFilterChip(label = "全部模块", selected = selectedModule == null) { selectedModule = null }
                        modules.forEach { module ->
                            LogFilterChip(
                                label = module,
                                selected = selectedModule == module,
                            ) { selectedModule = module }
                        }
                    }
                }
                SectionDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (filtered.isEmpty()) {
                    Text("（无匹配日志）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filtered.reversed().forEach { entry ->
                        LogEntryItem(entry = entry, onCopyCode = { copyToClipboard(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: AppLog.Entry,
    onCopyCode: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    logLevelIcon(entry.level),
                    contentDescription = entry.level.name,
                    modifier = Modifier.size(18.dp),
                    tint = logLevelColor(entry.level),
                )
                Text(logLevelLabel(entry.level), style = MaterialTheme.typography.labelSmall, color = logLevelColor(entry.level))
                Text(entry.module, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(entry.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (entry.code != null) {
                    IconButton(onClick = { onCopyCode(entry.code) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制错误码", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            if (expanded && entry.code != null) {
                Text(
                    "错误码：${entry.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SelectablePill(
        text = label,
        selected = selected,
        onClick = onClick,
    )
}

private fun logLevelLabel(level: AppLog.Level): String = when (level) {
    AppLog.Level.INFO -> "信息"
    AppLog.Level.WARN -> "警告"
    AppLog.Level.ERROR -> "错误"
    AppLog.Level.EVENT -> "事件"
}

private fun logLevelIcon(level: AppLog.Level): androidx.compose.ui.graphics.vector.ImageVector = when (level) {
    AppLog.Level.INFO -> Icons.Filled.Info
    AppLog.Level.WARN -> Icons.Filled.Warning
    AppLog.Level.ERROR -> Icons.Filled.Error
    AppLog.Level.EVENT -> Icons.Filled.AutoAwesome
}

@Composable
private fun logLevelColor(level: AppLog.Level): androidx.compose.ui.graphics.Color = when (level) {
    AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
    AppLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
    AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
    AppLog.Level.EVENT -> MaterialTheme.colorScheme.primary
}

private fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "时间未知"
    val instant = runCatching { Instant.parse(iso) }.getOrElse { return "时间未知" }
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}

private fun formatDateTimeShort(iso: String?): String {
    if (iso.isNullOrBlank()) return "未知"
    val instant = runCatching { Instant.parse(iso) }.getOrElse { return "未知" }
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return if (hours > 0) "${hours} 小时 ${minutes} 分钟" else "${minutes} 分钟"
}

private fun formatSyncDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    if (ms < 60_000) return "${"%.1f".format(ms / 1000.0)}秒"
    return "${ms / 60_000}分${(ms % 60_000) / 1000}秒"
}

private fun isBookDownloaded(book: BookEntity): Boolean {
    if (book.content_status == "missing" || book.content_status == "failed" || book.content_status == "downloading") return false
    return !book.local_content_path.isNullOrBlank() || !book.local_uri.isNullOrBlank()
}

private fun formatBytes(size: Long): String {
    if (size <= 0) return "0 B"
    val kb = size / 1024.0
    return if (kb < 1024) "%.1f KB".format(kb) else "%.2f MB".format(kb / 1024)
}

private fun clearReaderCache(context: Context): Long {
    var freed = 0L
    fun clearDir(dir: File?): Long {
        var total = 0L
        dir?.listFiles()?.forEach {
            total += if (it.isDirectory) {
                val sub = clearDir(it)
                it.deleteRecursively()
                sub
            } else {
                val len = it.length()
                if (it.delete()) len else 0L
            }
        }
        return total
    }
    freed += clearDir(context.cacheDir)
    freed += clearDir(context.externalCacheDir)
    return freed
}

// ============================== 通用小组件 ==============================

@Composable
private fun DegradedNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
private fun RangeRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.widthIn(min = 48.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = if (step > 0f) ((max - min) / step).toInt() - 1 else 0,
            modifier = Modifier.weight(1f),
        )
        Text(valueLabel(value), modifier = Modifier.widthIn(min = 56.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            OutlinedButton(
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        title = label,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)
