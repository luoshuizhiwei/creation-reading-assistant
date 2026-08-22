package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** EPUB / Markdown 章节到达记录；复合主键保证重复到达只刷新阅读时间。 */
@Singleton
class ChapterReadRepository @Inject constructor(
    private val dao: ChapterReadDao,
) {
    fun observeReadChapters(bookId: String): Flow<List<Int>> =
        dao.observeReadChapters(bookId)

    suspend fun markRead(bookId: String, chapterIndex: Int) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(chapterIndex >= 0) { "chapterIndex must not be negative" }
        dao.upsert(
            ChapterReadEntity(
                book_id = bookId,
                chapter_index = chapterIndex,
                read_at = Instant.now().toString(),
            ),
        )
    }

    suspend fun clearForBook(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        dao.clearForBook(bookId)
    }
}
