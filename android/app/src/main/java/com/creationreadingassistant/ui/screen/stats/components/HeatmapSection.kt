package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.HeatmapCell
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * 年度阅读热力图：GitHub 贡献图风格。
 * 列 = 周（52~53 周），行 = 星期（周一 ~ 周日），方块颜色深浅表示当日阅读时长。
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
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("365 天阅读足迹", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (activeDays > 0) "$activeDays 天开卷 · ${formatCompactDuration(totalMs)}" else "近一年暂无阅读",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (grid.isEmpty()) {
                Text(
                    "开始阅读后，这里会展示每天的阅读热力图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = layout.microGap),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 月份标签行（与列对齐）
                    MonthLabelRow(labels = monthLabels)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 左侧星期标签（周一 / 周三 / 周五 / 周日）
                        WeekdayLabelColumn()

                        // 主体热力图网格：按列（周）排布，每列 7 格（周一~周日）
                        HeatmapGrid(weeks = grid)
                    }

                    // 图例：less → more
                    LegendRow()
                }
            }
        }
    }
}

/* ========== 网格构建与二维化 ========== */

/** 每周 7 格；每格 = [0,7) 对应 周一..周日。 */
private data class HeatmapWeek(
    /** 第一列（周一）对应的日期，用于列归属月份的判断。 */
    val anchorDate: LocalDate,
    val cells: List<HeatmapCell?>, // 长度 = 7
)

/**
 * 按 GitHub 风格二维化：把连续 365 天的平铺 cells 排列成
 * 「列 = 周（从左到右）、行 = 星期（从上到下，周一~周日）」的矩阵。
 * 第一年起始日之前的格子留空（渲染为透明占位）。
 */
private fun arrangeHeatmapGrid(cells: List<HeatmapCell>): List<HeatmapWeek> {
    if (cells.isEmpty()) return emptyList()
    // 找第一列（周一）作为起点，可能早于 cells 第一个日期
    val firstDate = cells.first().date
    val lastDate = cells.last().date
    val firstDow = firstDate.dayOfWeek.value // 1=Mon..7=Sun
    val gridStartDate = firstDate.minusDays((firstDow - 1).toLong())

    // 构建日期 → cell 的快速索引
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

/** 为每一列（周）打一个月份标签，仅当该周对应月份变化时才显示，避免所有列都写字。 */
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
private fun MonthLabelRow(labels: List<String?>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WEEKDAY_LABEL_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        // 宽度占位：用等宽的 Box 对齐下面热力图的每一列
        val cellW = Modifier.width(CELL_SIZE)
        labels.forEach { label ->
            Box(cellW) {
                if (label != null) {
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
private fun WeekdayLabelColumn() {
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
        val showOn = setOf(0, 2, 4, 6) // 周一、周三、周五、周日对应的行下标
        repeat(7) { row ->
            val h = if (labels.size > showOn.indexOf(row)) CELL_SIZE else CELL_SIZE
            Box(Modifier.height(h)) {
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
private fun HeatmapGrid(weeks: List<HeatmapWeek>) {
    // 一周 7 行，每列对应一周；逐周写，每周画 7 个 cell（从上到下 Mon..Sun）
    Row(
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        verticalAlignment = Alignment.Top,
    ) {
        weeks.forEach { week ->
            Column(
                verticalArrangement = Arrangement.spacedBy(CELL_GAP),
            ) {
                week.cells.forEach { cell ->
                    HeatmapCellBox(cell)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCellBox(cell: HeatmapCell?) {
    val scheme = MaterialTheme.colorScheme
    val color = remember(cell) {
        when {
            cell == null -> Color.Transparent
            cell.durationMs <= 0L -> scheme.surfaceVariant.copy(alpha = 0.35f)
            else -> {
                // 基于 primary 色 + alpha 梯度；0 时长已在上面分支
                val i = cell.intensity.coerceIn(0f, 1f)
                // 四档：0-0.12 很淡、0.12-0.35 淡、0.35-0.7 中、0.7+ 浓
                val alpha = when {
                    i <= 0.12f -> 0.22f
                    i <= 0.35f -> 0.42f
                    i <= 0.70f -> 0.68f
                    else -> 0.92f
                }
                scheme.primary.copy(alpha = alpha)
            }
        }
    }
    val hasCell = cell != null
    Box(
        modifier = Modifier
            .width(CELL_SIZE)
            .height(CELL_SIZE)
            .clip(CELL_SHAPE)
            .then(if (hasCell) Modifier.background(color) else Modifier),
    )
}

@Composable
private fun LegendRow() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.heatmap_intensity_less),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        val alphas = listOf(0.22f, 0.42f, 0.68f, 0.92f)
        alphas.forEachIndexed { i, a ->
            Box(
                modifier = Modifier
                    .width(CELL_SIZE)
                    .height(CELL_SIZE)
                    .clip(CELL_SHAPE)
                    .background(if (i == 0) scheme.surfaceVariant.copy(alpha = 0.35f) else scheme.primary.copy(alpha = a)),
            )
            if (i < alphas.lastIndex) Spacer(Modifier.width(CELL_GAP))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.heatmap_intensity_more),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

private val CELL_SIZE = 11.dp
private val CELL_GAP = 2.5.dp
private val CELL_SHAPE = RoundedCornerShape(2.dp)
private val WEEKDAY_LABEL_WIDTH = 18.dp
