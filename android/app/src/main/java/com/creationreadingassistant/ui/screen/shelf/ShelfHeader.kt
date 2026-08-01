package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.screen.ShelfSortMode
import com.creationreadingassistant.ui.screen.ShelfViewMode
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ProgressBarShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi

// ===================== 顶部 Header =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelfHeader(
    selectionMode: Boolean,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onExitSearch: () -> Unit,
    selectedCount: Int,
    hasActiveImports: Boolean,
    showPageMenu: Boolean,
    onTogglePageMenu: () -> Unit,
    onImport: () -> Unit,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    importBadge: Int,
    onOpenImportHistory: () -> Unit,
    onClosePageMenu: () -> Unit,
    onOpenDesktopBooks: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                IconButton(onClick = onExitSelection) { Icon(Icons.Filled.Close, contentDescription = "退出多选") }
                Text("选择书籍", style = MaterialTheme.typography.titleLarge)
            } else if (searchActive) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜索书名、作者或文件名", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "清空搜索", modifier = Modifier.size(18.dp)) }
                }
                TextButton(onClick = onExitSearch) { Text("取消") }
            } else {
                Text(
                    "书架",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索书架", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onImport, enabled = !hasActiveImports) {
                        Icon(Icons.Filled.Add, contentDescription = "导入本地书籍", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Box {
                        IconButton(onClick = onTogglePageMenu) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "书架更多操作", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = showPageMenu, onDismissRequest = onClosePageMenu) {
                            DropdownMenuItem(
                                text = { Text("批量管理") },
                                onClick = onEnterSelection,
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("从电脑下载") },
                                onClick = onOpenDesktopBooks,
                                leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("导入历史") },
                                onClick = onOpenImportHistory,
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = if (importBadge > 0) ({ Text("$importBadge") }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================== 导入队列浮动卡片 =====================
@Composable
internal fun ImportQueueCard(
    tasks: List<ImportTaskUi>,
    batch: ImportBatchUiState,
) {
    val progress = if (batch.total > 0) {
        batch.completed.toFloat() / batch.total.toFloat()
    } else {
        0f
    }
    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    when {
                        batch.isScanning -> "正在扫描文件夹"
                        batch.total > 0 -> "正在导入 ${batch.completed}/${batch.total}"
                        else -> "正在准备导入"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (batch.total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(ProgressBarShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(ProgressBarShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            tasks.take(3).forEach { task ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(task.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tasks.size > 3) {
                Text("还有 ${tasks.size - 3} 本等待中……", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===================== 工具栏 =====================
@Composable
internal fun Toolbar(
    totalCount: Int,
    showFilterButton: Boolean,
    sortMode: ShelfSortMode,
    viewMode: ShelfViewMode,
    onOpenSort: () -> Unit,
    onOpenFilter: () -> Unit,
    onToggleView: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$totalCount 本书", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenSort) { Text(SORT_OPTIONS.first { it.first == sortMode }.second) }
        Spacer(modifier = Modifier.weight(1f))
        if (showFilterButton) {
            OutlinedButton(onClick = onOpenFilter, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("筛选")
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Row(
            modifier = Modifier
                .clip(spec.listItemShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            IconButton(onClick = { if (viewMode != ShelfViewMode.GRID) { haptic(HapticFeedbackType.TextHandleMove); onToggleView() } }) {
                Icon(Icons.Outlined.GridView, contentDescription = "网格视图", tint = if (viewMode == ShelfViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { if (viewMode != ShelfViewMode.LIST) { haptic(HapticFeedbackType.TextHandleMove); onToggleView() } }) {
                Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "列表视图", tint = if (viewMode == ShelfViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun ActiveFilterNote(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("当前已应用筛选", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onReset, contentPadding = PaddingValues(0.dp)) { Text("重置全部") }
    }
}
