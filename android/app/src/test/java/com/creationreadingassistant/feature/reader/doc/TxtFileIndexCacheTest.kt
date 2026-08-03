package com.creationreadingassistant.feature.reader.doc

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtFileIndexCacheTest {
    @Test
    fun `reuses valid index and invalidates when source changes`() {
        val root = Files.createTempDirectory("txt-index-cache-").toFile()
        try {
            val source = File(root, "source.txt").apply {
                writeText("第一章\n正文\n第二章\n更多正文")
            }
            val cache = TxtFileIndexCache(File(root, "cache"))
            val first = cache.getOrBuild(source)
            val second = cache.getOrBuild(source)

            assertEquals(first, second)
            assertTrue(File(root, "cache/txt_index_v1").listFiles().orEmpty().isNotEmpty())

            source.appendText("\n第三章\n新增正文")
            source.setLastModified(System.currentTimeMillis() + 2_000)
            val changed = cache.getOrBuild(source)
            assertNotEquals(first.totalCharCount, changed.totalCharCount)
        } finally {
            root.deleteRecursively()
        }
    }
}
