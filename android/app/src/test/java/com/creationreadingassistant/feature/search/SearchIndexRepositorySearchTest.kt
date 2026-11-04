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
import io.mockk.coEvery
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
        var bookActive: (String) -> Boolean = { true }
        override suspend fun upsertAllIfBookActive(rows: List<SearchTermRow>): Boolean {
            val id = rows.firstOrNull()?.book_id ?: return false
            if (!bookActive(id)) return false
            upsertAll(rows)
            return true
        }
        override suspend fun countActiveBook(bookId: String): Int = if (bookActive(bookId)) 1 else 0

        val rows = mutableListOf<SearchTermRow>()
        override suspend fun upsertAll(rows: List<SearchTermRow>) { this.rows += rows }
        override suspend fun deleteByBook(bookId: String) { rows.removeIf { it.book_id == bookId } }
        override suspend fun deleteByBookAndBasis(bookId: String, textBasis: String) {
            rows.removeIf { it.book_id == bookId && it.text_basis == textBasis }
        }
        override suspend fun countIndexedBooks(): Long = rows.map { it.book_id }.distinct().size.toLong()
        override suspend fun rowsByTermForBasis(term: String, textBasis: String): List<SearchTermRow> =
            rows.filter { it.term == term && it.text_basis == textBasis }
        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()
    }

    private class FakeSearchIndexCoverageDao : SearchIndexCoverageDao {
        var bookActive: (String) -> Boolean = { true }
        override suspend fun upsertAllIfBookActive(rows: List<SearchIndexCoverageRow>): Boolean {
            val id = rows.firstOrNull()?.book_id ?: return false
            if (!bookActive(id)) return false
            upsertAll(rows)
            return true
        }
        override suspend fun countActiveBook(bookId: String): Int = if (bookActive(bookId)) 1 else 0

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
        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()
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
        activeBookIds: Set<String> = terms.map { it.book_id }.toSet(),
    ): Harness {
        val termDao = FakeSearchTermDao().apply { rows += terms }
        val coverageDao = FakeSearchIndexCoverageDao().apply { rows += coverageRows }
        // `bookDao.activeIds()` 是搜索侧「只认活跃书」的判据来源（M-BUG-10 读取侧防御）。
        // 默认把 term 表里出现过的书全部视为活跃 —— 既有用例的语义因此保持不变；
        // 需要模拟「已删书残留」的用例显式传入更小的集合。
        val bookDao = mockk<BookDao>(relaxed = true)
        coEvery { bookDao.activeIds() } returns activeBookIds.toList()
        val repo = SearchIndexRepository(
            context = mockk<Context>(relaxed = true),
            bookDao = bookDao,
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

    // ===== M-BUG-10 读取侧防御：已删书的残留索引行不得冒进搜索结果 =====

    @Test
    fun `searchContent drops hits whose book is no longer active`() = runTest {
        // b3 已删（索引行还残留着），b1 / b2 / b4 仍活跃。
        val h = buildHarness(seedTerms, seedCoverage, activeBookIds = setOf("b1", "b2", "b4"))
        val hits = h.repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))

        // b3 那条 hits=5（得分最高）必须消失，只剩 b1 的两条。
        // 修复前它会出现在结果里，且因 BookDao.getByIds 带 deleted_at IS NULL 拿不到书名，
        // 结果行退化成裸 bookId（真机复现：搜 `cra` 出现标题 `b6dd8663-…`）。
        assertEquals(2, hits.size)
        assertTrue(hits.none { it.bookId == "b3" })
        assertTrue(hits.all { it.bookId == "b1" })
    }

    @Test
    fun `searchContent returns nothing when no book is active`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage, activeBookIds = emptySet())
        assertTrue(
            h.repo.searchContent("世界", bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY))
                .isEmpty(),
        )
    }

    @Test
    fun `searchContent limit applies to live hits rather than being consumed by orphan rows`() = runTest {
        // 过滤必须发生在 take(limit) **之前**：b3 的残留行得分最高（hits=5），
        // 若先截断再过滤，limit=2 会被 b3 占掉一个名额，活跃命中只剩 1 条。
        val h = buildHarness(seedTerms, seedCoverage, activeBookIds = setOf("b1"))
        val hits = h.repo.searchContent(
            "世界",
            bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY),
            limit = 2,
        )
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.bookId == "b1" })
    }

    // ===== 孤儿索引行回收（挂在「重建索引」入口，见 triggerFullRebuildNow） =====

    @Test
    fun `purgeOrphanIndexRows removes only rows of inactive books`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage, activeBookIds = setOf("b1", "b2"))

        val removed = h.repo.purgeOrphanIndexRows()

        // b3、b4 都已删，各只剩 original 一行。
        assertEquals(2, removed)
        assertTrue(h.termDao.rows.none { it.book_id == "b3" || it.book_id == "b4" })
        assertTrue(h.termDao.rows.any { it.book_id == "b1" })
        assertTrue(h.termDao.rows.any { it.book_id == "b2" })
        // coverage 必须与 term 同生共死：留着孤儿的 coverage 行会让「已索引本数」虚高
        // （countIndexedBooksCheap 数的正是 coverage 里 original 基准的行数）。
        assertTrue(h.coverageDao.rows.none { it.book_id == "b3" || it.book_id == "b4" })
        assertTrue(h.coverageDao.rows.any { it.book_id == "b1" })
    }

    @Test
    fun `purgeOrphanIndexRows is a no-op when every indexed book is active`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage)
        val before = h.termDao.rows.size

        assertEquals(0, h.repo.purgeOrphanIndexRows())

        assertEquals(before, h.termDao.rows.size)
        assertEquals(seedCoverage.size, h.coverageDao.rows.size)
    }

    // ===== coverage-only 孤儿（term 已空、只剩 coverage 行）=====

    @Test
    fun `purgeOrphanIndexRows reclaims a coverage-only orphan book`() = runTest {
        // gone 已删，且**没有**任何 term 行 —— 只有 coverage 行。
        // 修复前候选集合只来自 search_terms，这本书根本进不了候选，孤儿 coverage 永久残留。
        val h = buildHarness(
            terms = listOf(row("世界", "b1", 1, SearchTextBasis.ORIGINAL, hits = 2, offsets = "10:2")),
            coverageRows = listOf(
                coverage("b1", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("gone", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("gone", SearchTextBasis.DISPLAY, SearchCoverageState.FULL),
            ),
            activeBookIds = setOf("b1"),
        )

        val removed = h.repo.purgeOrphanIndexRows()

        assertEquals("孤儿书按「本」计数，不是按行数", 1, removed)
        assertTrue("孤儿 coverage 行必须被回收", h.coverageDao.rows.none { it.book_id == "gone" })
        assertTrue("活跃书的 coverage 不得被动", h.coverageDao.rows.any { it.book_id == "b1" })
        assertTrue("活跃书的 term 不得被动", h.termDao.rows.any { it.book_id == "b1" })
    }

    @Test
    fun `purgeOrphanIndexRows keeps a coverage-only book that is still active`() = runTest {
        // onlyCoverage 仍活跃，只是还没建出 term 行（例如刚标记 FULL 后 term 被清空）。
        // 它必须在候选集合里出现（否则找不到），但**不得**被当成孤儿删掉。
        val h = buildHarness(
            terms = listOf(row("世界", "b1", 1, SearchTextBasis.ORIGINAL, hits = 2, offsets = "10:2")),
            coverageRows = listOf(
                coverage("b1", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("onlyCoverage", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
            ),
            activeBookIds = setOf("b1", "onlyCoverage"),
        )

        assertEquals(0, h.repo.purgeOrphanIndexRows())

        assertTrue(
            "活跃书的 coverage 即使没有 term 行也必须保留",
            h.coverageDao.rows.any { it.book_id == "onlyCoverage" },
        )
        assertEquals(2, h.coverageDao.rows.size)
    }

    @Test
    fun `purgeOrphanIndexRows counts a book with both term and coverage rows once`() = runTest {
        // 同一本孤儿书在两张表里都有行：只算**一本**，两类行全部删除。
        val h = buildHarness(
            terms = listOf(
                row("世界", "b1", 1, SearchTextBasis.ORIGINAL, hits = 2, offsets = "10:2"),
                row("甲", "gone", 1, SearchTextBasis.ORIGINAL, hits = 1),
                row("甲", "gone", 1, SearchTextBasis.DISPLAY, hits = 1),
            ),
            coverageRows = listOf(
                coverage("b1", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("gone", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("gone", SearchTextBasis.DISPLAY, SearchCoverageState.PARTIAL),
            ),
            activeBookIds = setOf("b1"),
        )

        assertEquals("一本书无论占多少行都只计一次", 1, h.repo.purgeOrphanIndexRows())

        assertTrue(h.termDao.rows.none { it.book_id == "gone" })
        assertTrue(h.coverageDao.rows.none { it.book_id == "gone" })
        assertTrue(h.termDao.rows.any { it.book_id == "b1" })
        assertTrue(h.coverageDao.rows.any { it.book_id == "b1" })
    }

    // ===== C01 恢复接口：incompleteBookIds（依赖 coverageDao.rowsByStates）=====

    @Test
    fun `incompleteBookIds returns only incomplete states on the requested basis`() = runTest {
        val h = buildHarness(
            terms = emptyList(),
            coverageRows = listOf(
                coverage("bFull", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("bPartial", SearchTextBasis.ORIGINAL, SearchCoverageState.PARTIAL),
                coverage("bFailed", SearchTextBasis.ORIGINAL, SearchCoverageState.FAILED),
                // display 基准上的 PARTIAL 不应出现在默认（original）结果里。
                coverage("bPartial", SearchTextBasis.DISPLAY, SearchCoverageState.PARTIAL),
                // NOT_APPLICABLE 由 SearchCoveragePolicy.isIncomplete 排除，不算未完成。
                coverage("bNa", SearchTextBasis.ORIGINAL, SearchCoverageState.NOT_APPLICABLE),
            ),
            activeBookIds = setOf("bFull", "bPartial", "bFailed", "bNa"),
        )

        assertEquals(listOf("bFailed", "bPartial"), h.repo.incompleteBookIds())
    }

    @Test
    fun `incompleteBookIds filters by basis and returns each book once sorted`() = runTest {
        // b1 在两个基准上都未完成；b2 只在 display 基准上未完成（original 已 FULL）。
        val h = buildHarness(
            terms = emptyList(),
            coverageRows = listOf(
                coverage("b1", SearchTextBasis.ORIGINAL, SearchCoverageState.PARTIAL),
                coverage("b1", SearchTextBasis.DISPLAY, SearchCoverageState.PREVIEW_ONLY),
                coverage("b2", SearchTextBasis.ORIGINAL, SearchCoverageState.FULL),
                coverage("b2", SearchTextBasis.DISPLAY, SearchCoverageState.PARTIAL),
            ),
            activeBookIds = setOf("b1", "b2"),
        )

        // 默认 original 基准：只有 b1（b2 的 original 是 FULL）。
        assertEquals(listOf("b1"), h.repo.incompleteBookIds())
        // display 基准：两本都未完成，且按 id 升序、每本只出现一次。
        assertEquals(listOf("b1", "b2"), h.repo.incompleteBookIds(basis = SearchTextBasis.DISPLAY))
    }

    // ===== 「重建索引」入口不得把并发守卫置起（否则 worker 空转无限重试） =====

    /**
     * `ensureIndexedIncremental` 开头是并发守卫
     * `if (_progress.value.isRunning) return IndexSweepResult.PROGRESSED`。
     * 重建入口若抢先置 `isRunning = true`，worker 每次启动都会被守卫短路成
     * 立即 PROGRESSED ⇒ `Result.retry()` 空转、退避间隔无限增长，索引**永远不会真的重建**，
     * UI 永久停在「后台构建中：0 / N 本」。
     *
     * 真机已复现：RETRY 间隔 10s→20s→…→80s 递增，全程无写入、无日志。
     */
    @Test
    fun `triggerFullRebuildNow must not leave the running guard set`() = runTest {
        val h = buildHarness(seedTerms, seedCoverage)

        h.repo.triggerFullRebuildNow()

        assertFalse(
            "重建入口不得把 isRunning 置 true —— 会让 worker 被并发守卫永久短路",
            h.repo.progress.value.isRunning,
        )
    }
}
