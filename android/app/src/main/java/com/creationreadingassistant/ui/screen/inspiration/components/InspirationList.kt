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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
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
import com.creationreadingassistant.ui.components.IslandCard
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
    val spec = LocalComponentSpec.current
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
        // 1. 类型筛选导轨 + 排序按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.pageHorizontal,
                    vertical = 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                        text = if (count > 0) "$label $count" else label,
                        selected = typeFilter == value,
                        onClick = { onAction(InspirationAction.UpdateTypeFilter(value)) },
                    )
                }
            }
            Surface(
                onClick = { onAction(InspirationAction.OpenSortSheet) },
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    spec.hairlineBorderWidth,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        // 2. 状态筛选行：当有状态项时平滑展示在独立导轨中
        if (availableStatuses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontal, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectablePill(
                    text = "全部状态",
                    selected = statusFilter == "all",
                    onClick = { onAction(InspirationAction.UpdateStatusFilter("all")) },
                )
                availableStatuses.forEach { (value, label) ->
                    val count = statusCounts[value] ?: 0
                    SelectablePill(
                        text = if (count > 0) "$label $count" else label,
                        selected = statusFilter == value,
                        onClick = { onAction(InspirationAction.UpdateStatusFilter(value)) },
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
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        )
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp),
                        )
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(2.dp)) }
                itemsIndexed(filtered, key = { _, item -> item.id }) { index, item ->
                    InspirationRecordCard(
                        item = item,
                        source = sourceOf(item),
                        tags = tagsOf(item),
                        onOpen = { onAction(InspirationAction.OpenDetail(item.id)) },
                        onMore = { onAction(InspirationAction.OpenItemActions(item.id)) },
                        entranceDelay = index * 40,
                    )
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
    val accentColor = when (item.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> Color(0xFF2563EB)
    }

    IslandCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(delayMillis = entranceDelay, reducedMotion = rememberReducedMotion())
            .testTag("inspiration-card-${item.id}"),
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 顶行：类型微胶囊 + 更新时间 + 更多操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = PillShape,
                    border = BorderStroke(spec.hairlineBorderWidth, accentColor.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(PillShape)
                                .background(accentColor),
                        )
                        Text(
                            getTypeLabel(item.type),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor,
                        )
                    }
                }
                Text(
                    formatListTime(item.updated_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 标题
            Text(
                item.title.ifBlank { "未命名灵感" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 正文或摘录摘要
            val summary = item.body.ifBlank { source?.excerpt ?: "还没有正文" }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            // 来源书籍微卡片
            val hasSourceLine =
                !source?.bookTitle.isNullOrBlank() ||
                    !source?.locationLabel.isNullOrBlank() ||
                    !source?.chapterTitle.isNullOrBlank()
            if (hasSourceLine) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(spec.hintRadius),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        val locLabel = source?.locationLabel ?: source?.chapterTitle
                        Text(
                            "来源：${source?.bookTitle?.let { "《$it》" } ?: "阅读足迹"}${locLabel?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 标签列表
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.take(4).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = PillShape,
                        ) {
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
