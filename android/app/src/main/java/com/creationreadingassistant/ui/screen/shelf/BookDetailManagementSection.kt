package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun ChipRow(names: List<String>, active: List<String>, onClick: () -> Unit) {
    if (names.isEmpty()) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            val isActive = active.contains(name)
            SelectablePill(
                text = (if (isActive) "✓ " else "") + name,
                selected = isActive,
                onClick = onClick,
            )
        }
    }
}

@Composable
internal fun BookDetailFileInfoSection(book: BookEntity) {
    DetailIslandCard {
        SectionTitle("文件信息")
        InfoRow("原始文件名", book.original_file_name ?: "未知")
        InfoRow("导入时间", (book.imported_at ?: "未知").take(19).replace("T", " "))
        InfoRow("文件大小", formatBytes(book.size))
        InfoRow("格式", book.format.uppercase())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookDetailShelvesSection(
    shelves: List<ShelfEntity>,
    onRemoveShelf: (String) -> Unit,
) {
    DetailIslandCard {
        SectionTitle("所在书单")
        if (shelves.isEmpty()) {
            Text("尚未加入书单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shelves.forEach { shelf ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(shelf.name) },
                        shape = RoundedCornerShape(999.dp),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.size(18.dp),
                                onClick = { onRemoveShelf(shelf.id) },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookDetailCategoriesSection(
    categories: List<CategoryEntity>,
    onRemoveCategory: (String) -> Unit,
) {
    DetailIslandCard {
        SectionTitle("所属分类")
        if (categories.isEmpty()) {
            Text("尚未设置分类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(category.name) },
                        shape = RoundedCornerShape(999.dp),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.size(18.dp),
                                onClick = { onRemoveCategory(category.id) },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookDetailTagsSection(
    allTags: List<TagEntity>,
    assignedTagIds: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    DetailIslandCard {
        SectionTitle("书籍标签 (${assignedTagIds.size})")
        if (allTags.isEmpty()) {
            Text(
                "还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allTags.forEach { tag ->
                    val assigned = assignedTagIds.contains(tag.id)
                    InputChip(
                        selected = assigned,
                        shape = RoundedCornerShape(999.dp),
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            if (assigned) onRemoveTag(tag.id) else onAddTag(tag.id)
                        },
                        label = { Text(tag.name) },
                        trailingIcon = if (assigned) {
                            {
                                IconButton(
                                    modifier = Modifier.size(18.dp),
                                    onClick = { onRemoveTag(tag.id) },
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookDetailDeleteSection(
    book: BookEntity,
    onDelete: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        color = AppError.copy(alpha = 0.05f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, AppError.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptic(HapticFeedbackType.LongPress)
                onDelete(book.id)
            },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppError.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppError, modifier = Modifier.size(AppIconSize.Medium))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("删除本书", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = AppError)
                // 与 feature/library/deletion/DeletionCopy 的范围口径保持一致：
                // 「删除整本资料」移除的是书籍资料与阅读数据（DB 软删除 + 关联清理），
                // 磁盘上的内部正文副本由「移除正文」单独回收，删除动作本身不会释放这部分空间。
                // 这里不得声称已删「本机正文」，否则用户会对数据留存与空间释放做出错误判断。
                Text(
                    DELETE_BOOK_SELF_DESCRIPTION,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppError.copy(alpha = 0.75f),
                )
            }
        }
    }
}
