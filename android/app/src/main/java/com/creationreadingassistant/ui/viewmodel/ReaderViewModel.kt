package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * 阅读器 ViewModel。
 * - 纯文本（txt / md）：沿用 P1 的 ContentResolver 读取。
 * - EPUB（P2）：经 EpubRepository 解析为原生章节模型，进度落库到 reading_progress。
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val epubRepository: EpubRepository,
    private val noteDao: NoteDao,
) : ViewModel() {

    private companion object {
        const val MAX_IN_MEMORY_TEXT_BYTES = 8L * 1024L * 1024L
    }

    // 纯文本（txt / md）
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    // EPUB
    private val _epubBook = MutableStateFlow<EpubBook?>(null)
    val epubBook: StateFlow<EpubBook?> = _epubBook.asStateFlow()

    private val _chapterIndex = MutableStateFlow(0)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 当前书籍 id（epub 注册进库后才有；纯文本临时打开为 null）。 */
    private val _currentBookId = MutableStateFlow<String?>(null)
    val currentBookId: StateFlow<String?> = _currentBookId.asStateFlow()

    /** 依据扩展名分流：.epub 走原生解析，其余按纯文本。 */
    fun openFile(context: Context, uri: Uri) {
        // 用 path segment 判断扩展名（主线程安全），避免主线程 contentResolver 查询
        val isEpub = (uri.lastPathSegment ?: "").endsWith(".epub", ignoreCase = true)
        if (isEpub) {
            openEpub(context, uri)
        } else {
            loadText(context, uri)
        }
    }

    private fun openEpub(@Suppress("unused") context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val book = epubRepository.openEpub(uri)
                val idx = epubRepository.loadProgress(book.id)
                val clamped = idx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                _title.value = book.title
                _content.value = ""
                _epubBook.value = book
                _currentBookId.value = book.id
                _chapterIndex.value = clamped
            } catch (e: Exception) {
                _error.value = e.message ?: "解析 EPUB 失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun goToChapter(index: Int) {
        val book = _epubBook.value ?: return
        val clamped = index.coerceIn(0, book.chapters.lastIndex)
        _chapterIndex.value = clamped
        viewModelScope.launch(Dispatchers.IO) {
            val percent = if (book.chapters.isEmpty()) 0f else (clamped + 1).toFloat() / book.chapters.size
            epubRepository.saveProgress(book.id, clamped, percent)
        }
    }

    fun loadText(context: Context, uri: Uri) {
        _epubBook.value = null
        _currentBookId.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val name = queryDisplayName(context, uri)
                val size = queryFileSize(context, uri)
                if (size > MAX_IN_MEMORY_TEXT_BYTES) {
                    throw IllegalArgumentException(
                        "TXT/Markdown 文件过大（超过 8 MB），当前版本为避免内存溢出暂不整本载入。" +
                            "请先分割文件，后续版本将支持分块阅读。",
                    )
                }
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    PlainTextDecoder.decode(stream.readBytes()).text
                } ?: ""
                _title.value = name
                _content.value = text
            } catch (e: Exception) {
                _error.value = e.message ?: "读取文件失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 保存一条笔记（可挂当前书籍）。 */
    fun saveNote(quote: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = Instant.now().toString()
            noteDao.upsert(
                NoteEntity(
                    id = UUID.randomUUID().toString(),
                    book_id = _currentBookId.value,
                    title = (body.ifBlank { quote }).take(40),
                    body = body,
                    excerpt = quote.takeIf { it.isNotBlank() },
                    chapter_title = null,
                    progress_percent = null,
                    kind = "note",
                    locator_json = null,
                    payload = "{}",
                    created_at = now,
                    device_id = null,
                    revision = 1,
                    updated_at = now,
                    deleted_at = null,
                ),
            )
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        var name = "未命名文档"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }
}
