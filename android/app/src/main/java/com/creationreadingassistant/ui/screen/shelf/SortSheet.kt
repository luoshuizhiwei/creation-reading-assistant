package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    // 排序下拉仅 4 个选项，属轻量菜单；去掉玻璃窗口模糊（GlassModalBottomSheet 的
    // glassWindowBlur 会在弹层进场/退场动画期间对整窗实时模糊，在 Redmi 设备上造成
    // 140ms+ 的 GPU 阻塞掉帧，详见 results/shelf-sort-performance-report.md §7）。
    // 其余视觉/行为与普通 ModalBottomSheet 完全一致。
    ModalBottomSheet(
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
                    trailing = { if (current == mode) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AppIconSize.Small)) },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}
