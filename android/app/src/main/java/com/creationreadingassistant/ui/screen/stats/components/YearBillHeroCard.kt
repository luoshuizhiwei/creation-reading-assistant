package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate
import kotlin.math.roundToInt

private data class YearBillMetricItem(
    val icon: ImageVector,
    val iconTint: Color,
    val value: String,
    val label: String,
    val subtitle: String,
)

/**
 * 年度账单 Hero 卡：
 * - 顶部仪式感渐变横幅 + 年度总结金句；
 * - 2x2 纸墨微岛核心指标卡片，配 32dp 微彩底座与展示衬线数字；
 * - 仅当 StatsPeriod == YEAR 且有阅读数据时组合。
 */
@Composable
internal fun YearBillHeroCard(
    stats: StatsUi,
    anchorYear: Int,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val layout = LocalLayoutTokens.current
    val scheme = MaterialTheme.colorScheme

    val hoursFloat = remember(stats.totalReadingMs) {
        stats.totalReadingMs.toFloat() / 3_600_000f
    }
    val hours = rememberCountUp(hoursFloat.roundToInt(), reducedMotion)
    val days = rememberCountUp(stats.readingDays, reducedMotion)
    val longest = rememberCountUp(stats.streakLongest, reducedMotion)
    val completed = rememberCountUp(stats.completed, reducedMotion)

    val headline = remember(stats.totalReadingMs, stats.completed, stats.streakLongest, stats.readingDays) {
        buildYearHeadline(
            totalMs = stats.totalReadingMs,
            readingDays = stats.readingDays,
            completedBooks = stats.completed,
            longestStreak = stats.streakLongest,
        )
    }

    val metrics = listOf(
        YearBillMetricItem(
            icon = Icons.Outlined.Timer,
            iconTint = Color(0xFF2563EB),
            value = formatCompactDuration((hours * 60L) * 60_000L),
            label = "累计阅读",
            subtitle = "$hours 小时 $anchorYear",
        ),
        YearBillMetricItem(
            icon = Icons.Outlined.LocalFireDepartment,
            iconTint = Color(0xFFF59E0B),
            value = "$days 天",
            label = "开卷天数",
            subtitle = "全年坚持",
        ),
        YearBillMetricItem(
            icon = Icons.Outlined.EmojiEvents,
            iconTint = Color(0xFF10B981),
            value = "$completed 本",
            label = "读完书籍",
            subtitle = "翻完最后一页",
        ),
        YearBillMetricItem(
            icon = Icons.Outlined.AutoStories,
            iconTint = Color(0xFF8B5CF6),
            value = "$longest 天",
            label = "最长连续",
            subtitle = "不间断的记录",
        ),
    )

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-year-bill-hero"),
        contentPadding = 0.dp,
    ) {
        // 顶部仪式感渐变横幅 + 年度金句
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.92f),
                            scheme.primary.copy(alpha = 0.65f),
                        ),
                    ),
                )
                .padding(horizontal = layout.pageHorizontal, vertical = 18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconPedestal(
                        icon = Icons.Outlined.AutoStories,
                        tint = Color.White,
                        size = 28.dp,
                        iconSize = 16.dp,
                    )
                    Text(
                        "$anchorYear 年度阅读报告",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Text(
                    text = "“ $headline ”",
                    color = Color.White.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = DisplayFontFamily,
                    ),
                )
            }
        }

        // 2x2 纸墨微岛关键指标网格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            metrics.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { item ->
                        YearBillMicroCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearBillMicroCard(
    item: YearBillMetricItem,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    Surface(
        modifier = modifier,
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconPedestal(
                    icon = item.icon,
                    tint = item.iconTint,
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** 纯函数：根据年度数据拼装一句有人情味的总结，字数控制在 36 字以内。 */
internal fun buildYearHeadline(
    totalMs: Long,
    readingDays: Int,
    completedBooks: Int,
    longestStreak: Int,
): String {
    val today = LocalDate.now()
    val yearDays = today.lengthOfYear()
    val pct = if (yearDays > 0) (readingDays.toFloat() / yearDays * 100f).toInt() else 0
    val hours = totalMs / 3_600_000f

    return when {
        readingDays == 0 -> "今年还没打开过书，新的一年一起开卷吧。"
        completedBooks >= 12 ->
            "平均每月读完 1 本，共翻阅 ${hours.toInt()} 小时 —— 书是你最稳的年度旅伴。"
        longestStreak >= 30 ->
            "最长连续阅读 $longestStreak 天，在 $pct% 的日子里与书作伴。"
        hours >= 50f ->
            "累计 ${hours.toInt()} 小时、$readingDays 天开卷 —— 你的每一次翻开都算数。"
        completedBooks >= 3 ->
            "今年读完了 $completedBooks 本书，在 $readingDays 个日子里与文字相遇。"
        else ->
            "在 $readingDays 天里翻开书页，字句正在悄悄积累。"
    }
}
