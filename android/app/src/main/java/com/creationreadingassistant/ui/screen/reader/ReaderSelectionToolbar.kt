package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.sheets.HIGHLIGHT_COLORS
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.viewmodel.ReaderAction

internal data class SelectionToolbarActionSpec(
    val id: String,
    val label: String,
)

// 产品规划：阅读 → 记灵感 → 创作，无独立笔记概念。「记为灵感」提为一级动作，
// 原「笔记」动作从工具条移除（高亮颜色/书签/标注列表仍在「笔记与标注」弹层内保留）。
internal val selectionPrimaryActions = listOf(
    SelectionToolbarActionSpec("highlight", "高亮"),
    SelectionToolbarActionSpec("inspiration", "记为灵感"),
    SelectionToolbarActionSpec("copy", "复制"),
    SelectionToolbarActionSpec("more", "更多"),
)

internal val selectionMoreActions = listOf(
    SelectionToolbarActionSpec("ai", "AI 解读"),
    SelectionToolbarActionSpec("search", "搜索"),
    SelectionToolbarActionSpec("cancel", "取消选择"),
)

/** Snapshot the selection before clearing it so the search field never receives an empty query. */
internal fun readerSelectionSearchActions(selectedText: String): List<ReaderAction> = listOf(
    ReaderAction.SetSearchQuery(selectedText),
    ReaderAction.ClearSelection,
    ReaderAction.OpenSheet(ReaderSheet.SEARCH),
)

@Composable
internal fun SelectionToolbar(
    paper: ReaderPaperPalette,
    selectedText: String,
    showColorRow: Boolean,
    onToggleColor: () -> Unit,
    onPickColor: (String) -> Unit,
    onAiExplain: () -> Unit,
    onInspiration: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // 选中文本摘录微条
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(13.dp)
                        .clip(CircleShape)
                        .background(paper.accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            if (showColorRow) {
                Text(
                    text = "选择高亮颜色",
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    HIGHLIGHT_COLORS.forEach { colorName ->
                        val color = paper.highlightSolid(colorName)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .semantics { contentDescription = "高亮颜色 $colorName" }
                                .clickable { onPickColor(colorName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .background(color, shape = CircleShape),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onToggleColor,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.heightIn(min = 38.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = paper.accent,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("返回", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SelectionCapsuleAction(
                        icon = Icons.Outlined.BorderColor,
                        label = selectionPrimaryActions[0].label,
                        pedestalColor = Color(0xFFF9A825).copy(alpha = 0.15f),
                        iconTint = Color(0xFFF57F17),
                        onClick = onToggleColor,
                        modifier = Modifier.weight(1f),
                    )
                    SelectionCapsuleAction(
                        icon = Icons.Outlined.Lightbulb,
                        label = selectionPrimaryActions[1].label,
                        pedestalColor = Color(0xFF7E57C2).copy(alpha = 0.15f),
                        iconTint = Color(0xFF5E35B1),
                        onClick = onInspiration,
                        modifier = Modifier.weight(1f),
                    )
                    SelectionCapsuleAction(
                        icon = Icons.Outlined.ContentCopy,
                        label = selectionPrimaryActions[2].label,
                        pedestalColor = Color(0xFF1E88E5).copy(alpha = 0.15f),
                        iconTint = Color(0xFF1565C0),
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        SelectionCapsuleAction(
                            icon = Icons.Outlined.MoreHoriz,
                            label = selectionPrimaryActions[3].label,
                            pedestalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { moreExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            shadowElevation = 6.dp,
                        ) {
                            selectionMoreActions.forEach { action ->
                                val (icon, tint) = when (action.id) {
                                    "ai" -> Icons.Outlined.AutoAwesome to Color(0xFF00897B)
                                    "search" -> Icons.Outlined.Search to Color(0xFF0288D1)
                                    else -> Icons.Outlined.Close to Color(0xFFE53935)
                                }
                                DropdownMenuItem(
                                    text = { Text(action.label, style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(tint.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        moreExpanded = false
                                        when (action.id) {
                                            "ai" -> onAiExplain()
                                            "inspiration" -> onInspiration()
                                            "search" -> onSearch()
                                            "cancel" -> onClear()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionCapsuleAction(
    icon: ImageVector,
    label: String,
    pedestalColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(pedestalColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
