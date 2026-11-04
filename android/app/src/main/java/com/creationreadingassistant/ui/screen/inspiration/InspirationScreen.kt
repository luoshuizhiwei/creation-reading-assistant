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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
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
import com.creationreadingassistant.ui.viewmodel.BookListState
import com.creationreadingassistant.ui.viewmodel.CategoryListState
import com.creationreadingassistant.ui.viewmodel.InspirationCategoryFilterOption
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import com.creationreadingassistant.ui.viewmodel.inspirationCategoryNames

/**
 * 详情页来源书的显示状态。
 *
 * 由 payload 的 `source.bookId` + ViewModel 的 [BookListState] 共同派生，
 * 不使用 `emptyList()` 同时代表“未加载”和“确定为空”：
 * - [Loading]：书籍 Flow 尚未首发；不得显示确定缺书，也不得伪装可用；
 * - [Available]：已加载且来源书存在；书名优先取真实书籍；
 * - [Missing]：已加载且来源书确实缺失；保留历史书名快照并明确提示；
 * - [Failed]：书籍 Flow 读取失败；明确“无法确认”，不得降级成缺书；
 * - [NotLinked]：灵感本身没有关联来源书。
 */
internal sealed interface SourceBookStatus {
    data object NotLinked : SourceBookStatus
    data object Loading : SourceBookStatus
    data class Available(val book: BookEntity) : SourceBookStatus
    data object Missing : SourceBookStatus
    data class Failed(val message: String?) : SourceBookStatus
}

/** 纯函数：把真实书籍加载状态解析为详情可消费的来源书状态。 */
internal fun resolveSourceBookStatus(
    source: InspirationSourceInfo?,
    booksState: BookListState,
): SourceBookStatus {
    val bookId = source?.bookId?.takeIf { it.isNotBlank() }
        ?: return SourceBookStatus.NotLinked
    return when (booksState) {
        BookListState.Loading -> SourceBookStatus.Loading
        is BookListState.Failed -> SourceBookStatus.Failed(booksState.message)
        is BookListState.Loaded -> booksState.books.firstOrNull { it.id == bookId }
            ?.let { SourceBookStatus.Available(it) }
            ?: SourceBookStatus.Missing
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InspirationScreen(
    state: InspirationUiState,
    items: List<InspirationEntity>,
    booksState: BookListState,
    /** 任务93-v1-A：分类目录加载状态（Loading/Failed 不当作空目录）。 */
    categoriesState: CategoryListState,
    availableCategories: List<Pair<String, String>>,
    categoryOptions: List<InspirationCategoryFilterOption>,
    filteredItems: List<InspirationEntity>,
    availableTypes: List<Pair<String, String>>,
    typeCounts: Map<String, Int>,
    availableStatuses: List<Pair<String, String>>,
    statusCounts: Map<String, Int>,
    sortMode: String,
    showSkeleton: Boolean,
    selectedEntity: InspirationEntity?,
    detailResolving: Boolean,
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
    // 列表卡片显示分类名称：活动目录内显示真实名称，旧 id 显示不可用标签（不隐藏）。
    val categoryNamesOf: (InspirationEntity) -> List<String> =
        remember(viewModel, availableCategories) {
            { entity -> inspirationCategoryNames(viewModel.categoryIdsOf(entity), availableCategories) }
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
        actionsEnabled = !detailResolving && selectedEntity != null,
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
        // 列表搜索态的输入框由 ToolbarConfig.titleContent 提供；此前漏传导致 searchOpen 后
        // 搜索图标隐藏、输入框却从未渲染（用户看不到搜索框）。仅在搜索打开时接管标题区，
        // 关闭态继续走 AppTopBar 的标准标题样式，避免字体/排版变化。
        titleContent = if (state.searchOpen) toolbar.titleContent else null,
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
                        statusFilter = state.statusFilter,
                        categoryFilter = state.categoryFilter,
                        categoryOptions = categoryOptions,
                        categoryNamesOf = categoryNamesOf,
                        availableTypes = availableTypes,
                        typeCounts = typeCounts,
                        availableStatuses = availableStatuses,
                        statusCounts = statusCounts,
                        sortMode = sortMode,
                        showSkeleton = showSkeleton,
                        sourceOf = sourceOf,
                        tagsOf = tagsOf,
                        onAction = onAction,
                    )
                }
                is InspirationPage.Detail -> {
                    if (selectedEntity != null) {
                        val source = sourceOf(selectedEntity)
                        val payload = viewModel.payloadFor(selectedEntity)
                        InspirationDetail(
                            entity = selectedEntity,
                            source = source,
                            // 来源书状态由真实 BookListState 派生；Loading/Failed 不会被
                            // 当成确定缺书，Available 时才使用真实书名。
                            sourceStatus = resolveSourceBookStatus(source, booksState),
                            payload = payload,
                            tags = tagsOf(selectedEntity),
                            categoryNames = inspirationCategoryNames(payload.categoryIds, availableCategories),
                            viewModel = viewModel,
                            onAction = onAction,
                        )
                    } else if (detailResolving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("inspiration-detail-loading"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "加载中…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        // 持久 id 不存在：Route 下一帧会安全返回列表，这里先给明确反馈，不留空白。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("inspiration-detail-missing"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "灵感不存在，正在返回列表…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is InspirationPage.Editor -> {
                    InspirationEditor(
                        existing = editingEntity,
                        viewModel = viewModel,
                        availableCategories = availableCategories,
                        categoriesLoading = categoriesState is CategoryListState.Loading,
                        categoriesFailed = categoriesState is CategoryListState.Failed,
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SORT_OPTIONS.forEachIndexed { index, (value, label) ->
                SettingRow(
                    title = label,
                    leading = {
                        Icon(
                            imageVector = if (value == "title") Icons.Outlined.SortByAlpha else Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = if (sortMode == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        if (sortMode == value) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
