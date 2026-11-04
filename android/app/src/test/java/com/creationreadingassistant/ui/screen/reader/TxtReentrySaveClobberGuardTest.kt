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
 * CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v1 —— 两处保存接缝的定向回归（红→绿）。
 *
 * 覆盖本轮设备实测定位的两个真实缺陷：
 *
 * 1) **恢复落位循环等待流与判定源错帧**（ReaderProgressEffects.layoutChanges）：
 *    `settleSnapshot` 取 `layoutInfo.visibleItemsInfo`（测量结果），而 `layoutChanges`
 *    原取 `firstVisibleItemIndex`（滚动请求位置）。两源相差一帧 → 循环「等到变化就重滚」
 *    却永远读不到「已落位」，列表稳定后永久挂起、`pendingInitialPosition` 永不解除、
 *    滚动保存被门控拦截。回归证明：当两源跨帧错位时（`crossFrameDivergent` 模型）旧行为
 *    不落位，同源（修复后）才落位并释放。
 *
 * 2) **销毁用过渡布局重复落库覆盖权威退出值**（ReaderPlatformEffects.onDispose）：
 *    ON_PAUSE/ON_STOP 已在权威布局下落过位置后，onDispose 又按销毁瞬间的更窄视口重测、
 *    覆盖成错误偏移。回归证明：[shouldPersistOnDispose] 在已由生命周期落库时返回 false。
 *
 * 全部纯 JVM，不启设备、不构建 APK。
 */
class TxtReentrySaveClobberGuardTest {

    private fun units(total: Int, perUnit: Int): List<ReadingUnit> =
        (0 until total).map { i -> ReadingUnit(i, 0, "", i * perUnit, perUnit) }

    private val units = units(40, 68)
    private val totalChars = 40 * 68
    private val targetIndex = unitIndexForOffset(units, 2553) // = 37

    /**
     * 模拟「等待流与判定源跨帧错位」的旧生产布局状态：
     * `requestedIndex`（等等待流读，`scrollToItem` 立即写入）与 `measuredIndex`
     * （等判定读，下一帧才测量）在滚动请求后短暂不同。
     * [sameSource] = true 表示修复后两者同源（都读测量结果）。
     */
    private inner class Scaffold(
        private val framesToApply: Int = 1,
        private val sameSource: Boolean,
    ) {
        // 等待流源：请求位置（scrollToItem 立即生效）
        val requestLayout = MutableStateFlow(plainRestoreLayoutSnapshot(units.size, 0, units.size - 1))
        // 判定源：测量位置（若干帧后才反映）
        val measureLayout = MutableStateFlow(plainRestoreLayoutSnapshot(units.size, 0, units.size - 1))
        var pendingTarget: Int? = null
        private var framesSinceRequest = 0
        val scrollCalls = mutableListOf<Int>()

        /** 判定源。修复后 = 测量结果；错位模型 = 同样取测量结果（判定本身不变）。 */
        fun settleSnapshot(): Triple<Int, Int, Boolean> {
            val (total, first, last) = measureLayout.value
            return Triple(first, last, total == 0)
        }

        /**
         * 等待流。修复后 = 与判定同源（measureLayout）；
         * 错位模型 = requestLayout（旧缺陷：跨帧错位）。
         */
        fun layoutChanges() = (if (sameSource) measureLayout else requestLayout).map { it }

        fun scrollTo(index: Int) {
            scrollCalls.add(index)
            pendingTarget = index
            framesSinceRequest = 0
            // 请求位置立即写入（模拟 scrollToItem 立即更新 firstVisibleItemIndex）
            requestLayout.value = plainRestoreLayoutSnapshot(units.size, index, units.size - 1)
        }

        fun frame() {
            val t = pendingTarget ?: return
            framesSinceRequest++
            if (framesSinceRequest >= framesToApply) {
                measureLayout.value = plainRestoreLayoutSnapshot(units.size, t, units.size - 1)
                pendingTarget = null
            }
        }
    }

    private fun runLoop(scaffold: Scaffold, timeoutMs: Long = 1_500): Pair<Boolean, List<Int>> {
        var released = false
        runBlocking {
            runCatching {
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
                    repeat(30) {
                        kotlinx.coroutines.yield()
                        scaffold.frame()
                        kotlinx.coroutines.yield()
                    }
                    job.join()
                }
            }
        }
        return released to scaffold.scrollCalls
    }

    @Test
    fun `red - cross-frame divergent wait stream never settles within bound`() {
        // 旧缺陷：等待流读请求位置（scrollToItem 立即写入），判定读测量位置（滞后）。
        // 请求值首次到目标后不再变化，而判定仍读旧测量值 → 循环第二次等「请求变化」
        // 时永久挂起（即便测量随后跟上也不会被再检查），落位永不成立（释放失败）。
        val s = Scaffold(framesToApply = 3, sameSource = false)
        val (released, _) = runLoop(s)
        assertFalse("错帧模型下旧行为不得落位（证明缺陷）", released)
    }

    @Test
    fun `green - same-source wait stream settles and releases once`() {
        val s = Scaffold(framesToApply = 3, sameSource = true)
        val (released, calls) = runLoop(s)
        assertTrue("同源后必须落位并释放 pending", released)
        assertEquals("落位仅需一次滚动", 1, calls.size)
    }

    @Test
    fun `dispose persist skipped after lifecycle persist`() {
        // 已由 ON_PAUSE/ON_STOP 权威落库 → 销毁不得再用过渡布局覆盖。
        assertFalse("已由生命周期落库时 onDispose 必须跳过", shouldPersistOnDispose(true))
        // 未经生命周期落库（同 Activity 内路由离开）→ onDispose 仍需落库。
        assertTrue("未经生命周期落库时 onDispose 必须落库", shouldPersistOnDispose(false))
    }
}
