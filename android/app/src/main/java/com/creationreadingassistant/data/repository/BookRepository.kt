package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.mergeReaderProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.creationreadingassistant.App
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书籍仓储 —— 封装书架所需的 Room 读写。
 * P1 仅暴露书架列表与示例数据播种；正文读取走 SAF（见 ReaderScreen）。
 */
@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val bookFileDao: BookFileDao,
    private val progressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao,
    private val noteDao: NoteDao,
    private val highlightDao: HighlightDao,
    private val inspirationDao: InspirationDao,
    private val bookTagDao: BookTagDao,
    private val bookCategoryDao: BookCategoryDao,
    private val shelfBookDao: ShelfBookDao,
) {
    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAllActive()
    fun observeProgress(): Flow<List<ReadingProgressEntity>> = progressDao.observeAllActive()
    fun observeSessions(): Flow<List<ReadingSessionEntity>> = sessionDao.observeAllActive()
    fun observeSessionsByBook(bookId: String): Flow<List<ReadingSessionEntity>> = sessionDao.observeByBook(bookId)
    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAllActive()
    fun observeHighlights(): Flow<List<HighlightEntity>> = highlightDao.observeAllActive()
    fun observeInspirations(): Flow<List<InspirationEntity>> = inspirationDao.observeAllActive()

    /** 已缓存正文的书籍数量（Profile 存储页）。 */
    fun observeCachedCount(): Flow<Int> = bookContentDao.observeCachedCount()

    /** 已缓存正文的总字节数（Profile 存储页）。 */
    fun observeCachedBytes(): Flow<Long> = bookContentDao.observeCachedBytes()

    suspend fun getById(id: String): BookEntity? = bookDao.getById(id)

    /** 批量按 id 查询（仅活跃记录），供搜索结果收集来源书名等场景消除 N+1。 */
    suspend fun getByIds(ids: Collection<String>): List<BookEntity> = bookDao.getByIds(ids)

    /** 全局搜索：书名/作者/原始文件名/标签名模糊检索（BookDao.search）。 */
    suspend fun search(q: String): List<BookEntity> = bookDao.search(q)

    suspend fun upsert(book: BookEntity) = bookDao.upsert(book)

    /**
     * 阅读器自动进度写入：用 mergeReaderProgress 合并现有业务状态（搁置/读完不回退），
     * 只更新位置与百分比字段，保留累计时长、同步信息等已有数据。
     */
    suspend fun saveProgress(incoming: ReadingProgressEntity) {
        val now = Instant.now().toString()
        progressDao.upsert(
            mergeReaderProgress(
                existing = progressDao.getByBook(incoming.book_id),
                incoming = incoming,
                nowIso = now,
                nowMillis = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * 删除书籍并级联清理相关记录（阅读进度、会话、笔记、高亮、正文、文件、书单/分类/标签关联）。
     * 所有操作在事务中执行，确保软删除的原子性。
     */
    suspend fun deleteBook(id: String) {
        bookDao.runInTransaction {
            val now = Instant.now().toString()
            bookDao.softDelete(id, now)
            progressDao.getByBook(id)?.let { progressDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
            sessionDao.upsertAll(sessionDao.observeByBook(id).first().map { it.copy(deleted_at = now, updated_at = now) })
            noteDao.upsertAll(noteDao.observeAllActive().first().filter { it.book_id == id }.map { it.copy(deleted_at = now, updated_at = now) })
            highlightDao.upsertAll(highlightDao.observeByBook(id).first().map { it.copy(deleted_at = now, updated_at = now) })
            bookContentDao.getByBook(id)?.let { bookContentDao.upsert(it.copy(reader_preview = null, epub_json = null)) }
            bookFileDao.getByBook(id)?.let { bookFileDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
            bookTagDao.clearByBook(id)
            bookCategoryDao.clearByBook(id)
            shelfBookDao.clearByBook(id)
        }
    }

    /**
     * 清理书籍本地正文缓存（保留书架元数据、进度、笔记、标签等）。
     * 用于批量管理中的“清缓存”。
     */
    suspend fun clearBookCache(id: String) {
        val now = Instant.now().toString()
        bookDao.getById(id)?.let { bookDao.upsert(it.copy(content_status = "missing", local_uri = null, local_content_path = null, updated_at = now)) }
        bookContentDao.getByBook(id)?.let { bookContentDao.upsert(it.copy(reader_preview = null, epub_json = null)) }
        bookFileDao.getByBook(id)?.let { bookFileDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
    }

    /** 播种锁：防止多个 ViewModel 同时调用 seedSampleIfEmpty() 产生重复示例书。 */
    private val seedMutex = Mutex()

    /** 首次启动播种一本示例书，方便真机自测（仅当书架为空时）。 */
    suspend fun seedSampleIfEmpty() {
        try {
            App.trace("Seed", "start countActive")
            seedMutex.withLock {
                val count = bookDao.countActive()
                App.trace("Seed", "count=$count")
                if (count > 0) return
                addSampleBook("示例·墨韵小札")
            }
        } catch (e: Throwable) {
            android.util.Log.e("BookRepo", "seedSampleIfEmpty failed", e)
        }
    }

    /** 新增一本示例书（FAB / 手动触发）。返回创建后的书籍实体（导入历史记录用）。 */
    suspend fun addSampleBook(title: String = "示例书"): BookEntity {
        val now = nowIso()
        val shortId = java.util.UUID.randomUUID().toString().take(8)
        val book = BookEntity(
            id = "sample-${java.util.UUID.randomUUID()}",
            title = "$title $shortId",
            author = "创作阅读助手",
            format = "txt",
            size = 0,
            content_status = "available",
            payload = "{}",
            updated_at = now,
        )
        try {
            bookDao.upsert(book)
        } catch (e: Throwable) {
            android.util.Log.e("BookRepo", "addSampleBook failed", e)
        }
        return book
    }

    /**
     * 更新书籍阅读进度与完成状态（用于首页继续阅读面板的「标记已读完 / 标记未读」）。
     */
    suspend fun updateReadingProgress(
        bookId: String,
        progressPercent: Float,
        completionState: String,
    ) {
        val now = Instant.now().toString()
        val existing = progressDao.getByBook(bookId)
        val requestedState = ReadingCompletionState.fromStorage(completionState)
        val state = if (progressPercent >= 99.5f) {
            ReadingCompletionState.FINISHED
        } else {
            requestedState
        }
        val finished = state == ReadingCompletionState.FINISHED
        progressDao.upsert(
            (existing ?: ReadingProgressEntity(
                book_id = bookId,
                updated_at = now,
            )).copy(
                progress_percent = progressPercent.coerceIn(0f, 100f),
                completion_state = state.storageValue,
                last_read_at = if (progressPercent > 0) now else existing?.last_read_at,
                updated_at = now,
                // 已读完写入完成时间，未读/在读置 null（对齐网页 completedAt）
                completed_at = if (finished) existing?.completed_at ?: System.currentTimeMillis() else null,
            ),
        )
    }

    /**
     * 只改变阅读生命周期状态，不改进度、位置、阅读时长、笔记或灵感。
     * 搁置与恢复在读都必须走这里，避免把“管理状态”误当成一次阅读进度更新。
     */
    suspend fun setReadingState(bookId: String, state: ReadingCompletionState) {
        val now = Instant.now().toString()
        val existing = progressDao.getByBook(bookId)
        val base = existing ?: ReadingProgressEntity(book_id = bookId, updated_at = now)
        progressDao.upsert(
            base.copy(
                completion_state = state.storageValue,
                completed_at = when (state) {
                    ReadingCompletionState.FINISHED -> base.completed_at ?: System.currentTimeMillis()
                    ReadingCompletionState.READING,
                    ReadingCompletionState.SHELVED -> null
                },
                updated_at = now,
            ),
        )
    }

    /**
     * 恢复被删除的书籍及其关联数据（用于撤销删除）。
     * 通过查询各表的 deleted_at 记录并还原。
     */
    suspend fun restoreBook(bookId: String) {
        val now = nowIso()
        bookDao.runInTransaction {
            bookDao.getDeleted().find { it.id == bookId }?.let {
                bookDao.upsert(it.copy(deleted_at = null, updated_at = now))
            }
            progressDao.getDeleted().find { it.book_id == bookId }?.let {
                progressDao.upsert(it.copy(deleted_at = null, updated_at = now))
            }
            sessionDao.upsertAll(sessionDao.getDeleted().filter { it.book_id == bookId }.map {
                it.copy(deleted_at = null, updated_at = now)
            })
            bookFileDao.getDeleted().find { it.book_id == bookId }?.let {
                bookFileDao.upsert(it.copy(deleted_at = null, updated_at = now))
            }
            noteDao.upsertAll(noteDao.getDeleted().filter { it.book_id == bookId }.map {
                it.copy(deleted_at = null, updated_at = now)
            })
            highlightDao.upsertAll(highlightDao.getDeleted().filter { it.book_id == bookId }.map {
                it.copy(deleted_at = null, updated_at = now)
            })
        }
    }

    /** 更新书籍基础信息（书名/作者/简介）。 */
    suspend fun updateBookInfo(bookId: String, title: String, author: String?, description: String? = null) {
        val now = nowIso()
        bookDao.getById(bookId)?.let {
            bookDao.upsert(it.copy(title = title, author = author, description = description, updated_at = now))
        }
    }

    /** 更新书籍封面（Uri 或生成的文字封面 DataURL 均可）。 */
    suspend fun updateBookCover(bookId: String, coverUrl: String?) {
        val now = nowIso()
        bookDao.getById(bookId)?.let {
            bookDao.upsert(it.copy(cover_data_url = coverUrl, updated_at = now))
        }
    }

    private fun nowIso(): String =
        java.time.Instant.now().toString()
}
