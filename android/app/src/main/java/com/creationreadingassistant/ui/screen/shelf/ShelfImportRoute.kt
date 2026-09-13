package com.creationreadingassistant.ui.screen.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 东方纸墨低饱和雅致格式色彩
private val FormatEpubColor = Color(0xFF5E506B) // 黛紫
private val FormatTxtColor = Color(0xFF385E69)  // 霁蓝 / 墨青
private val FormatPdfColor = Color(0xFF8C5835)  // 赭石 / 暖褐
private val FormatMdColor = Color(0xFF3B5E4B)   // 苍松 / 墨绿

// 导入渠道专用微彩底座色（文件：墨青，文件夹：天蓝，电脑：淡紫）
private val ChannelFileColor = Color(0xFF385E69)    // 墨青
private val ChannelFolderColor = Color(0xFF2E6592)  // 天蓝
private val ChannelDesktopColor = Color(0xFF6E5677) // 淡紫

// 导入历史状态微彩
private val SkipSlate = Color(0xFF5A6C7C) // 青灰跳过状态

@Composable
internal fun ShelfImportRoute(
    navController: NavHostController,
    viewModel: ShelfViewModel,
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val batch = source.auxiliary.importBatch
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDesktopBooks by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importFolder)
    }

    AppScreenScaffold(
        title = "导入书籍",
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = {
            if (source.auxiliary.importHistory.isNotEmpty()) {
                IconButton(onClick = { confirmClearHistory = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { viewport ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(viewport)) {
            val adaptive = adaptivePageMetrics(maxWidth, LocalLayoutTokens.current)
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = adaptive.contentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = LocalLayoutTokens.current.pageVertical,
                    bottom = LocalLayoutTokens.current.pageVertical + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
            ) {
                // 1. 导入渠道选择微岛卡片与格式微胶囊（东方纸墨雅致低饱和色系 + 32dp 微彩底座）
                item {
                    SectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            // 格式微胶囊提示栏
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("支持格式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FormatCapsule("EPUB", FormatEpubColor)
                                FormatCapsule("TXT", FormatTxtColor)
                                FormatCapsule("MD", FormatMdColor)
                            }
                            OrganizerDivider()
                            ImportChannelRow(
                                icon = Icons.Outlined.FolderOpen,
                                title = "我的书籍目录",
                                description = "在 App 内浏览、智能识别并批量导入",
                                enabled = !batch.isRunning,
                                tint = ChannelFolderColor,
                            ) {
                                navController.navigate(SHELF_LIBRARY_ROUTE)
                            }
                            OrganizerDivider()
                            ImportChannelRow(
                                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                title = "选择文件",
                                description = "一次选择一本或多本 EPUB、TXT、Markdown",
                                enabled = !batch.isRunning,
                                tint = ChannelFileColor,
                            ) {
                                filePicker.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
                            }
                            OrganizerDivider()
                            ImportChannelRow(
                                icon = Icons.Outlined.FolderOpen,
                                title = "选择文件夹",
                                description = "扫描文件夹及其子文件夹中的支持格式",
                                enabled = !batch.isRunning,
                                tint = ChannelFolderColor,
                            ) {
                                folderPicker.launch(null)
                            }
                            OrganizerDivider()
                            ImportChannelRow(
                                icon = Icons.Outlined.Computer,
                                title = "从电脑导入",
                                description = "下载电脑端已同步的书籍正文",
                                enabled = !batch.isRunning,
                                tint = ChannelDesktopColor,
                            ) {
                                showDesktopBooks = true
                            }
                        }
                    }
                }

                // 2. 批次处理状态与指标微岛卡片（微岛化进度条与分色徽章）
                if (batch.isRunning || batch.hasResult) {
                    item {
                        ImportProgressCard(
                            batch = batch,
                            onStop = viewModel::requestStopImport,
                            onRetry = viewModel::retryFailedImports,
                        )
                    }
                }

                // 3. 当前实时处理队列
                if (source.auxiliary.importTasks.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Text(
                                "当前处理队列",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${source.auxiliary.importTasks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    item {
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                source.auxiliary.importTasks.forEachIndexed { index, task ->
                                    if (index > 0) OrganizerDivider()
                                    ImportTaskRow(task)
                                }
                            }
                        }
                    }
                }

                // 4. 导入历史列表与空态
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(
                            "导入记录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (source.auxiliary.importHistory.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${source.auxiliary.importHistory.size} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (source.auxiliary.importHistory.isEmpty()) {
                    item {
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                            ) {
                                LineArtBook(modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "还没有导入记录",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "从本地或电脑导入书籍后，这里会完整保留解析详情、编码识别与入库状态。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    items(source.auxiliary.importHistory, key = { it.id }) { entry ->
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            ImportHistoryRow(entry)
                        }
                    }
                }

                // 5. 底部服务与安全提示微岛
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp).padding(top = 1.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "应用具备完整的后台导入管道。返回书架或切换页面不会中断导入流程。点击停止时会安全完成当前正文，已入库书籍保持不变。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDesktopBooks) {
        DesktopBooksSheet(
            onDismiss = { showDesktopBooks = false },
            onDownload = { bookId ->
                viewModel.downloadBookContent(bookId) { message ->
                    scope.launch { snackbar.showSnackbar(message) }
                }
            },
            listBooks = viewModel::listDesktopBooks,
        )
    }

    if (confirmClearHistory) {
        GlassAlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清空导入记录？") },
            text = { Text("只会清除导入日志与历史列表，不会删除已经导入书架的书籍与正文。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImportHistory()
                    confirmClearHistory = false
                }) { Text("清空", color = AppError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportProgressCard(
    batch: ImportBatchUiState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = if (batch.total > 0) batch.completed.toFloat() / batch.total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "importLinearProgress",
    )

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            batch.isRunning -> "正在导入 ${batch.sourceLabel}"
                            batch.stopped > 0 -> "导入已停止"
                            batch.failed > 0 -> "导入完成（有失败项）"
                            else -> "本次导入完成"
                        },
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

            // 微岛化平滑进度条
            if (batch.isRunning && batch.total > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = AppWarning,
                            modifier = Modifier.size(16.dp),
                        )
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

            when {
                batch.isRunning -> {
                    OutlinedButton(
                        onClick = onStop,
                        enabled = !batch.stopRequested,
                        shape = PillShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (batch.stopRequested) "将在当前文件完成后停止" else "停止后续导入")
                    }
                }
                batch.failures.isNotEmpty() -> {
                    Button(
                        onClick = onRetry,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重试失败项 (${batch.failures.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportTaskRow(task: ImportTaskUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 32dp 微彩底座
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    when (task.status) {
                        "error" -> AppError.copy(alpha = 0.12f)
                        "done" -> AppSuccess.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (task.status) {
                "processing" -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                "error" -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = AppError,
                    modifier = Modifier.size(16.dp),
                )
                else -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AppSuccess,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.phase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportChannelRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    tint: Color,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 32dp 微彩底座（带 0.5dp 柔和发丝微边框）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f))
                .border(0.5.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportHistoryRow(entry: ImportHistoryEntry) {
    val isSuccess = entry.status == "success"
    val isDup = entry.isDuplicate || entry.status == "duplicate"
    val isSkip = entry.status == "skipped"
    val statusColor = when {
        isSuccess -> AppSuccess
        isDup -> AppWarning
        isSkip -> SkipSlate
        else -> AppError
    }
    val statusLabel = when {
        isSuccess -> "入库成功"
        isDup -> "重复跳过"
        isSkip -> "已跳过"
        else -> "解析失败"
    }

    val formatColor = when (entry.format.uppercase()) {
        "EPUB" -> FormatEpubColor
        "TXT" -> FormatTxtColor
        "PDF" -> FormatPdfColor
        "MD", "MARKDOWN" -> FormatMdColor
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 32dp 微彩底座（与状态微彩呼应）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .border(0.5.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // 精致圆润的状态微徽章（成功绿、失败红、跳过青灰、重复琥珀）
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(0.5.dp, statusColor.copy(alpha = 0.35f), PillShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (entry.format.isNotBlank()) {
                    FormatCapsule(entry.format.uppercase(), formatColor)
                }
                if (entry.fileSize > 0) {
                    Text(
                        text = formatBytes(entry.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!entry.encoding.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = entry.encoding,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (!entry.bookTitle.isNullOrBlank()) {
                    Text(
                        text = "· 《${entry.bookTitle}》",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "· " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (!entry.error.isNullOrBlank()) {
                Text(
                    text = entry.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
