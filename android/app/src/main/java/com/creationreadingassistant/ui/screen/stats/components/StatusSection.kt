package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.screen.stats.BookStatus
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun StatusSection(
    status: BookStatus,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-status"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("书籍状态", style = MaterialTheme.typography.titleMedium)
                Text(
                    "共 ${status.total} 本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBar("在读", status.reading, status.total, MaterialTheme.colorScheme.primary)
            if (status.shelved > 0) {
                StatusBar("搁置", status.shelved, status.total, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBar("已读完", status.completed, status.total, MaterialTheme.colorScheme.tertiary)
            StatusBar("未开始", status.unread, status.total, MaterialTheme.colorScheme.secondary)
            if (status.unreadable > 0) {
                StatusBar(
                    "不可读",
                    status.unreadable,
                    status.total,
                    MaterialTheme.colorScheme.error,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "不可读包括同步占位、导入失败或文件缺失的书籍。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) (count.toFloat() / total) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color = color),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(52.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction)
                    .height(8.dp)
                    .clip(LocalComponentSpec.current.listItemShape)
                    .background(color = color),
            )
        }
        Text("$count", style = MaterialTheme.typography.bodySmall)
    }
}
