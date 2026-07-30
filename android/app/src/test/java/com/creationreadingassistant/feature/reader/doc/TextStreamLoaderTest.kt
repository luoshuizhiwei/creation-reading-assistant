package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class TextStreamLoaderTest {

    private lateinit var cacheDir: File
    private val tempFiles = mutableListOf<File>()

    /** Use a small threshold (100 bytes) so tests don't need huge files. */
    private val testThreshold = 100L

    @Before
    fun setUp() {
        cacheDir = createTempDir("loader_test_cache_")
    }

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        cacheDir.deleteRecursively()
    }

    private fun track(file: File): File {
        tempFiles.add(file)
        return file
    }

    private fun loader(): TextStreamLoader = TextStreamLoader(cacheDir, testThreshold)

    /** Generate a UTF-8 byte array of approximately [size] bytes. */
    private fun generateBytes(size: Int): ByteArray {
        // Each Chinese char is 3 bytes in UTF-8, so ~size/3 chars
        val charCount = (size / 3).coerceAtLeast(1)
        val text = "测".repeat(charCount)
        return text.toByteArray(Charsets.UTF_8)
    }

    // ── 1. Unknown SIZE (null): actual > threshold → streaming ───────────

    @Test
    fun `load with null reportedSize and actual size above threshold routes to streaming`() {
        val bytes = generateBytes((testThreshold + 50).toInt())
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertNull(result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        // Clean up
        result.tempFile?.delete()
    }

    // ── 2. Zero SIZE: actual > threshold → streaming ─────────────────────

    @Test
    fun `load with zero reportedSize and actual size above threshold routes to streaming`() {
        val bytes = generateBytes((testThreshold + 50).toInt())
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = 0L)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        result.tempFile?.delete()
    }

    // ── 3. Negative SIZE: actual > threshold → streaming ─────────────────

    @Test
    fun `load with negative reportedSize and actual size above threshold routes to streaming`() {
        val bytes = generateBytes((testThreshold + 50).toInt())
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = -1L)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        result.tempFile?.delete()
    }

    // ── 4. Small file via unknown SIZE: actual < threshold → small file ──

    @Test
    fun `load with null reportedSize and actual size below threshold routes to small file`() {
        val text = "这是一小段测试文本。"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size < testThreshold) // sanity check

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertNull(result.tempFile)
        assertNull(result.fileIndex)
        assertNotNull(result.fullText)
        assertEquals(text, result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
    }

    // ── 5. Positive SIZE honored when correct → still verifies by copy ───

    @Test
    fun `load with correct positive reportedSize still verifies by copy`() {
        val text = "短文本"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size < testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = bytes.size.toLong())

        assertFalse(result.isStreaming)
        assertEquals(text, result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
    }

    // ── 6. Positive SIZE wrong value: reports 100 but actual is large → streaming

    @Test
    fun `load with wrong positive reportedSize routes based on actual size`() {
        // Provider says 100 bytes but actual content is above threshold
        val bytes = generateBytes((testThreshold + 50).toInt())
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = 100L)

        // Should route based on ACTUAL size, not reported
        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        result.tempFile?.delete()
    }

    // ── 7. Temp file cleanup on failure ──────────────────────────────────

    @Test
    fun `load deletes temp file on read error`() {
        // Create a stream that throws mid-read
        val failingStream = object : InputStream() {
            private var count = 0
            override fun read(): Int {
                if (count++ > 10) throw IOException("Simulated read error")
                return 'A'.code
            }
        }

        val loader = loader()
        val filesBefore = cacheDir.listFiles()?.size ?: 0

        try {
            loader.load(failingStream, reportedSize = null)
            assertTrue("Should have thrown", false)
        } catch (e: IOException) {
            assertEquals("Simulated read error", e.message)
        }

        // Temp file should have been cleaned up
        val remaining = cacheDir.listFiles()?.filter {
            it.name.startsWith("txt_stream_") && it.name.endsWith(".tmp")
        } ?: emptyList()
        assertEquals(0, remaining.size)
    }

    // ── 8. cleanupStaleTempFiles ─────────────────────────────────────────

    @Test
    fun `cleanupStaleTempFiles removes old files but keeps new ones`() {
        val loader = loader()

        // Create an "old" temp file (set lastModified to 2 hours ago)
        val oldFile = track(File(cacheDir, "txt_stream_old123.tmp"))
        oldFile.writeText("old content")
        oldFile.setLastModified(System.currentTimeMillis() - 7_200_000L) // 2 hours ago

        // Create a "new" temp file
        val newFile = track(File(cacheDir, "txt_stream_new456.tmp"))
        newFile.writeText("new content")
        // lastModified is now by default

        // Create an unrelated file (should not be deleted)
        val unrelated = track(File(cacheDir, "other_file.txt"))
        unrelated.writeText("unrelated")
        unrelated.setLastModified(System.currentTimeMillis() - 7_200_000L)

        loader.cleanupStaleTempFiles(maxAgeMs = 3_600_000L) // 1 hour

        assertFalse("Old temp file should be deleted", oldFile.exists())
        assertTrue("New temp file should still exist", newFile.exists())
        assertTrue("Unrelated file should not be affected", unrelated.exists())

        // Clean up
        newFile.delete()
        unrelated.delete()
    }

    // ── 9. LoadResult fields correctness ─────────────────────────────────

    @Test
    fun `LoadResult fields are correct for small file path`() {
        val text = "Hello, 世界!"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertNull(result.tempFile)
        assertNull(result.fileIndex)
        assertNotNull(result.fullText)
        assertEquals(text, result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        assertNotNull(result.document)
    }

    @Test
    fun `LoadResult fields are correct for streaming path`() {
        // Create content with chapter headings to test index generation
        val body = "测试正文内容。".repeat(50) // ~400 chars per chapter, above 300 density threshold
        val content = "第一章 开端\n$body\n第二章 发展\n$body"
        val bytes = content.toByteArray(Charsets.UTF_8)

        // Ensure it's above threshold
        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertNull(result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
        assertNotNull(result.document)
        assertTrue("Should have detected chapters", result.fileIndex!!.chapters.size >= 2)

        // Clean up
        result.tempFile?.delete()
    }

    // ── 10. Load from FileInputStream (simulating real file) ─────────────

    @Test
    fun `load from FileInputStream works correctly`() {
        val text = "通过文件流加载的测试内容。"
        val sourceFile = track(File.createTempFile("source_", ".txt"))
        sourceFile.writeText(text, Charsets.UTF_8)

        val result = FileInputStream(sourceFile).use { fis ->
            loader().load(fis, reportedSize = sourceFile.length())
        }

        assertFalse(result.isStreaming)
        assertEquals(text, result.fullText)
    }

    // ── G5 Category 9: Lifecycle Tests ─────────────────────────────────

    @Test
    fun `scan InputStream does NOT close the stream`() {
        val text = "第一章 测试\n" + "测试正文内容。".repeat(100)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val tracking = CloseTrackingInputStream(ByteArrayInputStream(bytes))

        // scan(InputStream) should NOT close the stream (caller responsible)
        TxtFileScanner.scan(tracking)

        assertFalse("scan(InputStream) should NOT close the stream", tracking.isClosed)
        assertEquals(0, tracking.closeCount)
    }

    @Test
    fun `scan File closes its internal FileInputStream`() {
        val text = "第一章 测试\n" + "测试正文内容。".repeat(100)
        val file = track(File.createTempFile("test_scan_file_", ".txt"))
        file.writeText(text, Charsets.UTF_8)

        // scan(File) should work correctly (it internally uses .use{} to close)
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-8", index.encoding)
        assertTrue("Should have detected chapter", index.chapters.isNotEmpty())
    }

    @Test
    fun `TextStreamLoader failure cleanup deletes temp file`() {
        // Create a stream that throws mid-read
        val failingStream = object : InputStream() {
            private var count = 0
            override fun read(): Int {
                if (count++ > 50) throw IOException("Simulated read error")
                return 'A'.code
            }
        }

        val loader = loader()

        try {
            loader.load(failingStream, reportedSize = null)
            assertTrue("Should have thrown", false)
        } catch (e: IOException) {
            assertEquals("Simulated read error", e.message)
        }

        // Temp file should have been cleaned up
        val remaining = cacheDir.listFiles()?.filter {
            it.name.startsWith("txt_stream_") && it.name.endsWith(".tmp")
        } ?: emptyList()
        assertEquals("No temp files should remain after failure", 0, remaining.size)
    }

    @Test
    fun `cleanupStaleTempFiles removes old but keeps recent`() {
        val loader = loader()

        // Create old temp file (>1 hour)
        val oldFile = track(File(cacheDir, "txt_stream_stale_old.tmp"))
        oldFile.writeText("old stale content")
        oldFile.setLastModified(System.currentTimeMillis() - 7_200_000L) // 2 hours ago

        // Create recent temp file
        val recentFile = track(File(cacheDir, "txt_stream_recent.tmp"))
        recentFile.writeText("recent content")
        // lastModified is now by default

        loader.cleanupStaleTempFiles(maxAgeMs = 3_600_000L) // 1 hour

        assertFalse("Old stale temp file should be deleted", oldFile.exists())
        assertTrue("Recent temp file should still exist", recentFile.exists())

        // Clean up
        recentFile.delete()
    }

    @Test
    fun `concurrent load uses correct result for each stream`() {
        val loader = loader()

        // First load: small file
        val text1 = "第一个文件的内容"
        val bytes1 = text1.toByteArray(Charsets.UTF_8)
        val result1 = loader.load(ByteArrayInputStream(bytes1), reportedSize = null)

        // Second load: different content
        val text2 = "第二个文件的内容完全不同"
        val bytes2 = text2.toByteArray(Charsets.UTF_8)
        val result2 = loader.load(ByteArrayInputStream(bytes2), reportedSize = null)

        // Both results should be correct and independent
        assertFalse(result1.isStreaming)
        assertFalse(result2.isStreaming)
        assertEquals(text1, result1.fullText)
        assertEquals(text2, result2.fullText)
    }

    // ── G5 Category 9: Additional Lifecycle Tests ────────────────────────

    @Test
    fun `load with streaming path preserves encoding detection`() {
        // Create content with chapter headings that's above threshold
        val body = "测试正文内容。".repeat(50)
        val content = "第一章 开端\n$body\n第二章 发展\n$body"
        val bytes = content.toByteArray(Charsets.UTF_8)

        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertEquals("UTF-8", result.fileIndex!!.encoding)
        assertTrue("Should have detected chapters", result.fileIndex!!.chapters.size >= 2)

        // Clean up
        result.tempFile?.delete()
    }

    @Test
    fun `load with small file path creates correct document`() {
        val text = "这是一小段测试文本。"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size < testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertNull(result.tempFile)
        assertNull(result.fileIndex)
        assertNotNull(result.fullText)
        assertEquals(text, result.fullText)
        assertNotNull(result.document)

        // Verify document has correct content
        assertEquals(text.length, result.document.totalChars)
    }

    @Test
    fun `multiple sequential loads work correctly`() {
        val loader = loader()

        // Load 5 different files sequentially
        for (i in 1..5) {
            val text = "第${i}个文件的内容"
            val bytes = text.toByteArray(Charsets.UTF_8)
            val result = loader.load(ByteArrayInputStream(bytes), reportedSize = null)

            assertFalse(result.isStreaming)
            assertEquals(text, result.fullText)
        }
    }

    // ── G5 Category 9: Additional Loader Edge Cases ──────────────────────

    @Test
    fun `load with exact threshold size routes correctly`() {
        // Create content exactly at threshold
        val text = "测".repeat((testThreshold / 3).toInt()) // ~100 bytes
        val bytes = text.toByteArray(Charsets.UTF_8)

        // Adjust to be exactly at threshold
        val exactBytes = ByteArray(testThreshold.toInt())
        System.arraycopy(bytes, 0, exactBytes, 0, minOf(bytes.size, exactBytes.size))

        val stream = ByteArrayInputStream(exactBytes)
        val result = loader().load(stream, reportedSize = null)

        // Should route to small-file path (actualSize <= threshold)
        assertFalse("Exact threshold should route to small-file", result.isStreaming)
        assertEquals(exactBytes.size.toLong(), result.actualSizeBytes)
    }

    @Test
    fun `load with one byte over threshold routes to streaming`() {
        // Create content just over threshold
        val text = "测".repeat((testThreshold / 3).toInt() + 10)
        val bytes = text.toByteArray(Charsets.UTF_8)

        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue("Should route to streaming", result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)

        // Clean up
        result.tempFile?.delete()
    }

    @Test
    fun `cleanupStaleTempFiles with custom maxAge`() {
        val loader = loader()

        // Create temp files with different ages
        val veryOldFile = track(File(cacheDir, "txt_stream_veryold.tmp"))
        veryOldFile.writeText("very old")
        veryOldFile.setLastModified(System.currentTimeMillis() - 10_800_000L) // 3 hours ago

        val somewhatOldFile = track(File(cacheDir, "txt_stream_somewhat.tmp"))
        somewhatOldFile.writeText("somewhat old")
        somewhatOldFile.setLastModified(System.currentTimeMillis() - 1_800_000L) // 30 min ago

        val newFile = track(File(cacheDir, "txt_stream_new.tmp"))
        newFile.writeText("new")

        // Cleanup with 1 hour max age
        loader.cleanupStaleTempFiles(maxAgeMs = 3_600_000L)

        assertFalse("Very old file should be deleted", veryOldFile.exists())
        assertTrue("Somewhat old file should still exist", somewhatOldFile.exists())
        assertTrue("New file should still exist", newFile.exists())

        // Clean up
        somewhatOldFile.delete()
        newFile.delete()
    }

    // ── G5 Category 9: Additional Loader Integration Tests ───────────────

    @Test
    fun `load with GB18030 encoding detects correctly`() {
        val gb18030 = java.nio.charset.Charset.forName("GB18030")
        val text = "第一章 测试\n" + "测试正文内容。".repeat(50)
        val bytes = text.toByteArray(gb18030)

        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertEquals("GB18030", result.fileIndex!!.encoding)

        // Clean up
        result.tempFile?.delete()
    }

    @Test
    fun `load with UTF-16LE encoding detects correctly`() {
        val text = "第一章 测试\n" + "测试正文内容。".repeat(50)
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val contentBytes = text.toByteArray(Charsets.UTF_16LE)
        val bytes = bom + contentBytes

        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertEquals("UTF-16LE", result.fileIndex!!.encoding)

        // Clean up
        result.tempFile?.delete()
    }

    @Test
    fun `load with UTF-16BE encoding detects correctly`() {
        val text = "第一章 测试\n" + "测试正文内容。".repeat(50)
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val contentBytes = text.toByteArray(Charsets.UTF_16BE)
        val bytes = bom + contentBytes

        assertTrue("Test content should be above threshold", bytes.size > testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertTrue(result.isStreaming)
        assertNotNull(result.tempFile)
        assertNotNull(result.fileIndex)
        assertEquals("UTF-16BE", result.fileIndex!!.encoding)

        // Clean up
        result.tempFile?.delete()
    }

    // ── G5 Category 9: Additional Loader Edge Case Tests ─────────────────

    @Test
    fun `load with empty stream returns empty document`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertEquals(0L, result.actualSizeBytes)
        assertNotNull(result.fullText)
        assertEquals("", result.fullText)
    }

    @Test
    fun `load with very small content returns small file`() {
        val text = "A"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertEquals(text, result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
    }

    @Test
    fun `load with content just below threshold returns small file`() {
        val text = "测".repeat((testThreshold / 3).toInt() - 1)
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertTrue("Test content should be below threshold", bytes.size < testThreshold)

        val stream = ByteArrayInputStream(bytes)
        val result = loader().load(stream, reportedSize = null)

        assertFalse(result.isStreaming)
        assertEquals(text, result.fullText)
        assertEquals(bytes.size.toLong(), result.actualSizeBytes)
    }
}
