package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationState
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.screen.reader.TemporaryReturnSaveGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 120-v1：全局搜索临时查阅「按真实入口上下文返回」合同（定向回归）。
 *
 * ## 缺陷（120-B）
 * 旧 [SourceNavigationContract.beginTemporaryInspection] 的返回目标取 `active ?: normalReading`：
 * 用户读完 A 返回 Home（协调器为 Activity 作用域、状态跨导航存活）后，从全局搜索发起临时查阅 B，
 * 会**凭历史 normalReading 持久指针**把 A 压入返回栈 —— 返回被带回普通阅读器 A，
 * 而真实来源是全局搜索页。历史持久指针 ≠ 当前来源/导航生命周期。
 *
 * ## 正常设计（120-A，必须保持）
 * 阅读器内发起临时查阅（active 非空）时压入当前阅读位、按 LIFO 返回原阅读处，
 * 且 normalReading 持久指针、嵌套 LIFO、失败从未提交、迟到请求所有权、原位置冻结
 * （[TemporaryReturnSaveGuard]）合同不变。
 *
 * ## 修复语义
 * 1. `beginTemporaryInspection` 的返回目标收紧为仅 `active`（当前活跃阅读位）；
 * 2. 新增会话边界 `endReaderSession`：离开 reader 导航域时清 active/临时栈/在途与已提交请求身份，
 *    **保留** normalReading 持久指针（供「继续阅读」等恢复场景重建普通阅读）；
 * 3. [AppNavigation] 在导航目标离开 reader 域时调用 endReaderSession（接线测试属 Compose 层，
 *    本类覆盖其状态语义与返回裁决序列）。
 *
 * 本类用例在**旧代码**下：`leaving reader then temporary from outside pushes nothing` 与
 * `session end voids pending and committed identities` 失败（压了历史 A / 身份未作废）；
 * 在修复后全部通过 —— 用以区分正常设计与缺陷，不以清空全部状态/重启绕过。
 */
class GlobalSearchTemporaryReturnContractTest {

    @Before
    fun setUp() {
        TemporaryReturnSaveGuard.clear()
    }

    @After
    fun tearDown() {
        TemporaryReturnSaveGuard.clear()
    }

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ── 120-B 核心：离开阅读器后从阅读器外发起临时查阅，不得凭历史普通阅读记录压栈 ──

    @Test
    fun `leaving reader then temporary from outside pushes nothing and returns null route`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)

        // 读 A（active=normalReading=A）→ 真实 Back 离开阅读器到 Home：会话结束（AppNavigation 接线）
        vm.recordNormalReading(a)
        vm.endReaderSession()

        // 从全局搜索发起临时查阅 B：生产路径 = request → 目标就绪 → commit
        val requestId = requireNotNull(vm.requestTemporaryInspection(b))
        assertTrue(vm.commitPendingInspection(requestId, "book-b"))

        // 缺陷断言：历史 normalReading=A 不得被压入返回栈
        assertEquals("临时目标成为当前 active", b, vm.state.value.active)
        assertEquals(
            "阅读器外发起不得凭历史普通阅读记录压栈（120-B）",
            emptyList<SourceNavigationTarget>(),
            vm.state.value.temporaryReturnStack,
        )
        // normalReading 持久指针保留（历史阅读指针是恢复数据，不是返回目标）
        assertEquals("normalReading 持久指针必须保留", a, vm.state.value.normalReading)
        // 无可返回目标：返回按钮不显示；统一返回动作得到 null → 生产 ReaderRoute popBackStack 弹回搜索页
        assertFalse(vm.hasReturnableTarget)
        assertNull(
            "栈空时统一返回动作必须交还导航回退（弹回全局搜索页）",
            resolveTemporaryReturnRoute(vm),
        )
    }

    // ── 120-A 回归保护：阅读器内发起仍压当前阅读位并按合同返回 ──

    @Test
    fun `in-reader temporary inspection still pushes active and returns to reading spot`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)

        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(b)

        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertTrue(vm.hasReturnableTarget)

        val route = requireNotNull(resolveTemporaryReturnRoute(vm))
        val expected = requireNotNull(readerReturnRoute(a))
        assertEquals("返回 route 必须是普通阅读 route（不带 navigationMode）", expected, route)
        assertFalse("普通返回不得携带 navigationMode=temporary", route.contains("navigationMode=temporary"))
        // 返回后栈清空、active 回到 A，normalReading 恒为 A（位置不污染）
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertEquals(a, vm.state.value.normalReading)
        // 栈弹空返回普通阅读时生产会武装原位置冻结守卫（120-A 位置保护合同不变）
        assertTrue("返回普通阅读必须武装原位置冻结守卫", TemporaryReturnSaveGuard.isArmed("book-a"))
    }

    // ── 会话边界状态语义 ──

    @Test
    fun `end reader session clears active and temp stack but keeps normal reading pointer`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)

        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(b)

        vm.endReaderSession()

        assertNull("会话结束后 active 必须失效", vm.state.value.active)
        assertEquals(
            "残留临时栈随会话结束消失（与重启语义同类）",
            emptyList<SourceNavigationTarget>(),
            vm.state.value.temporaryReturnStack,
        )
        assertEquals("normalReading 持久指针必须保留", a, vm.state.value.normalReading)
        assertFalse(vm.hasReturnableTarget)
    }

    // ── 会话边界对请求身份的作废（沿 recordNormalReading 的作废语义）──

    @Test
    fun `session end voids pending and committed identities`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)

        val committed = requireNotNull(vm.requestTemporaryInspection(b))
        assertTrue(vm.commitPendingInspection(committed, "book-b"))
        assertTrue(vm.isRequestCommitted(committed))
        val pending = requireNotNull(vm.requestTemporaryInspection(target("book-c", 300)))

        vm.endReaderSession()

        assertFalse("会话结束后已提交身份必须作废（不跨会话残留）", vm.isRequestCommitted(committed))
        assertNull("会话结束后在途请求必须作废", vm.pendingInspection.value)
        assertFalse(
            "会话结束后迟到提交必须被拒绝（状态不变）",
            vm.commitPendingInspection(pending, "book-c"),
        )
    }

    // ── 契约纯函数级：active 缺席但 normalReading 在场的状态，begin 不得压 normalReading ──

    @Test
    fun `contract level - begin without active must not fall back to normal reading pointer`() {
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        // 会话结束后的状态形状：active=null、normalReading=a、栈空
        val endedSession = SourceNavigationState(active = null, normalReading = a)

        val began = SourceNavigationContract.beginTemporaryInspection(endedSession, b)

        assertEquals(
            "begin 不得用历史 normalReading 冒充返回目标（120-B 根因）",
            emptyList<SourceNavigationTarget>(),
            began.temporaryReturnStack,
        )
        assertEquals(b, began.active)
        assertEquals("normalReading 指针不被 begin 改写", a, began.normalReading)
    }

    // ── 会话结束后的普通阅读恢复：继续阅读 A 重建 active（恢复路径不回归）──

    @Test
    fun `normal reading after session end re-establishes active for later in-reader hops`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val c = target("book-c", 300)

        vm.recordNormalReading(a)
        vm.endReaderSession()

        // Home「继续阅读」普通打开 A：onSourcePositionChanged → recordNormalReading 重建
        vm.recordNormalReading(a)
        assertEquals(a, vm.state.value.active)

        // 此后阅读器内发起临时 C 恢复正常压栈语义（120-A）
        vm.beginTemporaryInspection(c)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertTrue(vm.hasReturnableTarget)
    }
}
