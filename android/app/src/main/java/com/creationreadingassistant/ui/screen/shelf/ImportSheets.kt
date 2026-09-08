package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 东方纸墨柔和低饱和配色
private val FormatPurple = Color(0xFF6E5675) // 黛檀（EPUB）
private val FormatBlue = Color(0xFF3F637D)   // 霁蓝（TXT）
private val FormatAmber = Color(0xFF9E6532)  // 暖赭（PDF）
private val FormatGreen = Color(0xFF3B6E55)  // 松竹（MD）
private val SkipSlate = Color(0xFF5A6661)    // 砚墨跳过状态

private fun formatColor(format: String): Color = when (format.uppercase()) {
    "EPUB" -> FormatPurple
    "TXT" -> FormatBlue
    "PDF" -> FormatAmber
    "MD", "MARKDOWN" -> FormatGreen
    else -> Color(0xFF5A6661)
}

// ===================== 底部弹层：从电脑下载 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBooksSheet(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    listBooks: suspend () -> List<SyncContract.BookFileManifest>,
) {
    var books by remember { mutableStateOf<List<SyncContract.BookFileManifest>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic(rememberReducedMotion())

    fun load() {
        scope.launch {
            loading = true
            books = listBooks()
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            // 顶部标题微岛
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("从电脑导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "局域网快速同步正文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 刷新按钮微胶囊
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        load()
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "刷新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (books.isEmpty()) {
                // 空状态：精致东方纸墨微岛卡片（配网络连接状态微指示与重试引导）
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineArtBook(modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "暂无可下载书籍",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 网络连接状态微指示
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, Color(0xFFD97706).copy(alpha = 0.25f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFFD97706)),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "当前没有可用书籍",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "可检查电脑端同步目录是否包含书籍，再重新探测。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 重新探测重试引导微胶囊
                        Surface(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                load()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "重新探测连接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            } else {
                // 书籍列表：封面微缩占位图与下载状态高光指示
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        books.forEachIndexed { index, book ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                )
                            }
                            DesktopBookRowItem(
                                book = book,
                                onDownload = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onDownload(book.bookId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 可下载书籍列表项：封面微缩占位图与下载状态高光指示 */
@Composable
private fun DesktopBookRowItem(
    book: SyncContract.BookFileManifest,
    onDownload: () -> Unit,
) {
    val fmtColor = formatColor(book.format)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // 封面微缩占位图
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = fmtColor.copy(alpha = 0.12f),
            border = BorderStroke(0.6.dp, fmtColor.copy(alpha = 0.30f)),
            modifier = Modifier.size(width = 34.dp, height = 46.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 书脊阴影
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = book.format.uppercase().take(4),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fmtColor,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = book.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatCapsule(book.format.uppercase(), fmtColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    formatBytes(book.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 下载状态高光指示微胶囊
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "下载",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ===================== 导入来源 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSourceSheet(
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("添加到书架", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "可以一次选择多本书，也可以扫描一个文件夹。应用具备后台导入管道。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 格式微胶囊标签（东方纸墨柔和低饱和调色板）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("支持格式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormatCapsule("EPUB", FormatPurple)
                FormatCapsule("TXT", FormatBlue)
                FormatCapsule("PDF", FormatAmber)
                FormatCapsule("MD", FormatGreen)
            }

            // 目录选择微岛卡片
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ImportSourceRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "选择书籍",
                        description = "一次选择一本或多本本地文件",
                        tint = FormatPurple,
                        onClick = onSelectFiles,
                    )
                    OrganizerDivider()
                    ImportSourceRow(
                        icon = Icons.Outlined.Folder,
                        title = "扫描文件夹",
                        description = "包含子文件夹，自动跳过其他非支持文件",
                        tint = FormatBlue,
                        onClick = onSelectFolder,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImportSourceRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 32dp 微彩底座
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(AppIconSize.Small),
        )
    }
}

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
    val progress = if (batch.total > 0) batch.completed.toFloat() / batch.total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "sheetBatchProgress",
    )

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
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

// ===================== 格式与状态微胶囊 =====================
@Composable
internal fun FormatCapsule(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 10.sp,
        )
    }
}

@Composable
internal fun StatusMicroBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.35f), PillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
