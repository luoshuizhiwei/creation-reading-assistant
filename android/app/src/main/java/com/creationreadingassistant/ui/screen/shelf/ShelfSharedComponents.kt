package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

internal const val SHELF_ORGANIZER_ROUTE = "shelf/organizer"
internal const val SHELF_SEARCH_ROUTE = "shelf/search"
internal const val SHELF_IMPORT_ROUTE = "shelf/import"
internal const val SHELF_SELECTION_ROUTE = "shelf/organizer/select/{kind}"

@Composable
internal fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
}

@Composable
internal fun OrganizerDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

internal fun statusLabel(value: ShelfStatusFilter): String = when (value) {
    ShelfStatusFilter.ALL -> "全部"
    ShelfStatusFilter.READING -> "在读"
    ShelfStatusFilter.READABLE -> "本机可读"
    ShelfStatusFilter.COMPLETED -> "已完成"
    ShelfStatusFilter.UNREAD -> "未开始"
    ShelfStatusFilter.SHELVED -> "搁置"
}
