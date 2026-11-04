package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import com.creationreadingassistant.ui.navigation.decodeSourceLocatorFromRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1-I 重写：临时查阅统一 LIFO 返回动作回归。
 *
 * 原测试（BackHandler 优先级、返回按钮可见性、回调调用次数）复述了生产 `when` 分支与
 * `&&` 表达式、或断言恒真的布尔字面量（`assertTrue(true && true)`），属于伪测试：即使生产
 * 代码被改坏也照样通过。这里改为直接调用生产函数 [temporaryReturnRouteStep] —— 返回按钮
 * （ReturnToReading）、顶栏 Back、系统 Back 三者共用的唯一返回入口 —— 并用真实
 * [TemporaryReadingNavigationViewModel] 驱动状态，断言真实产出的 route 字符串。
 */
class ReaderTemporaryBackTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    private fun coordinatorWithNormalReading(): TemporaryReadingNavigationViewModel =
        TemporaryReadingNavigationViewModel().apply { recordNormalReading(target("book-a", 100)) }

    @Test
    fun `temporary return from single level goes back to normal reading route`() {
        val vm = coordinatorWithNormalReading()
        vm.beginTemporaryInspection(target("book-b", 200))

        val route = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm)

        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-a?sourceLocator="))
        assertFalse(route.contains("navigationMode="))
        assertEquals(100, decodeSourceLocatorFromRoute(route)?.legacyOffset)
    }

    @Test
    fun `temporary return from intermediate level keeps temporary mode`() {
        val vm = coordinatorWithNormalReading()
        vm.beginTemporaryInspection(target("book-b", 200))
        vm.beginTemporaryInspection(target("book-c", 300))

        val first = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm)
        assertNotNull(first)
        assertTrue(first!!.startsWith("reader/book-b?sourceLocator="))
        assertTrue(first.contains("navigationMode=temporary"))

        val second = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm)
        assertNotNull(second)
        assertTrue(second!!.startsWith("reader/book-a?sourceLocator="))
        assertFalse(second.contains("navigationMode="))
    }

    @Test
    fun `normal reading mode never fabricates a temporary return route`() {
        val vm = coordinatorWithNormalReading()
        vm.beginTemporaryInspection(target("book-b", 200))

        assertNull(temporaryReturnRouteStep(ReaderNavigationMode.NORMAL, vm))
        // 且不消费协调器状态：普通阅读的返回仍由 NavController 负责。
        assertTrue(vm.hasReturnableTarget)
    }

    @Test
    fun `empty return stack preserves original exit-reader behaviour`() {
        val vm = coordinatorWithNormalReading()
        assertNull(temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm))
    }

    @Test
    fun `missing coordinator preserves original exit-reader behaviour`() {
        assertNull(temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, null))
    }

    @Test
    fun `return button and back share one LIFO action producing identical routes`() {
        // 返回按钮与 Back 都通过同一生产函数取目标；相同起始状态下二者必须得到完全一致的 route，
        // 且各自消费一层栈（互不共享状态）。
        val vmForBack = coordinatorWithNormalReading().apply { beginTemporaryInspection(target("book-b", 200)) }
        val vmForButton = coordinatorWithNormalReading().apply { beginTemporaryInspection(target("book-b", 200)) }

        val backRoute = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vmForBack)
        val buttonRoute = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vmForButton)

        assertNotNull(backRoute)
        assertEquals(backRoute, buttonRoute)
        assertEquals(100, decodeSourceLocatorFromRoute(backRoute!!)?.legacyOffset)
        // 两个协调器都各自消费了一层（栈已空），说明动作确实执行了一次 LIFO 返回。
        assertFalse(vmForBack.hasReturnableTarget)
        assertFalse(vmForButton.hasReturnableTarget)
    }

    // ── 207-r2：未提交（失败/取消）route 的返回目标 = 还原已提交会话，不消费合法栈 ──
    // 背景：临时查阅 route 以 launchSingleTop 进入时，Navigation 2.5.1 会替换栈顶同 destination
    // 的旧 reader entry；被替换的原 reader 不在 NavController 返回栈内，未提交 route 若交还
    // popBackStack 会退到 reader 域外。生产契约：把协调器仍持有的已提交会话还原为导航路径。

    /** 207-r2 辅助：经真实请求生命周期把 T1 提交进会话（登记 → 加载成功 → commit）。 */
    private fun commitTemporary(vm: TemporaryReadingNavigationViewModel, bookId: String, offset: Int): Long {
        val requestId = vm.requestTemporaryInspection(target(bookId, offset))
        assertNotNull(requestId)
        assertTrue(vm.commitPendingInspection(requestId!!, bookId))
        return requestId
    }

    @Test
    fun `uncommitted second-level request restores committed session without consuming stack`() {
        val vm = coordinatorWithNormalReading()
        commitTemporary(vm, "book-b", 200) // T1 真实提交：active=B，LIFO=[A]
        // T2 只登记未提交（加载失败/取消）。
        val pendingT2 = vm.requestTemporaryInspection(target("book-c", 300))
        assertNotNull(pendingT2)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-c", 300),
            ownedRequestId = pendingT2,
        )

        // 还原目标是已提交会话 T1（book-b），且栈非空 → 继续以临时模式呈现。
        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-b?sourceLocator="))
        assertTrue(route.contains("navigationMode=temporary"))
        assertEquals(200, decodeSourceLocatorFromRoute(route)?.legacyOffset)
        // 关键：LIFO 与已提交会话分毫不动 —— 未提交 route 无权推进协调器。
        assertTrue(vm.hasReturnableTarget)
        assertEquals(1, vm.state.value.temporaryReturnStack.size)
        assertEquals("book-b", vm.state.value.active?.bookId)
        // 本 route 的在途请求已按 ownedRequestId 作废。
        assertNull(vm.pendingInspection.value)
    }

    @Test
    fun `uncommitted first-level pending failure restores normal reading route`() {
        val vm = coordinatorWithNormalReading() // active=A，LIFO=[]
        val pendingT1 = vm.requestTemporaryInspection(target("book-b", 200))
        assertNotNull(pendingT1)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-b", 200),
            ownedRequestId = pendingT1,
        )

        // 栈空 → 还原为普通阅读处 route（不带 navigationMode），普通阅读位置仍是 A。
        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-a?sourceLocator="))
        assertFalse(route.contains("navigationMode="))
        assertEquals(100, decodeSourceLocatorFromRoute(route)?.legacyOffset)
        assertFalse(vm.hasReturnableTarget)
        assertEquals("book-a", vm.state.value.active?.bookId)
        assertNull(vm.pendingInspection.value)
    }

    @Test
    fun `cancelled pending request restores committed session after explicit abandon`() {
        val vm = coordinatorWithNormalReading()
        commitTemporary(vm, "book-b", 200)
        val pendingT2 = vm.requestTemporaryInspection(target("book-c", 300))
        assertNotNull(pendingT2)
        // 取消路径：返回前请求先被显式作废（生产上等价的 onDispose 边界）。
        assertTrue(vm.abandonPendingInspection(pendingT2!!))

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-c", 300),
            ownedRequestId = pendingT2,
        )

        // 作废后按请求身份仍判未提交 → 同样还原已提交会话，不消费合法栈。
        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-b?sourceLocator="))
        assertTrue(route.contains("navigationMode=temporary"))
        assertTrue(vm.hasReturnableTarget)
        assertEquals(1, vm.state.value.temporaryReturnStack.size)
    }

    @Test
    fun `uncommitted route without committed session returns null for external entry`() {
        // 外部入口（如「我的→阅读笔记」经 readerTemporaryRouteForSource 直进临时查阅）：
        // 协调器无已提交会话（active=null），被压住的栈内前一项是真实来源页 → 返回 null 交还导航回退。
        val vm = TemporaryReadingNavigationViewModel()
        val pendingT1 = vm.requestTemporaryInspection(target("book-b", 200))
        assertNotNull(pendingT1)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-b", 200),
            ownedRequestId = pendingT1,
        )

        assertNull(route)
        assertNull(vm.pendingInspection.value)
        assertFalse(vm.hasReturnableTarget)
    }

    @Test
    fun `committed request through owned identity keeps consuming LIFO`() {
        // 回归保护：真正提交过的 route 仍走既有 LIFO 合同（消费一层）。
        val vm = coordinatorWithNormalReading()
        val committedT1 = commitTemporary(vm, "book-b", 200)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-b", 200),
            ownedRequestId = committedT1,
        )

        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-a?sourceLocator="))
        assertFalse(route.contains("navigationMode="))
        assertFalse(vm.hasReturnableTarget)
        assertEquals("book-a", vm.state.value.active?.bookId)
    }

    @Test
    fun `late result of abandoned request never commits to newer session`() {
        // 迟到结果不得提交到更新会话：T2 取消并还原后，其结果此刻才到达。
        val vm = coordinatorWithNormalReading()
        commitTemporary(vm, "book-b", 200)
        val pendingT2 = vm.requestTemporaryInspection(target("book-c", 300))
        assertNotNull(pendingT2)
        vm.abandonPendingInspection(pendingT2!!)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-c", 300),
            ownedRequestId = pendingT2,
        )
        assertNotNull(route)

        // 迟到的提交必须被协调器按请求身份拒绝，且不得改变会话/栈。
        assertFalse(vm.commitPendingInspection(pendingT2, "book-c"))
        assertEquals("book-b", vm.state.value.active?.bookId)
        assertEquals(1, vm.state.value.temporaryReturnStack.size)
    }
}
