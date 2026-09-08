package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.formatDuration
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun BookInfoSheet(
    bookTitle: String,
    bookAuthor: String?,
    bookFormat: String,
    chapterCount: Int,
    wordCount: Int,
    currentChapterTitle: String,
    progressPercent: Float,
    activeReadingMs: Long,
    savedReadingMs: Long,
    sessionsCount: Int,
    sourceFile: String?,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val haptic = rememberHaptic(rememberReducedMotion())

    if (confirmDelete) {
        // G26：确认弹框同样去除 glassWindowBlur（反射整窗实时模糊），避免在 sheet 之上
        // 再叠一层模糊 Dialog 与翻页争 GPU；除模糊外视觉/行为与普通 AlertDialog 一致。
        AppAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("删除本书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "确定从书架移除《${bookTitle}》吗？本地正文文件、阅读进度和笔记将一并移除，删除后可随时从书架恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                val deleteInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        confirmDelete = false
                        onDelete()
                    },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.bounceable(deleteInteraction),
                ) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        confirmDelete = false
                    },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.bounceable(cancelInteraction),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }

    ReaderSheetScaffold(title = "书籍信息") {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                // 顶部书籍标题、作者与格式微胶囊
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = bookAuthor ?: "作者未知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 格式微徽章（EPUB / TXT 独立微徽章）
                        val isEpub = bookFormat.equals("epub", ignoreCase = true)
                        val badgeTint = if (isEpub) Color(0xFF2563EB) else Color(0xFF059669)
                        Surface(
                            shape = PillShape,
                            color = badgeTint.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, badgeTint.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = bookFormat.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 阅读统计：4 列独立微岛指标卡矩阵
                BookInfoSectionHeader(
                    icon = Icons.Outlined.BarChart,
                    title = "阅读统计",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BookStatMicroIsland(
                        icon = Icons.Outlined.AccessTime,
                        value = formatDuration(activeReadingMs),
                        label = "本次已读",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.weight(1f),
                    )
                    BookStatMicroIsland(
                        icon = Icons.Outlined.HourglassEmpty,
                        value = formatDuration(savedReadingMs),
                        label = "累计阅读",
                        tint = Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                    )
                    BookStatMicroIsland(
                        icon = Icons.Outlined.AutoStories,
                        value = "$sessionsCount",
                        label = "阅读次数",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                    )
                    BookStatMicroIsland(
                        icon = Icons.Outlined.DonutLarge,
                        value = "${progressPercent.toInt()}%",
                        label = "当前进度",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 正文信息：微岛列表卡片
                BookInfoSectionHeader(
                    icon = Icons.Outlined.Info,
                    title = "正文信息",
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        BookInfoIslandRow(
                            icon = Icons.Outlined.FormatListNumbered,
                            label = "章节数",
                            value = "$chapterCount 章",
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        BookInfoIslandRow(
                            icon = Icons.Outlined.TextFields,
                            label = "总字数",
                            value = "${wordCount} 字",
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        BookInfoIslandRow(
                            icon = Icons.Outlined.BookmarkBorder,
                            label = "当前章节",
                            value = currentChapterTitle.ifBlank { "正文" },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        BookInfoIslandRow(
                            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            label = "来源文件",
                            value = sourceFile ?: "本地导入",
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // “阅读设置”微岛按钮
                val settingsInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onOpenSettings()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceable(settingsInteraction),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "阅读设置",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 危险操作微岛化
                BookInfoSectionHeader(
                    icon = Icons.Outlined.WarningAmber,
                    title = "危险操作",
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                val deleteInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        confirmDelete = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceable(deleteInteraction),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "从书架移除本书",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 分区标题带 20dp 独立微图标底座。
 */
@Composable
private fun BookInfoSectionHeader(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
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
            color = if (tint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 4 列独立微岛统计指标卡。
 */
@Composable
private fun BookStatMicroIsland(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(tint.copy(alpha = 0.12f), shape = RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = tint,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = DisplayFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}

/**
 * 正文信息微岛卡片单行。
 */
@Composable
private fun BookInfoIslandRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
