package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ============================== 筛选与编码工具 ==============================

/** 类型筛选的 Saveable 编码（顺序无关的名称集合 → 逗号串）。 */
internal fun encodeTypes(types: Set<AnnotationType>): String =
    types.joinToString(",") { it.name }

internal fun decodeTypes(raw: String): Set<AnnotationType> =
    raw.split(",").mapNotNull { name -> AnnotationType.entries.firstOrNull { it.name == name } }.toSet()
        .ifEmpty { AnnotationType.entries.toSet() }

@Composable
internal fun annotationTypeLabel(type: AnnotationType): String = stringResource(
    when (type) {
        AnnotationType.HIGHLIGHT -> R.string.annotations_type_highlight
        AnnotationType.NOTE -> R.string.annotations_type_note
        AnnotationType.BOOKMARK -> R.string.annotations_type_bookmark
    },
)

// ============================== 筛选栏 ==============================

@Composable
internal fun AnnotationFilterBar(
    types: Set<AnnotationType>,
    onTypesChange: (Set<AnnotationType>) -> Unit,
    books: List<BookEntity>,
    selectedBookId: String,
    onBookChange: (String) -> Unit,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    selectMode: Boolean,
    onToggleSelectMode: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allTypes = AnnotationType.entries.toSet()
            AnnotationFilterChip(
                label = stringResource(R.string.annotations_filter_all_types),
                selected = types == allTypes,
                onClick = { onTypesChange(allTypes) },
            )
            AnnotationType.entries.forEach { type ->
                AnnotationFilterChip(
                    label = annotationTypeLabel(type),
                    selected = type in types,
                    onClick = {
                        onTypesChange(if (type in types) types - type else types + type)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            AnnotationFilterChip(
                label = stringResource(
                    if (selectMode) R.string.annotations_select_exit else R.string.annotations_select_enter,
                ),
                selected = selectMode,
                onClick = onToggleSelectMode,
                icon = Icons.Outlined.Checklist,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 书籍筛选（仅列出有笔记条目的书）
            Box {
                var bookMenuOpen by remember { mutableStateOf(false) }
                val selectedTitle = books.firstOrNull { it.id == selectedBookId }?.title
                    ?: stringResource(R.string.annotations_filter_all_books)
                Surface(
                    onClick = { bookMenuOpen = true },
                    shape = spec.pillShape,
                    color = if (selectedBookId.isBlank()) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        spec.hairlineBorderWidth,
                        if (selectedBookId.isBlank()) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Book,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = selectedTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(
                    expanded = bookMenuOpen,
                    onDismissRequest = { bookMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.annotations_filter_all_books)) },
                        onClick = {
                            bookMenuOpen = false
                            onBookChange("")
                        },
                    )
                    books.forEach { book ->
                        DropdownMenuItem(
                            text = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                bookMenuOpen = false
                                onBookChange(book.id)
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.annotations_filter_keyword_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = RoundedCornerShape(spec.hintRadius),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
internal fun AnnotationFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val spec = LocalComponentSpec.current
    Surface(
        onClick = onClick,
        shape = spec.pillShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(
            spec.hairlineBorderWidth,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
