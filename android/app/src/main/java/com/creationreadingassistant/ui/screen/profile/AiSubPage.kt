package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Wifi
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import androidx.compose.foundation.layout.Box
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== AI 设置页 ==============================

@Composable
internal fun AiSettingsSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val ai = state.ai
    val keyDraft = state.aiKeyDraft
    val keySaved = ai.apiKey.isNotBlank()
    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                DegradedNote("手机端 AI Key 已持久化在应用本地沙箱，不会跨设备同步，也不参与 WebDAV 同步或数据导出；配置 OpenAI-compatible 接口后，灵感详情页可一键生成 AI 候选版本。")
            }
        }
        // 非加密 http 接口的一次性警示（不拦截请求，仅提示风险）
        state.aiHttpWarning?.let { warning ->
            item {
                Box(Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingRow(
                        title = "启用 AI 助手",
                        trailing = { Switch(checked = ai.enabled, onCheckedChange = { v -> onAction(ProfileAction.UpdateAi { copy(enabled = v) }) }) },
                    )
                    OutlinedTextField(value = ai.baseUrl, onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(baseUrl = v) }) }, placeholder = { Text("https://dashscope.aliyuncs.com/compatible-mode/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    OutlinedTextField(value = ai.model, onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(model = v) }) }, placeholder = { Text("qwen-plus") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    Column {
                        Text("创造性 ${"%.1f".format(ai.temperature)}", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.Slider(
                            value = ai.temperature,
                            onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(temperature = v) }) },
                            valueRange = 0f..1.5f,
                            steps = 14,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { v -> onAction(ProfileAction.UpdateAiKeyDraft(v)) },
                        placeholder = { Text(if (keySaved) "已保存 API Key；留空则不修改" else "粘贴手机端 API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AppIconSize.Small))
                        Text(if (keySaved) "已保存 API Key；不会同步、导出或上传到 WebDAV。" else "还没有保存手机端 API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = ai.prompt,
                        onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(prompt = v) }) },
                        placeholder = { Text("个性化提示词（可选）") },
                        singleLine = false,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(ProfileAction.SaveAiKey) }) { Text("保存设置") }
                        OutlinedButton(onClick = { onAction(ProfileAction.TestAi) }) { Icon(Icons.Outlined.Wifi, contentDescription = null); Text("测试", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.ClearAiKey) }) { Text("清除 Key") }
                    }
                }
            }
        }
    }
}
