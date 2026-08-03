package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.ThemeSwitchButton
import com.creationreadingassistant.ui.theme.AppPalette

// ============================== 外观页 ==============================

@Composable
internal fun AppearanceSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    var showPalettePicker by remember { mutableStateOf(false) }
    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionCard {
                Text("应用外观影响首页、书架、灵感、统计和设置；阅读页正文背景仍在阅读器设置里单独控制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                SettingRow(
                    title = "配色主题",
                    subtitle = "当前配色会长期保存；加入更多配色后可在这里切换",
                    trailing = {
                        Text(
                            AppPalette.fromStored(state.appearance.colorPalette).displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = if (AppPalette.entries.size > 1) {
                        { showPalettePicker = true }
                    } else null,
                )
                ThemeSwitchButton(
                    currentMode = state.appearance.themeMode,
                    onModeChange = { newTheme -> onAction(ProfileAction.UpdateAppearance { copy(themeMode = newTheme) }) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            SectionCard {
                SettingRow(
                    title = "纸张纹理",
                    subtitle = "默认关闭；开启后只叠加极淡纸感，不改变主题色",
                    trailing = { Switch(checked = state.appearance.paperTexture, onCheckedChange = { checked -> onAction(ProfileAction.UpdateAppearance { copy(paperTexture = checked) }) }) },
                )
            }
        }
    }
    if (showPalettePicker) {
        val selected = AppPalette.fromStored(state.appearance.colorPalette)
        AlertDialog(
            onDismissRequest = { showPalettePicker = false },
            title = { Text("选择配色主题") },
            text = {
                Column {
                    AppPalette.entries.forEach { palette ->
                        SettingRow(
                            title = palette.displayName,
                            trailing = {
                                RadioButton(
                                    selected = palette == selected,
                                    onClick = null,
                                )
                            },
                            onClick = {
                                onAction(ProfileAction.UpdateAppearance { copy(colorPalette = palette.storageId) })
                                showPalettePicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPalettePicker = false }) { Text("关闭") }
            },
        )
    }
}
