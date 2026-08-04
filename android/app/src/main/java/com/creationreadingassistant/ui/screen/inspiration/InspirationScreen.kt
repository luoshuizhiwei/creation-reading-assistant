package com.creationreadingassistant.ui.screen.inspiration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.inspiration.components.InspirationDetail
import com.creationreadingassistant.ui.screen.inspiration.components.InspirationEditor
import com.creationreadingassistant.ui.screen.inspiration.components.InspirationList
import com.creationreadingassistant.ui.screen.inspiration.components.rememberDetailToolbar
import com.creationreadingassistant.ui.screen.inspiration.components.rememberEditorToolbar
import com.creationreadingassistant.ui.screen.inspiration.components.rememberListToolbar
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InspirationScreen(
    state: InspirationUiState,
    items: List<InspirationEntity>,
    books: List<BookEntity>,
    filteredItems: List<InspirationEntity>,
    availableTypes: List<Pair<String, String>>,
    typeCounts: Map<String, Int>,
    sortMode: String,
    selectedEntity: InspirationEntity?,
    editingEntity: InspirationEntity?,
    actionSheetItem: InspirationEntity?,
    viewModel: InspirationViewModel,
    onAction: (InspirationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceOf: (InspirationEntity) -> InspirationSourceInfo? = remember(viewModel) {
        { viewModel.sourceOf(it) }
    }
    val tagsOf: (InspirationEntity) -> List<String> = remember(viewModel) {
        { viewModel.tagsOf(it) }
    }

    val listToolbar = rememberListToolbar(
        searchOpen = state.searchOpen,
        query = state.query,
        onQueryChange = { onAction(InspirationAction.UpdateQuery(it)) },
        onSearchToggle = { onAction(InspirationAction.ToggleSearch) },
        onCloseSearch = { onAction(InspirationAction.CloseSearch) },
        onNew = { onAction(InspirationAction.OpenEditor(null)) },
    )

    val detailToolbar = rememberDetailToolbar(
        onBack = { onAction(InspirationAction.Back) },
        onEdit = {
            selectedEntity?.id?.let { id -> onAction(InspirationAction.OpenEditor(id)) }
        },
        onMore = {
            selectedEntity?.id?.let { id -> onAction(InspirationAction.OpenItemActions(id)) }
        },
    )

    val editorToolbar = rememberEditorToolbar(
        editingExisting = editingEntity != null,
        onBack = { onAction(InspirationAction.Back) },
    )

    val toolbar = when (val p = state.page) {
        is InspirationPage.Editor -> editorToolbar
        is InspirationPage.Detail -> detailToolbar
        is InspirationPage.List -> listToolbar
    }

    BackHandler(enabled = state.canConsumeBack) {
        onAction(InspirationAction.Back)
    }

    AppScreenScaffold(
        modifier = modifier.testTag("inspiration-screen"),
        title = toolbar.title,
        navigationIcon = toolbar.navigationIcon,
        actions = toolbar.actions,
        snackbarHost = {
            val local = LocalInspirationSnackbar.current
            if (local != null) SnackbarHost(hostState = local)
        },
        topBarSupportingContent = toolbar.topBarSupporting,
    ) { viewportPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(viewportPadding)
                .testTag("inspiration-content"),
        ) {
            when (val p = state.page) {
                is InspirationPage.List -> {
                    InspirationList(
                        items = items,
                        filtered = filteredItems,
                        typeFilter = state.typeFilter,
                        availableTypes = availableTypes,
                        typeCounts = typeCounts,
                        sortMode = sortMode,
                        showSkeleton = state.showSkeleton,
                        sourceOf = sourceOf,
                        tagsOf = tagsOf,
                        onAction = onAction,
                    )
                }
                is InspirationPage.Detail -> {
                    if (selectedEntity != null) {
                        InspirationDetail(
                            entity = selectedEntity,
                            source = sourceOf(selectedEntity),
                            tags = tagsOf(selectedEntity),
                            viewModel = viewModel,
                            onAction = onAction,
                        )
                    } else {
                        // 详情加载不到：退回列表
                        Box(Modifier.fillMaxSize()) { /* 空，等下一帧 Route 处理 */ }
                    }
                }
                is InspirationPage.Editor -> {
                    InspirationEditor(
                        existing = editingEntity,
                        viewModel = viewModel,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    // ─── Bottom sheets ───
    if (state.sheet is InspirationSheet.Sort) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        GlassModalBottomSheet(
            onDismissRequest = { onAction(InspirationAction.CloseSheet) },
            sheetState = sheetState,
        ) {
            SheetHandle()
            Text(
                "排序方式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            SORT_OPTIONS.forEachIndexed { index, (value, label) ->
                SettingRow(
                    title = label,
                    leading = {
                        Icon(
                            imageVector = if (value == "title") Icons.Outlined.SortByAlpha else Icons.Outlined.Tune,
                            contentDescription = null,
                        )
                    },
                    trailing = {
                        if (sortMode == value) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = { onAction(InspirationAction.ChangeSort(value)) },
                )
                if (index < SORT_OPTIONS.lastIndex) SectionDivider()
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }

    if (state.sheet is InspirationSheet.ItemActions && actionSheetItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val src = sourceOf(actionSheetItem)
        GlassModalBottomSheet(
            onDismissRequest = { onAction(InspirationAction.CloseSheet) },
            sheetState = sheetState,
        ) {
            SheetHandle()
            Text(
                actionSheetItem.title.ifBlank { "未命名灵感" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            val actionRows = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
                add(Triple(Icons.Outlined.AutoAwesome, "查看详情") {
                    onAction(InspirationAction.OpenDetail(actionSheetItem.id))
                })
                add(Triple(Icons.Outlined.Edit, "编辑") {
                    onAction(InspirationAction.EditItem(actionSheetItem.id))
                })
                add(Triple(Icons.Outlined.ContentCopy, "复制内容") {
                    onAction(InspirationAction.CopyCurrentItem)
                })
                if (src?.bookId != null) {
                    add(Triple(Icons.Outlined.Book, "查看来源书籍") {
                        onAction(InspirationAction.OpenCurrentSource)
                    })
                }
                add(Triple(Icons.Outlined.Delete, "删除灵感") {
                    onAction(InspirationAction.RequestDelete(actionSheetItem.id))
                })
            }
            actionRows.forEachIndexed { index, (icon, label, onClick) ->
                SettingRow(
                    title = label,
                    leading = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (label == "删除灵感") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = onClick,
                )
                if (index < actionRows.lastIndex) SectionDivider()
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }

    // ─── Dialogs ───
    if (state.confirmDelete is ConfirmDeleteState.Pending) {
        GlassAlertDialog(
            onDismissRequest = { onAction(InspirationAction.CancelDelete) },
            title = { Text("删除这条灵感？") },
            text = { Text("只会删除当前灵感，不会删除来源书籍、笔记或正文。") },
            confirmButton = {
                TextButton(onClick = { onAction(InspirationAction.ConfirmDeleteNow) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(InspirationAction.CancelDelete) }) { Text("取消") }
            },
        )
    }

    if (state.editorDirty is EditorDirtyState.ConfirmDiscard) {
        GlassAlertDialog(
            onDismissRequest = { onAction(InspirationAction.ContinueEditing) },
            title = { Text("放弃未保存修改？") },
            text = { Text("返回后，本次输入的内容不会保存。") },
            confirmButton = {
                TextButton(onClick = { onAction(InspirationAction.DiscardConfirmed) }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(InspirationAction.ContinueEditing) }) { Text("继续编辑") }
            },
        )
    }
}
