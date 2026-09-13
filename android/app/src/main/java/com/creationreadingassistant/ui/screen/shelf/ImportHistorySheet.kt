package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SkipSlate = Color(0xFF5A6661)

// ===================== 底部弹层：导入历史 =====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val haptic = rememberHaptic(rememberReducedMotion())
    val layout = LocalLayoutTokens.current
    val progress = if (batch.total > 0) batch.completed.toFloat() / batch.total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "sheetBatchProgress",
    )

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = layout.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        // 历史最多持久化 100 条（ImportHistoryStore），队列也可能很长；内容必定超过一屏，
        // 这里必须给 sheet 内容一个可滚动容器，否则屏幕外的记录既看不到也点不到。
        Column(
            modifier = Modifier
                // 水平内边距走布局令牌（同一 shell 规则）
                .padding(horizontal = layout.pageHorizontal, vertical = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                Text("导入历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    IconButton(onClick = { haptic(HapticFeedbackType.LongPress); onClear() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "清空历史", modifier = Modifier.size(AppIconSize.Compact), tint = AppError)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // 导入结果总结微岛卡片
            if (batch.hasResult || batch.isRunning) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 32dp 微彩底座
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        when {
                                            batch.isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            batch.failed > 0 -> AppError.copy(alpha = 0.14f)
                                            batch.stopped > 0 -> AppWarning.copy(alpha = 0.14f)
                                            else -> AppSuccess.copy(alpha = 0.14f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (batch.isRunning) {
                                    CircularProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = when {
                                            batch.failed > 0 -> Icons.Outlined.ErrorOutline
                                            batch.stopped > 0 -> Icons.Outlined.Stop
                                            else -> Icons.Outlined.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            batch.failed > 0 -> AppError
                                            batch.stopped > 0 -> AppWarning
                                            else -> AppSuccess
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (batch.isRunning) "正在处理 ${batch.sourceLabel}" else "本次导入总结",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (batch.isRunning && batch.total > 0) {
                                        "已处理 ${batch.completed} / ${batch.total} 本 (${(progress * 100).toInt()}%)"
                                    } else {
                                        "共解析 ${batch.total} 本，成功入库 ${batch.succeeded} 本"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 平滑过渡进度条
                        if (batch.isRunning && batch.total > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedProgress)
                                        .fillMaxHeight()
                                        .clip(PillShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }

                        // 分色徽章（成功翠绿、跳过青灰、失败赤红、重复琥珀）
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StatusMicroBadge("成功 ${batch.succeeded}", AppSuccess)
                            if (batch.duplicates > 0) StatusMicroBadge("重复 ${batch.duplicates}", AppWarning)
                            if (batch.skipped > 0) StatusMicroBadge("跳过 ${batch.skipped}", SkipSlate)
                            if (batch.failed > 0) StatusMicroBadge("失败 ${batch.failed}", AppError)
                            if (batch.stopped > 0) StatusMicroBadge("未处理 ${batch.stopped}", MaterialTheme.colorScheme.outline)
                        }

                        if (batch.truncated || batch.unreadableFolders > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppWarning.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AppWarning, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = buildList {
                                            if (batch.truncated) add("文件较多，已按安全上限停止扫描")
                                            if (batch.unreadableFolders > 0) add("${batch.unreadableFolders} 个子文件夹无法读取")
                                        }.joinToString("；"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppWarning,
                                    )
                                }
                            }
                        }

                        if (!batch.isRunning) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (batch.failures.isNotEmpty()) {
                                    Button(
                                        onClick = onRetryFailed,
                                        shape = PillShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Text("重试失败项")
                                    }
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
                    Text("还没有导入记录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("导入书籍后，这里会显示每次导入的结果、编码识别和失败原因。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (tasks.isNotEmpty()) {
                    SectionTitle("本次导入队列（${tasks.size}）")
                    SectionCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column {
                            tasks.forEachIndexed { index, task ->
                                if (index > 0) OrganizerDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val taskColor = when (task.status) {
                                        "error" -> AppError
                                        "duplicate", "skipped" -> SkipSlate
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    // 32dp 微彩底座
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(taskColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (task.status == "processing") {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = taskColor)
                                        } else {
                                            Icon(
                                                imageVector = when (task.status) {
                                                    "error" -> Icons.Outlined.Warning
                                                    "skipped" -> Icons.Outlined.Stop
                                                    else -> Icons.Outlined.CheckCircle
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = taskColor,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(task.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
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
                        }.joinToString(" · "),
                    )
                    SectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            history.forEachIndexed { index, entry ->
                                if (index > 0) OrganizerDivider()
                                val isSuccess = entry.status == "success"
                                val isDup = entry.isDuplicate || entry.status == "duplicate"
                                val isSkip = entry.status == "skipped"
                                val statusColor = when {
                                    isSuccess -> AppSuccess
                                    isDup -> AppWarning
                                    isSkip -> SkipSlate
                                    else -> AppError
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 32dp 微彩底座
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(statusColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isSuccess -> Icons.Outlined.CheckCircle
                                                isDup || isSkip -> Icons.Outlined.WarningAmber
                                                else -> Icons.Outlined.ErrorOutline
                                            },
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            if (entry.format.isNotBlank()) {
                                                FormatCapsule(entry.format.uppercase(), formatColor(entry.format))
                                            }
                                            if (entry.fileSize > 0) {
                                                Text(formatBytes(entry.fileSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (!entry.encoding.isNullOrBlank()) {
                                                Text(entry.encoding, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (entry.bookTitle != null) {
                                                Text("《${entry.bookTitle}》", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Text(
                                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                        if (entry.error != null) {
                                            Text(entry.error, style = MaterialTheme.typography.bodySmall, color = AppError, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
