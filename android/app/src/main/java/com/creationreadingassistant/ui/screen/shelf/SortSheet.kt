package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

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
    val haptic = rememberHaptic(rememberReducedMotion())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SortByAlpha,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppIconSize.Small),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "排序方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "选择书架书籍排序规则",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 胶囊滤芯 Chip 网格布局（2x2）
            val chunkedOptions = SORT_OPTIONS.chunked(2)
            chunkedOptions.forEachIndexed { index, rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { (mode, label) ->
                        val selected = current == mode
                        val icon = sortModeIcon(mode)
                        SortCapsuleChip(
                            icon = icon,
                            label = label,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onSelect(mode)
                            },
                        )
                    }
                }
                if (index < chunkedOptions.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SortCapsuleChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
    }
    val iconBaseColor = if (selected) {
        primaryColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    val iconTint = if (selected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBaseColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(AppIconSize.Small),
                )
            }
        }
    }
}

private fun sortModeIcon(mode: ShelfSortMode): ImageVector = when (mode) {
    ShelfSortMode.RECENT -> Icons.Outlined.AccessTime
    ShelfSortMode.IMPORTED -> Icons.Outlined.CalendarToday
    ShelfSortMode.TITLE -> Icons.Outlined.SortByAlpha
    ShelfSortMode.PROGRESS -> Icons.AutoMirrored.Outlined.TrendingUp
}
