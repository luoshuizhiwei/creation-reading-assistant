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
}
