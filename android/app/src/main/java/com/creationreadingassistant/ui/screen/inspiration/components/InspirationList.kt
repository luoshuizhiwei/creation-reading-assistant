package com.creationreadingassistant.ui.screen.inspiration.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Tune
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.inspiration.InspirationAction
import com.creationreadingassistant.ui.screen.inspiration.SORT_OPTIONS
import com.creationreadingassistant.ui.screen.inspiration.TYPE_OPTIONS
import com.creationreadingassistant.ui.screen.inspiration.formatListTime
import com.creationreadingassistant.ui.screen.inspiration.getTypeLabel
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel

/**
 * 灵感列表页内容。不创建 Scaffold，不创建顶栏。
 * 只负责：类型筛选 + 排序按钮 + （空状态 / 卡片列表）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun InspirationList(
    items: List<InspirationEntity>,
    filtered: List<InspirationEntity>,
    typeFilter: String,
    availableTypes: List<Pair<String, String>>,
    typeCounts: Map<String, Int>,
    statusFilter: String = "all",
    availableStatuses: List<Pair<String, String>> = emptyList(),
    statusCounts: Map<String, Int> = emptyMap(),
    sortMode: String,
    showSkeleton: Boolean,
    sourceOf: (InspirationEntity) -> InspirationSourceInfo?,
    tagsOf: (InspirationEntity) -> List<String>,
    onAction: (InspirationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()

    val hasAny = items.isNotEmpty()
    val hasSearch = run {
        var q by remember { mutableStateOf("") }
        false
    }
    val needle = run { "" }
    val hasFilter = typeFilter != "all" || statusFilter != "all"
    val emptyKind: String? = if (filtered.isEmpty()) {
        when {
            !hasAny -> "empty"
            hasFilter -> "filter"
            else -> "search"
        }
    } else null

    Column(modifier = modifier.fillMaxSize().testTag("inspiration-list")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.pageHorizontal,
                    vertical = layout.relatedGap,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SelectablePill(
                    text = "全部",
                    selected = typeFilter == "all" && statusFilter == "all",
                    onClick = {
                        onAction(InspirationAction.UpdateTypeFilter("all"))
                        onAction(InspirationAction.UpdateStatusFilter("all"))
                    },
                )
                availableTypes.forEach { (value, label) ->
                    val count = typeCounts[value] ?: 0
                    SelectablePill(
                        text = "$label $count",
                        selected = typeFilter == value,
                        onClick = { onAction(InspirationAction.UpdateTypeFilter(value)) },
                    )
                }
            }
            // 状态筛选行（第二行）：按 STATUS_OPTIONS 中实际存在的状态过滤
            if (availableStatuses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.pageHorizontal)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SelectablePill(
                        text = "全部状态",
                        selected = statusFilter == "all",
                        onClick = { onAction(InspirationAction.UpdateStatusFilter("all")) },
                    )
                    availableStatuses.forEach { (value, label) ->
                        SelectablePill(
                            text = "$label ${statusCounts[value] ?: 0}",
                            selected = statusFilter == value,
                            onClick = { onAction(InspirationAction.UpdateStatusFilter(value)) },
                        )
                    }
                }
            }
            Surface(
                onClick = { onAction(InspirationAction.OpenSortSheet) },
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(AppIconSize.Compact),
                    )
                    Text(
                        SORT_OPTIONS.firstOrNull { it.first == sortMode }?.second ?: "最近更新",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (showSkeleton) {
            ListSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
                count = 5,
                reducedMotion = reducedMotion,
            )
        } else if (emptyKind != null) {
            val (title, body) = when (emptyKind) {
                "search" -> "没有搜索结果" to "试试更短的关键词，或清除搜索。"
                "filter" -> "这个类型还没有内容" to "切换到全部，或新建一条灵感。"
                else -> "还没有灵感" to "记录设定、摘录或创作片段。"
            }
            FullEmptyState(
                icon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        LineArtBookmark()
                    }
                },
                title = title,
                body = body,
                contentPadding = 32.dp,
                primaryAction = if (emptyKind == "empty") {
                    "新建灵感" to { onAction(InspirationAction.OpenEditor(null)) }
                } else null,
                secondaryAction = if (emptyKind != "empty") {
                    "查看全部" to { onAction(InspirationAction.ResetFilter) }
                } else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = layout.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(filtered, key = { _, item -> item.id }) { index, item ->
                    InspirationRecordCard(
                        item = item,
                        source = sourceOf(item),
                        tags = tagsOf(item),
                        onOpen = { onAction(InspirationAction.OpenDetail(item.id)) },
                        onMore = { onAction(InspirationAction.OpenItemActions(item.id)) },
                        entranceDelay = index * 40,
                    )
                    if (index != filtered.lastIndex) SectionDivider()
                }
                item { Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
            }
        }
    }
}

@Composable
private fun InspirationRecordCard(
    item: InspirationEntity,
    source: InspirationSourceInfo?,
    tags: List<String>,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    entranceDelay: Int = 0,
) {
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = LocalLayoutTokens.current.contentGap)
            .animateEnter(delayMillis = entranceDelay, reducedMotion = rememberReducedMotion())
            .testTag("inspiration-card-${item.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = PillShape,
            ) {
                Text(
                    getTypeLabel(item.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                formatListTime(item.updated_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            IconButton(
                onClick = onMore,
                modifier = Modifier.size(LocalLayoutTokens.current.minimumTouchTarget),
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title.ifBlank { "未命名灵感" },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.24).sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val summary = item.body.ifBlank { source?.excerpt ?: "还没有正文" }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        val hasSourceLine =
            !source?.bookTitle.isNullOrBlank() ||
                !source?.locationLabel.isNullOrBlank() ||
                !source?.chapterTitle.isNullOrBlank()
        if (hasSourceLine) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Book,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                val locLabel = source?.locationLabel ?: source?.chapterTitle
                Text(
                    "来源：${source?.bookTitle?.let { "《$it》" } ?: "阅读记录"}${locLabel?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                tags.take(3).forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = spec.pillShape,
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
