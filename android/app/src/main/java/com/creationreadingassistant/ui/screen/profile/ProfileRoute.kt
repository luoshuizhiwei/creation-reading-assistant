package com.creationreadingassistant.ui.screen.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.viewmodel.ProfileLibraryState
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.ui.screen.ProfileScreen
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

/**
 * 通过 CompositionLocal 从 Route 层把 SnackbarHostState 桥给 Pure Screen 层。
 * Pure Screen 默认 LocalProfileSnackbar = null,因此 snackbarHost 是一个空占位,
 * 满足 "Pure Screen 不持有 SnackbarHostState"。
 */
internal val LocalProfileSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * Profile 页 **View 层(Route)**——拥有所有副作用、协程、导航、ViewModel、Snackbar、确认对话框。
 *
 * ## 本层消费的内容(纯 Screen 层禁止出现)
 * - `hiltViewModel<ProfileViewModel>()`、`hiltViewModel<SettingsViewModel>()`、`hiltViewModel<TaxonomyViewModel>()`
 * - `navController.navigate(...)`
 * - `collectAsStateWithLifecycle()`
 * - `SnackbarHostState` + `scope.launch { showSnackbar(...) }`
 * - `rememberLauncherForActivityResult(...)`
 * - `BackHandler(...)`
 * - `produceState(...)`
 * - 确认对话框状态管理
 *
 * ## 数据流
 * ViewModel.StateFlow → 本 Route 合并纯 UI 状态(subPage 来自导航参数、confirmDialog 与 aiKeyDraft 为本地状态)
 *                     → com.creationreadingassistant.ui.screen.profile.ProfileUiState
 *                     → ProfileScreen(uiState, onAction)(纯渲染)
 *
 * ## 交互流
 * ProfileScreen 点击 → ProfileAction sealed → 本 Route 的 handleProfileAction(...) →
 *  Snackbar / navController.navigate / ViewModel 调用 / 确认对话框。
 */
@Composable
internal fun ProfileRoute(
    navController: NavHostController? = null,
    subPage: ProfileSubPage? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
    taxonomyVm: TaxonomyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- ViewModel 状态收集 ----
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

    // ---- 设置状态收集 ----
    val appearance by settingsVm.appearance.collectAsStateWithLifecycle()
    val reader by settingsVm.reader.collectAsStateWithLifecycle()
    val ai by settingsVm.ai.collectAsStateWithLifecycle()

    // ---- 本地 UI 状态 ----
    var confirmDialog by remember { mutableStateOf<ConfirmSpec?>(null) }
    // AI Key 用本地草稿编辑,保存时写回持久化(避免每次按键都写盘)
    var aiKeyDraft by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }

    // ---- 子页专用的完整档案数据:仅在进入某个子页后(currentSubPage != null)才订阅 ----
    val libraryState by produceState(initialValue = ProfileLibraryState()) {
        viewModel.libraryState.collect { value = it }
    }
    val books = libraryState.books

    // ---- 文件导出/导入 ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let { viewModel.export(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.import(context, it) } }

    val showMsg: (String) -> Unit = { scope.launch { snackbarHostState.showSnackbar(it) } }

    // ---- ViewModel 状态消息透传为 Snackbar ----
    LaunchedEffect(pairMsg) { pairMsg?.let { showMsg(it) } }
    LaunchedEffect(syncMsg) { syncMsg?.let { showMsg(it) } }
    LaunchedEffect(webDavMsg) { webDavMsg?.let { showMsg(it) } }
    LaunchedEffect(aiMsg) { aiMsg?.let { showMsg(it) } }
    LaunchedEffect(bridgeStatus) { bridgeStatus?.let { showMsg(it) } }

    // ---- 构建 ProfileUiState ----
    val webDavConfigured = !webDavConfig?.url.isNullOrBlank()
    val paired = config != null
    val uiState = ProfileUiState(
        currentSubPage = subPage,
        paired = paired,
        webDavConfigured = webDavConfigured,
        aiConfigured = ai.enabled && ai.apiKey.isNotBlank(),
        appThemeLabel = themeLabel(appearance.themeMode),
        homeSummary = homeSummary,
        pairing = pairing,
        syncing = syncing,
        baseUrl = config?.baseUrl,
        lastSyncResult = lastSyncResult,
        syncLogs = syncLogs,
        webDavConfig = webDavConfig,
        webDavBackups = webDavBackups,
        appearance = appearance,
        reader = reader,
        ai = ai,
        aiKeyDraft = aiKeyDraft,
        libraryState = libraryState,
        pendingDownloadCount = books.count { !isBookDownloaded(it) },
        indexBytes = books.sumOf { it.size.toLong() },
    )

    // ---- Action 处理器 ----
    val handleAction: (ProfileAction) -> Unit = { action ->
        handleProfileAction(
            action = action,
            navController = navController,
            viewModel = viewModel,
            settingsVm = settingsVm,
            context = context,
            scope = scope,
            snackbarHostState = snackbarHostState,
            exportLauncher = exportLauncher,
            importLauncher = importLauncher,
            aiKeyDraft = aiKeyDraft,
            onAiKeyDraftChange = { aiKeyDraft = it },
            onConfirmDialogChange = { confirmDialog = it },
            showMsg = showMsg,
        )
    }

    // ---- 渲染 ----
    CompositionLocalProvider(LocalProfileSnackbar provides snackbarHostState) {
        ProfileScreen(
            state = uiState,
            onAction = handleAction,
        )

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
}

/**
 * ProfileAction → 副作用分发器。所有导航/Snackbar/ViewModel 调用都走这里。
 */
private fun handleProfileAction(
    action: ProfileAction,
    navController: NavHostController?,
    viewModel: ProfileViewModel,
    settingsVm: SettingsViewModel,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    aiKeyDraft: String,
    onAiKeyDraftChange: (String) -> Unit,
    onConfirmDialogChange: (ConfirmSpec?) -> Unit,
    showMsg: (String) -> Unit,
) {
    when (action) {
        // ---- Navigation ----
        is ProfileAction.OpenSubPage -> {
            if (action.page == ProfileSubPage.READING) {
                navController?.navigate("my-reading")
            } else {
                navController?.navigate("profile/${action.page.routeSegment}")
            }
        }
        is ProfileAction.OpenBook -> navController?.navigate("reader/${action.bookId}")
        ProfileAction.GoBack -> navController?.popBackStack()
        ProfileAction.ScanQr -> navController?.navigate("pairing")

        // ---- Sync ----
        is ProfileAction.StartPairing -> viewModel.startPairing(action.rawQr)
        ProfileAction.SyncNow -> viewModel.syncNow()
        ProfileAction.Unpair -> viewModel.unpair()
        is ProfileAction.RetryItem -> viewModel.retryItem(action.item)
        ProfileAction.RetryFailed -> viewModel.retryFailed()

        // ---- WebDAV ----
        is ProfileAction.SaveWebDav -> {
            viewModel.saveWebDav(action.url, action.user, action.pass)
            showMsg("WebDAV 配置已保存")
        }
        ProfileAction.UploadBackup -> viewModel.backupNow(context)
        ProfileAction.TestWebDav -> viewModel.testWebDav()
        ProfileAction.RefreshBackups -> viewModel.loadWebDavBackups()
        is ProfileAction.DownloadRestore -> viewModel.downloadRestore(context, action.filename)
        ProfileAction.ClearWebDav -> {
            viewModel.clearWebDav()
            showMsg("WebDAV 配置已清除")
        }

        // ---- Settings ----
        is ProfileAction.UpdateAppearance -> settingsVm.updateAppearance(action.block)
        is ProfileAction.UpdateReader -> settingsVm.updateReader(action.block)
        ProfileAction.ResetReader -> settingsVm.updateReader { ReaderSettings() }
        is ProfileAction.UpdateAi -> settingsVm.updateAi(action.block)
        ProfileAction.SaveAiKey -> {
            settingsVm.updateAi { copy(apiKey = aiKeyDraft) }
            showMsg("AI 设置已保存")
        }
        ProfileAction.ClearAiKey -> {
            onAiKeyDraftChange("")
            settingsVm.updateAi { copy(apiKey = "") }
            showMsg("已清除 API Key")
        }
        is ProfileAction.UpdateAiKeyDraft -> onAiKeyDraftChange(action.value)

        // ---- Storage ----
        ProfileAction.Export -> exportLauncher.launch("cra-export-${System.currentTimeMillis()}.json")
        ProfileAction.Import -> importLauncher.launch(arrayOf("application/json", "*/*"))
        ProfileAction.ClearReaderCache -> {
            onConfirmDialogChange(
                ConfirmSpec(
                    title = "清理缓存",
                    message = "确认清理阅读器正文缓存?将删除本地缓存条目(含 EPUB 解压缓存、图片缓存),释放后可重新生成。不会删除正式书籍、进度、书签和笔记。",
                    onConfirm = {
                        val freed = clearReaderCache(context)
                        showMsg("已清理缓存,释放 ${formatBytes(freed)}")
                    },
                )
            )
        }

        // ---- AI ----
        ProfileAction.TestAi -> viewModel.testAi()

        // ---- Snackbar ----
        is ProfileAction.ShowMessage -> showMsg(action.text)
    }
}
