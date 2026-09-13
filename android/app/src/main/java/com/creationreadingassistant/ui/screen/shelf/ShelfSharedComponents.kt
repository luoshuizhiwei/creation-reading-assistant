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
internal const val SHELF_LIBRARY_ROUTE = "shelf/library"
internal const val SHELF_SELECTION_ROUTE = "shelf/organizer/select/{kind}"

/**
 * 「删除本书」的自述文案 —— [BookActionSheet]（长按面板）与 `BookDetailDeleteSection`
 * （详情面板）**共用同一份**，避免同一事实在两处各写一遍后分叉。
 *
 * 口径严格对齐 `feature/library/deletion/DeletionCopy` 与 `strings_deletion.xml` 的
 * `deletion_scope_delete_book_body`：「删除整本资料」移除的是书籍资料与阅读数据
 * （DB 软删除 + 关联清理），磁盘上的内部正文副本由「移除正文」（REMOVE_CONTENT）单独回收，
 * 删除动作自身不释放这部分空间。
 *
 * **不得**写成「同时移除本机正文和阅读数据」——那正是 `DeletionCopy.kt` 头注释警告的
 * 「混着说」：会让用户以为删书即回收磁盘空间，从而对数据留存做出错误判断。
 * 措辞边界由 `ShelfDeleteCopyConsistencyTest` 守住。
 */
internal const val DELETE_BOOK_SELF_DESCRIPTION = "移除书籍资料、进度、书签和笔记；不删除本地正文文件"

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
