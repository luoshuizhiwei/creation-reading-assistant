package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
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

/**
 * 年度账单 Hero 卡：顶部展示「全年总结一句话 + 3 个仪式感大字指标」。
 * 仅当 StatsPeriod == YEAR 且有阅读数据时组合；空数据交给 global-empty 兜底。
 */
@Composable
internal fun YearBillHeroCard(
    stats: StatsUi,
    anchorYear: Int,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
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

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-year-bill-hero"),
        contentPadding = 0.dp,
    ) {
        // 顶部渐变色横条 + 年度标题（仪式感）
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.90f),
                            scheme.primary.copy(alpha = 0.58f),
                        ),
                    ),
                )
                .padding(horizontal = layout.pageHorizontal, vertical = 18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "$anchorYear 年度阅读报告",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFontFamily),
                    )
                }
                Text(
                    headline,
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 三大关键数字：总时长 / 开卷天数 / 最长连续
        Column(
            Modifier
                .fillMaxWidth()
                .padding(layout.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YearHeroMetric(
                    icon = {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    value = {
                        Text(
                            formatCompactDuration((hours * 60L) * 60_000L),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                            color = scheme.onSurface,
                        )
                    },
                    label = "累计阅读",
                    sub = "$hours 小时 $anchorYear",
                    modifier = Modifier.weight(1f),
                )
                YearHeroMetric(
                    icon = {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    value = {
                        Text(
                            "$days 天",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                            color = scheme.onSurface,
                        )
                    },
                    label = "开卷天数",
                    sub = "全年坚持",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YearHeroMetric(
                    icon = {
                        Icon(
                            Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    value = {
                        Text(
                            "$completed 本",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                            color = scheme.onSurface,
                        )
                    },
                    label = "读完书籍",
                    sub = "翻完最后一页",
                    modifier = Modifier.weight(1f),
                )
                YearHeroMetric(
                    icon = {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    value = {
                        Text(
                            "$longest 天",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                            color = scheme.onSurface,
                        )
                    },
                    label = "最长连续",
                    sub = "不间断的记录",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun YearHeroMetric(
    icon: @Composable () -> Unit,
    value: @Composable () -> Unit,
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    val scheme = MaterialTheme.colorScheme
    val spec = LocalComponentSpec.current
    Row(
        modifier = modifier
            .clip(spec.listItemShape)
            .background(scheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            value()
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.75f))
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
