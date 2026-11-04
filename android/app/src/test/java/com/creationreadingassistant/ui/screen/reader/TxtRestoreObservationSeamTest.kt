package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务10-v3 定向回归：**真实观察接缝**下恢复循环为何未消费有效布局更新。
 *
 * 生产接缝事实（ReaderProgressEffects 恢复 effect）：
 *  - `settleSnapshot` 与 `layoutChanges` 读取的是**同一个** `plainListState.layoutInfo`；
 *  - `scrollTo` = `plainListState.scrollToItem(i)`，其布局**在下一帧测量后**才反映；
 *  - `layoutChanges` 是 `snapshotFlow{...}.conflate()`，生产由 Compose 帧推进。
 *
 * 因此本测试用**同一个共享布局状态**同时喂 `settleSnapshot` 与 `layoutChanges`，
 * 并以“帧”推进（`frame()`）模拟测量，而不是手工排列两个独立探针的返回值。
 *
 * 旧实现的缺陷：循环在 `scrollTo` 后**立即**采样（尚未测量），判未落位；随后
 * 以 `settleBefore`（滚动前捕获）为基线等待变化——但 `settleBefore` 已被本次滚动
 * 在下一次采样前更新，导致等待基线漂移，真实布局更新被当作“已消费”，循环要么
 * 重复滚动要么永久挂起，`pendingInitialPosition` 永不解除。
 */
class TxtRestoreObservationSeamTest {

    private fun units(total: Int, perUnit: Int): List<ReadingUnit> =
        (0 until total).map { i -> ReadingUnit(i, 0, "", i * perUnit, perUnit) }

    private val units = units(40, 68)
    private val totalChars = 40 * 68
    private val targetIndex = unitIndexForOffset(units, 2553) // = 37

    /**
     * 模拟真实 LazyListState 的布局状态：一次 `scrollToItem(i)` 请求在若干帧后被测量应用。
     * 生产 `settleSnapshot` 与 `layoutChanges` 都读取这**同一个** `layout`。
     *
     * @param framesToApply 滚动请求到被测量之间的帧数（模拟 Compose 一帧延迟）。
     * @param clampTo 若请求无法把目标置顶（如目标项高度不足），测量后停在此项（模拟 clamp）。
     */
    private inner class Scaffold(
        private val framesToApply: Int = 1,
        private val clampTo: Int? = null,
    ) {
        // (totalItemsCount, firstVisibleItemIndex, lastVisibleItemIndex)
        val layout = MutableStateFlow(plainRestoreLayoutSnapshot(units.size, 0, units.size - 1))
        var pendingTarget: Int? = null
        private var framesSinceRequest = 0
        val scrollCalls = mutableListOf<Int>()

        /** 生产 settleSnapshot：直接读 layoutInfo（同一 layout）。 */
        fun settleSnapshot(): Triple<Int, Int, Boolean> {
            val (total, first, last) = layout.value
            return Triple(first, last, total == 0)
        }

        /** 生产 layoutChanges：snapshotFlow{ (total, first, last) }。 */
        fun layoutChanges() = layout.map { it }

        /** 生产 scrollTo：请求滚动；真正的布局变更在 frame() 后才出现。 */
        fun scrollTo(index: Int) {
            scrollCalls.add(index)
            pendingTarget = index
            framesSinceRequest = 0
        }

        /** 推进一帧：若存在未应用的滚动请求且到帧，则应用测量。 */
        fun frame() {
            val t = pendingTarget ?: return
            framesSinceRequest++
            if (framesSinceRequest >= framesToApply) {
                val applied = clampTo ?: t
                layout.value = plainRestoreLayoutSnapshot(units.size, applied, units.size - 1)
                pendingTarget = null
            }
        }
    }

    private fun runLoop(scaffold: Scaffold, timeoutMs: Long = 2_000): Pair<Boolean, List<Int>> {
        var released = false
        runBlocking {
            withTimeout(timeoutMs) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { scaffold.scrollTo(it) },
                        settleSnapshot = { scaffold.settleSnapshot() },
                        layoutChanges = scaffold.layoutChanges(),
                    )
                }
                // 周期推进“帧”，并给协程让出调度——模拟 Compose 在滚动后若干帧测量并提交。
                repeat(30) {
                    kotlinx.coroutines.yield()
                    scaffold.frame()
                    kotlinx.coroutines.yield()
                }
                job.join()
            }
        }
        return released to scaffold.scrollCalls
    }

    @Test
    fun `seam - scroll applied one frame later must still settle and release`() {
        // 生产语义：scrollToItem 后下一帧才测量到目标。旧实现在同帧采样失败后
        // 以漂移的基线等待，导致真实更新被误判 → 永不释放。
        val s = Scaffold(framesToApply = 1)
        val (released, calls) = runLoop(s)
        assertTrue("滚动在下一帧被测量后必须落位并释放 pending", released)
        assertEquals("落位仅需一次滚动，不得重复", 1, calls.size)
    }

    @Test
    fun `seam - late layout update after several frames is consumed`() {
        val s = Scaffold(framesToApply = 3)
        val (released, calls) = runLoop(s)
        assertTrue("数帧后的真实布局更新必须被消费并落位", released)
        assertEquals("落位仅需一次滚动", 1, calls.size)
    }
}
