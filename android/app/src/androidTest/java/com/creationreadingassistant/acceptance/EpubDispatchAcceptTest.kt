package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1 —— 任务24：EPUB 保存派发链**设备**回归。
 *
 * 16-v6 的新保存派发链（`ReaderProgressSaveOrder`）同时改写了 TXT 与 EPUB 的落库入口
 * （`EpubRepository.saveProgress` 显式带事件序号）。本测试在**真实 Android/Room 环境**下
 * 走真实 `EpubRepository`（真实 ZIP 解析 + 真实 Room 写入），验证：
 *  · 迟到的旧事件不得覆盖其后更精确的新位置（新派发链核心合同）；
 *  · 同序章切换落库（粗位置）不得覆盖其后的精确位置保存；
 *  · 真实 EPUB 解析可打开（正文就绪）。
 *
 * 说明（边界，如实标注）：这是**生产入口的保存派发链设备验证**，不是完整阅读器 UI 前进；
 * EPUB 阅读器 UI 前进/退出的完整链路本批未覆盖（见 REPORT 未覆盖项）。
 */
@RunWith(AndroidJUnit4::class)
class EpubDispatchAcceptTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: EpubRepository
    private lateinit var bookId: String
    private lateinit var epubFile: File

    private companion object {
        const val TASK_ID = "CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1"
        const val MARKER = "EPUB24_BODY_UNIQUE_MARKER"
    }

    @Before
    fun setUp() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries().build()
        repo = EpubRepository(
            context = context,
            bookDao = db.bookDao(),
            readingProgressDao = db.readingProgressDao(),
            ioDispatcher = Dispatchers.IO,
            fileOps = PassthroughFileOps(),
        )
        bookId = "test-t24-epub-" + UUID.randomUUID().toString().take(8)
        val epubDir = File(context.filesDir, "books/epub").apply { mkdirs() }
        epubFile = File(epubDir, "$bookId.epub")
        writeMinimalEpub(epubFile, "测试 EPUB 甲", "$MARKER " + "正文内容甲乙丙丁。".repeat(120))
    }

    @After
    fun tearDown() {
        runCatching {
            db.openHelper.writableDatabase.apply {
                listOf("highlights", "notes", "reading_sessions").forEach {
                    execSQL("DELETE FROM $it WHERE book_id = ?", arrayOf(bookId))
                }
                listOf("book_files", "book_content", "reading_progress").forEach {
                    execSQL("DELETE FROM $it WHERE book_id = ?", arrayOf(bookId))
                }
                execSQL("DELETE FROM books WHERE id = ?", arrayOf(bookId))
            }
        }
        File(context.filesDir, "books/epub/$bookId.epub").delete()
        File(context.filesDir, "books/epub/$bookId").deleteRecursively()
        db.close()
    }

    private fun seedBook(): BookEntity {
        val now = Instant.now().toString()
        val entity = BookEntity(
            id = bookId, title = "测试 EPUB 甲", author = "测试作者",
            format = "epub", size = epubFile.length().toInt(),
            local_content_path = epubFile.absolutePath,
            content_status = "available", imported_at = now, updated_at = now,
        )
        runBlocking { db.bookDao().upsert(entity) }
        return entity
    }

    @Test
    fun regression_epubSaveProgressDispatchOrder() = runBlocking {
        val stored = seedBook()

        // 真实 EPUB 解析（真实 ZIP → 章节正文）
        val book = repo.openStoredEpub(stored)
        assertTrue("真实 EPUB 解析必须产出章节", book.chapters.isNotEmpty())

        // 新派发链：先章切换粗位置（order=1），再精确位置（order=3），最后迟到旧事件（order=2）
        repo.saveProgress(bookId, chapterIndex = 1, percent = 20f, offsetInChapter = 500, absoluteOffset = 1500, order = 1L)
        repo.saveProgress(bookId, chapterIndex = 2, percent = 45f, offsetInChapter = 800, absoluteOffset = 3200, order = 3L)
        repo.saveProgress(bookId, chapterIndex = 1, percent = 20f, offsetInChapter = 500, absoluteOffset = 1500, order = 2L)

        val offset = repo.loadProgressOffset(bookId)
        val chapter = repo.loadProgress(bookId)

        writeEvidence("regression_epubSaveProgressDispatchOrder", JSONObject().apply {
            put("bookId", bookId)
            put("chapterCount", book.chapters.size)
            put("finalChapterOffset", offset)
            put("finalChapter", chapter)
            put("note", "真实 EpubRepository.saveProgress 派发链：迟到旧事件(order=2, ci=1/off=500)不得覆盖其后更新事件(order=3, ci=2/off=800)")
        })

        assertEquals("迟到的旧事件不得覆盖其后更新的章内位置", 800, offset)
        assertEquals("章号必须是最新事件的章号", 2, chapter)
    }

    // ── 夹具 ──────────────────────────────────────────────────────────────────

    private fun writeMinimalEpub(dest: File, title: String, body: String) {
        dest.parentFile!!.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            fun entry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name)); zos.write(content.toByteArray(Charsets.UTF_8)); zos.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", CONTAINER)
            entry("OPS/package.opf", opf(title))
            entry("OPS/text/ch1.xhtml", chapter(body))
        }
    }

    private fun opf(title: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title><dc:identifier id="uid">t24</dc:identifier>
          </metadata>
          <manifest><item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="c1"/></spine>
        </package>
    """.trimIndent()

    private fun chapter(body: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>章</title></head>
        <body><p>$body</p></body></html>
    """.trimIndent()

    private class PassthroughFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) { source.copyTo(dest, overwrite = true) }
    }

    private fun writeEvidence(step: String, payload: JSONObject) {
        val dir = File(context.getExternalFilesDir(null), "j1_guard_evidence").apply { mkdirs() }
        val runId = "run_epub_" + android.os.Process.myPid() + "_" + (System.currentTimeMillis() % 100000)
        val file = File(dir, "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID); put("runId", runId); put("step", step)
            put("pid", android.os.Process.myPid()); put("packageName", context.packageName)
            put("timestamp", Instant.now().toString()); put("timestampMillis", System.currentTimeMillis())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
    }
}

private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
