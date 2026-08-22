package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

/**
 * 规则 pill 后缀：扫描中 →「扫描中…」，完成 →「N章」，取消/失败 → 可解释文案；
 * 其他规则回退到预览章数；无预览时不显示「0章」（大型流式 TXT 未扫描前的错误展示）。
 */
internal fun txtRulePillSuffix(
    status: TxtRuleScanStatus?,
    ruleId: String,
    previewCount: Int,
): String? = when (status) {
    is TxtRuleScanStatus.Running ->
        if (status.ruleId == ruleId) "扫描中…" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Completed ->
        if (status.ruleId == ruleId) "${status.chapterCount}章" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Cancelled ->
        if (status.ruleId == ruleId) "已取消" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Failed ->
        if (status.ruleId == ruleId) "扫描失败" else previewSuffix(previewCount)
    TxtRuleScanStatus.Idle, null -> previewSuffix(previewCount)
}

private fun previewSuffix(previewCount: Int): String? =
    if (previewCount > 0) "${previewCount}章" else null

internal data class ReaderTocEntry(
    val index: Int,
    val title: String,
    val volume: String,
    val isCurrent: Boolean,
    val isRecent: Boolean,
    val isRead: Boolean = false,
)

internal fun readerTocEntries(
    titles: List<String>,
    current: Int,
    recent: List<Int>,
    read: Set<Int> = emptySet(),
): List<ReaderTocEntry> {
    var volume = "正文"
    return titles.mapIndexedNotNull { index, title ->
        if (isVolumeHeader(title)) {
            volume = title
            null
        } else {
            ReaderTocEntry(
                index = index,
                title = title.ifBlank { "未命名章节" },
                volume = volume,
                isCurrent = index == current,
                isRecent = index in recent,
                isRead = index in read,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TocSheet(
    entries: List<ReaderTocEntry>,
    current: Int,
    totalChapters: Int,
    onPick: (Int) -> Unit,
    txtRules: List<TxtChapterDetector.Rule> = emptyList(),
    selectedTxtRule: String = "builtin",
    txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>> = emptyMap(),
    txtRuleScanStatus: TxtRuleScanStatus? = null,
    onTxtRule: (String) -> Unit = {},
    onCancelTxtScan: () -> Unit = {},
    onManageRules: () -> Unit = {},
    /** 本书书签（kind == "bookmark" 的笔记）；内嵌展示并可直接跳转。 */
    bookmarks: List<com.creationreadingassistant.data.local.entity.NoteEntity> = emptyList(),
    onPickBookmark: (com.creationreadingassistant.data.local.entity.NoteEntity) -> Unit = {},
    showReadStatus: Boolean = false,
    showClearReadMarks: Boolean = false,
    onClearReadMarks: () -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val collapsed = remember { mutableStateOf<Set<String>>(emptySet()) }
    var rulesExpanded by remember { mutableStateOf(false) }
    var clearMenuExpanded by remember { mutableStateOf(false) }
    var confirmClearReads by remember { mutableStateOf(false) }
    val groups = remember(entries) { entries.groupBy { it.volume }.toList() }
    val recentEntries = remember(entries) { entries.filter { it.isRecent }.take(5) }
    val readCount = remember(entries) { entries.count { it.isRead } }
    val chapterIndices = remember(entries) { entries.map { it.index } }
    val listState = rememberLazyListState()
    val currentListPosition = remember(groups, current, recentEntries) {
        var position = if (recentEntries.isEmpty()) 0 else 2
        run search@{
            groups.forEach { (_, chapterEntries) ->
                position += 1 // volume header
                val offset = chapterEntries.indexOfFirst { it.index == current }
                if (offset >= 0) {
                    position += offset
                    return@search
                }
                position += chapterEntries.size
            }
        }
        position
    }
    LaunchedEffect(current, chapterIndices) {
        if (entries.any { it.index == current }) listState.scrollToItem(currentListPosition.coerceAtLeast(0))
    }

    if (confirmClearReads) {
        GlassAlertDialog(
            onDismissRequest = { confirmClearReads = false },
            title = { Text("清除已读标记") },
            text = { Text("确定清除本书全部章节的已读标记吗？此操作不会删除阅读进度、书签或笔记。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearReads = false
                        onClearReadMarks()
                    },
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearReads = false }) { Text("取消") }
            },
        )
    }

    ReaderSheetScaffold(
        title = "目录",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "${(current + 1).coerceAtLeast(1)} / ${totalChapters.coerceAtLeast(0)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (showReadStatus) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "已读 $readCount/${entries.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (showClearReadMarks) {
                    Box {
                        IconButton(onClick = { clearMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "目录更多操作")
                        }
                        DropdownMenu(
                            expanded = clearMenuExpanded,
                            onDismissRequest = { clearMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("清除已读标记") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                },
                                onClick = {
                                    clearMenuExpanded = false
                                    confirmClearReads = true
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (txtRules.isNotEmpty()) {
            TocRulesEntryRow(onClick = onManageRules)
        }
        if (bookmarks.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap / 2),
            ) {
                Text("书签", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                bookmarks.take(8).forEach { bm ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPickBookmark(bm) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            bm.excerpt?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            FullEmptyState(
                modifier = Modifier.fillMaxWidth().weight(1f),
                icon = { LineArtBook(sizeDp = 72.dp) },
                title = "未发现目录",
                body = "这本书暂未识别到章节结构，无法在此浏览。",
            )
            return@ReaderSheetScaffold
        }
        if (txtRules.isNotEmpty()) {
            TocRecognitionSection(
                txtRules = txtRules,
                selectedTxtRule = selectedTxtRule,
                txtRulePreviews = txtRulePreviews,
                txtRuleScanStatus = txtRuleScanStatus,
                expanded = rulesExpanded,
                onToggleExpanded = { rulesExpanded = !rulesExpanded },
                onTxtRule = onTxtRule,
                onCancelTxtScan = onCancelTxtScan,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = layout.pageHorizontal,
                vertical = layout.relatedGap,
            ),
            verticalArrangement = Arrangement.spacedBy(layout.microGap),
        ) {
            if (recentEntries.isNotEmpty()) {
                item("recent_title") {
                    Text("最近浏览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item("recent_chapters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(layout.relatedGap)) {
                        items(recentEntries, key = { "recent_${it.index}" }) { entry ->
                            Surface(
                                onClick = { onPick(entry.index) },
                                shape = PillShape,
                                color = if (entry.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    entry.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            groups.forEach { (volume, chapterEntries) ->
                val isCollapsed = volume in collapsed.value
                item("volume_$volume", contentType = "volume_header") {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(volume, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (showReadStatus) {
                                "已读 ${chapterEntries.count { it.isRead }}/${chapterEntries.size}"
                            } else {
                                "${chapterEntries.size}章"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            if (isCollapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                            contentDescription = if (isCollapsed) "展开" else "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!isCollapsed) {
                    items(chapterEntries, key = { "chapter_${it.index}" }, contentType = { "toc_row" }) { entry ->
                        TocRow(entry = entry, onPick = { onPick(entry.index) })
                    }
                }
            }
        }
    }
}

/** 目录页顶部「目录与净化规则」管理入口：始终可见，点击交给宿主打开规则管理 Sheet。 */
@Composable
private fun TocRulesEntryRow(onClick: () -> Unit) {
    val layout = LocalLayoutTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap)
            .clip(LocalComponentSpec.current.listItemShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "管理目录与净化规则" }
            .padding(horizontal = layout.cardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = "目录与净化规则",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(layout.contentGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = "目录与净化规则",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "管理目录识别与替换净化",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = "进入规则管理",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「目录识别」快捷区：从章节列表末尾移到顶部管理入口附近；保留快速单选与扫描进度。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TocRecognitionSection(
    txtRules: List<TxtChapterDetector.Rule>,
    selectedTxtRule: String,
    txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    txtRuleScanStatus: TxtRuleScanStatus?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTxtRule: (String) -> Unit,
    onCancelTxtScan: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = layout.pageHorizontal)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("目录识别", style = MaterialTheme.typography.titleSmall)
                Text("仅在目录不准确时调整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "收起目录识别" else "展开目录识别",
            )
        }
        (txtRuleScanStatus as? TxtRuleScanStatus.Running)?.let { running ->
            TxtScanProgressRow(status = running, onCancel = onCancelTxtScan)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                txtRules.forEach { rule ->
                    val preview = txtRulePreviews[rule.id].orEmpty()
                    val suffix = txtRulePillSuffix(txtRuleScanStatus, rule.id, preview.size)
                    OptionPill(
                        selected = selectedTxtRule == rule.id,
                        label = if (suffix == null) rule.label else "${rule.label} · $suffix",
                        onClick = { onTxtRule(rule.id) },
                    )
                }
            }
        }
    }
}

/** 扫描中的进度 + 取消行：进度未知时 indeterminate，已知时 0..1。 */
@Composable
private fun TxtScanProgressRow(
    status: TxtRuleScanStatus.Running,
    onCancel: () -> Unit,
) {
    val percent = status.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "正在扫描目录，$percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val progress = status.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.weight(1f))
        }
        Text(
            text = "扫描中…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) {
            Text("取消")
        }
    }
}

@Composable
internal fun TocRow(entry: ReaderTocEntry, onPick: () -> Unit) {
    // 简洁目录行：序号 + 标题两栏。当前章仅用主色文字标识（无色块/竖条/徽章），
    // 已读章节降为 outline 色；整行高度紧凑，快速滑动时的重组与测量成本也更低。
    val indexColor = if (entry.isCurrent) MaterialTheme.colorScheme.primary
    else if (entry.isRead) MaterialTheme.colorScheme.outline
    else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = when {
        entry.isCurrent -> MaterialTheme.colorScheme.primary
        entry.isRead -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LocalComponentSpec.current.listItemShape)
            .clickable(onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (entry.index + 1).toString(),
            modifier = Modifier
                .width(40.dp)
                .padding(start = 4.dp)
                // 当前章优先保持“当前章节”语义，不叠加已读标记（计数仍含当前章）
                .semantics { if (entry.isRead && !entry.isCurrent) contentDescription = "已读章节" },
            style = MaterialTheme.typography.labelMedium,
            color = indexColor,
            fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f).padding(vertical = 11.dp, horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor,
            fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (entry.isCurrent) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { contentDescription = "当前章节" },
            )
        }
    }
}

/** 按「卷/部」标题聚合章节；保留给单元测试和兼容调用。 */
internal fun groupChaptersByVolume(titles: List<String>): List<Pair<String, List<Int>>> {
    val result = mutableListOf<Pair<String, MutableList<Int>>>()
    for ((i, title) in titles.withIndex()) {
        if (isVolumeHeader(title)) result.add(title to mutableListOf())
        else {
            if (result.isEmpty()) result.add("正文" to mutableListOf())
            result.last().second.add(i)
        }
    }
    return result
}

internal fun isVolumeHeader(title: String): Boolean {
    if (title.isBlank()) return false
    return title.contains("卷") || title.contains("部") ||
        title.contains("Part", ignoreCase = true) || title.contains("Volume", ignoreCase = true) ||
        Regex("^第[一二三四五六七八九十\\d]+[卷部]").containsMatchIn(title)
}
