package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationState
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
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

    // R2-J1.3：临时查阅读者进入时，先把「当前普通/活动 source target」推进 J1.1 协调器的 LIFO
    // 返回栈，再呈现目的地（允许跨书）。仅当目的地与协调器当前 active 不同才推进：这样返回
    // 到中间层时会再次以 temporary 模式进入，但不会重复压栈。无有效目的地（无全局 source 坐标）
    // 时不推进；协调器为空时保持「无可返回位置 → 正常离开 reader」的降级行为。
    LaunchedEffect(bookId, sourceLocatorJson, navigationMode) {
        if (navigationMode == ReaderNavigationMode.TEMPORARY && temporaryNavigation != null) {
            val destination = sourceTarget(bookId, sourceLocatorJson)
            if (destination != null && destination != temporaryNavigation.state.value.active) {
                temporaryNavigation.beginTemporaryInspection(destination)
            }
        }
    }

    // R2-J1-I：统一的 LIFO 返回动作。返回按钮（ReturnToReading）、顶栏 Back 与系统 Back
    // 复用同一个闭包 —— 三者语义完全一致：临时查阅按协调器逐层返回；无有效返回目标（普通阅读、
    // 协调器缺省、临时栈空）时维持原有「离开阅读器」行为，绝不伪造目标。返回目标只来自
    // source locator（见 [temporaryReturnRouteStep]），不依赖 NavController back stack 数量。
    val performTemporaryReturn: () -> Unit = {
        val returnRoute = temporaryReturnRouteStep(navigationMode, temporaryNavigation)
        if (returnRoute != null) {
            navController.navigate(returnRoute) { launchSingleTop = true }
        } else {
            navController.popBackStack()
        }
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
        ),
    )
}

/**
 * R2-J1-I：统一的临时查阅 LIFO 返回步骤（纯函数，可在 JVM 测试）。
 *
 * 返回按钮（ReturnToReading）、顶栏 Back 与系统 Back 必须共用同一返回动作，避免三处各自
 * 解释「返回目标」。决策真源是 [TemporaryReadingNavigationViewModel] 的临时返回栈（逐层
 * LIFO），不依赖 NavController back stack 数量，也绝不根据页码 / 显示偏移伪造目标。
 *
 * @return 应导航到的 reader route；非临时查阅 / 协调器缺省 / 临时栈空时返回 null，调用方保持
 *   原有「离开阅读器」行为。命中时本函数会消费协调器一层返回栈（与 [resolveTemporaryReturnRoute] 一致）。
 */
internal fun temporaryReturnRouteStep(
    navigationMode: ReaderNavigationMode,
    temporaryNavigation: TemporaryReadingNavigationViewModel?,
): String? {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return null
    if (temporaryNavigation == null) return null
    return resolveTemporaryReturnRoute(temporaryNavigation)
}

/**
 * R2-J1-I：是否存在可返回的临时目标。仅当临时返回栈非空时为 true —— 普通阅读位置本身不构成
 * 「待返回的临时目标」，因此栈空时恒为 false。供返回按钮可见性与 BackHandler 使用。
 */
internal fun hasReturnableTemporaryTarget(state: SourceNavigationState?): Boolean =
    state?.temporaryReturnStack?.isNotEmpty() == true
