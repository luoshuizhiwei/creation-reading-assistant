package com.creationreadingassistant.ui.screen.homearchive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.viewmodel.CompletedArchiveItem
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

internal enum class InspirationArchiveSort { UPDATED, CREATED, TITLE }
internal enum class CompletedArchiveSort { COMPLETED, TITLE }

/**
 * 完读书籍封面微书脊立体阴影画笔（顶级常量，零 GC 分配）。
 */
internal val CompletedCoverSpineShadowBrush = Brush.horizontalGradient(
    listOf(
        Color.Black.copy(alpha = 0.25f),
        Color.Transparent,
    )
)

/**
 * 完读统计微岛背景渐变画笔（顶级常量，零 GC 分配）。
 */
internal val CompletedStatsBackgroundBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFF10B981).copy(alpha = 0.08f),
        Color(0xFF10B981).copy(alpha = 0.02f),
        Color.Transparent,
    )
)

/**
 * 完读统计微岛图标底座渐变画笔（顶级常量，零 GC 分配）。
 */
internal val CompletedStatsIconHaloBrush = Brush.linearGradient(
    listOf(
        Color(0xFF10B981).copy(alpha = 0.22f),
        Color(0xFF059669).copy(alpha = 0.12f),
    )
)

/**
 * 摘录原句卡片左侧墨线竖标渐变画笔（顶级常量，零 GC 分配）。
 */
internal val ExcerptAccentVerticalBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF059669),
        Color(0xFF10B981).copy(alpha = 0.35f),
    )
)

private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun parseInspirationPayload(entity: InspirationEntity?): InspirationPayloadData {
    if (entity?.payload.isNullOrBlank()) return InspirationPayloadData()
    return runCatching { payloadJson.decodeFromString<InspirationPayloadData>(entity!!.payload!!) }
        .getOrDefault(InspirationPayloadData())
}

internal fun formatInspirationTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "刚刚"
    val instant = runCatching { Instant.parse(isoString) }.getOrNull() ?: return "刚刚"
    val zone = ZoneId.systemDefault()
    val localDateTime = instant.atZone(zone).toLocalDateTime()
    val now = java.time.ZonedDateTime.now(zone).toLocalDateTime()
    val days = ChronoUnit.DAYS.between(localDateTime.toLocalDate(), now.toLocalDate())
    return when {
        days == 0L -> {
            val hours = ChronoUnit.HOURS.between(localDateTime, now)
            if (hours <= 0) "刚刚" else "${hours}小时前"
        }
        days in 1..30 -> "${days}天前"
        localDateTime.year == now.year -> "%d月%d日".format(localDateTime.monthValue, localDateTime.dayOfMonth)
        else -> "%d年%d月%d日".format(localDateTime.year, localDateTime.monthValue, localDateTime.dayOfMonth)
    }
}

internal fun formatDetailTime(value: String?): String {
    if (value.isNullOrBlank()) return "刚刚"
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return "刚刚"
    val zdt = instant.atZone(ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))
}

internal fun formatReadingTime(value: Long): String {
    val minutes = (value.coerceAtLeast(0L) / 60_000.0).roundToInt()
    if (minutes <= 0) return "不足 1 分钟"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}小时${rest}分钟" else "${minutes}分钟"
}

internal fun completedDateLabel(item: CompletedArchiveItem): String {
    val timestamp = item.progress.completed_at ?: return "已读完"
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日读完"
}

internal fun getTypeLabel(type: String?): String = when (type) {
    "note" -> "想法"
    "excerpt" -> "金句摘录"
    "setting" -> "世界设定"
    "plot" -> "情节桥段"
    else -> "灵感"
}

@Composable
internal fun ArchiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholderText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清空",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    )
}
