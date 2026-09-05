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
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Style
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspirationSheet(
    bookTitle: String,
    chapterTitle: String,
    excerpt: String,
    progressPercent: Float,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSave: (String, String, List<String>, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("阅读灵感：$bookTitle") }
    var body by remember { mutableStateOf("") }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    val canSave = title.isNotBlank() || body.isNotBlank() || excerpt.isNotBlank()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }
    val haptic = rememberHaptic(rememberReducedMotion())

    ReaderSheetScaffold(title = "记录灵感") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部标题输入框微岛化
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            // 想法输入框微岛化
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("我的想法") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp)
                    .padding(top = 8.dp),
            )

            // 来源摘录：左侧 3dp 墨线竖标的纸墨微岛卡片
            if (excerpt.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                            )
                        }
                    }
                }
            }

            // 分类导轨微胶囊化
            InspirationSectionHeader(
                icon = Icons.Outlined.Style,
                title = "分类",
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { c ->
                    val active = selectedCategoryIds.contains(c.id)
                    InspirationChip(
                        label = c.name,
                        selected = active,
                        onClick = {
                            selectedCategoryIds = if (active) {
                                selectedCategoryIds - c.id
                            } else {
                                selectedCategoryIds + c.id
                            }
                        },
                    )
                }
            }

            // 新增分类输入框与添加按钮一体化微胶囊卡片
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
                modifier = Modifier.padding(top = 8.dp),
            )

            // 标签导轨微胶囊化
            InspirationSectionHeader(
                icon = Icons.Outlined.LocalOffer,
                title = "标签（默认带书名）",
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                inspirationTags.forEach { t ->
                    val active = selectedTagNames.contains(t.name)
                    InspirationChip(
                        label = t.name,
                        selected = active,
                        onClick = {
                            selectedTagNames = if (active) {
                                selectedTagNames - t.name
                            } else {
                                selectedTagNames + t.name
                            }
                        },
                    )
                }
                // 自定义选中但未入库标签
                selectedTagNames.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                    InspirationChip(
                        label = name,
                        selected = true,
                        onClick = {
                            selectedTagNames = selectedTagNames - name
                        },
                        onRemove = {
                            selectedTagNames = selectedTagNames - name
                        },
                    )
                }
            }

            // 新增标签输入框与添加按钮一体化微胶囊卡片
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
                modifier = Modifier.padding(top = 8.dp),
            )

            // 底部来源信息微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.padding(top = 14.dp),
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
                    )
                }
            }

            // 保存灵感主按钮微岛化
            val saveInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onSave(title.trim(), body.trim(), selectedTagNames, selectedCategoryIds)
                },
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                border = BorderStroke(
                    1.dp,
                    if (canSave) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .bounceable(saveInteraction),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "保存灵感",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
 * 分类与标签微胶囊：带轻触感与选中高光。
 */
@Composable
private fun InspirationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onAdd()
                },
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

