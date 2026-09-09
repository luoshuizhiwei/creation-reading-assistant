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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
) {
    val spec = LocalComponentSpec.current
    val books by viewModel.books.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "note") }
    var status by remember { mutableStateOf(existing?.status ?: "inbox") }
    val src0 = existing?.let { viewModel.sourceOf(it) }
    val tags0 = existing?.let { viewModel.tagsOf(it) } ?: emptyList()
    var tagsInput by remember { mutableStateOf(tags0.joinToString("，")) }
    var sourceBookId by remember { mutableStateOf(src0?.bookId ?: "") }
    var sourceLocation by remember { mutableStateOf(src0?.locationLabel ?: "") }
    var sourceExcerpt by remember { mutableStateOf(src0?.excerpt ?: "") }
    var error by remember { mutableStateOf("") }

    val initialSignature = remember {
        listOf(title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt)
            .joinToString("|")
    }
    val signature = listOf(title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt)
        .joinToString("|")

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
        val sourceInfo =
            if (sourceBookId.isNotBlank() || sourceLocation.isNotBlank() || sourceExcerpt.isNotBlank()) {
                InspirationSourceInfo(
                    bookId = sourceBookId.ifBlank { null },
                    bookTitle = selectedBook?.title,
                    bookAuthor = selectedBook?.author,
                    locationLabel = sourceLocation.trim().ifBlank { null },
                    excerpt = sourceExcerpt.trim().ifBlank { null },
                )
            } else null
        val isNew = existing == null
        val id = existing?.id ?: java.util.UUID.randomUUID().toString()
        val draft = InspirationDraft(
            id = id,
            title = title.trim(),
            body = body,
            type = type,
            status = status,
            tags = parseTagInput(tagsInput),
            source = sourceInfo,
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
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
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
            }
        }

        // ── 2. 来源与阅读出处（可选） ──
        IslandCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IslandSectionHeader(
                    title = "来源出处（可选）",
                    icon = Icons.Outlined.MenuBook,
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
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
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
