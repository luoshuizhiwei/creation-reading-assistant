package com.creationreadingassistant.feature.reader.doc

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * P1-A：磁盘索引缓存身份必须跟随 profile.key——同一源文件换规则后不得复用旧索引，
 * 旧 ruleId 入口保持 ruleId 身份（缓存文件不漂移）。
 */
class TxtFileIndexCacheProfileTest {

    @Test
    fun `cache identity follows profile key and legacy ruleId`() {
        val root = Files.createTempDirectory("txt-index-profile-").toFile()
        try {
            val source = File(root, "source.txt").apply {
                writeText("第1章\n$body\n1. 序幕\n$body")
            }
            val cache = TxtFileIndexCache(File(root, "cache"))

            val profileA = TxtTocProfile("key-a", listOf(Regex("^第\\d+章$")), densityGuard = false)
            val profileB = TxtTocProfile("key-b", listOf(Regex("^\\d+\\. .+$")), densityGuard = false)

            val indexA = cache.getOrBuild(source, profileA)
            val indexB = cache.getOrBuild(source, profileB)

            assertEquals("key-a", indexA.detectedRuleId)
            assertEquals(listOf("第1章"), indexA.chapters.map { it.title })
            assertEquals("key-b", indexB.detectedRuleId)
            assertEquals("profileB 不识别「第1章」，其前内容应归为开篇", listOf("开篇", "1. 序幕"), indexB.chapters.map { it.title })
            assertNotEquals("不同 profile 不得复用同一索引", indexA, indexB)

            val legacy = cache.getOrBuild(source, "num-dot")
            assertEquals("旧 ruleId 入口身份不变", "num-dot", legacy.detectedRuleId)
        } finally {
            root.deleteRecursively()
        }
    }

    private val body = "测试正文内容。".repeat(70)
}
