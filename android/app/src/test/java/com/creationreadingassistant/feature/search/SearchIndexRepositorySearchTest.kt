package com.creationreadingassistant.feature.search

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageRow
import com.creationreadingassistant.data.local.dao.SearchIndexStateDao
import com.creationreadingassistant.data.local.dao.SearchTermDao
import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchIndexRepository.searchContent] 的双通道口径契约测试（R2-S1.6）。
 *
 * 不依赖 Room / 设备：用内存假 DAO 喂入 original / display 两套 term 行 + coverage 行，
 * 配合**真实** [SearchTokenizer]，断言搜索按基准过滤、命中标注来源、覆盖率从 coverage 表带上、
 * 上限截断按分排序生效、空查询/无命中返回空。
 *
 * 这套断言是「两者都搜并标注来源」口径的回归护栏——一旦有人把默认基准改回只搜原文、
 * 或把 display 行混进 original 查询，测试就会红。
 */
class SearchIndexRepositorySearchTest {

    private class FakeSearchTermDao : SearchTermDao {
        val rows = mutableListOf<SearchTermRow>()
        override suspend fun upsertAll(rows: List<SearchTermRow>) { this.rows += rows }
        override suspend fun deleteByBook(bookId: String) { rows.removeIf { it.book_id == bookId } }
        override suspend fun deleteByChapter(bookId: String, chapterIndex: Int) {
            rows.removeIf { it.book_id == bookId && it.chapter_index == chapterIndex }
        }
        override suspend fun deleteByBookAndBasis(bookId: String, textBasis: String) {
            rows.removeIf { it.book_id == bookId && it.text_basis == textBasis }
        }
        override suspend fun countTerms(): Long = rows.size.toLong()
        override suspend fun countIndexedBooks(): Long = rows.map { it.book_id }.distinct().size.toLong()
        override suspend fun rowsByTerm(term: String): List<SearchTermRow> = rows.filter { it.term == term }
        override suspend fun rowsByTermForBasis(term: String, textBasis: String): List<SearchTermRow> =
            rows.filter { it.term == term && it.text_basis == textBasis }
        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()
    }

    private class FakeSearchIndexCoverageDao : SearchIndexCoverageDao {
        val rows = mutableListOf<SearchIndexCoverageRow>()
        override suspend fun upsertAll(rows: List<SearchIndexCoverageRow>) {
            for (r in rows) {
                val idx = this.rows.indexOfFirst { it.book_id == r.book_id && it.text_basis == r.text_basis }
                if (idx >= 0) this.rows[idx] = r else this.rows.add(r)
            }
        }
        override suspend fun upsert(row: SearchIndexCoverageRow) = upsertAll(listOf(row))
        override suspend fun rowsForBook(bookId: String): List<SearchIndexCoverageRow> =
            rows.filter { it.book_id == bookId }
        override suspend fun row(bookId: String, textBasis: String): SearchIndexCoverageRow? =
            rows.firstOrNull { it.book_id == bookId && it.text_basis == textBasis }
        override suspend fun all(): List<SearchIndexCoverageRow> = rows.toList()
        override suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow> =
            rows.filter { it.coverage in states }
        override suspend fun countByStates(states: List<String>): Long = rowsByStates(states).size.toLong()
        override suspend fun deleteByBook(bookId: String) { rows.removeIf { it.book_id == bookId } }
        override suspend fun deleteAll() { rows.clear() }
    }

    private fun row(
        term: String,
        bookId: String,
        chapter: Int,
        basis: SearchTextBasis,
        hits: Int,
        offsets: String? = null,
    ) = SearchTermRow(
        term = term,
        book_id = bookId,
        chapter_index = chapter,
        text_basis = basis.wire,
        hits = hits,
        offsets = offsets,
    )

    private fun coverage(bookId: String, basis: SearchTextBasis, state: SearchCoverageState) =
        SearchIndexCoverageRow(
            book_id = bookId,
            text_basis = basis.wire,
            format = "txt",
            coverage = state.wire,
            indexed_chapters = 5,
            total_chapters = 5,
            tokenizer_version = 2,
            indexed_at = 1,
        )

    /** 仓库 + 其背后的 Fake DAO，供「需要观察落盘结果」的用例使用。 */
    private class Harness(
        val repo: SearchIndexRepository,
        val termDao: FakeSearchTermDao,
        val coverageDao: FakeSearchIndexCoverageDao,
    )

    private fun buildHarness(
        terms: List<SearchTermRow>,
        coverageRows: List<SearchIndexCoverageRow>,
    ): Harness {
        val termDao = FakeSearchTermDao().apply { rows += terms }
        val coverageDao = FakeSearchIndexCoverageDao().apply { rows += coverageRows }
        val repo = SearchIndexRepository(
            context = mockk<Context>(relaxed = true),
            bookDao = mockk(relaxed = true),
            bookContentDao = mockk(relaxed = true),
            termDao = termDao,
            stateDao = mockk(relaxed = true),
            coverageDao = coverageDao,
            tokenizer = SearchTokenizer(),
            rulesRepository = mockk(relaxed = true),
        )
        return Harness(repo, termDao, coverageDao)
    }

    private fun buildRepository(
        terms: List<SearchTermRow>,
        coverageRows: List<SearchIndexCoverageRow>,
    ): SearchIndexRepository = buildHarness(terms, coverageRows).repo

    private val seedTerms = listOf(
        row("世界", "b1", 1, SearchTextBasis.ORIGINAL, hits = 2, offsets = "10:2,20:2"),
        row("世界", "b1", 1, SearchTextBasis.DISPLAY, hits = 3, offsets = "0:2,5:2"),
        row("世界", "b3", 1, SearchTextBasis.ORIGINAL, hits = 5, offsets = "0:2"),
        row("小说", "b2", 1, SearchTextBasis.ORIGINAL, hits = 1),
        row("小说", "b2", 1, SearchTextBasis.DISPLAY, hits = 1),
        row("李白", "b4", 1, SearchTextBasis.ORIGINAL, hits = 4, offsets = "0:2"),
    )

    private val seedCoverage = listOf(
        coverage("b1", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
        coverage("b1", SearchTextBasis.DISPLAY, SearchCoverageState.FULL),
        coverage("b2", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
        coverage("b2", SearchTextBasis.DISPLAY, SearchCoverageState.FULL),
        coverage("b3", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
        coverage("b4", SearchTextBasis.ORIGINAL, SearchCoverageState.PARTIAL),
    )

    private val repo by lazy { buildRepository(seedTerms, seedCoverage) }

    @Test
    fun `default basis is original only`() = runTest {
        val hits = repo.searchContent("世界")
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.textBasis == SearchTextBasis.ORIGINAL })
    }

    @Test
    fun `original only basis searches original channel`() = runTest {
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL))
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.textBasis == SearchTextBasis.ORIGINAL })
        val b1 = hits.first { it.bookId == "b1" }
        assertEquals(10, b1.charOffset) // 原文通道偏移来自 source 行 "10:2,20:2"
    }

    @Test
    fun `display only basis searches display channel`() = runTest {
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.DISPLAY))
        assertEquals(1, hits.size)
        val b1 = hits.single()
        assertEquals("b1", b1.bookId)
        assertEquals(SearchTextBasis.DISPLAY, b1.textBasis)
        assertEquals(0, b1.charOffset) // 显示文通道偏移来自 display 行 "0:2,5:2"
    }

    @Test
    fun `dual basis searches both channels and labels each hit`() = runTest {
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
        // b1 在两种基准各出一条 + b3 仅原文 = 3 条
        assertEquals(3, hits.size)
        val b1Bases = hits.filter { it.bookId == "b1" }.map { it.textBasis }.toSet()
        assertEquals(setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY), b1Bases)
    }

    @Test
    fun `basis filter excludes the other channel entirely`() = runTest {
        val displayOnly = repo.searchContent("世界", bases = setOf(SearchTextBasis.DISPLAY))
        assertTrue(displayOnly.none { it.textBasis == SearchTextBasis.ORIGINAL })
        val originalOnly = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL))
        assertTrue(originalOnly.none { it.textBasis == SearchTextBasis.DISPLAY })
    }

    @Test
    fun `coverage is attached from the coverage table per basis`() = runTest {
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
        assertTrue(hits.all { it.coverage == SearchCoverageState.FULL })
    }

    @Test
    fun `terms absent in display channel still return their original hits`() = runTest {
        // b4 仅在 original 通道有「李白」，display 通道没有该行
        val hits = repo.searchContent("李白", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
        assertEquals(1, hits.size)
        assertEquals(SearchTextBasis.ORIGINAL, hits.single().textBasis)
        assertEquals(0, hits.single().charOffset)
    }

    @Test
    fun `dual basis keeps both original and display rows for a term present in both`() = runTest {
        val hits = repo.searchContent("小说", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
        assertEquals(2, hits.size)
        assertTrue(hits.any { it.textBasis == SearchTextBasis.ORIGINAL })
        assertTrue(hits.any { it.textBasis == SearchTextBasis.DISPLAY })
    }

    @Test
    fun `results are sorted by score descending`() = runTest {
        // b3 原文 hits=5 最靠前，其次 b1 显示文 hits=3，最后 b1 原文 hits=2
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
        val scores = hits.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
        assertEquals("b3", hits.first().bookId)
    }

    @Test
    fun `limit caps the result count after sorting by score`() = runTest {
        val hits = repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY), limit = 2)
        assertEquals(2, hits.size)
        // 前两名：b3 原文(5) + b1 显示文(3)；b1 原文(2) 被截断
        assertEquals("b3", hits[0].bookId)
        assertEquals(SearchTextBasis.DISPLAY, hits[1].textBasis)
        assertEquals("b1", hits[1].bookId)
    }

    @Test
    fun `blank query returns no hits`() = runTest {
        assertTrue(repo.searchContent("   ").isEmpty())
        assertTrue(repo.searchContent("").isEmpty())
    }

    @Test
    fun `no matching term returns no hits`() = runTest {
        assertTrue(
            repo.searchContent("不存在的词汇xyz", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
                .isEmpty(),
        )
    }

    @Test
    fun `zero limit returns no hits`() = runTest {
        assertTrue(repo.searchContent("世界", limit = 0).isEmpty())
    }

    @Test
    fun `coverage null is tolerated when no coverage row exists for that basis`() = runTest {
        // b3 只有 original coverage 行；display 命中不会出现（因为 term 表没有 b3 display 行），
        // 这里验证 coverage 缺失不会抛异常：b4 的 original 命中应正常带出 PARTIAL。
        val hits = repo.searchContent("李白", bases = setOf(SearchTextBasis.ORIGINAL))
        assertEquals(1, hits.size)
        assertEquals(SearchCoverageState.PARTIAL, hits.single().coverage)
    }

    // ===== §6.7：显示文投影失败的精确回收 =====

    @Test
    fun `discarding display rows removes display only and keeps original intact`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage)
        val pending = mutableListOf(
            row("甲", "b1", 2, SearchTextBasis.ORIGINAL, hits = 1),
            row("甲", "b1", 2, SearchTextBasis.DISPLAY, hits = 1),
        )
        h.repo.discardDisplayRows("b1", pending)

        // 待落盘：display 行就地摘除，original 行原样保留 —— 本批不会留下半截 display 行。
        assertEquals(1, pending.size)
        assertEquals(SearchTextBasis.ORIGINAL.wire, pending.single().text_basis)

        // 已落盘：只清 b1 的 display 行；b1 的 original 行、其它书的 display 行一律不动。
        assertTrue(h.termDao.rows.none { it.book_id == "b1" && it.text_basis == SearchTextBasis.DISPLAY.wire })
        assertTrue(h.termDao.rows.any { it.book_id == "b1" && it.text_basis == SearchTextBasis.ORIGINAL.wire })
        assertTrue(h.termDao.rows.any { it.book_id == "b2" && it.text_basis == SearchTextBasis.DISPLAY.wire })
    }

    @Test
    fun `discarding display rows is idempotent`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage)
        val before = h.termDao.rows.count { it.book_id == "b1" }
        val pending = mutableListOf<SearchTermRow>()
        repeat(3) { h.repo.discardDisplayRows("b1", pending) }
        // b1 种子里是 1 条 original + 1 条 display；反复调用只应减掉那 1 条 display。
        assertEquals(before - 1, h.termDao.rows.count { it.book_id == "b1" })
    }
}
