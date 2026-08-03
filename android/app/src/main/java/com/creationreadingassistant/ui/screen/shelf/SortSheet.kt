package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.LocalComponentSpec

/** 书架排序选项（label 对应 UI 文案）。 */
internal val SORT_OPTIONS = listOf(
    ShelfSortMode.RECENT to "最近阅读",
    ShelfSortMode.IMPORTED to "导入时间",
    ShelfSortMode.TITLE to "书名",
    ShelfSortMode.PROGRESS to "进度",
)

// ===================== 底部弹层：排序 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortSheet(current: ShelfSortMode, onSelect: (ShelfSortMode) -> Unit, onDismiss: () -> Unit) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("排序方式", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            SORT_OPTIONS.forEach { (mode, label) ->
                SettingRow(
                    title = label,
                    trailing = { if (current == mode) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}
