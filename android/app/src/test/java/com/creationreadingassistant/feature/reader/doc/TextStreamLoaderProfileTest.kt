package com.creationreadingassistant.feature.reader.doc

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-A：TextStreamLoader 必须把 TxtTocProfile 实际用于小文件目录构建与大文件
 * 流式扫描——小文件文档按 profile 模式识别，流式索引的 detectedRuleId 落 profile.key。
 */
class TextStreamLoaderProfileTest {

    private lateinit var cacheDir: File
    private val testThreshold = 256L

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("loader_profile_cache_").toFile()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun profile() = TxtTocProfile(
        key = "custom-seq",
        patterns = listOf(Regex("^序\\d+ .+$")),
        densityGuard = false,
    )

    /** 小文件用例正文：约 35 字（约 105 字节），低于测试阈值 256B。 */
    private val smallBody = "测试正文内容。".repeat(5)
    /** 大文件用例正文：约 490 字（约 1.4KB），高于测试阈值 256B。 */
    private val largeBody = "测试正文内容。".repeat(70)

    @Test
    fun `small direct file with profile detects custom chapters`() {
        val source = File(cacheDir, "book.txt").apply {
            writeText("序1 开端\n$smallBody\n序2 结局\n$smallBody")
        }

        val result = TextStreamLoader(cacheDir, testThreshold).loadDirectFile(source, profile())

        assertFalse(result.isStreaming)
        assertEquals(listOf("序1 开端", "序2 结局"), result.document.chapters.map { it.title })
    }

    @Test
    fun `large direct file with profile streams with profile key identity`() {
        val source = File(cacheDir, "book.txt").apply {
            writeText("序1 开端\n$largeBody\n序2 结局\n$largeBody")
        }

        val result = TextStreamLoader(cacheDir, testThreshold).loadDirectFile(source, profile())

        assertTrue(result.isStreaming)
        assertEquals("custom-seq", result.fileIndex?.detectedRuleId)
        assertEquals(listOf("序1 开端", "序2 结局"), result.fileIndex?.chapters?.map { it.title })
        assertEquals(listOf("序1 开端", "序2 结局"), result.document.chapters.map { it.title })
    }

    @Test
    fun `input stream load with profile detects custom chapters on small file`() {
        val bytes = "序1 开端\n$smallBody\n序2 结局\n$smallBody".toByteArray(Charsets.UTF_8)

        val result = TextStreamLoader(cacheDir, testThreshold)
            .load(ByteArrayInputStream(bytes), reportedSize = null, profile = profile())

        assertFalse(result.isStreaming)
        assertEquals(listOf("序1 开端", "序2 结局"), result.document.chapters.map { it.title })
    }
}
