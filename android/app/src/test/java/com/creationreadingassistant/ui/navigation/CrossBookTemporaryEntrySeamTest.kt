package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.screen.reader.PendingInspectionOutcome
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import com.creationreadingassistant.ui.screen.reader.pendingInspectionOutcome
import com.creationreadingassistant.ui.screen.reader.temporaryReturnRouteStep
import com.creationreadingassistant.ui.screen.search.SEARCH_NO_SOURCE_POSITION_MESSAGE
import com.creationreadingassistant.ui.screen.search.SearchContentHitNavigation
import com.creationreadingassistant.ui.screen.search.searchBookHitRoute
import com.creationreadingassistant.ui.screen.search.searchContentHitNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * J1-CROSSBOOK-TEMPORARY-SEAM：跨书临时查阅的**真实入口判定与真实会话状态转移**回归。
 *
 * 与 v1 的差别（v1 只交付了「有定义、无调用」的 helper）：本类断言的是**生产调用链本身**——
 * `SearchScreen` 点击正文命中时调用的 [searchContentHitNavigation]、`ReaderRoute` 观察加载结果时
 * 调用的 [pendingInspectionOutcome]，以及 `TemporaryReadingNavigationViewModel` 的真实状态机。
 *
 * v4 新增 §7/§8：把「本 route 是否已提交」从**目标值判定**改为**本次请求的成功身份**判定
 * （缺陷 1），并让加载 observer 只处置**本 route 拥有的 requestId**、核对完整 destination
 * （缺陷 2）。两条判据都由生产路径实际调用，不是测试专用复制逻辑。
 *
 * 覆盖任务书 §五 要求的六条：
 *  1. 搜索无可靠 locator → 只反馈、不导航普通 B；
 *  2. 用户主动普通打开书籍仍正常（不受失败降级规则影响）；
 *  3. 目标**真实加载成功**才提交临时会话；
 *  4. 目标不存在/不可读 → 原会话、普通阅读位置、合法既有栈全部保留；
 *  5. 失败/取消/迟到结果不覆盖较新的导航；
 *  6. 成功跨书与嵌套返回保持既有 LIFO / source 合同。
 *
 * 边界（诚实标注）：本类只证明 JVM 可判定的入口判定与会话状态转移。真实 Compose 点击、
 * Snackbar 呈现、`LaunchedEffect`/`DisposableEffect` 的真实重组时序与阅读器真实加载失败 UI
 * 属设备旅程，**未**由本类证明（见报告「未覆盖」）。
 */
class CrossBookTemporaryEntrySeamTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ── 1. 搜索无可靠 locator：只反馈，不导航普通 B ────────────────────────

    @Test
    fun `content hit without a precise offset yields feedback only and no navigable target`() {
        val outcome = searchContentHitNavigation(bookId = "book-b", preciseOffset = null)

        assertEquals(SearchContentHitNavigation.NoReliableSource, outcome)
        assertTrue("失败分支必须有用户可见反馈", SEARCH_NO_SOURCE_POSITION_MESSAGE.isNotBlank())
        // NoReliableSource 类型上不携带 route：调用方不可能拿它去 navigate 普通打开 B。
        assertNull((outcome as? SearchContentHitNavigation.TemporaryInspection)?.route)
    }

    @Test
    fun `content hit with a precise offset stays a temporary inspection`() {
        val outcome = searchContentHitNavigation(bookId = "book-b", preciseOffset = 4_096)

        val navigation = outcome as? SearchContentHitNavigation.TemporaryInspection
        assertNotNull("有精确 offset 时必须能构造临时查阅目标", navigation)
        val route = navigation!!.route
        assertTrue(route.startsWith("reader/book-b"))
        assertTrue("必须是临时查阅，否则返回行为会退化成普通阅读", route.contains("navigationMode=temporary"))
        assertFalse("临时查阅不得携带 highlightId", route.contains("highlightId="))
        assertEquals(4_096, decodeSourceLocatorFromRoute(route)!!.legacyOffset)
    }

    @Test
    fun `negative or blank book coordinates never degrade to a plain open`() {
        assertEquals(
            SearchContentHitNavigation.NoReliableSource,
            searchContentHitNavigation(bookId = "book-b", preciseOffset = -1),
        )
        assertEquals(
            SearchContentHitNavigation.NoReliableSource,
            searchContentHitNavigation(bookId = "   ", preciseOffset = 10),
        )
    }

    // ── 2. 用户主动「普通打开书籍」仍正常 ─────────────────────────────────

    @Test
    fun `the explicit book hit entry still opens the book as ordinary reading`() {
        val route = searchBookHitRoute("book-b")

        assertEquals("reader/book-b", route)
        assertFalse("普通打开不得带临时查阅模式", route.contains("navigationMode="))
        assertFalse(route.contains("sourceLocator="))
        assertFalse(route.contains("highlightId="))
    }

    // ── 3. 目标真实加载成功才提交临时会话 ─────────────────────────────────

    @Test
    fun `a temporary session is committed only after the target really loads`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)

        val requestId = vm.requestTemporaryInspection(b)
        assertNotNull(requestId)
        // 请求在途期间会话必须分毫不动 —— 这正是「失败无需回滚」的前提。
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertEquals(b, vm.pendingInspection.value?.destination)

        assertTrue(vm.commitPendingInspection(requestId!!, "book-b"))

        assertEquals(b, vm.state.value.active)
        assertEquals(a, vm.state.value.normalReading)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertNull("提交后请求必须清空", vm.pendingInspection.value)
    }

    @Test
    fun `commit is refused while loading and for a result belonging to another book`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        val requestId = vm.requestTemporaryInspection(target("book-b", 200))!!

        assertFalse("结果属于另一本书不得提交", vm.commitPendingInspection(requestId, "book-c"))
        assertFalse("没有加载结果（null）不得提交", vm.commitPendingInspection(requestId, null))
        assertNotNull("被拒的提交不得误清在途请求", vm.pendingInspection.value)
        assertEquals("被拒的提交不得改动会话", target("book-a", 100), vm.state.value.active)

        assertTrue(vm.commitPendingInspection(requestId, "book-b"))
    }

    @Test
    fun `a null destination never enters a pending request`() {
        val vm = TemporaryReadingNavigationViewModel()

        assertNull(vm.requestTemporaryInspection(null))
        assertNull(vm.pendingInspection.value)
    }

    // ── 3b. ReaderRoute 实际调用的加载结果处置判定 ────────────────────────

    @Test
    fun `pending inspection outcome follows the real load result for the owned request`() {
        val bookId = "book-b"
        val destination = target(bookId, 200)
        val owned = PendingTemporaryInspection(requestId = 7L, destination = destination)

        fun outcome(
            mode: ReaderNavigationMode = ReaderNavigationMode.TEMPORARY,
            ownedRequestId: Long? = 7L,
            pending: PendingTemporaryInspection? = owned,
            currentDestination: SourceNavigationTarget? = destination,
            requestedBookId: String? = bookId,
            isReady: Boolean = false,
            errorMessage: String? = null,
        ): PendingInspectionOutcome = pendingInspectionOutcome(
            navigationMode = mode,
            ownedRequestId = ownedRequestId,
            pending = pending,
            currentDestination = currentDestination,
            requestedBookId = requestedBookId,
            isReady = isReady,
            errorMessage = errorMessage,
        )

        assertEquals("普通打开书籍不产生临时会话", PendingInspectionOutcome.WAIT,
            outcome(mode = ReaderNavigationMode.NORMAL, isReady = true))
        assertEquals("本 route 没有在途请求时不处置", PendingInspectionOutcome.WAIT,
            outcome(ownedRequestId = null, isReady = true))
        assertEquals("全局无 pending 时不处置", PendingInspectionOutcome.WAIT,
            outcome(pending = null, isReady = true))
        assertEquals("不是本 route 拥有的 requestId 不得处置", PendingInspectionOutcome.WAIT,
            outcome(ownedRequestId = 8L, isReady = true))
        assertEquals("本 route 完整 destination 与 pending 不一致不得处置", PendingInspectionOutcome.WAIT,
            outcome(currentDestination = target("book-c", 300), isReady = true))
        assertEquals("加载结果不属于目标书不得提交", PendingInspectionOutcome.WAIT,
            outcome(requestedBookId = "book-c", isReady = true))
        assertEquals("仍在加载：既不算成功也不回退", PendingInspectionOutcome.WAIT,
            outcome(isReady = false, errorMessage = null))
        assertEquals("目标就绪才是成功", PendingInspectionOutcome.COMMIT,
            outcome(isReady = true))
        assertEquals("目标报错只放弃请求", PendingInspectionOutcome.ABANDON,
            outcome(isReady = false, errorMessage = "打开失败"))
    }

    // ── 4. 目标不存在/不可读：原会话、普通目标、合法栈全部保留 ─────────────

    @Test
    fun `an unreadable target keeps the original session normal position and stack intact`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b1 = target("book-b1", 200)
        val missing = target("book-missing", 300)
        vm.recordNormalReading(a)
        // 先有一次已成功提交的跨书查阅（嵌套场景的现场）
        assertTrue(vm.commitPendingInspection(vm.requestTemporaryInspection(b1)!!, "book-b1"))
        val beforeFailedHop = vm.state.value

        // 第二次跳转的目标书不存在/正文不可读：请求 → 加载失败 → 放弃
        val second = vm.requestTemporaryInspection(missing)!!
        assertEquals("请求在途期间不得改动会话", beforeFailedHop, vm.state.value)
        assertTrue(vm.abandonPendingInspection(second))

        assertEquals("失败后原会话必须逐字段保留", beforeFailedHop, vm.state.value)
        assertEquals(b1, vm.state.value.active)
        assertEquals(a, vm.state.value.normalReading)
        assertEquals("合法既有栈不得被清空", listOf(a), vm.state.value.temporaryReturnStack)
        assertNull(vm.pendingInspection.value)
    }

    @Test
    fun `abandoning a pending request never clears an existing temporary stack`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(target("book-b1", 200))

        val id = vm.requestTemporaryInspection(target("book-missing", 300))!!
        assertTrue(vm.abandonPendingInspection(id))

        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertTrue("既有栈仍在，返回目标不得被伪造或清除", vm.hasReturnableTarget)
        assertEquals(target("book-b1", 200), vm.state.value.active)
    }

    // ── 5. 失败/取消/迟到结果不覆盖较新导航 ───────────────────────────────

    @Test
    fun `a superseded request can neither commit nor abandon the newer one`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        val first = vm.requestTemporaryInspection(target("book-b", 200))!!
        val second = vm.requestTemporaryInspection(target("book-c", 300))!!

        assertFalse("被取代的请求不得提交", vm.commitPendingInspection(first, "book-b"))
        assertFalse("被取代的请求不得放弃新请求", vm.abandonPendingInspection(first))
        assertEquals("新请求必须仍然在途", second, vm.pendingInspection.value?.requestId)
        assertEquals("旧结果不得改动会话", target("book-a", 100), vm.state.value.active)

        assertTrue(vm.commitPendingInspection(second, "book-c"))
        assertEquals(target("book-c", 300), vm.state.value.active)
    }

    @Test
    fun `a legitimate normal reading change while waiting invalidates the pending request`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        val id = vm.requestTemporaryInspection(target("book-b", 200))!!

        // 等待期间用户真实落在另一处普通阅读 —— 这是合法的导航变化，不能被迟到结果覆盖。
        vm.recordNormalReading(target("book-z", 500))

        assertNull("在途请求必须随普通阅读变化作废", vm.pendingInspection.value)
        assertFalse("迟到结果不得把用户拖回旧请求的目的地", vm.commitPendingInspection(id, "book-b"))
        assertEquals(target("book-z", 500), vm.state.value.active)
        assertEquals(target("book-z", 500), vm.state.value.normalReading)
    }

    @Test
    fun `cancelling a request by id is idempotent and leaves the session alone`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        val id = vm.requestTemporaryInspection(target("book-b", 200))!!

        assertTrue(vm.abandonPendingInspection(id))
        assertFalse("同一请求不得被重复放弃", vm.abandonPendingInspection(id))
        assertEquals(target("book-a", 100), vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
    }

    // ── 6. 成功跨书与嵌套返回保持既有 LIFO / source 合同 ──────────────────

    @Test
    fun `successful cross book hops keep the existing LIFO and source contract`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        val c = target("book-c", 300)
        vm.recordNormalReading(a)

        assertTrue(vm.commitPendingInspection(vm.requestTemporaryInspection(b)!!, "book-b"))
        assertTrue(vm.commitPendingInspection(vm.requestTemporaryInspection(c)!!, "book-c"))

        assertEquals(listOf(a, b), vm.state.value.temporaryReturnStack)
        assertEquals(c, vm.state.value.active)
        assertEquals("普通阅读位置不得被临时查阅搬走", a, vm.state.value.normalReading)

        vm.returnFromTemporaryInspection()
        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
        assertTrue(vm.hasReturnableTarget)

        vm.returnFromTemporaryInspection()
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
        assertFalse(vm.hasReturnableTarget)
    }

    @Test
    fun `committing the same destination twice does not grow the return stack`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        assertTrue(vm.commitPendingInspection(vm.requestTemporaryInspection(b)!!, "book-b"))
        val afterFirstCommit = vm.state.value

        assertTrue(vm.commitPendingInspection(vm.requestTemporaryInspection(b)!!, "book-b"))

        assertEquals("同一目的地重复提交不得重复压栈", afterFirstCommit, vm.state.value)
    }

    // ── 7. v4 缺陷 1：未提交 route 的返回不得消费合法栈（按请求身份判定） ──
    // 207-r2 机制更新：reader 域以 launchSingleTop 进入，Navigation 2.5.1 会用新 entry
    // 替换栈顶同 pattern 的旧 reader entry——对未提交 route 一律 pop 会退到 reader 域外。
    // 因此「不消费栈」的落地机制从「返回 null 交还导航回退」改为「显式还原协调器仍持有的
    // 已提交会话 route（restoreCommittedReaderSessionRoute，只读重建，不消费栈不改会话）」。
    // 协调器没有已提交会话（外部入口直进临时查阅）时仍返回 null 交还导航回退。

    @Test
    fun `returning from an uncommitted destination must not consume the legitimate stack`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        // C 尚未加载成功（在途等待）：active 仍是 B、栈仍是 [A]
        val cRequest = vm.requestTemporaryInspection(target("book-c", 300))!!

        // ReaderRoute.performTemporaryReturn 实际形态：携带当前 route 的目的地与本 route 拥有的 requestId
        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-c", 300),
            ownedRequestId = cRequest,
        )

        assertTrue(
            "未提交的当前 route 按 207-r2 还原已提交会话（B，temporary 语义）；还原不消费栈",
            route != null && route.startsWith("reader/book-b") && route.contains("navigationMode=temporary"),
        )
        assertNull("返回动作必须按本 route 拥有的 requestId 作废在途请求", vm.pendingInspection.value)
        assertFalse("取消后的迟到结果不得改写会话与栈", vm.commitPendingInspection(cRequest, "book-c"))
        assertEquals("此前已提交的 B 会话必须保留", b, vm.state.value.active)
        assertEquals("合法既有栈不得被消费", listOf(a), vm.state.value.temporaryReturnStack)

        // B 再次返回时才走既有 LIFO 合同回到 A（末层恢复普通阅读语义，不带 navigationMode）
        val routeFromB = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = b,
            ownedRequestId = bRequest,
        )
        assertTrue("B 的返回目标必须是 A", routeFromB!!.startsWith("reader/book-a"))
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
    }

    @Test
    fun `returning from a failed destination restores the previously committed route`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        // C 加载失败 → abandon 只清 pending，页面仍停在 C 失败页（此时 pending 已为空）
        val cRequest = vm.requestTemporaryInspection(target("book-c", 300))!!
        assertTrue(vm.abandonPendingInspection(cRequest))
        assertNull(vm.pendingInspection.value)

        // route 归属不能只凭 pending 是否非空判定：已失败的 C 不在会话里，不得消费合法栈
        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-c", 300),
            ownedRequestId = cRequest,
        )

        assertTrue(
            "已失败的当前 route 按 207-r2 还原已提交会话（B）；还原不消费合法栈",
            route != null && route.startsWith("reader/book-b") && route.contains("navigationMode=temporary"),
        )
        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)

        // B 再次返回时才正常回 A
        val routeFromB = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = b,
            ownedRequestId = bRequest,
        )
        assertTrue(routeFromB!!.startsWith("reader/book-a"))
        assertEquals(a, vm.state.value.active)
    }

    @Test
    fun `the production return path abandons only the request it owns`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        val oldRequest = vm.requestTemporaryInspection(target("book-b", 200))!!
        val newRequest = vm.requestTemporaryInspection(target("book-c", 300))!!

        // 旧 route 携带自己的 requestId 做返回/清理：不得取消较新的请求
        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = target("book-b", 200),
            ownedRequestId = oldRequest,
        )

        assertTrue(
            "从未提交的旧 route 按 207-r2 还原已提交会话（A，普通阅读，栈空无 temporary 语义）；不消费栈",
            route != null && route.startsWith("reader/book-a") && !route.contains("navigationMode=temporary"),
        )
        assertEquals("较新的请求不得被旧 route 清理取消", newRequest, vm.pendingInspection.value?.requestId)
        assertTrue("旧 route 离开后新请求仍有效", vm.commitPendingInspection(newRequest, "book-c"))
        assertEquals(target("book-c", 300), vm.state.value.active)
    }

    @Test
    fun `a committed destination keeps the existing LIFO return contract`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        val c = target("book-c", 300)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        val cRequest = vm.requestTemporaryInspection(c)!!
        assertTrue(vm.commitPendingInspection(cRequest, "book-c"))

        // C 已成功提交：返回动作走既有 LIFO 合同，先回 B（中间层保持 temporary 语义）
        val routeToB = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = c,
            ownedRequestId = cRequest,
        )
        assertTrue(routeToB!!.startsWith("reader/book-b"))
        assertEquals("LIFO 返回必须消费一层栈", listOf(a), vm.state.value.temporaryReturnStack)
        assertEquals(b, vm.state.value.active)

        // B 再次返回才回 A（末层恢复普通阅读语义）
        val routeToA = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = b,
            ownedRequestId = bRequest,
        )
        assertTrue(routeToA!!.startsWith("reader/book-a"))
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
    }

    @Test
    fun `normal mode and legacy two arg calls keep the existing contract`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        vm.recordNormalReading(a)

        // 普通阅读没有临时返回动作（携带新参也不改变这一点）
        assertNull(
            temporaryReturnRouteStep(
                ReaderNavigationMode.NORMAL,
                vm,
                currentDestination = a,
                ownedRequestId = null,
            ),
        )

        // 2 参旧形态（既有调用方）保持既有 LIFO 合同
        vm.beginTemporaryInspection(target("book-b", 200))
        val route = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm)
        assertTrue(route!!.startsWith("reader/book-a"))
        assertEquals(a, vm.state.value.active)
        assertEquals(emptyList<SourceNavigationTarget>(), vm.state.value.temporaryReturnStack)
    }

    // ── 8. v4 定向场景：重复目标值不得被当作「本次已提交」 ─────────────────
    // 这三条是缺陷 1 的直接红用例：v3 的 isCommittedDestination 用 active / 旧栈成员（目标值）
    // 判定，会把「重复请求栈内目标」「重复旧 active」的**未提交**请求误判为已提交，返回时
    // 错误消费合法栈。v4 以本次请求的成功身份判定后杜绝误消费；207-r2 起「不消费」的落地
    // 机制为还原已提交会话 route（不再是 null + 导航回退，见第 7 节说明）。

    @Test
    fun `re-requesting a target already in the return stack does not consume it when uncommitted`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        // active=B、栈=[A]。再次请求**栈内的 A**（等待中，未提交）——目标值与栈成员重合。
        val aRequest = vm.requestTemporaryInspection(a)!!

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = a,
            ownedRequestId = aRequest,
        )

        assertTrue(
            "重复栈内目标的未提交请求按 207-r2 还原已提交会话（B）；不消费合法栈",
            route != null && route.startsWith("reader/book-b") && route.contains("navigationMode=temporary"),
        )
        assertEquals("原 B 会话必须保留", b, vm.state.value.active)
        assertEquals("合法栈 [A] 不得被消费", listOf(a), vm.state.value.temporaryReturnStack)
        assertFalse("取消后的迟到结果不得改写会话与栈", vm.commitPendingInspection(aRequest, "book-a"))
        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
    }

    @Test
    fun `re-requesting the current active target as a new pending request does not consume the stack`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        // 重复旧 active B（目标值与 active 重合），但本次请求尚未提交。
        val duplicateActive = vm.requestTemporaryInspection(b)!!

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = b,
            ownedRequestId = duplicateActive,
        )

        assertTrue(
            "重复旧 active 的未提交请求按 207-r2 还原已提交会话（B）；不消费合法栈",
            route != null && route.startsWith("reader/book-b") && route.contains("navigationMode=temporary"),
        )
        assertEquals(b, vm.state.value.active)
        assertEquals("合法栈 [A] 不得被消费", listOf(a), vm.state.value.temporaryReturnStack)
    }

    @Test
    fun `a failed duplicate target keeps the session even after pending is cleared`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        vm.recordNormalReading(a)
        val bRequest = vm.requestTemporaryInspection(b)!!
        assertTrue(vm.commitPendingInspection(bRequest, "book-b"))
        // 重复栈内 A 的请求加载失败 → pending 已清，但 route 仍未成功。
        val aRequest = vm.requestTemporaryInspection(a)!!
        assertTrue(vm.abandonPendingInspection(aRequest))
        assertNull("失败后 pending 已清", vm.pendingInspection.value)

        val route = temporaryReturnRouteStep(
            ReaderNavigationMode.TEMPORARY,
            vm,
            currentDestination = a,
            ownedRequestId = aRequest,
        )

        assertTrue(
            "失败且 pending 已清的当前 route 按 207-r2 还原已提交会话（B）；不消费合法栈",
            route != null && route.startsWith("reader/book-b") && route.contains("navigationMode=temporary"),
        )
        assertEquals(b, vm.state.value.active)
        assertEquals(listOf(a), vm.state.value.temporaryReturnStack)
    }

    // ── 9. v4 缺陷 2：旧 route 的 Ready 结果不得提交别的 route 的请求 ───────

    @Test
    fun `an old route ready result cannot commit a request it does not own`() {
        val x1 = target("book-x", 100)
        val x2 = target("book-x", 500)
        val ownedByOldRoute = 11L
        val ownedByNewRoute = 22L
        val pendingNew = PendingTemporaryInspection(requestId = ownedByNewRoute, destination = x2)

        // 旧 route（同书、documentState 已 ready）持有的是旧 requestId：不得用全局最新
        // requestId 提交新 locator 请求。
        assertEquals(
            "旧 route 不得提交不是自己拥有的 requestId",
            PendingInspectionOutcome.WAIT,
            pendingInspectionOutcome(
                navigationMode = ReaderNavigationMode.TEMPORARY,
                ownedRequestId = ownedByOldRoute,
                pending = pendingNew,
                currentDestination = x1,
                requestedBookId = "book-x",
                isReady = true,
                errorMessage = null,
            ),
        )

        // 合法同书 ready 复用：新 route 针对**自身请求**明确确认 → 允许提交（不强制重新加载）。
        assertEquals(
            "本 route 拥有的同书 ready 请求可复用加载结果",
            PendingInspectionOutcome.COMMIT,
            pendingInspectionOutcome(
                navigationMode = ReaderNavigationMode.TEMPORARY,
                ownedRequestId = ownedByNewRoute,
                pending = pendingNew,
                currentDestination = x2,
                requestedBookId = "book-x",
                isReady = true,
                errorMessage = null,
            ),
        )
    }

    @Test
    fun `an old route observer never commits or rolls back a newer request`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        // 旧 route 登记并离开（DisposableEffect onDispose 会作废自己的 requestId）。
        val oldRequest = vm.requestTemporaryInspection(target("book-b", 200))!!
        assertTrue(vm.abandonPendingInspection(oldRequest))
        // 新 route 登记同书不同 locator 的新请求。
        val newRequest = vm.requestTemporaryInspection(target("book-b", 900))!!
        assertEquals(newRequest, vm.pendingInspection.value?.requestId)

        // 旧 route 迟到的 ready 结果：它自己的 requestId 已作废，且不是当前在途请求 —— 不得提交。
        assertFalse(vm.commitPendingInspection(oldRequest, "book-b"))
        assertEquals("旧结果不得改动会话", target("book-a", 100), vm.state.value.active)
        assertEquals("旧结果不得动到较新的在途请求", newRequest, vm.pendingInspection.value?.requestId)

        // 较新的请求仍可由其真正 owner 正常提交。
        assertTrue(vm.commitPendingInspection(newRequest, "book-b"))
        assertEquals(target("book-b", 900), vm.state.value.active)
    }
}
