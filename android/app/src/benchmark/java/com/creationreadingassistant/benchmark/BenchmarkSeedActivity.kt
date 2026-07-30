package com.creationreadingassistant.benchmark

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Benchmark-build-only corpus generator.
 *
 * It lives outside main/debug source sets and the benchmark target has its own application id, so
 * the 200+ synthetic records can never enter the user's day-to-day library.
 */
@AndroidEntryPoint
class BenchmarkSeedActivity : ComponentActivity() {
    @Inject lateinit var bookDao: BookDao
    @Inject lateinit var bookContentDao: BookContentDao
    @Inject lateinit var bookFileDao: BookFileDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                seedLibrary(
                    requestedCount = intent.getIntExtra(EXTRA_BOOK_COUNT, DEFAULT_BOOK_COUNT)
                        .coerceIn(200, 500),
                )
            }
            finish()
        }
    }

    private suspend fun seedLibrary(requestedCount: Int) {
        val corpusDir = File(filesDir, "benchmark-corpus").also { it.mkdirs() }
        val txtFile = File(corpusDir, "test.txt")
        if (!txtFile.isFile || txtFile.length() < 1_000_000L) {
            txtFile.bufferedWriter().use { writer ->
                repeat(360) { chapter ->
                    writer.appendLine("第 ${chapter + 1} 章")
                    repeat(36) {
                        writer.appendLine("这是一段用于验证长文本打开、翻页、搜索与恢复位置的中性测试正文。")
                    }
                }
            }
        }
        val epubFile = File(corpusDir, "test.epub")
        if (!epubFile.isFile || epubFile.length() == 0L) createBenchmarkEpub(epubFile)

        val generated = (0 until requestedCount).map { index ->
            val suffix = (index + 1).toString().padStart(3, '0')
            BookEntity(
                id = "benchmark-library-$suffix",
                title = if (index % 4 == 0) {
                    "压力书架长标题样本 $suffix：用于验证多行标题、排序和快速滚动时卡片仍然对齐"
                } else {
                    "压力书架样本 $suffix"
                },
                author = "测试作者 ${(index % 17) + 1}",
                format = if (index % 9 == 0) "epub" else "txt",
                original_file_name = "sample-$suffix.${if (index % 9 == 0) "epub" else "txt"}",
                size = 100_000 + index * 97,
                content_status = "missing",
                cover_data_url = if (index % 3 == 0) coverDataUrl(index) else null,
                imported_at = "2026-01-${((index % 28) + 1).toString().padStart(2, '0')}T00:00:00Z",
                updated_at = "2026-01-${((index % 28) + 1).toString().padStart(2, '0')}T00:00:00Z",
            )
        }
        bookDao.upsertAll(generated)

        seedReadableBook(
            id = TEST_TXT_ID,
            title = "测试 TXT",
            format = "txt",
            file = txtFile,
            updatedAt = "1999-01-01T00:00:00Z",
            preview = txtFile.bufferedReader().use { it.readText().take(20_000) },
        )
        seedReadableBook(
            id = TEST_EPUB_ID,
            title = "测试 EPUB",
            format = "epub",
            file = epubFile,
            updatedAt = "2999-01-01T00:00:00Z",
            preview = null,
        )
    }

    private suspend fun seedReadableBook(
        id: String,
        title: String,
        format: String,
        file: File,
        updatedAt: String,
        preview: String?,
    ) {
        val uri = Uri.fromFile(file).toString()
        val size = file.length().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        bookDao.upsert(
            BookEntity(
                id = id,
                title = title,
                author = "性能测试",
                format = format,
                original_file_name = file.name,
                size = size,
                local_uri = uri,
                local_content_path = file.absolutePath,
                content_status = "available",
                imported_at = updatedAt,
                updated_at = updatedAt,
            )
        )
        bookContentDao.upsert(BookContentEntity(book_id = id, reader_preview = preview))
        bookFileDao.upsert(
            BookFileEntity(
                book_id = id,
                file_name = file.name,
                format = format,
                size = size,
                local_uri = uri,
                updated_at = updatedAt,
            )
        )
    }

    private fun coverDataUrl(index: Int): String {
        val hue = (index * 37) % 360
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="120" height="168">
              <rect width="120" height="168" fill="hsl($hue,38%,46%)"/>
              <path d="M18 26h84M18 42h64M18 136h84" stroke="white" stroke-opacity=".72"/>
            </svg>
        """.trimIndent()
        return "data:image/svg+xml;utf8," +
            URLEncoder.encode(svg, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    private fun createBenchmarkEpub(output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.writeEntry("mimetype", "application/epub+zip")
            zip.writeEntry(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""".trimIndent(),
            )
            val manifest = (1..8).joinToString("") {
                """<item id="c$it" href="chapter$it.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spine = (1..8).joinToString("") { """<itemref idref="c$it"/>""" }
            zip.writeEntry(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="id">benchmark-epub</dc:identifier>
                    <dc:title>测试 EPUB</dc:title>
                    <dc:creator>性能测试</dc:creator>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>$manifest</manifest>
                  <spine>$spine</spine>
                </package>""".trimIndent(),
            )
            repeat(8) { chapter ->
                val paragraphs = (1..80).joinToString("") {
                    "<p>这是第 ${chapter + 1} 章的中性测试正文，用于验证 EPUB 打开、翻页和跳章性能。</p>"
                }
                zip.writeEntry(
                    "OEBPS/chapter${chapter + 1}.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第 ${chapter + 1} 章</title></head>
                    <body><h1>第 ${chapter + 1} 章</h1>$paragraphs</body></html>""".trimIndent(),
                )
            }
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    companion object {
        const val EXTRA_BOOK_COUNT = "bookCount"
        const val DEFAULT_BOOK_COUNT = 200
        const val TEST_TXT_ID = "benchmark-test-txt"
        const val TEST_EPUB_ID = "benchmark-test-epub"
    }
}
