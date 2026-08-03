package com.creationreadingassistant.ui.screen.readinghistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
            modifier = Modifier.testTag("my-reading-list"),
        ) {
            item(key = "filters") {
                ReadingFilterRail(state = state, onAction = onAction)
            }
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(MyReadingAction.UpdateQuery(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("my-reading-search"),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("搜索 ${state.counts[MyReadingFilter.ALL] ?: 0} 本书") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
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
            state.months.forEachIndexed { index, month ->
                val showYear = index == 0 || state.months[index - 1].year != month.year
                if (showYear) {
                    item(key = "year-${month.year}") {
                        Text(
                            "${month.year}年  ${state.months.filter { it.year == month.year }.sumOf { it.items.size }}",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = if (index == 0) 4.dp else 12.dp),
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
private fun ReadingFilterRail(state: MyReadingUiState, onAction: (MyReadingAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MyReadingFilter.entries.forEach { filter ->
            val selected = state.filter == filter
            Surface(
                onClick = { onAction(MyReadingAction.SelectFilter(filter)) },
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    "${filter.label} ${state.counts[filter] ?: 0}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: Int, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(9.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
        }
        Text("${month}月", style = MaterialTheme.typography.titleLarge)
        Text("  $count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineBookRow(item: MyReadingItem, onOpen: () -> Unit, onManage: () -> Unit) {
    val layout = LocalLayoutTokens.current
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(28.dp).fillMaxHeight()) {
            Surface(
                modifier = Modifier.width(1.dp).fillMaxHeight().align(Alignment.Center),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}
            Surface(
                modifier = Modifier.size(7.dp).align(Alignment.TopCenter).offset(y = 20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.outline,
            ) {}
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .testTag("reading-item-${item.book.id}"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(layout.cardPadding),
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        progressLabel(item),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (item.state == ReadingCompletionState.SHELVED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(item.book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        itemMeta(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BookCover(
                    book = item.book,
                    modifier = Modifier.size(58.dp, 82.dp),
                    percent = null,
                    fallback = {
                        Text(
                            item.book.title.take(3),
                            modifier = Modifier.align(Alignment.Center).padding(6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "管理《${item.book.title}》")
                }
            }
        }
    }
}

private fun progressLabel(item: MyReadingItem): String = when (item.state) {
    ReadingCompletionState.SHELVED -> "已搁置 · ${formatPercent(item.progress?.progress_percent ?: 0f)}"
    ReadingCompletionState.FINISHED -> "已读完"
    ReadingCompletionState.READING -> "读到 ${formatPercent(item.progress?.progress_percent ?: 0f)}"
}

private fun itemMeta(item: MyReadingItem): String {
    val started = Instant.ofEpochMilli(item.startedAtMs.coerceAtLeast(0L)).atZone(ZoneId.systemDefault())
    return "${started.year}年${started.monthValue}月开始阅读 · ${formatReadingTime(item.readingTimeMs)}"
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
