package com.creationreadingassistant.acceptance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.LocalExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CRA-ABC-DEVICE-CONTRACT-PREP-v1 / 任务34-v1 —— B：真实 Room 的关系保护。
 *
 * 现有 `LocalExportRoomRollbackTest` 只覆盖「较新书籍/来源引用 + 外键失败回滚」；
 * `LocalZipBackupPersistentRestoreTest` 不含本轮 shelf position / chapter read_at 冲突断言。
 * 本类补三项**真实 Room** 合同，经生产 `JsonBridge.importFromString`：
 *
 *   1. 旧备份导入后，**本地新增的关联**保持（不被旧备份清空）；
 *   2. **同键 shelfBook 已有 position 不被旧备份倒退**；缺失关联按合同新增；
 *   3. **chapterRead 同键 read_at 取较新值**（本地较新时保留本地，远端较新时采纳远端）。
 *
 * 真实 Room/SQLite 在内存数据库执行，非 fake DAO；未证明持久落盘或关闭重开。
 * 数据库用 `Room.inMemoryDatabaseBuilder` 建立，**不读不写**设备上的用户数据库。
 */
@RunWith(AndroidJUnit4::class)
class Task34RoomRelationProtectionTest {

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

    // ── 合同 1：旧备份导入后本地新增关联保持 ──────────────────────────────
    @Test
    fun task34_olderBackupImportKeepsLocallyAddedRelations() = runTest {
        seedBook("local-book")
        // 本地新增：一个标签关联 + 一个书单关联 + 一条已读标记（备份里都没有）
        database.tagDao().upsert(TagEntity(id = "local-tag", name = "本地标签", updated_at = STABLE))
        database.bookTagDao().upsert(BookTagEntity(book_id = "local-book", tag_id = "local-tag"))
        database.shelfDao().upsert(ShelfEntity(id = "local-shelf", name = "本地书单", sort_order = 0, updated_at = STABLE))
        database.shelfBookDao().upsert(ShelfBookEntity(shelf_id = "local-shelf", book_id = "local-book", position = 5))
        database.chapterReadDao().upsert(ChapterReadEntity("local-book", 3, LOCAL_NEWER))

        // 旧备份：同一本书，但**不含**以上任何关联
        val export = export(books = listOf(bookEnvelope(book("local-book", "旧备份书名", revision = 1, updatedAt = OLD))))
        bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))

        // 真实 Room 读回：本地新增关联必须原样保留
        assertEquals("本地标签关联必须保留", listOf("local-tag"),
            database.bookTagDao().getByBookIds(listOf("local-book")).map { it.tag_id })
        val shelfRows = database.shelfBookDao().getByBookIds(listOf("local-book"))
        assertEquals("本地新增书单关联必须保留", 1, shelfRows.size)
        assertEquals("local-shelf", shelfRows.single().shelf_id)
        assertEquals("本地已读标记必须保留", LOCAL_NEWER,
            database.chapterReadDao().getReadAt("local-book", 3))
    }

    // ── 合同 2：同键 shelfBook position 不被旧备份倒退；缺失关联按合同新增 ──
    @Test
    fun task34_sameKeyShelfPositionNotRegressedAndMissingRelationAdded() = runTest {
        seedBook("b1"); seedBook("b2")
        database.shelfDao().upsert(ShelfEntity(id = "s1", name = "书单1", sort_order = 0, updated_at = STABLE))
        database.shelfBookDao().upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 9))

        // 旧备份：s1/b1 的 position=1（更旧）；并新增 s1/b2（本地缺失）
        val export = export(
            books = listOf(bookEnvelope(book("b1", "b1", revision = 1, updatedAt = OLD)),
                           bookEnvelope(book("b2", "b2", revision = 1, updatedAt = OLD))),
            shelfBooks = listOf(
                ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 1),
                ShelfBookEntity(shelf_id = "s1", book_id = "b2", position = 2),
            ),
        )
        bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))

        val rows = database.shelfBookDao().getByBookIds(listOf("b1", "b2")).associateBy { it.book_id }
        assertEquals("同键 position 不得被旧备份倒退", 9, rows.getValue("b1").position)
        assertEquals("本地缺失的关联按备份新增", 2, rows.getValue("b2").position)
        assertEquals("书单关联总数 = 本地 1 + 新增 1", 2, rows.size)
    }

    // ── 合同 3：chapterRead 同键 read_at 取较新 ───────────────────────────
    @Test
    fun task34_chapterReadSameKeyKeepsNewerReadAt() = runTest {
        seedBook("b1")
        database.chapterReadDao().upsert(ChapterReadEntity("b1", 1, LOCAL_NEWER))  // 本地较新
        database.chapterReadDao().upsert(ChapterReadEntity("b1", 2, LOCAL_OLDER))  // 本地较旧

        val export = export(
            books = listOf(bookEnvelope(book("b1", "b1", revision = 1, updatedAt = OLD))),
            chapterReads = listOf(
                ChapterReadEntity("b1", 1, BACKUP_OLDER),   // 备份更旧 → 保留本地
                ChapterReadEntity("b1", 2, BACKUP_NEWER),   // 备份更新 → 采纳备份
                ChapterReadEntity("b1", 3, BACKUP_NEWER),   // 本地缺失 → 新增
            ),
        )
        bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))

        assertEquals("本地较新的 read_at 必须保留", LOCAL_NEWER,
            database.chapterReadDao().getReadAt("b1", 1))
        assertEquals("远端较新的 read_at 必须采纳", BACKUP_NEWER,
            database.chapterReadDao().getReadAt("b1", 2))
        assertEquals("本地缺失的已读标记按备份新增", BACKUP_NEWER,
            database.chapterReadDao().getReadAt("b1", 3))
    }

    // ── 夹具 ──────────────────────────────────────────────────────────────
    private suspend fun seedBook(id: String) {
        database.bookDao().upsert(book(id, id, revision = 1, updatedAt = STABLE))
    }

    private fun book(id: String, title: String, revision: Int, updatedAt: String) =
        BookEntity(id = id, title = title, format = "txt", revision = revision, updated_at = updatedAt)

    private fun bookEnvelope(book: BookEntity) = SyncEnvelope(
        id = book.id, type = "book", revision = book.revision, deviceId = "remote-device",
        updatedAt = book.updated_at, deletedAt = null, payload = book,
    )

    private fun export(
        books: List<SyncEnvelope<BookEntity>> = emptyList(),
        shelfBooks: List<ShelfBookEntity> = emptyList(),
        chapterReads: List<ChapterReadEntity> = emptyList(),
    ) = LocalExport(
        exportedAt = STABLE, deviceId = "task34-isolated-test",
        books = books, inspirations = emptyList(), progress = emptyList(), sessions = emptyList(),
        shelfBooks = shelfBooks, chapterReads = chapterReads,
    )

    private companion object {
        const val STABLE = "2026-09-23T00:00:00Z"
        const val OLD = "2026-09-20T00:00:00Z"
        const val LOCAL_NEWER = "2026-09-22T00:00:00Z"
        const val LOCAL_OLDER = "2026-09-18T00:00:00Z"
        const val BACKUP_NEWER = "2026-09-21T00:00:00Z"
        const val BACKUP_OLDER = "2026-09-19T00:00:00Z"
    }
}
