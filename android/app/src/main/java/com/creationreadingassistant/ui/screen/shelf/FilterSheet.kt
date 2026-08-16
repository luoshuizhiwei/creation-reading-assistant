package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ===================== 底部弹层：筛选 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    selectedShelfId: String,
    selectedCategoryId: String,
    selectedTagIds: Set<String>,
    formatFilter: String,
    onSelectShelf: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSelectFormat: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("筛选", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text("格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FilterChipRow(
                listOf("epub" to "EPUB", "txt" to "TXT", "md" to "Markdown"),
                formatFilter,
            ) { onSelectFormat(it) }
            if (shelves.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("书单", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(shelves.map { it.id to it.name }, selectedShelfId) { onSelectShelf(it) }
            }
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(categories.map { it.id to it.name }, selectedCategoryId) { onSelectCategory(it) }
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("标签（可多选）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                MultiFilterChipRow(tags.map { it.id to it.name }, selectedTagIds, onToggleTag)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// 多选 chips：点击切换选中态；「全部」清空整组。 */
@Composable
internal fun MultiFilterChipRow(options: List<Pair<String, String>>, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    // G 档：筛选 chips 离散选择加轻触感
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedIds.isEmpty(), onClick = { haptic(HapticFeedbackType.TextHandleMove); onToggle("") }, label = { Text("全部") })
        options.forEach { (id, name) ->
            FilterChip(selected = id in selectedIds, onClick = { haptic(HapticFeedbackType.TextHandleMove); onToggle(id) }, label = { Text(name) })
        }
    }
}

@Composable
internal fun FilterChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    // G 档：筛选 chips 离散选择加轻触感
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected.isEmpty(), onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect("") }, label = { Text("全部") })
        options.forEach { (id, name) ->
            FilterChip(selected = selected == id, onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect(id) }, label = { Text(name) })
        }
    }
}
