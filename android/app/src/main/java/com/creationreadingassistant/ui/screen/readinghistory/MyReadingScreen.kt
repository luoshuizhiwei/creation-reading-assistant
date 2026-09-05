package com.creationreadingassistant.ui.screen.readinghistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.viewmodel.MyReadingFilter
import com.creationreadingassistant.ui.viewmodel.MyReadingItem
import com.creationreadingassistant.ui.viewmodel.MyReadingUiState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun MyReadingScreen(
    state: MyReadingUiState,
    onAction: (MyReadingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    AppScreenScaffold(
        modifier = modifier.testTag("my-reading-screen"),
        title = "我的阅读",
        navigationIcon = {
            IconButton(onClick = { onAction(MyReadingAction.Back) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
            modifier = Modifier.testTag("my-reading-list"),
        ) {
            // 1. 顶层时光足迹总览微岛（未搜索且有内容时展示）
            if (state.query.isBlank() && state.months.isNotEmpty()) {
                item(key = "overview") {
                    ReadingFootprintOverview(state = state)
                }
            }

            // 2. 分类筛选胶囊导轨
            item(key = "filters") {
                ReadingFilterRail(state = state, onAction = onAction)
            }

            // 3. 搜索栏（14dp 弧度，带快速清除键）
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(MyReadingAction.UpdateQuery(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my-reading-search"),
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onAction(MyReadingAction.UpdateQuery("")) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    placeholder = { Text("搜索 ${state.counts[MyReadingFilter.ALL] ?: 0} 本书") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                )
            }

            // 4. 空状态提示
            if (state.isReady && state.months.isEmpty()) {
                item(key = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = if (state.query.isBlank()) "还没有符合条件的阅读记录" else "没有找到匹配的书",
                        body = if (state.query.isBlank()) "开始阅读后，进度、时长和状态会按月份整理在这里。" else "换个书名或作者试试。",
                        contentPadding = 28.dp,
                    )
                }
            }

            // 5. 按年与月份分组的时光流
            state.months.forEachIndexed { index, month ->
                val showYear = index == 0 || state.months[index - 1].year != month.year
                if (showYear) {
                    val yearTotal = state.months.filter { it.year == month.year }.sumOf { it.items.size }
                    item(key = "year-${month.year}") {
                        TimelineYearHeader(
                            year = month.year,
                            totalBooks = yearTotal,
                            isFirst = index == 0,
                        )
                    }
                }
                item(key = "month-${month.year}-${month.month}") {
                    MonthHeader(month = month.month, count = month.items.size)
                }
                month.items.forEach { item ->
                    item(key = "reading-${item.book.id}") {
                        TimelineBookRow(
                            item = item,
                            onOpen = { onAction(MyReadingAction.OpenBook(item.book)) },
                            onManage = { onAction(MyReadingAction.ManageBook(item.book)) },
                        )
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(layout.pageVertical)) }
        }
    }
}

@Composable
private fun ReadingFootprintOverview(
    state: MyReadingUiState,
    modifier: Modifier = Modifier,
) {
    val totalBooks = state.counts[MyReadingFilter.ALL] ?: 0
    val readingBooks = state.counts[MyReadingFilter.READING] ?: 0
    val finishedBooks = state.counts[MyReadingFilter.FINISHED] ?: 0
    val totalReadingMs = remember(state.months) {
        state.months.sumOf { month -> month.items.sumOf { it.readingTimeMs } }
    }

    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "阅读时光足迹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "累计记录 $totalBooks 本藏书历程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        formatReadingTime(totalReadingMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            FootprintMetricItem("在读", "$readingBooks 本", MaterialTheme.colorScheme.primary)
            FootprintMetricItem("已读完", "$finishedBooks 本", Color(0xFF10B981))
            FootprintMetricItem("已搁置", "${state.counts[MyReadingFilter.SHELVED] ?: 0} 本", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun FootprintMetricItem(
    label: String,
    value: String,
    accentColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = accentColor,
            ) {}
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReadingFilterRail(state: MyReadingUiState, onAction: (MyReadingAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MyReadingFilter.entries.forEach { filter ->
            val selected = state.filter == filter
            val count = state.counts[filter] ?: 0
            Surface(
                onClick = { onAction(MyReadingAction.SelectFilter(filter)) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        filter.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineYearHeader(year: Int, totalBooks: Int, isFirst: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 4.dp else 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${year} 年",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            "该年共读 $totalBooks 本",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun MonthHeader(month: Int, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
        Text(
            "${month} 月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                "$count 本",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineBookRow(
    item: MyReadingItem,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // 左侧时光导轨与发光状态节点
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
        ) {
            Surface(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ) {}
            val haloColor = when (item.state) {
                ReadingCompletionState.READING -> MaterialTheme.colorScheme.primary
                ReadingCompletionState.FINISHED -> Color(0xFF10B981)
                ReadingCompletionState.SHELVED -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = haloColor.copy(alpha = 0.2f),
                ) {}
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = haloColor,
                ) {}
            }
        }

        // 右侧阅读足迹卡片（消费统一 SectionCard）
        SectionCard(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .testTag("reading-item-${item.book.id}"),
            contentPadding = 12.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(
                    book = item.book,
                    modifier = Modifier
                        .size(54.dp, 76.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    percent = null,
                    fallback = {
                        Text(
                            item.book.title.take(3),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.book.author?.ifBlank { null } ?: "未知作者",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = item.book.format.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReadingStateBadge(item)
                        Text(
                            text = formatReadingTime(item.readingTimeMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(
                    onClick = onManage,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "管理《${item.book.title}》",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingStateBadge(item: MyReadingItem) {
    val (bg, fg, label) = when (item.state) {
        ReadingCompletionState.READING -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            "读到 ${formatPercent(item.progress?.progress_percent ?: 0f)}",
        )
        ReadingCompletionState.FINISHED -> Triple(
            Color(0xFF10B981).copy(alpha = 0.15f),
            Color(0xFF047857),
            "已读完",
        )
        ReadingCompletionState.SHELVED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "已搁置 · ${formatPercent(item.progress?.progress_percent ?: 0f)}",
        )
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatPercent(value: Float): String {
    val normalized = value.coerceIn(0f, 100f)
    return if (normalized >= 10f || normalized == 0f) "${normalized.roundToInt()}%" else "${"%.1f".format(normalized)}%"
}

private fun formatReadingTime(value: Long): String {
    val minutes = (value.coerceAtLeast(0L) / 60_000.0).roundToInt()
    if (minutes <= 0) return "不足 1 分钟"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours} 小时 ${rest} 分钟" else "$minutes 分钟"
}
