package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 书内搜索会话状态机（SearchSheet 取消/重启语义的纯 JVM seam）。
 *
 * 覆盖 P1 期望行为：取消后停止当前任务、保持已完成结果或进入 CANCELLED，
 * 同一查询不得自动重启，只有用户显式修改查询才可重启。
 */
class BookSearchSessionTest {

    private fun result(snippet: String = "…") =
        BookSearchResult(
            occurrenceIndex = 0, snippet = snippet, progressPercent = 0.5f, chapterIndex = -1,
            chapterTitle = "全文", charOffset = 0, absoluteRange = 0 until snippet.length,
        )

    @Test
    fun `cancel stops current run and enters CANCELLED`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        assertEquals(BookSearchPhase.SEARCHING, s.phase)

        s.cancel()

        assertEquals(BookSearchPhase.CANCELLED, s.phase)
    }

    @Test
    fun `cancel keeps previously completed results`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        val completed = listOf(result())
        s.onSearchCompleted(run1, completed)

        s.onQueryChanged("测试2")
        s.cancel()

        assertEquals(BookSearchPhase.CANCELLED, s.phase)
        assertEquals(completed, s.results)
    }

    @Test
    fun `replaying same query after cancel does not restart search`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        s.cancel()
        assertEquals(BookSearchPhase.CANCELLED, s.phase)

        // 旧实现：取消按钮自增 cancelToken → LaunchedEffect key 变化 →
        // 同一 query 被重新投递并自动重启搜索（等价于这里重放 onQueryChanged(same)）。
        s.onQueryChanged("测试")

        assertEquals("取消后重放同一查询不得自动重启", BookSearchPhase.CANCELLED, s.phase)
    }

    @Test
    fun `new explicit query after cancel restarts search`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        s.cancel()

        s.onQueryChanged("测试2")

        assertEquals(BookSearchPhase.SEARCHING, s.phase)
        assertEquals("测试2", s.query)
    }

    @Test
    fun `stale run completion after cancel is discarded`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        s.cancel()

        s.onSearchCompleted(run1, listOf(result("旧结果")))

        assertEquals(BookSearchPhase.CANCELLED, s.phase)
        assertEquals(emptyList<BookSearchResult>(), s.results)
    }

    @Test
    fun `stale completion of previous run does not overwrite newer run`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        s.onQueryChanged("测试2")
        val run2 = s.beginRun()

        s.onSearchCompleted(run1, listOf(result("旧结果")))
        assertEquals(BookSearchPhase.SEARCHING, s.phase)

        val newResults = listOf(result("新结果"))
        s.onSearchCompleted(run2, newResults)
        assertEquals(BookSearchPhase.COMPLETED, s.phase)
        assertEquals(newResults, s.results)
    }

    @Test
    fun `blank query resets to IDLE and clears results`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        s.onSearchCompleted(run1, listOf(result()))

        s.onQueryChanged("")

        assertEquals(BookSearchPhase.IDLE, s.phase)
        assertEquals("", s.query)
        assertEquals(emptyList<BookSearchResult>(), s.results)
    }

    // ── P1-A：状态机字段必须是 snapshot-observable ────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `phase writes are snapshot observable`() = runTest {
        val s = BookSearchSession()
        val phases = mutableListOf<BookSearchPhase>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            snapshotFlow { s.phase }.collect { phases += it }
        }
        runCurrent()
        assertEquals(listOf(BookSearchPhase.IDLE), phases)

        // 组合外（搜索协程/IO 线程）的写回以可观察快照提交
        Snapshot.withMutableSnapshot {
            s.onQueryChanged("测试")
        }
        runCurrent()

        // 普通 var 不触发快照失效：snapshotFlow 收不到 SEARCHING，
        // 等价于组合中直接读 session.phase 时 UI 卡在旧阶段。
        assertEquals(listOf(BookSearchPhase.IDLE, BookSearchPhase.SEARCHING), phases)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `results writes are snapshot observable`() = runTest {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        val observed = mutableListOf<List<BookSearchResult>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            snapshotFlow { s.results }.collect { observed += it }
        }
        runCurrent()
        assertEquals(listOf(emptyList<BookSearchResult>()), observed)

        val completed = listOf(result())
        Snapshot.withMutableSnapshot {
            s.onSearchCompleted(run1, completed)
        }
        runCurrent()

        assertEquals(listOf(emptyList<BookSearchResult>(), completed), observed)
        job.cancel()
    }

    // ── P2-B：查询清空 = 取消在途运行（迟到回报一律丢弃）──────────
    @Test
    fun `blank query invalidates in-flight run and drops late callbacks`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun()
        s.onProgress(run1, 5, 100)

        s.onQueryChanged("")

        assertEquals(BookSearchPhase.IDLE, s.phase)
        s.onProgress(run1, 50, 100)
        s.onSearchCompleted(run1, listOf(result("迟到结果")))
        assertEquals(0 to 0, s.progress)
        assertEquals(emptyList<BookSearchResult>(), s.results)
    }

    // ── P2-1：文档上下文变化（TXT 规则重扫 / 文档实例变化）────────────

    @Test
    fun `cancel then reopen panel in same context does not restart`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        s.beginRun("ctx-1")
        s.cancel()

        // SearchSheet 重开面板判定：同 query + 同上下文 → 不重启
        assertFalse(s.shouldRestartSearch("测试"))
        assertTrue(s.matchesSearchContext("ctx-1"))
        assertEquals(BookSearchPhase.CANCELLED, s.phase)
    }

    @Test
    fun `completed results survive reopen in same context`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun("ctx-1")
        val completed = listOf(result())
        s.onSearchCompleted(run1, completed)

        assertFalse(s.shouldRestartSearch("测试"))
        assertTrue(s.matchesSearchContext("ctx-1"))
        assertEquals(BookSearchPhase.COMPLETED, s.phase)
        assertEquals(completed, s.results)
    }

    @Test
    fun `context change after cancel with same query clears results and restarts`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun("ctx-1")
        s.onSearchCompleted(run1, listOf(result("旧结果")))
        s.onQueryChanged("测试2")
        s.cancel()
        assertEquals(BookSearchPhase.CANCELLED, s.phase)

        // txt 规则重扫 / 文档实例变化：即使 query 未变也必须清旧 results/currentIndex 并重启
        assertTrue(s.onContextChanged("测试", "ctx-2"))
        assertEquals(BookSearchPhase.SEARCHING, s.phase)
        assertEquals("测试", s.query)
        assertEquals(emptyList<BookSearchResult>(), s.results)
        assertEquals(-1, s.currentIndex)
        assertTrue(s.matchesSearchContext("ctx-2"))
        assertFalse(s.matchesSearchContext("ctx-1"))

        val run2 = s.beginRun("ctx-2")
        val newResults = listOf(result("新结果"))
        s.onSearchCompleted(run2, newResults)
        assertEquals(BookSearchPhase.COMPLETED, s.phase)
        assertEquals(newResults, s.results)
    }

    @Test
    fun `context change while completed with same query restarts and clears old results`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun("ctx-1")
        s.onSearchCompleted(run1, listOf(result("旧结果")))
        assertEquals(BookSearchPhase.COMPLETED, s.phase)

        assertTrue(s.onContextChanged("测试", "ctx-2"))
        assertEquals(BookSearchPhase.SEARCHING, s.phase)
        assertEquals(emptyList<BookSearchResult>(), s.results)
        assertEquals(-1, s.currentIndex)

        val run2 = s.beginRun("ctx-2")
        val newResults = listOf(result("新结果"))
        s.onSearchCompleted(run2, newResults)
        assertEquals(BookSearchPhase.COMPLETED, s.phase)
        assertEquals(newResults, s.results)
    }

    @Test
    fun `context change with blank query enters IDLE without stale results`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun("ctx-1")
        s.onSearchCompleted(run1, listOf(result()))

        assertFalse(s.onContextChanged("", "ctx-2"))
        assertEquals(BookSearchPhase.IDLE, s.phase)
        assertEquals(emptyList<BookSearchResult>(), s.results)
        assertEquals(-1, s.currentIndex)
        assertTrue(s.matchesSearchContext("ctx-2"))
    }
}
