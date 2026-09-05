package com.creationreadingassistant.ui.screen.reader.sheets

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
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
        runCatching { context.startActivity(Intent.createChooser(intent, "分享阅读笔记")) }
    }

    if (editingNote != null) {
        GlassAlertDialog(
            onDismissRequest = { editingNote = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text("编辑高亮笔记", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editingNote?.text?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "摘录：${excerpt.take(60)}${if (excerpt.length > 60) "…" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("笔记内容") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                    )
                }
            },
            confirmButton = {
                val confirmInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        editingNote?.let { onEditHighlightNote(it, noteDraft) }
                        editingNote = null
                    },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.bounceable(confirmInteraction),
                ) {
                    Text(
                        "保存",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        editingNote = null
                    },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.bounceable(cancelInteraction),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
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
                val grouped = highlights.groupBy { it.chapter_title ?: "" }.toSortedMap()
                grouped.forEach { (chapter, items) ->
                    item {
                        NotesSectionHeader(
                            icon = Icons.Outlined.FormatListBulleted,
                            title = if (chapter.isBlank()) "未分类" else chapter,
                            count = items.size,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    itemsIndexed(items, key = { _, h -> h.id }) { index, h ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .listItemEnter(index, reducedMotion),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                // 顶部：20dp 双层高光同心圆 + 摘录正文 + 删除微操作
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    val solidColor = paper.highlightSolid(h.color ?: "yellow")
                                    // 左侧 20dp 双层高光同心圆
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp)
                                            .background(solidColor.copy(alpha = 0.22f), CircleShape)
                                            .border(1.dp, solidColor.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .background(solidColor, CircleShape),
                                        )
                                    }

                                    // 摘录正文排版典雅
                                    Text(
                                        text = h.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                    )

                                    // 删除按钮
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic(HapticFeedbackType.TextHandleMove)
                                                onDeleteHighlight(h)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }

                                // 笔记气泡：纸墨微底座
                                h.note?.takeIf { it.isNotBlank() }?.let { noteContent ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, start = 28.dp, end = 2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.EditNote,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .padding(top = 1.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                noteContent,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                // 颜色切换圆点升级为发丝描边与双层光环的微胶囊网格
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, start = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    HIGHLIGHT_COLORS.forEach { c ->
                                        val isSelected = h.color == c
                                        val cColor = paper.highlightSolid(c)
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    onChangeHighlightColor(h, c)
                                                }
                                                .then(
                                                    if (isSelected) {
                                                        Modifier
                                                            .background(cColor.copy(alpha = 0.25f), CircleShape)
                                                            .border(1.5.dp, cColor, CircleShape)
                                                    } else {
                                                        Modifier.border(
                                                            0.6.dp,
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                            CircleShape,
                                                        )
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 10.dp else 12.dp)
                                                    .background(cColor, CircleShape),
                                            )
                                        }
                                    }
                                }

                                // 操作按钮行微胶囊化
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, start = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    NotesActionPill(
                                        text = "跳转",
                                        icon = Icons.Outlined.NearMe,
                                        onClick = { onJumpToHighlight(h) },
                                    )
                                    NotesActionPill(
                                        text = if (h.note.isNullOrBlank()) "加笔记" else "编辑笔记",
                                        icon = Icons.Outlined.EditNote,
                                        onClick = {
                                            noteDraft = h.note ?: ""
                                            editingNote = h
                                        },
                                    )
                                    NotesActionPill(
                                        text = "转笔记",
                                        icon = Icons.Outlined.FormatQuote,
                                        onClick = { onHighlightToNote(h) },
                                    )
                                    NotesActionPill(
                                        text = "转灵感",
                                        icon = Icons.Outlined.Lightbulb,
                                        onClick = { onHighlightToInspiration(h) },
                                    )
                                }
                            }
                        }
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
                    val isBookmark = n.kind == "bookmark"
                    val podColor = if (isBookmark) Color(0xFFD97706) else Color(0xFF2563EB)
                    val podIcon = if (isBookmark) Icons.Outlined.Bookmark else Icons.Outlined.FormatQuote

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .listItemEnter(index, reducedMotion),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 28dp 独立微彩圆角底座
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(podColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp))
                                    .border(0.6.dp, podColor.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    podIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = podColor,
                                )
                            }

                            // 典雅排版
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    text = n.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                n.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = excerpt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // 跳转微胶囊
                            NotesActionPill(
                                text = "跳转",
                                icon = Icons.Outlined.NearMe,
                                onClick = { onJumpToBookmark(n) },
                            )

                            Spacer(Modifier.width(6.dp))

                            // 删除微操作
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        onDeleteNote(n)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
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
                    val amberColor = Color(0xFFF59E0B)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .listItemEnter(index, reducedMotion),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 28dp 暖琥珀底座 + Lightbulb
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(amberColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp))
                                    .border(0.6.dp, amberColor.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = amberColor,
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    text = ins.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (ins.body.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = ins.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
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

/**
 * 章节与类别分组标题：带 20dp 独立微图标底座与数量微胶囊。
 */
@Composable
private fun NotesSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tint,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
            )
        }
    }
}

/**
 * 操作微胶囊按钮（跳转、笔记、转笔记、转灵感等）。
 */
@Composable
private fun NotesActionPill(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.bounceable(interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 内联笔记对话框（从 ReaderScreen 提取）。
 * 使用回调式 onSaveNote，不直接访问 DAO。全面微岛化升级。
 */
@Composable
internal fun ReaderNoteDialog(
    selectedText: String,
    noteBody: String,
    onNoteBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text("新建笔记", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "摘录：${selectedText.take(60)}${if (selectedText.length > 60) "…" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = onNoteBodyChange,
                    label = { Text("笔记内容") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                )
            }
        },
        confirmButton = {
            val confirmInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onSave()
                },
                shape = PillShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.bounceable(confirmInteraction),
            ) {
                Text(
                    "保存",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            val cancelInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.bounceable(cancelInteraction),
            ) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
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
