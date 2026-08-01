package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow

// ============================== AI 设置页 ==============================

@Composable
internal fun AiSettingsSubPage(
    modifier: Modifier,
    enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    baseUrl: String, onBaseUrlChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    temperature: Float, onTemperatureChange: (Float) -> Unit,
    keyDraft: String, onKeyDraftChange: (String) -> Unit,
    keySaved: Boolean,
    prompt: String, onPromptChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClearKey: () -> Unit,
) {
    var showKey by remember { mutableStateOf(keyDraft.isBlank()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DegradedNote("手机端 AI Key 已持久化在应用本地沙箱，不会跨设备同步，也不参与 WebDAV 同步或数据导出；配置 OpenAI-compatible 接口后，灵感详情页可一键生成 AI 候选版本。")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(
                    title = "启用 AI 助手",
                    trailing = { Switch(checked = enabled, onCheckedChange = onEnabledChange) },
                )
                OutlinedTextField(value = baseUrl, onValueChange = onBaseUrlChange, placeholder = { Text("https://dashscope.aliyuncs.com/compatible-mode/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                OutlinedTextField(value = model, onValueChange = onModelChange, placeholder = { Text("qwen-plus") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                Column {
                    Text("创造性 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = temperature,
                        onValueChange = onTemperatureChange,
                        valueRange = 0f..1.5f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = onKeyDraftChange,
                    placeholder = { Text(if (keySaved) "已保存 API Key；留空则不修改" else "粘贴手机端 API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(if (keySaved) "已保存 API Key；不会同步、导出或上传到 WebDAV。" else "还没有保存手机端 API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("个性化提示词（可选）") },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave) { Text("保存设置") }
                    OutlinedButton(onClick = onTest) { Icon(Icons.Filled.Wifi, contentDescription = null); Text("测试", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onClearKey) { Text("清除 Key") }
                }
            }
        }
    }
}
