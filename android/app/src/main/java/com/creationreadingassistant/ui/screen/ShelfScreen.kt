package com.creationreadingassistant.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.screen.shelf.ShelfRoute
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel

// ========== 旧入口：转发到 ShelfRoute（纯 View 层 / MVVM 分层） ==========
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun ShelfScreen(
    navController: NavHostController,
    viewModel: ShelfViewModel = hiltViewModel(),
    /** H4：从首页继续阅读「查看详情」跳入时，打开该书的书籍详情面板。 */
    initialDetailBookId: String? = null,
    modifier: Modifier = Modifier,
) {
    ShelfRoute(
        navController = navController,
        modifier = modifier,
        viewModel = viewModel,
        initialDetailBookId = initialDetailBookId,
    )
}
