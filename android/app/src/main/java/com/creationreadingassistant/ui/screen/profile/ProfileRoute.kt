package com.creationreadingassistant.ui.screen.profile

import android.content.Intent
import android.net.Uri
import com.creationreadingassistant.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.feature.annotations.AnnotationActions
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.LocalAnnotationActions
import com.creationreadingassistant.feature.annotations.navigationTargetOrNull
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
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
    val aiHttpWarning by viewModel.aiHttpWarning.collectAsStateWithLifecycle()
    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
    val zipStatus by viewModel.zipStatus.collectAsStateWithLifecycle()
    val homeSummary by viewModel.homeSummary.collectAsStateWithLifecycle()
    val goal by viewModel.goalState.collectAsStateWithLifecycle()
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
    //（首页不物化整库 books/progress/sessions/notes，避免任何 DB 变化都重算整库 + 排序）
    val libraryState by produceState(initialValue = ProfileLibraryState(), key1 = subPage != null) {
        if (subPage != null) viewModel.libraryState.collect { value = it }
    }
    val books = libraryState.books

    // ---- 文件导出/导入 ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let { viewModel.export(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.import(context, it) } }
    val zipExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? -> uri?.let { viewModel.zipExport(context, it) } }
    val zipImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.zipImport(context, it) } }

    val showMsg: (String) -> Unit = { scope.launch { snackbarHostState.showSnackbar(it) } }

    // ---- ViewModel 状态消息透传为 Snackbar ----
    LaunchedEffect(pairMsg) { pairMsg?.let { showMsg(it) } }
    LaunchedEffect(syncMsg) { syncMsg?.let { showMsg(it) } }
    LaunchedEffect(webDavMsg) { webDavMsg?.let { showMsg(it) } }
    LaunchedEffect(aiMsg) { aiMsg?.let { showMsg(it) } }
    LaunchedEffect(bridgeStatus) { bridgeStatus?.let { showMsg(it) } }
    LaunchedEffect(zipStatus) { zipStatus?.let { showMsg(it) } }

    // ---- R1 统一阅读笔记：动作桥（回源导航 / 删除撤销 / 编辑 / 改色 / 导出 / 分享）----

    // SAF Markdown 导出：点击时固定导出内容，系统对话框返回 uri 后写出；用户取消不产生任何数据修改
    var pendingAnnotationExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    val annotationExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri: Uri? ->
        val pending = pendingAnnotationExport
        pendingAnnotationExport = null
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(pending.first.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("无法写入目标文件")
        }.onSuccess {
            scope.launch { snackbarHostState.showSnackbar("已导出阅读笔记") }
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("导出失败：${it.message}") }
        }
    }

    // 删除提示 Snackbar：可撤销消息带「撤销」动作，只恢复最近一次删除的那批
    val annotationMsg by viewModel.annotationMsg.collectAsStateWithLifecycle()
    LaunchedEffect(annotationMsg) {
        val msg = annotationMsg ?: return@LaunchedEffect
        if (msg.undoable) {
            val result = snackbarHostState.showSnackbar(
                msg.text,
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteAnnotationEntries()
        } else {
            snackbarHostState.showSnackbar(msg.text)
        }
    }

    val annotationActions = remember(navController) {
        object : AnnotationActions {
            override fun jumpToEntry(entry: AnnotationEntry) {
                val nav = navController ?: return
                val bookId = entry.bookId
                if (bookId == null) {
                    scope.launch { snackbarHostState.showSnackbar("该记录未关联书籍，无法跳转") }
                    return
                }
                // R1-N1.1：类型化回源 —— 传递 URL 编码后的 typed stableId
                // （highlight:{id}/note:{id}/bookmark:{id}），让 ReaderProgressEffects 按类型
                // 精确匹配，避免同 rawId 跨表跳错；无 locator 的历史记录降级为只打开书籍，不伪造偏移
                val target = entry.navigationTargetOrNull()
                if (target != null) {
                    nav.navigate("reader/$bookId?highlightId=${Uri.encode(target)}")
                } else {
                    nav.navigate("reader/$bookId")
                }
            }

            // J1-I.2：临时查阅条目来源。只做导航 —— 走 sourceLocator + navigationMode=temporary，
            // 不携带 highlightId/noteId；返回栈的推进留给 ReaderRoute 解析 navigationMode 时触发。
            override fun inspectSourceTemporarily(entry: AnnotationEntry) {
                val nav = navController ?: return
                val route = readerTemporaryRouteForSource(
                    bookId = entry.bookId,
                    legacyOffset = entry.legacyOffset,
                    chapterIndex = entry.chapterIndex,
                    charOffset = entry.charOffset,
                )
                if (route == null) {
                    scope.launch { snackbarHostState.showSnackbar("该记录没有可定位的原文位置") }
                    return
                }
                nav.navigate(route)
            }

            override fun deleteEntries(entries: List<AnnotationEntry>) =
                viewModel.deleteAnnotationEntries(entries)

            override fun editAnnotation(entry: AnnotationEntry, newAnnotation: String) =
                viewModel.editAnnotationEntry(entry, newAnnotation)

            override fun changeHighlightColor(entry: AnnotationEntry, color: String) =
                viewModel.changeAnnotationEntryColor(entry, color)

            override fun exportMarkdown(markdown: String, suggestedFileName: String) {
                pendingAnnotationExport = markdown to suggestedFileName
                annotationExportLauncher.launch(suggestedFileName)
            }

            override fun shareMarkdown(markdown: String) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "阅读笔记")
                    putExtra(Intent.EXTRA_TEXT, markdown)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "分享阅读笔记")) }
            }
        }
    }
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
        aiHttpWarning = aiHttpWarning,
        aiKeyDraft = aiKeyDraft,
        libraryState = libraryState,
        goal = goal,
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
            zipExportLauncher = zipExportLauncher,
            zipImportLauncher = zipImportLauncher,
            aiKeyDraft = aiKeyDraft,
            onAiKeyDraftChange = { aiKeyDraft = it },
            onConfirmDialogChange = { confirmDialog = it },
            showMsg = showMsg,
        )
    }

    // ---- 渲染 ----
    CompositionLocalProvider(
        LocalProfileSnackbar provides snackbarHostState,
        LocalAnnotationActions provides annotationActions,
    ) {
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
    zipExportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    zipImportLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
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
        ProfileAction.OpenInspirations -> navController?.navigate("inspiration")
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
        is ProfileAction.DownloadRestore -> onConfirmDialogChange(
            ConfirmSpec(
                title = "恢复备份",
                message = "恢复将按版本规则合并覆写当前书籍、灵感、进度与会话数据。恢复前已自动备份数据库快照，若出问题可从快照找回。确认继续？",
                onConfirm = { viewModel.downloadRestore(context, action.filename) },
            )
        )
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
            // API Key 留空且已有存值 = 不修改 Key（占位文案已承诺此语义）
            val aiSettings = settingsVm.ai.value
            settingsVm.updateAi {
                if (aiKeyDraft.isBlank() && aiSettings.apiKey.isNotBlank()) this
                else copy(apiKey = aiKeyDraft.trim())
            }
            showMsg("AI 设置已保存")
        }
        ProfileAction.ClearAiKey -> {
            onAiKeyDraftChange("")
            settingsVm.updateAi { copy(apiKey = "") }
            showMsg("已清除 API Key")
        }
        is ProfileAction.UpdateAiKeyDraft -> onAiKeyDraftChange(action.value)

        // ---- Reading goal ----
        is ProfileAction.UpdateGoalMinutes -> viewModel.setGoalMinutes(action.minutes)
        is ProfileAction.UpdateGoalReminderEnabled -> viewModel.setGoalReminderEnabled(action.enabled)
        is ProfileAction.UpdateGoalReminderMinuteOfDay -> viewModel.setGoalReminderMinuteOfDay(action.minute)

        // ---- Storage ----
        ProfileAction.Export -> exportLauncher.launch("cra-export-${System.currentTimeMillis()}.json")
        ProfileAction.Import -> onConfirmDialogChange(
            ConfirmSpec(
                title = "导入数据",
                message = "导入将按版本规则合并覆写当前书籍、灵感、进度与会话数据（旧版本记录不会覆盖新版本）。建议先导出备份。确认继续？",
                onConfirm = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        )
        ProfileAction.ExportZip -> zipExportLauncher.launch("cra-backup-${System.currentTimeMillis()}.zip")
        ProfileAction.ImportZip -> onConfirmDialogChange(
            ConfirmSpec(
                title = "导入整包备份",
                message = "导入将解包并覆盖当前书籍源文件与数据库（按版本规则合并）。ZIP 不含 AI Key / WebDAV 密码。建议先导出备份。确认继续？",
                onConfirm = { zipImportLauncher.launch(arrayOf("application/zip", "*/*")) },
            )
        )
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
