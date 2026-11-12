package com.creationreadingassistant.feature.reader.navigation

import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 搜索命中首屏偏页回归：搜索索引的 charOffset 基于原始 Markdown 源文本，
 * 而阅读器分页/跳转基于 canonicalText（去标记）。搜索来的 legacyOffset 必须先迁移
 * 到 canonical 空间，否则目标字符不会落在选中页（真机 941→75.3% 偏页）。
 */
class SourceNavigationMarkdownOffsetMigrationTest {

    private val fixture = buildString {
        append("# 中性测试TXT 84v1 首屏落点\n\n")
        repeat(16) { i ->
            val n = i + 1
            append("## 第${n.toString().padStart(2, '0')}节\n\n")
            if (n == 12) {
                append("第12段【中性标记】：本段含唯一查询词 7391，位于整篇中间偏后位置，用于验证搜索结果点击后的首屏落点。本段前后均为中性正文，不含任何其他数字串。\n\n")
            } else {
                append("第${n.toString().padStart(2, '0')}段【中性标记】：这是中性测试正文，用于验证导入、分页阅读与位置移动。本段不含任何正式书库内容，仅用于产生足够多的分页以便观察位置保存。\n\n")
            }
        }
    }

    private fun sourceOffsetOf(word: String) = fixture.indexOf(word)

    @Test
    fun `markdown source offset must be migrated before becoming a canonical jump target`() {
        val sourceOffset = sourceOffsetOf("7391")
        assertEquals(941, sourceOffset)

        val document = MarkdownDocument(fixture)
        val canonical = document.text(0)
        assertTrue("canonicalText must contain the unique word", canonical.contains("7391"))
        val canonicalOffset = canonical.indexOf("7391")
        assertTrue("canonical offset must be smaller than source offset (markers removed)", canonicalOffset < sourceOffset)

        val migrated = document.migrateLegacyOffset(sourceOffset)
        assertEquals(
            "migrated offset must equal the word position in canonicalText",
            canonicalOffset, migrated,
        )
        assertTrue("word must belong to canonical page window around migrated offset",
            canonical.substring(migrated, (migrated + 4).coerceAtMost(canonical.length)).contains("7391"))
    }

    @Test
    fun `target migration only rewrites legacyOffset and preserves chapter and charOffset`() {
        val locator = ReaderLocator(
            legacyOffset = 941,
            chapterIndex = 0,
            charOffset = 120,
            excerptFingerprint = "fp",
        )
        val target = SourceNavigationContract.target("book", locator)
        assertNotNull(target)

        // 迁移函数由 MarkdownDocument 提供；契约层只负责精确改写 legacyOffset
        val migrated = SourceNavigationContract.migrateTargetOffset(target!!) { it - 300 }
        assertEquals(641, migrated.locator.legacyOffset)
        assertEquals(0, migrated.locator.chapterIndex)
        assertEquals(120, migrated.locator.charOffset)
        assertEquals("fp", migrated.locator.excerptFingerprint)
        assertEquals("book", migrated.bookId)
    }
}
