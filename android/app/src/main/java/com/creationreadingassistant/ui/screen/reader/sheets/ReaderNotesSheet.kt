package com.creationreadingassistant.ui.screen.reader.sheets

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesSheet(
    paper: ReaderPaperPalette,
    bookTitle: String,
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
    onJumpToBookmark: (NoteEntity) -> Unit,
) {
    var editingNote by remember { mutableStateOf<HighlightEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current

    // SAF 写文件导出：点击时先固定导出内容，系统对话框返回 uri 后写出。
    var pendingExportMarkdown by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val markdown = pendingExportMarkdown
        pendingExportMarkdown = null
        if (uri == null || markdown == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(markdown.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("无法写入目标文件")
        }.onSuccess {
            Toast.makeText(context, "已导出笔记", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun currentExportMarkdown(): String = buildNotesExportMarkdown(
        bookTitle = bookTitle,
        highlights = highlights,
        notes = notes,
        inspirations = inspirations,
        exportedAt = LocalDateTime.now(),
    )

    fun shareExport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "《${bookTitle}》阅读笔记")
            putExtra(Intent.EXTRA_TEXT, currentExportMarkdown())
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "分享阅读笔记")) }
    }

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
            if (highlights.isNotEmpty() || notes.isNotEmpty() || inspirations.isNotEmpty()) {
                Row {
                    TextButton(onClick = { shareExport() }) { Text("分享") }
                    TextButton(onClick = {
                        pendingExportMarkdown = currentExportMarkdown()
                        exportLauncher.launch(notesExportFileName(bookTitle, LocalDateTime.now()))
                    }) { Text("导出") }
                }
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
                                    IconButton(onClick = { onDeleteHighlight(h) }) { Icon(Icons.Outlined.Delete, contentDescription = "删除") }
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
                                if (n.kind == "bookmark") Icons.Outlined.Bookmark else Icons.Outlined.FormatQuote,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(n.title, fontWeight = FontWeight.Bold)
                                n.excerpt?.let { Text(it.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                            }
                            TextButton(onClick = { onJumpToBookmark(n) }) { Text("跳转") }
                            IconButton(onClick = { onDeleteNote(n) }) { Icon(Icons.Outlined.Delete, contentDescription = "删除") }
                        }
                    }
                }
            }
            if (inspirations.isNotEmpty()) {
                item { Text("灵感记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                itemsIndexed(inspirations, key = { _, ins -> ins.id }) { index, ins ->
                    SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).listItemEnter(index, reducedMotion)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
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

// ============================== 导出 ==============================

/** 导出时间戳格式（文件名与文档头共用，保持一致）。 */
private val EXPORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** SAF 建议文件名：`《书名》笔记-yyyyMMdd-HHmm.md`，非法文件名字符替换为下划线。 */
internal fun notesExportFileName(bookTitle: String, now: LocalDateTime): String {
    val safeTitle = bookTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "未命名" }
    val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "《${safeTitle}》笔记-$stamp.md"
}

/**
 * 组装全书阅读笔记的 Markdown 导出内容：书摘（按章节分组，含批注）、
 * 笔记、书签、灵感四节；空节整体省略。纯函数，JVM 单测锁定结构。
 */
internal fun buildNotesExportMarkdown(
    bookTitle: String,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    inspirations: List<InspirationEntity>,
    exportedAt: LocalDateTime,
): String = buildString {
    appendLine("# 《${bookTitle.ifBlank { "未命名" } }》阅读笔记")
    appendLine()
    appendLine("> 导出于 ${exportedAt.format(EXPORT_TIME_FORMAT)}")
    appendLine()

    if (highlights.isNotEmpty()) {
        appendLine("## 书摘（共 ${highlights.size} 条）")
        appendLine()
        highlights.groupBy { it.chapter_title ?: "" }.toSortedMap().forEach { (chapter, items) ->
            appendLine("### ${if (chapter.isBlank()) "未分类" else chapter}")
            appendLine()
            items.forEachIndexed { i, h ->
                appendLine("${i + 1}. ${h.text}")
                h.note?.takeIf { it.isNotBlank() }?.let { appendLine("   批注：$it") }
            }
            appendLine()
        }
    }

    val plainNotes = notes.filter { it.kind != "bookmark" }
    if (plainNotes.isNotEmpty()) {
        appendLine("## 笔记（共 ${plainNotes.size} 条）")
        appendLine()
        plainNotes.forEach { n ->
            appendLine("- **${n.title}**${if (n.body.isNotBlank()) "：${n.body}" else ""}")
            n.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > 摘录：$it") }
        }
        appendLine()
    }

    val bookmarks = notes.filter { it.kind == "bookmark" }
    if (bookmarks.isNotEmpty()) {
        appendLine("## 书签（共 ${bookmarks.size} 条）")
        appendLine()
        bookmarks.forEach { b ->
            appendLine("- ${b.title}")
            b.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > ${it}") }
        }
        appendLine()
    }

    if (inspirations.isNotEmpty()) {
        appendLine("## 灵感（共 ${inspirations.size} 条）")
        appendLine()
        inspirations.forEach { ins ->
            appendLine("### ${ins.title}")
            appendLine()
            if (ins.body.isNotBlank()) {
                appendLine(ins.body)
                appendLine()
            }
        }
    }
}
