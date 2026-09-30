package com.creationreadingassistant.feature.sync

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S12-① EPUB locator 单一写入契约（导入侧）。
 *
 * 契约：locator v2 的 "offset" 是全书字符偏移，导入时拿不到章起点表，必须缺省，
 * 由读侧按 `chapterStart[ci] + co` 派生；绝不以章内偏移冒充全局。
 * 本文件钉住：导入产物的字段形态（新契约）、旧导入格式（伪造全局）在
 * shouldApply 比较里的兼容行为、以及重复导入的等值不覆盖语义。
 */
class LegadoImportEpubLocatorContractTest {

    private val bookDao = mockk<BookDao>()
    private val progressDao = mockk<ReadingProgressDao>()
    private val importer = LegadoReadingProgressImporter(bookDao, progressDao)

    @Test
    fun `epub import stores chapter tuple without fabricating a global offset`() = runTest {
        val epub = book(id = "epub-1", title = "测试EPUB", author = "作者", format = "epub")
        coEvery { bookDao.getAllActive() } returns listOf(epub)
        coEvery { progressDao.getByBooks(listOf("epub-1")) } returns emptyList()
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        val report = importer.importArchive(
            zipOf(
                """[{"name":"测试EPUB","author":"作者","durChapterIndex":8,"durChapterPos":321,"totalChapterNum":20}]""",
            ),
        )

        assertEquals(1, report.imported)
        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }

        // 单一契约产物：v2 只有章元组（ci/co），没有伪造的全书 offset；
        // 顶层 legacy 镜像（chapter/offset，章内口径）保留给旧解析器。
        assertEquals(
            """{"chapter":8,"offset":321,"locator_v2":{"v":2,"ci":8,"co":321}}""",
            saved.captured.current_location_json,
        )
        val locator = LocatorCodec.locatorFromProgress(saved.captured.current_location_json)
        assertEquals(8, locator?.chapterIndex)
        assertEquals(321, locator?.charOffset)
        assertNull("导入 locator 不得伪造全书偏移", locator?.legacyOffset)
    }

    @Test
    fun `re-import compares legacy-format local locator by chapter tuple`() = runTest {
        // 旧导入格式（v2.offset 为伪造的章内值）仍是库里的存量数据：
        // shouldApply 必须继续按 ci/co 解析并比较，不受量纲修复影响。
        val epub = book(id = "epub-1", title = "测试EPUB", author = "作者", format = "epub")
        coEvery { bookDao.getAllActive() } returns listOf(epub)
        coEvery { progressDao.getByBooks(listOf("epub-1")) } returns listOf(
            ReadingProgressEntity(
                book_id = "epub-1",
                current_location_json =
                    """{"chapter":8,"offset":321,"locator_v2":{"v":2,"offset":321,"ci":8,"co":321}}""",
                updated_at = "2026-09-01T00:00:00Z",
            ),
        )
        coEvery { bookDao.runInTransaction(any()) } coAnswers { transactionBlock().invoke() }
        coJustRun { progressDao.upsert(any()) }

        // 远端更靠后 → 覆盖，且写入的是新契约（无伪造全局）。
        val ahead = importer.importArchive(
            zipOf("""[{"name":"测试EPUB","author":"作者","durChapterIndex":9,"durChapterPos":10}]"""),
        )
        assertEquals(1, ahead.imported)
        val healed = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(healed)) }
        assertEquals(
            """{"chapter":9,"offset":10,"locator_v2":{"v":2,"ci":9,"co":10}}""",
            healed.captured.current_location_json,
        )

        // 远端更靠前 → 等值/落后均不覆盖（重复导入幂等）。
        val behind = importer.importArchive(
            zipOf("""[{"name":"测试EPUB","author":"作者","durChapterIndex":2,"durChapterPos":1}]"""),
        )
        assertEquals(0, behind.imported)
        assertEquals(1, behind.unchanged)
        val equal = importer.importArchive(
            zipOf("""[{"name":"测试EPUB","author":"作者","durChapterIndex":8,"durChapterPos":321}]"""),
        )
        assertEquals(0, equal.imported)
        assertEquals(1, equal.unchanged)
        coVerify(exactly = 1) { progressDao.upsert(any()) }
    }

    private fun book(id: String, title: String, author: String, format: String) = BookEntity(
        id = id,
        title = title,
        author = author,
        format = format,
        updated_at = "2026-09-01T00:00:00Z",
    )

    private fun zipOf(json: String): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("bookshelf.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun io.mockk.MockKAnswerScope<Unit, Unit>.transactionBlock(): suspend () -> Unit =
        invocation.args[0] as suspend () -> Unit
}
