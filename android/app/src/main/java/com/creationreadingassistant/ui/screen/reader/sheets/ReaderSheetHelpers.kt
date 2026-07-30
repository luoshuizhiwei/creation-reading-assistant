package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SettingRow

/** 阅读设置/主题中的选项胶囊：统一委托 SelectablePill，自动跟随三套主题圆角/描边/底色。 */
@Composable
internal fun OptionPill(selected: Boolean, label: String, onClick: () -> Unit) {
    SelectablePill(
        text = label,
        selected = selected,
        onClick = onClick,
        selectedBorderTint = MaterialTheme.colorScheme.secondary,
    )
}

/** 阅读设置中的开关行（R7 阅读内快捷开关）：统一委托 SettingRow。 */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = label,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** 进度/书籍信息中的统计单元格。 */
@Composable
internal fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** 书籍信息中的信息行。 */
@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 高亮颜色常量，供 SelectionToolbar / NotesSheet 共享。 */
internal val HIGHLIGHT_COLORS = listOf("yellow", "red", "green", "blue", "purple")
