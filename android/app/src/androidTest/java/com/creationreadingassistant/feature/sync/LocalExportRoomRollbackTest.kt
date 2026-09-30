package com.creationreadingassistant.feature.sync

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S14 的真实 Room 保护网。
 *
 * 这里不使用 Mock DAO：第一项故意把一个不存在的 tag 写进备份关联表，使 SQLite
 * 在书籍已尝试写入、关联表已尝试重建后触发外键失败。只有整个
 * [JsonBridge.importFromString] 确实处于同一 Room 事务，先前的写入与清空才会回滚。
 * 数据库由 [Room.inMemoryDatabaseBuilder] 创建，绝不读取或写入设备上的用户数据库。
 */
@RunWith(AndroidJUnit4::class)
class LocalExportRoomRollbackTest {

    private lateinit var database: AppDatabase
    private lateinit var bridge: JsonBridge

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        bridge = JsonBridge(
            bookDao = database.bookDao(),
            inspirationDao = database.inspirationDao(),
            progressDao = database.readingProgressDao(),
            sessionDao = database.readingSessionDao(),
            noteDao = database.noteDao(),
            highlightDao = database.highlightDao(),
            tagDao = database.tagDao(),
            categoryDao = database.categoryDao(),
            shelfDao = database.shelfDao(),
            bookTagDao = database.bookTagDao(),
            bookCategoryDao = database.bookCategoryDao(),
            shelfBookDao = database.shelfBookDao(),
            chapterReadDao = database.chapterReadDao(),
            ioDispatcher = Dispatchers.Unconfined,
            sourceRefDao = database.librarySourceRefDao(),
            textRuleDao = database.readerTextRuleDao(),
            correctionDao = database.readerCorrectionDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importFailureRollsBackEarlierBookWriteAndRelationTableClearInRealRoom() = runTest {
        database.bookDao().upsert(book("existing", title = "保留书籍", revision = 4))
        database.tagDao().upsert(
            com.creationreadingassistant.data.local.entity.TagEntity(
                id = "existing-tag",
                name = "保留标签",
                updated_at = STABLE_TIME,
            ),
        )
        database.bookTagDao().upsert(BookTagEntity(book_id = "existing", tag_id = "existing-tag"))

        val export = export(
            books = listOf(bookEnvelope(book("incoming", title = "不应留下的书籍", revision = 1))),
            // 触发关联表重建；第二个外键故意不存在。书籍 upsert 和 clearAll 已发生后，
            // SQLite 才在 upsertAll 处报错，因此这是中途失败而不是解析前失败。
            bookTags = listOf(BookTagEntity(book_id = "incoming", tag_id = "missing-tag")),
        )

        val outcome = runCatching {
            bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))
        }

        assertTrue("受控外键冲突必须上抛", outcome.exceptionOrNull() is SQLiteConstraintException)
        assertFalse("中途导入的新书不得遗留", database.bookDao().getById("incoming") != null)
        assertNotNull("既有书籍不得被中途导入影响", database.bookDao().getById("existing"))
        assertEquals(
            listOf("existing-tag"),
            database.bookTagDao().getByBookIds(listOf("existing")).map { it.tag_id },
        )
    }

    @Test
    fun olderBackupKeepsNewerBookAndSourceReferenceInRealRoom() = runTest {
        database.bookDao().upsert(book("protected", title = "本地较新书籍", revision = 7, updatedAt = NEW_TIME))
        database.librarySourceRefDao().upsert(sourceRef("protected", lastSeenAt = 200L, hash = "local-v7"))

        val remoteOldBook = book("protected", title = "远端旧书籍", revision = 6, updatedAt = OLD_TIME)
        val export = export(
            books = listOf(bookEnvelope(remoteOldBook)),
            sourceRefs = listOf(SourceRefExport.fromEntity(sourceRef("protected", lastSeenAt = 100L, hash = "remote-v6"))),
        )

        bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))

        val retainedBook = database.bookDao().getById("protected")
        val retainedRef = database.librarySourceRefDao().getAll().single { it.book_id == "protected" }
        assertEquals("本地较新书籍", retainedBook?.title)
        assertEquals(7, retainedBook?.revision)
        assertEquals(200L, retainedRef.last_seen_at)
        assertEquals("local-v7", retainedRef.content_hash)
    }

    private fun export(
        books: List<SyncEnvelope<BookEntity>> = emptyList(),
        bookTags: List<BookTagEntity> = emptyList(),
        sourceRefs: List<SourceRefExport> = emptyList(),
    ) = LocalExport(
        exportedAt = STABLE_TIME,
        deviceId = "isolated-android-test",
        books = books,
        inspirations = emptyList(),
        progress = emptyList(),
        sessions = emptyList(),
        bookTags = bookTags,
        librarySourceRefs = sourceRefs,
    )

    private fun book(
        id: String,
        title: String,
        revision: Int,
        updatedAt: String = STABLE_TIME,
    ) = BookEntity(id = id, title = title, format = "txt", revision = revision, updated_at = updatedAt)

    private fun bookEnvelope(book: BookEntity) = SyncEnvelope(
        id = book.id,
        type = "book",
        revision = book.revision,
        deviceId = "remote-device",
        updatedAt = book.updated_at,
        deletedAt = null,
        payload = book,
    )

    private fun sourceRef(bookId: String, lastSeenAt: Long, hash: String) = LibrarySourceRefEntity(
        book_id = bookId,
        root_id = "test-root",
        provider_authority = "test.authority",
        document_id = "doc-$bookId",
        display_name = "测试 TXT",
        format = "txt",
        size = 1L,
        last_modified = lastSeenAt,
        content_hash = hash,
        candidate_fingerprint = null,
        last_seen_at = lastSeenAt,
        availability = "available",
    )

    private companion object {
        const val STABLE_TIME = "2026-09-23T00:00:00Z"
        const val OLD_TIME = "2026-09-20T00:00:00Z"
        const val NEW_TIME = "2026-09-22T00:00:00Z"
    }
}
