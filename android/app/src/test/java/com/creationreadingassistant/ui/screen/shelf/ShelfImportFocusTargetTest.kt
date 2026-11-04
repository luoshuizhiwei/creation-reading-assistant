package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.feature.library.ImportBatchSuccesses
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入后「查看」的定位判定（S05-④，含复核补强）。
 *
 * 这组纯函数表把四条契约钉住，每条都对应一个真实失败模式：
 *
 * 1. **只认本批**（[focusImportTarget]）。旧实现取 `ShelfPrefs.newBookIds.firstOrNull()` ——
 *    跨批次的持久「未查看」集合，于是「查看」可能滚到上一批遗留、用户始终没打开的书上。
 * 2. **按批次核对取 id**（[focusIdsForBatch]）。提示所对应的批次与导入器当前批次不一致时，
 *    绝不能把新批次的成功 id 当成旧提示的目标；批次一致但列表异常为空时返回空，
 *    由调用方明确反馈而不是静默结束。
 * 3. **必须可见**。目标被当前搜索 / 筛选排除时不能当成功处理。
 * 4. **有界等待**（[awaitFocusTarget]）。书已落库但列表流还没显示它时，不能立刻误报
 *    「被筛选排除」；但等待必须有上限，不能无限挂着变成新的过期请求。
 *
 * 不依赖 Compose 运行时 / 设备，纯 JVM 可跑。
 */
class ShelfImportFocusTargetTest {

    // ===== focusImportTarget：只认本批 + 必须可见 =====

    @Test
    fun `picks the first candidate that is currently visible`() {
        assertEquals(
            "b2",
            focusImportTarget(
                candidates = listOf("b1", "b2", "b3"),
                visibleBookIds = setOf("b2", "b3"),
            ),
        )
    }

    /** 第一本被筛掉时退到第二本，而不是整批都定位不到。 */
    @Test
    fun `skips candidates hidden by the current filter and falls back to the next one`() {
        assertEquals(
            "new2",
            focusImportTarget(
                candidates = listOf("new1", "new2"),
                visibleBookIds = setOf("new2"),
            ),
        )
    }

    /** 候选全被筛掉：返回 null，调用方据此清 focus 并告知用户（不得静默残留）。 */
    @Test
    fun `returns null when every candidate is hidden`() {
        assertNull(
            focusImportTarget(
                candidates = listOf("new1", "new2"),
                visibleBookIds = setOf("other"),
            ),
        )
    }

    @Test
    fun `returns null when the batch imported nothing`() {
        assertNull(focusImportTarget(candidates = emptyList(), visibleBookIds = setOf("b1")))
    }

    @Test
    fun `returns null when nothing is visible`() {
        assertNull(focusImportTarget(candidates = listOf("b1"), visibleBookIds = emptySet()))
    }

    /**
     * 回归护栏：**只**在候选里挑，绝不选中候选外的可见书。
     *
     * 这正是旧行为的失败模式 —— 定位走的是全局未查看集合，一本上一批遗留的可见书
     * 会被选中。若有人把实现改回「从可见集合里随便挑」，这条断言会红。
     */
    @Test
    fun `never selects a visible book that is not in this batch candidates`() {
        assertNull(
            focusImportTarget(
                candidates = listOf("thisBatch"),
                visibleBookIds = setOf("staleFromPreviousBatch"),
            ),
        )
        assertEquals(
            "thisBatch",
            focusImportTarget(
                candidates = listOf("thisBatch"),
                visibleBookIds = setOf("staleFromPreviousBatch", "thisBatch"),
            ),
        )
    }

    // ===== focusIdsForBatch：与提示对应的批次一致才定位 =====

    @Test
    fun `returns this batch ids when the importer batch matches the prompt batch`() {
        assertEquals(
            listOf("new1", "new2"),
            focusIdsForBatch(
                successes = ImportBatchSuccesses(batchId = "batch-1", bookIds = listOf("new1", "new2")),
                batchId = "batch-1",
            ),
        )
    }

    /**
     * 核心判别：批次已经换代时**不得**返回新批次的 id。
     *
     * 场景：用户停留在第一批判的完成提示上，期间又开始了第二次导入；此时导入器里记的
     * 是第二批。若照抄导入器的 id，用户点旧提示就会被带去这一批根本没导入的书。
     * 旧实现（读 Compose 异步收集的最近一帧）无法排除这种错配。
     */
    @Test
    fun `returns nothing when the importer has moved on to a newer batch`() {
        val ids = focusIdsForBatch(
            successes = ImportBatchSuccesses(batchId = "batch-2", bookIds = listOf("fromSecondBatch")),
            batchId = "batch-1",
        )
        assertTrue("批次已换代时不得定位新批次的书，actual=$ids", ids.isEmpty())
    }

    /** 批次一致但成功 id 异常为空：返回空，调用方据此给出明确反馈而不是静默结束。 */
    @Test
    fun `returns nothing when the matching batch has an empty id list`() {
        val ids = focusIdsForBatch(
            successes = ImportBatchSuccesses(batchId = "batch-1", bookIds = emptyList()),
            batchId = "batch-1",
        )
        assertTrue("成功 id 为空也必须能被调用方识别出来，actual=$ids", ids.isEmpty())
    }

    /** 还没有跑过任何批次（batchId 为空串）时同样返回空。 */
    @Test
    fun `returns nothing when no batch has run yet`() {
        assertTrue(
            focusIdsForBatch(
                successes = ImportBatchSuccesses(),
                batchId = "batch-1",
            ).isEmpty(),
        )
    }

    // ===== awaitFocusTarget：有界等待，不误报也不挂起 =====

    /**
     * 书在「落库了但列表流还没显示」之后才出现时，必须等到它出现再定位。
     *
     * 旧实现在第一次读不到就断言「被筛选排除」，用户刚导入成功却被告知找不到。
     */
    @Test
    fun `waits until the newly imported book shows up in the list`() = runTest {
        var polls = 0
        val target = awaitFocusTarget(
            candidates = listOf("new1"),
            visibleBookIds = {
                polls++
                if (polls < 3) emptySet() else setOf("new1")
            },
        )

        assertEquals("new1", target)
        assertEquals("应当在第 3 次轮询读到它", 3, polls)
    }

    /**
     * **有界**：真的被筛掉时必须放弃，不能无限等待。
     *
     * 这条同时是可证伪的「不挂起」护栏：若实现改成 `while(true)`，`runTest` 会因
     * 虚拟时间无限推进而失败/超时，且轮询计数断言必然不成立。
     */
    @Test
    fun `gives up after a bounded number of attempts instead of hanging`() = runTest {
        var polls = 0
        val target = awaitFocusTarget(
            candidates = listOf("new1"),
            visibleBookIds = {
                polls++
                emptySet()
            },
            maxAttempts = 4,
            retryDelayMillis = 10L,
        )

        assertNull("等满上限仍未出现，必须判为不可达", target)
        assertEquals("轮询次数必须有界且恰好等于上限", 4, polls)
    }

    /** 已经可见时只读一次，不做多余等待（导入后立刻点「查看」是常见路径）。 */
    @Test
    fun `returns immediately when the target is already visible`() = runTest {
        var polls = 0
        val target = awaitFocusTarget(
            candidates = listOf("new1"),
            visibleBookIds = {
                polls++
                setOf("new1")
            },
        )

        assertEquals("new1", target)
        assertEquals("已可见不应有任何多余轮询", 1, polls)
    }

    /** 本批没有候选时立刻返回，不空转等待。 */
    @Test
    fun `empty candidates return null without polling`() = runTest {
        var polls = 0
        val target = awaitFocusTarget(
            candidates = emptyList(),
            visibleBookIds = {
                polls++
                setOf("whatever")
            },
        )

        assertNull(target)
        assertEquals("没有候选就不该轮询", 0, polls)
    }

    /** 等待期间可见集合变化时，必须按**最新**集合判定，而不是第一次读到的快照。 */
    @Test
    fun `re-reads visibility on every attempt instead of caching the first snapshot`() = runTest {
        val visible = mutableSetOf<String>()
        var polls = 0
        val target = awaitFocusTarget(
            candidates = listOf("new1"),
            visibleBookIds = {
                polls++
                if (polls == 3) visible += "new1"
                visible.toSet()
            },
        )

        assertEquals("new1", target)
        assertEquals(3, polls)
    }
}
