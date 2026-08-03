package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val routeState by viewModel.routeUiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) {
        viewModel.onAction(ReaderAction.OpenBook(bookId.orEmpty()))
    }

    ReaderScreen(
        inputs = ReaderScreenInputs(
            bookId = bookId,
            highlightId = highlightId,
            documentUiState = routeState.document,
            screenState = routeState.screen,
            highlights = routeState.highlights,
            notes = routeState.notes,
            inspirations = routeState.inspirations,
            categories = routeState.categories,
            tags = routeState.tags,
            sessions = routeState.sessions,
            txtTocRuleIdFromVm = routeState.txtTocRuleId,
            chapterLoadResult = routeState.chapterLoadResult,
            txtRuleScanResult = routeState.txtRuleScanResult,
        ),
        callbacks = ReaderScreenCallbacks(
            onLoadChapterBlocks = viewModel::loadChapterBlocks,
            onExtractChapterText = viewModel::extractChapterText,
            onAction = viewModel::onAction,
            onDocumentAction = viewModel::onAction,
            onBack = { navController.popBackStack() },
            settingsStore = viewModel.settingsStore,
            aiClient = viewModel.aiClient,
            pageIndexStore = viewModel.pageIndexStore,
            anchorCacheStore = viewModel.anchorCacheStore,
            pagerHealthStore = viewModel.pagerHealthStore,
        ),
    )
}
