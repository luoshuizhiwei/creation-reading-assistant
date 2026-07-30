package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.bounceable

/**
 * 应用外观切换按钮（T4：仅 3 态）。
 *
 * 仅控制应用**外观**（跟随系统 / 浅色 / 深色），不含任何阅读器纸张入口（纸张选择在阅读器设置内独立控制）。
 * 三态色点统一为外壳主色（靛青 #3D5A80）；统一使用 M3 填充图标，弃 iOS 线性图标特判。
 * 无「默认」第 4 态。
 */
private val APPEARANCE_OPTIONS = listOf(
    Triple("system", "跟随系统", "随手机深浅色变化"),
    Triple("light", "浅色", "纸张感更强，适合白天"),
    Triple("dark", "深色", "夜间浏览更安静"),
)

@Composable
fun ThemeSwitchButton(
    currentMode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val current = APPEARANCE_OPTIONS.firstOrNull { it.first == currentMode } ?: APPEARANCE_OPTIONS[0]

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = { expanded = true })
            .bounceable(interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = current.second,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        APPEARANCE_OPTIONS.forEach { (value, title, desc) ->
            val selected = value == currentMode
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                        Column {
                            Text(
                                text = title,
                                color = if (selected) scheme.primary else scheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                onClick = {
                    onModeChange(value)
                    expanded = false
                },
            )
        }
    }
}
