package com.creationreadingassistant.feature.sync.legado

import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.LegadoImportDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchItemEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoader
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM 门禁（契约 §8）：预览分类阶梯、提交不变量、幂等、撤销语义、并发保护。
 * DB 层以 mockk 承载（与 WebDavBackupTest 同风格）；真实 Room 迁移/行为由
 * androidTest（AppDatabaseMigrationTest）与设备门禁覆盖。
 */
class LegadoImportPipelineTest {

    @MockK
    lateinit var db: AppDatabase

    @MockK
    lateinit var bookDao: BookDao

    @MockK
    lateinit var progressDao: ReadingProgressDao

    @MockK
    lateinit var legadoDao: LegadoImportDao

    private val chapters = listOf(
        DocChapter(0, "第一章 起点", 0, 100),
        DocChapter(1, "第二章 边界", 100, 100),
        DocChapter(2, "第三章 尾声", 200, 100),
    )

    private val savedProgress = HashMap<String, ReadingProgressEntity>()
    private val savedBooks = HashMap<String, BookEntity>()
    private val capturedItems = ArrayList<LegadoImportBatchItemEntity>()
    private val capturedBatches = HashMap<String, LegadoImportBatchEntity>()
    private val capturedBindings = HashMap<String, com.creationreadingassistant.data.local.entity.LegadoBookBindingEntity>()
    private var sessionInsertReturns: List<Long>? = null
    private var sessionInsertCalls = 0

    private lateinit var pipeline: LegadoImportPipeline

    private fun txtBook(id: String, title: String = "测试书甲", author: String? = "测试作者", format: String = "txt") =
        BookEntity(id = id, title = title, author = author, format = format, updated_at = "2026-01-01T00:00:00Z")

    private fun seedSessionInsertResult(result: List<Long>) {
        sessionInsertReturns = result
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery { bookDao.getAllActive() } answers { savedBooks.values.toList() }
        coEvery { bookDao.getById(any()) } answers { savedBooks[firstArg()] }
        coEvery { progressDao.getByBook(any()) } answers { savedProgress[firstArg()] }
        coEvery { progressDao.upsert(any()) } answers {
            val entity = firstArg<ReadingProgressEntity>()
            savedProgress[entity.book_id] = entity
        }
        coEvery { legadoDao.insertBatchItems(any()) } answers { capturedItems.addAll(firstArg()); Unit }
        coEvery { legadoDao.getBatchItems(any()) } answers { capturedItems.toList() }
        coEvery { legadoDao.insertBatch(any()) } answers {
            val batch = firstArg<LegadoImportBatchEntity>()
            capturedBatches[batch.batch_id] = batch
        }
        coEvery { legadoDao.finishBatch(any(), any(), any()) } answers {
            val old = capturedBatches[firstArg()]
            if (old != null) capturedBatches[firstArg()] = old.copy(status = secondArg(), undo_note = thirdArg())
        }
        coEvery { legadoDao.getBatch(any()) } answers { capturedBatches[firstArg()] }
        coEvery { legadoDao.insertSessionsIfAbsent(any()) } answers {
            sessionInsertCalls++
            when {
                sessionInsertReturns != null -> sessionInsertReturns!!
                sessionInsertCalls == 1 -> List(firstArg<List<*>>().size) { 1L }
                else -> List(firstArg<List<*>>().size) { -1L }
            }
        }
        coEvery { legadoDao.insertDaySnapshotsIfAbsent(any()) } answers {
            List(firstArg<List<*>>().size) { 1L }
        }
        coEvery { legadoDao.insertBookSnapshotsIfAbsent(any()) } answers {
            List(firstArg<List<*>>().size) { 1L }
        }
        coEvery { legadoDao.getActiveBindings(any()) } returns emptyList()
        coEvery { legadoDao.insertSubjectsIfAbsent(any()) } answers {
            List(firstArg<List<*>>().size) { 1L }
        }
        coEvery { legadoDao.countOtherRefs(any(), any()) } returns 0
        coEvery { legadoDao.getSources() } returns emptyList()
        coEvery { legadoDao.insertBinding(any()) } answers {
            val b = firstArg<com.creationreadingassistant.data.local.entity.LegadoBookBindingEntity>()
            capturedBindings[b.subject_id] = b
        }
        coEvery { legadoDao.getBinding(any()) } answers { capturedBindings[firstArg()] }
        coEvery { legadoDao.deleteBinding(any()) } answers { capturedBindings.remove(firstArg<String>()) }

        val documentLoader = mockk<ReaderDocumentLoader> {
            coEvery { load(any()) } answers {
                val bookId = firstArg<String>()
                val book = savedBooks[bookId]
                ReaderLoadedBook(
                    id = bookId,
                    title = book?.title ?: "",
                    author = book?.author,
                    originalFileName = null,
                    sizeBytes = 300,
                    savedReadingTimeMs = 0,
                    initialProgressPercent = 0f,
                    content = ReaderLoadedContent.Text(
                        fullText = "x".repeat(300),
                        streamingDocument = null,
                        fileIndex = null,
                        sourceFile = null,
                        ownedTempFile = null,
                        initialAbsoluteOffset = 0,
                        preDetectedChapters = chapters,
                    ),
                )
            }
        }
        pipeline = object : LegadoImportPipeline(db, bookDao, progressDao, legadoDao, documentLoader) {
            override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
        }
    }

    private fun seedLocalBook(vararg books: BookEntity) {
        books.forEach { savedBooks[it.id] = it }
    }

    private suspend fun preview(): LegadoImportPipeline.LegadoPreview =
        pipeline.buildPreview(LegadoFixtures.zip(), "测试备份.zip", sourceId = null)

    // ---- a) 章级落点 + 提交不变量 ----
    @Test
    fun `chapter-level landing writes verified chapter start with invariants`() = runTest {
        seedLocalBook(txtBook("b1"))
        val p = preview()
        val row = p.positions.single()
        assertEquals(LegadoImportPipeline.PositionCategory.CHAPTER, row.category)
        assertTrue(row.importable)
        assertEquals("b1", row.selectedBookId)
        assertEquals(100, row.landing?.txtAbsoluteOffset)

        val result = pipeline.commit(
            p,
            LegadoImportPipeline.CommitSelection(
                sourceId = null, newSourceLabel = null,
                importPositionOrdinals = setOf(0), importHistory = true,
                replaceSnapshotRowKeys = emptySet(),
            ),
        )
        assertTrue("commit: ${result.message}", result.ok)
        assertEquals(1, result.importedPositions)
        assertEquals(1, result.counts.sessions)
        assertEquals(1, result.bindingsCreated)

        val written = savedProgress["b1"]!!
        val locator = LocatorCodec.locatorFromProgress(written.current_location_json)
        assertNotNull(locator)
        assertEquals(100, locator!!.legacyOffset)
        assertEquals(33.33f, written.progress_percent, 0.5f)
        assertTrue(written.progress_percent <= LegadoImportPipeline.MAX_IMPORTED_PERCENT)
        assertNull(written.last_read_at) // 新行写 null，不冒充最近阅读
        assertNull(written.completed_at) // 绝不推导 FINISHED
        assertEquals("reading", written.completion_state)
        assertEquals(1, written.revision)
    }

    // ---- b) 同包重导零业务增量 ----
    @Test
    fun `second import of same archive adds zero business rows`() = runTest {
        seedLocalBook(txtBook("b1"))
        val selection = LegadoImportPipeline.CommitSelection(
            sourceId = null, newSourceLabel = null,
            importPositionOrdinals = setOf(0), importHistory = true,
            replaceSnapshotRowKeys = emptySet(),
        )
        val first = pipeline.commit(preview(), selection)
        assertTrue("first: ${first.message}", first.ok)
        val upsertsAfterFirst = savedProgress.size

        // 第二次：位置变 DUPLICATE（本地一致），会话变 REF。
        val second = pipeline.commit(preview(), selection)
        assertTrue("second: ${second.message}", second.ok)
        assertEquals(0, second.importedPositions)
        assertEquals(1, second.counts.refs) // 会话幂等引用
        assertEquals(0, second.counts.sessions)
        assertEquals(upsertsAfterFirst, savedProgress.size)
        coVerify(exactly = 1) { progressDao.upsert(match { it.book_id == "b1" && it.deleted_at == null }) }
    }

    // ---- c) FINISHED 全保护（含空位置行） ----
    @Test
    fun `finished book position protected even with empty location`() = runTest {
        seedLocalBook(txtBook("b1"))
        savedProgress["b1"] = ReadingProgressEntity(
            book_id = "b1", completion_state = "finished",
            current_location_json = null, updated_at = "2026-01-01T00:00:00Z",
        )
        val p = preview()
        val row = p.positions.single()
        assertEquals(LegadoImportPipeline.PositionCategory.CONFLICT, row.category)
        assertFalse(row.importable)
        assertNotNull(row.protection)
        assertTrue(row.protection!!.contains("FINISHED"))

        val result = pipeline.commit(
            p,
            LegadoImportPipeline.CommitSelection(
                sourceId = null, newSourceLabel = null,
                importPositionOrdinals = setOf(0), importHistory = false,
                replaceSnapshotRowKeys = emptySet(),
            ),
        )
        assertTrue("commit: ${result.message}", result.ok)
        assertEquals(0, result.importedPositions)
        coVerify(exactly = 0) { progressDao.upsert(match { it.book_id == "b1" }) }
    }

    // ---- d) Markdown 位置排除，历史可导 ----
    @Test
    fun `markdown book position missing but history importable`() = runTest {
        seedLocalBook(txtBook("b2", format = "md"))
        val p = preview()
        val row = p.positions.single()
        assertEquals(LegadoImportPipeline.PositionCategory.MISSING, row.category)
        assertFalse(row.importable)

        val result = pipeline.commit(
            p,
            LegadoImportPipeline.CommitSelection(
                sourceId = null, newSourceLabel = null,
                importPositionOrdinals = setOf(0), importHistory = true,
                replaceSnapshotRowKeys = emptySet(),
            ),
        )
        assertTrue("commit: ${result.message}", result.ok)
        assertEquals(0, result.importedPositions)
        assertEquals(1, result.counts.sessions)
        coVerify(exactly = 0) { progressDao.upsert(any()) }
    }

    // ---- e) 同名副本冲突 + 预览手选覆盖 ----
    @Test
    fun `ambiguous candidates resolved only by explicit override`() = runTest {
        seedLocalBook(txtBook("b3"), txtBook("b4"))
        val p = preview()
        val row = p.positions.single()
        assertEquals(LegadoImportPipeline.PositionCategory.CONFLICT, row.category)
        assertFalse(row.importable)
        assertEquals(2, row.candidates.size)

        val overridden = p.copy(
            positions = p.positions.map {
                if (it.ordinal == row.ordinal) it.copy(importable = true) else it
            },
        )
        val result = pipeline.commit(
            overridden,
            LegadoImportPipeline.CommitSelection(
                sourceId = null, newSourceLabel = null,
                importPositionOrdinals = setOf(row.ordinal), importHistory = false,
                replaceSnapshotRowKeys = emptySet(),
                candidateOverrides = mapOf(row.ordinal to "b3"),
            ),
        )
        assertTrue("commit: ${result.message}", result.ok)
        val written = savedProgress["b3"]!!
        assertNotNull(written.current_location_json)
        assertNull(savedProgress["b4"])
    }

    // ---- f) 并发保存 → 零写入 ----
    @Test
    fun `reader autosave between preview and commit aborts with zero writes`() = runTest {
        seedLocalBook(txtBook("b1"))
        savedProgress["b1"] = ReadingProgressEntity(
            book_id = "b1", current_location_json = "{\"offset\":0}",
            updated_at = "2026-01-01T00:00:00Z",
        )
        val p = preview()
        val row = p.positions.single()
        assertEquals(LegadoImportPipeline.PositionCategory.CONFLICT, row.category)

        // 预览后阅读器自动保存到别处。
        savedProgress["b1"] = savedProgress["b1"]!!.copy(
            current_location_json = "{\"offset\":150}", revision = 5,
        )
        val result = pipeline.commit(
            p,
            LegadoImportPipeline.CommitSelection(
                sourceId = null, newSourceLabel = null,
                importPositionOrdinals = setOf(row.ordinal), importHistory = false,
                replaceSnapshotRowKeys = emptySet(),
            ),
        )
        assertFalse(result.ok)
        assertTrue(result.message.contains("零写入"))
        assertEquals("{\"offset\":150}", savedProgress["b1"]!!.current_location_json)
    }

    // ---- g) 撤销：新建行软删除 + 重试幂等 ----
    @Test
    fun `undo soft-deletes created row and retry returns original result`() = runTest {
        seedLocalBook(txtBook("b1"))
        val selection = LegadoImportPipeline.CommitSelection(
            sourceId = null, newSourceLabel = null,
            importPositionOrdinals = setOf(0), importHistory = false,
            replaceSnapshotRowKeys = emptySet(),
        )
        val result = pipeline.commit(preview(), selection)
        assertTrue("commit: ${result.message}", result.ok)
        assertNotNull(savedProgress["b1"])

        val undo = pipeline.undoBatch(result.batchId!!)
        assertTrue(undo.ok)
        val after = savedProgress["b1"]!!
        assertNotNull(after.deleted_at) // 原无行 → 项目既有删除语义（软删除）
        assertEquals(2, after.revision)

        val retry = pipeline.undoBatch(result.batchId)
        assertTrue(retry.ok)
        assertEquals("undone_full", retry.status)
        assertEquals(2, after.revision) // 幂等：不再变更
    }

    // ---- h) 撤销：后续阅读修改 → 保留当前值（部分撤销） ----
    @Test
    fun `undo keeps current value when later reading changed progress`() = runTest {
        seedLocalBook(txtBook("b1"))
        val selection = LegadoImportPipeline.CommitSelection(
            sourceId = null, newSourceLabel = null,
            importPositionOrdinals = setOf(0), importHistory = false,
            replaceSnapshotRowKeys = emptySet(),
        )
        val result = pipeline.commit(preview(), selection)
        // 导入后本地继续阅读（版本+内容都变）。
        savedProgress["b1"] = savedProgress["b1"]!!.copy(
            revision = 9, current_location_json = "{\"offset\":250}",
        )
        val undo = pipeline.undoBatch(result.batchId!!)
        // 契约 §9：有冲突仍可撤销独立条目——绑定项安全回退（reverted=1），
        // 被后续阅读修改的进度保留当前值（kept>0）→ 部分撤销。
        assertEquals("undone_partial", undo.status)
        assertEquals(1, undo.reverted)
        assertTrue(undo.kept > 0)
        assertEquals("{\"offset\":250}", savedProgress["b1"]!!.current_location_json)
    }
}
