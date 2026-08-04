package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import androidx.compose.foundation.layout.Box
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

// ============================== 阅读与笔记 ==============================

@Composable
internal fun ReadingNotesSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
    page: ProfileSubPage,
) {
    val reducedMotion = rememberReducedMotion()
    val libraryState = state.libraryState
    val books = libraryState.books
    val notes = libraryState.notes
    val bookMap = remember(books) { books.associateBy { it.id } }
    val progressMap = libraryState.progressByBook
    val sessionsByBook = libraryState.readingDurationByBook
    val readingBooks = remember(books, progressMap) {
        books.sortedByDescending { progressMap[it.id]?.last_read_at ?: it.updated_at }
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (page == ProfileSubPage.READING) {
            if (readingBooks.isEmpty()) {
                item {
                    EmptyCard(icon = Icons.AutoMirrored.Filled.MenuBook, title = "还没有阅读记录", body = "打开任意书籍开始阅读后，这里会按最近阅读时间展示档案。")
                }
            } else {
                readingBooks.forEach { book ->
                    item(key = book.id) {
                        val p = progressMap[book.id]
                        Box(Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                            ReadingBookItem(
                                book = book,
                                progress = p,
                                totalMs = sessionsByBook[book.id] ?: 0L,
                            )
                        }
                    }
                }
            }
        } else {
            if (notes.isEmpty()) {
                item {
                    EmptyCard(icon = Icons.Filled.Description, title = "还没有笔记", body = "在阅读页选中文字添加笔记或书签后，它们会出现在这里。")
                }
            } else {
                notes.forEach { note ->
                    item(key = note.id) {
                        Box(Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                            NoteItem(note = note, book = note.book_id?.let { bookMap[it] })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingBookItem(book: BookEntity, progress: ReadingProgressEntity?, totalMs: Long) {
    val pct = (progress?.progress_percent ?: 0f).roundToInt()
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!book.author.isNullOrBlank()) {
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("进度 ${pct}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("累计 ${formatDuration(totalMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            progress?.last_read_at?.let {
                Text("最近阅读：${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteItem(note: NoteEntity, book: BookEntity?) {
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                note.title.ifBlank { "笔记" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val body = note.body.takeIf { it.isNotBlank() } ?: note.excerpt ?: ""
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    book?.let { "《${it.title}》" } ?: (note.chapter_title ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDateTime(note.created_at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
