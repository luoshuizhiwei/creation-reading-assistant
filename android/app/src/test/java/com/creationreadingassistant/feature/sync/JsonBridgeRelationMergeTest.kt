package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.feature.sync.SourceRefExport
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R3（CRA-BACKUP-RELATION-PRESERVATION-FIX-v1/v2）关系保守合并的生产行为锁定。
 *
 * 旧实现：任一备份关系列表非空 → clearAll 四张关系表 → 只重放备份行。
 * 备份之后本地新增的标签/分类/书单/已读关联被确定性清空。v2 返修补充：shelf_book
 * 同键整行保留（position 等非主键列不得被旧备份覆盖）。
 *
 * 这里用**有状态内存 DAO** 直接驱动真实 [JsonBridge.importFromString] 生产代码路径
 * （非 verify「调用了某方法」），逐表断言恢复后的落库状态。
 *
 * fake 语义与 Room 真实约束对齐：
 * - upsert / upsertAll = INSERT OR REPLACE，按各表主键**整行覆盖**（position、
 *   read_at 都是非主键列，REPLACE 会连值一起覆盖——因此生产代码必须先查本地再
 *   决定写什么，不能依赖 DAO 冲突策略）；
 * - clearAll 清空全表（保留方法供旧实现反证时替换）。
 *
 * 模拟层级（证据口径）：本类是 JVM 内存 fake，**不执行真实 Room/SQLite 事务**。
 * 事务失败用例只证明「合并代码异常原样上抛、上抛前不吞不改」，供真实 Room 事务
 * 回滚的前置条件成立；真实回滚合同由既有 androidTest LocalExportRoomRollbackTest
 * （Room.inMemoryDatabaseBuilder）覆盖，本类不重复宣称已运行真实回滚。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JsonBridgeRelationMergeTest {

    // ---- 有状态内存 DAO（只实现 JsonBridge 实际用到的方法）----

    private class StatefulSourceRefDao : LibrarySourceRefDao {
        val rows = LinkedHashMap<String, LibrarySourceRefEntity>()
        override suspend fun upsert(ref: LibrarySourceRefEntity) { rows[ref.book_id] = ref }
        override suspend fun getAll(): List<LibrarySourceRefEntity> = rows.values.toList()
        override suspend fun getByFingerprint(fingerprint: String): List<LibrarySourceRefEntity> = emptyList()
        override suspend fun updateObservation(bookIds: List<String>, nowMillis: Long, availability: String) {}
        override suspend fun updateAvailability(bookIds: List<String>, availability: String) {}
        override suspend fun backfillRootId(rootId: String, authority: String, documentIds: List<String>) {}
    }

    private class StatefulChapterReadDao : ChapterReadDao {
        val rows = LinkedHashMap<Pair<String, Int>, ChapterReadEntity>()
        override suspend fun upsert(entity: ChapterReadEntity) { rows[entity.book_id to entity.chapter_index] = entity }
        override suspend fun upsertAll(list: List<ChapterReadEntity>) { list.forEach { upsert(it) } }
        override fun observeReadChapters(bookId: String): Flow<List<Int>> = flowOf(rows.keys.filter { it.first == bookId }.map { it.second })
        override fun observeReadCount(bookId: String): Flow<Int> = flowOf(rows.keys.count { it.first == bookId })
        override suspend fun readCount(bookId: String): Int = rows.keys.count { it.first == bookId }
        override suspend fun getReadAt(bookId: String, chapterIndex: Int): String? = rows[bookId to chapterIndex]?.read_at
        override suspend fun clearForBook(bookId: String) { rows.keys.removeAll { it.first == bookId } }
        override suspend fun getAll(): List<ChapterReadEntity> = rows.values.toList()
        override suspend fun getByBookIds(bookIds: Collection<String>): List<ChapterReadEntity> = rows.values.filter { it.book_id in bookIds }
        override suspend fun clearAll() { rows.clear() }
    }

    private class StatefulBookTagDao : BookTagDao {
        val rows = LinkedHashMap<Pair<String, String>, BookTagEntity>()
        override suspend fun upsert(ref: BookTagEntity) { rows[ref.book_id to ref.tag_id] = ref }
        override suspend fun upsertAll(refs: List<BookTagEntity>) { refs.forEach { upsert(it) } }
        override suspend fun remove(bookId: String, tagId: String) { rows.remove(bookId to tagId) }
        override suspend fun clearByBook(bookId: String) { rows.keys.removeAll { it.first == bookId } }
        override fun observeTagIds(bookId: String): Flow<List<String>> = flowOf(rows.keys.filter { it.first == bookId }.map { it.second })
        override fun observeTagIdsForBooks(bookIds: List<String>): Flow<List<String>> = flowOf(rows.keys.filter { it.first in bookIds }.map { it.second })
        override suspend fun getBookIds(tagId: String): List<String> = rows.values.filter { it.tag_id == tagId }.map { it.book_id }
        override suspend fun getAllActive(): List<BookTagEntity> = rows.values.toList()
        override suspend fun getByBookIds(bookIds: Collection<String>): List<BookTagEntity> = rows.values.filter { it.book_id in bookIds }
        override suspend fun clearAll() { rows.clear() }
    }

    private class StatefulBookCategoryDao : BookCategoryDao {
        val rows = LinkedHashMap<Pair<String, String>, BookCategoryEntity>()
        override suspend fun upsert(ref: BookCategoryEntity) { rows[ref.book_id to ref.category_id] = ref }
        override suspend fun upsertAll(refs: List<BookCategoryEntity>) { refs.forEach { upsert(it) } }
        override suspend fun remove(bookId: String, categoryId: String) { rows.remove(bookId to categoryId) }
        override suspend fun clearByBook(bookId: String) { rows.keys.removeAll { it.first == bookId } }
        override suspend fun replaceForBooks(bookIds: List<String>, categoryId: String) {
            bookIds.forEach { clearByBook(it) }
            upsertAll(bookIds.map { BookCategoryEntity(book_id = it, category_id = categoryId) })
        }
        override fun observeCategoryIds(bookId: String): Flow<List<String>> = flowOf(rows.keys.filter { it.first == bookId }.map { it.second })
        override fun observeCategoryIdsForBooks(bookIds: List<String>): Flow<List<String>> = flowOf(rows.keys.filter { it.first in bookIds }.map { it.second })
        override suspend fun getBookIds(categoryId: String): List<String> = rows.values.filter { it.category_id == categoryId }.map { it.book_id }
        override suspend fun getAllActive(): List<BookCategoryEntity> = rows.values.toList()
        override suspend fun getByBookIds(bookIds: Collection<String>): List<BookCategoryEntity> = rows.values.filter { it.book_id in bookIds }
        override suspend fun clearAll() { rows.clear() }
    }

    private class StatefulShelfBookDao : ShelfBookDao {
        val rows = LinkedHashMap<Pair<String, String>, ShelfBookEntity>()
        override suspend fun upsert(ref: ShelfBookEntity) { rows[ref.shelf_id to ref.book_id] = ref }
        override suspend fun upsertAll(refs: List<ShelfBookEntity>) { refs.forEach { upsert(it) } }
        override suspend fun remove(shelfId: String, bookId: String) { rows.remove(shelfId to bookId) }
        override suspend fun clearByBook(bookId: String) { rows.keys.removeAll { it.second == bookId } }
        override fun observeShelfIds(bookId: String): Flow<List<String>> = flowOf(rows.keys.filter { it.second == bookId }.map { it.first })
        override fun observeShelfIdsForBooks(bookIds: List<String>): Flow<List<String>> = flowOf(rows.keys.filter { it.second in bookIds }.map { it.first })
        override suspend fun getBookIds(shelfId: String): List<String> = rows.values.filter { it.shelf_id == shelfId }.map { it.book_id }
        override suspend fun getAllActive(): List<ShelfBookEntity> = rows.values.toList()
        override suspend fun getByBookIds(bookIds: Collection<String>): List<ShelfBookEntity> = rows.values.filter { it.book_id in bookIds }
        override suspend fun clearAll() { rows.clear() }
    }

    private val bookTagDao = StatefulBookTagDao()
    private val bookCategoryDao = StatefulBookCategoryDao()
    private val shelfBookDao = StatefulShelfBookDao()
    private val chapterReadDao = StatefulChapterReadDao()
    private val sourceRefDao = StatefulSourceRefDao()
    private val bookDao = mockk<BookDao>(relaxed = true)
    private val inspirationDao = mockk<InspirationDao>(relaxed = true)
    private val progressDao = mockk<ReadingProgressDao>(relaxed = true)
    private val sessionDao = mockk<ReadingSessionDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val shelfDao = mockk<ShelfDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        sourceRefDao.rows.clear()
        io.mockk.coEvery { bookDao.runInTransaction(any()) } coAnswers
            { firstArg<suspend () -> Unit>().invoke() }
        io.mockk.coEvery { bookDao.getByIds(any()) } returns emptyList()
        io.mockk.coEvery { bookDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { inspirationDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { progressDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { sessionDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { noteDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { highlightDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { tagDao.getAllActive() } returns emptyList()
        io.mockk.coEvery { categoryDao.getAllActive() } returns emptyList()
        io.mockk.coEvery { shelfDao.getAllActive() } returns emptyList()
    }

    private fun bridge(customSourceRefDao: LibrarySourceRefDao? = sourceRefDao) = JsonBridge(
        bookDao, inspirationDao, progressDao, sessionDao,
        noteDao, highlightDao, tagDao, categoryDao, shelfDao,
        bookTagDao, bookCategoryDao, shelfBookDao, chapterReadDao,
        Dispatchers.Unconfined,
        sourceRefDao = customSourceRefDao,
    )

    private fun book(id: String, revision: Int = 1, updatedAt: String = "2026-01-01T00:00:00Z") =
        BookEntity(id = id, title = "书-$id", format = "txt", revision = revision, updated_at = updatedAt)

    private fun bookEnvelope(b: BookEntity) = SyncEnvelope(
        id = b.id, type = "book", revision = b.revision, deviceId = "remote-device",
        updatedAt = b.updated_at, deletedAt = null, payload = b,
    )

    private fun exportJson(
        books: List<SyncEnvelope<BookEntity>> = emptyList(),
        bookTags: List<BookTagEntity> = emptyList(),
        bookCategories: List<BookCategoryEntity> = emptyList(),
        shelfBooks: List<ShelfBookEntity> = emptyList(),
        chapterReads: List<ChapterReadEntity> = emptyList(),
        librarySourceRefs: List<SourceRefExport> = emptyList(),
    ): String {
        val export = LocalExport(
            exportedAt = "2026-08-05T00:00:00Z",
            deviceId = "test-device",
            books = books,
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
            bookTags = bookTags,
            bookCategories = bookCategories,
            shelfBooks = shelfBooks,
            chapterReads = chapterReads,
            librarySourceRefs = librarySourceRefs,
        )
        return json.encodeToString(LocalExport.serializer(), export)
    }

    // ---- 生产路径反例：旧备份 + 本地较新新增（修复的直接目标场景）----

    @Test
    fun `older backup preserves locally added book and all four relation kinds`() = runTest {
        // 备份时库里只有 b1 + 关系（R3 报告的反例）
        val backup = exportJson(
            books = listOf(bookEnvelope(book("b1", revision = 1))),
            bookTags = listOf(BookTagEntity("b1", "t1")),
            bookCategories = listOf(BookCategoryEntity("b1", "c1")),
            shelfBooks = listOf(ShelfBookEntity(shelf_id = "s1", book_id = "b1")),
            chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z")),
        )

        // 备份之后本地新增 b2 和四类关系（b2 实体不在备份里，本地库状态由 fake DAO 表达）
        bookTagDao.upsert(BookTagEntity("b2", "t9"))
        bookCategoryDao.upsert(BookCategoryEntity("b2", "c9"))
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s9", book_id = "b2"))
        chapterReadDao.upsert(ChapterReadEntity("b2", 7, "2026-09-01T00:00:00Z"))

        bridge().importFromString(context, backup)

        // 恢复后：b2 的四类关系全部仍在（旧实现会全部清空）
        assertTrue("b2 标签关联必须保留", bookTagDao.rows.containsKey("b2" to "t9"))
        assertTrue("b2 分类关联必须保留", bookCategoryDao.rows.containsKey("b2" to "c9"))
        assertTrue("b2 书单关联必须保留", shelfBookDao.rows.containsKey("s9" to "b2"))
        assertEquals(ChapterReadEntity("b2", 7, "2026-09-01T00:00:00Z"), chapterReadDao.rows["b2" to 7])
        // 备份关系正常补入
        assertTrue("b1 标签关联应从备份补入", bookTagDao.rows.containsKey("b1" to "t1"))
        assertEquals(2, bookTagDao.rows.size)
    }

    // ---- 本地较新 b1 被拒收旧实体时，其本地关系保持 ----

    @Test
    fun `locally newer book keeps its local relations when entity rejected`() = runTest {
        // 本地 b1 已是 revision 9；备份带 revision 2 旧实体（会被拒收）+ 旧关系
        io.mockk.coEvery { bookDao.getByIds(any()) } returns listOf(book("b1", revision = 9))
        bookTagDao.upsert(BookTagEntity("b1", "new-tag"))
        chapterReadDao.upsert(ChapterReadEntity("b1", 1, "2026-09-01T00:00:00Z"))

        bridge().importFromString(
            context,
            exportJson(
                books = listOf(bookEnvelope(book("b1", revision = 2))),
                bookTags = listOf(BookTagEntity("b1", "old-tag")),
                chapterReads = listOf(ChapterReadEntity("b1", 1, "2026-01-01T00:00:00Z")),
            ),
        )

        // 本地较新实体的关系不被旧备份关系替换；旧备份关系按补入合并共存
        assertTrue("本地新标签关联必须保留", bookTagDao.rows.containsKey("b1" to "new-tag"))
        assertTrue("备份旧标签关联按补入合并共存", bookTagDao.rows.containsKey("b1" to "old-tag"))
    }

    // ---- 备份只有一类关系，不能清掉其余三类 ----

    @Test
    fun `backup with only one relation kind does not clear the other three`() = runTest {
        bookTagDao.upsert(BookTagEntity("b1", "t1"))
        bookCategoryDao.upsert(BookCategoryEntity("b1", "c1"))
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1"))
        chapterReadDao.upsert(ChapterReadEntity("b1", 1, "2026-06-01T00:00:00Z"))

        // 备份只有一个 bookTag，其余三类为空
        bridge().importFromString(context, exportJson(bookTags = listOf(BookTagEntity("b2", "t2"))))

        assertTrue("本地分类关联必须保留", bookCategoryDao.rows.containsKey("b1" to "c1"))
        assertTrue("本地书单关联必须保留", shelfBookDao.rows.containsKey("s1" to "b1"))
        assertEquals(ChapterReadEntity("b1", 1, "2026-06-01T00:00:00Z"), chapterReadDao.rows["b1" to 1])
    }

    // ---- 旧备份空关系列表不能清本地关系 ----

    @Test
    fun `empty relation lists in backup keep local relations`() = runTest {
        bookTagDao.upsert(BookTagEntity("b1", "t1"))
        bookCategoryDao.upsert(BookCategoryEntity("b1", "c1"))
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1"))
        chapterReadDao.upsert(ChapterReadEntity("b1", 1, "2026-06-01T00:00:00Z"))

        bridge().importFromString(context, exportJson()) // 四个关系列表全空

        assertEquals(1, bookTagDao.rows.size)
        assertEquals(1, bookCategoryDao.rows.size)
        assertEquals(1, shelfBookDao.rows.size)
        assertEquals(1, chapterReadDao.rows.size)
    }

    // ---- 正常有效备份关系可补入 ----

    @Test
    fun `valid backup relations are merged in`() = runTest {
        bridge().importFromString(
            context,
            exportJson(
                bookTags = listOf(BookTagEntity("b1", "t1"), BookTagEntity("b1", "t2")),
                bookCategories = listOf(BookCategoryEntity("b1", "c1")),
                shelfBooks = listOf(ShelfBookEntity(shelf_id = "s1", book_id = "b1")),
                chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z")),
            ),
        )

        assertEquals(setOf("b1" to "t1", "b1" to "t2"), bookTagDao.rows.keys)
        assertEquals(setOf("b1" to "c1"), bookCategoryDao.rows.keys)
        assertEquals(setOf("s1" to "b1"), shelfBookDao.rows.keys)
        assertEquals(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z"), chapterReadDao.rows["b1" to 3])
    }

    // ---- 重复导入幂等 ----

    @Test
    fun `repeated import of same backup is idempotent`() = runTest {
        val snapshot = exportJson(
            bookTags = listOf(BookTagEntity("b1", "t1")),
            bookCategories = listOf(BookCategoryEntity("b1", "c1")),
            shelfBooks = listOf(ShelfBookEntity(shelf_id = "s1", book_id = "b1")),
            chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z")),
        )

        bridge().importFromString(context, snapshot)
        bridge().importFromString(context, snapshot)

        assertEquals(1, bookTagDao.rows.size)
        assertEquals(1, bookCategoryDao.rows.size)
        assertEquals(1, shelfBookDao.rows.size)
        assertEquals(1, chapterReadDao.rows.size)
        assertEquals("2026-01-01T00:00:00Z", chapterReadDao.rows["b1" to 3]?.read_at)
    }

    // ---- 书单同键 position 保护（R3-v2 返修目标）----

    @Test
    fun `existing shelf relation keeps local position over backup`() = runTest {
        // 本地同键 (s1, b1) 已被用户重排到 position=9；旧备份仍是 position=1。
        // 旧实现（v1）直接 upsertAll，REPLACE 整行覆盖 → position 退回 1。
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 9))

        bridge().importFromString(
            context,
            exportJson(shelfBooks = listOf(ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 1))),
        )

        assertEquals("同键书单关联必须整行保留本地 position", 9, shelfBookDao.rows["s1" to "b1"]?.position)
    }

    @Test
    fun `missing shelf relation is added with backup position`() = runTest {
        // 本地只有 (s1, b1)；备份带本地缺失的 (s1, b2, position=4) → 按备份补入
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 0))

        bridge().importFromString(
            context,
            exportJson(shelfBooks = listOf(ShelfBookEntity(shelf_id = "s1", book_id = "b2", position = 4))),
        )

        assertEquals(0, shelfBookDao.rows["s1" to "b1"]?.position)
        assertEquals("缺失关系按备份 position 补入", 4, shelfBookDao.rows["s1" to "b2"]?.position)
    }

    @Test
    fun `repeated shelf relation import keeps local position and no duplicates`() = runTest {
        shelfBookDao.upsert(ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 9))
        val snapshot = exportJson(
            shelfBooks = listOf(
                ShelfBookEntity(shelf_id = "s1", book_id = "b1", position = 1),
                ShelfBookEntity(shelf_id = "s1", book_id = "b2", position = 2),
            ),
        )

        bridge().importFromString(context, snapshot)
        bridge().importFromString(context, snapshot)

        assertEquals(2, shelfBookDao.rows.size)
        assertEquals("重复恢复不得倒退本地 position", 9, shelfBookDao.rows["s1" to "b1"]?.position)
        assertEquals(2, shelfBookDao.rows["s1" to "b2"]?.position)
    }

    // ---- 章节 read_at 不倒退 ----

    @Test
    fun `chapter read_at keeps the newer timestamp on conflict`() = runTest {
        // 本地较新已读（2026-09）；旧备份带同一章较旧 read_at（2026-01）
        chapterReadDao.upsert(ChapterReadEntity("b1", 3, "2026-09-01T00:00:00Z"))

        bridge().importFromString(
            context,
            exportJson(chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z"))),
        )

        assertEquals("本地较新 read_at 必须保留", "2026-09-01T00:00:00Z", chapterReadDao.rows["b1" to 3]?.read_at)
    }

    @Test
    fun `chapter read_at accepts backup newer timestamp`() = runTest {
        chapterReadDao.upsert(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z"))

        bridge().importFromString(
            context,
            exportJson(chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-09-01T00:00:00Z"))),
        )

        assertEquals("备份较新 read_at 应被接受", "2026-09-01T00:00:00Z", chapterReadDao.rows["b1" to 3]?.read_at)
    }

    // ---- 写入失败异常传播：全有状态，异常必须原样上抛 ----

    @Test
    fun `mid-import failure propagates and stops later relation writes`() = runTest {
        val boom = RuntimeException("chapter_reads 写入失败")
        val chapterReadDaoFailing = object : ChapterReadDao {
            override suspend fun upsertAll(list: List<ChapterReadEntity>) { throw boom }
            override suspend fun upsert(entity: ChapterReadEntity) {}
            override fun observeReadChapters(bookId: String): Flow<List<Int>> = flowOf(emptyList())
            override fun observeReadCount(bookId: String): Flow<Int> = flowOf(0)
            override suspend fun readCount(bookId: String): Int = 0
            override suspend fun getReadAt(bookId: String, chapterIndex: Int): String? = null
            override suspend fun clearForBook(bookId: String) {}
            override suspend fun getAll(): List<ChapterReadEntity> = emptyList()
            override suspend fun getByBookIds(bookIds: Collection<String>): List<ChapterReadEntity> = emptyList()
            override suspend fun clearAll() {}
        }
        val bridge = JsonBridge(
            bookDao, inspirationDao, progressDao, sessionDao,
            noteDao, highlightDao, tagDao, categoryDao, shelfDao,
            bookTagDao, bookCategoryDao, shelfBookDao, chapterReadDaoFailing,
            Dispatchers.Unconfined,
        )
        bookTagDao.upsert(BookTagEntity("local", "t1"))

        val result = runCatching {
            bridge.importFromString(
                context,
                exportJson(
                    bookTags = listOf(BookTagEntity("b1", "t1")),
                    chapterReads = listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z")),
                ),
            )
        }

        assertTrue("写入失败必须上抛（真实 Room 事务回滚的前置条件）", result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
        // 模拟层级声明：本 fake 不回滚，"local 行仍在"只证明异常上抛前本地行未被本次
        // 导入改动；真实 Room 事务回滚由既有 androidTest LocalExportRoomRollbackTest
        // 覆盖（未在本轮重跑），本用例不宣称已运行真实回滚。
        assertTrue("异常上抛前本地既有行保持（fake 不回滚，非真实事务证明）", bookTagDao.rows.containsKey("local" to "t1"))
        assertTrue("失败前的备份 bookTag 已写入（真实 Room 中将随事务回滚）", bookTagDao.rows.containsKey("b1" to "t1"))
    }

    // ---- 语义对齐检查：fake DAO 的 REPLACE 语义与生产注释一致 ----

    @Test
    fun `fake dao replace semantics match room insert-or-replace`() = runTest {
        // chapter_reads 复合主键 (book_id, chapter_index)：upsertAll 直接覆盖整行
        chapterReadDao.upsert(ChapterReadEntity("b1", 3, "2026-09-01T00:00:00Z"))
        chapterReadDao.upsertAll(listOf(ChapterReadEntity("b1", 3, "2026-01-01T00:00:00Z")))
        // 最后一行胜出——证明生产代码必须先比较 read_at 再决定是否写入（mergeChapterReads 的存在意义）
        assertEquals("2026-01-01T00:00:00Z", chapterReadDao.rows["b1" to 3]?.read_at)
    }

    private fun ChapterReadEntity(bookId: String, chapterIndex: Int, readAt: String) =
        com.creationreadingassistant.data.local.entity.ChapterReadEntity(
            book_id = bookId, chapter_index = chapterIndex, read_at = readAt,
        )

    @Test
    fun `import keeps inspiration and clears source_book_id when parent book missing`() = runTest {
        val inspWithMissingBook = com.creationreadingassistant.data.local.entity.InspirationEntity(
            id = "insp-orphan",
            title = "灵感-孤立书籍",
            body = "灵感正文必须保留",
            source_book_id = "deleted-book-999",
            created_at = "2026-01-01T00:00:00Z",
            updated_at = "2026-01-01T00:00:00Z",
        )
        val envelope = SyncEnvelope(
            id = inspWithMissingBook.id,
            type = "inspiration",
            revision = 1,
            deviceId = "dev",
            updatedAt = inspWithMissingBook.updated_at,
            deletedAt = null,
            payload = inspWithMissingBook,
        )

        val captured = io.mockk.slot<com.creationreadingassistant.data.local.entity.InspirationEntity>()
        io.mockk.coEvery { inspirationDao.upsert(capture(captured)) } returns Unit
        io.mockk.coEvery { bookDao.getById("deleted-book-999") } returns null

        val export = LocalExport(
            exportedAt = "2026-01-01T00:00:00Z",
            deviceId = "dev",
            books = emptyList(),
            inspirations = listOf(envelope),
            progress = emptyList(),
            sessions = emptyList(),
        )
        val jsonStr = localExportJson.encodeToString(LocalExport.serializer(), export)

        bridge().importFromString(context, jsonStr)

        assertTrue("灵感必须被入库写入", captured.isCaptured)
        assertEquals("insp-orphan", captured.captured.id)
        assertEquals("灵感正文必须保留", captured.captured.body)
        assertEquals(null, captured.captured.source_book_id)
    }

    // ---- 来源引用孤立排除与保守合并测试 ----

    @Test
    fun `export filters out source refs of deleted books and retains active books`() = runTest {
        val activeBook = book("active-b1")
        io.mockk.coEvery { bookDao.observeAllActive() } returns flowOf(listOf(activeBook))
        io.mockk.coEvery { inspirationDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { progressDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { sessionDao.observeAllActive() } returns flowOf(emptyList())
        io.mockk.coEvery { tagDao.getAllActive() } returns emptyList()
        io.mockk.coEvery { categoryDao.getAllActive() } returns emptyList()
        io.mockk.coEvery { shelfDao.getAllActive() } returns emptyList()
        sourceRefDao.upsert(sourceRef("active-b1", "active.txt"))
        sourceRefDao.upsert(sourceRef("deleted-b2", "deleted.txt"))

        val export = bridge().buildExport(context)

        assertEquals("导出的有效书目为1本", 1, export.books.size)
        assertEquals("仅保留活跃书籍的来源引用，排除已软删书籍引用", listOf("active-b1"), export.librarySourceRefs.map { it.book_id })
    }

    @Test
    fun `import skips orphan source refs missing parent book in both export and local db`() = runTest {
        val export = exportJson(
            books = listOf(bookEnvelope(book("b1"))),
            librarySourceRefs = listOf(
                sourceRefExport("b1", "b1.txt"),
                sourceRefExport("orphan-b2", "orphan.txt"),
            ),
        )

        bridge().importFromString(context, export)

        assertTrue("有效书籍来源引用必须正常恢复", sourceRefDao.rows.containsKey("b1"))
        assertTrue("缺失父记录的旧包孤立引用必须被跳过不入库", !sourceRefDao.rows.containsKey("orphan-b2"))
    }

    @Test
    fun `import retains source ref for book existing in local db even if book absent from export`() = runTest {
        val localBook = book("existing-local")
        io.mockk.coEvery { bookDao.getByIds(match { "existing-local" in it }) } returns listOf(localBook)

        val export = exportJson(
            books = emptyList(),
            librarySourceRefs = listOf(
                sourceRefExport("existing-local", "local.txt"),
                sourceRefExport("pure-orphan", "pure.txt"),
            ),
        )

        bridge().importFromString(context, export)

        assertTrue("本地已有合法书目的旧来源引用应恢复入库", sourceRefDao.rows.containsKey("existing-local"))
        assertTrue("纯无主孤立引用必须跳过", !sourceRefDao.rows.containsKey("pure-orphan"))
    }

    @Test
    fun `import keeps newer local source ref when timestamp is conflict`() = runTest {
        sourceRefDao.upsert(sourceRef("b1", "b1.txt", lastSeenAt = 2000L))

        val export = exportJson(
            books = listOf(bookEnvelope(book("b1"))),
            librarySourceRefs = listOf(sourceRefExport("b1", "b1-old.txt", lastSeenAt = 1000L)),
        )

        bridge().importFromString(context, export)

        assertEquals("本地较新来源引用必须保留", "b1.txt", sourceRefDao.rows["b1"]?.display_name)
        assertEquals(2000L, sourceRefDao.rows["b1"]?.last_seen_at)
    }

    @Test
    fun `import accepts newer remote source ref when timestamp is newer`() = runTest {
        sourceRefDao.upsert(sourceRef("b1", "b1-old.txt", lastSeenAt = 1000L))

        val export = exportJson(
            books = listOf(bookEnvelope(book("b1"))),
            librarySourceRefs = listOf(sourceRefExport("b1", "b1-new.txt", lastSeenAt = 2000L)),
        )

        bridge().importFromString(context, export)

        assertEquals("备份较新来源引用应覆盖", "b1-new.txt", sourceRefDao.rows["b1"]?.display_name)
        assertEquals(2000L, sourceRefDao.rows["b1"]?.last_seen_at)
    }

    private fun sourceRef(bookId: String, name: String, lastSeenAt: Long = 1000L) =
        LibrarySourceRefEntity(
            book_id = bookId,
            root_id = null,
            provider_authority = "com.android.providers.downloads.documents",
            document_id = "doc-$name",
            display_name = name,
            format = "txt",
            size = 100L,
            last_modified = 1000L,
            content_hash = "hash-$name",
            candidate_fingerprint = null,
            last_seen_at = lastSeenAt,
            availability = "available",
        )

    private fun sourceRefExport(bookId: String, name: String, lastSeenAt: Long = 1000L) =
        SourceRefExport.fromEntity(sourceRef(bookId, name, lastSeenAt))
}
