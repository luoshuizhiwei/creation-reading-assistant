package com.creationreadingassistant.feature.reader.doc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStreamLoaderDirectFileTest {
    @Test
    fun `small direct file is decoded without owned temp copy`() {
        val root = createTempDir(prefix = "text-loader-direct-")
        try {
            val source = File(root, "book.txt").apply { writeText("第一章\n正文") }
            val result = TextStreamLoader(root, streamingThresholdBytes = 1_024)
                .loadDirectFile(source)

            assertFalse(result.isStreaming)
            assertNull(result.tempFile)
            assertEquals("第一章\n正文", result.fullText)
            assertTrue(source.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `large direct file streams from source without deleting it`() {
        val root = createTempDir(prefix = "text-loader-direct-")
        try {
            val source = File(root, "book.txt").apply {
                writeText(buildString { repeat(300) { append("正文内容\n") } })
            }
            val result = TextStreamLoader(root, streamingThresholdBytes = 64)
                .loadDirectFile(source)

            assertTrue(result.isStreaming)
            assertNull(result.tempFile)
            assertTrue(result.document.totalChars > 0)
            assertTrue(source.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
