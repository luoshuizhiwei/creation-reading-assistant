package com.creationreadingassistant.ui.screen.inspiration.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.theme.PillShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.screen.inspiration.InspirationAction
import com.creationreadingassistant.ui.screen.inspiration.STATUS_OPTIONS
import com.creationreadingassistant.ui.screen.inspiration.TYPE_OPTIONS
import com.creationreadingassistant.ui.screen.inspiration.getStatusLabel
import com.creationreadingassistant.ui.screen.inspiration.getTypeLabel
import com.creationreadingassistant.ui.screen.inspiration.parseTagInput
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.InspirationDraft
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import com.creationreadingassistant.ui.viewmodel.unknownCategoryLabel

/**
 * 编辑器保存时的来源解析（任务94-v1）。
 *
 * 编辑器只暴露 bookId / locationLabel / excerpt 三个来源输入；章节、进度、locator
 * 与书名/作者快照都不在界面上。旧实现无条件重建 `InspirationSourceInfo`，导致
 * 「只改分类」保存时 chapterTitle / progressPercent / locatorJson 被清空、书籍字典
 * 未加载时书名/作者也会丢。
 *
 * 规则：
 * 1. 三个输入与预填值完全一致 → 原样复用 `original`（完整保留所有字段，含 locator）；
 * 2. 用户确实改了输入、且仍是同一本书 → 只覆盖被编辑的字段，位置元数据继续保留；
 * 3. 换到另一本书（或原本没有来源）→ 不继承旧书的章节/进度/locator，避免把旧定位
 *    盲目搬到新书或新位置；
 * 4. 三个输入都被清空 → 来源整体移除（沿用旧行为）。
 */
internal fun resolveEditorSource(
    original: InspirationSourceInfo?,
    sourceBookId: String,
    sourceLocation: String,
    sourceExcerpt: String,
    selectedBook: BookEntity?,
): InspirationSourceInfo? {
    val originalBookId = original?.bookId
    val inputsUnchanged = sourceBookId == (originalBookId ?: "") &&
        sourceLocation == (original?.locationLabel ?: "") &&
        sourceExcerpt == (original?.excerpt ?: "")
    if (inputsUnchanged) return original

    val hasAnyInput = sourceBookId.isNotBlank() ||
        sourceLocation.isNotBlank() ||
        sourceExcerpt.isNotBlank()
    if (!hasAnyInput) return null

    val newBookId = sourceBookId.ifBlank { null }
    val sameBook = original != null && originalBookId != null && originalBookId == newBookId
    return InspirationSourceInfo(
        bookId = newBookId,
        bookTitle = selectedBook?.title?.takeIf { it.isNotBlank() }
            ?: (if (sameBook) original?.bookTitle else null),
        bookAuthor = selectedBook?.author?.takeIf { it.isNotBlank() }
            ?: (if (sameBook) original?.bookAuthor else null),
        chapterTitle = if (sameBook) original?.chapterTitle else null,
        locationLabel = sourceLocation.trim().ifBlank { null },
        progressPercent = if (sameBook) original?.progressPercent else null,
        excerpt = sourceExcerpt.trim().ifBlank { null },
        locatorJson = if (sameBook) original?.locatorJson else null,
    )
}

/**
 * 由编辑器状态生成保存草稿。真实 UI 的保存按钮走同一函数，避免测试另写一份镜像逻辑。
 * `categoryIds = null` 表示用户没动过分类（ViewModel 保留旧值）；空列表表示明确清空。
 */
internal fun buildEditorDraft(
    id: String,
    title: String,
    body: String,
    type: String,
    status: String,
    tagsInput: String,
    sourceBookId: String,
    sourceLocation: String,
    sourceExcerpt: String,
    selectedBook: BookEntity?,
    originalSource: InspirationSourceInfo?,
    selectedCategories: List<String>,
    categoriesTouched: Boolean,
): InspirationDraft = InspirationDraft(
    id = id,
    title = title.trim(),
    body = body,
    type = type,
    status = status,
    tags = parseTagInput(tagsInput),
    source = resolveEditorSource(
        original = originalSource,
        sourceBookId = sourceBookId,
        sourceLocation = sourceLocation,
        sourceExcerpt = sourceExcerpt,
        selectedBook = selectedBook,
    ),
    categoryIds = if (categoriesTouched) selectedCategories else null,
)

/**
 * 灵感编辑器页内容。不创建 Scaffold，不创建顶栏。
 * 脏状态通过 EditorDirtyState（由 Route/Screen 聚合），不再散落在多个 Boolean。
 * 保存动作 → Route 会消费 EditorSaved 动作切换到详情。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun InspirationEditor(
    existing: InspirationEntity?,
    viewModel: InspirationViewModel,
    onAction: (InspirationAction) -> Unit,
    modifier: Modifier = Modifier,
    /** 任务93-v1-A：活动分类目录（id to 名称）；仅用于展示与选择，不写书籍分类关系。 */
    availableCategories: List<Pair<String, String>> = emptyList(),
    categoriesLoading: Boolean = false,
    categoriesFailed: Boolean = false,
) {
    val spec = LocalComponentSpec.current
    val books by viewModel.books.collectAsStateWithLifecycle()

    // 草稿生命周期绑定「正在编辑的条目」：existing 由 null 变为实体（列表晚于编辑器到达）
    // 或切换到另一条时必须重新预填，否则会把空白/上一条内容存回去。
    val editorKey = existing?.id ?: ""
    var title by remember(editorKey) { mutableStateOf(existing?.title ?: "") }
    var body by remember(editorKey) { mutableStateOf(existing?.body ?: "") }
    var type by remember(editorKey) { mutableStateOf(existing?.type ?: "note") }
    var status by remember(editorKey) { mutableStateOf(existing?.status ?: "inbox") }
    val src0 = existing?.let { viewModel.sourceOf(it) }
    val tags0 = existing?.let { viewModel.tagsOf(it) } ?: emptyList()
    var tagsInput by remember(editorKey) { mutableStateOf(tags0.joinToString("，")) }
    var sourceBookId by remember(editorKey) { mutableStateOf(src0?.bookId ?: "") }
    var sourceLocation by remember(editorKey) { mutableStateOf(src0?.locationLabel ?: "") }
    var sourceExcerpt by remember(editorKey) { mutableStateOf(src0?.excerpt ?: "") }
    // 任务93-v1-A：分类预填自 payload.categoryIds（不依赖目录是否已加载）。
    // categoriesTouched=false 时保存传 null，ViewModel 原样保留旧 id；
    // 只有用户点过分类胶囊才接管，空列表即“明确清空”。
    val categories0 = existing?.let { viewModel.categoryIdsOf(it) } ?: emptyList()
    var selectedCategories by remember(editorKey) { mutableStateOf(categories0) }
    var categoriesTouched by remember(editorKey) { mutableStateOf(false) }
    var error by remember(editorKey) { mutableStateOf("") }

    // 脏判定基线必须随 editorKey 一起重置，否则切换条目后会把「新条目 vs 旧基线」误判为已修改。
    val initialSignature = remember(editorKey) {
        listOf(
            title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt,
            selectedCategories.joinToString(","), categoriesTouched.toString(),
        ).joinToString("|")
    }
    val signature = listOf(
        title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt,
        selectedCategories.joinToString(","), categoriesTouched.toString(),
    ).joinToString("|")

    LaunchedEffect(signature) {
        onAction(InspirationAction.EditorSetDirty(signature != initialSignature))
    }

    var typeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var bookExpanded by remember { mutableStateOf(false) }

    val selectedBook = books.firstOrNull { it.id == sourceBookId }

    fun save() {
        if (title.isBlank() && body.isBlank() && sourceExcerpt.isBlank()) {
            error = "请至少填写标题、正文或来源摘录中的一项。"
            return
        }
        error = ""
        val isNew = existing == null
        val id = existing?.id ?: java.util.UUID.randomUUID().toString()
        val draft = buildEditorDraft(
            id = id,
            title = title,
            body = body,
            type = type,
            status = status,
            tagsInput = tagsInput,
            sourceBookId = sourceBookId,
            sourceLocation = sourceLocation,
            sourceExcerpt = sourceExcerpt,
            selectedBook = selectedBook,
            originalSource = src0,
            selectedCategories = selectedCategories,
            categoriesTouched = categoriesTouched,
        )
        viewModel.saveInspiration(draft)
        onAction(InspirationAction.EditorSaved(newId = id, wasNew = isNew))
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .animateEnter(reducedMotion = rememberReducedMotion())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── 1. 灵感主体卡片 ──
        IslandCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IslandSectionHeader(
                    title = "灵感内容",
                    icon = Icons.Outlined.Edit,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("标题")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("给这条灵感一个清楚的名字") },
                    singleLine = true,
                    shape = RoundedCornerShape(spec.hintRadius),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("类型")
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = getTypeLabel(type),
                                onValueChange = {},
                                readOnly = true,
                                shape = RoundedCornerShape(spec.hintRadius),
                                colors = textFieldColors,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false },
                            ) {
                                TYPE_OPTIONS.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { type = value; typeExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("状态")
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = getStatusLabel(status),
                                onValueChange = {},
                                readOnly = true,
                                shape = RoundedCornerShape(spec.hintRadius),
                                colors = textFieldColors,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false },
                            ) {
                                STATUS_OPTIONS.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { status = value; statusExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                FieldLabel("正文")
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("写下设定、冲突、人物动作或可以继续发展的片段……") },
                    shape = RoundedCornerShape(spec.hintRadius),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 10,
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("标签")
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    placeholder = { Text("用逗号或空格分隔多个标签") },
                    singleLine = true,
                    shape = RoundedCornerShape(spec.hintRadius),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                val previewTags = parseTagInput(tagsInput)
                if (previewTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        previewTags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                shape = PillShape,
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // 任务93-v1-A：分类选择（复用活动分类目录，ID 存入 payload.categoryIds）。
                Spacer(Modifier.height(12.dp))
                FieldLabel("分类")
                CategorySelector(
                    selectedIds = selectedCategories,
                    available = availableCategories,
                    loading = categoriesLoading,
                    failed = categoriesFailed,
                    onToggle = { id ->
                        categoriesTouched = true
                        selectedCategories = if (id in selectedCategories) {
                            selectedCategories - id
                        } else {
                            selectedCategories + id
                        }
                    },
                )
            }
        }

        // ── 2. 来源与阅读出处（可选） ──
        IslandCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IslandSectionHeader(
                    title = "来源出处（可选）",
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("关联来源书籍")
                ExposedDropdownMenuBox(
                    expanded = bookExpanded,
                    onExpandedChange = { bookExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedBook?.let {
                            "《${it.title}》${it.author?.let { a -> " · $a" } ?: ""}"
                        } ?: "不关联书籍",
                        onValueChange = {},
                        readOnly = true,
                        shape = RoundedCornerShape(spec.hintRadius),
                        colors = textFieldColors,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = bookExpanded,
                        onDismissRequest = { bookExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("不关联书籍") },
                            onClick = { sourceBookId = ""; bookExpanded = false },
                        )
                        books.forEach { book ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "《${book.title}》${book.author?.let { " · $it" } ?: ""}",
                                    )
                                },
                                onClick = { sourceBookId = book.id; bookExpanded = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                FieldLabel("章节或阅读位置")
                OutlinedTextField(
                    value = sourceLocation,
                    onValueChange = { sourceLocation = it },
                    placeholder = { Text("例如：第 12 章 / 38.5%") },
                    singleLine = true,
                    shape = RoundedCornerShape(spec.hintRadius),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("原文摘录")
                OutlinedTextField(
                    value = sourceExcerpt,
                    onValueChange = { sourceExcerpt = it },
                    placeholder = { Text("记录触发灵感的原文段落，独立保存不会混入正文") },
                    shape = RoundedCornerShape(spec.hintRadius),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 6,
                )
            }
        }

        if (error.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(spec.hintRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { save() },
            shape = PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (existing != null) "保存修改" else "保存灵感",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * 任务93-v1-A：灵感分类选择器（复用活动分类目录）。
 *
 * - 目录加载中/失败时只显示状态，不把已保存分类标成不可用，也不删除任何 id；
 * - 目录为空时给明确空态，沿用书架的分类管理，不另建管理系统；
 * - 已保存但不在目录中的旧 id 仍显示为「不可用分类」，用户可点按明确移除。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(
    selectedIds: List<String>,
    available: List<Pair<String, String>>,
    loading: Boolean,
    failed: Boolean,
    onToggle: (String) -> Unit,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            loading -> Text("分类目录加载中…", style = labelStyle, color = muted)
            failed -> Text("分类目录暂不可用；已保存的分类不会被清除。", style = labelStyle, color = muted)
            available.isEmpty() && selectedIds.isEmpty() ->
                Text("还没有可选分类。可先在书架的分类管理中创建。", style = labelStyle, color = muted)
        }
        if (!loading && !failed && available.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                available.forEach { (id, name) ->
                    val selected = id in selectedIds
                    Surface(
                        onClick = { onToggle(id) },
                        shape = PillShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            LocalComponentSpec.current.hairlineBorderWidth,
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                        ),
                    ) {
                        Text(
                            name,
                            style = labelStyle.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                muted
                            },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (!loading && !failed) {
            val availableIds = available.mapTo(HashSet()) { it.first }
            val unknownIds = selectedIds.filterNot { it in availableIds }
            if (unknownIds.isNotEmpty()) {
                Text(
                    "以下分类已不在分类目录中，仍会保留；点按可移除。",
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    unknownIds.forEach { id ->
                        Surface(
                            onClick = { onToggle(id) },
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = BorderStroke(
                                LocalComponentSpec.current.hairlineBorderWidth,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            ),
                        ) {
                            Text(
                                unknownCategoryLabel(id),
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
