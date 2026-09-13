package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.library.LibraryEntry
import com.creationreadingassistant.feature.library.LibrarySortMode
import com.creationreadingassistant.feature.library.RecognitionConfidence
import com.creationreadingassistant.feature.library.RecognitionDecision
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicy
import com.creationreadingassistant.ui.viewmodel.LibraryCrumb
import com.creationreadingassistant.ui.viewmodel.LibraryFileRow
import com.creationreadingassistant.ui.viewmodel.LibraryRecognitionRow
import com.creationreadingassistant.ui.viewmodel.LibraryRecognitionUiState
import com.creationreadingassistant.ui.viewmodel.LibraryShelfMatchKind
import com.creationreadingassistant.ui.viewmodel.RecognitionFilter

// ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝ 目录浏览与智能识别组件 ＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝

private val DirectoryTint = Color(0xFF2E6592) // 天蓝：目录
private val FileTint = Color(0xFF385E69)      // 墨青：普通文件
private val HintTint = Color(0xFF5A6C7C)      // 青灰：弱判定提示

/** 轻量文件尺寸格式化（与书架 Int 版本区分，避免同包重载冲突）。 */
internal fun formatLibrarySize(bytes: Long?): String = LibraryBrowserPolicy.formatSize(bytes)

internal fun formatLibraryTime(millis: Long?): String = LibraryBrowserPolicy.formatTime(millis)

/** 未设置目录 / 授权失效时的引导卡。 */
@Composable
internal fun LibraryRootSetupCard(
    unreadable: Boolean,
    onPickRoot: () -> Unit,
    onDismiss: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (unreadable) AppWarning.copy(alpha = 0.12f) else DirectoryTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (unreadable) Icons.Outlined.WarningAmber else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = if (unreadable) AppWarning else DirectoryTint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (unreadable) "无法访问原书籍目录" else "尚未设置我的书籍目录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (unreadable) {
                            "书架内已导入的书籍不受影响，重新授权后可继续浏览来源目录。"
                        } else {
                            "授权一个目录后，可以在 App 内浏览、搜索并批量导入其中的书籍。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickRoot, shape = PillShape) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (unreadable) "重新授权" else "设置书籍目录")
                }
                OutlinedButton(onClick = onDismiss, shape = PillShape) {
                    Text("返回书架")
                }
            }
            Text(
                text = "识别仅在本机完成，不上传文件名、目录或正文样本。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 已配置根目录时的概览卡。 */
@Composable
internal fun LibraryRootSummaryCard(
    rootName: String,
    onReload: () -> Unit,
    onChangeRoot: () -> Unit,
    onClearRoot: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(DirectoryTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = DirectoryTint, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("我的书籍目录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = rootName.ifBlank { "书籍目录" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onChangeRoot) { Text("更换") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReload) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
                TextButton(onClick = onClearRoot) {
                    Text("移除配置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 顶部横向面包屑（方案 6.2：不照搬参考项目的底部路径栏）。 */
@Composable
internal fun LibraryBreadcrumb(
    crumbs: List<LibraryCrumb>,
    onCrumbClick: (Int) -> Unit,
) {
    if (crumbs.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val isLast = index == crumbs.lastIndex
            Text(
                text = crumb.displayName.ifBlank { "书籍目录" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickableCompat(enabled = !isLast) { onCrumbClick(index) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (!isLast) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 排序选择器：名称 / 修改时间 / 大小。 */
@Composable
internal fun LibrarySortRow(
    sortMode: LibrarySortMode,
    onSortModeChange: (LibrarySortMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("排序", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectablePill(
            text = "名称",
            selected = sortMode == LibrarySortMode.NAME,
            onClick = { onSortModeChange(LibrarySortMode.NAME) },
        )
        SelectablePill(
            text = "修改时间",
            selected = sortMode == LibrarySortMode.MODIFIED_DESC,
            onClick = { onSortModeChange(LibrarySortMode.MODIFIED_DESC) },
        )
        SelectablePill(
            text = "大小",
            selected = sortMode == LibrarySortMode.SIZE_DESC,
            onClick = { onSortModeChange(LibrarySortMode.SIZE_DESC) },
        )
    }
}

/** 目录行：单击进入。 */
@Composable
internal fun LibraryDirectoryRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
) {
    LibraryRowShell(
        icon = Icons.Outlined.Folder,
        tint = DirectoryTint,
        title = entry.displayName.ifBlank { "未命名目录" },
        subtitle = "文件夹",
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(AppIconSize.Small),
            )
        },
        onClick = onClick,
    )
}

/** 文件行：确认已入架（有 bookId）单击打开书籍，其余单击切换选择；长按进入多选。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryFileItem(
    row: LibraryFileRow,
    selected: Boolean,
    inSelectionMode: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onOpen: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val title = row.entry.displayName.ifBlank { "未命名文件" }
    val meta = buildList {
        add(formatLibrarySize(row.entry.sizeBytes))
        add(formatLibraryTime(row.entry.lastModifiedMillis))
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .combinedClickable(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    // 多选状态下单击仍是切换选择；只有浏览态下确认已入架的文件才打开书籍。
                    if (inSelectionMode || row.openableBookId == null) onToggle() else onOpen()
                },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else FileTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            } else {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = FileTint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.shelfMatch?.let { match ->
            Spacer(Modifier.width(8.dp))
            // 判据决定文案：确认（精确来源/同内容）与疑似（名称/名+大小）必须分开呈现。
            val (tag, tint) = when (match.kind) {
                LibraryShelfMatchKind.EXACT_SOURCE -> "已在书架" to AppSuccess
                LibraryShelfMatchKind.SAME_CONTENT -> "已在书架" to AppSuccess
                LibraryShelfMatchKind.CONTENT_UPDATED -> "内容有更新" to AppWarning
                LibraryShelfMatchKind.POSSIBLE, LibraryShelfMatchKind.NAME_ONLY -> "疑似已在书架" to HintTint
            }
            MicroTag(tag, tint)
        }
    }
}

@Composable
private fun LibraryRowShell(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickableCompat(onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** 多选底栏：只做批量加入书架，不提供来源删除（方案 4.2 / 12）。 */
@Composable
internal fun LibrarySelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选择 $selectedCount 项",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onCancel()
            }) { Text("取消选择") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectablePill(
                text = if (allSelected) "取消全选" else "全选",
                selected = allSelected,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSelectAllToggle()
                },
            )
            SelectablePill(
                text = "反选",
                selected = false,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onInvert()
                },
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onImport, shape = PillShape) {
                Text("加入书架")
            }
        }
    }
}

/** 识别结果面板：进度、停止、分档过滤与候选列表。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryRecognitionPanel(
    state: LibraryRecognitionUiState,
    filter: RecognitionFilter,
    onFilterChange: (RecognitionFilter) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleCandidate: (String) -> Unit,
    onSelectRecommended: () -> Unit,
    onImport: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isRunning -> "正在识别"
                            state.finished -> "识别完成"
                            else -> "智能识别"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            state.isRunning -> {
                                val ratio = state.processedRatio
                                val total = state.summary?.discovered ?: 0
                                if (ratio == null) "正在读取目录…" else "已检查 ${state.summary?.processed ?: 0} / $total 个文件"
                            }
                            state.finished -> "本次不会导入任何文件，确认后才提交到书架"
                            else -> "在已授权书籍目录中查找可阅读文件。识别仅在本机完成，不上传正文。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isRunning) {
                OutlinedButton(onClick = onStop, shape = PillShape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("停止扫描")
                }
            } else if (!state.finished) {
                Button(onClick = onStart, shape = PillShape, modifier = Modifier.fillMaxWidth()) {
                    Text("开始智能识别")
                }
            } else {
                RecognitionSummaryRows(state)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecognitionFilter.entries.forEach { option ->
                        SelectablePill(
                            text = option.label,
                            selected = filter == option,
                            onClick = { onFilterChange(option) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onSelectRecommended) { Text("只选推荐") }
                    TextButton(onClick = onStart) { Text("重新识别") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onImport, shape = PillShape, enabled = state.selected.isNotEmpty()) {
                        Text("加入书架 (${state.selected.size})")
                    }
                }
            }

            state.warnings.forEach { warning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AppWarning, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(warning, style = MaterialTheme.typography.labelSmall, color = AppWarning)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecognitionSummaryRows(state: LibraryRecognitionUiState) {
    val summary = state.summary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (summary == null) {
            Text("未发现候选文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@FlowRow
        }
        MicroTag("推荐 ${summary.recommended}", AppSuccess)
        MicroTag("可能是书籍 ${summary.review}", AppWarning)
        MicroTag("未识别 ${summary.rejected}", MaterialTheme.colorScheme.outline)
        if (summary.skippedFiles > 0) MicroTag("跳过格式 ${summary.skippedFiles}", HintTint)
        if (summary.unreadableFolders > 0) MicroTag("不可读目录 ${summary.unreadableFolders}", AppError)
        if (summary.truncated) MicroTag("结果已截断", AppWarning)
    }
}

/** 单条识别候选。 */
@Composable
internal fun LibraryRecognitionItem(
    row: LibraryRecognitionRow,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val candidate = row.candidate
    val (tint, label) = when (candidate.decision) {
        RecognitionDecision.RECOMMENDED -> AppSuccess to "推荐导入"
        RecognitionDecision.REVIEW -> AppWarning to "可能是书籍"
        RecognitionDecision.REJECTED -> MaterialTheme.colorScheme.outline to "未识别"
    }
    val meta = buildList {
        add(candidate.format?.uppercase() ?: "未知格式")
        add(formatLibrarySize(candidate.sizeBytes))
        candidate.encoding?.let { add(it) }
        if (candidate.sampledChapterHeadings > 0) add("样本 ${candidate.sampledChapterHeadings} 章")
        if (candidate.confidence == RecognitionConfidence.HIGH) add("高置信")
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.32f),
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .combinedClickableCompat(
                enabled = candidate.decision != RecognitionDecision.REJECTED,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    selected -> Icons.Outlined.CheckCircle
                    candidate.decision == RecognitionDecision.REJECTED -> Icons.Outlined.ErrorOutline
                    else -> Icons.AutoMirrored.Outlined.MenuBook
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                MicroTag(label, tint)
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            // 识别依据必须可见，避免智能识别成为黑盒（方案 4.3）。
            candidate.reasons.forEach { reason ->
                Text(
                    text = "· ${reason.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
            row.shelfMatch?.let { match ->
                val (text, tint) = when (match.kind) {
                    LibraryShelfMatchKind.EXACT_SOURCE ->
                        "· 已在书架：来源位置与已入架记录一致" to AppSuccess
                    LibraryShelfMatchKind.SAME_CONTENT ->
                        "· 已在书架：内容指纹与已入架版本一致（可能是改名或移动后的同一文件）" to AppSuccess
                    LibraryShelfMatchKind.CONTENT_UPDATED ->
                        "· 内容有更新：来源文件与已入架版本的大小或时间不一致" to AppWarning
                    LibraryShelfMatchKind.POSSIBLE ->
                        "· 疑似已在书架：名称与大小一致，导入时以内容哈希为准" to HintTint
                    LibraryShelfMatchKind.NAME_ONLY ->
                        "· 疑似已在书架（按文件名推测，导入时以内容为准）" to HintTint
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontSize = 11.sp,
                )
            }
            candidate.suggestedAuthor?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = "· 作者建议：$author",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun MicroTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.32f), PillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 11.sp,
        )
    }
}

/** 未识别原因折叠提示条。 */
@Composable
internal fun LibraryInfoBanner(
    text: String,
    tint: Color = AppWarning,
    icon: ImageVector = Icons.Outlined.WarningAmber,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
        }
    }
}

/**
 * 面包屑等轻量行使用的点击封装。
 *
 * 单独抽出来是为了让 [LibraryBreadcrumb] 与 [LibraryRowShell] 共享同一套抑制逻辑，
 * 避免在文件级引入多处 `@OptIn(ExperimentalFoundationApi::class)`。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableCompat(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.combinedClickable(enabled = enabled, onClick = onClick)
