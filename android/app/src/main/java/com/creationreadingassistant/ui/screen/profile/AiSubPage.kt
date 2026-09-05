package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 常见 AI 提供商预设
 */
private data class AiProviderPreset(
    val name: String,
    val baseUrl: String,
    val model: String,
)

private val AI_PRESETS = listOf(
    AiProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    AiProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    AiProviderPreset("Ollama 本地", "http://10.0.2.2:11434/v1", "qwen2.5"),
)

// ============================== AI 伴读配置页 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingsSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val ai = state.ai
    val keyDraft = state.aiKeyDraft
    val keySaved = ai.apiKey.isNotBlank()

    var keyVisible by remember { mutableStateOf(false) }
    var testingConnection by remember { mutableStateOf(false) }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 顶部本地沙箱与安全隔离说明微岛
        item(key = "ai-safety-note") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "端侧安全沙箱隔离",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "手机端 AI API Key 仅保存在应用本地沙箱中，绝不上传、不参与跨设备同步或 WebDAV 备份。支持任何兼容 OpenAI 协议的模型接口。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }

        // 2. 非加密 http 接口警示微岛
        state.aiHttpWarning?.let { warning ->
            item(key = "ai-http-warning") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateEnter(reducedMotion = reducedMotion),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // 3. 模块一：提供商切换与模型端点微岛
        item(key = "ai-provider-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 标题与启用开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    "AI 伴读与智能创作",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "开启后在灵感工坊提供智能扩写、润色与辅助分析",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = ai.enabled,
                            onCheckedChange = { v ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.UpdateAi { copy(enabled = v) })
                            },
                        )
                    }

                    SectionDivider()

                    // 提供商切换微胶囊导轨（OptionPill / SelectablePill）
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "快速接入常用提供商",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AI_PRESETS.forEach { preset ->
                                val isSelected = ai.baseUrl == preset.baseUrl && ai.model == preset.model
                                SelectablePill(
                                    text = preset.name,
                                    selected = isSelected,
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        onAction(ProfileAction.UpdateAi {
                                            copy(baseUrl = preset.baseUrl, model = preset.model)
                                        })
                                    },
                                )
                            }
                        }
                    }

                    // 基础端点输入微岛
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "接口端点 (Base URL)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = ai.baseUrl,
                            onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(baseUrl = v) }) },
                            placeholder = { Text("https://dashscope.aliyuncs.com/compatible-mode/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )
                    }

                    // 模型名称输入微岛
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "模型名称 (Model)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = ai.model,
                            onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(model = v) }) },
                            placeholder = { Text("qwen-plus / deepseek-chat / gpt-4o-mini") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )
                    }
                }
            }
        }

        // 4. 模块二：参数与安全密钥微岛
        item(key = "ai-key-params-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubPageSectionTitle(
                        title = "参数微调与安全密钥",
                        icon = Icons.Outlined.Tune,
                        iconTint = Color(0xFF8B5CF6),
                    )

                    // 温度参数平滑高质感微导轨滑块
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "发散度 / 创造性 (Temperature)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    when {
                                        ai.temperature < 0.4f -> "严谨精准 · 适合考据与逻辑梳理"
                                        ai.temperature < 0.9f -> "均衡适度 · 适合日常润色与写作"
                                        else -> "灵动发散 · 适合头脑风暴与创意扩写"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // 温度微胶囊数值
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    text = "%.1f".format(ai.temperature),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Slider(
                            value = ai.temperature,
                            onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(temperature = v) }) },
                            valueRange = 0f..1.5f,
                            steps = 14,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SectionDivider()

                    // API Key 输入框微岛化（密码可见性切换、隔离说明底座）
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "API 密钥 (API Key)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { v -> onAction(ProfileAction.UpdateAiKeyDraft(v)) },
                            placeholder = {
                                Text(if (keySaved) "已保存 API Key；留空则继续使用" else "粘贴 API Key")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (keyVisible) "隐藏 Key" else "显示 Key",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )

                        // 安全隔离微说明底座
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (keySaved) Color(0xFF10B981).copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(
                                0.8.dp,
                                if (keySaved) Color(0xFF10B981).copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    if (keySaved) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = if (keySaved) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    if (keySaved) "已持久化于本地私有沙箱中；密钥受系统硬件加密层守护，绝不上传。"
                                    else "尚未配置 API Key，可直接在上方输入并点击下方保存。",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (keySaved) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. 模块三：个性化提示词微岛
        item(key = "ai-prompt-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SubPageSectionTitle(
                        title = "创作偏好与人设指令",
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        iconTint = Color(0xFFEC4899),
                    )
                    Text(
                        "为伴读 AI 注入偏好风格或背景设定（可选），将在润色、灵感生成与扩写时作为 System Prompt 生效。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    OutlinedTextField(
                        value = ai.prompt,
                        onValueChange = { v -> onAction(ProfileAction.UpdateAi { copy(prompt = v) }) },
                        placeholder = { Text("例如：保持严谨考据口吻，注重文字韵律，避免浮夸套话…") },
                        singleLine = false,
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                }
            }
        }

        // 6. 模块四：操作按钮组（测试连接微胶囊主按钮、保存与清除）
        item(key = "ai-actions-card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 主操作：保存设置微胶囊按钮
                Button(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onAction(ProfileAction.SaveAiKey)
                    },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "保存 AI 配置",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                // 次级操作：测试连接高质感微胶囊主按钮 + 清除 Key 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 升级的高质感微胶囊测试连接按钮（带加载动画与反馈）
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            testingConnection = true
                            onAction(ProfileAction.TestAi)
                        },
                        enabled = !testingConnection,
                        shape = PillShape,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp),
                    ) {
                        if (testingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (testingConnection) "正在验证…" else "测试模型连通性")
                    }

                    // 清除 Key 微胶囊按钮
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onAction(ProfileAction.ClearAiKey)
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("清空 Key")
                    }
                }
            }
        }
    }
}
