package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 目录 Sheet 的书签 Tab 内容。
 */
@Composable
internal fun BookmarksTabContent(
    bookmarks: List<NoteEntity>,
    onPickBookmark: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bookmarks.isEmpty()) {
        FullEmptyState(
            modifier = modifier,
            icon = { LineArtBook(sizeDp = 72.dp) },
            title = "暂无书签",
            body = "阅读时点击菜单添加书签，即可在此快速跳转。",
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                BookmarkMicroIsland(
                    bookmark = bm,
                    onJump = { onPickBookmark(bm) },
                )
            }
        }
    }
}

/**
 * 目录 Sheet 的笔记 Tab 内容。
 */
@Composable
internal fun NotesTabContent(
    notes: List<NoteEntity>,
    onPickNote: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notes.isEmpty()) {
        FullEmptyState(
            modifier = modifier,
            icon = { LineArtBook(sizeDp = 72.dp) },
            title = "暂无划线与笔记",
            body = "阅读选中文本添加划线或思考，灵感随时沉淀。",
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(notes, key = { "note_${it.id}" }) { note ->
                NoteMicroIsland(
                    note = note,
                    onJump = { onPickNote(note) },
                )
            }
        }
    }
}

/**
 * 书签列表项：28dp 暖金微底座与「跳转」微胶囊。
 */
@Composable
internal fun BookmarkMicroIsland(
    bookmark: NoteEntity,
    onJump: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onJump()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconPedestal(
                    icon = Icons.Outlined.Bookmark,
                    tint = Color(0xFFD97706),
                    size = 28.dp,
                    iconSize = 16.dp,
                )

                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = bookmark.title.ifBlank { "书签" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    bookmark.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 「跳转」微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "跳转",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 笔记列表项微岛卡片。
 */
@Composable
internal fun NoteMicroIsland(
    note: NoteEntity,
    onJump: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onJump()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconPedestal(
                    icon = Icons.Outlined.EditNote,
                    tint = Color(0xFF7C3AED),
                    size = 28.dp,
                    iconSize = 16.dp,
                )

                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = note.title.ifBlank { "笔记" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    note.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    note.body.takeIf { it.isNotBlank() }?.let { bodyText ->
                        Surface(
                            shape = RoundedCornerShape(spec.pedestalRadius),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        ) {
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }
                }
            }

            // 「跳转」微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.secondary.copy(alpha = spec.hairlineAlpha)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "跳转",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
