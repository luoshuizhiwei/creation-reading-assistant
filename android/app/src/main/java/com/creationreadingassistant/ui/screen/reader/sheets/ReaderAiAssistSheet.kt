package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val EmeraldGreen = Color(0xFF059669)
private val MoQingColor = Color(0xFF00796B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiAssistSheet(
    aiClient: AiClient,
    @Suppress("unused") bookTitle: String,
    chapterTitle: String,
    contextText: String,
) {
    var tab by remember { mutableStateOf("summary") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hasContext = contextText.isNotBlank()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    // 思考中波纹微动画
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingWave")
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveAlpha",
    )

    fun callAi() {
        if (!canSubmitReaderAiRequest(tab, contextText, question)) return
        loading = true
        error = null
        result = ""
        val contextLimit = readerAiSentCharacterCount(contextText)
        job = scope.launch(Dispatchers.IO) {
            try {
                val r = when (tab) {
                    "summary" -> aiClient.chatStreaming(
                        "你是一个阅读助手。请用简洁的中文总结下面的文本，控制在 200 字以内，突出核心观点。",
                        contextText.take(contextLimit),
                    ) { delta -> result += delta }
                    "qa" -> aiClient.chatStreaming(
                        "你是一个阅读助手。请基于提供的上下文来回答问题。如果上下文不足以回答，请诚实说明。",
                        "上下文：${contextText.take(contextLimit)}\n\n问题：$question",
                    ) { delta -> result += delta }
                    else -> aiClient.chatStreaming(
                        "你是一个阅读助手。请从下面的文本中提取 3-5 个关键要点，每条要点以「- 」开头，简明扼要。",
                        contextText.take(contextLimit),
                    ) { delta -> result += delta }
                }
                r.onSuccess {
                    loading = false
                    result = readerAiResultForRequestOutcome(result, successful = true)
                }.onFailure {
                    error = it.message
                    loading = false
                    result = readerAiResultForRequestOutcome(result, successful = false)
                }
            } catch (e: CancellationException) {
                loading = false
                result = readerAiResultForRequestOutcome(result, successful = false)
                throw e
            }
        }
    }

    ReaderSheetScaffold(
        title = "AI 阅读伴读",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶层模式导轨：全面升级为现代化圆润微胶囊导轨（SelectablePill 风格，带触觉反馈 rememberHaptic）
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val tabs = listOf(
                        "summary" to "章节摘要",
                        "qa" to "内容问答",
                        "keypoints" to "要点提取",
                    )
                    tabs.forEach { (mode, label) ->
                        val isSelected = tab == mode
                        val tabInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                if (tab != mode) {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    tab = mode
                                    result = ""
                                    error = null
                                }
                            },
                            shape = PillShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            } else {
                                Color.Transparent
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            } else {
                                null
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .bounceable(tabInteraction),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            // 上下文提示微卡片：升级为独立微岛卡片（14dp 圆角、发丝边框），左侧配备 28dp 墨青微彩底座 + Icons.AutoMirrored.Outlined.MenuBook 图标
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 28dp 墨青微彩底座
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MoQingColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MoQingColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (contextText.isNotBlank()) "已选入 ${contextText.length} 字正文" else "当前章节：$chapterTitle",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (contextText.length > AI_READER_CONTEXT_LIMIT) {
                            Text(
                                text = "篇幅较长，仅取前 $AI_READER_CONTEXT_LIMIT 字参与伴读分析",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // 内容问答输入框（qa）：输入框微岛化（14dp 圆角、微半透背景、聚焦发丝描边），单选快速追问微芯片
            if (tab == "qa") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("对选中文本或当前章节提问…") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                    )

                    // 单选快速追问微芯片
                    val quickPrompts = listOf(
                        "核心情节与转折",
                        "人物动机与性格",
                        "伏笔与深层隐喻",
                        "语言特色与写作手法",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickPrompts.forEach { prompt ->
                            val isSelected = question == prompt
                            val chipInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    question = if (isSelected) "" else prompt
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.dp else 0.8.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    },
                                ),
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.bounceable(chipInteraction),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 主操作按钮微岛化
            val submitInteraction = remember { MutableInteractionSource() }
            val canSubmit = canSubmitReaderAiRequest(tab, contextText, question)
            Surface(
                onClick = {
                    if (loading) {
                        haptic(HapticFeedbackType.TextHandleMove)
                        job?.cancel()
                    } else {
                        haptic(HapticFeedbackType.LongPress)
                        callAi()
                    }
                },
                enabled = canSubmit || loading,
                shape = RoundedCornerShape(14.dp),
                color = if (loading) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                } else if (canSubmit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                contentColor = if (loading) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else if (canSubmit) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                border = BorderStroke(
                    1.dp,
                    if (loading) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    } else if (canSubmit) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .bounceable(submitInteraction),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "取消生成",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (tab) {
                                "summary" -> "生成章节摘要"
                                "qa" -> "提问"
                                else -> "提取关键要点"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            error?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // 结果展示与流式气泡：独立纸墨微卡片，生成中显示翡翠绿流式波纹微指示器（“正在思考与生成…”）
            if (result.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Text(
                                    text = when (tab) {
                                        "summary" -> "章节摘要"
                                        "qa" -> "问答解读"
                                        else -> "关键要点"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // 翡翠绿流式波纹微指示器
                            if (loading) {
                                Surface(
                                    shape = PillShape,
                                    color = EmeraldGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, EmeraldGreen.copy(alpha = 0.3f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen.copy(alpha = if (reducedMotion) 1f else waveAlpha)),
                                        )
                                        Text(
                                            "正在思考与生成…",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = EmeraldGreen,
                                        )
                                    }
                                }
                            }
                        }

                        SelectionContainer {
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // 高质感微胶囊操作栏（“复制全文”、“存为灵感”微胶囊）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val copyInteraction = remember { MutableInteractionSource() }
                            val saveInteraction = remember { MutableInteractionSource() }
                            val readInteraction = remember { MutableInteractionSource() }

                            // 复制全文微胶囊
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    clipboard.setText(AnnotatedString(result))
                                },
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.bounceable(copyInteraction),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "复制全文",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 存为灵感微胶囊
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    clipboard.setText(AnnotatedString("【AI伴读灵感】\n$result"))
                                },
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.bounceable(saveInteraction),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "存为灵感",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // 朗读胶囊
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    clipboard.setText(AnnotatedString(result))
                                },
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.bounceable(readInteraction),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "朗读",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!hasContext) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        "当前没有可分析的正文内容。",
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
