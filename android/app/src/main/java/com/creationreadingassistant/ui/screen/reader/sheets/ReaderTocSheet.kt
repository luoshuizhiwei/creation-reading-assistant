package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
// 只依赖标题，不依赖 EpubChapter —— TXT 现在也有章节（TxtChapterDetector），
// 目录必须能同时服务两种格式。
internal fun TocSheet(
    titles: List<String>,
    current: Int,
    recent: List<Int>,
    onPick: (Int) -> Unit,
    txtRules: List<TxtChapterDetector.Rule> = emptyList(),
    selectedTxtRule: String = "builtin",
    txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>> = emptyMap(),
    onTxtRule: (String) -> Unit = {},
) {
    val collapsed = remember { mutableStateOf<Set<String>>(emptySet()) }
    val groups = remember(titles) { groupChaptersByVolume(titles) }
    val reducedMotion = rememberReducedMotion()
    Column(Modifier.fillMaxWidth().padding(LocalLayoutTokens.current.cardPadding)) {
        Text("目录", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        if (titles.isEmpty()) {
            Text("这本书暂未识别到目录。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        } else {
            AnimatedVisibility(
                visible = true,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(durationMillis = 220)) + slideInVertically(initialOffsetY = { it / 10 })
                },
                exit = ExitTransition.None,
            ) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
                // R5：最近浏览章节置顶
                if (recent.isNotEmpty()) {
                    item {
                        Text(
                            "最近浏览",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(recent) { i ->
                        val t = titles.getOrNull(i) ?: return@items
                        TocRow(i, t, i == current) { onPick(i) }
                    }
                    item { SectionDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                }
                // R5：按「卷/部」分组，可折叠
                groups.forEach { (volume, idxs) ->
                    val isCollapsed = collapsed.value.contains(volume)
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    collapsed.value = if (isCollapsed) collapsed.value - volume else collapsed.value + volume
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Text(volume, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (!isCollapsed) {
                        items(idxs) { i ->
                            TocRow(i, titles[i], i == current) { onPick(i) }
                        }
                    }
                }
            }
            }
        }
        if (txtRules.isNotEmpty()) {
            SectionDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            Text("目录不对？换一套识别规则", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                txtRules.forEach { rule ->
                    val preview = txtRulePreviews[rule.id].orEmpty()
                    val hint = preview.take(3).joinToString(" / ") { it.title }.take(42)
                    Column {
                        OptionPill(
                            selected = selectedTxtRule == rule.id,
                            label = "${rule.label} · ${preview.size} 章",
                            onClick = { onTxtRule(rule.id) },
                        )
                        if (selectedTxtRule == rule.id && hint.isNotBlank()) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TocRow(index: Int, title: String, isCurrent: Boolean, onPick: () -> Unit) {
    SettingRow(
        title = title,
        leading = {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        },
        trailing = if (isCurrent) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        onClick = onPick,
    )
}

/** R5：按「卷/部」标题聚合章节；检测不到卷时归入「正文」。 */
internal fun groupChaptersByVolume(titles: List<String>): List<Pair<String, List<Int>>> {
    val result = mutableListOf<Pair<String, MutableList<Int>>>()
    for ((i, title) in titles.withIndex()) {
        if (isVolumeHeader(title)) {
            result.add(title to mutableListOf())
        } else {
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
