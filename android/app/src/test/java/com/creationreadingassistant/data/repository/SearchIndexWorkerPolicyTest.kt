package com.creationreadingassistant.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchIndexWorkerPolicyTest {

    @Test
    fun `first two failed attempts retry`() {
        assertEquals(SearchIndexWorkerFailureAction.RETRY, searchIndexWorkerFailureAction(0))
        assertEquals(SearchIndexWorkerFailureAction.RETRY, searchIndexWorkerFailureAction(1))
    }

    @Test
    fun `third and later failed attempts stop automatic retry`() {
        assertEquals(SearchIndexWorkerFailureAction.FAIL, searchIndexWorkerFailureAction(2))
        assertEquals(SearchIndexWorkerFailureAction.FAIL, searchIndexWorkerFailureAction(5))
    }

    @Test
    fun `reaching the end of the snapshot completes the sweep`() {
        assertEquals(IndexSweepResult.COMPLETED, indexSweepResult(reached = 26, from = 0, until = 26))
    }

    @Test
    fun `an already fully swept snapshot completes without doing work`() {
        assertEquals(IndexSweepResult.COMPLETED, indexSweepResult(reached = 26, from = 26, until = 26))
    }

    @Test
    fun `partial progress asks for another window`() {
        // 8 本 / 共 26 本：预算耗尽前推进过 → 应重新入队续建
        assertEquals(IndexSweepResult.PROGRESSED, indexSweepResult(reached = 8, from = 0, until = 26))
        assertEquals(IndexSweepResult.PROGRESSED, indexSweepResult(reached = 20, from = 8, until = 26))
    }

    @Test
    fun `no progress at all stalls instead of retrying forever`() {
        // 一本都没推进：单本书本身超出一次作业窗口，重试只会空转
        assertEquals(IndexSweepResult.STALLED, indexSweepResult(reached = 0, from = 0, until = 26))
        assertEquals(IndexSweepResult.STALLED, indexSweepResult(reached = 8, from = 8, until = 26))
    }

    @Test
    fun `chapter level progress inside one book asks for another window`() {
        // 章节级续建（§6.1）：停在某一本上没换书（reached == from），但书内章节确实推进了
        // → 必须判 PROGRESSED 继续续建。否则超大 EPUB 会被误判成 STALLED 而提前放弃，
        //   那正是章节级续建要消灭的失败模式。
        assertEquals(
            IndexSweepResult.PROGRESSED,
            indexSweepResult(reached = 8, from = 8, until = 26, advancedWithinBook = true),
        )
        assertEquals(
            IndexSweepResult.PROGRESSED,
            indexSweepResult(reached = 25, from = 25, until = 26, advancedWithinBook = true),
        )
    }

    @Test
    fun `no chapter progress inside one book stalls`() {
        // 停在同一本上且书内一章都没推进：连一章都装不进一次窗口，重试无望 → STALLED
        assertEquals(
            IndexSweepResult.STALLED,
            indexSweepResult(reached = 8, from = 8, until = 26, advancedWithinBook = false),
        )
    }

    @Test
    fun `finishing the last book completes regardless of chapter progress`() {
        // 最后一本在本次窗口内建完（reached 到末尾）→ COMPLETED 优先于章节推进标志
        assertEquals(
            IndexSweepResult.COMPLETED,
            indexSweepResult(reached = 26, from = 25, until = 26, advancedWithinBook = true),
        )
    }

    // ===== §6.8：续建章节计数（indexed 累加 / total 全书口径）=====

    @Test
    fun `fresh build counts chapters with no baseline`() {
        // 整本重建：本轮建了 1774 章、全书 1774 章，基线为 0
        val counts = resumeChapterCounts(runIndexed = 1774, runTotal = 1774, baseIndexed = 0)
        assertEquals(1774, counts.indexed)
        assertEquals(1774, counts.total)
    }

    @Test
    fun `resumed build accumulates indexed chapters`() {
        // 续建：上一轮已建 1250 章，本轮又建 524 章 → 已建数必须累加，否则覆盖率倒退
        val counts = resumeChapterCounts(runIndexed = 524, runTotal = 1774, baseIndexed = 1250)
        assertEquals(1774, counts.indexed)
    }

    @Test
    fun `resumed build never adds baseline to total chapters`() {
        // ★ §6.8 的回归护栏：分母绝不能累加基线。
        // 真机踩过的坑：run.total(1249) + base.total(1774) = 3023，而全书只有 1774 章，
        // 于是整本建完 indexed/total 仍 < 1，覆盖率被永久误判为 PARTIAL。
        // 即便传入一个离谱的基线（脏分母 3023），分母也必须是本轮的全书口径值。
        val counts = resumeChapterCounts(
            runIndexed = 524,
            runTotal = 1774,
            baseIndexed = 1250,
            baseTotal = 3023,
        )
        assertEquals(1774, counts.total)
    }
}
