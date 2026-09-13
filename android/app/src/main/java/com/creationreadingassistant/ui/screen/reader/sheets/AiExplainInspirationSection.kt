package com.creationreadingassistant.ui.screen.reader.sheets

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiExplainInspirationSection(
    categories: List<CategoryEntity>,
    selectedCategoryIds: List<String>,
    onCategoryToggle: (String) -> Unit,
    newCategoryInput: String,
    onNewCategoryInputChange: (String) -> Unit,
    onCreateCategory: () -> Unit,
    inspirationTags: List<TagEntity>,
    selectedTagNames: List<String>,
    onTagToggle: (String) -> Unit,
    onCustomTagRemove: (String) -> Unit,
    newTagInput: String,
    onNewTagInputChange: (String) -> Unit,
    onCreateTag: () -> Unit,
    canDeposit: Boolean,
    onDeposit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val depositInteraction = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
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
                            onCategoryToggle(c.id)
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
                onValueChange = onNewCategoryInputChange,
                onAdd = onCreateCategory,
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
                            onTagToggle(t.name)
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
                            onCustomTagRemove(name)
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
                onValueChange = onNewTagInputChange,
                onAdd = onCreateTag,
            )

            // 底部“存入灵感库”升级为全宽高质感微岛主按钮
            Surface(
                onClick = {
                    if (canDeposit) {
                        haptic(HapticFeedbackType.LongPress)
                        onDeposit()
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
