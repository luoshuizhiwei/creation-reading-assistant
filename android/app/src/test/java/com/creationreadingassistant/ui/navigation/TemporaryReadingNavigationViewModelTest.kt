package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationState
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 临时查阅会话协调器的纯 JVM 回归：把 SourceNavigationContract 的纯状态规约验证到
 * 应用导航生命周期持有者层面，覆盖 6 个切片要求。
 */
class TemporaryReadingNavigationViewModelTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // 1. 普通阅读不会制造临时返回项
    @Test
    fun `recording normal reading never creates a temporary return item`() {
        val vm = TemporaryReadingNavigationViewModel()

        vm.recordNormalReading(target("book-a", 100))

        assertEquals(target("book-a", 100), vm.state.value.active)
        assertEquals(target("book-a", 100), vm.state.value.normalReading)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertFalse(vm.hasReturnableTarget)
    }

    // 2. A→临时 B→临时 C 的 LIFO 返回
    @Test
    fun `nested temporary inspections return in LIFO order`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        val c = target("book-c", 300)

        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(b)
        vm.beginTemporaryInspection(c)

        assertEquals(c, vm.state.value.active)
        assertEquals(a, vm.state.value.normalReading)
        assertEquals(listOf(a, b), vm.state.value.temporaryReturnStack)
        assertTrue(vm.hasReturnableTarget)

        vm.returnFromTemporaryInspection()
        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertTrue(vm.hasReturnableTarget)

        vm.returnFromTemporaryInspection()
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertFalse(vm.hasReturnableTarget)
    }

    // 3. 普通阅读行为清空临时链
    @Test
    fun `recording normal reading clears the temporary chain`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        vm.beginTemporaryInspection(target("book-b", 200))
        vm.beginTemporaryInspection(target("book-c", 300))

        vm.recordNormalReading(target("book-a", 150))

        assertEquals(target("book-a", 150), vm.state.value.active)
        assertEquals(target("book-a", 150), vm.state.value.normalReading)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertFalse(vm.hasReturnableTarget)
    }

    // 4. 8 层上限
    @Test
    fun `temporary history is bounded to eight recent origins`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("origin", 0))
        repeat(9) { i -> vm.beginTemporaryInspection(target("hop-$i", i)) }

        assertEquals(8, vm.state.value.temporaryReturnStack.size)
        // 最旧的普通位置被挤出，栈内只剩最近 8 个 origin
        assertEquals(target("hop-0", 0), vm.state.value.temporaryReturnStack.first())
        assertFalse(vm.state.value.temporaryReturnStack.contains(target("origin", 0)))
    }

    // 5. 无有效 source 坐标拒绝进入
    @Test
    fun `invalid or missing source coordinates are rejected and never enter state`() {
        val vm = TemporaryReadingNavigationViewModel()

        // 空白书籍 ID、负 offset、负章节坐标均被契约拒绝为 null
        assertNull(SourceNavigationContract.target(" ", ReaderLocator(5, null, null, null)))
        assertNull(SourceNavigationContract.target("book-a", ReaderLocator(-1, null, null, null)))
        assertNull(SourceNavigationContract.target("book-a", ReaderLocator(null, -1, 3, null)))

        // 协调器对 null 目标一律拒绝，不进入状态
        vm.recordNormalReading(null)
        vm.beginTemporaryInspection(null)

        assertEquals(SourceNavigationState(), vm.state.value)
        assertFalse(vm.hasReturnableTarget)
    }

    // 6. 新协调器实例：临时栈为空、普通位置不伪造恢复
    @Test
    fun `a fresh coordinator has no temporary stack and no fabricated normal reading`() {
        val vm = TemporaryReadingNavigationViewModel()

        assertEquals(SourceNavigationState(), vm.state.value)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertNull(vm.state.value.normalReading)
        assertNull(vm.state.value.active)
        assertFalse(vm.hasReturnableTarget)
    }
}