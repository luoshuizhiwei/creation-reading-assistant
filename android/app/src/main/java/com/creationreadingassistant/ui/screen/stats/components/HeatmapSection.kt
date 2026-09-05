package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.HeatmapCell
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate
import kotlin.math.ceil

/**
 * 年度阅读热力图微岛：
 * - 整年 365 天网状排布，圆角 2.5dp 方块；
 * - 柔和递进的纸墨阶梯渐变色彩；
 * - 顶部 32dp 微底座小图标与次级说明胶囊标签。
 */
@Composable
internal fun HeatmapSection(
    heatmap: List<HeatmapCell>,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val layout = LocalLayoutTokens.current
    val scheme = MaterialTheme.colorScheme

    val totalMs = remember(heatmap) { heatmap.sumOf { it.durationMs } }
    val activeDays = remember(heatmap) { heatmap.count { it.durationMs > 0L } }

    // 将 365 天的平铺序列按"星期几 → 第几周"二维化
    val grid = remember(heatmap) { arrangeHeatmapGrid(heatmap) }
    val monthLabels = remember(grid) { buildMonthColumnLabels(grid) }

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-heatmap"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题行：带 32dp 微彩底座与轻量胶囊摘要
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "365 天阅读足迹",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = if (activeDays > 0) "$activeDays 天开卷 · ${formatCompactDuration(totalMs)}" else "近一年暂无阅读",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

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
                            HeatmapGrid(weeks = grid, cellSize = cell, gap = gap)
                        }
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
private fun HeatmapGrid(weeks: List<HeatmapWeek>, cellSize: Dp, gap: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Top,
    ) {
        weeks.forEach { week ->
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                week.cells.forEach { cell ->
                    HeatmapCellBox(cell, cellSize)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCellBox(cell: HeatmapCell?, cellSize: Dp) {
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
    Box(
        modifier = Modifier
            .width(cellSize)
            .height(cellSize)
            .clip(if (cellSize >= 6.dp) CELL_SHAPE else RoundedCornerShape(1.dp))
            .then(if (hasCell) Modifier.background(color) else Modifier),
    )
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
