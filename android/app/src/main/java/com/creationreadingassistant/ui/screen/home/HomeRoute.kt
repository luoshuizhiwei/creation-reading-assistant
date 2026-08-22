package com.creationreadingassistant.ui.screen.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.screen.home.HomeContinueSheet
import com.creationreadingassistant.ui.theme.LocalVisualStyle
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import com.creationreadingassistant.ui.viewmodel.HomeViewModel
import com.creationreadingassistant.ui.util.bookNotReadyLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home 页 **View 层（Route）**——拥有所有副作用、协程、导航、ViewModel、Snackbar、readiness 检查、ContinueSheet。
 *
 * ## 本层消费的内容（纯 Screen 层禁止出现）
 * - `hiltViewModel<HomeViewModel>()`、`hiltViewModel<BookViewModel>()`
 * - `navController.navigate(...)`
 * - `collectAsStateWithLifecycle()`
 * - `SnackbarHostState` + `scope.launch { showSnackbar(...) }`
 * - `bookNotReadyLabel(book)` 做 readiness 检查
 * - `HomeContinueSheet`（含 ContinueSheet 的 ViewModel 交互）
 * - MainActivity 的 onHomeContentReady 回调（首屏骨架超时）
 *
 * ## 数据流
 * ViewModel.HomeUiState → 本 Route 合并纯 UI 状态（showSkeleton、isContinueSheetOpen）
 *                      → com.creationreadingassistant.ui.screen.home.HomeUiState
 *                      → HomeScreen(uiState, onAction)（纯渲染）
 *
 * ## 交互流
 * HomeScreen 点击 → HomeAction sealed → 本 Route 的 handle(action) →
 *  Snackbar / navController.navigate / sheet开关 / viewModel 调用。
 */
@Composable
fun HomeRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    bookViewModel: BookViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val data by homeViewModel.uiState.collectAsStateWithLifecycle()

    // 纯 UI 状态（骨架、sheet 开关）——非业务数据，由 Route 自己维护，不进 ViewModel
    var showSkeleton by remember { mutableStateOf(true) }
    var isContinueSheetOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 首屏骨架超时：首次组合 350ms 后自动关闭（如果 books 仍为空，就直接显示空态）
    LaunchedEffect(Unit) { delay(350); showSkeleton = false }
    LaunchedEffect(data.books) { if (data.books.isNotEmpty()) showSkeleton = false }

    // MainActivity 首屏渲染回调（旧 HomeScreen 行为保留）
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(data.isReady) {
        if (data.isReady) (activity as? MainActivity)?.onHomeContentReady()
    }

    // 把 ViewModel 源状态 + 纯 UI 状态 合并为纯 Screen 层的 HomeUiState
    val uiState = HomeUiState(
        showSkeleton = showSkeleton,
        isContinueSheetOpen = isContinueSheetOpen,
        books = data.books,
        progressById = data.progressById,
        sessionsByBook = data.sessionsByBook,
        removedContinueIds = data.removedContinueIds,
        continueBooks = data.continueBooks,
        completedBooks = data.completedBooks,
        recentInspirations = data.recentInspirations,
        thisWeekNew = data.thisWeekNew,
        readingCount = data.readingCount,
        completedCount = data.completedBooks.size,
        totalReadBooksCount = data.totalReadBooksCount,
        totalReadingMs = data.totalReadingMs,
        todayReadingMs = data.todayReadingMs,
        dailyGoalMinutes = data.dailyGoalMinutes,
    )

    // —— 纯 Screen 只拿 HomeUiState + onAction，不感知本 Route 的任何副作用 ——
    androidx.compose.foundation.layout.Box(modifier = modifier.testTag("home-route")) {
        // 通过 LocalHomeSnackbarHolder 把 snackbarHostState 注入 Pure Screen 层 AppScreenScaffold 的 snackbarHost slot
        CompositionLocalProvider(LocalHomeSnackbarHolder provides snackbarHostState) {
            HomeScreen(
                state = uiState,
                onAction = { action -> handleHomeAction(
                    action = action,
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    onToggleContinueSheet = { open -> isContinueSheetOpen = open },
                ) },
            )
        }
    }

    // 继续阅读管理 sheet（Route 层控制开关 + 传 nav/bookViewModel）
    if (isContinueSheetOpen) {
        HomeContinueSheet(
            navController = navController,
            books = data.books,
            progressById = data.progressById,
            sessions = data.sessionsByBook,
            removedIds = data.removedContinueIds,
            viewModel = bookViewModel,
            onDismiss = { isContinueSheetOpen = false },
        )
    }
}

/** HomeAction → 副作用分发器。所有导航/Snackbar/sheet 开关都走这里。 */
private fun handleHomeAction(
    action: HomeAction,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onToggleContinueSheet: (Boolean) -> Unit,
) {
    when (action) {
        HomeAction.OpenSearch -> navController.navigate("search")
        HomeAction.OpenMyReading -> navController.navigate("my-reading")
        HomeAction.OpenContinueSheet -> onToggleContinueSheet(true)
        HomeAction.DismissContinueSheet -> onToggleContinueSheet(false)
        is HomeAction.OpenBook -> openBookReadiness(action.book, navController, snackbarHostState, scope)
        HomeAction.NavigateToShelf -> navController.navigate("shelf")
        HomeAction.OpenCompletedBooks -> navController.navigate("home/completed")
        HomeAction.OpenRecentInspirations -> navController.navigate("home/inspirations")
        is HomeAction.NavigateToInspirationDetail -> navController.navigate("inspiration?inspId=${action.inspId}")
    }
}

/** 打开阅读器前的 readiness 校验（对齐网页 getBookReadiness）。未就绪时 Snackbar 提示。 */
private fun openBookReadiness(
    book: BookEntity,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val label = bookNotReadyLabel(book)
    if (label != null) {
        scope.launch {
            snackbarHostState.showSnackbar("《${book.title}》${label}，暂时无法打开。请检查文件状态或重新导入/下载正文。")
        }
        return
    }
    navController.navigate("reader/${book.id}")
}

/** 通过 CompositionLocal 从 Route 层把 SnackbarHostState 桥给 Pure Screen 层的 AppScreenScaffold snackbarHost slot。
 *  Pure Screen 默认 LocalHomeSnackbarHolder = null，因此 snackbarHost 是一个空占位，满足 "Pure Screen 不持有 SnackbarHostState"。 */
internal val LocalHomeSnackbarHolder =
    androidx.compose.runtime.staticCompositionLocalOf<SnackbarHostState?> { null }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
