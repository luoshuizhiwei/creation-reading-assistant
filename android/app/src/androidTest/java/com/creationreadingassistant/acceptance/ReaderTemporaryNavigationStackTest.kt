package com.creationreadingassistant.acceptance

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import com.creationreadingassistant.ui.navigation.readerNavigationMode
import com.creationreadingassistant.ui.navigation.readerSourceRoute
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import com.creationreadingassistant.ui.navigation.sourceTarget
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import com.creationreadingassistant.ui.screen.reader.dispatchTemporaryReturn
import com.creationreadingassistant.ui.screen.reader.navigateReaderRouteSingleTop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务207-r3：真实 NavHost/NavController 返回栈观测 + 生产同键请求生命周期 + 生产共享分派。
 *
 * ## 本轮状态（不得超证据宣称）
 * **本轮仅交付编译（compileDebugAndroidTestKotlin），未在任何设备/模拟器执行 —— 全部用例 device NOT_RUN，
 * 编译交付可供Root审查，不声称行为通过。**不声称「独立复现」「彻底解决」，不把 205 失败根因当已实证。
 *
 * ## 相对 207-r3 的修正（Root静态定位两项，207-r4）
 * 1. 【真离开观测清理】r3 的壳只在 DisposableEffect(ownedRequestId) 作废 pending，真实 pop 回
 *    入口后 landedBookId/currentReaderBackAction 等残留最后 reader 值，assertLandedOnEntry 的
 *    null 断言会被自身残留判错。现以 `DisposableEffect(entry.id)` 挂清理：singleTop 复制替换
 *    保留 id 且 Crossfade 以 id 为 key，参数切换/requestId 变化时组合槽位不变、effect 不重启
 *    —— 已发布的新 route 动作不被中途清空；只有组合真正离开（真实 pop/移出返回栈）才清空。
 *    不删 null 断言、不用无条件默认值遮错。
 * 2. 【还原层请求身份】还原 T1' 是参数切回的新 route 实例（组合保留）：必须等「目标参数匹配的
 *    新请求」完成登记并通过 currentReaderCommitAction 提交（active 相同去重、不重压栈）再 Back，
 *    否则生产 step 按未提交分支还原 T1、回不到 S0。等待用 [awaitPendingRequestFor] 三方一致
 *    （pending 存在 + destination 匹配 + 壳发布值 == pending.requestId），不能只等
 *    currentOwnedRequestId 非空（可能仍为旧 id）。各层逐步断言 mode/bookId/请求身份
 *    （isRequestCommitted/新旧 id 区分）/LIFO 深度/返回入口；迟到提交仍用真实取消的 T2 requestId。
 *
 * ## 相对 207-r2 的修正（Root核验三P1）
 * 1. 【entry.id 保留】2.5.1 singleTop 复制构造**保留 entry.id**（Root保存的
 *    navigation-common-2.5.1-NavBackStackEntry.kt 78-85 行：复制构造沿用 `entry.id`；
 *    NavController.kt:1702-1713 正是调用该构造）。r2 的「id 改变」断言按源码事实更正为
 *    「对象替换、id 保留、destination 参数更新」；观测口径 = 实际 destination 参数、
 *    previous entry 身份、pop 结果，不用组合存活数当栈数量。
 * 2. 【生产同键登记】`remember(entry.id)` 在 id 保留语义下不随参数切换重登记；reader 壳改为
 *    与生产 ReaderRoute 同键（bookId/sourceLocator/navigationMode/协调器）的 LaunchedEffect
 *    登记，`var ownedRequestId by remember { mutableStateOf }` 保存，DisposableEffect 只作废
 *    自己登记的旧 pending。
 * 3. 【生产共享分派】不再镜像 navigate 选项：导航经生产
 *    [navigateReaderRouteSingleTop]，返回经生产 [dispatchTemporaryReturn]（ReaderRoute.kt
 *    提取的唯一实现，生产 `onInspectSourceTemporarily`/`performTemporaryReturn` 同源调用）。
 *    文档就绪提交（显式 [currentReaderCommitAction]）与保存 await 仍不在壳内，见缺口。
 * 4. 【一个@Test一次setContent】三个入口拆为独立 @Test（[shelfChain]…[profileChain]），
 *    每个用例只 `launchGraph` 一次。
 * 5. 【真实迟到 requestId】迟到提交断言使用 T2 壳真实登记并随取消作废的 requestId
 *    （[currentOwnedRequestId] 捕获），不再伪造 stale 请求。
 *
 * ## 诚实缺口（不构成生产闭环的部分）
 * - 文档加载结果以「显式提交 / 从不提交」代替真实 ReaderViewModel 加载（19 依赖不可在壳内构造）；
 * - 保存 await（awaitProgressSaves）在 ReaderScreen 生产边界内，不在壳内；
 * - 完整真实 UI 链由新增 ReaderTemporaryNavigationProductionTest（生产组合）承担，本轮同样 NOT_RUN。
 */
@RunWith(AndroidJUnit4::class)
class ReaderTemporaryNavigationStackTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var coordinator: TemporaryReadingNavigationViewModel

    // 壳落地观测（SideEffect 发布；只用于参数级断言，不用作栈数量）。
    // @Volatile：SideEffect 在 UI 线程写、waitUntil 在测试线程轮询读。
    @Volatile
    private var landedBookId: String? = null
    @Volatile
    private var landedMode: ReaderNavigationMode? = null

    // 当前栈顶 reader entry 的动作：由壳构建，调用生产共享分派。
    @Volatile
    private var currentReaderBackAction: (() -> Unit)? = null
    // 「文档就绪提交」动作：测试在加载成功时刻显式调用（等价生产 observer COMMIT 时刻）。
    @Volatile
    private var currentReaderCommitAction: (() -> Unit)? = null
    // 当前壳真实登记的 ownedRequestId（迟到提交断言用它，不伪造）。
    @Volatile
    private var currentOwnedRequestId: Long? = null

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    /** 生产 reader destination 的 route pattern（与 AppNavigation 的 composable 声明逐字一致）。 */
    private val readerPattern =
        "reader/{bookId}?highlightId={highlightId}&sourceLocator={sourceLocator}&navigationMode={navigationMode}"

    /** 真实生产入口目的地：书架、搜索、我的（阅读笔记列表在 ProfileRoute 内）。 */
    private val originalEntries = listOf("shelf", "search", "profile")

    /** 每个 @Test 恰调用一次（一个 setContent）。 */
    private fun launchGraph(startEntry: String) {
        landedBookId = null
        landedMode = null
        currentReaderBackAction = null
        currentReaderCommitAction = null
        currentOwnedRequestId = null
        compose.setContent {
            val controller = rememberNavController()
            SideEffect { nav = controller }
            NavHost(navController = controller, startDestination = startEntry) {
                composable(startEntry) { Text("entry-screen") }
                originalEntries.filter { it != startEntry }.forEach { other ->
                    composable(other) { Text("entry-screen") }
                }
                composable(
                    route = readerPattern,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("highlightId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("sourceLocator") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("navigationMode") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val bookId = entry.arguments?.getString("bookId").orEmpty()
                    val mode = readerNavigationMode(entry.arguments?.getString("navigationMode"))
                    val locatorJson = entry.arguments?.getString("sourceLocator")
                    val destination = sourceTarget(bookId, locatorJson)
                    // 207-r3：生产同键请求登记（ReaderRoute LaunchedEffect(bookId, sourceLocatorJson,
                    // navigationMode, temporaryNavigation) 同键）。singleTop 保留 entry.id，
                    // 不能以 entry.id 为键 —— 参数切换时必须重登记新请求。
                    var ownedRequestId by remember { mutableStateOf<Long?>(null) }
                    LaunchedEffect(bookId, locatorJson, mode, coordinator) {
                        ownedRequestId = if (mode == ReaderNavigationMode.TEMPORARY) {
                            coordinator.requestTemporaryInspection(destination)
                        } else {
                            null
                        }
                    }
                    DisposableEffect(ownedRequestId) {
                        // 只作废本壳登记的旧 requestId（生产同口径：abandon 只对当前在途生效）。
                        val requestId = ownedRequestId
                        onDispose {
                            if (requestId != null) coordinator.abandonPendingInspection(requestId)
                        }
                    }
                    DisposableEffect(entry.id) {
                        // 207-r4 修1：发布观测的清理只挂「本 reader 组合真正离开」的边界。
                        // 键取 entry.id：singleTop 复制替换保留 id（NavBackStackEntry 复制构造
                        // 78-85 行）且 Crossfade 以 id 为 key，参数切换（S0→T1→T2、还原 T1'）
                        // 时组合槽位不变，本 effect 不重启 —— 已发布的新 route 动作/请求 id
                        // 不会被 requestId 变化中途清空；只有真实 pop/移出返回栈使组合离开时
                        // 才清空全部发布观测，assertLandedOnEntry 的 null 断言因此反映真实
                        // 退出而不是本壳残留（不删断言、不用默认值遮错）。
                        onDispose {
                            currentReaderBackAction = null
                            currentReaderCommitAction = null
                            currentOwnedRequestId = null
                            landedBookId = null
                            landedMode = null
                        }
                    }
                    // 普通落地报告普通阅读位置（生产 onSourcePositionChanged 正常态同源语义）。
                    LaunchedEffect(entry.id, mode, destination) {
                        if (mode == ReaderNavigationMode.NORMAL) {
                            coordinator.recordNormalReading(destination)
                        }
                    }
                    // 返回动作 = 生产 dispatchTemporaryReturn（决策+navigate/pop 分派唯一实现）。
                    val backAction: () -> Unit = {
                        dispatchTemporaryReturn(
                            controller,
                            mode,
                            coordinator,
                            currentDestination = destination,
                            ownedRequestId = ownedRequestId,
                        )
                    }
                    // 文档就绪提交：测试显式触发（等价生产 observer COMMIT 时刻；协调器 API 真实）。
                    val commitAction: () -> Unit = {
                        val rid = ownedRequestId
                        if (mode == ReaderNavigationMode.TEMPORARY && rid != null && destination != null) {
                            coordinator.commitPendingInspection(rid, destination.bookId)
                        }
                    }
                    SideEffect {
                        landedBookId = bookId
                        landedMode = mode
                        currentReaderBackAction = backAction
                        currentReaderCommitAction = commitAction
                        currentOwnedRequestId = ownedRequestId
                    }
                    Column { Text("reader:$bookId:$mode") }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun awaitReaderShell(bookId: String, mode: ReaderNavigationMode) {
        compose.waitUntil(timeoutMillis = 15_000) {
            landedBookId == bookId && landedMode == mode
        }
        compose.waitForIdle()
        assertEquals(readerPattern, nav.currentDestination?.route)
        assertEquals(bookId, landedBookId)
        assertEquals(mode, landedMode)
    }

    private fun assertLandedOnEntry(entry: String) {
        assertEquals(entry, nav.currentDestination?.route)
        assertNull("退出后不得有 reader 观测残留", landedBookId)
        assertNull(currentReaderBackAction)
    }

    /**
     * 207-r4 修2：等待「指定目标的、壳当前真实持有的」在途请求完成登记并返回其 requestId。
     * 不能只等 currentOwnedRequestId 非空 —— singleTop 保留组合槽位时它可能仍是参数切换前的
     * 旧 id；必须三方一致（协调器当前 pending 存在、pending.destination 匹配目标书、
     * 壳发布值 == pending.requestId）才认定本 route 的新请求已登记。
     */
    private fun awaitPendingRequestFor(bookId: String): Long {
        compose.waitUntil(timeoutMillis = 15_000) {
            val pending = coordinator.pendingInspection.value
            pending != null &&
                pending.destination.bookId == bookId &&
                currentOwnedRequestId == pending.requestId
        }
        compose.waitForIdle()
        val pending = coordinator.pendingInspection.value
        assertNotNull("目标 $bookId 的在途请求必须存在", pending)
        return pending!!.requestId
    }

    /** 入口→S0→T1→T2→T1→S0→一次普通退出回原入口（每入口一个 @Test 调用本函数）。 */
    private fun runHappyChain(entry: String) {
        compose.runOnIdle {
            nav.navigate(requireNotNull(readerSourceRoute(target("book-a", 100))))
        }
        compose.waitForIdle()
        awaitReaderShell("book-a", ReaderNavigationMode.NORMAL)
        compose.runOnIdle {
            assertEquals("book-a", coordinator.state.value.normalReading?.bookId)
        }

        // S0 → T1：生产 inspect 分派（launchSingleTop）→ 真实登记 → 文档就绪提交。
        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-b", 200, null, null)),
            )
        }
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        val t1RequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle { currentReaderCommitAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("T1 请求必须真实提交", coordinator.isRequestCommitted(t1RequestId))
            assertEquals("singleTop 后上一项仍是真实入口，无 reader 残留", entry, nav.previousBackStackEntry?.destination?.route)
            assertEquals(1, coordinator.state.value.temporaryReturnStack.size)
        }

        // T1 → T2：同键重登记新请求（id 保留），提交后 LIFO=[A,B]。
        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-c", 300, null, null)),
            )
        }
        awaitReaderShell("book-c", ReaderNavigationMode.TEMPORARY)
        val t2RequestId = awaitPendingRequestFor("book-c")
        compose.runOnIdle {
            assertTrue("T2 新请求 id 必须不同于 T1 旧 id", t2RequestId != t1RequestId)
            currentReaderCommitAction?.invoke()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("T2 请求必须真实提交", coordinator.isRequestCommitted(t2RequestId))
            assertEquals(entry, nav.previousBackStackEntry?.destination?.route)
            assertEquals(2, coordinator.state.value.temporaryReturnStack.size)
        }

        // T2 → T1（生产 dispatchTemporaryReturn：LIFO 消费 + singleTop 导航）。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        compose.runOnIdle {
            assertEquals(entry, nav.previousBackStackEntry?.destination?.route)
            assertEquals(1, coordinator.state.value.temporaryReturnStack.size)
        }

        // 207-r4 修2：还原后的 T1 是新 route 实例（组合保留但参数已切回 book-b）——
        // 必须等「目标参数匹配的新请求」完成登记并显式提交（active 相同去重、不重压栈），
        // 再执行 Back；否则生产 step 按未提交分支还原 T1，永远回不到 S0。
        val restoredT1RequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle {
            assertTrue(
                "还原 T1 的新请求 id 必须不同于被消费的旧 T1/T2 id",
                restoredT1RequestId != t1RequestId && restoredT1RequestId != t2RequestId,
            )
            currentReaderCommitAction?.invoke()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("还原 T1 请求必须真实提交", coordinator.isRequestCommitted(restoredT1RequestId))
            assertEquals("active 相同去重：不重复压栈", 1, coordinator.state.value.temporaryReturnStack.size)
        }

        // T1 → S0。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        awaitReaderShell("book-a", ReaderNavigationMode.NORMAL)
        compose.runOnIdle {
            assertEquals(entry, nav.previousBackStackEntry?.destination?.route)
            assertEquals(0, coordinator.state.value.temporaryReturnStack.size)
        }

        // 一次普通退出（NORMAL → 生产决策 null → popBackStack），回真实原入口。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertLandedOnEntry(entry)
            assertFalse("协调器临时栈应已清空", coordinator.hasReturnableTarget)
        }
    }

    @Test
    fun shelfChain_originalEntryExit_backToShelfInOnePop() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("shelf")
        compose.runOnIdle { assertLandedOnEntry("shelf") }
        runHappyChain("shelf")
    }

    @Test
    fun searchChain_originalEntryExit_backToSearchInOnePop() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("search")
        compose.runOnIdle { assertLandedOnEntry("search") }
        runHappyChain("search")
    }

    @Test
    fun profileChain_originalEntryExit_backToProfileInOnePop() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("profile")
        compose.runOnIdle { assertLandedOnEntry("profile") }
        runHappyChain("profile")
    }

    /** S0→T1 pending 失败：未提交 route 返回还原 S0 普通阅读 route，LIFO/导航路径都不丢。 */
    @Test
    fun firstLevelPendingFailure_restoresNormalReaderWithoutConsumingStack() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("shelf")
        compose.runOnIdle {
            nav.navigate(requireNotNull(readerSourceRoute(target("book-a", 100))))
        }
        compose.waitForIdle()
        awaitReaderShell("book-a", ReaderNavigationMode.NORMAL)

        // T1 只登记不提交（文档一直不就绪 —— pending 失败/等待形态）。
        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-b", 200, null, null)),
            )
        }
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        val pendingT1RequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle {
            assertEquals("未提交请求不改会话：active 仍是 S0", "book-a", coordinator.state.value.active?.bookId)
        }

        // pending 失败后按返回：生产分派还原 S0 普通阅读 route。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("book-a", landedBookId)
            assertEquals(ReaderNavigationMode.NORMAL, landedMode)
            assertEquals("还原不消费 LIFO", 0, coordinator.state.value.temporaryReturnStack.size)
            assertEquals("book-a", coordinator.state.value.active?.bookId)
            // 普通 mode 登记为 null：DisposableEffect(ownedRequestId) 随变更作废在途请求。
            assertNull("失败请求必须已作废", coordinator.pendingInspection.value)
            assertFalse("失败请求从未提交", coordinator.isRequestCommitted(pendingT1RequestId))
        }

        // 导航路径未丢：一次 pop 即回原入口书架。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle { assertLandedOnEntry("shelf") }
    }

    /**
     * S0→T1成功→T2 pending 取消：还原 T1 临时会话（不消费 LIFO）；T2 真实登记并随取消作废的
     * requestId 的迟到提交被拒（不伪造 stale 请求）；恢复的 T1 去重重提交后完整 LIFO 退出。
     */
    @Test
    fun secondLevelPendingCancel_restoresCommittedSession_rejectsRealLateRequestId() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("search")
        compose.runOnIdle {
            nav.navigate(requireNotNull(readerSourceRoute(target("book-a", 100))))
        }
        compose.waitForIdle()
        awaitReaderShell("book-a", ReaderNavigationMode.NORMAL)

        // T1 真实提交。
        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-b", 200, null, null)),
            )
        }
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        val t1RequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle { currentReaderCommitAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("T1 请求必须真实提交", coordinator.isRequestCommitted(t1RequestId))
            assertEquals(1, coordinator.state.value.temporaryReturnStack.size)
        }

        // T2 只登记不提交（取消场景），捕获壳真实登记的 requestId（身份等待，不伪造）。
        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-c", 300, null, null)),
            )
        }
        awaitReaderShell("book-c", ReaderNavigationMode.TEMPORARY)
        val realT2RequestId = awaitPendingRequestFor("book-c")
        compose.runOnIdle {
            assertTrue("T2 新请求 id 必须不同于 T1 旧 id", realT2RequestId != t1RequestId)
            assertEquals("T2 未提交：LIFO 不动", 1, coordinator.state.value.temporaryReturnStack.size)
        }

        // pending 取消后按返回：还原 T1（book-b，仍临时模式），LIFO 不动。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("book-b", landedBookId)
            assertEquals(ReaderNavigationMode.TEMPORARY, landedMode)
            assertEquals("还原不消费 LIFO", 1, coordinator.state.value.temporaryReturnStack.size)
            assertEquals("book-b", coordinator.state.value.active?.bookId)
        }

        // 207-r4 修2：恢复的 T1 必须等 book-b 匹配的新请求完成登记，再显式提交
        // （目的地已是 active → 去重，不重复压栈）；不能只等 currentOwnedRequestId 非空。
        val restoredT1RequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle {
            assertTrue(
                "还原 T1 的新请求 id 必须不同于被取消的 T2 与旧 T1",
                restoredT1RequestId != realT2RequestId && restoredT1RequestId != t1RequestId,
            )
            currentReaderCommitAction?.invoke()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("还原 T1 请求必须真实提交", coordinator.isRequestCommitted(restoredT1RequestId))
            assertEquals("active 相同去重：不重复压栈", 1, coordinator.state.value.temporaryReturnStack.size)
        }

        // 迟到提交：用 T2 真实被取消的 requestId（非伪造），必须被协调器拒绝且不改会话。
        compose.runOnIdle {
            assertFalse(coordinator.commitPendingInspection(realT2RequestId!!, "book-c"))
            assertEquals("book-b", coordinator.state.value.active?.bookId)
            assertEquals(1, coordinator.state.value.temporaryReturnStack.size)
        }

        // 完整 LIFO 退出：T1 → S0 → 一次 pop 回原入口（搜索）。
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("book-a", landedBookId)
            assertEquals(0, coordinator.state.value.temporaryReturnStack.size)
        }
        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertLandedOnEntry("search")
            assertFalse(coordinator.hasReturnableTarget)
        }
    }

    /**
     * 外部入口直进临时查阅（生产「我的→阅读笔记」/灵感卡的真实形态）：pending 失败时协调器
     * 无已提交会话 → 生产分派返回 popBackStack，回到真实来源页（Profile），不伪造 reader 目标。
     */
    @Test
    fun externalTemporaryPendingFailure_popsBackToRealSourceEntry() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("profile")

        compose.runOnIdle {
            nav.navigate(requireNotNull(readerTemporaryRouteForSource("book-b", 200, null, null)))
        }
        compose.waitForIdle()
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        val pendingRequestId = awaitPendingRequestFor("book-b")
        compose.runOnIdle {
            assertNull("外部直进未提交：无已提交会话", coordinator.state.value.active)
        }

        compose.runOnIdle { currentReaderBackAction?.invoke() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertLandedOnEntry("profile")
            assertFalse(coordinator.hasReturnableTarget)
            assertNull(coordinator.state.value.active)
            assertFalse("外部 pending 失败请求从未提交", coordinator.isRequestCommitted(pendingRequestId))
        }
    }

    /**
     * 观测口径自检（Root P1 更正）：2.5.1 singleTop 复制构造**保留 entry.id**、替换 entry 对象、
     * destination 参数更新；previous entry 身份不变。以生产 [navigateReaderRouteSingleTop] 驱动。
     */
    @Test
    fun singleTopReplacement_preservesEntryId_updatesDestinationArguments() {
        coordinator = TemporaryReadingNavigationViewModel()
        launchGraph("shelf")
        compose.runOnIdle {
            nav.navigate(requireNotNull(readerSourceRoute(target("book-a", 100))))
        }
        compose.waitForIdle()
        awaitReaderShell("book-a", ReaderNavigationMode.NORMAL)

        var s0EntryId: String? = null
        var previousIdAtS0: String? = null
        compose.runOnIdle {
            s0EntryId = nav.currentBackStackEntry?.id
            previousIdAtS0 = nav.previousBackStackEntry?.id
        }
        compose.waitForIdle()

        compose.runOnIdle {
            navigateReaderRouteSingleTop(
                nav,
                requireNotNull(readerTemporaryRouteForSource("book-b", 200, null, null)),
            )
        }
        compose.waitForIdle()
        awaitReaderShell("book-b", ReaderNavigationMode.TEMPORARY)
        compose.runOnIdle {
            // 2.5.1 复制构造保留 id（NavBackStackEntry 78-85 行）：断言相等，不是改变。
            assertEquals("singleTop 复制构造保留 entry.id", s0EntryId, nav.currentBackStackEntry?.id)
            // destination 参数已更新为新目的地。
            assertEquals("book-b", nav.currentBackStackEntry?.arguments?.getString("bookId"))
            // previous entry 身份与 destination 不变（无累积）。
            assertEquals(previousIdAtS0, nav.previousBackStackEntry?.id)
            assertEquals("shelf", nav.previousBackStackEntry?.destination?.route)
            // 协调器侧：参数切换后新请求已登记（同键 LaunchedEffect），未提交前 active 仍是 S0。
            assertEquals("book-a", coordinator.state.value.active?.bookId)
        }
    }
}
