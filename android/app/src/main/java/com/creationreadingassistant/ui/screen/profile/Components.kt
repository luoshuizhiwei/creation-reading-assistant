package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SettingRow

// ============================== 共享组件 ==============================

@Composable
internal fun EmptyCard(icon: ImageVector, title: String, body: String) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// ============================== 通用小组件 ==============================

@Composable
internal fun DegradedNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
internal fun RangeRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.widthIn(min = 48.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = if (step > 0f) ((max - min) / step).toInt() - 1 else 0,
            modifier = Modifier.weight(1f),
        )
        Text(valueLabel(value), modifier = Modifier.widthIn(min = 56.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    // 视觉统一：与阅读器弹层 OptionPill 同语言（SelectablePill，pillShape + 内建触感），
    // 替代旧 OutlinedButton + 硬编码 8.dp 圆角的分段按钮
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (enabled) Modifier else Modifier.alpha(0.5f),
    ) {
        options.forEach { (value, label) ->
            SelectablePill(
                text = label,
                selected = value == selected,
                onClick = { if (enabled) onSelect(value) },
            )
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        title = label,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
internal fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)
