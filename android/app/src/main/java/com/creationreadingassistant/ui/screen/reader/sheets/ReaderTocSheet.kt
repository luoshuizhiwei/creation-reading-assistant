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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape

internal data class ReaderTocEntry(
    val index: Int,
    val title: String,
    val volume: String,
    val isCurrent: Boolean,
    val isRecent: Boolean,
)

internal fun readerTocEntries(
    titles: List<String>,
    current: Int,
    recent: List<Int>,
): List<ReaderTocEntry> {
    var volume = "正文"
    return titles.mapIndexedNotNull { index, title ->
        if (isVolumeHeader(title)) {
            volume = title
            null
        } else {
            ReaderTocEntry(index, title.ifBlank { "未命名章节" }, volume, index == current, index in recent)
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
    onTxtRule: (String) -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val collapsed = remember { mutableStateOf<Set<String>>(emptySet()) }
    var rulesExpanded by remember { mutableStateOf(false) }
    val groups = remember(entries) { entries.groupBy { it.volume }.toList() }
    val recentEntries = remember(entries) { entries.filter { it.isRecent }.take(5) }
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
    LaunchedEffect(current, entries) {
        if (entries.any { it.index == current }) listState.scrollToItem(currentListPosition.coerceAtLeast(0))
    }

    ReaderSheetScaffold(
        title = "目录",
        trailing = {
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = "${(current + 1).coerceAtLeast(1)} / ${totalChapters.coerceAtLeast(0)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        },
    ) {
        if (entries.isEmpty()) {
            FullEmptyState(
                modifier = Modifier.fillMaxWidth().weight(1f),
                icon = { LineArtBook(sizeDp = 72.dp) },
                title = "未发现目录",
                body = "这本书暂未识别到章节结构，无法在此浏览。",
            )
            return@ReaderSheetScaffold
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
                item("volume_$volume") {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(volume, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chapterEntries.size}章", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                            contentDescription = if (isCollapsed) "展开" else "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!isCollapsed) {
                    items(chapterEntries, key = { "chapter_${it.index}" }) { entry ->
                        TocRow(entry = entry, onPick = { onPick(entry.index) })
                    }
                }
            }
            if (txtRules.isNotEmpty()) {
                item("txt_rules") {
                    Column(Modifier.fillMaxWidth().padding(top = layout.relatedGap)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { rulesExpanded = !rulesExpanded }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("目录识别", style = MaterialTheme.typography.titleSmall)
                                Text("仅在目录不准确时调整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                if (rulesExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (rulesExpanded) "收起目录识别" else "展开目录识别",
                            )
                        }
                        if (rulesExpanded) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                txtRules.forEach { rule ->
                                    val preview = txtRulePreviews[rule.id].orEmpty()
                                    OptionPill(
                                        selected = selectedTxtRule == rule.id,
                                        label = "${rule.label} · ${preview.size}章",
                                        onClick = { onTxtRule(rule.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TocRow(entry: ReaderTocEntry, onPick: () -> Unit) {
    val selectedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LocalComponentSpec.current.listItemShape)
            .background(if (entry.isCurrent) selectedColor else MaterialTheme.colorScheme.background)
            .clickable(onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).fillMaxHeight().background(
                if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
            ),
        )
        Text(
            text = (entry.index + 1).toString().padStart(2, '0'),
            modifier = Modifier.width(48.dp).padding(start = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (entry.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (entry.isCurrent) {
            Text("当前", modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
