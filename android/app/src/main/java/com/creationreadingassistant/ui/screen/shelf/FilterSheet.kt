package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.settings.SavedShelfFilter
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ===================== 底部弹层：筛选 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    selectedShelfId: String,
    selectedCategoryId: String,
    selectedTagIds: Set<String>,
    formatFilter: String,
    onSelectShelf: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSelectFormat: (String) -> Unit,
    onDismiss: () -> Unit,
    /** L1：已保存的动态视图（筛选条件组合的快照）。 */
    savedViews: List<SavedShelfFilter> = emptyList(),
    /** L1：当前是否有筛选生效（决定保存入口可用性）。 */
    canSaveCurrent: Boolean = false,
    onSaveCurrent: (String) -> Unit = {},
    onApplySaved: (SavedShelfFilter) -> Unit = {},
    onDeleteSaved: (String) -> Unit = {},
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 与 BookDetailSheet 等其它 shelf 弹层同一宽度口径（平板/折叠屏居中，窄屏无影响）
        sheetMaxWidth = LocalLayoutTokens.current.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                // 水平内边距走布局令牌（同一 shell 规则），不再各自写 18/20dp 魔数
                .padding(horizontal = LocalLayoutTokens.current.pageHorizontal, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏微岛标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppIconSize.Small),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "筛选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "按格式、书单、分类或标签快速检索",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 0. 动态视图（L1）：保存的筛选条件组合。与上方「书单」不同——
            // 书单是静态书籍集合，动态视图是筛选条件快照，书架内容变化时结果自动跟着变。
            var newViewName by remember { mutableStateOf("") }
            Spacer(modifier = Modifier.height(4.dp))
            FilterSectionHeader("动态视图（保存的筛选）", badge = if (savedViews.isEmpty()) null else "${savedViews.size}")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newViewName,
                    onValueChange = { newViewName = it },
                    placeholder = { Text("给当前筛选起个名字", style = MaterialTheme.typography.labelMedium) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.labelMedium,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (canSaveCurrent && newViewName.isNotBlank()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    onClick = {
                        if (canSaveCurrent && newViewName.isNotBlank()) {
                            onSaveCurrent(newViewName.trim())
                            newViewName = ""
                        }
                    },
                ) {
                    Text(
                        "保存",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (canSaveCurrent && newViewName.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            if (savedViews.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                savedViews.forEach { view ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            onClick = { onApplySaved(view) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                view.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                        TextButton(onClick = { onDeleteSaved(view.name) }) {
                            Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Text(
                    "把常用的筛选组合存成动态视图，下次一键套用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 1. 格式
            FilterSectionHeader("格式")
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipRow(
                listOf("epub" to "EPUB", "txt" to "TXT", "md" to "Markdown"),
                formatFilter,
            ) { onSelectFormat(it) }

            // 2. 书单
            if (shelves.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                FilterSectionHeader("书单", badge = "${shelves.size}")
                Spacer(modifier = Modifier.height(8.dp))
                FilterChipRow(shelves.map { it.id to it.name }, selectedShelfId) { onSelectShelf(it) }
            }

            // 3. 分类
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                FilterSectionHeader("分类", badge = "${categories.size}")
                Spacer(modifier = Modifier.height(8.dp))
                FilterChipRow(categories.map { it.id to it.name }, selectedCategoryId) { onSelectCategory(it) }
            }

            // 4. 标签
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                FilterSectionHeader("标签（可多选）", badge = "${tags.size}")
                Spacer(modifier = Modifier.height(8.dp))
                MultiFilterChipRow(tags.map { it.id to it.name }, selectedTagIds, onToggleTag)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

// 多选 chips：点击切换选中态；「全部」清空整组。
@Composable
internal fun MultiFilterChipRow(options: List<Pair<String, String>>, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterCapsuleChip(
            text = "全部",
            selected = selectedIds.isEmpty(),
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onToggle("")
            },
        )
        options.forEach { (id, name) ->
            val isSelected = id in selectedIds
            FilterCapsuleChip(
                text = name,
                selected = isSelected,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onToggle(id)
                },
            )
        }
    }
}

@Composable
internal fun FilterChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterCapsuleChip(
            text = "全部",
            selected = selected.isEmpty(),
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onSelect("")
            },
        )
        options.forEach { (id, name) ->
            val isSelected = selected == id
            FilterCapsuleChip(
                text = name,
                selected = isSelected,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSelect(id)
                },
            )
        }
    }
}

@Composable
private fun FilterCapsuleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.2.dp else 1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}
