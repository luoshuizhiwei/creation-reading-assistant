package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.MutedCoverFallback
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 书架网格封面微书脊立体阴影画笔（顶级常量，零 GC 分配）。
 */
private val GridBookTileSpineShadowBrush = Brush.horizontalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.22f),
        Color.Black.copy(alpha = 0.08f),
        Color.Transparent,
    ),
)

/**
 * 书架列表封面微书脊立体阴影画笔（顶级常量，零 GC 分配）。
 */
private val ListBookTileSpineShadowBrush = Brush.horizontalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.20f),
        Color.Black.copy(alpha = 0.05f),
        Color.Transparent,
    ),
)

// ===================== 多选栏 =====================
@Composable
internal fun SelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 28dp 微彩图标底座
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "已选 $selectedCount 本",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSelectAll()
                },
                enabled = visibleCount > 0,
            ) {
                Text(if (visibleCount > 0) "全选 ($visibleCount)" else "全选")
            }
            TextButton(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClear()
                },
                enabled = selectedCount > 0,
            ) {
                Text("清空")
            }
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
            horizontalAlignment = Alignment.CenterHorizontally,
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
    val layout = LocalLayoutTokens.current
    val haptic = rememberHaptic(rememberReducedMotion())
    val onClick = {
        if (selectionMode) {
            haptic(HapticFeedbackType.TextHandleMove)
            onToggleSelected(book.id)
        } else {
            onOpenBook(book)
        }
    }
    val tileModifier = modifier
        .fillMaxWidth()
        .testTag("book-tile-${book.id}")
        .semantics { contentDescription = "打开书籍" }
        .combinedClickable(
            onClick = onClick,
            onLongClickLabel = "打开书籍操作",
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
            // 书籍封面微岛：8dp 圆角、微边框、微书脊立体阴影、多选态流体边框
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (selectionMode && selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
                },
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    BookCover(
                        book = book,
                        percent = percent,
                        modifier = Modifier.fillMaxSize(),
                        sealSize = 30.dp,
                        fallback = { ShelfCoverFallback(book, isCompact = false) },
                    )

                    // 微书脊立体阴影（书脊厚度暗部 + 装订折线反光）
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(GridBookTileSpineShadowBrush),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(0.6.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.18f)),
                    )

                    // 下载中遮罩
                    if (downloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 多选选择态高质感徽章
                    if (selectionMode) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(24.dp)
                                .align(Alignment.TopStart),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // 书名
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 进度指示器与微胶囊
            if (downloading) {
                Text(
                    text = "下载中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else if (readiness.tone == ReadinessTone.READY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 百分比微胶囊标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    ) {
                        Text(
                            text = if (percent >= 99.5f) "完" else "${percent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    ProgressLine(percent = percent, modifier = Modifier.weight(1f))
                }
            } else {
                val tone = toneColor(readiness.tone)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tone.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, tone.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = readiness.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tone,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    } else {
        // 现代纸墨微岛高密度列表项
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = tileModifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. 左侧小封面（48dp x 66dp，圆角 8dp，微书脊立体阴影）
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selectionMode && selected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
                    },
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(width = 48.dp, height = 66.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BookCover(
                            book = book,
                            percent = percent,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp),
                            fallback = { ShelfCoverFallback(book, isCompact = true) },
                        )
                        // 微书脊阴影
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(ListBookTileSpineShadowBrush),
                        )
                        if (downloading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 2. 中间信息流：书名、作者与阅读进度
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val authorStr = book.author?.trim()?.takeIf { it.isNotBlank() }
                    val progressStr = if (downloading) {
                        "下载中…"
                    } else if (readiness.tone == ReadinessTone.READY) {
                        if (percent >= 99.5f) "已读完" else if (percent > 0f) "已读 ${percent.toInt()}%" else "未读"
                    } else {
                        readiness.label
                    }
                    val subtitle = if (authorStr != null) "$authorStr · $progressStr" else progressStr

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (percent > 0f && percent < 99.5f) {
                        ProgressLine(
                            percent = percent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(72.dp)
                                .height(3.dp),
                        )
                    }
                }

                // 3. 多选勾选徽章；普通状态的书籍操作统一由长按整行打开。
                if (selectionMode) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 4. 底部分割线（内缩 66dp 与左侧封面避让对齐）
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
internal fun ShelfCoverFallback(
    book: BookEntity,
    isCompact: Boolean = false,
) {
    MutedCoverFallback(book = book, isCompact = isCompact)
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
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p / 100f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
