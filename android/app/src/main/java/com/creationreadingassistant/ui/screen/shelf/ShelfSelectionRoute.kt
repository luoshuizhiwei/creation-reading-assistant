@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel

@Composable
internal fun ShelfSelectionRoute(
    kind: String,
    navController: NavHostController,
    viewModel: ShelfViewModel,
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val title = when (kind) {
        "status" -> "阅读状态"
        "shelf" -> "书单"
        "category" -> "分类"
        "tag" -> "标签"
        else -> "排序"
    }
    val options: List<Pair<String, String>> = when (kind) {
        "status" -> listOf("ALL" to "全部", "READING" to "在读", "SHELVED" to "搁置", "COMPLETED" to "已完成", "UNREAD" to "未开始")
        "shelf" -> listOf("" to "全部") + source.library.shelves.map { it.id to it.name }
        "category" -> listOf("" to "全部") + source.library.categories.map { it.id to it.name }
        "tag" -> listOf("" to "全部标签") + source.library.tags.map { it.id to it.name }
        else -> listOf("RECENT" to "最近阅读", "IMPORTED" to "最近导入", "TITLE" to "书名", "PROGRESS" to "阅读进度")
    }
    val selected = when (kind) {
        "status" -> session.statusFilter.name
        "shelf" -> session.selectedShelfId
        "category" -> session.selectedCategoryId
        "tag" -> ""
        else -> session.sortMode.name
    }
    AppScreenScaffold(
        title = title,
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = {
            if (kind == "tag") TextButton(onClick = { navController.popBackStack() }) { Text("完成") }
        },
    ) { viewport ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(viewport),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            items(options, key = { it.first }) { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                when (kind) {
                                    "status" -> viewModel.setStatusFilter(ShelfStatusFilter.valueOf(id))
                                    "shelf" -> viewModel.setSelectedShelf(id)
                                    "category" -> viewModel.setSelectedCategory(id)
                                    "tag" -> if (id.isEmpty()) viewModel.clearSelectedTags() else viewModel.toggleSelectedTag(id)
                                    else -> viewModel.setSortMode(ShelfSortMode.valueOf(id))
                                }
                                if (kind != "tag") navController.popBackStack()
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val isSelected = if (kind == "tag") {
                        if (id.isEmpty()) session.selectedTagIds.isEmpty() else id in session.selectedTagIds
                    } else {
                        selected == id
                    }
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
