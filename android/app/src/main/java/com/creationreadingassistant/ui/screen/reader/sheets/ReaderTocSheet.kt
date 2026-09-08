package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

internal enum class ReaderTocTab(val label: String) {
    TOC("目录"),
    BOOKMARKS("书签"),
    NOTES("笔记"),
}

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
    notes: List<NoteEntity> = emptyList(),
    onPickNote: (NoteEntity) -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val spec = LocalComponentSpec.current
    var selectedTab by remember { mutableStateOf(ReaderTocTab.TOC) }
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
        // G26：确认弹框去除 glassWindowBlur（反射整窗实时模糊），避免在目录 sheet 之上
        // 再叠一层模糊 Dialog 与翻页争 GPU；除模糊外与普通 AlertDialog 一致。
        AlertDialog(
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

    ReaderSheetScaffold(
        title = "目录与标记",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        trailing = {
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
                    ReaderTocTab.TOC to "目录 (${entries.size})",
                    ReaderTocTab.BOOKMARKS to "书签 (${bookmarks.size})",
                    ReaderTocTab.NOTES to "笔记 (${notes.size})",
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
                    TocRulesEntryRow(onClick = onManageRules)
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
                } else if (showReplacementRulesEntry) {
                    // EPUB 分页净化：结构保真投影可用时的「替换净化」入口
                    TocRulesEntryRow(
                        onClick = onManageRules,
                        title = "替换净化",
                        subtitle = "管理正文替换净化规则",
                    )
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 最近阅读章节微胶囊导轨
                        if (recentEntries.isNotEmpty()) {
                            item("recent_title") {
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
                            item("recent_chapters") {
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

                        // 按卷分组的章节独立微岛卡片列表
                        groups.forEach { (volume, chapterEntries) ->
                            val isCollapsed = volume in collapsed.value
                            item("volume_$volume", contentType = "volume_header") {
                                Surface(
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                                    },
                                    shape = RoundedCornerShape(spec.hintRadius),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            IconPedestal(
                                                icon = Icons.Outlined.AutoStories,
                                                tint = MaterialTheme.colorScheme.primary,
                                                size = 20.dp,
                                                iconSize = 12.dp,
                                            )
                                            Text(
                                                volume,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }

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

            ReaderTocTab.BOOKMARKS -> {
                if (bookmarks.isEmpty()) {
                    FullEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        icon = { LineArtBook(sizeDp = 72.dp) },
                        title = "暂无书签",
                        body = "阅读时点击菜单添加书签，即可在此快速跳转。",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                            BookmarkMicroIsland(
                                bookmark = bm,
                                onJump = { onPickBookmark(bm) },
                            )
                        }
                    }
                }
            }

            ReaderTocTab.NOTES -> {
                if (notes.isEmpty()) {
                    FullEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        icon = { LineArtBook(sizeDp = 72.dp) },
                        title = "暂无划线与笔记",
                        body = "阅读选中文本添加划线或思考，灵感随时沉淀。",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(notes, key = { "note_${it.id}" }) { note ->
                            NoteMicroIsland(
                                note = note,
                                onJump = { onPickNote(note) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代化墨青微岛卡片：目录与净化规则管理入口。
 */
@Composable
private fun TocRulesEntryRow(
    onClick: () -> Unit,
    title: String = "目录与净化规则",
    subtitle: String = "管理目录智能识别与正则净化",
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .bounceable(interaction)
            .semantics { contentDescription = "管理目录与净化规则" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 墨青微图标底座（微岛收敛：统一走 IconPedestal / pedestalRadius）
                IconPedestal(
                    icon = Icons.Outlined.Tune,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 28.dp,
                    iconSize = 16.dp,
                    contentDescription = "目录与净化规则",
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "进入规则管理",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 目录识别快捷区。
 */
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
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(spec.hintRadius))
                .clickable {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onToggleExpanded()
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("目录识别", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("仅在目录不准确时调整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "收起目录识别" else "展开目录识别",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (txtRuleScanStatus as? TxtRuleScanStatus.Running)?.let { running ->
            TxtScanProgressRow(status = running, onCancel = onCancelTxtScan)
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                txtRules.forEach { rule ->
                    val preview = txtRulePreviews[rule.id].orEmpty()
                    val suffix = txtRulePillSuffix(txtRuleScanStatus, rule.id, preview.size)
                    val label = if (suffix == null) rule.label else "${rule.label} · $suffix"
                    OptionPill(
                        selected = selectedTxtRule == rule.id,
                        label = label,
                        onClick = { onTxtRule(rule.id) },
                    )
                }
            }
        }
    }
}

/** 扫描中的进度 + 取消行。 */
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

/**
 * 目录章节列表项（TocItemRow / TocRow）：
 * - 独立微岛卡片设计，支持多级章节缩进视觉指示
 * - 当前正在阅读的章节采用 primary 浅底与高光微胶囊发丝描边
 * - 已读章节右侧展示翡翠绿微打勾胶囊或微指示点
 */
@Composable
internal fun TocRow(entry: ReaderTocEntry, onPick: () -> Unit) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onPick()
        },
        shape = RoundedCornerShape(spec.hintRadius),
        color = when {
            entry.isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            entry.isRead -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        },
        // 微岛收敛：三档描边宽度取令牌（当前章 = borderWidth 强调、已读/未读 = hairlineBorderWidth 发丝），
        // 但保留 0.35 / 0.25 / 0.18 的透明度分级——它是「当前 > 已读 > 未读」的语义强度阶梯，
        // 若一并压平到 hairlineAlpha 会让未读行也浮出边框，丢失层次。
        border = when {
            entry.isCurrent -> BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha))
            entry.isRead -> BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            else -> BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 章节序号微底座
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        when {
                            entry.isCurrent -> MaterialTheme.colorScheme.primary
                            entry.isRead -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        shape = RoundedCornerShape(spec.pedestalRadius),
                    )
                    .semantics { if (entry.isRead && !entry.isCurrent) contentDescription = "已读章节" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (entry.index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        entry.isCurrent -> MaterialTheme.colorScheme.onPrimary
                        entry.isRead -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.width(10.dp))

            // 章节标题
            Text(
                text = entry.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    entry.isCurrent -> MaterialTheme.colorScheme.primary
                    entry.isRead -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )

            // 右侧指示微胶囊：阅读中高光胶囊 / 已读翡翠绿对勾胶囊
            if (entry.isCurrent) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = PillShape,
                    modifier = Modifier.semantics { contentDescription = "当前章节" },
                ) {
                    Text(
                        text = "阅读中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (entry.isRead) {
                // 翡翠绿微打勾胶囊
                Surface(
                    shape = PillShape,
                    color = Color(0xFF059669).copy(alpha = 0.12f),
                    border = BorderStroke(spec.hairlineBorderWidth, Color(0xFF059669).copy(alpha = spec.hairlineAlpha)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "已读",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "已读",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 书签列表项：28dp 暖金微底座与「跳转」微胶囊。
 */
@Composable
private fun BookmarkMicroIsland(
    bookmark: NoteEntity,
    onJump: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onJump()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // 28dp 暖金微底座（微岛收敛：统一走 IconPedestal / pedestalRadius）
                IconPedestal(
                    icon = Icons.Outlined.Bookmark,
                    tint = Color(0xFFD97706),
                    size = 28.dp,
                    iconSize = 16.dp,
                )

                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = bookmark.title.ifBlank { "书签" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    bookmark.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 「跳转」微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "跳转",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 笔记列表项微岛卡片。
 */
@Composable
private fun NoteMicroIsland(
    note: NoteEntity,
    onJump: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onJump()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // 28dp 紫罗兰微底座（微岛收敛：统一走 IconPedestal / pedestalRadius）
                IconPedestal(
                    icon = Icons.Outlined.EditNote,
                    tint = Color(0xFF7C3AED),
                    size = 28.dp,
                    iconSize = 16.dp,
                )

                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = note.title.ifBlank { "笔记" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    note.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    note.body.takeIf { it.isNotBlank() }?.let { bodyText ->
                        Surface(
                            shape = RoundedCornerShape(spec.pedestalRadius),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        ) {
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }
                }
            }

            // 「跳转」微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.secondary.copy(alpha = spec.hairlineAlpha)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "跳转",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
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
