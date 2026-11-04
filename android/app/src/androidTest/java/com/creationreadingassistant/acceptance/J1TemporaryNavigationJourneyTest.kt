package com.creationreadingassistant.acceptance

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
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
import com.creationreadingassistant.ui.navigation.PendingTemporaryInspection
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import com.creationreadingassistant.ui.navigation.decodeSourceLocatorFromRoute
import com.creationreadingassistant.ui.navigation.readerNavigationMode
import com.creationreadingassistant.ui.navigation.readerReturnRoute
import com.creationreadingassistant.ui.navigation.readerSourceRoute
import com.creationreadingassistant.ui.navigation.readerTemporaryRoute
import com.creationreadingassistant.ui.navigation.resolveTemporaryReturnRoute
import com.creationreadingassistant.ui.navigation.sourceTarget
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 【辅助 Probe 测试声明】
 *
 * 依据 Codex 联合冻结核查与 CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v4 任务书要求：
 * 本测试类使用私有 [J1ReaderProbe] 图，复制 observer 并手动提供 documentState/request，
 * 仅能作为轻量级的**辅助接缝测试**保留，**不得计入真实阅读器设备验收**。
 * 真实生产阅读器旅程验收由 [J1RealReaderJourneyTest] 承担。
 *
 * ## 这一层证明什么（仅限辅助接缝）
 * 用真实 `NavHostController` + 真实 `NavHost` 跑 route 编解码与基础状态机。
 */
@RunWith(AndroidJUnit4::class)
class J1TemporaryNavigationJourneyTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var coordinator: TemporaryReadingNavigationViewModel
    private lateinit var nav: NavHostController
    private val docStates = mutableStateMapOf<String, DocState>()
    private var landedRoute: String? = null
    private var performReturn: (() -> Unit)? = null

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    private fun landing(bookId: String, mode: ReaderNavigationMode): String = "reader/$bookId#${mode.name}"

    private fun launch() {
        coordinator = TemporaryReadingNavigationViewModel()
        compose.setContent {
            val controller = rememberNavController()
            SideEffect { nav = controller }
            NavHost(navController = controller, startDestination = "shelf") {
                composable("shelf") {
                    SideEffect { landedRoute = "shelf" }
                    Text("shelf")
                }
                composable(
                    route = "reader/{bookId}?highlightId={highlightId}&sourceLocator={sourceLocator}&navigationMode={navigationMode}",
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("highlightId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("sourceLocator") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("navigationMode") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    J1ReaderProbe(
                        entry = entry,
                        navController = controller,
                        coordinator = coordinator,
                        docStateOf = { bid -> docStates[bid.orEmpty()] ?: DocState(null, false, null) },
                        onLanding = { landedRoute = it },
                        onReturnAction = { performReturn = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun navigateNormal(bookId: String, offset: Int) {
        nav.navigate(requireNotNull(readerSourceRoute(target(bookId, offset))))
    }

    private fun navigateTemporary(bookId: String, offset: Int) {
        nav.navigate(requireNotNull(readerTemporaryRoute(target(bookId, offset)))) { launchSingleTop = false }
    }

    private fun returnNow() {
        (performReturn ?: error("返回动作尚未接线")).invoke()
    }

    private fun openNormalA() {
        compose.runOnIdle {
            docStates["book-a"] = DocState("book-a", true, null)
            navigateNormal("book-a", 100)
        }
        compose.waitForIdle()
    }

    private fun openCommittedB() {
        compose.runOnIdle {
            docStates["book-b"] = DocState("book-b", true, null)
            navigateTemporary("book-b", 200)
        }
        compose.waitForIdle()
    }

    // ------------------------------------------------------------------ 场景

    /** 场景 1：A→B 临时查阅成功，再正常返回 A。 */
    @Test
    fun aToBCommitThenReturnLandsBackOnA() {
        launch()
        openNormalA()
        openCommittedB()

        compose.runOnIdle {
            assertEquals("B 成功查阅后 active 应为 B", target("book-b", 200), coordinator.state.value.active)
            assertEquals("A 应被压入 LIFO 返回栈", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
            assertEquals("当前落点应为临时模式 B", landing("book-b", ReaderNavigationMode.TEMPORARY), landedRoute)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals("返回后应落在 A（普通模式）", landing("book-a", ReaderNavigationMode.NORMAL), landedRoute)
            assertEquals("返回后临时栈应清空", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
            assertEquals("返回后 active 应为 A", target("book-a", 100), coordinator.state.value.active)
        }
    }

    /** 场景 2：B→C 加载失败返回，恢复 B 且不消耗合法栈。 */
    @Test
    fun failedSecondHopReturnsToBWithoutConsumingLegitStack() {
        launch()
        openNormalA()
        openCommittedB()

        compose.runOnIdle {
            docStates["book-c"] = DocState("book-c", false, "无法打开")
            navigateTemporary("book-c", 300)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("失败的 C 不得进入会话", target("book-b", 200), coordinator.state.value.active)
            assertEquals("合法栈必须保持 [A]", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
            assertNull("失败请求不得残留 pending", coordinator.pendingInspection.value)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals("返回应落在 B（仍为临时模式）", landing("book-b", ReaderNavigationMode.TEMPORARY), landedRoute)
            assertEquals("返回后 active 应为 B", target("book-b", 200), coordinator.state.value.active)
            assertEquals("未提交的 C 不得消费合法栈", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
        }
    }

    /** 场景 3：重复**旧 active** 目标的未提交返回，不得消费旧栈。 */
    @Test
    fun uncommittedReRequestOfCurrentActiveDoesNotConsumeStack() {
        launch()
        openNormalA()
        openCommittedB()

        compose.runOnIdle {
            docStates["book-b"] = DocState("book-b", false, null)
            navigateTemporary("book-b", 200)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull("未提交的重复 active 请求不得残留 pending", coordinator.pendingInspection.value)
            assertEquals("未提交请求不得改动栈", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals("未提交的重复 active 返回必须交还导航回退", landing("book-b", ReaderNavigationMode.TEMPORARY), landedRoute)
            assertEquals("旧栈不得被消费", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
            assertEquals("active 不得被改动", target("book-b", 200), coordinator.state.value.active)
        }
    }

    /** 场景 4：重复**栈内成员**目标的未提交返回，不得消费旧栈。 */
    @Test
    fun uncommittedReRequestOfStackMemberDoesNotConsumeStack() {
        launch()
        openNormalA()
        openCommittedB()

        compose.runOnIdle {
            docStates["book-a"] = DocState("book-a", false, null)
            navigateTemporary("book-a", 100)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull("未提交的重复栈成员请求不得残留 pending", coordinator.pendingInspection.value)
            assertEquals("未提交请求不得改动栈", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals("未提交的重复栈成员返回必须交还导航回退", landing("book-b", ReaderNavigationMode.TEMPORARY), landedRoute)
            assertEquals("旧栈不得被消费", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
            assertEquals("active 不得被改动", target("book-b", 200), coordinator.state.value.active)
        }
    }

    /** 场景 5：旧 route 的就绪结果不得提交**同书不同 locator** 的新请求。 */
    @Test
    fun oldRouteReadyResultCannotCommitNewerSameBookLocatorRequest() {
        launch()
        openNormalA()

        compose.runOnIdle {
            docStates["book-b"] = DocState("book-b", false, null)
            navigateTemporary("book-b", 200)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("未就绪不得提交", target("book-a", 100), coordinator.state.value.active)
            assertEquals("未就绪不得压栈", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { coordinator.requestTemporaryInspection(target("book-b", 900)) }
        compose.waitForIdle()

        compose.runOnIdle { docStates["book-b"] = DocState("book-b", true, null) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("旧 route 不得提交新 locator 请求", target("book-a", 100), coordinator.state.value.active)
            assertEquals("旧 route 不得压栈", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
            assertEquals(
                "在途请求必须仍是新的 locator 请求",
                target("book-b", 900),
                coordinator.pendingInspection.value?.destination,
            )
        }

        compose.runOnIdle {
            val rid = requireNotNull(coordinator.pendingInspection.value).requestId
            assertTrue(coordinator.commitPendingInspection(rid, "book-b"))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("新 locator 请求提交后 active 应为新位点", target("book-b", 900), coordinator.state.value.active)
            assertEquals("应把 A 压入返回栈", listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
        }
    }

    /** 场景 6：合法提交后按既有 LIFO 逐层返回（多层）。 */
    @Test
    fun committedMultiLevelLifoReturnsLayerByLayer() {
        launch()
        openNormalA()
        openCommittedB()
        compose.runOnIdle {
            docStates["book-c"] = DocState("book-c", true, null)
            navigateTemporary("book-c", 300)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(target("book-c", 300), coordinator.state.value.active)
            assertEquals(listOf(target("book-a", 100), target("book-b", 200)), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("第一层返回应落在 B（临时模式）", landing("book-b", ReaderNavigationMode.TEMPORARY), landedRoute)
            assertEquals(listOf(target("book-a", 100)), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { returnNow() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("第二层返回应落在 A（普通模式）", landing("book-a", ReaderNavigationMode.NORMAL), landedRoute)
            assertEquals("返回到底后栈应清空", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
        }
    }

    /** 场景 7：取消（离开 route）后迟到结果被拒绝，普通模式不退化。 */
    @Test
    fun cancelledRequestRejectsLateResultAndNormalModeStaysIntact() {
        launch()
        openNormalA()

        compose.runOnIdle {
            docStates["book-b"] = DocState("book-b", false, null)
            navigateTemporary("book-b", 200)
        }
        compose.waitForIdle()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull("取消后不得残留 pending", coordinator.pendingInspection.value)
            assertEquals("取消不得改动会话", target("book-a", 100), coordinator.state.value.active)
            assertEquals("取消不得制造返回栈", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { docStates["book-b"] = DocState("book-b", true, null) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("迟到结果不得提交", target("book-a", 100), coordinator.state.value.active)
            assertEquals("迟到结果不得压栈", emptyList<SourceNavigationTarget>(), coordinator.state.value.temporaryReturnStack)
        }

        compose.runOnIdle { navigateTemporary("book-b", 200) }
        compose.waitForIdle()
        compose.runOnIdle { returnNow() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("普通模式未退化", landing("book-a", ReaderNavigationMode.NORMAL), landedRoute)
        }
    }

    /** 场景 8：临时查阅期间不得污染普通阅读位置。 */
    @Test
    fun temporaryInspectionNeverPollutesNormalReadingPosition() {
        launch()
        openNormalA()
        compose.runOnIdle {
            assertEquals("普通阅读位置应为 A@100", target("book-a", 100), coordinator.state.value.normalReading)
        }
        openCommittedB()
        compose.runOnIdle {
            assertEquals("临时期间 active 为 B", target("book-b", 200), coordinator.state.value.active)
            assertEquals("临时期间普通阅读位置必须仍是 A@100", target("book-a", 100), coordinator.state.value.normalReading)
        }
        compose.runOnIdle {
            docStates["book-c"] = DocState("book-c", true, null)
            navigateTemporary("book-c", 300)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("多层临时期间普通阅读位置必须仍是 A@100", target("book-a", 100), coordinator.state.value.normalReading)
        }
    }

    /** 附：route 编解码自洽（真实生产函数，不含 UI）。 */
    @Test
    fun productionRouteEncodingRoundTripsBookAndLocator() {
        val a = target("book-a", 100)
        val temp = requireNotNull(readerTemporaryRoute(a))
        val normal = requireNotNull(readerReturnRoute(a))
        assertTrue("临时 route 必须带 navigationMode", temp.contains("navigationMode=temporary"))
        assertEquals(ReaderNavigationMode.TEMPORARY, readerNavigationMode("temporary"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(null))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("normal"))
        assertEquals(100, decodeSourceLocatorFromRoute(temp)?.legacyOffset)
        assertEquals(100, decodeSourceLocatorFromRoute(normal)?.legacyOffset)
    }
}

private data class DocState(val requestedBookId: String?, val isReady: Boolean, val errorMessage: String?)

private enum class ProbeOutcome { COMMIT, ABANDON, WAIT }

private fun inspectionOutcome(
    navigationMode: ReaderNavigationMode,
    ownedRequestId: Long?,
    pending: PendingTemporaryInspection?,
    currentDestination: SourceNavigationTarget?,
    requestedBookId: String?,
    isReady: Boolean,
    errorMessage: String?,
): ProbeOutcome {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return ProbeOutcome.WAIT
    if (ownedRequestId == null) return ProbeOutcome.WAIT
    if (pending == null) return ProbeOutcome.WAIT
    if (pending.requestId != ownedRequestId) return ProbeOutcome.WAIT
    if (currentDestination == null || pending.destination != currentDestination) return ProbeOutcome.WAIT
    if (requestedBookId != pending.destination.bookId) return ProbeOutcome.WAIT
    return when {
        isReady -> ProbeOutcome.COMMIT
        errorMessage != null -> ProbeOutcome.ABANDON
        else -> ProbeOutcome.WAIT
    }
}

private fun returnStep(
    navigationMode: ReaderNavigationMode,
    coordinator: TemporaryReadingNavigationViewModel?,
    currentDestination: SourceNavigationTarget?,
    ownedRequestId: Long?,
): String? {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return null
    if (coordinator == null) return null
    if (ownedRequestId != null) coordinator.abandonPendingInspection(ownedRequestId)
    if (ownedRequestId != null && !coordinator.isRequestCommitted(ownedRequestId)) return null
    return resolveTemporaryReturnRoute(coordinator)
}

@Composable
private fun J1ReaderProbe(
    entry: NavBackStackEntry,
    navController: NavHostController,
    coordinator: TemporaryReadingNavigationViewModel,
    docStateOf: (String?) -> DocState,
    onLanding: (String) -> Unit,
    onReturnAction: ((() -> Unit)?) -> Unit,
) {
    val bookId = entry.arguments?.getString("bookId")
    val sourceLocatorJson = entry.arguments?.getString("sourceLocator")
    val navigationMode = readerNavigationMode(entry.arguments?.getString("navigationMode"))
    val destination = remember(bookId, sourceLocatorJson) { sourceTarget(bookId, sourceLocatorJson) }

    var ownedRequestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bookId, sourceLocatorJson, navigationMode) {
        if (navigationMode == ReaderNavigationMode.TEMPORARY) {
            ownedRequestId = coordinator.requestTemporaryInspection(destination)
        }
    }
    DisposableEffect(ownedRequestId) {
        val rid = ownedRequestId
        onDispose { if (rid != null) coordinator.abandonPendingInspection(rid) }
    }

    val pending = coordinator.pendingInspection.collectAsState().value
    val doc = docStateOf(bookId)

    LaunchedEffect(navigationMode, destination, doc.isReady) {
        if (navigationMode == ReaderNavigationMode.NORMAL && doc.isReady && destination != null) {
            coordinator.recordNormalReading(destination)
        }
    }

    LaunchedEffect(ownedRequestId, pending?.requestId, doc.requestedBookId, doc.isReady, doc.errorMessage, navigationMode) {
        when (
            inspectionOutcome(
                navigationMode = navigationMode,
                ownedRequestId = ownedRequestId,
                pending = pending,
                currentDestination = destination,
                requestedBookId = doc.requestedBookId,
                isReady = doc.isReady,
                errorMessage = doc.errorMessage,
            )
        ) {
            ProbeOutcome.COMMIT ->
                ownedRequestId?.let { coordinator.commitPendingInspection(it, doc.requestedBookId) }

            ProbeOutcome.ABANDON ->
                ownedRequestId?.let { coordinator.abandonPendingInspection(it) }

            ProbeOutcome.WAIT -> Unit
        }
    }

    val returnAction: () -> Unit = {
        val route = returnStep(navigationMode, coordinator, destination, ownedRequestId)
        if (route != null) {
            navController.navigate(route) { launchSingleTop = true }
        } else {
            navController.popBackStack()
        }
    }
    SideEffect {
        onReturnAction(returnAction)
        onLanding("reader/$bookId#${navigationMode.name}")
    }
    Text("probe")
}

