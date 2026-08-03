package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===================== 底部弹层：从电脑下载 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBooksSheet(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    listBooks: suspend () -> List<SyncContract.BookFileManifest>,
) {
    var books by remember { mutableStateOf<List<SyncContract.BookFileManifest>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { loading = true; books = listBooks(); loading = false } }
    LaunchedEffect(Unit) { load() }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("从电脑导入", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { load() }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else if (books.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("暂无可下载书籍", style = MaterialTheme.typography.bodyMedium)
                    Text("请确认电脑端已开启同步服务并有可下载的书籍正文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                books.forEach { book ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onDownload(book.bookId) }.padding(vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(book.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${book.format.uppercase()} · ${formatBytes(book.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ===================== 导入来源 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSourceSheet(
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("添加到书架", style = MaterialTheme.typography.titleMedium)
            Text(
                "可以一次选择多本书，也可以扫描一个文件夹。只会读取 EPUB、TXT 和 Markdown。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            ImportSourceRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "选择书籍",
                description = "一次选择一本或多本文件",
                onClick = onSelectFiles,
            )
            SectionDivider()
            ImportSourceRow(
                icon = Icons.Filled.Folder,
                title = "扫描文件夹",
                description = "包含子文件夹，自动跳过其他文件",
                onClick = onSelectFolder,
            )
        }
    }
}

@Composable
internal fun ImportSourceRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===================== 底部弹层：导入历史 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportHistorySheet(
    tasks: List<ImportTaskUi>,
    history: List<ImportHistoryEntry>,
    batch: ImportBatchUiState,
    onRetryFailed: () -> Unit,
    onDismissBatch: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // G 档：清空历史是结果性动作，加确认触感
    val haptic = rememberHaptic(rememberReducedMotion())
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text("导入历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    IconButton(onClick = { haptic(HapticFeedbackType.LongPress); onClear() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空历史", modifier = Modifier.size(16.dp), tint = AppError)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (batch.hasResult) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (batch.isRunning) "正在处理 ${batch.sourceLabel}" else "本次导入结果",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            buildList {
                                add("成功 ${batch.succeeded}")
                                if (batch.duplicates > 0) add("重复 ${batch.duplicates}")
                                if (batch.skipped > 0) add("跳过 ${batch.skipped}")
                                if (batch.failed > 0) add("失败 ${batch.failed}")
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (batch.truncated || batch.unreadableFolders > 0) {
                            Text(
                                buildList {
                                    if (batch.truncated) add("文件较多，已按安全上限停止扫描")
                                    if (batch.unreadableFolders > 0) add("${batch.unreadableFolders} 个子文件夹无法读取")
                                }.joinToString("；"),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppWarning,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!batch.isRunning) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (batch.failures.isNotEmpty()) {
                                    TextButton(onClick = onRetryFailed) { Text("重试失败项") }
                                }
                                TextButton(onClick = onDismissBatch) { Text("知道了") }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (tasks.isEmpty() && history.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    LineArtBook(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("还没有导入记录", style = MaterialTheme.typography.bodyMedium)
                    Text("导入书籍后，这里会显示每次导入的结果、编码识别和失败原因。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (tasks.isNotEmpty()) {
                    SectionTitle("本次导入队列（${tasks.size}）")
                    tasks.forEach { task ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val taskColor = when (task.status) {
                                "error" -> AppError
                                "duplicate", "skipped" -> AppWarning
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                when (task.status) {
                                    "processing" -> Icons.Filled.Refresh
                                    "error", "skipped" -> Icons.Filled.Warning
                                    else -> Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = taskColor,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(task.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(task.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (history.isNotEmpty()) {
                    val successCount = history.count { it.status == "success" }
                    val failedCount = history.count { it.status == "failed" }
                    val duplicateCount = history.count { it.isDuplicate || it.status == "duplicate" }
                    val skippedCount = history.count { it.status == "skipped" }
                    SectionTitle(
                        buildList {
                            add("历史记录（${history.size}）")
                            add("成功 $successCount")
                            if (duplicateCount > 0) add("重复 $duplicateCount")
                            if (skippedCount > 0) add("跳过 $skippedCount")
                            if (failedCount > 0) add("失败 $failedCount")
                        }.joinToString(" · ")
                    )
                    history.forEach { entry ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (entry.status == "success") Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = when (entry.status) {
                                    "success" -> AppSuccess
                                    "duplicate", "skipped" -> AppWarning
                                    else -> AppError
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val meta = buildList {
                                    if (entry.fileSize > 0) add(formatBytes(entry.fileSize))
                                    if (entry.format.isNotBlank()) add(entry.format.uppercase())
                                    if (!entry.encoding.isNullOrBlank()) add(entry.encoding)
                                    if (entry.bookTitle != null) add("《${entry.bookTitle}》")
                                    if (entry.error != null) add(entry.error)
                                }.joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
