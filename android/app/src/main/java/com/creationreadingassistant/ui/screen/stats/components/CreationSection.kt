package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.screen.stats.formatThousands
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun CreationSection(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-creation"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("阅读与创作", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CreationItem(
                    Icons.Outlined.AccessTime,
                    formatCompactDuration(stats.totalReadingMs),
                    "阅读时长",
                    Modifier.weight(1f),
                    reducedMotion = reducedMotion,
                )
                CreationItem(
                    Icons.Outlined.CalendarMonth,
                    "${stats.readingDays}",
                    "阅读天数",
                    Modifier.weight(1f),
                    reducedMotion = reducedMotion,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CreationItem(
                    Icons.Outlined.ChatBubble,
                    "${stats.noteCount}",
                    "笔记",
                    Modifier.weight(1f),
                    reducedMotion = reducedMotion,
                )
                CreationItem(
                    Icons.Outlined.AutoAwesome,
                    "${stats.inspirationCount}",
                    "灵感",
                    Modifier.weight(1f),
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@Composable
private fun CreationItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = LocalComponentSpec.current.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppIconSize.Small),
            )
            val numeric = value.toIntOrNull()
            Text(
                if (numeric != null) rememberCountUp(numeric, reducedMotion).toString() else value,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
