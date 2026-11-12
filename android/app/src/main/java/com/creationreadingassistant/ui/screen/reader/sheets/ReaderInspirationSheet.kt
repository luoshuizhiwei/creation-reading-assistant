package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.screen.reader.ReaderPageBackHandler
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.viewmodel.InspirationSaveOutcome
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.util.UUID

/** 字符串列表的 saveable 存取器（标签 / 分类草稿用；ArrayList 可直接入 Bundle）。 */
private val InspirationDraftStringListSaver: Saver<List<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toList() },
)

/**
 * 判断灵感草稿相对初始值是否有修改（Back 确认弹窗的判据；纯函数便于单测）。
 * 标题恢复初始、正文留空、标签/分类回到默认时视为「无修改」，可直接返回。
 */
internal fun inspirationDraftHasChanges(
    initialTitle: String,
    initialTags: List<String>,
    title: String,
    body: String,
    tags: List<String>,
    categoryIds: List<String>,
): Boolean = title != initialTitle ||
    body.isNotBlank() ||
    tags != initialTags ||
    categoryIds.isNotEmpty()

/**
 * 摘录旁写**完整编辑页**（READER-PAGES-v1 P3）。
 *
 * 与旧「记录灵感」底部弹层的差异：
 * - 满高页面：顶栏「返回 / 记灵感 / 保存」——保存固定顶栏，键盘出现仍可达
 *   （IME 只在附页容器根层消费一次，本页不再重复抬高）；
 * - 来源区显示真实书名/章节；摘录默认最多 3 行，有明确展开/收起；无摘录不造占位引文；
 * - 标题轻量单行；正文是主编辑区；标签/分类默认收成摘要行，点击页内展开；
 * - 草稿（标题/正文/标签/分类/在途 id）按 bookId rememberSaveable：覆盖重组与
 *   Activity 配置重建；本批不新增 DB 草稿表（进程被杀后不承诺恢复）；
 * - **保存等待真实落库**：派发后按钮禁用显示「保存中…」；收到 [InspirationSaveOutcome.Saved]
 *   （按本会话稳定 entityId 匹配）才清选区、关页、提示成功；失败保留全部草稿并显示
 *   可见错误，重试复用同一 entityId（upsert 覆盖同条，不重复写入）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspirationPage(
    bookId: String,
    bookTitle: String,
    chapterTitle: String,
    excerpt: String,
    progressPercent: Float,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    saveOutcome: InspirationSaveOutcome?,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSave: (entityId: String, title: String, body: String, tags: List<String>, categoryIds: List<String>) -> Unit,
    onClose: () -> Unit,
    onClearSelection: () -> Unit,
    onSaveNotice: (String) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    // ── 草稿（按书籍保存于 saveable：覆盖重组与配置重建）──────────────────
    val initialTitle = "阅读灵感：$bookTitle"
    val initialTags = listOf(bookTitle, "阅读灵感")
    var draftTitle by rememberSaveable(bookId) { mutableStateOf(initialTitle) }
    var draftBody by rememberSaveable(bookId) { mutableStateOf("") }
    var draftTags by rememberSaveable(bookId, stateSaver = InspirationDraftStringListSaver) {
        mutableStateOf(initialTags)
    }
    var draftCategoryIds by rememberSaveable(bookId, stateSaver = InspirationDraftStringListSaver) {
        mutableStateOf(emptyList<String>())
    }
    var newTagInput by rememberSaveable(bookId) { mutableStateOf("") }
    var newCategoryInput by rememberSaveable(bookId) { mutableStateOf("") }
    var excerptExpanded by rememberSaveable(bookId) { mutableStateOf(false) }
    var taxonomyExpanded by rememberSaveable(bookId) { mutableStateOf(false) }
    /** 本次编辑会话的稳定实体 id：首次保存生成，失败重试复用，成功/放弃后清空。 */
    var pendingEntityId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    /** 有派发未收到结果的保存（保存中禁重复提交）。 */
    var awaitingSave by rememberSaveable(bookId) { mutableStateOf(false) }
    /** 最近一次失败的可见错误（保存中/成功后清空）。 */
    var saveError by remember { mutableStateOf<String?>(null) }

    val hasChanges = inspirationDraftHasChanges(
        initialTitle = initialTitle,
        initialTags = initialTags,
        title = draftTitle,
        body = draftBody,
        tags = draftTags,
        categoryIds = draftCategoryIds,
    )
    val canSave = draftTitle.isNotBlank() || draftBody.isNotBlank() || excerpt.isNotBlank()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }

    fun clearDraft() {
        draftTitle = initialTitle
        draftBody = ""
        draftTags = initialTags
        draftCategoryIds = emptyList()
        newTagInput = ""
        newCategoryInput = ""
        pendingEntityId = null
        awaitingSave = false
        saveError = null
    }

    // ── 保存完成信号消费：只有匹配当前会话 id 的结果才被采信 ────────────────
    LaunchedEffect(saveOutcome) {
        val id = pendingEntityId ?: return@LaunchedEffect
        when (val outcome = saveOutcome) {
            is InspirationSaveOutcome.Saved -> if (outcome.requestId == id) {
                awaitingSave = false
                // 落库成功才：清草稿 → 清选区 → 关页 → 提示（合同 P3 顺序）。
                clearDraft()
                onClearSelection()
                onSaveNotice("已保存灵感，并记录来源阅读位置")
                onClose()
            }

            is InspirationSaveOutcome.Failed -> if (outcome.requestId == id) {
                // 失败：保留全部草稿与稳定 id，展示可见错误，允许重试。
                awaitingSave = false
                saveError = "保存失败：${outcome.message}"
            }

            null -> Unit
        }
    }

    // ── Back 链：有键盘先收键盘（容器级）；保存中保留编辑页；有修改先确认 ──────
    // P1（Root集中修正）：awaitingSave 期间返回**不得**打开「放弃修改」——旧 Saved
    // 到达会无条件清稿，清掉的就是保存期间被锁进去的这份草稿的一致性基线；此处
    // 保留当前编辑页并给出「正在保存」状态。可见返回按钮同规则；确认弹窗的
    // 「放弃修改」再校验一次在途状态，防止弹窗残留越过守卫。
    var confirmDiscard by remember { mutableStateOf(false) }
    var savingBackHint by remember { mutableStateOf(false) }
    ReaderPageBackHandler {
        when {
            awaitingSave -> savingBackHint = true
            hasChanges -> confirmDiscard = true
            else -> onClose()
        }
    }
    if (confirmDiscard) {
        AppAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("有未保存的修改", fontWeight = FontWeight.Bold) },
            text = { Text("离开将丢失本次编辑内容。") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    // 再校验：落库在途时放弃动作失效（不能越过保存锁定清草稿）。
                    if (!awaitingSave) {
                        clearDraft()
                        onClose()
                    }
                }) { Text("放弃修改", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    ReaderSheetScaffold(
        title = "记灵感",
        fullScreen = true,
        onBack = {
            // 可见返回按钮与系统 Back 同一规则：不丢草稿，保存中不打开放弃确认。
            when {
                awaitingSave -> savingBackHint = true
                hasChanges -> confirmDiscard = true
                else -> onClose()
            }
        },
        trailing = {
            TextButton(
                onClick = {
                    // 内部再查在途状态：防止重组间隙重入派发第二份相同请求。
                    if (awaitingSave) return@TextButton
                    haptic(HapticFeedbackType.LongPress)
                    val id = pendingEntityId ?: UUID.randomUUID().toString().also { pendingEntityId = it }
                    awaitingSave = true
                    confirmDiscard = false
                    savingBackHint = false
                    saveError = null
                    onSave(id, draftTitle.trim(), draftBody.trim(), draftTags, draftCategoryIds)
                },
                enabled = canSave && !awaitingSave,
            ) {
                Text(if (awaitingSave) "保存中…" else "保存")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 来源区：真实书名 / 章节 / 进度（无编造）。
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "来源：$bookTitle · ${chapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 来源摘录：默认最多 3 行 + 明确展开/收起（无摘录不渲染，不造占位引文）。
            if (excerpt.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "来源摘录",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = excerpt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (excerptExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { excerptExpanded = !excerptExpanded },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Text(
                                    if (excerptExpanded) "收起摘录" else "展开摘录",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            // 标题：轻量单行字段。保存中 readOnly（文本保持可读/可滚动/可选中，
            // 语义不灰化），阻止在途期间的后续输入进入草稿（P1）。
            OutlinedTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                label = { Text("标题") },
                singleLine = true,
                readOnly = awaitingSave,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // 正文：主要编辑区（有界滚动由外层滚动容器承担）。保存中 readOnly 同上。
            OutlinedTextField(
                value = draftBody,
                onValueChange = { draftBody = it },
                label = { Text("我的想法") },
                readOnly = awaitingSave,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .testTag("insp-lock-body"),
            )

            // 标签 / 分类：默认摘要行，点击页内展开（不铺满标签挤掉正文）。
            // 保存中摘要行不可展开（展开只读无意义，且避免在途期间继续改选）。
            if (!taxonomyExpanded) {
                Surface(
                    onClick = { taxonomyExpanded = true },
                    enabled = !awaitingSave,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Style,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "分类 ${draftCategoryIds.size} · 标签 ${draftTags.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "展开分类与标签",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                // 分类导轨
                InspirationSectionHeader(
                    icon = Icons.Outlined.Style,
                    title = "分类",
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { c ->
                        val active = draftCategoryIds.contains(c.id)
                        InspirationChip(
                            label = c.name,
                            selected = active,
                            enabled = !awaitingSave,
                            onClick = {
                                draftCategoryIds = if (active) {
                                    draftCategoryIds - c.id
                                } else {
                                    draftCategoryIds + c.id
                                }
                            },
                        )
                    }
                }
                QuickAddInlineBar(
                    placeholder = "新建分类名称…",
                    value = newCategoryInput,
                    onValueChange = { newCategoryInput = it },
                    onAdd = {
                        val n = newCategoryInput.trim()
                        if (n.isNotBlank()) {
                            val id = onCreateCategory(n)
                            draftCategoryIds = draftCategoryIds + id
                            newCategoryInput = ""
                        }
                    },
                    enabled = !awaitingSave,
                )

                // 标签导轨
                InspirationSectionHeader(
                    icon = Icons.Outlined.LocalOffer,
                    title = "标签（默认带书名）",
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    inspirationTags.forEach { t ->
                        val active = draftTags.contains(t.name)
                        InspirationChip(
                            label = t.name,
                            selected = active,
                            enabled = !awaitingSave,
                            onClick = {
                                draftTags = if (active) {
                                    draftTags - t.name
                                } else {
                                    draftTags + t.name
                                }
                            },
                        )
                    }
                    // 自定义选中但未入库标签
                    draftTags.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                        InspirationChip(
                            label = name,
                            selected = true,
                            enabled = !awaitingSave,
                            onClick = { draftTags = draftTags - name },
                            onRemove = { draftTags = draftTags - name },
                        )
                    }
                }
                QuickAddInlineBar(
                    placeholder = "新标签，逗号或空格分隔…",
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    onAdd = {
                        val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                        if (names.isNotEmpty()) {
                            names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                            draftTags = (draftTags + names).distinct()
                            newTagInput = ""
                        }
                    },
                    enabled = !awaitingSave,
                )
                TextButton(
                    onClick = { taxonomyExpanded = false },
                    enabled = !awaitingSave,
                ) {
                    Text("收起分类与标签", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 保存中返回提示：Back/返回键在途被守卫时给出「正在保存」状态（P1）。
            if (awaitingSave && savingBackHint) {
                Text(
                    text = "正在保存，请稍候…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 可见错误：失败保留草稿，允许重试。
            saveError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 分区标题带 20dp 独立微图标底座。
 */
@Composable
private fun InspirationSectionHeader(
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
 * 分类与标签微胶囊：带轻触感与选中高光。[enabled]=false 时不可点击
 * （保存中锁定；语义上 Click 动作移除，读屏播报不可用）。
 */
@Composable
private fun InspirationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = rememberHaptic(rememberReducedMotion())
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = PillShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (selected) 1.2.dp else 0.8.dp,
            color = if (selected) primaryColor.copy(alpha = 0.6f) else outlineVariant.copy(alpha = 0.45f),
        ),
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.bounceable(interaction),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (onRemove != null) 6.dp else 12.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = primaryColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onRemove()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * 新增分类/标签输入框与添加按钮一体化微胶囊卡片。[enabled]=false 时输入只读、
 * 添加按钮不可点（保存中锁定）。
 */
@Composable
private fun QuickAddInlineBar(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
                readOnly = !enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (enabled) onAdd() }),
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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onAdd()
                },
                enabled = enabled,
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.bounceable(interaction),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "添加",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
