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
import com.creationreadingassistant.data.settings.DurationUnit
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
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.durationUnitToggle
import java.time.LocalDate

private data class YearBillMetricItem(
    val icon: ImageVector,
    val iconTint: Color,
    val value: String,
    val label: String,
    val subtitle: String,
    /**
     * 只作用于主值文本的附加修饰符；**仅时长指标**传 [durationUnitToggle]。
     *
     * 用 `Modifier` 而非布尔开关：调用方自己决定挂什么，本组件不替调用方猜测
     * 哪个指标是时长（"42 篇"、"3 天" 这类计数挂上去是错的）。
     */
    val valueModifier: Modifier = Modifier,
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

    val durationUnit = LocalDurationUnit.current

    // S15-F12-R1：主值、subtitle、金句必须来自**同一份原始时长**。
    //
    // 旧实现先把 totalReadingMs 抬成整数小时（`roundToInt()`）再回乘成 ms 交给公共
    // formatter，而金句用原始 ms ⇒ 50 小时 30 分时主值「51 小时」、金句「累计 50 小时」。
    // 现在动画仍按「分钟」递增（比按毫秒递增更贴近用户读数粒度），但**动画的终点
    // 就是原始时长的整分钟值**，不做任何四舍五入；因此动画结束后主值与金句逐字一致。
    //
    // 分钟粒度是显示下限（公共 formatter 本身也以分钟为单位、不足一分钟显示 0 分钟），
    // 故这里量化到分钟不会与公共结果产生偏差。
    val totalMinutes = stats.totalReadingMs / 60_000L
    val animatedMinutes = rememberCountUp(totalMinutes.toInt(), "stats.yearbill.hours", reducedMotion)
    val cardDurationText = yearBillCardValue(animatedMinutes, durationUnit)

    val days = rememberCountUp(stats.readingDays, "stats.yearbill.days", reducedMotion)
    val longest = rememberCountUp(stats.streakLongest, "stats.yearbill.longest", reducedMotion)
    val completed = rememberCountUp(stats.completed, "stats.yearbill.completed", reducedMotion)

    val headline = remember(stats.totalReadingMs, stats.completed, stats.streakLongest, stats.readingDays, durationUnit) {
        buildYearHeadline(
            totalMs = stats.totalReadingMs,
            readingDays = stats.readingDays,
            completedBooks = stats.completed,
            longestStreak = stats.streakLongest,
            unit = durationUnit,
        )
    }

    val metrics = listOf(
        YearBillMetricItem(
            icon = Icons.Outlined.Timer,
            iconTint = Color(0xFF2563EB),
            value = cardDurationText,
            label = "累计阅读",
            // 副标题与主值同源同单位：DAYS 模式下不能一边写「1 天」一边写「30 小时」。
            subtitle = "$cardDurationText $anchorYear",
            // 年度账单 Hero 的唯一切换入口挂在「累计阅读」主值上。
            //
            // subtitle 与金句刻意不挂：它们与主值**同源同文**（同一个 cardDurationText /
            // spoken），再挂就是同一语义的第二个、第三个入口，TalkBack 会在同一张卡里
            // 读到三个同名的「切换时长显示单位」。
            valueModifier = Modifier.durationUnitToggle(),
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
                modifier = item.valueModifier,
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

/**
 * 纯函数：年度账单「累计阅读」主值 / subtitle 的时长文本（渲染与测试共用）。
 *
 * S15-F12-R1 的单一事实源入口：把**原始时长**量化到整分钟（动画粒度）后交给公共
 * [formatCompactDuration]，不做任何小时级四舍五入。
 *
 * 为什么不是"先抬成整数小时再回乘"：那会让 50 小时 30 分变成 51 小时，而
 * [buildYearHeadline] 用原始时长得到 50 小时，同一张卡自相矛盾。量化到分钟
 * 不会引入偏差——公共 formatter 本身也以分钟为单位（不足一分钟显示 0 分钟）。
 */
internal fun yearBillCardValue(totalMs: Long, unit: DurationUnit): String =
    formatCompactDuration(totalMs / 60_000L * 60_000L, unit)

/**
 * 纯函数：由动画当前值（整分钟）求主值文本，供渲染期每帧调用。
 */
internal fun yearBillCardValue(animatedMinutes: Int, unit: DurationUnit): String =
    formatCompactDuration(animatedMinutes.toLong() * 60_000L, unit)

/**
 * 纯函数：根据年度数据拼装一句有人情味的总结，字数控制在 36 字以内。
 *
 * [unit] 决定句中时长词的单位：两处时长表述都经公共 [formatCompactDuration] 产生，
 * 因此不会出现「一边说 1 天、一边说 30 小时」的自相矛盾。分支判定仍用小时量级
 * （`hours >= 50f`），与单位偏好解耦——换单位不该改变命中哪一句。
 */
internal fun buildYearHeadline(
    totalMs: Long,
    readingDays: Int,
    completedBooks: Int,
    longestStreak: Int,
    unit: DurationUnit = DurationUnit.HOURS,
): String {
    val today = LocalDate.now()
    val yearDays = today.lengthOfYear()
    val pct = if (yearDays > 0) (readingDays.toFloat() / yearDays * 100f).toInt() else 0
    val hours = totalMs / 3_600_000f
    val spoken = formatCompactDuration(totalMs, unit)

    return when {
        readingDays == 0 -> "今年还没打开过书，新的一年一起开卷吧。"
        completedBooks >= 12 ->
            "平均每月读完 1 本，共翻阅 $spoken —— 书是你最稳的年度旅伴。"
        longestStreak >= 30 ->
            "最长连续阅读 $longestStreak 天，在 $pct% 的日子里与书作伴。"
        hours >= 50f ->
            "累计 $spoken、$readingDays 天开卷 —— 你的每一次翻开都算数。"
        completedBooks >= 3 ->
            "今年读完了 $completedBooks 本书，在 $readingDays 个日子里与文字相遇。"
        else ->
            "在 $readingDays 天里翻开书页，字句正在悄悄积累。"
    }
}
