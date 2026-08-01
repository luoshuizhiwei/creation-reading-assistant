package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.screen.BatchSheetKind
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ===================== 批量操作栏 =====================
@Composable
internal fun BatchActionBar(
    selectedCount: Int,
    onAddToShelf: () -> Unit,
    onSetCategory: () -> Unit,
    onTag: () -> Unit,
    onDownload: () -> Unit,
    onClearCache: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val enabled = selectedCount > 0
            BatchButton("加入书单", enabled = enabled, onClick = onAddToShelf)
            BatchButton("设置分类", enabled = enabled, onClick = onSetCategory)
            BatchButton("标签", enabled = enabled, onClick = onTag)
            BatchButton("下载", enabled = enabled, onClick = onDownload)
            BatchButton("清缓存", enabled = enabled, onClick = onClearCache)
            BatchButton("删除", enabled = enabled, onClick = onDelete, danger = true)
        }
    }
}

@Composable
internal fun BatchButton(text: String, enabled: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (danger) AppError else MaterialTheme.colorScheme.onSurface)
    }
}

// ===================== 底部弹层：批量编辑 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchSheet(
    kind: BatchSheetKind,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemoveTag: (String) -> Unit = {},
    onCreate: (String) -> Unit,
    showMessage: (String) -> Unit,
    selectedCount: Int = 0,
) {
    val title = when (kind) { BatchSheetKind.SHELF -> "加入书单"; BatchSheetKind.CATEGORY -> "设置分类"; BatchSheetKind.TAG -> "管理标签" }
    val items: List<Pair<String, String>> = when (kind) {
        BatchSheetKind.SHELF -> shelves.map { it.id to it.name }
        BatchSheetKind.CATEGORY -> categories.map { it.id to it.name }
        BatchSheetKind.TAG -> tags.map { it.id to it.name }
    }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // S5：批量操作显示已选计数（对齐网页「已选 X 本」）
            if (kind == BatchSheetKind.TAG) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("已选 $selectedCount 本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (items.isEmpty() && !showCreate) {
                Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (kind == BatchSheetKind.TAG) {
                // S5：标签批量支持「添加 / 移除」
                items.forEach { (id, name) ->
                    SettingRow(
                        title = name,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onSelect(id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    enabled = selectedCount > 0,
                                ) { Text("添加") }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = { onRemoveTag(id) },
                                    enabled = selectedCount > 0,
                                ) { Text("移除", color = AppError) }
                            }
                        },
                    )
                }
            } else {
                items.forEach { (id, name) ->
                    SettingRow(
                        title = name,
                        onClick = { onSelect(id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()
            Spacer(modifier = Modifier.height(8.dp))
            if (showCreate) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("输入名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newName.isNotBlank()) { onCreate(newName.trim()); showCreate = false; newName = ""; showMessage("已创建") }
                    }) { Text("创建") }
                    TextButton(onClick = { showCreate = false; newName = "" }) { Text("取消") }
                }
            } else {
                TextButton(onClick = { showCreate = true }) { Text("+ 创建新的") }
            }
        }
    }
}
