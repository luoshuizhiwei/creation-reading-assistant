package com.creationreadingassistant.ui.screen.readinghistory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.screen.shelf.bookNotReadyLabel
import com.creationreadingassistant.ui.screen.shelf.BookActionSheet
import com.creationreadingassistant.ui.viewmodel.MyReadingViewModel
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel

@Composable
fun MyReadingRoute(
    navController: NavHostController,
    viewModel: MyReadingViewModel = hiltViewModel(),
    shelfViewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var managedBook by remember { mutableStateOf<BookEntity?>(null) }
    var restorePrompt by remember { mutableStateOf<BookEntity?>(null) }
    var deletePrompt by remember { mutableStateOf<BookEntity?>(null) }
    var repairBookId by remember { mutableStateOf<String?>(null) }
    val repairLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = repairBookId
        if (uri != null && id != null) shelfViewModel.reselectFile(id, uri) {}
        repairBookId = null
    }

    fun requestOpen(book: BookEntity) {
        val progress = state.months.asSequence().flatMap { it.items.asSequence() }
            .firstOrNull { it.book.id == book.id }?.progress
        if (progress?.readingState == ReadingCompletionState.SHELVED) {
            restorePrompt = book
        } else if (bookNotReadyLabel(book) == null) {
            navController.navigate("reader/${book.id}")
        } else {
            managedBook = book
        }
    }

    MyReadingScreen(
        state = state,
        onAction = { action ->
            when (action) {
                MyReadingAction.Back -> navController.popBackStack()
                is MyReadingAction.UpdateQuery -> viewModel.setQuery(action.value)
                is MyReadingAction.SelectFilter -> viewModel.setFilter(action.value)
                is MyReadingAction.OpenBook -> requestOpen(action.book)
                is MyReadingAction.ManageBook -> managedBook = action.book
            }
        },
    )

    val book = managedBook
    if (book != null) {
        val progressById = state.months.flatMap { it.items }.mapNotNull { item -> item.progress?.let { item.book.id to it } }.toMap()
        BookActionSheet(
            book = book,
            progressById = progressById,
            onDismiss = { managedBook = null },
            onContinue = { managedBook = null; requestOpen(it) },
            onDownload = { shelfViewModel.downloadBookContent(it.id) {}; managedBook = null },
            onRepair = {
                managedBook = null
                repairBookId = it.id
                repairLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onOpenDetail = { id -> managedBook = null; navController.navigate("shelf/detail/$id") },
            onDelete = { managedBook = null; deletePrompt = book },
        )
    }

    val shelved = restorePrompt
    if (shelved != null) {
        GlassAlertDialog(
            onDismissRequest = { restorePrompt = null },
            title = { androidx.compose.material3.Text("恢复阅读状态？") },
            text = { androidx.compose.material3.Text("恢复为在读后会保留原有进度、时长、笔记和灵感，并从当前位置继续。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    restorePrompt = null
                    shelfViewModel.restoreReading(shelved.id) { success, _ ->
                        if (success && bookNotReadyLabel(shelved) == null) navController.navigate("reader/${shelved.id}")
                    }
                }) { androidx.compose.material3.Text("恢复并阅读") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restorePrompt = null }) {
                    androidx.compose.material3.Text("暂不恢复")
                }
            },
        )
    }

    val deleting = deletePrompt
    if (deleting != null) {
        GlassAlertDialog(
            onDismissRequest = { deletePrompt = null },
            title = { androidx.compose.material3.Text("删除书籍？") },
            text = { androidx.compose.material3.Text("本地正文、阅读进度、笔记和灵感关联数据会一并移除，此操作不可撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    deletePrompt = null
                    shelfViewModel.deleteBook(deleting.id)
                }) { androidx.compose.material3.Text("删除", color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletePrompt = null }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
}
