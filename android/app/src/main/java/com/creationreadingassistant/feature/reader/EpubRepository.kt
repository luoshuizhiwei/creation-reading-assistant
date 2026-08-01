package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
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
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** 进程内解析缓存，最多缓存 3 本，超出自动淘汰最久未访问的条目。 */
    private val memoryCache = object : LinkedHashMap<String, EpubBook>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpubBook>?): Boolean {
            return size > 3
        }
    }

    suspend fun openEpub(uri: Uri): EpubBook {
        val book = EpubParser.parse(context, uri)
        persistOpenedBook(book, uri.lastPathSegment)
        return book
    }

    /**
     * 打开书架中已有的 EPUB。优先使用应用自有副本，并显式保留数据库 book id，
     * 避免从 file:// 回退打开时生成新 id、丢失原书进度。
     */
    suspend fun openStoredEpub(stored: BookEntity): EpubBook {
        memoryCache[stored.id]?.let { return it }
        val uri = stored.local_uri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: stored.local_content_path
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.fromFile(File(it.removePrefix("file://"))) }
            ?: throw IllegalStateException("本书缺少可读取的 EPUB 文件，请重新导入")
        val book = EpubParser.parse(
            context = context,
            uri = uri,
            expectedBookId = stored.id,
            fallbackLocalPath = stored.local_content_path,
        )
        persistOpenedBook(book, stored.original_file_name ?: uri.lastPathSegment)
        return book
    }

    /**
     * Replaces the local EPUB bytes for an existing book while preserving its database id and
     * every id-keyed reading record. The selected file is fully parsed before the old copy is
     * touched, then swapped through a staging file so a failed repair cannot destroy the last
     * readable copy.
     */
    suspend fun replaceStoredEpub(
        stored: BookEntity,
        selectedUri: Uri,
        selectedFileName: String,
    ): EpubBook = withContext(ioDispatcher) {
        val validated = EpubParser.parse(context, selectedUri)
        val validatedFile = File(validated.cachedEpubPath)
        require(validatedFile.isFile && validatedFile.length() > 0L) {
            "所选 EPUB 没有可读取的正文"
        }

        val target = File(context.filesDir, "books/epub/${stored.id}.epub")
        target.parentFile?.mkdirs()
        val staging = File(target.parentFile, "${stored.id}.repairing")
        val backup = File(target.parentFile, "${stored.id}.backup")
        staging.delete()
        backup.delete()
        try {
            validatedFile.inputStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            require(staging.length() == validatedFile.length() && staging.length() > 0L) {
                "复制 EPUB 时文件不完整"
            }
            if (target.exists() && !target.renameTo(backup)) {
                target.copyTo(backup, overwrite = true)
                target.delete()
            }
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }

            memoryCache.remove(stored.id)
            val replacement = EpubParser.parse(
                context = context,
                uri = Uri.fromFile(target),
                expectedBookId = stored.id,
                fallbackLocalPath = target.absolutePath,
            )
            val now = Instant.now().toString()
            val updated = stored.copy(
                title = replacement.title.takeIf { it.isNotBlank() } ?: stored.title,
                author = replacement.author ?: stored.author,
                original_file_name = selectedFileName,
                size = target.length().toBookSize(),
                local_uri = Uri.fromFile(target).toString(),
                local_content_path = target.absolutePath,
                content_status = "available",
                updated_at = now,
            )
            if (bookDao.update(updated) == 0) bookDao.upsert(updated)
            memoryCache[stored.id] = replacement
            backup.delete()
            if (validatedFile.absolutePath != target.absolutePath) validatedFile.delete()
            replacement
        } catch (error: Throwable) {
            memoryCache.remove(stored.id)
            target.delete()
            if (backup.exists()) {
                if (!backup.renameTo(target)) {
                    backup.copyTo(target, overwrite = true)
                    backup.delete()
                }
            }
            throw error
        } finally {
            staging.delete()
        }
    }

    private suspend fun persistOpenedBook(book: EpubBook, originalFileName: String?) {
        val now = Instant.now().toString()
        val existing = bookDao.getById(book.id)
        val record = mergeEpubBookRecord(
            existing = existing,
            parsed = book,
            originalFileName = originalFileName,
            resolvedSize = withContext(ioDispatcher) {
                fileSize(book.cachedEpubPath)
            },
            now = now,
        )
        if (existing == null || bookDao.update(record) == 0) {
            bookDao.upsert(record)
        }
        memoryCache[book.id] = book
    }

    /**
     * 修复旧版本留下的 EPUB size=0 记录。
     *
     * 只更新确认可读取且长度大于 0 的记录；权限失效或暂时不可访问时保留原记录，
     * 避免把一次瞬时 I/O 失败误判成正文丢失。
     */
    suspend fun repairMissingLocalFileSizes(): Int = withContext(ioDispatcher) {
        var repaired = 0
        bookDao.getEpubBooksNeedingSizeRepair().forEach { stored ->
            val uri = stored.local_uri
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val resolvedSize = uri?.let { resolveUriSize(it, stored.local_content_path) }
                ?: fileSize(stored.local_content_path)
            if (resolvedSize > 0) {
                repaired += bookDao.updateSizeIfMissing(stored.id, resolvedSize)
            }
        }
        repaired
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

    private fun resolveUriSize(uri: Uri, fallbackPath: String? = null): Int {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
        if (queried > 0) return queried.toBookSize()

        val descriptorLength = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
        if (descriptorLength > 0) return descriptorLength.toBookSize()

        if (uri.scheme == "file") {
            val directFileSize = fileSize(uri.path)
            if (directFileSize > 0) return directFileSize
        }
        return fileSize(fallbackPath)
    }

    private fun fileSize(path: String?): Int {
        if (path.isNullOrBlank()) return 0
        val normalized = path.removePrefix("file://")
        return runCatching { File(normalized).length().toBookSize() }.getOrDefault(0)
    }
}

internal fun mergeEpubBookRecord(
    existing: BookEntity?,
    parsed: EpubBook,
    originalFileName: String?,
    resolvedSize: Int,
    now: String,
): BookEntity {
    if (existing == null) {
        return BookEntity(
            id = parsed.id,
            title = parsed.title,
            author = parsed.author,
            format = "epub",
            original_file_name = originalFileName,
            size = resolvedSize.coerceAtLeast(0),
            local_uri = parsed.localUri,
            local_content_path = parsed.cachedEpubPath,
            content_status = "available",
            imported_at = now,
            updated_at = now,
        )
    }
    return existing.copy(
        title = parsed.title.ifBlank { existing.title },
        author = parsed.author ?: existing.author,
        format = "epub",
        original_file_name = existing.original_file_name ?: originalFileName,
        size = if (resolvedSize > 0) resolvedSize else existing.size,
        local_uri = parsed.localUri,
        local_content_path = parsed.cachedEpubPath,
        content_status = "available",
        updated_at = now,
    )
}

private fun Long.toBookSize(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
