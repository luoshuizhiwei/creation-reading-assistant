package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.ReaderExitBoundary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 任务 197-v1：统一保存边界与真实回归测试。
 *
 * 核心目标与验收合同：
 * 1. 直接驱动真实生产类 [ReaderExitBoundary] 与 [ReaderLifecyclePersistGate]，
 *    移除 196 中多余自建的 isTransitioning/hasNavigated 标志与重复算法；
 * 2. 精确控制被 await 的保存句柄：真实捕获 dispatchExitSave() 派发的保存句柄，
 *    而非孤立预置的 D0 或旁路句柄；
 * 3. 严格断言执行全序：dispatchExitSave -> awaitProgressSaves -> onDrained -> action；
 * 4. 覆盖 11 项场景：未完成不导航不标记、失败不导航且异常可观察、取消语义区分、
 *    成功全序恰一次、真实生产边界防重入、前台重入复位、ON_RESUME 不使在途转换重复导航，
 *    以及门控自身状态迁移。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTemporaryEntryBoundaryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 可受控的保存协调器，完全对应真实生产环境中的数据保存层。
     * 当 dispatchExitSave 执行时生成 Deferred，awaitProgressSaves 等待所有未完成 Deferred。
     */
    private class ControllableSaveCoordinator {
        val inFlightSaves = mutableListOf<CompletableDeferred<Unit>>()
        var dispatchCount = 0
            private set

        fun dispatchSave(): CompletableDeferred<Unit> {
            dispatchCount++
            val deferred = CompletableDeferred<Unit>()
            inFlightSaves += deferred
            return deferred
        }

        suspend fun awaitProgressSaves() {
            for (deferred in inFlightSaves.toList()) {
                deferred.await()
            }
        }

        val latestInFlight: CompletableDeferred<Unit>?
            get() = inFlightSaves.lastOrNull()

        val pendingCount: Int
            get() = inFlightSaves.count { !it.isCompleted }
    }

    // ==========================================
    // 门控自身状态迁移验证（合并 195-r1 定向用例）
    // ==========================================

    @Test
    fun gate_initialState_allowsPauseOrStop_andDispose() {
        val gate = ReaderLifecyclePersistGate()
        assertTrue("初始状态应允许暂停/停止持久化", gate.shouldPersistOnPauseOrStop())
        assertTrue("初始状态应允许销毁持久化", gate.shouldPersistOnDispose())
    }

    @Test
    fun gate_onPauseOrStop_blocksSubsequentLifecyclePersist() {
        val gate = ReaderLifecyclePersistGate()
        gate.onPauseOrStop()
        assertFalse("暂停/停止后应阻断生命周期持久化", gate.shouldPersistOnPauseOrStop())
        assertFalse("暂停/停止后应阻断销毁持久化", gate.shouldPersistOnDispose())
    }

    @Test
    fun gate_onResume_resetsGate_allowingSubsequentPersist() {
        val gate = ReaderLifecyclePersistGate()
        gate.onPauseOrStop()
        assertFalse(gate.shouldPersistOnPauseOrStop())

        gate.onResume()
        assertTrue("回到前台复位门控后应允许暂停持久化", gate.shouldPersistOnPauseOrStop())
        assertTrue("回到前台复位门控后应允许销毁持久化", gate.shouldPersistOnDispose())
    }

    // ==========================================
    // 真实生产边界 requestExitWithAction 核心合同验证
    // ==========================================

    @Test
    fun temporaryEntry_whenSavePending_mustNotNavigateOrMarkGate() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()
        val observedFailures = mutableListOf<Throwable>()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc1&navigationMode=temporary"

        // 触发真实生产 requestExitWithAction
        boundary.requestExitWithAction(
            action = { navigations += targetRoute },
            onFailure = { observedFailures += it },
        )
        runCurrent()

        // 此时 dispatchExitSave 已被调用，产生在途保存句柄
        assertEquals(1, coordinator.dispatchCount)
        assertEquals(1, coordinator.pendingCount)
        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)

        // 在途保存未完成：不应执行 action，不应标记门控
        assertEquals(0, navigations.size)
        assertTrue("在途保存未完成前，门控不得关闭", gate.shouldPersistOnPauseOrStop())
        assertEquals(0, observedFailures.size)

        // 推进在途保存完成并驱动协程就绪队列
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        // 排空完成后：action 恰执行一次，门控关闭
        assertEquals(1, navigations.size)
        assertEquals(targetRoute, navigations.first())
        assertFalse("排空完成后门控应置位关闭，阻断切后台重复写", gate.shouldPersistOnPauseOrStop())
        assertEquals(0, observedFailures.size)
    }

    @Test
    fun temporaryEntry_whenSaveFails_mustNotNavigate_mustExposeOriginalException_andMustNotMarkGate() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()
        val observedFailures = mutableListOf<Throwable>()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc2&navigationMode=temporary"

        boundary.requestExitWithAction(
            action = { navigations += targetRoute },
            onFailure = { observedFailures += it },
        )
        runCurrent()

        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)

        // 模拟底层存储写入失败（如数据库锁定）
        val expectedError = IllegalStateException("database locked or disk io error")
        activeSave!!.completeExceptionally(expectedError)
        runCurrent()
        advanceUntilIdle()

        // 验证失败合同：
        // 1) 严禁执行 action（停留在原页）
        assertEquals("保存失败时不得执行跳转动作", 0, navigations.size)
        // 2) 严禁置位门控（避免假装保存成功而导致后续生命周期丢失）
        assertTrue("保存失败时门控不得置位关闭", gate.shouldPersistOnPauseOrStop())
        // 3) 原始异常必须完整透传给 onFailure，供 UI 展示提示
        assertEquals(1, observedFailures.size)
        val reported = observedFailures.first()
        assertTrue("必须保留原始异常类型", reported is IllegalStateException)
        assertEquals("database locked or disk io error", reported.message)
    }

    @Test
    fun temporaryEntry_whenSaveCancelledByStorage_mustNotNavigate_andMustSurfaceCancellation() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()
        val observedFailures = mutableListOf<Throwable>()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc3&navigationMode=temporary"

        boundary.requestExitWithAction(
            action = { navigations += targetRoute },
            onFailure = { observedFailures += it },
        )
        runCurrent()

        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)

        // 模拟保存句柄被底层取消
        val cancellation = CancellationException("storage job cancelled by pool")
        activeSave!!.cancel(cancellation)
        runCurrent()
        advanceUntilIdle()

        // 保存被取消视同保存未成功：不得跳转，门控不关闭，异常透传
        assertEquals("保存被取消时不得跳转", 0, navigations.size)
        assertTrue("保存被取消时门控不得置位关闭", gate.shouldPersistOnPauseOrStop())
        assertEquals(1, observedFailures.size)
        val reported = observedFailures.first()
        assertTrue(reported is CancellationException)
        assertEquals("storage job cancelled by pool", reported.message)
    }

    @Test
    fun temporaryEntry_whenCallingScopeCancelled_mustNotNavigateOrMarkGate() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()
        val observedFailures = mutableListOf<Throwable>()

        val parentJob = Job()
        val customScope = CoroutineScope(testDispatcher + parentJob)

        val boundary = ReaderExitBoundary(
            scope = customScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc4&navigationMode=temporary"

        boundary.requestExitWithAction(
            action = { navigations += targetRoute },
            onFailure = { observedFailures += it },
        )
        runCurrent()

        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)

        // 外部 Scope 被销毁（例如页面销毁）
        parentJob.cancel(CancellationException("screen destroyed"))
        runCurrent()

        // 此时即便存储层完成，已取消的 Scope 也不得执行 action 或 mark gate
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals("调用方协程取消后严禁跳转", 0, navigations.size)
        assertTrue("调用方协程取消后门控保持未关闭", gate.shouldPersistOnPauseOrStop())
    }

    @Test
    fun temporaryEntry_whenSaveSucceeds_mustDrainBeforeMarkingGate_andNavigateExactlyOnce() = runTest(testDispatcher) {
        val trace = mutableListOf<String>()
        val gate = ReaderLifecyclePersistGate()
        val coordinator = ControllableSaveCoordinator()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = {
                trace += "dispatchExitSave"
                coordinator.dispatchSave()
            },
            awaitProgressSaves = {
                trace += "awaitProgressSaves"
                coordinator.awaitProgressSaves()
            },
            onDrained = {
                trace += "onDrained"
                gate.onPauseOrStop()
            },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc5&navigationMode=temporary"
        var actionExecutionCount = 0

        boundary.requestExitWithAction(
            action = {
                trace += "action"
                actionExecutionCount++
            },
            onFailure = { trace += "onFailure" },
        )
        runCurrent()

        // 校验初次同步派发
        assertEquals(listOf("dispatchExitSave", "awaitProgressSaves"), trace)
        assertEquals(0, actionExecutionCount)
        assertTrue(gate.shouldPersistOnPauseOrStop())

        // 在途排空
        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        // 校验全序严格一致：dispatchExitSave -> awaitProgressSaves -> onDrained -> action
        val expectedTrace = listOf("dispatchExitSave", "awaitProgressSaves", "onDrained", "action")
        assertEquals("必须严格遵循全序顺序：派发 -> 排空 -> 标记门控 -> 执行动作", expectedTrace, trace)
        assertEquals("动作必须恰好执行一次", 1, actionExecutionCount)
        assertFalse("排空完成后门控应关闭", gate.shouldPersistOnPauseOrStop())
    }

    @Test
    fun temporaryEntry_duplicateOrRapidClicks_mustNotCauseDuplicateNavigationOrDispatches() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val route1 = "reader/book-1?sourceLocator=loc-1&navigationMode=temporary"
        val route2 = "reader/book-1?sourceLocator=loc-2&navigationMode=temporary"

        // 第一次点击
        boundary.requestExitWithAction(action = { navigations += route1 })
        runCurrent()
        assertEquals(1, coordinator.dispatchCount)
        assertEquals(0, navigations.size)

        // 在排空在途期间快速第二次点击（防重入）
        boundary.requestExitWithAction(action = { navigations += route2 })
        runCurrent()

        // 必须被生产边界自身防重入拦截，不得重复派发保存
        assertEquals("在途期间防重入：不得重复派发保存", 1, coordinator.dispatchCount)
        assertEquals(0, navigations.size)

        // 完成首次在途保存
        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals("仅首次合法点击执行导航动作", 1, navigations.size)
        assertEquals(route1, navigations.first())

        // 导航已完成后再次点击：navigated 状态已置位，直接返回
        boundary.requestExitWithAction(action = { navigations += route2 })
        runCurrent()
        advanceUntilIdle()

        assertEquals("已导航状态下再次点击应被完全忽略", 1, coordinator.dispatchCount)
        assertEquals("已导航状态下再次点击不得重复执行动作", 1, navigations.size)
    }

    @Test
    fun temporaryEntry_lifecycleDeduplicationAndResumeReset_mustPreserveContract() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        val navigations = mutableListOf<String>()

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        val targetRoute = "reader/book-1?sourceLocator=loc-clean&navigationMode=temporary"
        boundary.requestExitWithAction(action = { navigations += targetRoute })
        runCurrent()

        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, navigations.size)
        assertFalse(gate.shouldPersistOnPauseOrStop())

        // 步骤 A：模拟页面退入后台（ON_PAUSE, ON_STOP）
        // 门控返回 false，成功阻断切后台的重复写入，保护 S0 记录不被刷新
        val shouldPersistOnPause = gate.shouldPersistOnPauseOrStop()
        assertFalse("切后台时门控阻断重复保存", shouldPersistOnPause)

        // 步骤 B：模拟用户后续从临时页面返回普通阅读器（ON_RESUME）
        boundary.onResume()
        gate.onResume()

        // 步骤 C：门控与边界状态应完全复位，允许后续合法操作与退出
        assertTrue("回到前台复位门控后应允许后续保存", gate.shouldPersistOnPauseOrStop())

        // 再次触发退出，应正常允许执行
        val returnRoute = "shelf"
        boundary.requestExitWithAction(action = { navigations += returnRoute })
        runCurrent()

        val secondSave = coordinator.latestInFlight
        assertNotNull(secondSave)
        secondSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals(2, navigations.size)
        assertEquals(returnRoute, navigations.last())
    }

    @Test
    fun temporaryEntry_onResumeDuringInFlightTransition_doesNotCauseDuplicateNavigation() = runTest(testDispatcher) {
        val coordinator = ControllableSaveCoordinator()
        val gate = ReaderLifecyclePersistGate()
        var actionCallCount = 0

        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { coordinator.dispatchSave() },
            awaitProgressSaves = { coordinator.awaitProgressSaves() },
            onDrained = { gate.onPauseOrStop() },
        )

        // 发起转换请求
        boundary.requestExitWithAction(action = { actionCallCount++ })
        runCurrent()

        val activeSave = coordinator.latestInFlight
        assertNotNull(activeSave)
        assertEquals(1, coordinator.dispatchCount)
        assertEquals(0, actionCallCount)

        // 在途期间发生 ON_RESUME
        boundary.onResume()
        runCurrent()

        // 在途的保存未完成前，action 仍为 0
        assertEquals(0, actionCallCount)

        // 推进在途保存完成
        activeSave!!.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        // 在途保存完成后，动作恰好执行一次，不会重复导航
        assertEquals("在途转换遭遇 ON_RESUME 后，保存完成仍应恰好执行一次动作", 1, actionCallCount)
        assertFalse(gate.shouldPersistOnPauseOrStop())
    }
}
