package com.creationreadingassistant.ui.screen.reader.sheets

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.annotations.groupByChapterInDocumentOrder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDateTime

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
    val haptic = rememberHaptic(reducedMotion)
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
        // 分享失败必须说出来：静默 no-op 会让用户以为「已经发出去了」。
        // （用户在选择器里主动取消不会抛异常，因此这里只覆盖「没有可用目标」。）
        runCatching { context.startActivity(Intent.createChooser(intent, "分享阅读笔记")) }
            .onFailure {
                Toast.makeText(context, "没有可用的分享目标", Toast.LENGTH_SHORT).show()
            }
    }

    // R3-P1：删除高亮 / 笔记不可逆（高亮可能带着用户手写的批注），
    // 因此先把「删除」变成一次请求，用户确认后才下发回调。回调签名与命令通道保持不变。
    var pendingDelete by remember { mutableStateOf<NotesDeleteRequest?>(null) }
    pendingDelete?.let { request ->
        DeleteAnnotationConfirmDialog(
            request = request,
            onConfirm = {
                pendingDelete = null
                when (request) {
                    is NotesDeleteRequest.Highlight -> onDeleteHighlight(request.entity)
                    is NotesDeleteRequest.Note -> onDeleteNote(request.entity)
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    editingNote?.let { note ->
        HighlightNoteEditDialog(
            highlight = note,
            noteDraft = noteDraft,
            onDraftChange = { noteDraft = it },
            onSave = {
                onEditHighlightNote(note, noteDraft)
                editingNote = null
            },
            onDismiss = { editingNote = null },
        )
    }

    ReaderSheetScaffold(
        title = "笔记与标注",
        trailing = {
            if (highlights.isNotEmpty() || notes.isNotEmpty() || inspirations.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val shareInteraction = remember { MutableInteractionSource() }
                    val exportInteraction = remember { MutableInteractionSource() }

                    // 分享微胶囊
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            shareExport()
                        },
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.bounceable(shareInteraction),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "分享",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "分享",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 导出微胶囊
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            pendingExportMarkdown = currentExportMarkdown()
                            exportLauncher.launch(notesExportFileName(bookTitle, LocalDateTime.now()))
                        },
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.bounceable(exportInteraction),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = "导出",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "导出",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    ) {
        // 顶部“添加当前位置书签”按钮微岛化
        val addBookmarkInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                onAddBookmark()
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .bounceable(addBookmarkInteraction),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "添加当前位置书签",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            // 高亮书摘列表
            if (highlights.isNotEmpty()) {
                // 文档序分组（组内最小 chapterIndex 升序），替代中文标题字典序
                val grouped = groupByChapterInDocumentOrder(
                    highlights,
                    chapterTitleOf = { it.chapter_title },
                    chapterIndexOf = { LocatorCodec.decode(it.locator_json)?.chapterIndex },
                    createdAtOf = { it.created_at },
                )
                grouped.forEach { (chapter, items) ->
                    item {
                        NotesSectionHeader(
                            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            title = chapter?.takeIf { it.isNotBlank() } ?: "未分类",
                            count = items.size,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    itemsIndexed(items, key = { _, h -> h.id }) { index, h ->
                        HighlightItemCard(
                            paper = paper,
                            highlight = h,
                            index = index,
                            reducedMotion = reducedMotion,
                            onDelete = { pendingDelete = NotesDeleteRequest.Highlight(h) },
                            onChangeColor = { c -> onChangeHighlightColor(h, c) },
                            onJump = { onJumpToHighlight(h) },
                            onEditNote = {
                                noteDraft = h.note ?: ""
                                editingNote = h
                            },
                            onConvertToNote = { onHighlightToNote(h) },
                            onConvertToInspiration = { onHighlightToInspiration(h) },
                        )
                    }
                }
            }

            // 笔记与书签
            if (notes.isNotEmpty()) {
                item {
                    NotesSectionHeader(
                        icon = Icons.Outlined.Bookmark,
                        title = "笔记 / 书签",
                        count = notes.size,
                        tint = Color(0xFF2563EB),
                    )
                }
                itemsIndexed(notes, key = { _, n -> n.id }) { index, n ->
                    NoteItemCard(
                        note = n,
                        index = index,
                        reducedMotion = reducedMotion,
                        onJump = { onJumpToBookmark(n) },
                        onDelete = { pendingDelete = NotesDeleteRequest.Note(n) },
                    )
                }
            }

            // 灵感记录
            if (inspirations.isNotEmpty()) {
                item {
                    NotesSectionHeader(
                        icon = Icons.Outlined.Lightbulb,
                        title = "灵感记录",
                        count = inspirations.size,
                        tint = Color(0xFFF59E0B),
                    )
                }
                itemsIndexed(inspirations, key = { _, ins -> ins.id }) { index, ins ->
                    InspirationItemCard(
                        inspiration = ins,
                        index = index,
                        reducedMotion = reducedMotion,
                    )
                }
            }

            // 空状态
            if (highlights.isEmpty() && notes.isEmpty() && inspirations.isEmpty()) {
                item {
                    FullEmptyState(
                        icon = { LineArtBookmark(sizeDp = 72.dp) },
                        title = "还没有笔记或灵感",
                        body = "选中正文即可高亮、存笔记或记为灵感。",
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 待确认的删除请求；确认之前不下发任何删除回调。 */
private sealed interface NotesDeleteRequest {
    val preview: String

    data class Highlight(val entity: HighlightEntity) : NotesDeleteRequest {
        override val preview: String get() = entity.text
    }

    data class Note(val entity: NoteEntity) : NotesDeleteRequest {
        override val preview: String get() = entity.title
    }
}

/**
 * 删除高亮 / 笔记的二次确认。
 *
 * 必须展示**被删内容的摘录**：同一个列表里可能有几十条同类条目，
 * 只说「确定删除吗？」用户无法确认自己点中的是哪一条。
 */
@Composable
private fun DeleteAnnotationConfirmDialog(
    request: NotesDeleteRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = when (request) {
        is NotesDeleteRequest.Highlight -> "高亮"
        is NotesDeleteRequest.Note -> "笔记"
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "删除这条$label？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "「${request.preview.ifBlank { "（无摘录）" }}」将被删除，且无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
