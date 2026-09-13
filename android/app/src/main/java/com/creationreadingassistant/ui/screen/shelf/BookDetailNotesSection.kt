package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.theme.AppIconSize

@Composable
internal fun ExpandableRow(
    /** 稳定的条目标识（实体 id）。展开态必须跟着条目走，否则列表中增删一条会让
     *  「展开」状态按位置错位到相邻条目上。 */
    key: Any,
    title: String,
    subtitle: String? = null,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by remember(key) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(AppIconSize.Small),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
internal fun BookDetailNotesSection(
    notes: List<NoteEntity>,
    highlights: List<HighlightEntity>,
) {
    val bookmarks = notes.filter { it.kind == "bookmark" }
    val noteList = notes.filter { it.kind != "bookmark" }
    DetailIslandCard {
        SectionTitle("书签与笔记 · ${bookmarks.size} 书签 · ${noteList.size + highlights.size} 条")
        if (bookmarks.isEmpty() && noteList.isEmpty() && highlights.isEmpty()) {
            Text(
                "阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            bookmarks.forEach { n ->
                ExpandableRow(
                    key = "bookmark-${n.id}",
                    title = "书签 · ${(n.progress_percent ?: 0f).toInt()}%",
                    subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                ) {
                    if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                    if (n.body.isNotBlank()) InfoRow("正文", n.body)
                }
            }
            noteList.forEach { n ->
                ExpandableRow(
                    key = "note-${n.id}",
                    title = "笔记 · ${(n.progress_percent ?: 0f).toInt()}%",
                    subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                ) {
                    if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                    if (n.body.isNotBlank()) InfoRow("正文", n.body)
                }
            }
            highlights.forEach { h ->
                ExpandableRow(
                    key = "highlight-${h.id}",
                    title = "高亮 · ${(h.progress_percent ?: 0f).toInt()}%",
                    subtitle = h.text,
                ) {
                    if (!h.note.isNullOrBlank()) InfoRow("笔记", h.note)
                    if (!h.chapter_title.isNullOrBlank()) InfoRow("章节", h.chapter_title)
                }
            }
        }
    }
}

@Composable
internal fun BookDetailInspirationsSection(inspirations: List<InspirationEntity>) {
    DetailIslandCard {
        SectionTitle("灵感 (${inspirations.size})")
        if (inspirations.isEmpty()) {
            Text(
                "还没有与本书相关的灵感。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            inspirations.forEach { ins ->
                ExpandableRow(key = "insp-${ins.id}", title = ins.title, subtitle = ins.body.take(40)) {
                    InfoRow("类型", ins.type)
                    if (ins.body.isNotBlank()) InfoRow("正文", ins.body)
                }
            }
        }
    }
}
