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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.feature.annotations.navigationTargetId
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
    /** READER-PAGES-v1：宿主持久列表状态（按 bookId 保存位置）；缺省行为不变。 */
    listState: LazyListState = rememberLazyListState(),
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
            state = listState,
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
 *
 * A01：数据源是 [TocNoteEntry]（高亮 + 非书签笔记），不再是裸 `List<NoteEntity>` ——
 * 页面文案承诺「划线与笔记」，而旧签名只收笔记，高亮**根本没有参数入口**，
 * 于是宿主漏传时页面稳定显示空态。改用统一条目后，两类内容走同一渲染路径。
 */
@Composable
internal fun NotesTabContent(
    entries: List<TocNoteEntry>,
    onPick: (TocNoteEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** READER-PAGES-v1：宿主持久列表状态（按 bookId 保存位置）；缺省行为不变。 */
    listState: LazyListState = rememberLazyListState(),
) {
    if (entries.isEmpty()) {
        FullEmptyState(
            modifier = modifier,
            icon = { LineArtBook(sizeDp = 72.dp) },
            title = "暂无划线与笔记",
            body = "阅读选中文本添加划线或思考，灵感随时沉淀。",
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // key 用 entry.listKey（类型前缀 + rawId）：高亮与笔记是两张表，
            // 同 UUID 跨表出现时裸 id 会让 LazyColumn 抛重复 key 或复用错节点。
            items(entries, key = { it.listKey }) { entry ->
                when (entry) {
                    is TocNoteEntry.Highlight -> HighlightMicroIsland(
                        highlight = entry.highlight,
                        onJump = { onPick(entry) },
                    )

                    is TocNoteEntry.Note -> NoteMicroIsland(
                        note = entry.note,
                        onJump = { onPick(entry) },
                    )
                }
            }
        }
    }
}

/**
 * 目录「笔记」Tab 的统一条目（A01）。
 *
 * 高亮与普通笔记来自两张表、字段不同，但在 Tab 里是同一类内容（页面文案即
 * 「划线与笔记」）。用 sealed 类型承载而不是合并成一个大 data class：
 * - 渲染时能按类型分派到各自微岛，不丢字段；
 * - 回源时 [type] 与 [navigationTarget] 天然带类型，不需要调用方记「这条该按哪张表跳」。
 */
internal sealed interface TocNoteEntry {
    /** 原表主键（不含类型前缀）。 */
    val rawId: String

    /** 回源类型，决定 typed 目标前缀。 */
    val type: AnnotationType

    /**
     * LazyColumn 的 key：类型前缀 + rawId。
     *
     * 与 [navigationTarget] 同值是有意的 —— 二者都由「类型 + rawId」唯一确定，
     * 分开维护只会让它们有机会分叉。
     */
    val listKey: String get() = navigationTarget

    /** typed stableId（`highlight:{id}` / `note:{id}`），供书内回源使用。 */
    val navigationTarget: String

    data class Highlight(val highlight: HighlightEntity) : TocNoteEntry {
        override val rawId: String get() = highlight.id
        override val type: AnnotationType get() = AnnotationType.HIGHLIGHT
        override val navigationTarget: String get() = highlightNavigationTarget(highlight)
    }

    data class Note(val note: NoteEntity) : TocNoteEntry {
        override val rawId: String get() = note.id
        override val type: AnnotationType get() = AnnotationType.NOTE
        override val navigationTarget: String get() = noteNavigationTarget(note)
    }
}

/**
 * 高亮的 typed 回源目标：恒为 `highlight:{id}`。
 *
 * 为什么必须带类型：`resolveAnnotationTarget` 对裸 rawId 保留「先高亮、后笔记」的
 * 旧兼容语义，而 highlights 与 notes 是两张表 —— 同一 rawId 跨表存在时会跳错表。
 */
internal fun highlightNavigationTarget(highlight: HighlightEntity): String =
    navigationTargetId(AnnotationType.HIGHLIGHT, highlight.id)

/**
 * 笔记类记录（含书签）的 typed 回源目标：`note:{id}` 或 `bookmark:{id}`。
 *
 * 书签也是 notes 表的一行，靠 `kind` 区分；类型由 [noteAnnotationType] 单点判定，
 * 避免调用方各自写 `if (kind == "bookmark")` 而漏掉一处。
 */
internal fun noteNavigationTarget(note: NoteEntity): String =
    navigationTargetId(noteAnnotationType(note), note.id)

/** 笔记记录的标注类型：`kind == "bookmark"` 为书签，其余为普通笔记。 */
internal fun noteAnnotationType(note: NoteEntity): AnnotationType =
    if (note.kind == NOTE_KIND_BOOKMARK) AnnotationType.BOOKMARK else AnnotationType.NOTE

/**
 * 「笔记」Tab 的数据源：**高亮 + 非书签笔记**，顺序沿用入参（DAO 的
 * `ORDER BY created_at DESC`），本函数不排序、不查询、不去重。
 *
 * 书签被显式排除：它有自己的 Tab，重复进入笔记 Tab 会让同一条记录出现两次。
 * 这里再过滤一次是幂等防线 —— 宿主已按同一规则过滤，两处都写错才会漏书签。
 */
internal fun tocNoteEntries(
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
): List<TocNoteEntry> =
    highlights.map(TocNoteEntry::Highlight) +
        notes.filter { it.kind != NOTE_KIND_BOOKMARK }.map(TocNoteEntry::Note)

/**
 * 书签判定用的 kind 字面量。
 *
 * 与 `AnnotationNavigationTarget` / `AnnotationExport` 里的同名字面量必须一致；
 * 那两处在 A01 授权边界外，未一并提取为共享常量（见报告 P3）。
 */
internal const val NOTE_KIND_BOOKMARK = "bookmark"

/**
 * 高亮列表项微岛卡片。
 *
 * 与 [NoteMicroIsland] 同构（微底座 + 摘录 + 跳转胶囊），只换图标与色调，
 * 使「划线」与「笔记」在同一列表里视觉可区分而不需要新组件体系。
 */
@Composable
internal fun HighlightMicroIsland(
    highlight: HighlightEntity,
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
                    tint = highlightTint(highlight.color),
                    size = 28.dp,
                    iconSize = 16.dp,
                )

                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = highlight.chapter_title?.takeIf { it.isNotBlank() } ?: "划线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = highlight.text.ifBlank { "（无摘录）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            JumpPill()
        }
    }
}

/** 高亮颜色的微底座色调；未知色键回退主色，不抛错（历史数据可能带自定义色）。 */
private fun highlightTint(color: String?): Color = when (color) {
    "yellow" -> Color(0xFFF59E0B)
    "red" -> Color(0xFFEF4444)
    "green" -> Color(0xFF10B981)
    "blue" -> Color(0xFF2563EB)
    "purple" -> Color(0xFF7C3AED)
    else -> Color(0xFF2563EB)
}

/**
 * 「跳转」微胶囊（A01 新增高亮卡片专用）。
 *
 * 既有 [BookmarkMicroIsland] / [NoteMicroIsland] 各自内联了同形胶囊；本次只为本轮新增的
 * 高亮卡片提取一个私有副本，**不**回改既有两处（A01 不做无谓重构，避免扩大 diff）。
 */
@Composable
private fun JumpPill() {
    val spec = LocalComponentSpec.current
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
