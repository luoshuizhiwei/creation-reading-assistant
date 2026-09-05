package com.creationreadingassistant.ui.screen.inspiration.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.screen.inspiration.InspirationAction
import com.creationreadingassistant.ui.screen.inspiration.aiActionLabel
import com.creationreadingassistant.ui.screen.inspiration.formatDetailTime
import com.creationreadingassistant.ui.screen.inspiration.getStatusLabel
import com.creationreadingassistant.ui.screen.inspiration.getTypeLabel
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel

/**
 * 灵感详情页内容。不创建 Scaffold，不创建顶栏。
 * 只负责：标题 / 正文 / 标签 / 来源区块 / AI 候选与 AI 动作按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun InspirationDetail(
    entity: InspirationEntity,
    source: InspirationSourceInfo?,
    tags: List<String>,
    viewModel: InspirationViewModel,
    onAction: (InspirationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    val variants by viewModel.observeVariants(entity.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var generatingAction by remember { mutableStateOf<String?>(null) }

    val accentColor = when (entity.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> Color(0xFF2563EB)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .animateEnter(reducedMotion = rememberReducedMotion())
            .padding(16.dp),
    ) {
        // ── 1. 核心灵感卡片 ──
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = PillShape,
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accentColor),
                            )
                            Text(
                                getTypeLabel(entity.type),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = accentColor,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = PillShape,
                    ) {
                        Text(
                            getStatusLabel(entity.status),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        "更新于 ${formatDetailTime(entity.updated_at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    entity.title.ifBlank { "未命名灵感" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                if (entity.body.isNotBlank()) {
                    Text(
                        entity.body,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        "还没有正文。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                shape = PillShape,
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. 来源书籍与阅读足迹微岛 ──
        if (source != null) {
            Spacer(Modifier.height(14.dp))
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "来源书籍与阅读足迹",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        onClick = { onAction(InspirationAction.OpenCurrentSource) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    source.bookTitle?.let { "《$it》" } ?: "来源书籍已不可用",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!source.bookAuthor.isNullOrBlank()) {
                                    Text(
                                        source.bookAuthor,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "前往阅读",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val locationParts = listOfNotNull(
                        source.chapterTitle,
                        source.locationLabel,
                        source.progressPercent?.let {
                            String.format(java.util.Locale.US, "%.1f%%", it)
                        },
                    )
                    if (locationParts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "阅读位置：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                shape = PillShape,
                            ) {
                                Text(
                                    locationParts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (!source.excerpt.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Text(
                                    source.excerpt,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        lineHeight = 22.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 3. AI 灵感工坊 ──
        Spacer(Modifier.height(14.dp))
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "AI 灵感工坊",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    if (variants.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = PillShape,
                        ) {
                            Text(
                                "${variants.size} 个候选",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (generatingAction != null) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                Text(
                    "AI 只生成辅助候选，不会自动覆盖正文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    viewModel.AI_ACTIONS.forEach { (action, label) ->
                        FilledTonalButton(
                            enabled = entity.body.isNotBlank() && generatingAction == null,
                            onClick = {
                                generatingAction = action
                                viewModel.runInspirationAction(entity.id, action, entity.title, entity.body) { ok ->
                                    generatingAction = null
                                    onAction(
                                        InspirationAction.ShowMessage(
                                            if (ok) "AI 候选已保存，原文没有被覆盖。" else "生成失败，请检查 AI 设置",
                                        ),
                                    )
                                }
                            },
                        ) {
                            if (generatingAction == action) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }

        if (variants.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            variants.forEach { v ->
                VariantCard(
                    variant = v,
                    onApply = {
                        viewModel.applyVariant(entity.id, v)
                        onAction(InspirationAction.ShowMessage("已采用候选版本"))
                    },
                    onDelete = {
                        viewModel.deleteVariant(v.id)
                    },
                    onCopy = { text ->
                        onAction(InspirationAction.CopyVariantText(text))
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VariantCard(
    variant: InspirationVariantEntity,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
) {
    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(AppIconSize.Compact),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    aiActionLabel(variant.kind, defaultAiLabels),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!variant.model.isNullOrBlank()) {
                    Text(
                        "· ${variant.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!variant.content.isNullOrBlank()) {
                Text(
                    variant.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { variant.content?.let { onCopy(it) } }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onApply,
                    shape = PillShape,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("采用此版本")
                }
            }
        }
    }
}

private val defaultAiLabels: Map<String, String> = mapOf(
    "polish" to "润色",
    "expand" to "扩写",
    "platform-style" to "平台风格化",
    "conflict" to "生成冲突",
    "humanize" to "去 AI 味",
)
