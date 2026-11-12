package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationState
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.navigation.PendingTemporaryInspection
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import com.creationreadingassistant.ui.navigation.readerReturnRoute
import com.creationreadingassistant.ui.navigation.readerTemporaryRoute
import com.creationreadingassistant.ui.navigation.resolveTemporaryReturnRoute
import com.creationreadingassistant.ui.navigation.sourceTarget
import com.creationreadingassistant.ui.screen.reader.ReaderScreenInputs
import com.creationreadingassistant.ui.screen.reader.ReaderScreenCallbacks
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderViewModel
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult

/** 连接导航与文档 ViewModel；阅读页面本身不再负责打开文件或释放文档资源。 */
@Composable
fun ReaderRoute(
    navController: NavHostController,
    bookId: String?,
    highlightId: String? = null,
    sourceLocatorJson: String? = null,
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.NORMAL,
    temporaryNavigation: TemporaryReadingNavigationViewModel? = null,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val routeState by viewModel.routeUiState.collectAsStateWithLifecycle()
    // READER-PAGES-v1：灵感保存的落库完成信号（VM 公开流），随 inputs 传给附页。
    val inspirationSaveOutcome by viewModel.inspirationSaveOutcome.collectAsStateWithLifecycle()

    // R2-J1-I：订阅协调器状态的实时快照。返回按钮可见性与统一返回动作都派生自这份状态，
    // 而不是一次性快照 —— 逐层 LIFO 返回后按钮会即时更新/消失。协调器缺省（未注入）时为 null。
    val temporaryNavState: SourceNavigationState? = if (temporaryNavigation != null) {
        temporaryNavigation.state.collectAsStateWithLifecycle().value
    } else {
        null
    }

    LaunchedEffect(bookId) {
        viewModel.onAction(ReaderAction.OpenBook(bookId.orEmpty()))
    }

    // J1-CROSSBOOK-v2 §四.2：临时查阅读者进入时**只登记请求**，不立即改会话 —— 目的地是否成立
    // 必须以「真实加载/可用结果」为边界（见下方 observer），否则一本打不开的书会被当成
    // 「已成功查阅」留在临时栈里。无有效目的地（无全局 source 坐标）时登记被拒绝，会话保持原样。
    // v3 缺陷2：登记结果（本 route 拥有的 requestId）必须保留 —— 返回/取消/离开时按它作废
    // 在途请求，迟到的结果不得改写会话/栈。
    val currentDestination = remember(bookId, sourceLocatorJson) { sourceTarget(bookId, sourceLocatorJson) }
    var ownedRequestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bookId, sourceLocatorJson, navigationMode, temporaryNavigation) {
        if (navigationMode == ReaderNavigationMode.TEMPORARY && temporaryNavigation != null) {
            ownedRequestId = temporaryNavigation.requestTemporaryInspection(currentDestination)
        }
    }
    DisposableEffect(ownedRequestId) {
        val requestId = ownedRequestId
        onDispose {
            // 用户返回/离开或被新 route 替换时，按本 route 拥有的 requestId 作废在途请求。
            // abandon 只对仍是当前在途的 requestId 生效：旧 route 的清理不会取消较新的请求，
            // 取消后的迟到结果不得改变会话/栈。
            if (requestId != null) {
                temporaryNavigation?.abandonPendingInspection(requestId)
            }
        }
    }

    // J1-CROSSBOOK-v2/v4 §四.2/§四.4：以真实加载结果作为临时会话的成功边界。
    //  - 加载成功且就是本次请求的那本书 → 提交会话（把当前 active 压入 LIFO 返回栈并切换目的地）；
    //  - 加载失败 → 只放弃本次请求：会话、普通阅读位置与既有临时栈**分毫不动**
    //    （先登记后提交的写法让「失败回退」退化成「从未写入」，不会清空合法既有栈，也不会伪造返回目标）；
    //  - 仍在加载 / 结果属于另一本书 / **请求不是本 route 拥有的** → 一律不动作
    //    （迟到的、已取消的、已作废的、以及全局最新但属于别的 route 的结果，都不得提交，
    //     也不得回滚更新的会话）。
    // v4 缺陷2：observer 只能处置**本 route 拥有的 requestId**（[ownedRequestId]），并核对完整
    // destination 与对应加载结果/身份；不得读取全局最新 pending 的 requestId 去提交。
    val pendingInspection = temporaryNavigation?.pendingInspection?.collectAsStateWithLifecycle()?.value
    val documentState = routeState.document
    LaunchedEffect(
        ownedRequestId,
        pendingInspection?.requestId,
        documentState.requestedBookId,
        documentState.isReady,
        documentState.errorMessage,
        navigationMode,
    ) {
        val coordinator = temporaryNavigation ?: return@LaunchedEffect
        val outcome = pendingInspectionOutcome(
            navigationMode = navigationMode,
            ownedRequestId = ownedRequestId,
            pending = pendingInspection,
            currentDestination = currentDestination,
            requestedBookId = documentState.requestedBookId,
            isReady = documentState.isReady,
            errorMessage = documentState.errorMessage,
        )
        when (outcome) {
            PendingInspectionOutcome.COMMIT -> {
                val requestId = ownedRequestId ?: return@LaunchedEffect
                coordinator.commitPendingInspection(requestId, documentState.requestedBookId)
            }

            PendingInspectionOutcome.ABANDON -> {
                val requestId = ownedRequestId ?: return@LaunchedEffect
                coordinator.abandonPendingInspection(requestId)
            }

            PendingInspectionOutcome.WAIT -> Unit // 仍在加载：既不算成功，也不回退。
        }
    }

    // R2-J1-I：统一的 LIFO 返回动作。返回按钮（ReturnToReading）、顶栏 Back 与系统 Back
    // 复用同一个闭包 —— 三者语义完全一致：临时查阅按协调器逐层返回；无有效返回目标（普通阅读、
    // 协调器缺省、临时栈空）时维持原有「离开阅读器」行为，绝不伪造目标。返回目标只来自
    // source locator（见 [temporaryReturnRouteStep]），不依赖 NavController back stack 数量。
    val performTemporaryReturn: () -> Unit = {
        // v3：携带当前 route 的目的地与本 route 拥有的 requestId —— 未提交/失败的当前 route
        // 不得消费合法 LIFO 栈（交还导航回退），返回动作同时按 requestId 作废在途请求。
        // 207-r3：分派提为 [dispatchTemporaryReturn]，生产回调与图测试共用同一实现。
        dispatchTemporaryReturn(
            navController,
            navigationMode,
            temporaryNavigation,
            currentDestination = currentDestination,
            ownedRequestId = ownedRequestId,
        )
    }

    ReaderScreen(
        inputs = ReaderScreenInputs(
            bookId = bookId,
            highlightId = highlightId,
            sourceLocatorJson = sourceLocatorJson,
            navigationMode = navigationMode,
            documentUiState = routeState.document,
            screenState = routeState.screen,
            highlights = routeState.highlights,
            notes = routeState.notes,
            inspirations = routeState.inspirations,
            categories = routeState.categories,
            tags = routeState.tags,
            sessions = routeState.sessions,
            readChapters = routeState.readChapters,
            txtTocRuleIdFromVm = routeState.txtTocRuleId,
            chapterLoadResult = routeState.chapterLoadResult,
            txtRuleScanResult = routeState.txtRuleScanResult,
            txtRuleScanStatus = routeState.txtRuleScanStatus,
            ruleSnapshot = routeState.ruleSnapshot,
            ruleMutationResult = routeState.ruleMutationResult,
            inspirationSaveOutcome = inspirationSaveOutcome,
        ),
        callbacks = ReaderScreenCallbacks(
            onLoadChapterBlocks = viewModel::loadChapterBlocks,
            onExtractChapterText = viewModel::extractChapterText,
            onAction = viewModel::onAction,
            onDocumentAction = viewModel::onAction,
            // 顶栏 Back / 系统 Back 与返回按钮共用同一 LIFO 返回动作。
            onBack = performTemporaryReturn,
            settingsStore = viewModel.settingsStore,
            aiClient = viewModel.aiClient,
            pageIndexStore = viewModel.pageIndexStore,
            anchorCacheStore = viewModel.anchorCacheStore,
            pagerHealthStore = viewModel.pagerHealthStore,
            pageIndexManager = viewModel.pageIndexManager,
            // R2-J1-I：reader 侧从真实、稳定的 source 位置计算路径上报精确位置（仅普通阅读 +
            // 非 initial pending + 有效坐标），协调器据此维护普通阅读位置。
            onSourcePositionChanged = { target -> temporaryNavigation?.recordNormalReading(target) },
            // R2-J1-I：实时派生自协调器临时栈（不是一次性快照）。
            hasReturnableTarget = hasReturnableTemporaryTarget(temporaryNavState),
            // 返回按钮复用与 Back 完全相同的动作。
            onTemporaryReturn = performTemporaryReturn,
            // 189-A / 207-r1：reader 内「临时查看来源」→ 导航到 temporary route。新 route 实例
            // （navigationMode=temporary）自行登记请求、真实加载成功后 commit 并压 LIFO；
            // 返回链复用 resolveTemporaryReturnRoute / readerReturnRoute。不离开 reader 域。
            // 使用 launchSingleTop = true 保证 reader 域内保持单一栈顶 entry，
            // 避免多层临时查看在 NavController 返回栈内累积旧 reader 导致最终普通退出超时回到旧 reader 界面。
            onInspectSourceTemporarily = { route ->
                // 189-A / 207-r1 / 207-r3：reader 内「临时查看来源」→ 导航到 temporary route。
                // 207-r3：分派提为 [navigateReaderRouteSingleTop]，与临时返回共用同一导航实现。
                navigateReaderRouteSingleTop(navController, route)
            },
        ),
        // CRA-READER-EXIT-SAVE-DRAIN-v1：真实退出边界（系统 Back / 顶栏 Back / 临时返回）
        // 在导航前等待本 reader 已派发的阅读进度保存正常完成；失败/取消不导航。
        awaitProgressSaves = viewModel::awaitProgressSaves,
    )
}

/**
 * R2-J1-I：统一的临时查阅 LIFO 返回步骤（纯函数，可在 JVM 测试）。
 *
 * 返回按钮（ReturnToReading）、顶栏 Back 与系统 Back 必须共用同一返回动作，避免三处各自
 * 解释「返回目标」。决策真源是 [TemporaryReadingNavigationViewModel] 的临时返回栈（逐层
 * LIFO），不依赖 NavController back stack 数量，也绝不根据页码 / 显示偏移伪造目标。
 *
 * @return 应导航到的 reader route；非临时查阅 / 协调器缺省时返回 null，调用方保持原有
 *   「离开阅读器」行为。已提交 route 命中时消费协调器一层返回栈（与 [resolveTemporaryReturnRoute]
 *   一致）；未提交 route 按 [restoreCommittedReaderSessionRoute] 还原已提交会话路径（不消费栈），
 *   无已提交会话时返回 null 交还导航回退。
 *
 * v3/v4 参数（均有缺省值，既有调用方不受影响）：
 * - [currentDestination]：当前 route 的目的地（由 [sourceTarget] 从 route 参数还原）。保留给
 *   完整 destination 核对使用；**不得**用它作为「本 route 是否已提交」的判据（缺陷 1 的根因）。
 * - [ownedRequestId]：本 route 登记的在途请求 id。返回/取消时按它作废在途请求；是否走 LIFO
 *   也以**本次请求的成功身份**（[TemporaryReadingNavigationViewModel.isRequestCommitted]）判定，
 *   而不是目标值。abandon 只对仍是当前在途的 requestId 生效，旧 route 的清理不会取消较新的请求。
 */
internal fun temporaryReturnRouteStep(
    navigationMode: ReaderNavigationMode,
    temporaryNavigation: TemporaryReadingNavigationViewModel?,
    currentDestination: SourceNavigationTarget? = null,
    ownedRequestId: Long? = null,
): String? {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return null
    if (temporaryNavigation == null) return null
    // 返回/取消/离开时按本 route 拥有的 requestId 作废在途请求。abandon 只对仍是
    // 当前在途的 requestId 生效，取消后的迟到结果不得改变会话/栈。
    if (ownedRequestId != null) {
        temporaryNavigation.abandonPendingInspection(ownedRequestId)
    }
    // 缺陷 1：只有本 route 自己的请求**真正提交成功过**才走既有 LIFO 合同。判据是请求身份，
    // 不是目标值。
    if (ownedRequestId != null && !temporaryNavigation.isRequestCommitted(ownedRequestId)) {
        // 207-r2：临时查阅 route 以 launchSingleTop 进入时，Navigation 2.5.1 的 NavController
        // 会用新 entry **替换**栈顶同 destination 的旧 reader entry（reader 域共享同一 route
        // pattern，见 AppNavigation 的 reader/{bookId}?... 声明）。被替换的原 reader entry
        // 已不在返回栈内，此时对未提交（失败/取消）route 一律 popBackStack 会退到 reader 域外，
        // 丢掉仍然合法的原 reader 页面。这里把协调器仍持有的**已提交会话**还原为导航路径：
        // 不消费 LIFO、不改会话状态，只按 active 重建 route；协调器没有已提交会话
        // （如「我的→阅读笔记」外部入口直进临时查阅）时才返回 null 交还导航回退 ——
        // 该场景下被本 route 压住的栈内前一项就是真实来源页。
        return restoreCommittedReaderSessionRoute(temporaryNavigation)
    }
    return resolveTemporaryReturnRoute(temporaryNavigation)
}

/**
 * 207-r2：把协调器当前**已提交**的会话还原为 reader route（未提交 route 的返回目标）。
 *
 * 与 [resolveTemporaryReturnRoute] 的差别：本函数**不消费**临时返回栈、不改 active/pending，
 * 只读状态重建 route —— 未提交 route 无权推进协调器，只负责把导航路径接回仍然合法的会话。
 * 语义分层与 [resolveTemporaryReturnRoute] 一致：返回后临时栈仍非空 → 继续以
 * `navigationMode=temporary` 呈现；栈已空（回到普通阅读处）→ 不带 navigationMode，并按
 * 既有合同武装 [TemporaryReturnSaveGuard]（真实位移前 12 列保持冻结）。
 * active 为 null（无已提交会话）返回 null，调用方保持 popBackStack 的原有行为。
 */
private fun restoreCommittedReaderSessionRoute(
    temporaryNavigation: TemporaryReadingNavigationViewModel,
): String? {
    val active = temporaryNavigation.state.value.active ?: return null
    val stillTemporary = temporaryNavigation.state.value.temporaryReturnStack.isNotEmpty()
    if (!stillTemporary) {
        TemporaryReturnSaveGuard.arm(
            bookId = active.bookId,
            returnOffset = active.locator.legacyOffset,
            chapterIndex = active.locator.chapterIndex,
        )
    }
    return if (stillTemporary) readerTemporaryRoute(active) else readerReturnRoute(active)
}

/**
 * R2-J1-I：是否存在可返回的临时目标。仅当临时返回栈非空时为 true —— 普通阅读位置本身不构成
 * 「待返回的临时目标」，因此栈空时恒为 false。供返回按钮可见性与 BackHandler 使用。
 */
internal fun hasReturnableTemporaryTarget(state: SourceNavigationState?): Boolean =
    state?.temporaryReturnStack?.isNotEmpty() == true

/**
 * J1-CROSSBOOK-v2/v4：一次在途临时查阅请求相对**真实加载结果**的处置判定
 * （纯函数；[ReaderRoute] 的 LaunchedEffect 直接调用它，不是平行 helper）。
 *
 * 判定顺序即合同优先级：
 * 1. 非临时查阅模式 → 不处置（普通打开书籍永远不产生临时会话）；
 * 2. 本 route 没有在途请求（ownedRequestId 为 null）→ 不处置；
 * 3. 全局最新 pending 不是本 route 拥有的那个 requestId → 不处置（**旧 route 不得用全局
 *    最新 requestId 提交新 locator 请求**）；
 * 4. 本 route 的完整 destination 与 pending 目的地不一致 → 不处置；
 * 5. 加载结果不属于本次请求的目标书 → 不处置；
 * 6. 目标就绪 → [PendingInspectionOutcome.COMMIT]（这才是「成功」的唯一形态）；
 * 7. 目标报错 → [PendingInspectionOutcome.ABANDON]（只丢弃请求，会话分毫不动）；
 * 8. 其余（仍在加载）→ [PendingInspectionOutcome.WAIT]（既不算成功，也不回退）。
 *
 * 缺陷 2 关闭方式：observer 只处置**本 route 拥有的 requestId**（[ownedRequestId]），并核对完整
 * destination（bookId + locator）与对应加载结果/身份（[requestedBookId]）；绝不读取全局最新
 * pending 的 requestId 去提交。合法同书 ready 复用由**当前 route 针对自身请求明确确认**，
 * 不为规避归属问题无条件强制重新加载。
 */
internal enum class PendingInspectionOutcome { COMMIT, ABANDON, WAIT }

internal fun pendingInspectionOutcome(
    navigationMode: ReaderNavigationMode,
    ownedRequestId: Long?,
    pending: PendingTemporaryInspection?,
    currentDestination: SourceNavigationTarget?,
    requestedBookId: String?,
    isReady: Boolean,
    errorMessage: String?,
): PendingInspectionOutcome {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return PendingInspectionOutcome.WAIT
    // 本 route 没有在途请求 → 不处置。
    if (ownedRequestId == null) return PendingInspectionOutcome.WAIT
    // 缺陷2：只处置本 route 拥有的 requestId，不能用全局最新 pending 的 requestId 代替结果归属。
    if (pending == null) return PendingInspectionOutcome.WAIT
    if (pending.requestId != ownedRequestId) return PendingInspectionOutcome.WAIT
    // 核对**完整 destination**（bookId + locator），不能只比 bookId。
    if (currentDestination == null || pending.destination != currentDestination) {
        return PendingInspectionOutcome.WAIT
    }
    // 加载结果必须属于本次请求的目标书（加载身份 = requestedBookId）。
    if (requestedBookId != pending.destination.bookId) return PendingInspectionOutcome.WAIT
    return when {
        isReady -> PendingInspectionOutcome.COMMIT
        errorMessage != null -> PendingInspectionOutcome.ABANDON
        else -> PendingInspectionOutcome.WAIT
    }
}

/**
 * 207-r3：reader 域内导航分派的唯一实现（生产回调与图测试共用，不新增策略层）。
 *
 * 临时查阅进入（[ReaderRoute] 的 `onInspectSourceTemporarily`）与临时返回（LIFO/还原 route）
 * 都以 `launchSingleTop = true` 导航：2.5.1 对同 destination 复制替换栈顶 entry 并保留
 * entry.id（NavController.kt:1702-1713 调 NavBackStackEntry 复制构造，id 沿用 78-85 行），
 * reader 域内不累积旧 entry。语义与提取前逐字节一致。
 */
internal fun navigateReaderRouteSingleTop(navController: NavHostController, route: String) {
    navController.navigate(route) { launchSingleTop = true }
}

/**
 * 207-r3：「生产返回决策 → navigate/pop」的实际分派体（[ReaderRoute] 的
 * `performTemporaryReturn` 与图测试共用同一实现）。
 *
 * 决策真源仍是 [temporaryReturnRouteStep]（含 207-r2 未提交还原语义）：返回非空 →
 * [navigateReaderRouteSingleTop]；null → 交还 [NavHostController.popBackStack]。
 * 保存 await 边界不在此处：ReaderScreen 在调用 onBack 前已完成 awaitProgressSaves，
 * 本函数不改变任何保存语义。
 */
internal fun dispatchTemporaryReturn(
    navController: NavHostController,
    navigationMode: ReaderNavigationMode,
    temporaryNavigation: TemporaryReadingNavigationViewModel?,
    currentDestination: SourceNavigationTarget? = null,
    ownedRequestId: Long? = null,
) {
    val returnRoute = temporaryReturnRouteStep(
        navigationMode,
        temporaryNavigation,
        currentDestination = currentDestination,
        ownedRequestId = ownedRequestId,
    )
    if (returnRoute != null) {
        navigateReaderRouteSingleTop(navController, returnRoute)
    } else {
        navController.popBackStack()
    }
}
