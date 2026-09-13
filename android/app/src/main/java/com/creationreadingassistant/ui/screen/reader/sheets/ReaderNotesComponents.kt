package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 章节与类别分组标题：带 20dp 独立微图标底座与数量微胶囊。
 */
@Composable
internal fun NotesSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
            )
        }
    }
}

/**
 * 操作微胶囊按钮（跳转、笔记、转笔记、转灵感等）。
 */
@Composable
internal fun NotesActionPill(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.bounceable(interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 高亮书摘微岛卡片。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HighlightItemCard(
    paper: ReaderPaperPalette,
    highlight: HighlightEntity,
    index: Int,
    reducedMotion: Boolean,
    onDelete: () -> Unit,
    onChangeColor: (String) -> Unit,
    onJump: () -> Unit,
    onEditNote: () -> Unit,
    onConvertToNote: () -> Unit,
    onConvertToInspiration: () -> Unit,
) {
    val haptic = rememberHaptic(reducedMotion)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .listItemEnter(index, reducedMotion),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // 顶部：20dp 双层高光同心圆 + 摘录正文 + 删除微操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                val solidColor = paper.highlightSolid(highlight.color ?: "yellow")
                // 左侧 20dp 双层高光同心圆
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                        .background(solidColor.copy(alpha = 0.22f), CircleShape)
                        .border(1.dp, solidColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(solidColor, CircleShape),
                    )
                }

                // 摘录正文排版典雅
                Text(
                    text = highlight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )

                // 删除按钮
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // 笔记气泡：纸墨微底座
            highlight.note?.takeIf { it.isNotBlank() }?.let { noteContent ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 28.dp, end = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(top = 1.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            noteContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 颜色切换圆点升级为发丝描边与双层光环的微胶囊网格
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIGHLIGHT_COLORS.forEach { c ->
                    val isSelected = highlight.color == c
                    val cColor = paper.highlightSolid(c)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onChangeColor(c)
                            }
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(cColor.copy(alpha = 0.25f), CircleShape)
                                        .border(1.5.dp, cColor, CircleShape)
                                } else {
                                    Modifier.border(
                                        0.6.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        CircleShape,
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 12.dp)
                                .background(cColor, CircleShape),
                        )
                    }
                }
            }

            // 操作按钮行微胶囊化
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NotesActionPill(
                    text = "跳转",
                    icon = Icons.Outlined.NearMe,
                    onClick = onJump,
                )
                NotesActionPill(
                    text = if (highlight.note.isNullOrBlank()) "加笔记" else "编辑笔记",
                    icon = Icons.Outlined.EditNote,
                    onClick = onEditNote,
                )
                NotesActionPill(
                    text = "转笔记",
                    icon = Icons.Outlined.FormatQuote,
                    onClick = onConvertToNote,
                )
                NotesActionPill(
                    text = "转灵感",
                    icon = Icons.Outlined.Lightbulb,
                    onClick = onConvertToInspiration,
                )
            }
        }
    }
}

/**
 * 笔记与书签微岛卡片。
 */
@Composable
internal fun NoteItemCard(
    note: NoteEntity,
    index: Int,
    reducedMotion: Boolean,
    onJump: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = rememberHaptic(reducedMotion)
    val isBookmark = note.kind == "bookmark"
    val podColor = if (isBookmark) Color(0xFFD97706) else Color(0xFF2563EB)
    val podIcon = if (isBookmark) Icons.Outlined.Bookmark else Icons.Outlined.FormatQuote

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .listItemEnter(index, reducedMotion),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 28dp 独立微彩圆角底座
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(podColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp))
                    .border(0.6.dp, podColor.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    podIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = podColor,
                )
            }

            // 典雅排版
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                note.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 跳转微胶囊
            NotesActionPill(
                text = "跳转",
                icon = Icons.Outlined.NearMe,
                onClick = onJump,
            )

            Spacer(Modifier.width(6.dp))

            // 删除微操作
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * 灵感微岛卡片。
 */
@Composable
internal fun InspirationItemCard(
    inspiration: InspirationEntity,
    index: Int,
    reducedMotion: Boolean,
) {
    val amberColor = Color(0xFFF59E0B)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .listItemEnter(index, reducedMotion),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 28dp 暖琥珀底座 + Lightbulb
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(amberColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp))
                    .border(0.6.dp, amberColor.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = amberColor,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = inspiration.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inspiration.body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = inspiration.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
