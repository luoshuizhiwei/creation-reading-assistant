package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.HeatmapCell
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.durationUnitToggle
import java.time.LocalDate
import kotlin.math.ceil

/**
 * 年度阅读热力图微岛：
 * - 整年 365 天网状排布，圆角 2.5dp 方块；
 * - 柔和递进的纸墨阶梯渐变色彩；
 * - 顶部 32dp 微底座小图标与次级说明胶囊标签；
 * - 支持点击单个方块查看精确日期与时长微气泡提示。
 */
@Composable
internal fun HeatmapSection(
    heatmap: List<HeatmapCell>,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val scheme = MaterialTheme.colorScheme

    val totalMs = remember(heatmap) { heatmap.sumOf { it.durationMs } }
    val activeDays = remember(heatmap) { heatmap.count { it.durationMs > 0L } }

    var selectedCell by remember { mutableStateOf<HeatmapCell?>(null) }

    // 将 365 天的平铺序列按"星期几 → 第几周"二维化
    val grid = remember(heatmap) { arrangeHeatmapGrid(heatmap) }
    val monthLabels = remember(grid) { buildMonthColumnLabels(grid) }

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-heatmap"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题行：收敛到共享 IslandSectionHeader（32dp IconPedestal 底座）+ 轻量胶囊摘要
            IslandSectionHeader(
                title = "365 天阅读足迹",
                icon = Icons.Outlined.CalendarMonth,
                tint = scheme.primary,
                trailing = {
                    Surface(
                        shape = PillShape,
                        color = scheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            scheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        ),
                    ) {
                        Text(
                            text = if (activeDays > 0) "$activeDays 天开卷 · ${formatCompactDuration(totalMs, LocalDurationUnit.current)}" else "近一年暂无阅读",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            // 整张热力图只有这一个汇总口径的时长入口。
                            //
                            // 单日 tooltip 与格子刻意不挂：格子本身已有「点击选中」的
                            // 主交互，再挂长按会让一次点击先被长按手势消费；且 365 个格子
                            // 各自成为入口会让 TalkBack 在同屏读到 365 个同名操作。
                            modifier = Modifier
                                .durationUnitToggle()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                },
            )

            if (grid.isEmpty()) {
                Text(
                    "开始阅读后，这里会展示每天的阅读热力图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = layout.microGap),
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val cols = grid.size.coerceAtLeast(1)
                    val gap = if (constraints.maxWidth / cols < with(density) { 7.dp.toPx() }) {
                        1.dp
                    } else {
                        2.5.dp
                    }
                    val gapPx = with(density) { gap.toPx() }
                    val cellPx = (((constraints.maxWidth - (cols - 1) * gapPx) / cols)
                        .coerceAtMost(with(density) { MAX_CELL_SIZE.toPx() }))
                        .coerceAtLeast(1f)
                    val cell = with(density) { cellPx.toDp() }
                    val gridWidthPx = cols * cellPx + (cols - 1) * gapPx
                    val centered = gridWidthPx < constraints.maxWidth - 1f
                    val showWeekday = cell >= 8.dp

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                    ) {
                        // 月份标签行（与列对齐）
                        MonthLabelRow(
                            labels = monthLabels,
                            cellSize = cell,
                            gap = gap,
                            leadingWidth = if (showWeekday) WEEKDAY_LABEL_WIDTH else 0.dp,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 左侧星期标签（周一 / 周三 / 周五 / 周日）：格子太小时省略
                            if (showWeekday) {
                                WeekdayLabelColumn(cellSize = cell)
                            }

                            // 主体热力图网格：按列（周）排布，每列 7 格（周一~周日）
                            HeatmapGrid(
                                weeks = grid,
                                cellSize = cell,
                                gap = gap,
                                selectedCell = selectedCell,
                                onCellClick = { c ->
                                    selectedCell = if (selectedCell?.date == c.date) null else c
                                },
                            )
                        }
                    }
                }

                // 点击单元格展开的微提示卡片
                AnimatedVisibility(
                    visible = selectedCell != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    selectedCell?.let { c ->
                        HeatmapCellTooltip(
                            cell = c,
                            onDismiss = { selectedCell = null },
                        )
                    }
                }

                // 图例：less → more 柔和阶梯
                LegendRow()
            }
        }
    }
}

/* ========== 网格构建与二维化 ========== */

private data class HeatmapWeek(
    val anchorDate: LocalDate,
    val cells: List<HeatmapCell?>,
)

private fun arrangeHeatmapGrid(cells: List<HeatmapCell>): List<HeatmapWeek> {
    if (cells.isEmpty()) return emptyList()
    val firstDate = cells.first().date
    val lastDate = cells.last().date
    val firstDow = firstDate.dayOfWeek.value
    val gridStartDate = firstDate.minusDays((firstDow - 1).toLong())

    val cellByDate = cells.associateBy { it.date }
    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(gridStartDate, lastDate) + 1
    val totalWeeks = ceil(totalDays.toDouble() / 7.0).toInt()

    val weeks = ArrayList<HeatmapWeek>(totalWeeks)
    var weekStart = gridStartDate
    repeat(totalWeeks) {
        val entries = ArrayList<HeatmapCell?>(7)
        var d = weekStart
        repeat(7) {
            entries += if (d.isBefore(firstDate) || d.isAfter(lastDate)) null else cellByDate[d]
            d = d.plusDays(1)
        }
        weeks += HeatmapWeek(weekStart, entries)
        weekStart = weekStart.plusDays(7)
    }
    return weeks
}

private fun buildMonthColumnLabels(weeks: List<HeatmapWeek>): List<String?> {
    var lastMonth = -1
    return weeks.map { w ->
        val m = w.anchorDate.monthValue
        if (m != lastMonth) {
            lastMonth = m
            "${m}月"
        } else {
            null
        }
    }
}

/* ========== UI 子组件 ========== */

@Composable
private fun MonthLabelRow(
    labels: List<String?>,
    cellSize: Dp,
    gap: Dp,
    leadingWidth: Dp,
) {
    val density = LocalDensity.current
    val minLabelSpanPx = with(density) { MIN_LABEL_SPAN.toPx() }
    val stepPx = with(density) { (cellSize + gap).toPx() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (leadingWidth > 0.dp) {
            Spacer(Modifier.width(leadingWidth))
        }
        var lastShownCol = Int.MIN_VALUE / 2
        labels.forEachIndexed { col, label ->
            Box(Modifier.width(cellSize)) {
                if (label != null && (col - lastShownCol) * stepPx >= minLabelSpanPx) {
                    lastShownCol = col
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayLabelColumn(cellSize: Dp) {
    Column(
        modifier = Modifier.width(WEEKDAY_LABEL_WIDTH).padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        horizontalAlignment = Alignment.End,
    ) {
        val labels = listOf(
            stringResource(R.string.heatmap_weekday_mon),
            stringResource(R.string.heatmap_weekday_wed),
            stringResource(R.string.heatmap_weekday_fri),
            stringResource(R.string.heatmap_weekday_sun),
        )
        val showOn = setOf(0, 2, 4, 6)
        repeat(7) { row ->
            Box(Modifier.height(cellSize)) {
                if (row in showOn) {
                    val idx = showOn.indexOf(row)
                    Text(
                        labels[idx],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    weeks: List<HeatmapWeek>,
    cellSize: Dp,
    gap: Dp,
    selectedCell: HeatmapCell?,
    onCellClick: (HeatmapCell) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Top,
    ) {
        weeks.forEach { week ->
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                week.cells.forEach { cell ->
                    val isSelected = cell != null && selectedCell?.date == cell.date
                    HeatmapCellBox(
                        cell = cell,
                        cellSize = cellSize,
                        isSelected = isSelected,
                        onClick = { if (cell != null) onCellClick(cell) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCellBox(
    cell: HeatmapCell?,
    cellSize: Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = remember(cell) {
        when {
            cell == null -> Color.Transparent
            cell.durationMs <= 0L -> scheme.surfaceContainerHighest.copy(alpha = 0.45f)
            else -> {
                val i = cell.intensity.coerceIn(0f, 1f)
                val alpha = when {
                    i <= 0.15f -> 0.28f
                    i <= 0.40f -> 0.50f
                    i <= 0.75f -> 0.72f
                    else -> 0.95f
                }
                scheme.primary.copy(alpha = alpha)
            }
        }
    }
    val hasCell = cell != null
    val shape = if (cellSize >= 6.dp) CELL_SHAPE else RoundedCornerShape(1.dp)

    Box(
        modifier = Modifier
            .width(cellSize)
            .height(cellSize)
            .clip(shape)
            .then(if (hasCell) Modifier.background(color) else Modifier)
            .then(
                if (isSelected) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier
            )
            .then(
                if (hasCell) Modifier.clickable(onClick = onClick) else Modifier
            ),
    )
}

@Composable
private fun HeatmapCellTooltip(
    cell: HeatmapCell,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = LocalComponentSpec.current

    val dow = cell.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
    val dateStr = "${cell.date.year}年${cell.date.monthValue}月${cell.date.dayOfMonth}日 $dow"
    val durationText = if (cell.durationMs > 0L) {
        "阅读 ${formatCompactDuration(cell.durationMs, LocalDurationUnit.current)} · 活跃度 ${(cell.intensity.coerceIn(0f, 1f) * 100).roundToInt()}%"
    } else {
        "该日暂无阅读记录"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.9f),
        border = BorderStroke(spec.hairlineBorderWidth, scheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (cell.durationMs > 0L) Icons.Outlined.AccessTime else Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = if (cell.durationMs > 0L) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Column {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (cell.durationMs > 0L) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭提示",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendRow() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.heatmap_intensity_less),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        val colors = listOf(
            scheme.surfaceContainerHighest.copy(alpha = 0.45f),
            scheme.primary.copy(alpha = 0.28f),
            scheme.primary.copy(alpha = 0.50f),
            scheme.primary.copy(alpha = 0.72f),
            scheme.primary.copy(alpha = 0.95f),
        )
        colors.forEachIndexed { i, c ->
            Box(
                modifier = Modifier
                    .width(LEGEND_CELL_SIZE)
                    .height(LEGEND_CELL_SIZE)
                    .clip(CELL_SHAPE)
                    .background(c),
            )
            if (i < colors.lastIndex) Spacer(Modifier.width(CELL_GAP))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.heatmap_intensity_more),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

private val MAX_CELL_SIZE = 13.dp
private val MIN_LABEL_SPAN = 22.dp
private val WEEKDAY_LABEL_WIDTH = 18.dp
private val LEGEND_CELL_SIZE = 10.dp
private val CELL_GAP = 2.5.dp
private val CELL_SHAPE = RoundedCornerShape(2.5.dp)
