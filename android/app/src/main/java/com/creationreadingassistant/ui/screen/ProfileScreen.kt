package com.creationreadingassistant.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.AppTopBar
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.profile.AboutSubPage
import com.creationreadingassistant.ui.screen.profile.AiSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.AppearanceSubPage
import com.creationreadingassistant.ui.screen.profile.DiagnosticsSubPage
import com.creationreadingassistant.ui.screen.profile.LibrarySubPage
import com.creationreadingassistant.ui.screen.profile.PrivacySubPage
import com.creationreadingassistant.ui.screen.profile.ProfileHomeContent
import com.creationreadingassistant.ui.screen.profile.ReadingNotesSubPage
import com.creationreadingassistant.ui.screen.profile.ReaderSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.StorageSubPage
import com.creationreadingassistant.ui.screen.profile.SyncSubPage
import com.creationreadingassistant.ui.screen.profile.WebDavSubPage
import com.creationreadingassistant.ui.screen.profile.clearReaderCache
import com.creationreadingassistant.ui.screen.profile.formatBytes
import com.creationreadingassistant.ui.screen.profile.isBookDownloaded
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ProfileLibraryState
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// 与网页版 mobile-updates.ts 保持一致的发布链接
internal const val MOBILE_RELEASES_URL = "https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases"
internal const val MOBILE_RELEASE_API_URL = "https://api.github.com/repos/luoshuizhiwei/creation-reading-assistant-releases/releases/latest"

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
 * 所有子页通过内部 currentSubPage 切换，并向应用壳报告全屏子页状态以隐藏顶层底栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    navController: NavHostController? = null,
    onSubPageVisibilityChanged: (Boolean) -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    val homeSummary by viewModel.homeSummary.collectAsStateWithLifecycle()
    val lastSyncResult by viewModel.lastSyncResult.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

    var currentSubPage by remember { mutableStateOf<ProfileSubPage?>(null) }
    LaunchedEffect(currentSubPage) {
        onSubPageVisibilityChanged(currentSubPage != null)
    }
    BackHandler(enabled = currentSubPage != null) {
        currentSubPage = null
    }
    // 子页专用的完整档案数据：仅在进入某个子页后（currentSubPage != null）才订阅，
    // 首页不物化任何书籍 / 进度 / 会话 / 笔记实体，也不触发子页查询。
    val libraryState by produceState(initialValue = ProfileLibraryState(), currentSubPage) {
        if (currentSubPage == null) return@produceState
        viewModel.libraryState.collect { value = it }
    }
    val books = libraryState.books
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
    val reducedMotion = rememberReducedMotion()

    Scaffold(
        topBar = {
            if (currentSubPage == null) {
                AppTopBar(
                    title = "我的",
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
                AppTopBar(
                    title = subPageTitle(currentSubPage),
                    compact = true,
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
            .animateEnter(reducedMotion = reducedMotion)

        when (val page = currentSubPage) {
            null -> ProfileHomeContent(
                modifier = modifier,
                paired = paired,
                webDavConfigured = webDavConfigured,
                aiConfigured = ai.enabled && ai.apiKey.isNotBlank(),
                appThemeLabel = themeLabel(appearance.themeMode),
                totalDurationMs = homeSummary.totalDurationMs,
                completedBookCount = homeSummary.completedBookCount,
                inspirationCount = homeSummary.inspirationCount,
                onNavigate = { currentSubPage = it },
                reducedMotion = reducedMotion,
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
                settings = reader,
                onSettingsChange = { updated -> settingsVm.updateReader { updated } },
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

internal enum class ProfileSubPage {
    SYNC, WEBDAV, APPEARANCE, READER, AI,
    TAGS, CATEGORIES, SHELVES, READING, NOTES,
    STORAGE, PRIVACY, ABOUT, DIAGNOSTICS
}

internal data class ConfirmSpec(val title: String, val message: String, val onConfirm: () -> Unit)

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
