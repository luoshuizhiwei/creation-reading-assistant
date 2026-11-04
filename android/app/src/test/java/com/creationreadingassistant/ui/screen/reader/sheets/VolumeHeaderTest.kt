package com.creationreadingassistant.ui.screen.reader.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卷标判定回归。
 *
 * 旧实现用 `contains("卷") || contains("部")` 原子串判据，会把「正文标题里恰好含
 * 卷/部字」的章节误判成卷名，[readerTocEntries] 随即把整章从目录里丢掉。
 * 下面的反例即来自真实 TXT 的章节标题形态（编号 + 空格 + 标题，标题里带「试卷」
 * 「拳击部」「部分」，以及人姓「长谷部」）。
 */
class VolumeHeaderTest {

    @Test
    fun `chapter titles merely mentioning juan or bu stay chapters`() {
        val bodyTitles = listOf(
            "019 系统的部分规则",
            "104 她没去拳击部",
            "125 举报试卷怎么样呢？算作弊吗？",
            "130 组长：举报试卷掀翻桌子有待商榷。",
            "131 该不会要和拳击部的人约会吧",
            "132 租监控，拳击部的后辈到底会是谁呢？",
            "139 我打算把试卷以五百万的价格卖出去",
            "145 改试卷的题目--本人当晚数了多少只羊？",
            "146 卖给会长的天价试卷(二合一5k)",
            "258 长谷部同学：这个标题里有三部",
        )
        for (title in bodyTitles) {
            assertFalse("不应判为卷标: $title", isVolumeHeader(title))
        }
    }

    @Test
    fun `whole-line volume markers are still recognized`() {
        val headers = listOf(
            "第一卷", "第1卷", "第二部", "卷一", "卷之三", "上卷", "中卷", "第二卷",
            "第一卷 风起云涌", "第1卷：少年篇", "Vol.1", "Volume 2", "Part 3", "PART IV",
        )
        for (header in headers) {
            assertTrue("应判为卷标: $header", isVolumeHeader(header))
        }
    }

    @Test
    fun `fullwidth leading indentation does not change the verdict`() {
        assertTrue(isVolumeHeader("　　第一卷"))
        assertFalse(isVolumeHeader("　　019 系统的部分规则"))
    }

    @Test
    fun `chapters with juan in their title keep their slot instead of becoming a group name`() {
        val titles = listOf("第一卷", "001 开端", "002 举报试卷怎么样呢", "003 尾声")
        val entries = readerTocEntries(titles, current = 2, recent = emptyList())

        // 旧行为：002 被当成卷名吞掉，目录只剩 2 章且后续分组名变成那一整串标题。
        assertEquals(listOf(1, 2, 3), entries.map { it.index })
        assertEquals(listOf("001 开端", "002 举报试卷怎么样呢", "003 尾声"), entries.map { it.title })
        assertTrue(entries.all { it.volume == "第一卷" })
    }

    @Test
    fun `grouping keeps non volume titles out of the header list`() {
        val groups = groupChaptersByVolume(listOf("第一卷", "甲", "002 举报试卷", "第二卷", "丙"))
        assertEquals(listOf("第一卷", "第二卷"), groups.map { it.first })
        assertEquals(listOf(1, 2), groups[0].second)
        assertEquals(listOf(4), groups[1].second)
    }
}
