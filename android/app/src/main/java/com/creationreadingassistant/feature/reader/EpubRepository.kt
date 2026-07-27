package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.domain.model.EpubBook
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB 仓储：负责打开解析、登记到书架（满足 reading_progress 外键约束）、
 * 进度持久化（复用 V2 reading_progress 表）。
 */
@Singleton
class EpubRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
) {
    /** 进程内解析缓存，最多缓存 3 本，超出自动淘汰最久未访问的条目。 */
    private val memoryCache = object : LinkedHashMap<String, EpubBook>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpubBook>?): Boolean {
            return size > 3
        }
    }

    suspend fun openEpub(uri: Uri): EpubBook {
        val book = EpubParser.parse(context, uri)
        val now = Instant.now().toString()
        bookDao.upsert(
            BookEntity(
                id = book.id,
                title = book.title,
                author = book.author,
                format = "epub",
                original_file_name = uri.lastPathSegment,
                local_uri = book.localUri,
                content_status = "available",
                updated_at = now,
            ),
        )
        memoryCache[book.id] = book
        return book
    }

    fun cached(bookId: String): EpubBook? = memoryCache[bookId]

    suspend fun saveProgress(
        bookId: String,
        chapterIndex: Int,
        percent: Float,
        offsetInChapter: Int = 0,
    ) {
        readingProgressDao.upsert(
            ReadingProgressEntity(
                book_id = bookId,
                progress_percent = percent,
                completion_state = if (percent >= 99.9f) "finished" else "reading",
                current_location_json =
                    """{"chapter":$chapterIndex,"offset":${offsetInChapter.coerceAtLeast(0)}}""",
                updated_at = Instant.now().toString(),
            ),
        )
    }

    suspend fun loadProgress(bookId: String): Int {
        val raw = readingProgressDao.getByBook(bookId)?.current_location_json ?: return 0
        val m = Regex("\"chapter\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        return m?.toIntOrNull() ?: 0
    }

    suspend fun loadProgressOffset(bookId: String): Int {
        val raw = readingProgressDao.getByBook(bookId)?.current_location_json ?: return 0
        val m = Regex("\"offset\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        return m?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
