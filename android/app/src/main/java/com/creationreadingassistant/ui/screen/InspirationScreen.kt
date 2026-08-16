package com.creationreadingassistant.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.ui.screen.inspiration.InspirationRoute
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel

/**
 * 灵感页面 —— 兼容入口。
 *
 * 实际 UI / 状态 / 路由拆分已迁移至：
 *  - [ui.screen.inspiration.InspirationRoute]
 *  - [ui.screen.inspiration.InspirationScreen]
 *  - [ui.screen.inspiration.InspirationUiState]
 *  - [ui.screen.inspiration.InspirationAction]
 *  - [ui.screen.inspiration.InspirationPage]
 *  - 子组件: [ui.screen.inspiration.components.*]
 *
 * 这里只保留对旧调用点 (NavHost / HomeInspirationSection / ReaderInspirationSheet) 的兼容转发。
 */
@Composable
fun InspirationScreen(
    viewModel: InspirationViewModel = hiltViewModel(),
    initialSelectedId: String? = null,
    modifier: Modifier = Modifier,
    onOpenBook: (String) -> Unit = {},
) {
    InspirationRoute(
        viewModel = viewModel,
        initialSelectedId = initialSelectedId,
        modifier = modifier,
        onOpenBook = onOpenBook,
    )
}
