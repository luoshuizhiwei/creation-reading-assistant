package com.creationreadingassistant.feature.sync

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.bookStatus
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LegadoReadingProgressImporterTest {

    private val bookDao = mockk<BookDao>()
    private val progressDao = mockk<ReadingProgressDao>()
    private val importer = LegadoReadingProgressImporter(bookDao, progressDao)

    @Test
    fun `imports only uniquely matched book and preserves epub chapter coordinate`() = runTest {
        val epub = book(id = "epub-1", title = "  示例 书  ", author = "作者", format = "epub")
        val sameTitleOne = book(id = "dup-1", title = "同名书", author = "甲")
        val sameTitleTwo = book(id = "dup-2", title = "同名书", author = "甲")
        coEvery { bookDao.getAllActive() } returns listOf(epub, sameTitleOne, sameTitleTwo)
        coEvery { progressDao.getByBooks(listOf("epub-1")) } returns emptyList()
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val archive = zipOf(
            """
            [
              {"name":"示例  书","author":"作者","durChapterIndex":8,"durChapterPos":321,"durChapterTime":1750000000000,"durChapterTitle":"第九章","totalChapterNum":20},
              {"name":"同名书","author":"甲","durChapterIndex":2,"durChapterPos":4},
              {"name":"不存在","author":"乙","durChapterIndex":1,"durChapterPos":2},
              {"name":"坏数据","author":"乙","durChapterIndex":-1,"durChapterPos":2}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        assertEquals(4, report.scanned)
        assertEquals(1, report.imported)
        assertEquals(1, report.ambiguous)
        assertEquals(1, report.unmatched)
        assertEquals(1, report.invalid)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals("epub-1", saved.captured.book_id)
        assertEquals(40f, saved.captured.progress_percent)
        val locator = LocatorCodec.locatorFromProgress(saved.captured.current_location_json)
        assertEquals(8, locator?.chapterIndex)
        assertEquals(321, locator?.charOffset)
    }

    @Test
    fun `does not overwrite an existing native plain text position`() = runTest {
        val text = book(id = "text-1", title = "同一本", author = "作者", format = "txt")
        coEvery { bookDao.getAllActive() } returns listOf(text)
        coEvery { progressDao.getByBooks(listOf("text-1")) } returns listOf(
            ReadingProgressEntity(
                book_id = "text-1",
                current_location_json = """{"offset":9999,"locator_v2":{"v":2,"offset":9999,"ci":0,"co":9999}}""",
                updated_at = "2026-09-01T00:00:00Z",
            ),
        )
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val report = importer.importArchive(zipOf("""[{"name":"同一本","author":"作者","durChapterIndex":9,"durChapterPos":1}]"""))

        assertEquals(0, report.imported)
        assertEquals(1, report.unchanged)
        coVerify(exactly = 0) { progressDao.upsert(any()) }
    }

    @Test
    fun `plain text stores chapter marker instead of unsafe raw offset`() = runTest {
        val text = book(id = "text-1", title = "同一本", author = "作者", format = "txt")
        coEvery { bookDao.getAllActive() } returns listOf(text)
        coEvery { progressDao.getByBooks(listOf("text-1")) } returns emptyList()
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        importer.importArchive(zipOf("""[{"name":"同一本","author":"作者","durChapterIndex":3,"durChapterPos":77}]"""))

        val saved = slot<ReadingProgressEntity>()
        coVerify { progressDao.upsert(capture(saved)) }
        assertEquals(LegadoProgressMarker.encode(3, 77), saved.captured.current_location_json)
    }

    @Test
    fun `markdown book is counted unsupported instead of storing an unreadable marker`() = runTest {
        // 回归：Markdown 的章节按 H1/H2 切、偏移基于规范阅读文本，与开源阅读的目录
        // 规则不等价。若此处仍写入 legado 标记，打开该书时 parseStoredOffset 读不到
        // "offset" 键会静默回到书首，用户却看到「已导入 1 条」。
        val markdown = book(id = "md-1", title = "同一本", author = "作者", format = "md")
        coEvery { bookDao.getAllActive() } returns listOf(markdown)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val report = importer.importArchive(
            zipOf("""[{"name":"同一本","author":"作者","durChapterIndex":3,"durChapterPos":77}]"""),
        )

        assertEquals(0, report.imported)
        assertEquals(1, report.unsupported)
        assertEquals(1, report.skipped)
        coVerify(exactly = 0) { progressDao.upsert(any()) }
        assertTrue(report.summary().contains("暂不支持导入 1"))
    }

    @Test
    fun `mixed markdown and plain text candidates stay ambiguous`() = runTest {
        // 排除 Markdown 不得放宽既有歧义语义：同名同作者只要多于一个候选，
        // 就仍然跳过，而不是挑出其中可导入的那一本。
        val markdown = book(id = "md-1", title = "同名", author = "甲", format = "md")
        val text = book(id = "txt-1", title = "同名", author = "甲", format = "txt")
        coEvery { bookDao.getAllActive() } returns listOf(markdown, text)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val report = importer.importArchive(
            zipOf("""[{"name":"同名","author":"甲","durChapterIndex":2,"durChapterPos":9}]"""),
        )

        assertEquals(0, report.imported)
        assertEquals(0, report.unsupported)
        assertEquals(1, report.ambiguous)
        coVerify(exactly = 0) { progressDao.upsert(any()) }
    }

    @Test
    fun `rejects archive without bookshelf entry`() {
        val error = runCatching { importer.parseArchive(zipOf("{}", entryName = "config.xml")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("bookshelf.json"))
    }

    @Test
    fun `importing last chapter does not silently mark book as finished and shelf status stays reading`() = runTest {
        val epub = book(id = "epub-last", title = "末章测试书", author = "作者", format = "epub")
        coEvery { bookDao.getAllActive() } returns listOf(epub)
        coEvery { progressDao.getByBooks(listOf("epub-last")) } returns emptyList()
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val archive = zipOf(
            """
            [
              {"name":"末章测试书","author":"作者","durChapterIndex":999,"durChapterPos":10,"durChapterTime":1750000000000,"durChapterTitle":"第一千章","totalChapterNum":1000}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        assertEquals(1, report.imported)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals("reading", saved.captured.completion_state)
        assertEquals(null, saved.captured.completed_at)
        assertTrue("progress_percent should be strictly below 99.5f", saved.captured.progress_percent < 99.5f)
        assertEquals(LegadoReadingProgressImporter.MAX_IMPORTED_PERCENT, saved.captured.progress_percent, 0.001f)
        // 关键断言：书架状态判定必须为 READING，绝不可被分类到 COMPLETED（已完成）
        assertEquals(
            ShelfStatusFilter.READING,
            bookStatus(epub, saved.captured.progress_percent, saved.captured.completion_state),
        )
    }

    @Test
    fun `importing progress on shelved book preserves shelved state and existing fields`() = runTest {
        val text = book(id = "text-shelved", title = "搁置测试书", author = "作者", format = "txt")
        val existing = ReadingProgressEntity(
            book_id = "text-shelved",
            progress_percent = 20f,
            completion_state = ReadingCompletionState.SHELVED.storageValue,
            payload = "user_shelf_payload",
            device_id = "device_alpha",
            revision = 7,
            current_location_json = LegadoProgressMarker.encode(1, 50),
            updated_at = "2026-09-01T00:00:00Z",
        )
        coEvery { bookDao.getAllActive() } returns listOf(text)
        coEvery { progressDao.getByBooks(listOf("text-shelved")) } returns listOf(existing)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        // 导入末章（999/1000），即使百分比接近 100%，也必须保持 SHELVED
        val archive = zipOf(
            """
            [
              {"name":"搁置测试书","author":"作者","durChapterIndex":999,"durChapterPos":10,"totalChapterNum":1000}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        assertEquals(1, report.imported)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals(ReadingCompletionState.SHELVED.storageValue, saved.captured.completion_state)
        assertEquals(null, saved.captured.completed_at)
        assertEquals("user_shelf_payload", saved.captured.payload)
        assertEquals("device_alpha", saved.captured.device_id)
        assertEquals(7, saved.captured.revision)
        assertEquals(LegadoReadingProgressImporter.MAX_IMPORTED_PERCENT, saved.captured.progress_percent, 0.001f)
        // 书架状态必须依然是 SHELVED
        assertEquals(
            ShelfStatusFilter.SHELVED,
            bookStatus(text, saved.captured.progress_percent, saved.captured.completion_state),
        )
    }

    @Test
    fun `importing on already finished book is skipped as unchanged`() = runTest {
        val epub = book(id = "epub-finished", title = "已完成测试书", author = "作者", format = "epub")
        val existing = ReadingProgressEntity(
            book_id = "epub-finished",
            progress_percent = 90f,
            completion_state = ReadingCompletionState.FINISHED.storageValue,
            completed_at = 1750000000000L,
            current_location_json = """{"chapter":10,"offset":100,"locator_v2":{"v":2,"ci":10,"co":100}}""",
            updated_at = "2026-09-01T00:00:00Z",
        )
        coEvery { bookDao.getAllActive() } returns listOf(epub)
        coEvery { progressDao.getByBooks(listOf("epub-finished")) } returns listOf(existing)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val archive = zipOf(
            """
            [
              {"name":"已完成测试书","author":"作者","durChapterIndex":999,"durChapterPos":100,"totalChapterNum":1000}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        // 用户明确标记已完成的书籍跳过导入，不覆盖本地状态
        assertEquals(0, report.imported)
        assertEquals(1, report.unchanged)
        coVerify(exactly = 0) { progressDao.upsert(any()) }
    }

    @Test
    fun `importing mid progress updates reading book normally and preserves fields`() = runTest {
        val text = book(id = "text-reading", title = "中段阅读书", author = "作者", format = "txt")
        val existing = ReadingProgressEntity(
            book_id = "text-reading",
            progress_percent = 10f,
            completion_state = ReadingCompletionState.READING.storageValue,
            payload = "keep_payload",
            revision = 3,
            current_location_json = LegadoProgressMarker.encode(5, 50),
            updated_at = "2026-09-01T00:00:00Z",
        )
        coEvery { bookDao.getAllActive() } returns listOf(text)
        coEvery { progressDao.getByBooks(listOf("text-reading")) } returns listOf(existing)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val archive = zipOf(
            """
            [
              {"name":"中段阅读书","author":"作者","durChapterIndex":50,"durChapterPos":80,"totalChapterNum":100}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        assertEquals(1, report.imported)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals(50f, saved.captured.progress_percent, 0.001f)
        assertEquals("reading", saved.captured.completion_state)
        assertEquals(null, saved.captured.completed_at)
        assertEquals("keep_payload", saved.captured.payload)
        assertEquals(3, saved.captured.revision)
        assertEquals(
            ShelfStatusFilter.READING,
            bookStatus(text, saved.captured.progress_percent, saved.captured.completion_state),
        )
    }

    @Test
    fun `duplicate or backwards imported progress is skipped as unchanged`() = runTest {
        val text = book(id = "text-dup", title = "重复导入书", author = "作者", format = "txt")
        val existing = ReadingProgressEntity(
            book_id = "text-dup",
            progress_percent = 50f,
            completion_state = ReadingCompletionState.READING.storageValue,
            current_location_json = LegadoProgressMarker.encode(50, 100),
            updated_at = "2026-09-01T00:00:00Z",
        )
        coEvery { bookDao.getAllActive() } returns listOf(text)
        coEvery { progressDao.getByBooks(listOf("text-dup")) } returns listOf(existing)
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        // 导入相同的位置 (50, 100)
        val archiveEqual = zipOf(
            """
            [
              {"name":"重复导入书","author":"作者","durChapterIndex":50,"durChapterPos":100,"totalChapterNum":100}
            ]
            """.trimIndent(),
        )
        val reportEqual = importer.importArchive(archiveEqual)
        assertEquals(0, reportEqual.imported)
        assertEquals(1, reportEqual.unchanged)

        // 导入落后的位置 (49, 200)
        val archiveBack = zipOf(
            """
            [
              {"name":"重复导入书","author":"作者","durChapterIndex":49,"durChapterPos":200,"totalChapterNum":100}
            ]
            """.trimIndent(),
        )
        val reportBack = importer.importArchive(archiveBack)
        assertEquals(0, reportBack.imported)
        assertEquals(1, reportBack.unchanged)

        coVerify(exactly = 0) { progressDao.upsert(any()) }
    }

    @Test
    fun `approximate percent is safely capped at MAX_IMPORTED_PERCENT even if chapterIndex exceeds totalChapters`() = runTest {
        val epub = book(id = "epub-overflow", title = "溢出测试书", author = "作者", format = "epub")
        coEvery { bookDao.getAllActive() } returns listOf(epub)
        coEvery { progressDao.getByBooks(listOf("epub-overflow")) } returns emptyList()
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        // 远端数据异常：章索引 150，总章数 100
        val archive = zipOf(
            """
            [
              {"name":"溢出测试书","author":"作者","durChapterIndex":150,"durChapterPos":0,"totalChapterNum":100}
            ]
            """.trimIndent(),
        )

        val report = importer.importArchive(archive)

        assertEquals(1, report.imported)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals(LegadoReadingProgressImporter.MAX_IMPORTED_PERCENT, saved.captured.progress_percent, 0.001f)
        assertEquals("reading", saved.captured.completion_state)
        assertEquals(null, saved.captured.completed_at)
        assertEquals(
            ShelfStatusFilter.READING,
            bookStatus(epub, saved.captured.progress_percent, saved.captured.completion_state),
        )
    }

    private fun book(id: String, title: String, author: String, format: String = "txt") = BookEntity(
        id = id,
        title = title,
        author = author,
        format = format,
        updated_at = "2026-09-01T00:00:00Z",
    )

    private fun zipOf(json: String, entryName: String = "bookshelf.json"): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun io.mockk.MockKAnswerScope<Unit, Unit>.transactionBlock(): suspend () -> Unit =
        invocation.args[0] as suspend () -> Unit
}
