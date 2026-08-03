package com.creationreadingassistant.ui.screen.inspiration.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .animateEnter(reducedMotion = rememberReducedMotion())
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = spec.pillShape,
            ) {
                Text(
                    getTypeLabel(entity.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = spec.pillShape,
            ) {
                Text(
                    getStatusLabel(entity.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                "更新于 ${formatDetailTime(entity.updated_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            entity.title.ifBlank { "未命名灵感" },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        if (entity.body.isNotBlank()) {
            Text(entity.body, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                "还没有正文。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = spec.pillShape,
                    ) {
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        if (source != null) {
            Spacer(Modifier.height(16.dp))
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Book, contentDescription = null)
                        Text("来源", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onAction(InspirationAction.OpenCurrentSource) },
                        modifier = Modifier.fillMaxWidth().padding(0.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                source.bookTitle ?: "来源书籍已不可用",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!source.bookAuthor.isNullOrBlank()) {
                                Text(
                                    source.bookAuthor,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                        Text(
                            locationParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!source.excerpt.isNullOrBlank()) {
                        Text(
                            "“${source.excerpt}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // ── AI 候选版本（对照 web InspirationVariant） ──
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("AI 候选版本", style = MaterialTheme.typography.titleSmall)
            if (variants.isNotEmpty()) {
                Text(
                    "${variants.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (generatingAction != null) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            "AI 只生成辅助候选，不会自动覆盖正文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(8.dp))
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
                        onAction(InspirationAction.RunInspirationAi(entity.id, action))
                    },
                ) {
                    if (generatingAction == action) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (variants.isEmpty()) {
            Text(
                "需要时再主动生成候选。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    aiActionLabel(variant.kind, defaultAiLabels),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { variant.content?.let { onCopy(it) } }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onApply) { Text("采用") }
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
