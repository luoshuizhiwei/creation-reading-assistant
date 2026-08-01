package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.ThemeSwitchButton

// ============================== 外观页 ==============================

@Composable
internal fun AppearanceSubPage(
    modifier: Modifier,
    appTheme: String,
    onThemeChange: (String) -> Unit,
    paperTexture: Boolean,
    onPaperTextureChange: (Boolean) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Text("应用外观影响首页、书架、灵感、统计和设置；阅读页正文背景仍在阅读器设置里单独控制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            ThemeSwitchButton(
                currentMode = appTheme,
                onModeChange = onThemeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard {
            SettingRow(
                title = "纸张纹理",
                subtitle = "开启后界面叠加纸感底纹",
                trailing = { Switch(checked = paperTexture, onCheckedChange = onPaperTextureChange) },
            )
        }
    }
}
