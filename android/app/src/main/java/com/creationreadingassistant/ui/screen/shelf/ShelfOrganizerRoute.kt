@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel

@Composable
internal fun ShelfOrganizerRoute(
    navController: NavHostController,
    viewModel: ShelfViewModel,
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    AppScreenScaffold(
        title = "书架整理",
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = { TextButton(onClick = viewModel::resetShelfFilters) { Text("清除筛选") } },
    ) { viewport ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(viewport),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    OrganizerRow("阅读状态", statusLabel(session.statusFilter)) {
                        navController.navigate("shelf/organizer/select/status")
                    }
                    OrganizerDivider()
                    OrganizerRow("书单", source.library.shelves.firstOrNull { it.id == session.selectedShelfId }?.name ?: "全部") {
                        navController.navigate("shelf/organizer/select/shelf")
                    }
                    OrganizerDivider()
                    OrganizerRow("分类", source.library.categories.firstOrNull { it.id == session.selectedCategoryId }?.name ?: "全部") {
                        navController.navigate("shelf/organizer/select/category")
                    }
                    OrganizerDivider()
                    OrganizerRow("标签", selectedTagSummary(session.selectedTagIds, source.library.tags.associate { it.id to it.name })) {
                        navController.navigate("shelf/organizer/select/tag")
                    }
                }
            }
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    OrganizerRow("排序", sortLabel(session.sortMode)) {
                        navController.navigate("shelf/organizer/select/sort")
                    }
                }
            }
            item {
                Text(
                    "筛选仅在本次使用中保留；排序和视图会长期保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OrganizerRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sortLabel(value: ShelfSortMode): String = when (value) {
    ShelfSortMode.RECENT -> "最近阅读"
    ShelfSortMode.IMPORTED -> "最近导入"
    ShelfSortMode.TITLE -> "书名"
    ShelfSortMode.PROGRESS -> "阅读进度"
}

internal fun selectedTagSummary(selectedIds: Set<String>, names: Map<String, String>): String = when (selectedIds.size) {
    0 -> "全部"
    1 -> names[selectedIds.first()] ?: "1 个标签"
    else -> "${selectedIds.size} 个标签"
}
