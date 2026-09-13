package com.creationreadingassistant.ui.screen.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicy
import com.creationreadingassistant.ui.viewmodel.LibraryBrowserViewModel
import com.creationreadingassistant.ui.viewmodel.RecognitionFilter

private enum class LibraryMode { BROWSER, RECOGNIZE }

/**
 * 我的书籍目录：授权根目录、App 内浏览与智能识别入口。
 *
 * 本页只读取来源目录。真正写书架的只有 [onImport] 交出去的 URI 列表 —— 由共享的
 * `ShelfViewModel` 调 `ShelfImporter` 执行，因此本页不持有任何导入状态，也不复制正文。
 */
@Composable
internal fun LibraryBrowserRoute(
    navController: NavHostController,
    viewModel: LibraryBrowserViewModel,
    onImport: (uris: List<Uri>, sourceLabel: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recognition by viewModel.recognition.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(LibraryMode.BROWSER) }
    var filter by remember { mutableStateOf(RecognitionFilter.RECOMMENDED) }
    var confirmClearRoot by remember { mutableStateOf(false) }

    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::saveRoot)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    fun submitBrowserSelection() {
        val uris = viewModel.selectedUris()
        if (uris.isEmpty()) {
            viewModel.reportEmptySelection()
            return
        }
        viewModel.clearSelection()
        viewModel.reportImportStarted()
        onImport(uris, "书籍目录")
        navController.popBackStack()
    }

    AppScreenScaffold(
        title = "我的书籍目录",
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = {
            if (state.hasRoot) {
                IconButton(onClick = viewModel::reload) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { viewport ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(viewport)) {
            val adaptive = adaptivePageMetrics(maxWidth, LocalLayoutTokens.current)
            val reservedBottom = if (mode == LibraryMode.BROWSER && state.inSelectionMode) 168.dp else 0.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = adaptive.contentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = LocalLayoutTokens.current.pageVertical,
                    bottom = LocalLayoutTokens.current.pageVertical + 24.dp + reservedBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
            ) {
                if (!state.hasRoot) {
                    item(key = "library_setup") {
                        LibraryRootSetupCard(
                            unreadable = false,
                            onPickRoot = { rootPicker.launch(null) },
                            onDismiss = { navController.popBackStack() },
                        )
                    }
                    return@LazyColumn
                }

                if (state.unreadable) {
                    item(key = "library_unreadable") {
                        LibraryRootSetupCard(
                            unreadable = true,
                            onPickRoot = { rootPicker.launch(null) },
                            onDismiss = { navController.popBackStack() },
                        )
                    }
                }

                item(key = "library_root") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryRootSummaryCard(
                            rootName = state.rootName,
                            onReload = viewModel::reload,
                            onChangeRoot = { rootPicker.launch(null) },
                            onClearRoot = { confirmClearRoot = true },
                        )
                        LibraryBreadcrumb(
                            crumbs = state.crumbs,
                            onCrumbClick = viewModel::navigateToCrumb,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectablePill(
                                text = "当前目录",
                                selected = mode == LibraryMode.BROWSER,
                                onClick = { mode = LibraryMode.BROWSER },
                            )
                            SelectablePill(
                                text = "智能识别",
                                selected = mode == LibraryMode.RECOGNIZE,
                                onClick = {
                                    mode = LibraryMode.RECOGNIZE
                                    if (!recognition.isRunning && !recognition.finished) viewModel.startRecognition()
                                },
                            )
                        }
                    }
                }

                if (mode == LibraryMode.BROWSER) {
                    item(key = "library_search") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                placeholder = { Text("搜索文件名…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LibrarySortRow(
                                sortMode = state.sortMode,
                                onSortModeChange = viewModel::setSortMode,
                            )
                            if (state.truncated) {
                                LibraryInfoBanner("目录较大，仅显示前 1000 项；可缩小目录范围后重试。")
                            }
                        }
                    }

                    if (state.isLoading) {
                        item(key = "library_loading") {
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "正在读取目录…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (state.isEmpty && !state.isLoading && !state.unreadable) {
                        item(key = "library_empty") {
                            EmptyLibraryCard()
                        }
                    }

                    items(state.directories, key = { "dir-${it.documentId}" }) { entry ->
                        LibraryDirectoryRow(entry = entry) {
                            viewModel.openDirectory(entry.documentId, entry.displayName)
                        }
                    }

                    items(state.files, key = { "file-${it.entry.documentId}" }) { row ->
                        LibraryFileItem(
                            row = row,
                            selected = row.entry.documentId in state.selection,
                            inSelectionMode = state.inSelectionMode,
                            onToggle = { viewModel.toggleFile(row.entry.documentId) },
                            onLongPress = { viewModel.toggleFile(row.entry.documentId) },
                            onOpen = {
                                row.openableBookId?.let { bookId ->
                                    navController.navigate("reader/$bookId")
                                }
                            },
                        )
                    }
                } else {
                    item(key = "library_recognition_panel") {
                        LibraryRecognitionPanel(
                            state = recognition,
                            filter = filter,
                            onFilterChange = { filter = it },
                            onStart = viewModel::startRecognition,
                            onStop = viewModel::stopRecognition,
                            onToggleCandidate = viewModel::toggleRecognitionCandidate,
                            onSelectRecommended = viewModel::selectRecommended,
                            onImport = {
                                val uris = viewModel.selectedRecognitionUris()
                                if (uris.isEmpty()) {
                                    viewModel.reportEmptySelection()
                                } else {
                                    viewModel.reportImportStarted()
                                    onImport(uris, "智能识别")
                                    navController.popBackStack()
                                }
                            },
                        )
                    }

                    val rows = recognition.rows.filter {
                        LibraryBrowserPolicy.matchesFilter(it.candidate.decision, it.shelfMatch, filter)
                    }
                    if (recognition.finished && rows.isEmpty()) {
                        item(key = "library_recognition_empty") {
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = when (filter) {
                                        RecognitionFilter.RECOMMENDED -> "没有新的推荐文件，可切换到「全部」查看识别结果。"
                                        RecognitionFilter.IN_SHELF -> "没有已入架或疑似入架的文件。"
                                        RecognitionFilter.REJECTED -> "没有未识别的文件。"
                                        RecognitionFilter.ALL -> "本次扫描没有发现候选文件。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(rows, key = { "rec-${it.key}" }) { row ->
                        LibraryRecognitionItem(
                            row = row,
                            selected = row.key in recognition.selected,
                            onToggle = { viewModel.toggleRecognitionCandidate(row.key) },
                        )
                    }
                }
            }

            if (mode == LibraryMode.BROWSER && state.inSelectionMode) {
                LibrarySelectionBar(
                    selectedCount = state.selection.size,
                    allSelected = state.allFilesSelected,
                    onSelectAllToggle = {
                        if (state.allFilesSelected) viewModel.clearSelection() else viewModel.selectAllVisible()
                    },
                    onInvert = viewModel::invertSelection,
                    onCancel = viewModel::clearSelection,
                    onImport = { submitBrowserSelection() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (confirmClearRoot) {
        GlassAlertDialog(
            onDismissRequest = { confirmClearRoot = false },
            title = { Text("移除书籍目录配置？") },
            text = {
                Text("只会清除 App 内保存的目录授权与浏览位置，不会删除来源目录中的任何文件，书架内已导入书籍也不受影响。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRoot()
                    confirmClearRoot = false
                }) {
                    Text("移除", color = AppError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRoot = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyLibraryCard() {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LineArtBook(modifier = Modifier.size(48.dp))
            Text(
                text = "这个目录里没有可阅读文件",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "支持 EPUB、TXT 与 Markdown。可以进入子文件夹，或更换为其他书籍目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
