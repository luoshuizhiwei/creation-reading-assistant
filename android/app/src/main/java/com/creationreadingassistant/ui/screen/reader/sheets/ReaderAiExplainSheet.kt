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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val VioletBlue = Color(0xFF6366F1)
private val EmeraldGreen = Color(0xFF059669)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AiExplainSheet(
    aiClient: AiClient,
    selectedText: String,
    bookTitle: String,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSaveInspiration: (String, List<String>, List<String>) -> Unit,
) {
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    // 思考解析中波纹微动画
    val infiniteTransition = rememberInfiniteTransition(label = "explainThinkingWave")
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveAlpha",
    )

    fun callExplain() {
        if (selectedText.isBlank()) return
        loading = true
        error = null
        result = ""
        val contextLimit = readerAiSentCharacterCount(selectedText)
        job = scope.launch(Dispatchers.IO) {
            try {
                aiClient.chatStreaming(
                    "你是一个阅读助手。请对选中的文本进行深入解读：分析其含义、背景、写作手法或潜在寓意。保持专业但易懂。",
                    "请解读这段文字：${selectedText.take(contextLimit)}",
                ) { delta -> result += delta }
                    .onSuccess {
                        loading = false
                        result = readerAiResultForRequestOutcome(result, successful = true)
                    }
                    .onFailure {
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
        title = "AI 词句解读",
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
            // 选中文本微卡片：原正文摘录升级为左侧带有 3dp 墨线竖标的纸墨微岛卡片（14dp 圆角、微半透背景）
            if (selectedText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        // 左侧 3dp 墨线竖标
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "来源摘录",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = selectedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        "请先选中一段正文，再使用 AI 解读。",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // 解读主操作按钮微岛化
            val explainInteraction = remember { MutableInteractionSource() }
            val canExplain = selectedText.isNotBlank()
            Surface(
                onClick = {
                    if (loading) {
                        haptic(HapticFeedbackType.TextHandleMove)
                        job?.cancel()
                    } else {
                        haptic(HapticFeedbackType.LongPress)
                        callExplain()
                    }
                },
                enabled = canExplain || loading,
                shape = RoundedCornerShape(14.dp),
                color = if (loading) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                } else if (canExplain) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                contentColor = if (loading) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else if (canExplain) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                border = BorderStroke(
                    1.dp,
                    if (loading) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    } else if (canExplain) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .bounceable(explainInteraction),
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
                            "取消",
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
                            "解读选中文本",
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

            // AI 解析结果卡片：配备 28dp 紫蓝微彩底座（AutoAwesome 图标），结果展示在发丝描边纸墨微岛中，包含“复制解析”微胶囊
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
                                // 28dp 紫蓝微彩底座
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VioletBlue.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = VioletBlue,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    text = "AI 深度解读",
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
                                            "正在思考与解析…",
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

                        // 快捷操作胶囊（复制解析、直接存入灵感、朗读）
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

                            // 复制解析微胶囊
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
                                        "复制解析",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 直接存入灵感微胶囊
                            val canSaveDirect = canSaveReaderAiResult(result, loading, error)
                            Surface(
                                onClick = {
                                    if (canSaveDirect) {
                                        haptic(HapticFeedbackType.LongPress)
                                        onSaveInspiration(result, selectedTagNames, selectedCategoryIds)
                                    }
                                },
                                enabled = canSaveDirect,
                                shape = PillShape,
                                color = if (canSaveDirect) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    0.8.dp,
                                    if (canSaveDirect) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    },
                                ),
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
                                        tint = if (canSaveDirect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                    Text(
                                        "直接存入灵感",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (canSaveDirect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // 朗读微胶囊
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

                // 灵感沉淀组件微岛化：分类与标签选择区升级为圆润微胶囊芯片轨道（FilterChip 圆角 12dp、选中微高光）、内联快速新建栏、全宽微岛主按钮
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 分类区域
                        ExplainSectionHeader(
                            icon = Icons.Outlined.Style,
                            title = "灵感分类",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            categories.forEach { c ->
                                val active = selectedCategoryIds.contains(c.id)
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        selectedCategoryIds = if (active) selectedCategoryIds - c.id else selectedCategoryIds + c.id
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text(c.name, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = if (active) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = active,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        borderWidth = if (active) 1.2.dp else 0.8.dp,
                                    ),
                                )
                            }
                        }

                        // 新建分类微岛
                        QuickAddInlineBar(
                            placeholder = "新建分类名称…",
                            value = newCategoryInput,
                            onValueChange = { newCategoryInput = it },
                            onAdd = {
                                val n = newCategoryInput.trim()
                                if (n.isNotBlank()) {
                                    val id = onCreateCategory(n)
                                    selectedCategoryIds = selectedCategoryIds + id
                                    newCategoryInput = ""
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // 标签区域
                        ExplainSectionHeader(
                            icon = Icons.Outlined.LocalOffer,
                            title = "灵感标签",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            inspirationTags.forEach { t ->
                                val active = selectedTagNames.contains(t.name)
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        selectedTagNames = if (active) selectedTagNames - t.name else selectedTagNames + t.name
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text(t.name, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = if (active) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = active,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        borderWidth = if (active) 1.2.dp else 0.8.dp,
                                    ),
                                )
                            }
                            selectedTagNames.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        selectedTagNames = selectedTagNames - name
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "移除",
                                            modifier = Modifier.size(11.dp),
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = true,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        borderWidth = 1.2.dp,
                                    ),
                                )
                            }
                        }

                        // 新建标签微岛
                        QuickAddInlineBar(
                            placeholder = "新标签，逗号或空格分隔…",
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            onAdd = {
                                val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                                if (names.isNotEmpty()) {
                                    names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                                    selectedTagNames = (selectedTagNames + names).distinct()
                                    newTagInput = ""
                                }
                            },
                        )

                        // 底部“存入灵感库”升级为全宽高质感微岛主按钮
                        val depositInteraction = remember { MutableInteractionSource() }
                        val canDeposit = canSaveReaderAiResult(result, loading, error)
                        Surface(
                            onClick = {
                                if (canDeposit) {
                                    haptic(HapticFeedbackType.LongPress)
                                    onSaveInspiration(result, selectedTagNames, selectedCategoryIds)
                                }
                            },
                            enabled = canDeposit,
                            shape = RoundedCornerShape(16.dp),
                            color = if (canDeposit) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            contentColor = if (canDeposit) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            border = BorderStroke(
                                1.dp,
                                if (canDeposit) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .bounceable(depositInteraction),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "存入灵感库",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 分区标题带 20dp 独立微图标底座。
 */
@Composable
private fun ExplainSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 新增分类/标签输入框与添加按钮一体化微胶囊卡片。
 */
@Composable
private fun QuickAddInlineBar(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            val canAdd = value.trim().isNotBlank()
            Surface(
                onClick = {
                    if (canAdd) {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onAdd()
                    }
                },
                enabled = canAdd,
                shape = PillShape,
                color = if (canAdd) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                contentColor = if (canAdd) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .padding(start = 6.dp)
                    .bounceable(interaction),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        "添加",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
