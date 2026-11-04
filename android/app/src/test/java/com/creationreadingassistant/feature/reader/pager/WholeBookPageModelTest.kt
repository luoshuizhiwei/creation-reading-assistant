package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WholeBookPageModel] 的 JVM 锁定测试（优化清单 A1 验收项：
 * 前缀和的插入 / 删除 / 重排三种变更均有断言）。
 */
class WholeBookPageModelTest {

    @Test
    fun `all-estimated book totals from default capacity`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000, 4000), defaultCharsPerPage = 800)
        assertEquals(5, model.pagesOf(0))
        assertEquals(15, model.totalPages)
        assertEquals(0, model.pageOffsetOf(0))
        assertEquals(5, model.pageOffsetOf(1))
        assertEquals(10, model.pageOffsetOf(2))
    }

    @Test
    fun `zero-char chapter still occupies one page`() {
        val model = WholeBookPageModel(intArrayOf(0, 100), defaultCharsPerPage = 800)
        assertEquals(1, model.pagesOf(0))
        assertEquals(1, model.pagesOf(1))
    }

    @Test
    fun `recording exact pages shifts later chapters and reports change`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000, 4000), defaultCharsPerPage = 800)
        assertTrue(model.recordExactPages(0, 10))
        // 重复登记同值：热路径无脑调用不应触发刷新。
        assertFalse(model.recordExactPages(0, 10))
        // 平均页容量由精确样本反推：4000/10 = 400 字/页 → 未排章 4000 字估 10 页。
        assertEquals(400, model.effectiveCharsPerPage)
        assertEquals(10, model.pagesOf(1))
        assertEquals(30, model.totalPages)
        assertEquals(10, model.pageOffsetOf(1))
        assertEquals(20, model.pageOffsetOf(2))
    }

    @Test
    fun `re-layout of a chapter incrementally corrects the index`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000), defaultCharsPerPage = 800)
        model.recordExactPages(0, 10)
        model.recordExactPages(1, 10)
        assertEquals(20, model.totalPages)
        // 字号变小重排：第 0 章变 12 页，第 1 章全局起点随之前移。
        assertTrue(model.recordExactPages(0, 12))
        assertEquals(12, model.pageOffsetOf(1))
        assertEquals(22, model.totalPages)
        assertEquals(12, model.globalPageOf(1, 0))
    }

    @Test
    fun `forgetting exact pages falls back to estimation`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000), defaultCharsPerPage = 800)
        model.recordExactPages(0, 10)
        assertTrue(model.isExact(0))
        assertTrue(model.forgetExactPages(0))
        assertFalse(model.isExact(0))
        assertFalse(model.forgetExactPages(0))
        assertEquals(5, model.pagesOf(0))
        assertEquals(model.effectiveCharsPerPage, WholeBookPageModel.DEFAULT_CHARS_PER_PAGE)
    }

    @Test
    fun `chapter insertion keeps exact data only for same index same char count`() {
        val model = WholeBookPageModel(intArrayOf(1000, 2000, 3000), defaultCharsPerPage = 800)
        model.recordExactPages(1, 4)
        model.recordExactPages(2, 6)
        // 完全一致的替换是 no-op。
        assertFalse(model.setChapterCharCounts(intArrayOf(1000, 2000, 3000)))
        // 第 1 章字符数变化 → 精确作废；第 2 章同下标同字符数 → 保留。
        assertTrue(model.setChapterCharCounts(intArrayOf(1000, 2500, 3000)))
        assertFalse(model.isExact(1))
        assertTrue(model.isExact(2))
        assertEquals(6, model.pagesOf(2))
    }

    @Test
    fun `deleting tail chapters with exact data reports drop`() {
        val model = WholeBookPageModel(intArrayOf(1000, 2000, 3000), defaultCharsPerPage = 800)
        model.recordExactPages(2, 6)
        assertTrue(model.setChapterCharCounts(intArrayOf(1000, 2000)))
        assertEquals(2, model.chapterCount)
        assertFalse(model.setChapterCharCounts(intArrayOf(1000, 2000)))
    }

    @Test
    fun `percent is monotonic across pages and chapters and ends at 100`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000), defaultCharsPerPage = 800)
        model.recordExactPages(0, 10)
        var last = -1f
        for (chapter in 0..1) {
            val pages = model.pagesOf(chapter)
            for (page in 0..pages) {
                val pct = model.percentOf(chapter, page, pages)
                assertTrue("percent must not regress: $last -> $pct", pct >= last)
                last = pct
            }
        }
        assertEquals(100f, model.percentOf(1, 10, 10), 0.001f)
        assertEquals(0f, model.percentOf(0, 0, 10), 0.001f)
    }

    @Test
    fun `percent clamps overshooting page index instead of jumping past 100`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000), defaultCharsPerPage = 800)
        assertEquals(100f, model.percentOf(1, 999, 5), 0.001f)
        // 第 1 章起点第 5 页、共 5 页（0 基最大页号 4）→ 越界夹到全局第 9 页。
        assertEquals(9, model.globalPageOf(1, 999))
    }

    @Test
    fun `empty book yields zero total and rejects access`() {
        val model = WholeBookPageModel(IntArray(0))
        assertEquals(0, model.totalPages)
        try {
            model.percentOf(0, 0, 1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 越界访问属调用方 bug，必须炸给测试看。
        }
    }

    @Test
    fun `revision bumps on every structural or page-count change only`() {
        val model = WholeBookPageModel(intArrayOf(4000, 4000), defaultCharsPerPage = 800)
        val before = model.revision
        model.recordExactPages(0, 10)
        val afterRecord = model.revision
        assertTrue(afterRecord > before)
        model.recordExactPages(0, 10)
        assertEquals(afterRecord, model.revision)
    }
}
