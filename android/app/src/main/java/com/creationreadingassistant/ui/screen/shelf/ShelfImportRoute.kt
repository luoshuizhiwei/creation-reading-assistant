package com.creationreadingassistant.ui.screen.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.launch

@Composable
internal fun ShelfImportRoute(
    navController: NavHostController,
    viewModel: ShelfViewModel,
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val batch = source.auxiliary.importBatch
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDesktopBooks by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importFolder)
    }
    AppScreenScaffold(
        title = "导入书籍",
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = {
            if (source.auxiliary.importHistory.isNotEmpty()) {
                TextButton(onClick = { confirmClearHistory = true }) { Text("清空") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { viewport ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(viewport)) {
            val adaptive = adaptivePageMetrics(maxWidth, LocalLayoutTokens.current)
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = adaptive.contentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    horizontal = adaptive.horizontalPadding,
                    vertical = LocalLayoutTokens.current.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
            ) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    ImportSourceRow(
                        icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                        title = "选择文件",
                        description = "一次选择一本或多本 EPUB、TXT、Markdown",
                        enabled = !batch.isRunning,
                    ) {
                        filePicker.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
                    }
                    OrganizerDivider()
                    ImportSourceRow(
                        icon = Icons.Outlined.FolderOpen,
                        title = "选择文件夹",
                        description = "扫描文件夹及其子文件夹中的支持格式",
                        enabled = !batch.isRunning,
                    ) {
                        folderPicker.launch(null)
                    }
                    OrganizerDivider()
                    ImportSourceRow(
                        icon = Icons.Outlined.Computer,
                        title = "从电脑导入",
                        description = "下载电脑端已同步的书籍正文",
                        enabled = !batch.isRunning,
                    ) {
                        showDesktopBooks = true
                    }
                }
            }
            if (batch.isRunning || batch.hasResult) {
                item { ImportProgressCard(batch, viewModel::requestStopImport, viewModel::retryFailedImports) }
            }
            if (source.auxiliary.importTasks.isNotEmpty()) {
                item { Text("当前队列", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
                items(source.auxiliary.importTasks, key = { it.id }) { task ->
                    ImportLine(task.fileName, task.phase, task.status)
                }
            }
            item { Text("导入记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
            if (source.auxiliary.importHistory.isEmpty()) {
                item { Text("还没有导入记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)) }
            } else {
                items(source.auxiliary.importHistory, key = { it.id }) { entry ->
                    ImportLine(
                        title = entry.fileName,
                        subtitle = entry.error ?: buildList {
                            if (entry.format.isNotBlank()) add(entry.format.uppercase())
                            entry.encoding?.takeIf(String::isNotBlank)?.let(::add)
                            if (entry.isDuplicate) add("重复文件")
                        }.joinToString(" · ").ifBlank { "已导入" },
                        status = entry.status,
                    )
                }
            }
            item {
                Text(
                    "返回或切换页面不会主动停止导入，请保持应用运行。停止时会先完成当前文件，已成功导入的书籍不会回滚。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            }
        }
    }
    if (showDesktopBooks) {
        DesktopBooksSheet(
            onDismiss = { showDesktopBooks = false },
            onDownload = { bookId ->
                viewModel.downloadBookContent(bookId) { message ->
                    scope.launch { snackbar.showSnackbar(message) }
                }
            },
            listBooks = viewModel::listDesktopBooks,
        )
    }
    if (confirmClearHistory) {
        GlassAlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清空导入记录？") },
            text = { Text("只会清除导入记录，不会删除已经导入的书籍。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImportHistory()
                    confirmClearHistory = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ImportProgressCard(batch: ImportBatchUiState, onStop: () -> Unit, onRetry: () -> Unit) {
    val progress = if (batch.total > 0) batch.completed.toFloat() / batch.total else 0f
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (batch.isRunning) CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (batch.failed > 0) AppWarning else AppSuccess)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (batch.isRunning) "正在导入 ${batch.sourceLabel}" else if (batch.stopped > 0) "导入已停止" else "本次导入完成", style = MaterialTheme.typography.titleSmall)
                    Text(
                        buildList {
                            add("成功 ${batch.succeeded}")
                            if (batch.duplicates > 0) add("重复 ${batch.duplicates}")
                            if (batch.skipped > 0) add("跳过 ${batch.skipped}")
                            if (batch.failed > 0) add("失败 ${batch.failed}")
                            if (batch.stopped > 0) add("未处理 ${batch.stopped}")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                batch.isRunning -> OutlinedButton(onClick = onStop, enabled = !batch.stopRequested, modifier = Modifier.fillMaxWidth()) {
                    Text(if (batch.stopRequested) "将在当前文件完成后停止" else "停止后续导入")
                }
                batch.failures.isNotEmpty() -> Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试失败项") }
            }
        }
    }
}

@Composable
private fun ImportLine(title: String, subtitle: String, status: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (status == "success" || status == "done") Icons.Outlined.CheckCircle else if (status == "processing") Icons.AutoMirrored.Outlined.InsertDriveFile else Icons.Outlined.Warning,
            contentDescription = null,
            tint = when (status) {
                "success", "done" -> AppSuccess
                "processing" -> MaterialTheme.colorScheme.primary
                else -> AppError
            },
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
