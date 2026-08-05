package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesSheet(
    paper: ReaderPaperPalette,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    inspirations: List<InspirationEntity>,
    onAddBookmark: () -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onChangeHighlightColor: (HighlightEntity, String) -> Unit,
    onEditHighlightNote: (HighlightEntity, String) -> Unit,
    onHighlightToNote: (HighlightEntity) -> Unit,
    onHighlightToInspiration: (HighlightEntity) -> Unit,
    onJumpToHighlight: (HighlightEntity) -> Unit,
    onExportHighlights: () -> Unit,
) {
    var editingNote by remember { mutableStateOf<HighlightEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val reducedMotion = rememberReducedMotion()

    if (editingNote != null) {
        GlassAlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("编辑高亮笔记") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("笔记内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingNote?.let { onEditHighlightNote(it, noteDraft) }
                    editingNote = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("取消") } },
        )
    }

    ReaderSheetScaffold(
        title = "笔记与标注",
        trailing = {
            if (highlights.isNotEmpty()) {
                TextButton(onClick = onExportHighlights) { Text("导出") }
            }
        },
    ) {
        Button(
            onClick = onAddBookmark,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("添加当前位置书签") }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            if (highlights.isNotEmpty()) {
                val grouped = highlights.groupBy { it.chapter_title ?: "" }.toSortedMap()
                grouped.forEach { (chapter, items) ->
                    item {
                        Text(
                            if (chapter.isBlank()) "未分类" else chapter,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    itemsIndexed(items, key = { _, h -> h.id }) { index, h ->
                        SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).listItemEnter(index, reducedMotion)) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(14.dp).background(paper.highlightSolid(h.color ?: "yellow"), shape = CircleShape),
                                    )
                                    Text(h.text.take(60), Modifier.weight(1f).padding(horizontal = 8.dp))
                                    IconButton(onClick = { onDeleteHighlight(h) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                                }
                                h.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 22.dp, top = 2.dp),
                                    )
                                }
                                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HIGHLIGHT_COLORS.forEach { c ->
                                        androidx.compose.foundation.layout.Box(
                                            Modifier
                                                .size(22.dp)
                                                .background(paper.highlightSolid(c), shape = CircleShape)
                                                .border(
                                                    if (h.color == c) 2.dp else 0.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                )
                                                .clickable { onChangeHighlightColor(h, c) },
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { onJumpToHighlight(h) }) { Text("跳转") }
                                    TextButton(onClick = { noteDraft = h.note ?: ""; editingNote = h }) { Text("笔记") }
                                    TextButton(onClick = { onHighlightToNote(h) }) { Text("转笔记") }
                                    TextButton(onClick = { onHighlightToInspiration(h) }) { Text("转灵感") }
                                }
                            }
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) {
                item { Text("笔记 / 书签", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                itemsIndexed(notes, key = { _, n -> n.id }) { index, n ->
                    SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).listItemEnter(index, reducedMotion)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (n.kind == "bookmark") Icons.Filled.Bookmark else Icons.Filled.FormatQuote,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(n.title, fontWeight = FontWeight.Bold)
                                n.excerpt?.let { Text(it.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                            }
                            IconButton(onClick = { onDeleteNote(n) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                        }
                    }
                }
            }
            if (inspirations.isNotEmpty()) {
                item { Text("灵感记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                itemsIndexed(inspirations, key = { _, ins -> ins.id }) { index, ins ->
                    SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).listItemEnter(index, reducedMotion)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ins.title, fontWeight = FontWeight.Bold)
                                if (ins.body.isNotBlank()) Text(ins.body.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
            if (highlights.isEmpty() && notes.isEmpty() && inspirations.isEmpty()) {
                item {
                    FullEmptyState(
                        icon = { LineArtBookmark(sizeDp = 72.dp) },
                        title = "还没有笔记或灵感",
                        body = "选中正文即可高亮、存笔记或记为灵感。",
                    )
                }
            }
        }
    }
}

/**
 * 内联笔记对话框（从 ReaderScreen 提取）。
 * 使用回调式 onSaveNote，不直接访问 DAO。
 */
@Composable
internal fun ReaderNoteDialog(
    selectedText: String,
    noteBody: String,
    onNoteBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建笔记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedText.isNotBlank()) {
                    Text("摘录：${selectedText.take(60)}${if (selectedText.length > 60) "…" else ""}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = onNoteBodyChange,
                    label = { Text("笔记内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
