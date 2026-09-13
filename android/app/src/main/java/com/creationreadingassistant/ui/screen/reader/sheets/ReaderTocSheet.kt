package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                        verticalArrangement = Arrangement.spacedBy(0.dp),
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
                }
            }

            ReaderTocTab.BOOKMARKS -> {
                BookmarksTabContent(
                    bookmarks = bookmarks,
                    onPickBookmark = onPickBookmark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            ReaderTocTab.NOTES -> {
                NotesTabContent(
                    notes = notes,
                    onPickNote = onPickNote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
