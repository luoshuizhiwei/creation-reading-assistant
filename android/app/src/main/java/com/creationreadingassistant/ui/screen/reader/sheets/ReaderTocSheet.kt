package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

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
    /**
     * 无 TXT 章节识别规则时是否仍显示「替换净化」入口（EPUB 分页投影可用时为 true）。
     * TXT 路径由 [txtRules] 非空驱动同一入口。
     */
    showReplacementRulesEntry: Boolean = false,
    /** 本书书签（kind == "bookmark" 的笔记）；内嵌展示并可直接跳转。 */
    bookmarks: List<NoteEntity> = emptyList(),
    onPickBookmark: (NoteEntity) -> Unit = {},
    showReadStatus: Boolean = false,
    showClearReadMarks: Boolean = false,
    onClearReadMarks: () -> Unit = {},
    /**
     * 笔记 Tab 的数据源：**高亮 + 非书签笔记**（A01）。
     *
     * 由生产宿主经 [tocNoteEntries] 计算后显式传入。刻意**不给默认值** —— 旧签名带
     * `emptyList()` 默认值，宿主漏传时页面稳定显示「暂无划线与笔记」而不报错，
     * 正是 A01 缺陷能长期存在的原因（见 `TocNotesWiringGuardTest`）。
     */
    notes: List<TocNoteEntry>,
    /** 笔记 Tab 条目点击：按 [TocNoteEntry.navigationTarget] 走 typed 回源。 */
    onPickNote: (TocNoteEntry) -> Unit,
    // ── READER-PAGES-v1：书内索引附页形态（默认关闭，既有弹层形态不变）──────
    /** true = 全屏索引附页：顶栏 返回/书名/定位当前章节/更多；低频规则操作入「更多」菜单。 */
    pageStyle: Boolean = false,
    /** 附页顶栏书名。 */
    bookTitle: String = "",
    /** 附页根页关闭（= 既有 CloseSheet）。 */
    onClose: () -> Unit = {},
    /** 三个 Tab 的列表状态：宿主按 bookId 持久化（rememberSaveable），跨开关保留位置。 */
    tocListState: LazyListState = rememberLazyListState(),
    bookmarkListState: LazyListState = rememberLazyListState(),
    noteListState: LazyListState = rememberLazyListState(),
    /** 首次打开定位当前章节一次（宿主按 bookId 记忆）；此后重组/切 Tab 不再强行滚回。 */
    autoLocateCurrent: Boolean = false,
    /** 自动定位完成回调（宿主据此置位，避免再次进入重复定位）。 */
    onAutoLocated: () -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val spec = LocalComponentSpec.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(ReaderTocTab.TOC) }
    var searchQuery by remember { mutableStateOf("") }
    val collapsed = remember { mutableStateOf<Set<String>>(emptySet()) }
    var rulesExpanded by remember { mutableStateOf(false) }
    var clearMenuExpanded by remember { mutableStateOf(false) }
    var confirmClearReads by remember { mutableStateOf(false) }
    val groups = remember(entries) { entries.groupBy { it.volume }.toList() }
    val recentEntries = remember(entries) { entries.filter { it.isRecent }.take(5) }
    val readCount = remember(entries) { entries.count { it.isRead } }
    val chapterIndices = remember(entries) { entries.map { it.index } }

    // 确保当前章节所在分卷展开，避免用户打开目录找不到当前阅读位置
    val currentVolume = remember(entries, current) { entries.firstOrNull { it.index == current }?.volume }
    LaunchedEffect(currentVolume) {
        if (currentVolume != null && currentVolume in collapsed.value) {
            collapsed.value = collapsed.value - currentVolume
        }
    }

    val listState = tocListState
    // 精确计算当前章节在 LazyColumn 中的实际位置（排除已折叠分卷所占用的项数，彻底消除跳偏）
    val currentListPosition = remember(groups, current, recentEntries, collapsed.value) {
        var position = if (recentEntries.isEmpty()) 0 else 2
        run search@{
            groups.forEach { (volume, chapterEntries) ->
                position += 1 // volume header
                if (volume in collapsed.value) {
                    return@forEach
                }
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

    // READER-PAGES-v1：首次打开定位当前章节一次（宿主按 bookId 记忆）。
    // 附页复用宿主持久列表状态：再次打开时恢复上次位置，不强行滚回。
    LaunchedEffect(current, chapterIndices, autoLocateCurrent) {
        if (autoLocateCurrent && entries.any { it.index == current }) {
            listState.scrollToItem(currentListPosition.coerceAtLeast(0), scrollOffset = -200)
            onAutoLocated()
        }
    }

    // READER-PAGES-v1：顶栏「定位当前章节」——先清搜索过滤，再滚到当前章。
    var locateRequest by remember { mutableStateOf(0) }
    LaunchedEffect(locateRequest, searchQuery) {
        if (locateRequest > 0 && searchQuery.isBlank()) {
            // 过滤视图已随 searchQuery 清空退出组合，标准列表此刻已用 listState 组合完成。
            listState.scrollToItem(currentListPosition.coerceAtLeast(0), scrollOffset = -200)
        }
    }

    // 快速章节搜索过滤
    val filteredEntries = remember(entries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            val lower = q.lowercase()
            entries.filter { entry ->
                entry.title.lowercase().contains(lower) ||
                    (entry.index + 1).toString() == q ||
                    entry.index.toString() == q
            }
        }
    }

    // 浮动“回到当前章”微胶囊可见性（当读者划离当前章超过 6 项时优雅呈现）
    val showReturnToCurrent = remember(listState, currentListPosition, searchQuery) {
        derivedStateOf {
            searchQuery.isBlank() && abs(listState.firstVisibleItemIndex - currentListPosition) > 6
        }
    }

    if (confirmClearReads) {
        // G26：确认弹框去除 glassWindowBlur（反射整窗实时模糊），避免在目录 sheet 之上
        // 再叠一层模糊 Dialog 与翻页争 GPU；除模糊外与普通 AlertDialog 一致。
        AppAlertDialog(
            onDismissRequest = { confirmClearReads = false },
            title = { Text("清除已读标记", fontWeight = FontWeight.Bold) },
            text = { Text("确定清除本书全部章节的已读标记吗？此操作不会删除阅读进度、书签或笔记。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearReads = false
                        onClearReadMarks()
                    },
                ) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearReads = false }) { Text("取消") }
            },
        )
    }

    // READER-PAGES-v1：附页形态的「更多」菜单展开态。
    var pageMenuExpanded by remember { mutableStateOf(false) }

    ReaderSheetScaffold(
        title = if (pageStyle) bookTitle.ifBlank { "目录与标记" } else "目录与标记",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        fullScreen = pageStyle,
        onBack = if (pageStyle) onClose else null,
        trailing = if (pageStyle) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 顶栏「定位当前章节」：清空标题过滤后回到当前章（合同 P1）。
                    IconButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            searchQuery = ""
                            locateRequest++
                        },
                    ) {
                        Icon(Icons.Outlined.NearMe, contentDescription = "定位当前章节")
                    }
                    // 低频操作收敛到「更多」菜单：章节进度 / 已读统计 / 规则管理 / 清除已读标记。
                    Box {
                        IconButton(onClick = { pageMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "目录更多操作")
                        }
                        DropdownMenu(
                            expanded = pageMenuExpanded,
                            onDismissRequest = { pageMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("第 ${(current + 1).coerceAtLeast(1)} / ${totalChapters.coerceAtLeast(0)} 章")
                                },
                                onClick = {},
                                enabled = false,
                            )
                            if (showReadStatus) {
                                DropdownMenuItem(
                                    text = { Text("已读 $readCount/${entries.size}") },
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                            if (txtRules.isNotEmpty() || showReplacementRulesEntry) {
                                DropdownMenuItem(
                                    text = { Text(if (txtRules.isNotEmpty()) "目录与净化规则" else "替换净化") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        pageMenuExpanded = false
                                        onManageRules()
                                    },
                                )
                            }
                            if (showClearReadMarks) {
                                DropdownMenuItem(
                                    text = { Text("清除已读标记") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        pageMenuExpanded = false
                                        confirmClearReads = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 章节进度微胶囊
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                ) {
                    Text(
                        text = "${(current + 1).coerceAtLeast(1)} / ${totalChapters.coerceAtLeast(0)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }

                // 已读章节翡翠绿微胶囊
                if (showReadStatus) {
                    Surface(
                        shape = PillShape,
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        border = BorderStroke(spec.hairlineBorderWidth, Color(0xFF059669).copy(alpha = spec.hairlineAlpha)),
                    ) {
                        Text(
                            text = "已读 $readCount/${entries.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }

                // 清除已读标记下拉菜单
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
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
            }
        },
    ) {
        // 1. 顶层 Tab（目录 / 书签 / 笔记）圆润微胶囊导轨
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val tabs = listOf(
                    ReaderTocTab.TOC to "目录",
                    ReaderTocTab.BOOKMARKS to "书签",
                    ReaderTocTab.NOTES to "笔记",
                )
                tabs.forEach { (tab, label) ->
                    val isSelected = selectedTab == tab
                    val tabInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            selectedTab = tab
                        },
                        shape = PillShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isSelected) BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .bounceable(tabInteraction),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            ReaderTocTab.TOC -> {
                if (txtRules.isNotEmpty()) {
                    // TXT 规则识别引导栏：现代化墨青微岛卡片
                    // READER-PAGES-v1：附页形态下引导卡入「更多」菜单，识别区（扫描进度/
                    // 快捷规则胶囊）保留在页内——扫描进度必须持续可见。
                    if (!pageStyle) {
                        TocRulesEntryRow(onClick = onManageRules)
                    }
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
                } else if (showReplacementRulesEntry && !pageStyle) {
                    // EPUB 分页净化：结构保真投影可用时的「替换净化」入口
                    // （附页形态下同入口收敛到「更多」菜单）
                    TocRulesEntryRow(
                        onClick = onManageRules,
                        title = "替换净化",
                        subtitle = "管理正文替换净化规则",
                    )
                }

                if (entries.size > 1) {
                    // 章节快速过滤搜索框：长小说快速检索章节
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "快速搜索章节（如 50、终章）...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "清空搜索",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (entries.isEmpty()) {
                    FullEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        icon = { LineArtBook(sizeDp = 72.dp) },
                        title = "未发现目录",
                        body = "这本书暂未识别到章节结构，无法在此浏览。",
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (searchQuery.isNotBlank()) {
                            // 搜索过滤视图
                            if (filteredEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "未找到包含“$searchQuery”的章节",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    item("search_count_header") {
                                        Text(
                                            "匹配到 ${filteredEntries.size} 个章节",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                                        )
                                    }
                                    items(filteredEntries, key = { "search_${it.index}" }, contentType = { "toc_row" }) { entry ->
                                        TocRow(entry = entry, onPick = { onPick(entry.index) })
                                    }
                                }
                            }
                        } else {
                            // 标准目录分组视图
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                // 最近阅读章节微胶囊导轨
                                if (recentEntries.isNotEmpty()) {
                                    item("recent_title", contentType = "toc_recent_title") {
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            )
                                            Text(
                                                "最近浏览",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    item("recent_chapters", contentType = "toc_recent_row") {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(vertical = 2.dp),
                                        ) {
                                            items(recentEntries, key = { "recent_${it.index}" }) { entry ->
                                                val chipInteraction = remember { MutableInteractionSource() }
                                                Surface(
                                                    onClick = {
                                                        haptic(HapticFeedbackType.TextHandleMove)
                                                        onPick(entry.index)
                                                    },
                                                    shape = PillShape,
                                                    color = if (entry.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                    border = BorderStroke(
                                                        spec.borderWidth,
                                                        if (entry.isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                                                    ),
                                                    modifier = Modifier.bounceable(chipInteraction),
                                                ) {
                                                    Text(
                                                        entry.title,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 卷名作为清晰的结构分隔；章节使用安静的阅读清单，不再逐项堆叠卡片。
                                groups.forEach { (volume, chapterEntries) ->
                                    val isCollapsed = volume in collapsed.value
                                    item("volume_$volume", contentType = "volume_header") {
                                        Surface(
                                            onClick = {
                                                haptic(HapticFeedbackType.TextHandleMove)
                                                collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                                            },
                                            shape = RoundedCornerShape(spec.listItemRadius),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    volume,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
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
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            thickness = spec.hairlineBorderWidth,
                                        )
                                    }

                                    if (!isCollapsed) {
                                        items(chapterEntries, key = { "chapter_${it.index}" }, contentType = { "toc_row" }) { entry ->
                                            TocRow(entry = entry, onPick = { onPick(entry.index) })
                                        }
                                    }
                                }
                            }

                            // 浮动“回到当前章”微胶囊
                            if (showReturnToCurrent.value) {
                                Surface(
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        scope.launch {
                                            listState.animateScrollToItem(currentListPosition.coerceAtLeast(0), scrollOffset = -200)
                                        }
                                    },
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 20.dp, bottom = 16.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.NearMe,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = "当前章节",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ReaderTocTab.BOOKMARKS -> {
                BookmarksTabContent(
                    bookmarks = bookmarks,
                    onPickBookmark = onPickBookmark,
                    listState = bookmarkListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            ReaderTocTab.NOTES -> {
                NotesTabContent(
                    entries = notes,
                    onPick = onPickNote,
                    listState = noteListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
