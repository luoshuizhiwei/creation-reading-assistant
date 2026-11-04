package com.creationreadingassistant.ui.screen.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.creationreadingassistant.data.settings.SelectionActionGroup
import com.creationreadingassistant.data.settings.SelectionActionPreferences
import com.creationreadingassistant.feature.annotations.AnnotationActions
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.LocalAnnotationActions
import com.creationreadingassistant.feature.annotations.navigationTargetOrNull
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import com.creationreadingassistant.ui.viewmodel.ProfileLibraryState
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.ui.viewmodel.DictionaryViewModel
import com.creationreadingassistant.ui.screen.ProfileScreen
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.sync.LocalZipRestoreState

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
    /**
     * 设置搜索要聚焦的锚点（来自路由 `?focus=`）。null = 正常浏览。
     * 只在聚焦生效期间挂到 [LocalFocusedSettingsAnchor] 上，滚动完成后即消费掉。
     */
    focusAnchor: String? = null,
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
    val legadoBackups by viewModel.legadoBackups.collectAsStateWithLifecycle()
    val aiMsg by viewModel.aiMsg.collectAsStateWithLifecycle()
    val aiTesting by viewModel.aiTesting.collectAsStateWithLifecycle()
    val aiHttpWarning by viewModel.aiHttpWarning.collectAsStateWithLifecycle()
    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
    val zipStatus by viewModel.zipStatus.collectAsStateWithLifecycle()
    // N1-repair-v1：整包恢复的**持久**中断状态（来自磁盘，跨实例可见，不是一次性 Toast）。
    val zipRestoreState by viewModel.zipRestoreState.collectAsStateWithLifecycle()
    val homeSummary by viewModel.homeSummary.collectAsStateWithLifecycle()
    val goal by viewModel.goalState.collectAsStateWithLifecycle()
    val lastSyncResult by viewModel.lastSyncResult.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val syncPrefs by viewModel.syncPrefs.collectAsStateWithLifecycle()
    val aboutUpdate by viewModel.aboutUpdate.collectAsStateWithLifecycle()
    val searchIndexProgress by viewModel.searchIndexProgress.collectAsStateWithLifecycle()

    // ---- 设置状态收集 ----
    val appearance by settingsVm.appearance.collectAsStateWithLifecycle()
    val reader by settingsVm.reader.collectAsStateWithLifecycle()
    val ai by settingsVm.ai.collectAsStateWithLifecycle()
    // ---- R3-X1：选区动作配置 + 离线词库 ----
    // 只经 Reader 侧暴露的最小门面消费：Profile 拿不到 SelectionActionStore，也拿不到
    // SettingsViewModel 上与选区无关的设置面，因此无从复制默认值 / sanitize / URL 校验 /
    // 动作排序与过滤——那些裁决唯一地留在仓储里。
    val selectionPrefs: SelectionActionPreferences = settingsVm.selectionActionPreferences
    val selectionActions by selectionPrefs.settings.collectAsStateWithLifecycle()
    // 词库面板用自己的 ViewModel：它与阅读器内的词典面板共享同一个 @Singleton 仓储，
    // 因此在设置页导入/卸载后，阅读器内查词看到的是同一份词库。
    val dictionaryVm: DictionaryViewModel = hiltViewModel()
    val dictionaryState by dictionaryVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(subPage) {
        if (subPage == ProfileSubPage.SELECTION) dictionaryVm.refreshInstalled()
    }
    val dictionaryMessage = dictionaryState.message
    LaunchedEffect(dictionaryMessage) {
        dictionaryMessage?.let {
            snackbarHostState.showSnackbar(it)
            dictionaryVm.consumeMessage()
        }
    }
    val dictionaryImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { dictionaryVm.install(it) } }

    // ---- 本地 UI 状态 ----
    var confirmDialog by remember { mutableStateOf<ConfirmSpec?>(null) }
    // AI Key 用本地草稿编辑,保存时写回持久化(避免每次按键都写盘)
    var aiKeyDraft by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }

    // ---- 设置项搜索的滚动定位 ----
    // 整页列表状态由 Route 持有后下发给子页：搜索命中项在几屏之外时，
    // 光靠节点自身的 bringIntoViewRequester 够不着（那个节点还没被组合），
    // 必须有人能驱动列表滚动。控制器的实现细节见 SettingsFocusController。
    val focusController = remember(subPage) { SettingsFocusController() }
    val settingsListState = rememberLazyListState()
    val focusedAnchor = focusAnchor?.takeIf { it.isNotBlank() }
    // 由 Route 启动滚动，而不是让目标锚点自己发起：目标在屏幕外时它还没被组合，
    // 等它触发滚动就是死锁（首轮真机验证的症状：跳到了子页却停在页首）。
    LaunchedEffect(focusedAnchor, subPage) {
        focusedAnchor?.let { focusController.reveal(it) }
    }

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

    // 进入「我的」页即从磁盘读一次中断状态：新实例（重启/重进）同样能看到未完成提示。
    LaunchedEffect(Unit) { viewModel.refreshZipRestoreState(context) }

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
        syncPrefs = syncPrefs,
        aboutUpdate = aboutUpdate,
        webDavConfig = webDavConfig,
        webDavBackups = webDavBackups,
        legadoBackups = legadoBackups,
        appearance = appearance,
        reader = reader,
        selectionActions = selectionActions,
        dictionaries = dictionaryState.installed.map {
            InstalledDictionaryRow(it.baseName, it.bookName, it.wordCount)
        },
        ai = ai,
        aiHttpWarning = aiHttpWarning,
        aiKeyDraft = aiKeyDraft,
        aiTesting = aiTesting,
        libraryState = libraryState,
        goal = goal,
        pendingDownloadCount = books.count { !isBookDownloaded(it) },
        indexBytes = books.sumOf { it.size.toLong() },
        searchIndexProgress = searchIndexProgress,
    )

    // ---- Action 处理器 ----
    val handleAction: (ProfileAction) -> Unit = { action ->
        handleProfileAction(
            action = action,
            navController = navController,
            viewModel = viewModel,
            settingsVm = settingsVm,
            selectionPrefs = selectionPrefs,
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
            dictionaryVm = dictionaryVm,
            dictionaryImportLauncher = dictionaryImportLauncher,
        )
    }

    // ---- 渲染 ----
    CompositionLocalProvider(
        LocalProfileSnackbar provides snackbarHostState,
        LocalAnnotationActions provides annotationActions,
        // 聚焦锚点来自路由参数：子页的 SettingsAnchor 命中后自行高亮，
        // 滚动则由 focusController 驱动（子页把列表状态登记进来）。
        LocalFocusedSettingsAnchor provides focusedAnchor,
        LocalSettingsFocusController provides focusController,
        LocalSettingsListState provides settingsListState,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // N1-repair-v1：上次整包恢复未完成时，在页首常驻说明（不依赖 Snackbar 记忆），
            // 重试入口复用既有的「导入整包备份」动作（同一确认对话框 + SAF 选择器）。
            zipRestoreState?.takeIf { !it.isComplete }?.let { interrupted ->
                ZipRestoreInterruptedBanner(
                    state = interrupted,
                    onRetry = { handleAction(ProfileAction.ImportZip) },
                )
            }
            ProfileScreen(
                state = uiState,
                onAction = handleAction,
                modifier = Modifier.weight(1f),
            )
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
}

/**
 * 整包恢复未完成时的常驻说明条（N1-repair-v1）。
 *
 * 与 Snackbar 的区别：状态来自磁盘（[LocalZipRestoreState]），新实例照样显示；
 * 文案如实说明「上次没做完、现在处于什么阶段」，并给出**明确的重试入口**——
 * 复用既有的「导入整包备份」动作重新选择同一份备份。这里不提供任何「清理/回滚」
 * 按钮：无归属证据的文件一律保留，未完成状态只能靠重新导入同一份备份收敛。
 */
@Composable
private fun ZipRestoreInterruptedBanner(
    state: LocalZipRestoreState,
    onRetry: () -> Unit,
) {
    val stageText = when (state.stage) {
        LocalZipRestoreState.STAGE_STARTED -> "已开始，恢复未完成（数据库状态未知）"
        LocalZipRestoreState.STAGE_DB_COMMITTED -> "数据库已更新、书籍文件尚未复制完"
        LocalZipRestoreState.STAGE_UNKNOWN -> "状态不可判定（状态文件损坏）"
        else -> "阶段：${state.stage}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "上次整包恢复未完成",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$stageText。当前数据库与书籍文件可能不一致，导出已被阻止，以免把混合状态写进新备份。" +
                    state.failureSummary?.let { "\n上次失败：$it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRetry) { Text("重新选择同一备份重试") }
            }
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
    selectionPrefs: SelectionActionPreferences,
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
    dictionaryVm: DictionaryViewModel,
    dictionaryImportLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
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
        // 搜索结果直达：带上 focus 锚点，子页据此滚到那一项。
        // 锚点为空（子页兜底入口）时不带查询参数，等价于普通打开子页。
        // READING 走独立的 "my-reading" 路由，那里没有设置项锚点体系，只做普通导航。
        is ProfileAction.OpenSettingsEntry -> {
            val nav = navController ?: return
            if (action.page == ProfileSubPage.READING) {
                nav.navigate("my-reading")
            } else if (action.anchor.isBlank()) {
                nav.navigate("profile/${action.page.routeSegment}")
            } else {
                nav.navigate("profile/${action.page.routeSegment}?focus=${Uri.encode(action.anchor)}")
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
        is ProfileAction.SetSyncItem -> when (action.item) {
            SyncToggleItem.BOOKS -> viewModel.setSyncBooks(action.enabled)
            SyncToggleItem.PROGRESS -> viewModel.setSyncProgress(action.enabled)
            SyncToggleItem.INSPIRATIONS -> viewModel.setSyncInspirations(action.enabled)
        }
        is ProfileAction.SetConflictStrategy -> viewModel.setConflictStrategy(action.strategy)
        // 搜索索引两个动作走 ViewModel 的**真实分发入口**：UI 与 JVM 定向测试共用同一份
        // when 分支，不为测试复制一份转发逻辑（CRA-SEARCH-INDEX-CANCEL-CONTROL-v3）。
        ProfileAction.RebuildSearchIndex,
        ProfileAction.CancelSearchIndex,
        ->
            viewModel.handleProfileAction(action) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

        // ---- WebDAV ----
        is ProfileAction.SaveWebDav -> {
            viewModel.saveWebDav(action.url, action.user, action.pass)
            showMsg("WebDAV 配置已保存")
        }
        ProfileAction.UploadBackup -> viewModel.backupNow(context)
        is ProfileAction.TestWebDav -> viewModel.testWebDav(action.url, action.user, action.pass)
        ProfileAction.RefreshBackups -> viewModel.loadWebDavBackups()
        is ProfileAction.DownloadRestore -> onConfirmDialogChange(
            ConfirmSpec(
                title = "恢复备份",
                message = "恢复将按版本规则合并当前书籍、灵感、进度、会话、笔记、高亮、标签、分类、书单、正文规则与来源引用数据（旧版本记录不会覆盖新版本）。恢复前已自动备份数据库快照，若出问题可从快照找回。确认继续？",
                onConfirm = { viewModel.downloadRestore(context, action.filename) },
            )
        )
        ProfileAction.RefreshLegadoBackups -> viewModel.loadLegadoBackups()
        is ProfileAction.ImportLegadoBackup -> onConfirmDialogChange(
            ConfirmSpec(
                title = "导入开源阅读记录",
                message = "将只读取 ${action.filename} 内的 bookshelf.json，并只导入能以书名和作者唯一匹配到本地 EPUB 或纯文本书籍的阅读位置。不会导入书源、设置、书架分组或正文；本地已有更可靠位置的书，以及 Markdown 书，都会跳过。确认继续？",
                onConfirm = { viewModel.importLegadoBackup(context, action.filename) },
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

        // ---- Selection toolbar / dictionary (R3-X1) ----
        // 一对一委托给 Reader 的门面；槽位上下限、固定组不可隐藏、模板校验与模式白名单
        // 全部由仓储裁决，这里不复述任何规则。
        is ProfileAction.TogglePrimarySelectionAction ->
            selectionPrefs.setActionEnabled(action.id, action.enabled, SelectionActionGroup.PRIMARY)
        is ProfileAction.ToggleMoreSelectionAction ->
            selectionPrefs.setActionEnabled(action.id, action.enabled, SelectionActionGroup.MORE)
        is ProfileAction.SetBrowserUrlTemplate -> {
            selectionPrefs.setBrowserUrlTemplate(action.value)
            showMsg("浏览器查询地址已保存")
        }
        is ProfileAction.SetDictionaryUrlTemplate -> {
            selectionPrefs.setDictionaryUrlTemplate(action.value)
            showMsg("在线词典地址已保存")
        }
        is ProfileAction.SetDictionaryMode -> selectionPrefs.setDictionaryMode(action.mode)
        ProfileAction.ResetSelectionActions -> {
            selectionPrefs.resetToDefaults()
            showMsg("已恢复选区默认设置")
        }
        ProfileAction.ImportDictionary -> dictionaryImportLauncher.launch(
            arrayOf("application/zip", "application/octet-stream"),
        )
        is ProfileAction.UninstallDictionary -> dictionaryVm.uninstall(action.baseName)

        // ---- Reading goal ----
        is ProfileAction.UpdateGoalMinutes -> viewModel.setGoalMinutes(action.minutes)
        is ProfileAction.UpdateGoalReminderEnabled -> viewModel.setGoalReminderEnabled(action.enabled)
        is ProfileAction.UpdateGoalReminderMinuteOfDay -> viewModel.setGoalReminderMinuteOfDay(action.minute)

        // ---- Storage ----
        ProfileAction.Export -> exportLauncher.launch("cra-export-${System.currentTimeMillis()}.json")
        ProfileAction.Import -> onConfirmDialogChange(
            ConfirmSpec(
                title = "导入数据",
                message = "导入将按版本规则合并当前书籍、灵感、进度、会话、笔记、高亮、标签、分类、书单、正文规则与来源引用数据（旧版本记录不会覆盖新版本）。建议先导出备份。确认继续？",
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
                        showMsg("已清理阅读器临时缓存,释放 ${formatBytes(freed)}")
                    },
                )
            )
        }
        ProfileAction.ClearReaderCacheNow -> {
            // 该动作只来自「清空所有离线书籍」确认流：本消息只覆盖阅读器临时缓存，
            // 不得写成笼统的「已清理缓存」——离线正文的清空结果以上一条 ShowMessage 为准。
            val freed = clearReaderCache(context)
            showMsg("已清理阅读器临时缓存,释放 ${formatBytes(freed)}")
        }

        // ---- AI ----
        ProfileAction.TestAi -> viewModel.testAi()

        // ---- 检查更新 ----
        is ProfileAction.CheckUpdate -> viewModel.checkUpdate(action.currentVersion)

        // ---- Snackbar ----
        is ProfileAction.ShowMessage -> showMsg(action.text)
    }
}
