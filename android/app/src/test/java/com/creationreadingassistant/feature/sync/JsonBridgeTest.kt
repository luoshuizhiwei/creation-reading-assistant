package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 锁定 [JsonBridge.importFromString] 的合并语义（与局域网同步拉取共用 [SyncMergePolicy]）：
 * 1. 远端 revision 更新 → 覆盖本地
 * 2. 远端 revision 更旧 → 不覆盖本地
 * 3. revision 相等 → 比较 updated_at 决胜负（较新者胜）
 *
 * P1-A7 扩展：四类实体（books/inspirations/progress/sessions）全覆盖、墓碑删除传播、
 * 畸形 JSON 与未知 schemaVersion 拒绝、导入失败异常上抛（事务可回滚）、导出→导入往返幂等、
 * 导出内容不含凭据字段。
 *
 * mockk 模式参考 ProfileSyncEngineTest：DAO 全部 relaxed mock，
 * runInTransaction 桩成立即执行传入的挂起块（JVM 可测，不依赖 Room）。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JsonBridgeTest {

    private val bookDao = mockk<BookDao>(relaxed = true)
    private val inspirationDao = mockk<InspirationDao>(relaxed = true)
    private val progressDao = mockk<ReadingProgressDao>(relaxed = true)
    private val sessionDao = mockk<ReadingSessionDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val shelfDao = mockk<ShelfDao>(relaxed = true)
    private val bookTagDao = mockk<BookTagDao>(relaxed = true)
    private val bookCategoryDao = mockk<BookCategoryDao>(relaxed = true)
    private val shelfBookDao = mockk<ShelfBookDao>(relaxed = true)
    private val chapterReadDao = mockk<ChapterReadDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true }

    private fun bridge() = JsonBridge(
        bookDao, inspirationDao, progressDao, sessionDao,
        noteDao, highlightDao, tagDao, categoryDao, shelfDao,
        bookTagDao, bookCategoryDao, shelfBookDao, chapterReadDao,
        Dispatchers.Unconfined,
    )

    @Before
    fun setUp() {
        // 事务桩：直接执行块本身，不依赖真实 Room。
        coEvery { bookDao.runInTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
        // 新增实体：relaxed mock 对 Flow 返回空流，first() 会抛 NoSuchElement——补空列表桩
        coEvery { noteDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { highlightDao.observeAllActive() } returns flowOf(emptyList())
    }

    private fun book(
        id: String,
        revision: Int,
        updatedAt: String,
        title: String = "书-$id",
    ) = BookEntity(id = id, title = title, format = "txt", revision = revision, updated_at = updatedAt)

    private fun inspiration(id: String, revision: Int, updatedAt: String) =
        InspirationEntity(id = id, title = "灵感-$id", created_at = "2026-01-01T00:00:00Z", revision = revision, updated_at = updatedAt)

    private fun progress(bookId: String, revision: Int, updatedAt: String, percent: Float = 50f) =
        ReadingProgressEntity(book_id = bookId, progress_percent = percent, revision = revision, updated_at = updatedAt)


    private fun noteEntity(id: String, revision: Int, updatedAt: String) =
        com.creationreadingassistant.data.local.entity.NoteEntity(
            id = id, title = "笔记$id", body = "正文", kind = "note", created_at = updatedAt, updated_at = updatedAt, revision = revision,
        )

    private fun highlightEntity(id: String, revision: Int, updatedAt: String) =
        com.creationreadingassistant.data.local.entity.HighlightEntity(
            id = id, book_id = "b1", text = "高亮", created_at = updatedAt, updated_at = updatedAt, revision = revision,
        )

    private fun tagEntity(id: String, revision: Int, updatedAt: String) =
        com.creationreadingassistant.data.local.entity.TagEntity(
            id = id, name = "标签$id", type = "book", created_at = updatedAt, updated_at = updatedAt, revision = revision,
        )

    private fun session(id: String, bookId: String, revision: Int, updatedAt: String) =
        ReadingSessionEntity(id = id, book_id = bookId, revision = revision, updated_at = updatedAt)

    private fun exportJson(
        books: List<SyncEnvelope<BookEntity>> = emptyList(),
        inspirations: List<SyncEnvelope<InspirationEntity>> = emptyList(),
        progress: List<SyncEnvelope<ReadingProgressEntity>> = emptyList(),
        sessions: List<SyncEnvelope<ReadingSessionEntity>> = emptyList(),
        schemaVersion: Int = 1,
    ): String {
        val export = LocalExport(
            schemaVersion = schemaVersion,
            exportedAt = "2026-08-05T00:00:00Z",
            deviceId = "test-device",
            books = books,
            inspirations = inspirations,
            progress = progress,
            sessions = sessions,
        )
        return json.encodeToString(LocalExport.serializer(), export)
    }

    private fun <T> envelope(
        entity: T,
        id: String,
        type: String,
        revision: Int,
        updatedAt: String,
        deletedAt: String? = null,
    ) = SyncEnvelope(
        id = id,
        type = type,
        revision = revision,
        deviceId = "remote-device",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        payload = entity,
    )

    private fun bookEnvelope(b: BookEntity, deletedAt: String? = null) =
        envelope(b, b.id, "book", b.revision, b.updated_at, deletedAt)

    @Test
    fun `remote newer revision overwrites local`() = runTest {
        val local = book("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z", title = "远端新版")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote))))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }

    @Test
    fun `remote older revision does not overwrite local`() = runTest {
        val local = book("b1", revision = 5, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z", title = "远端旧版")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote))))

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `equal revision newer updatedAt wins`() = runTest {
        val local = book("b1", revision = 3, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 3, updatedAt = "2026-02-01T00:00:00Z", title = "远端同版更新")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote))))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }

    @Test
    fun `equal revision older updatedAt keeps local`() = runTest {
        val local = book("b1", revision = 3, updatedAt = "2026-03-01T00:00:00Z")
        val remote = book("b1", revision = 3, updatedAt = "2026-02-01T00:00:00Z", title = "远端同版更旧")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote))))

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `unknown id is inserted`() = runTest {
        val remote = book("new-book", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns emptyList()

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote))))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }

    // ---- 墓碑删除（P1-A7）----

    @Test
    fun `newer remote tombstone propagates deletion`() = runTest {
        val localActive = book("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val remoteDeleted = book("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z")
            .copy(deleted_at = "2026-06-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns listOf(localActive)

        bridge().importFromString(
            context,
            exportJson(books = listOf(bookEnvelope(remoteDeleted, deletedAt = "2026-06-01T00:00:00Z"))),
        )

        // 删除以软删除实体落库（deleted_at 保留），本地活跃记录被墓碑覆盖
        coVerify(exactly = 1) { bookDao.upsert(remoteDeleted) }
    }

    @Test
    fun `older remote tombstone does not resurrect newer local edit`() = runTest {
        // 本地在远端删除之后又编辑过（revision 更高）：旧墓碑不得把新数据清掉
        val localActive = book("b1", revision = 5, updatedAt = "2026-07-01T00:00:00Z")
        val remoteDeleted = book("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z")
            .copy(deleted_at = "2026-06-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns listOf(localActive)

        bridge().importFromString(
            context,
            exportJson(books = listOf(bookEnvelope(remoteDeleted, deletedAt = "2026-06-01T00:00:00Z"))),
        )

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `older remote active record does not resurrect newer local tombstone`() = runTest {
        // 本地已删除（revision 5），远端带着旧版活跃记录（revision 2）回来：已删数据不复活
        val localTombstone = book("b1", revision = 5, updatedAt = "2026-07-01T00:00:00Z")
            .copy(deleted_at = "2026-07-01T00:00:00Z")
        val remoteActive = book("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns listOf(localTombstone)

        bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remoteActive))))

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    // ---- 非 books 实体合并（P1-A7：此前只测了 books）----

    @Test
    fun `inspirations merge by same policy`() = runTest {
        val localOld = inspiration("i1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val remoteNew = inspiration("i1", revision = 3, updatedAt = "2026-06-01T00:00:00Z")
        coEvery { inspirationDao.getByIds(any()) } returns listOf(localOld)

        bridge().importFromString(
            context,
            exportJson(inspirations = listOf(envelope(remoteNew, "i1", "inspiration", remoteNew.revision, remoteNew.updated_at))),
        )

        coVerify(exactly = 1) { inspirationDao.upsert(remoteNew) }
    }

    @Test
    fun `progress merge by book id and same policy`() = runTest {
        val localNewer = progress("b1", revision = 9, updatedAt = "2026-07-01T00:00:00Z")
        val remoteOlder = progress("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z", percent = 10f)
        coEvery { progressDao.getByBooks(any()) } returns listOf(localNewer)

        bridge().importFromString(
            context,
            exportJson(progress = listOf(envelope(remoteOlder, "b1", "progress", remoteOlder.revision, remoteOlder.updated_at))),
        )

        // 旧快照进度不得覆盖本地新进度
        coVerify(exactly = 0) { progressDao.upsert(any<ReadingProgressEntity>()) }
    }

    @Test
    fun `sessions merge by same policy`() = runTest {
        val remote = session("s1", "b1", revision = 1, updatedAt = "2026-06-01T00:00:00Z")
        coEvery { sessionDao.getByIds(any()) } returns emptyList()

        bridge().importFromString(
            context,
            exportJson(sessions = listOf(envelope(remote, "s1", "session", remote.revision, remote.updated_at))),
        )

        coVerify(exactly = 1) { sessionDao.upsert(remote) }
    }

    // ---- 畸形输入拒绝（P1-A7）----

    @Test
    fun `malformed json is rejected`() = runTest {
        val result = runCatching { bridge().importFromString(context, "这不是 JSON") }
        assertTrue("畸形 JSON 应报错", result.isFailure)
        assertTrue(result.exceptionOrNull() is SerializationException)
        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `unknown schema version is rejected`() = runTest {
        val remote = book("b1", revision = 9, updatedAt = "2026-06-01T00:00:00Z")
        val result = runCatching {
            bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(remote)), schemaVersion = 99))
        }
        assertTrue("未知 schemaVersion 应拒绝导入", result.isFailure)
        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    // ---- 事务回滚（P1-A7：导入失败异常必须上抛，不吞掉）----

    @Test
    fun `mid-import failure propagates for transaction rollback`() = runTest {
        val first = book("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val second = book("b2", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns emptyList()
        coEvery { bookDao.upsert(first) } returns Unit
        coEvery { bookDao.upsert(second) } throws IllegalStateException("磁盘满")

        val result = runCatching {
            bridge().importFromString(context, exportJson(books = listOf(bookEnvelope(first), bookEnvelope(second))))
        }

        // 异常必须冒泡：真实 Room 事务因失败整体回滚，不留半导入状态
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ---- 导出 → 导入往返与幂等（P1-A7）----

    @Test
    fun `export contains all four groups and roundtrips without loss`() = runTest {
        val b = book("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val i = inspiration("i1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val p = progress("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val s = session("s1", "b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        coEvery { bookDao.observeAllActive() } returns flowOf(listOf(b))
        coEvery { inspirationDao.observeAllActive() } returns flowOf(listOf(i))
        coEvery { progressDao.observeAllActive() } returns flowOf(listOf(p))
        coEvery { sessionDao.observeAllActive() } returns flowOf(listOf(s))

        val exported = bridge().exportToString(context)

        // 四组数据都在导出里
        assertTrue(exported.contains("\"books\""))
        assertTrue(exported.contains("\"inspirations\""))
        assertTrue(exported.contains("\"progress\""))
        assertTrue(exported.contains("\"sessions\""))

        // 空库导入：每条恰好 upsert 一次，不丢不重
        coEvery { bookDao.getByIds(any()) } returns emptyList()
        coEvery { inspirationDao.getByIds(any()) } returns emptyList()
        coEvery { progressDao.getByBooks(any()) } returns emptyList()
        coEvery { sessionDao.getByIds(any()) } returns emptyList()
        bridge().importFromString(context, exported)

        coVerify(exactly = 1) { bookDao.upsert(b) }
        coVerify(exactly = 1) { inspirationDao.upsert(i) }
        coVerify(exactly = 1) { progressDao.upsert(p) }
        coVerify(exactly = 1) { sessionDao.upsert(s) }
    }

    @Test
    fun `export includes notes taxonomy and readmarks then rebuilds associations`() = runTest {
        // 2026-08-22 回归：备份必须包含笔记/高亮/标签/分类/书单/已读标记，
        // 否则恢复后这些数据全部丢失（此前导出仅四类实体，与文案承诺不符）。
        val n = noteEntity("n1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val h = highlightEntity("h1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val t = tagEntity("t1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        // 原有四类在导出前先被收集：relaxed 空流会 first() 抛——补空列表
        coEvery { bookDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { inspirationDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { progressDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { sessionDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { noteDao.observeAllActive() } returns flowOf(listOf(n))
        coEvery { highlightDao.observeAllActive() } returns flowOf(listOf(h))
        coEvery { tagDao.getAllActive() } returns listOf(t)
        coEvery { bookTagDao.getAllActive() } returns listOf(com.creationreadingassistant.data.local.entity.BookTagEntity("b1", "t1"))
        coEvery { chapterReadDao.getAll() } returns listOf(com.creationreadingassistant.data.local.entity.ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z"))

        val exported = bridge().exportToString(context)

        assertTrue("导出应含 notes", exported.contains("\"notes\""))
        assertTrue("导出应含 highlights", exported.contains("\"highlights\""))
        assertTrue("导出应含 tags", exported.contains("\"tags\""))
        assertTrue("导出应含 bookTags", exported.contains("\"bookTags\""))
        assertTrue("导出应含 chapterReads", exported.contains("\"chapterReads\""))

        // 导入：实体按 revision 合并、关联整表重建
        coEvery { noteDao.getById(any()) } returns null
        coEvery { highlightDao.getById(any()) } returns null
        coEvery { tagDao.getById(any()) } returns null
        bridge().importFromString(context, exported)

        coVerify(exactly = 1) { noteDao.upsert(n) }
        coVerify(exactly = 1) { highlightDao.upsert(h) }
        coVerify(exactly = 1) { tagDao.upsert(t) }
        coVerify(exactly = 1) { bookTagDao.clearAll() }
        coVerify(exactly = 1) { chapterReadDao.clearAll() }
        coVerify(exactly = 1) { chapterReadDao.upsertAll(any()) }
    }

    @Test
    fun `repeated import of same snapshot is idempotent`() = runTest {
        val remote = book("b1", revision = 4, updatedAt = "2026-06-01T00:00:00Z")
        val snapshot = exportJson(books = listOf(bookEnvelope(remote)))
        coEvery { bookDao.getByIds(any()) } returns emptyList()

        bridge().importFromString(context, snapshot)
        bridge().importFromString(context, snapshot)

        // 重复导入同一快照：upsert 主键去重，不产生重复记录
        coVerify(exactly = 2) { bookDao.upsert(remote) }
    }

    // ---- 凭据隔离（P1-A7 验收项）----

    @Test
    fun `export carries no credential fields`() = runTest {
        coEvery { bookDao.observeAllActive() } returns flowOf(listOf(book("b1", 1, "2026-01-01T00:00:00Z")))
        coEvery { inspirationDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { progressDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { sessionDao.observeAllActive() } returns flowOf(emptyList())

        val exported = bridge().exportToString(context)

        // 导出只含四组业务数据；AI Key / WebDAV 凭据 / 同步 token 存于独立加密存储，永不进入备份
        assertFalse(exported.contains("apiKey"))
        assertFalse(exported.contains("webDavPassword"))
        assertFalse(exported.contains("dav-pass"))
        assertFalse(exported.contains("token"))
        val knownFields = setOf("schemaVersion", "exportedAt", "deviceId", "books", "inspirations", "progress", "sessions")
        val topLevelFields = kotlinx.serialization.json.Json.parseToJsonElement(exported)
            .let { it as kotlinx.serialization.json.JsonObject }.keys
        assertTrue("导出顶层字段越界：$topLevelFields", topLevelFields.all { it in knownFields })
    }
}
