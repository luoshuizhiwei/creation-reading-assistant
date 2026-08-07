package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 批注仓储 —— 封装阅读器的高亮 / 笔记（含书签）/ 灵感写入。
 * 由 ReaderViewModel 使用，ViewModel 不再直接依赖这三个 DAO（APP_MODULE_PLAN §7.2）。
 */
@Singleton
class NoteRepository @Inject constructor(
    private val highlightDao: HighlightDao,
    private val noteDao: NoteDao,
    private val inspirationDao: InspirationDao,
) {
    fun observeHighlightsByBook(bookId: String): Flow<List<HighlightEntity>> =
        highlightDao.observeByBook(bookId)

    fun observeNotesByBook(bookId: String): Flow<List<NoteEntity>> =
        noteDao.observeByBook(bookId)

    fun observeInspirationsByBook(bookId: String): Flow<List<InspirationEntity>> =
        inspirationDao.observeByBook(bookId)

    suspend fun saveHighlight(highlight: HighlightEntity) = highlightDao.upsert(highlight)

    /** 软删除高亮（置 deleted_at），不存在时为无操作。 */
    suspend fun deleteHighlight(highlightId: String) {
        val existing = highlightDao.getById(highlightId) ?: return
        highlightDao.upsert(existing.copy(deleted_at = Instant.now().toString()))
    }

    suspend fun updateHighlightColor(highlightId: String, color: String) {
        val h = highlightDao.getById(highlightId) ?: return
        highlightDao.upsert(h.copy(color = color, updated_at = Instant.now().toString(), revision = h.revision + 1))
    }

    suspend fun updateHighlightNote(highlightId: String, note: String) {
        val h = highlightDao.getById(highlightId) ?: return
        highlightDao.upsert(h.copy(note = note.ifBlank { null }, updated_at = Instant.now().toString(), revision = h.revision + 1))
    }

    suspend fun saveNote(note: NoteEntity) = noteDao.upsert(note)

    /** 全局搜索：笔记标题/正文模糊检索（NoteDao.search）。 */
    suspend fun searchNotes(q: String): List<NoteEntity> = noteDao.search(q)

    /** 全局搜索：高亮正文/笔记模糊检索（HighlightDao.search）。 */
    suspend fun searchHighlights(q: String): List<HighlightEntity> = highlightDao.search(q)

    /** 软删除笔记（置 deleted_at），不存在时为无操作。 */
    suspend fun deleteNote(noteId: String) {
        val existing = noteDao.getById(noteId) ?: return
        noteDao.upsert(existing.copy(deleted_at = Instant.now().toString()))
    }

    /** 新增书签（kind = bookmark 的笔记）。统一写入 v2 locator，使跳转与高亮共用解析路径。 */
    suspend fun addBookmark(
        bookId: String,
        title: String,
        offset: Int,
        absOffset: Int = -1,
        chapterIndex: Int = -1,
        charOffset: Int = -1,
        locatorJson: String? = null,
    ) {
        val now = Instant.now().toString()
        noteDao.upsert(
            NoteEntity(
                id = java.util.UUID.randomUUID().toString(),
                book_id = bookId.ifBlank { null },
                title = "书签 · $title",
                body = "",
                excerpt = null,
                chapter_title = title.ifBlank { null },
                progress_percent = offset.toFloat(),
                kind = "bookmark",
                locator_json = locatorJson,
                payload = "{}",
                created_at = now,
                device_id = null,
                revision = 1,
                updated_at = now,
                deleted_at = null,
            ),
        )
    }

    /** 高亮转笔记，不存在时为无操作。 */
    suspend fun convertHighlightToNote(highlightId: String) {
        val h = highlightDao.getById(highlightId) ?: return
        val now = Instant.now().toString()
        noteDao.upsert(
            NoteEntity(
                id = java.util.UUID.randomUUID().toString(),
                book_id = h.book_id,
                title = "笔记：${h.chapter_title ?: ""}",
                body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                excerpt = h.text,
                chapter_title = h.chapter_title,
                progress_percent = h.progress_percent ?: 0f,
                kind = "note",
                locator_json = h.locator_json,
                payload = "{}",
                created_at = now,
                device_id = null,
                revision = 1,
                updated_at = now,
                deleted_at = null,
            ),
        )
    }

    /** 高亮转灵感，不存在时为无操作。 */
    suspend fun convertHighlightToInspiration(highlightId: String) {
        val h = highlightDao.getById(highlightId) ?: return
        val now = Instant.now().toString()
        inspirationDao.upsert(
            InspirationEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = "高亮灵感：${h.text.take(24)}",
                body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                type = "note",
                status = "inbox",
                source_book_id = h.book_id,
                payload = "{}",
                created_at = now,
                device_id = null,
                revision = 1,
                updated_at = now,
                deleted_at = null,
            ),
        )
    }

    suspend fun saveInspiration(inspiration: InspirationEntity) = inspirationDao.upsert(inspiration)
}
