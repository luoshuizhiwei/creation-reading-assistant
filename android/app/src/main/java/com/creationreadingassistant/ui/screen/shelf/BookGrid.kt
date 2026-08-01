package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.pullrefresh.PullRefreshDefaults
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.progressFor
import com.creationreadingassistant.ui.screen.readiness
import com.creationreadingassistant.ui.screen.ReadinessTone
import com.creationreadingassistant.ui.screen.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.ShelfViewMode
import com.creationreadingassistant.ui.screen.toneColor
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ProgressBarShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem

// ===================== 多选栏 =====================
@Composable
internal fun SelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $selectedCount 本", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll, enabled = visibleCount > 0) { Text(if (visibleCount > 0) "全选($visibleCount)" else "全选") }
            TextButton(onClick = onClear, enabled = selectedCount > 0) { Text("清空") }
        }
    }
}

// ===================== 状态筛选条（对齐 web .shelf-status-rail） =====================
@Composable
internal fun StatusRail(
    statusFilter: ShelfStatusFilter,
    onSelect: (ShelfStatusFilter) -> Unit,
) {
    val options = listOf(
        ShelfStatusFilter.ALL to "全部",
        ShelfStatusFilter.READING to "在读",
        ShelfStatusFilter.COMPLETED to "已完成",
        ShelfStatusFilter.UNREAD to "未开始",
        ShelfStatusFilter.READABLE to "本机可读",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            options.forEach { (value, label) ->
                val active = statusFilter == value
                SelectablePill(text = label, selected = active, onClick = { onSelect(value) })
            }
    }
}

// ===================== 书封网格 / 列表 =====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGrid(
    books: List<ShelfBookItem>,
    progressById: Map<String, ReadingProgressEntity>,
    viewMode: ShelfViewMode,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    actionBookId: String?,
    downloadingIds: Set<String>,
    onOpenBook: (BookEntity) -> Unit,
    onToggleActions: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    if (viewMode == ShelfViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontal),
            contentPadding = PaddingValues(vertical = layout.relatedGap),
            verticalArrangement = Arrangement.spacedBy(layout.gridGap),
            horizontalArrangement = Arrangement.spacedBy(layout.gridGap),
        ) {
            items(
                items = books,
                key = { it.bookId },
                contentType = { viewMode },
            ) { item ->
                val book = item.book
                BookTile(
                    book = book,
                    percent = progressFor(progressById, book.id),
                    viewMode = ShelfViewMode.GRID,
                    selectionMode = selectionMode,
                    selected = selectedIds.contains(book.id),
                    actionsOpen = actionBookId == book.id,
                    downloading = downloadingIds.contains(book.id),
                    onOpenBook = onOpenBook,
                    onToggleActions = onToggleActions,
                    onToggleSelected = onToggleSelected,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontal),
            contentPadding = PaddingValues(vertical = layout.relatedGap),
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            items(
                items = books,
                key = { it.bookId },
                contentType = { viewMode },
            ) { item ->
                val book = item.book
                BookTile(
                    book = book,
                    percent = progressFor(progressById, book.id),
                    viewMode = ShelfViewMode.LIST,
                    selectionMode = selectionMode,
                    selected = selectedIds.contains(book.id),
                    actionsOpen = actionBookId == book.id,
                    downloading = downloadingIds.contains(book.id),
                    onOpenBook = onOpenBook,
                    onToggleActions = onToggleActions,
                    onToggleSelected = onToggleSelected,
                )
            }
        }
    }
}

// ===================== B2：下拉刷新指示器（克制细弧 + 墨线文字，非默认 spinner）=====================
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ShelfRefreshIndicator(
    state: PullRefreshState,
    refreshing: Boolean,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val color = MaterialTheme.colorScheme.primary
    val visible = refreshing || state.progress > 0.01f
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            val useIndeterminate = !reducedMotion && refreshing
            if (useIndeterminate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            } else {
                val p = if (reducedMotion) 1f else state.progress.coerceIn(0f, 1f)
                CircularProgressIndicator(
                    progress = { p },
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (refreshing) "刷新中" else "下拉刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookTile(
    book: BookEntity,
    percent: Float,
    viewMode: ShelfViewMode,
    selectionMode: Boolean,
    selected: Boolean,
    actionsOpen: Boolean,
    downloading: Boolean,
    onOpenBook: (BookEntity) -> Unit,
    onToggleActions: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readiness = book.readiness()
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val haptic = rememberHaptic(rememberReducedMotion())
    val onClick = {
        if (selectionMode) {
            haptic(HapticFeedbackType.TextHandleMove)
            onToggleSelected(book.id)
        } else onOpenBook(book)
    }
    val tileModifier = modifier
        .fillMaxWidth()
        .semantics { contentDescription = "打开书籍" }
        .combinedClickable(
            onClick = onClick,
            onLongClick = {
                if (!selectionMode) {
                    haptic(HapticFeedbackType.LongPress)
                    onToggleActions(book.id)
                }
            },
        )

    if (viewMode == ShelfViewMode.GRID) {
        Column(
            modifier = tileModifier,
            verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(spec.cardShape),
            ) {
                BookCover(book = book, percent = percent, modifier = Modifier.fillMaxSize(), sealSize = 30.dp, fallback = { ShelfCoverFallback(book) })
                if (downloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(PillShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = { onToggleActions(book.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(layout.minimumTouchTarget),
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "管理《${book.title}》",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val label = if (downloading) {
                "下载中…"
            } else if (readiness.tone == ReadinessTone.READY) {
                "${percent.toInt()}%"
            } else {
                readiness.label
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = toneColor(readiness.tone),
                    maxLines = 1,
                )
                ProgressLine(percent = percent, modifier = Modifier.weight(1f))
            }
        }
    } else {
        SectionCard(
            modifier = tileModifier,
            contentPadding = layout.compactCardPadding,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    BookCover(book = book, percent = percent, modifier = Modifier.size(56.dp, 78.dp), fallback = { ShelfCoverFallback(book) })
                    if (downloading) {
                        Box(
                            modifier = Modifier
                                .size(56.dp, 78.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(book.author ?: "作者未知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressLine(percent = percent)
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(PillShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(onClick = { onToggleActions(book.id) }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "管理《${book.title}》", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShelfCoverFallback(book: BookEntity) {
    val scheme = MaterialTheme.colorScheme
    // 无封面回退：取主题 surface 容器色做柔和渐变（永不用土黄/羊皮纸色），与主题一致
    val coverFallback = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to scheme.surfaceContainerHigh,
            1.0f to scheme.surfaceVariant,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(coverFallback)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(book.title.take(4), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Text(book.format.uppercase(), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ProgressLine(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val p = percent.coerceIn(0f, 100f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(ProgressBarShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p / 100f)
                .height(4.dp)
                .clip(ProgressBarShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
