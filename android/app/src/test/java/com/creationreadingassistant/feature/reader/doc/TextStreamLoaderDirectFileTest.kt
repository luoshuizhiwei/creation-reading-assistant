package com.creationreadingassistant.feature.reader.doc

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStreamLoaderDirectFileTest {
    @Test
    fun `small direct file is decoded without owned temp copy`() {
        val root = Files.createTempDirectory("text-loader-direct-").toFile()
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
        val root = Files.createTempDirectory("text-loader-direct-").toFile()
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

    @Test
    fun `large direct file exposes backing source that is not owned`() {
        val root = Files.createTempDirectory("text-loader-direct-").toFile()
        try {
            val source = File(root, "book.txt").apply {
                writeText(buildString { repeat(300) { append("正文内容\n") } })
            }
            val result = TextStreamLoader(root, streamingThresholdBytes = 64)
                .loadDirectFile(source)

            assertTrue(result.isStreaming)
            assertNull("直接源文件不是拥有的临时文件", result.tempFile)
            assertEquals("可直接重扫的 backing source 必须是原文件", source, result.sourceFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `small direct file does not retain a rescan source`() {
        val root = Files.createTempDirectory("text-loader-direct-").toFile()
        try {
            val source = File(root, "book.txt").apply { writeText("第一章\n正文") }
            val result = TextStreamLoader(root, streamingThresholdBytes = 1_024)
                .loadDirectFile(source)

            assertFalse(result.isStreaming)
            assertNull("小文件无需保留可重扫 source", result.sourceFile)
            assertNull(result.tempFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
