package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖率判定的纯函数契约测试。
 *
 * 重点不是「跑通」，而是把 [SearchCoveragePolicy] 的边界钉死：
 * 「部分索引不是全文完成」这条 README 约束必须由断言保证，
 * 否则以后有人把 `indexed < total` 的判断删掉，UI 会开始谎报全量。
 */
class SearchCoveragePolicyTest {

    // ── forChapterIndex：全量 / 部分 ───────────────────────────────────

    @Test
    fun `all chapters indexed without truncation is full`() {
        assertEquals(
            SearchCoverageState.FULL,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 120, totalChapters = 120, truncated = false),
        )
    }

    @Test
    fun `truncated chapter downgrades full to partial`() {
        assertEquals(
            SearchCoverageState.PARTIAL,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 120, totalChapters = 120, truncated = true),
        )
    }

    @Test
    fun `skipped chapter downgrades full to partial`() {
        assertEquals(
            SearchCoverageState.PARTIAL,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 119, totalChapters = 120, truncated = false),
        )
    }

    @Test
    fun `zero indexed bodies out of known chapters is partial not metadata only`() {
        // 切出了章节、但没有任何一章贡献正文（都是标题章）—— 这是「部分」而不是「只有元数据」。
        assertEquals(
            SearchCoverageState.PARTIAL,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 0, totalChapters = 5, truncated = false),
        )
    }

    @Test
    fun `no chapters at all falls back to metadata only`() {
        assertEquals(
            SearchCoverageState.METADATA_ONLY,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 0, totalChapters = 0, truncated = false),
        )
        assertEquals(
            SearchCoverageState.METADATA_ONLY,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 0, totalChapters = -1, truncated = false),
        )
    }

    @Test
    fun `more indexed than total still counts as full`() {
        // 防御性：索引侧多算一章不应被误判成「部分」（只有 < 才是漏）。
        assertEquals(
            SearchCoverageState.FULL,
            SearchCoveragePolicy.forChapterIndex(indexedChapters = 121, totalChapters = 120, truncated = false),
        )
    }

    // ── isIncomplete：重建候选判据 ─────────────────────────────────────

    @Test
    fun `full and not applicable are both complete`() {
        // NOT_APPLICABLE 是显示文通道的**稳定终态**（无生效替换规则、与原文一致、不建索引），
        // 不是「还没建」，绝不能算进「未完成」——否则「重建未完成索引」会把整个书库都误判。
        assertFalse(SearchCoveragePolicy.isIncomplete(SearchCoverageState.FULL))
        assertFalse(SearchCoveragePolicy.isIncomplete(SearchCoverageState.NOT_APPLICABLE))
        SearchCoverageState.entries
            .filter { it != SearchCoverageState.FULL && it != SearchCoverageState.NOT_APPLICABLE }
            .forEach { state ->
                assertTrue("$state 应判为未完成", SearchCoveragePolicy.isIncomplete(state))
            }
    }

    @Test
    fun `not applicable is a stable terminal state for books without replace rules`() {
        // 无生效替换规则的书：显示文与原文逐字一致，再建一套 display 索引只是重复，
        // 故不建索引、不产生 display 行，覆盖态记 NOT_APPLICABLE（不是 PENDING）。
        assertEquals(SearchCoverageState.NOT_APPLICABLE, SearchCoverageState.fromWire("not_applicable"))
    }

    // ── wire 契约：磁盘上已写过的字符串不可漂移 ─────────────────────────

    @Test
    fun `text basis wire values are frozen`() {
        assertEquals("original", SearchTextBasis.ORIGINAL.wire)
        assertEquals("display", SearchTextBasis.DISPLAY.wire)
    }

    @Test
    fun `coverage state wire values are frozen`() {
        assertEquals("full", SearchCoverageState.FULL.wire)
        assertEquals("partial", SearchCoverageState.PARTIAL.wire)
        assertEquals("preview_only", SearchCoverageState.PREVIEW_ONLY.wire)
        assertEquals("metadata_only", SearchCoverageState.METADATA_ONLY.wire)
        assertEquals("failed", SearchCoverageState.FAILED.wire)
        assertEquals("pending", SearchCoverageState.PENDING.wire)
        assertEquals("not_applicable", SearchCoverageState.NOT_APPLICABLE.wire)
    }

    @Test
    fun `wire round trip`() {
        SearchTextBasis.entries.forEach { basis ->
            assertEquals(basis, SearchTextBasis.fromWire(basis.wire))
        }
        SearchCoverageState.entries.forEach { state ->
            assertEquals(state, SearchCoverageState.fromWire(state.wire))
        }
        assertNull(SearchTextBasis.fromWire("unknown"))
        assertNull(SearchCoverageState.fromWire(null))
    }
}
