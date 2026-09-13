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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.screen.inspiration.InspirationAction
import com.creationreadingassistant.ui.screen.inspiration.aiActionLabel
import com.creationreadingassistant.ui.screen.inspiration.formatDetailTime
import com.creationreadingassistant.ui.screen.inspiration.getStatusLabel
import com.creationreadingassistant.ui.screen.inspiration.getTypeLabel
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.InspirationAdoptionRecord
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
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
    payload: InspirationPayloadData?,
    tags: List<String>,
    viewModel: InspirationViewModel,
    onAction: (InspirationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    // observeVariants 每次调用都返回新 Flow；collectAsStateWithLifecycle 内部以 Flow 实例为
    // produceState 的 key，直接在组合中调用会在每次重组时重新订阅。按 entity.id 记住同一实例。
    val variantsFlow = remember(entity.id) { viewModel.observeVariants(entity.id) }
    val variants by variantsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

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
        IslandCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = PillShape,
                        border = BorderStroke(spec.hairlineBorderWidth, accentColor.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(PillShape)
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
            IslandCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IslandSectionHeader(
                        title = "来源书籍与阅读足迹",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        onClick = { onAction(InspirationAction.OpenCurrentSource) },
                        shape = RoundedCornerShape(spec.hintRadius),
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
                            IconPedestal(
                                icon = Icons.Outlined.Book,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 36.dp,
                                iconSize = 20.dp,
                            )
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
                            shape = RoundedCornerShape(spec.hintRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                spec.hairlineBorderWidth,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
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
                                        .clip(PillShape)
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

                    // R5-I2：携带有效 source locator 的摘录可精确回源（临时查阅，返回不丢阅读位置）
                    val locateRoute = inspirationLocateRoute(source.bookId, source.locatorJson)
                    if (locateRoute != null) {
                        FilledTonalButton(
                            onClick = {
                                onAction(
                                    InspirationAction.InspectSourceLocator(
                                        bookId = source.bookId ?: return@FilledTonalButton,
                                        locatorJson = source.locatorJson ?: return@FilledTonalButton,
                                    ),
                                )
                            },
                            shape = PillShape,
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Text("查阅原文位置", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── 2.b 多摘录素材卡：聚合的各来源摘录（每条可独立回源）──
        val aggregatedExcerpts = payload?.excerpts.orEmpty()
        if (aggregatedExcerpts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            IslandCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IslandSectionHeader(
                        title = "聚合摘录 · ${aggregatedExcerpts.size} 条来源",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    aggregatedExcerpts.forEach { excerpt ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(spec.hintRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                spec.hairlineBorderWidth,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        listOfNotNull(
                                            excerpt.bookTitle?.let { "《$it》" },
                                            excerpt.chapterTitle,
                                        ).joinToString(" · ").ifBlank { "来源快照" },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (inspirationLocateRoute(excerpt.bookId, excerpt.locatorJson) != null) {
                                        TextButton(onClick = {
                                            onAction(
                                                InspirationAction.InspectSourceLocator(
                                                    bookId = excerpt.bookId ?: return@TextButton,
                                                    locatorJson = excerpt.locatorJson ?: return@TextButton,
                                                ),
                                            )
                                        }) {
                                            Text("定位", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (!excerpt.excerpt.isNullOrBlank()) {
                                    Text(
                                        excerpt.excerpt,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 2.c 采用去向（R5-I1：采用记录与原文摘录/用户想法/AI 内容分开）──
        AdoptionSection(
            adoptions = payload?.adoptions.orEmpty(),
            onAdd = { kind, value, note ->
                viewModel.addAdoption(entity.id, kind, value, note) { ok ->
                    onAction(
                        InspirationAction.ShowMessage(
                            if (ok) "已记录采用去向，素材标记为已采用。" else "去向内容不能为空。",
                        ),
                    )
                }
            },
            onDelete = { record -> viewModel.removeAdoption(entity.id, record) },
        )

        // ── 3. AI 灵感工坊 ──
        Spacer(Modifier.height(14.dp))
        IslandCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IslandSectionHeader(
                    title = "AI 灵感工坊",
                    icon = Icons.Outlined.AutoAwesome,
                    tint = MaterialTheme.colorScheme.primary,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
                            if (generatingAction != null) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    },
                )
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
    IslandCard(
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

/**
 * R5-I2：灵感摘录的「临时查阅原文」route。
 * 与「我的 → 阅读笔记」同判据（[readerTemporaryRouteForSource] 拒绝无全局偏移的 locator）；
 * 无有效坐标返回 null，调用方据此隐藏入口，绝不伪造 offset=0 的假位置。
 */
private fun inspirationLocateRoute(bookId: String?, locatorJson: String?): String? {
    if (bookId.isNullOrBlank() || locatorJson.isNullOrBlank()) return null
    val locator = LocatorCodec.decode(locatorJson)
    return readerTemporaryRouteForSource(
        bookId = bookId,
        legacyOffset = locator?.legacyOffset,
        chapterIndex = locator?.chapterIndex,
        charOffset = locator?.charOffset,
    )
}

/**
 * 采用去向区块（R5-I1）：文字/链接两类去向记录 + 新增对话框 + 删除。
 * 记录只追加在 payload，不修改素材正文；首次采用把状态推进为「已采用」。
 */
@Composable
private fun AdoptionSection(
    adoptions: List<InspirationAdoptionRecord>,
    onAdd: (kind: String, value: String, note: String?) -> Unit,
    onDelete: (InspirationAdoptionRecord) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Spacer(Modifier.height(14.dp))
    IslandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IslandSectionHeader(
                title = "采用去向",
                icon = Icons.Outlined.Link,
                tint = MaterialTheme.colorScheme.primary,
                trailing = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("记录", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
            if (adoptions.isEmpty()) {
                Text(
                    "还没有采用记录。素材被写进正文或引用后，在这里记下文字或链接去向。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                adoptions.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = PillShape,
                        ) {
                            Text(
                                if (record.kind == InspirationAdoptionRecord.KIND_LINK) "链接" else "文字",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                record.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (!record.note.isNullOrBlank()) {
                                Text(
                                    record.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            formatDetailTime(record.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        TextButton(onClick = { onDelete(record) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除采用记录",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var kind by remember { mutableStateOf(InspirationAdoptionRecord.KIND_TEXT) }
        var value by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("记录采用去向") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            InspirationAdoptionRecord.KIND_TEXT to "文字去向",
                            InspirationAdoptionRecord.KIND_LINK to "链接去向",
                        ).forEach { (k, label) ->
                            Surface(
                                color = if (kind == k) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = PillShape,
                                onClick = { kind = k },
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = {
                            Text(
                                if (kind == InspirationAdoptionRecord.KIND_LINK) {
                                    "链接（URL 或本地路径）"
                                } else {
                                    "文字去向（用在哪一章/哪个作品）"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotBlank(),
                    onClick = {
                        onAdd(kind, value, note)
                        showAddDialog = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            },
        )
    }
}
