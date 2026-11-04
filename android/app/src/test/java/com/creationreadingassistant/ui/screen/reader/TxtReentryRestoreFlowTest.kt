package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TXT 恢复协程动态回归（CRA-TXT-RESTORE-TIMING-EVIDENCE-v2，对应 Codex 核查时序补证）。
 *
 * 继承CRA-TXT-REENTRY-FIX-v3的基础用例，并补充布局推进时序。
 *
 * TC-T1、TC-T2使用同一布局状态映射settle快照与StateFlow冷流：验证两次未落位后
 * 的后续推进，以及检查返回到重新订阅之间推进时冷订阅读取当前值。
 * TC-T3/T4检查同状态挂起、StateFlow当前值重播过滤。
 *
 * JVM范围：只证明受控布局状态下生产恢复函数的抽象时序逻辑。生产侧snapshotFlow的
 * Compose快照提交、conflate与帧调度，以及真实LazyListState行为尚未验证；不能据此
 * 认定设备上UI移动而数据库冻结的根因已证或已根治。
 */
class TxtReentryRestoreFlowTest {

    private fun units(total: Int, perUnit: Int): List<ReadingUnit> =
        (0 until total).map { i -> ReadingUnit(i, 0, "", i * perUnit, perUnit) }

    private val units = units(40, 68)
    private val totalChars = 40 * 68
    private val targetIndex = unitIndexForOffset(units, 2553) // = 37

    /** 受控布局/落位快照源：生产传 snapshotFlow(...).conflate()，测试推进这些流。 */
    private class Snapshot {
        val layout = MutableStateFlow(plainRestoreLayoutSnapshot(0, 0, 0)) // (totalItemsCount, first, last)
        val settle = MutableStateFlow(Triple(0, 0, true)) // (first, last, unitsEmpty)
        val scrollCalls = mutableListOf<Int>()
        var landOn: Int = 0 // 滚动后列表实际停留项（模拟吞滚动 = 0）
    }

    // ─── 原有 9 个测试（v3 基线，保持不变） ──────────────────────────────────────

    @Test
    fun `ready but unsettled does not rescroll on the same state - waits for real layout change`() {
        val s = Snapshot()
        // 布局先就绪（40 项、停 0），落位失败（landOn=0 ≠ target 37）
        s.layout.value = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1)
        s.landOn = 0
        var released = false
        runBlocking {
            withTimeout(10_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { s.scrollCalls.add(it) },
                        settleSnapshot = { s.settle.value },
                        layoutChanges = s.layout,
                    )
                }
                // 同状态（layout 未再变化）下协程必须挂起等待——不重复滚动
                assertTrue("首次滚动已执行", s.scrollCalls.size >= 1)
                assertEquals(listOf(targetIndex), s.scrollCalls)
                assertFalse("未落位不得释放 pending", released)
                // 模拟 Codex P1 场景：布局状态不变时不得再滚——推进虚拟时间/让出后仍只有 1 次滚动
                kotlinx.coroutines.yield()
                kotlinx.coroutines.yield()
                assertEquals("同状态不得重复滚动", 1, s.scrollCalls.size)
                // 布局真实变化 → 完成并落位（这次 landOn=37，落位快照同步推进）
                s.landOn = targetIndex
                s.layout.value = plainRestoreLayoutSnapshot(units.size, targetIndex, units.size - 1)
                s.settle.value = Triple(targetIndex, targetIndex, false)
                kotlinx.coroutines.yield()
                job.join()
            }
        }
        assertTrue("布局变化后必须完成并释放 pending", released)
        // CRA-JOINT-31-ACCEPT-CLOSURE-v3：观察/等待顺序修正后，循环在等到“真实布局更新”时
        // 先在该新布局上判定落位——本用例第二阶段把布局推进到 targetIndex（已落位），因此
        // 不再需要第二次滚动。原断言 [37,37] 锁的是旧实现“观察到变化即重滚”的行为，那正是
        // 本次要修的“观察到已落位更新却不消费”缺陷。实质语义（落位后释放 pending、同状态
        // 不重复滚动、未落位不得释放）保持不变。
        assertEquals("布局推进到目标后一次滚动即落位（不得在已落位的新布局上重复滚动）",
            listOf(targetIndex), s.scrollCalls)
    }

    @Test
    fun `landing reached on same position retry must release pending rather than waiting forever`() {
        val s = Snapshot()
        // 初始：列表已经在目标位置 targetIndex（例如重进或布局直接在目标项）
        s.layout.value = plainRestoreLayoutSnapshot(units.size, targetIndex, units.size - 1)
        var snapshotCalls = 0
        val settleProvider: suspend () -> Triple<Int, Int, Boolean> = {
            snapshotCalls++
            if (snapshotCalls == 1) {
                // 首次采样模拟旧快照/未落位
                Triple(0, 0, false)
            } else {
                // 第二次采样时真实 UI 已经处于目标单元
                Triple(targetIndex, units.size - 1, false)
            }
        }
        var released = false
        runBlocking {
            withTimeout(1_000) {
                released = runPlainRestoreLoop(
                    savedPlainOffset = 2553, savedPlainPercent = 0f,
                    totalChars = totalChars, units = units, pagedAbsOffset = -1,
                    scrollTo = { s.scrollCalls.add(it) },
                    settleSnapshot = settleProvider,
                    layoutChanges = s.layout,
                )
            }
        }
        assertTrue("相同位置重试后落位必须释放 pending，绝不得因布局不变永久等待超时", released)
    }


    @Test
    fun `bottom clamp on the last short unit is a legal landing`() {
        // 书尾短项：目标==最后一单元，clamp 后首项=38、末项=39（目标在屏内）。
        // v2 的 settled 只认首项==目标，会永久拒绝；v3 必须接受。
        val settle = Triple(38, units.lastIndex, false)
        assertTrue(
            plainRestoreSettled(
                firstVisibleItemIndex = settle.first,
                lastVisibleItemIndex = settle.second,
                targetItemIndex = units.lastIndex,
                unitsEmpty = false,
                isLastUnit = true,
            ),
        )
        // 目标不在末单元时，首项≠目标仍不算落位
        assertFalse(
            plainRestoreSettled(
                firstVisibleItemIndex = 10, lastVisibleItemIndex = 39,
                targetItemIndex = 37, unitsEmpty = false,
                isLastUnit = false,
            ),
        )
        // 完整流程：就绪 → 滚动 → clamp 落位 → 释放
        val s = Snapshot()
        s.layout.value = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1)
        s.landOn = 38 // clamp 后首项 38，末项 39
        var released = false
        runBlocking {
            withTimeout(10_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = units.last().charStart, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { s.scrollCalls.add(it) },
                        settleSnapshot = { Triple(38, units.lastIndex, false) },
                        layoutChanges = s.layout,
                    )
                }
                kotlinx.coroutines.yield()
                job.join()
            }
        }
        assertTrue("书尾 clamp 必须被确认为合法落位", released)
        assertEquals(listOf(units.lastIndex), s.scrollCalls)
    }

    @Test
    fun `cancellation while waiting leaves no scroll and no release for the new book`() {
        val s = Snapshot()
        // 未就绪状态启动 → 协程挂起在 layoutChanges.first() 等待
        var released = false
        runBlocking {
            withTimeout(10_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { s.scrollCalls.add(it) },
                        settleSnapshot = { s.settle.value },
                        layoutChanges = s.layout,
                    )
                }
                // 相同 units、相同偏移：此刻切书（取消旧协程）后喂"新书的就绪布局"——
                // 旧协程已被取消，不得消费、不得滚动、不得释放。
                job.cancel()
                s.layout.value = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1)
                kotlinx.coroutines.yield()
                assertTrue("等待中取消：不滚动", s.scrollCalls.isEmpty())
                assertFalse("等待中取消：不释放", released)
                job.join()
            }
        }
    }

    @Test
    fun `restore not completed never writes zero - completed allows normal save`() {
        // ① 未完成（挂起等待）时 pending=true → 0 位置保存被生产门控拦截
        assertFalse(
            "等待期间退出保存必须被拦截（DB 的 2553 不会被 0 覆盖）",
            canPersistNormalReadingProgress(initialPositionPending = true, temporaryInspection = false),
        )
        val s = Snapshot()
        s.layout.value = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1)
        s.landOn = targetIndex
        var released = false
        runBlocking {
            withTimeout(10_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { s.scrollCalls.add(it) },
                        settleSnapshot = { Triple(targetIndex, targetIndex, false) },
                        layoutChanges = s.layout,
                    )
                }
                kotlinx.coroutines.yield()
                job.join()
            }
        }
        // ② 完成后 pending=false → 正常保存放行，且从真实列表状态读出目标单元起点（≠0）
        assertTrue(released)
        assertTrue(canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = false))
        val state = androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = targetIndex, firstVisibleItemScrollOffset = 0)
        assertEquals(units[targetIndex].charStart, currentPlainListOffset(state, units, fallbackOffset = 0))
        assertTrue(units[targetIndex].charStart > 0)
    }

    @Test
    fun `zero-target fresh book passes through without waiting`() {
        val t = plainRestoreTarget(
            savedPlainOffset = 0, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = false,
        )
        assertEquals(0, t!!.itemIndex)
        assertEquals(0, t.targetOffset)
        assertNull(t.takeIf { it.targetOffset > 0 })
    }

    @Test
    fun `settled v4 - clamp only for last unit, middle-item edge rejected`() {
        // 正常落位：首项==目标
        assertTrue(
            plainRestoreSettled(firstVisibleItemIndex = 37, lastVisibleItemIndex = 38, targetItemIndex = 37, unitsEmpty = false, isLastUnit = false),
        )
        // 书尾 clamp：目标==末单元（isLastUnit）、首项<目标、末项==目标
        assertTrue(
            plainRestoreSettled(firstVisibleItemIndex = 38, lastVisibleItemIndex = 39, targetItemIndex = 39, unitsEmpty = false, isLastUnit = true),
        )
        // Codex P1 反例（40 单元 first=36/lastVisible=37/target=37）：目标不是末单元，
        // 只是恰好贴视口底边——必须拒绝（v3 会误判为已落位）
        assertFalse(
            plainRestoreSettled(firstVisibleItemIndex = 36, lastVisibleItemIndex = 37, targetItemIndex = 37, unitsEmpty = false, isLastUnit = false),
        )
        // 目标不可见：拒绝
        assertFalse(
            plainRestoreSettled(firstVisibleItemIndex = 10, lastVisibleItemIndex = 12, targetItemIndex = 37, unitsEmpty = false, isLastUnit = false),
        )
        // 目标==末单元但末可见项不是目标：clamp 条件不完整，拒绝
        assertFalse(
            plainRestoreSettled(firstVisibleItemIndex = 36, lastVisibleItemIndex = 38, targetItemIndex = 39, unitsEmpty = false, isLastUnit = true),
        )
        // 未挂载：拒绝
        assertFalse(
            plainRestoreSettled(firstVisibleItemIndex = 37, lastVisibleItemIndex = 37, targetItemIndex = 37, unitsEmpty = true, isLastUnit = true),
        )
    }

    @Test
    fun `effect keys explicitly include book and list identity - wiring guard`() {
        // B 的接入核对面（源码级）：恢复 effect 的 key 集合必须显式含 bid 与
        // plainListState。JVM 无法驱动真实 Compose 重组，本测试钉住接入事实；
        // 「key 失效真的取消协程」是 Compose 运行时行为，留给 MuMu 验证。
        val src = java.io.File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt")
            .readText()
        val keysBlock = src.substring(
            src.indexOf("// 纯文本恢复上次滚动位置"),
            src.indexOf(") {", src.indexOf("pendingInitialPositionState.value,", src.indexOf("// 纯文本恢复上次滚动位置"))),
        )
        assertTrue("恢复 effect key 必须含 bid（书籍身份）", Regex("\n        bid,").containsMatchIn(keysBlock))
        assertTrue("恢复 effect key 必须含 plainListState（列表身份）", Regex("\n        plainListState,").containsMatchIn(keysBlock))
    }

    @Test
    fun `nonzero target with layout not ready defers and keeps pending`() {
        val t = plainRestoreTarget(
            savedPlainOffset = 2553, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = false,
        )
        assertNull(t)
        assertFalse(canPersistNormalReadingProgress(initialPositionPending = true, temporaryInspection = false))
    }

    // ─── CRA-TXT-RESTORE-TIMING-EVIDENCE-v2 新增时序补证用例 ──────────────────

    /** 后续布局状态推进后，生产控制流等待变化并重试。 */
    @Test
    fun `TC_T1 later shared layout advancement retries after two unsettled samples`() = runBlocking {
        val state = MutableStateFlow(plainRestoreLayoutSnapshot(units.size, 0, units.size - 1))
        val secondSampleReached = CompletableDeferred<Unit>()
        var sampleCount = 0
        val scrollCalls = mutableListOf<Int>()
        val updater = launch {
            secondSampleReached.await()
            kotlinx.coroutines.yield()
            state.value = plainRestoreLayoutSnapshot(units.size, targetIndex, units.size - 1)
        }
        val restored = withTimeout(2_000) {
            runPlainRestoreLoop(
                savedPlainOffset = 2553, savedPlainPercent = 0f,
                totalChars = totalChars, units = units, pagedAbsOffset = -1,
                scrollTo = { scrollCalls.add(it) },
                settleSnapshot = {
                    sampleCount++
                    val snapshot = state.value.let { (_, first, last) -> Triple(first, last, false) }
                    if (sampleCount == 2) secondSampleReached.complete(Unit)
                    snapshot
                },
                layoutChanges = state.map { it },
            )
        }
        updater.join()
        assertTrue("共享布局状态后续落位应放行恢复", restored)
        // CRA-JOINT-31-ACCEPT-CLOSURE-v3：等到真实布局更新后直接在该布局上判定落位；
        // 本用例推进后的布局 (units.size, targetIndex, units.size-1) 已落位，故一次滚动即
        // 确认（旧断言 2 次滚动锁的是旧实现“观察到变化即重滚”）。实质语义（消费后续布局
        // 推进、落位放行）不变。
        assertEquals("初次和布局推进后各滚动一次", 1, scrollCalls.size)
        assertTrue("布局推进并确认落位需在第二次采样之后", sampleCount >= 3)
    }

    /** 更新发生在检查返回与layoutChanges冷流订阅之间时，订阅仍见到当前状态。 */
    @Test
    fun `TC_T2 state update between second check and cold subscription is observed`() = runBlocking {
        val state = MutableStateFlow(plainRestoreLayoutSnapshot(units.size, 0, units.size - 1))
        val secondSnapshotRead = CompletableDeferred<Unit>()
        val updater = launch {
            secondSnapshotRead.await()
            state.value = plainRestoreLayoutSnapshot(units.size, targetIndex, units.size - 1)
        }
        val scrollCalls = mutableListOf<Int>()
        var sampleCount = 0
        val restored = withTimeout(2_000) {
            runPlainRestoreLoop(
                savedPlainOffset = 2553, savedPlainPercent = 0f,
                totalChars = totalChars, units = units, pagedAbsOffset = -1,
                scrollTo = { scrollCalls.add(it) },
                settleSnapshot = {
                    sampleCount++
                    val snapshot = state.value.let { (_, first, last) -> Triple(first, last, false) }
                    if (sampleCount == 2) {
                        secondSnapshotRead.complete(Unit)
                        kotlinx.coroutines.yield()
                    }
                    snapshot
                },
                layoutChanges = state.map { it },
            )
        }
        updater.join()
        assertTrue("冷订阅应读取当前布局值，不丢失检查后的推进", restored)
        // CRA-JOINT-31-ACCEPT-CLOSURE-v3：本次等到的是冷订阅读到的当前布局
        // (units.size, targetIndex, units.size-1)，已落位——直接确认，无需重滚。
        // 实质语义（冷订阅不丢失检查后的推进、落位放行）不变。
        assertEquals("布局推进到目标后一次滚动即落位", 1, scrollCalls.size)
        assertTrue("冷订阅读到推进后需在第二次采样之后确认", sampleCount >= 3)
    }
    /**
     * TC-T3：快照确实未变化时，控制流真正挂起，不忙循环。
     *
     * 场景：
     * - 布局就绪但 settleSnapshot 永远返回未落位；
     * - layoutChanges 注入一次变化（触发重试），但 settleSnapshot 仍未落位；
     * - 再无变化 → 协程必须挂起。
     *
     * 目的：证明控制流在"两次采样均未落位 + layoutChanges 无新变化"时真正挂起，
     * 不产生忙循环（无限滚动），避免 Codex P1 的重订阅立即重滚问题在新版本中复现。
     *
     * 验收标准：向 layoutChanges 注入变化后，若第二次 settleSnapshot 仍未落位，
     * 协程应在下一次 layoutChanges 变化前挂起（不产生额外 scroll 调用）。
     */
    @Test
    fun `TC_T3 no busy loop when snapshot unchanged - coroutine suspends not rescrolls`() {
        val s = Snapshot()
        s.layout.value = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1) // 就绪，首项=0

        var scrollCount = 0
        // settleSnapshot 始终返回未落位（模拟吞滚动场景：UI 根本没到目标项）
        val neverSettled: suspend () -> Triple<Int, Int, Boolean> = {
            Triple(0, 0, false) // 始终 first=0，未落位
        }

        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { scrollCount++ },
                        settleSnapshot = neverSettled,
                        layoutChanges = s.layout,
                    )
                }
                // 首次滚动 + 两次采样均未落位 → 协程应挂起等待 layoutChanges 变化
                // 让出几次后，scroll 次数不应增加
                repeat(5) { kotlinx.coroutines.yield() }
                val scrollCountAfterFirstPass = scrollCount
                assertEquals("TC-T3：首次 scrollTo 恰好执行一次", 1, scrollCountAfterFirstPass)

                // 注入一次新布局变化（不落位，first=1）→ 触发第二轮重试
                s.layout.value = plainRestoreLayoutSnapshot(units.size, 1, units.size - 1)
                repeat(5) { kotlinx.coroutines.yield() }

                // 第二轮：又滚动一次（layout 变化触发重试），两次采样仍未落位，再次挂起
                val scrollCountAfterSecondLayout = scrollCount
                // 应恰好 2 次（第一轮 + 第二轮）
                assertEquals("TC-T3：第二次 layout 变化触发第二次 scrollTo", 2, scrollCountAfterSecondLayout)

                // 无新变化 → 协程必须挂起，不再产生滚动
                repeat(10) { kotlinx.coroutines.yield() }
                assertEquals("TC-T3：无新 layout 变化时不得产生额外 scrollTo（无忙循环）", 2, scrollCount)
                assertFalse("TC-T3：未落位时不得释放 pending", released)

                job.cancel()
                job.join()
            }
        }
    }

    /**
     * TC-T4：layoutChanges 首次发射（StateFlow 当前值重播）不触发假阳性重试。
     *
     * 场景：
     * - layoutChanges 是 MutableStateFlow，当前值 state-X；
     * - runPlainRestoreLoop 开始时 settleBefore = layoutChanges.first() = state-X；
     * - scrollTo 后 settleSnapshot① 未落位，yield，settleSnapshot② 未落位；
     * - 进入 layoutChanges.first { it != settleBefore = state-X }；
     * - 此时 layoutChanges 当前值仍为 state-X → StateFlow.first { it != state-X }
     *   必须挂起，不立即返回（因为当前值等于 before，过滤条件不满足）；
     * - 随后注入 state-Y → 应触发重试并落位。
     *
     * 目的：证明控制流中 `layoutChanges.first { it != before }` 的 before 过滤
     * 确实能阻止 StateFlow 的当前值重播导致的假阳性立即返回。
     * 若此过滤失效，控制流会在 layoutChanges 值不变时也立即重进循环（忙循环）。
     *
     * JVM 范围：StateFlow.first { pred } 的行为是确定的：若当前值不满足 pred 则挂起等待。
     * 本用例验证生产控制流中 before 赋值和过滤是正确的。
     */
    @Test
    fun `TC_T4 layoutChanges initial value replay does not bypass before filter`() {
        val stateX = plainRestoreLayoutSnapshot(units.size, 0, units.size - 1)
        val stateY = plainRestoreLayoutSnapshot(units.size, targetIndex, units.size - 1)
        val layoutChanges = MutableStateFlow(stateX) // 初始值 state-X

        var snapshotCallCount = 0
        // 前两次采样未落位；第三次（第二轮滚动后）落位
        val settleProvider: suspend () -> Triple<Int, Int, Boolean> = {
            snapshotCallCount++
            if (snapshotCallCount <= 2) Triple(0, 0, false)
            else Triple(targetIndex, targetIndex, false)
        }

        val scrollCalls = mutableListOf<Int>()
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = { scrollCalls.add(it) },
                        settleSnapshot = settleProvider,
                        layoutChanges = layoutChanges,
                    )
                }
                // 首次滚动已发生，两次采样未落位，现在协程应挂起等待 layout 变化
                repeat(5) { kotlinx.coroutines.yield() }
                assertEquals("TC-T4：首次滚动恰好 1 次", 1, scrollCalls.size)
                assertFalse("TC-T4：未落位时未释放", released)

                // 验证：此时 layoutChanges 仍为 state-X，协程确实挂起（不额外滚动）
                repeat(5) { kotlinx.coroutines.yield() }
                assertEquals("TC-T4：state-X 重播不触发额外滚动（before 过滤生效）", 1, scrollCalls.size)

                // 注入 state-Y → 触发第二轮，settleSnapshot③ 落位
                layoutChanges.value = stateY
                repeat(5) { kotlinx.coroutines.yield() }
                job.join()
            }
        }
        assertTrue("TC-T4：state-Y 注入后最终落位并释放 pending", released)
        // CRA-JOINT-31-ACCEPT-CLOSURE-v3：state-Y = (units.size, targetIndex, units.size-1)
        // 已落位；等到该更新后直接确认落位即返回，无需再滚一次（旧断言 2 次锁的是旧实现
        // “观察到变化即重滚”）。实质语义（state-X 重播不触发假阳性、before 过滤生效、
        // state-Y 后落位放行）不变。
        assertEquals("TC-T4：state-Y 落位后一次滚动即放行", 1, scrollCalls.size)
    }
}
