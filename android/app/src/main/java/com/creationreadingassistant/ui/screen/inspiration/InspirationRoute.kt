package com.creationreadingassistant.ui.screen.inspiration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.viewmodel.InspirationItemsState
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import kotlinx.coroutines.launch

@Composable
internal fun InspirationRoute(
    modifier: Modifier = Modifier,
    viewModel: InspirationViewModel = hiltViewModel(),
    initialSelectedId: String? = null,
    onOpenBook: (String) -> Unit = {},
) {
    val itemsState by viewModel.itemsState.collectAsStateWithLifecycle()
    val pendingSavedIds by viewModel.pendingSavedIds.collectAsStateWithLifecycle()
    val items = (itemsState as? InspirationItemsState.Loaded)?.items ?: emptyList()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val sortMode by viewModel.inspirationSort.collectAsStateWithLifecycle()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var state by remember {
        mutableStateOf(
            InspirationUiState(
                page = initialSelectedId?.let { InspirationPage.Detail(it) }
                    ?: InspirationPage.List,
            ),
        )
    }

    // 骨架屏只由 repository flow 首次真实 emission 驱动：Loading → 骨架，Loaded(空) → 空状态。
    val showSkeleton = itemsState is InspirationItemsState.Loading

    val detailResolution = (state.page as? InspirationPage.Detail)?.let { page ->
        resolveDetail(page.inspirationId, itemsState, pendingSavedIds)
    }
    val selectedEntity = (detailResolution as? DetailResolution.Found)?.entity

    val sourceOf: (InspirationEntity) -> InspirationSourceInfo? = remember(viewModel) {
        { viewModel.sourceOf(it) }
    }
    val tagsOf: (InspirationEntity) -> List<String> = remember(viewModel) {
        { viewModel.tagsOf(it) }
    }

    val filtered = remember(items, state.query, state.typeFilter, state.statusFilter, sortMode) {
        val needle = state.query.trim().lowercase()
        filterAndSortItems(items, needle, state.typeFilter, state.statusFilter, sortMode, sourceOf, tagsOf)
    }
    val availableStatuses = remember(items) {
        val counts = items.groupingBy { it.status ?: "inbox" }.eachCount()
        STATUS_OPTIONS.filter { counts.containsKey(it.first) }
    }
    val statusCounts = remember(items) {
        items.groupingBy { it.status ?: "inbox" }.eachCount()
    }
    val availableTypes = remember(items) {
        val counts = items.groupingBy { it.type ?: "note" }.eachCount()
        TYPE_OPTIONS.filter { counts.containsKey(it.first) }
    }
    val typeCounts = remember(items) {
        items.groupingBy { it.type ?: "note" }.eachCount()
    }

    fun message(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    // 列表已加载但详情 id 不存在且无 pending：安全返回列表，避免永久空白。
    LaunchedEffect(state.page, detailResolution) {
        if (state.page is InspirationPage.Detail && detailResolution is DetailResolution.NotFound) {
            message("灵感不存在。")
            state = state.copy(page = InspirationPage.List)
        }
    }

    val editingId = (state.page as? InspirationPage.Editor)?.inspirationId
    val editingEntity = editingId?.let { id -> items.firstOrNull { it.id == id } }
    val actionItem = (state.sheet as? InspirationSheet.ItemActions)?.inspirationId
        ?.let { id -> items.firstOrNull { it.id == id } }

    fun copyEntity(item: InspirationEntity) {
        val src = sourceOf(item)
        val text = listOfNotNull(item.title, item.body, src?.excerpt)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        clipboard.setText(AnnotatedString(text))
        message("灵感内容已复制。")
    }

    fun onAction(action: InspirationAction) {
        when (action) {
            /* 返回优先级：弹层 → 删除确认 → 未保存确认 → 编辑器 → 详情 → 搜索/列表 */
            InspirationAction.Back -> {
                when {
                    state.sheet !is InspirationSheet.None ->
                        state = state.copy(sheet = InspirationSheet.None)

                    state.confirmDelete !is ConfirmDeleteState.None ->
                        state = state.copy(confirmDelete = ConfirmDeleteState.None)

                    state.editorDirty is EditorDirtyState.ConfirmDiscard ->
                        state = state.copy(editorDirty = EditorDirtyState.Dirty)

                    state.page is InspirationPage.Editor -> {
                        if (state.editorDirty is EditorDirtyState.Dirty) {
                            state = state.copy(editorDirty = EditorDirtyState.ConfirmDiscard)
                        } else {
                            val id = (state.page as InspirationPage.Editor).inspirationId
                            state = state.copy(
                                editorDirty = EditorDirtyState.Clean,
                                page = id?.let { InspirationPage.Detail(it) }
                                    ?: InspirationPage.List,
                            )
                        }
                    }

                    state.page is InspirationPage.Detail ->
                        state = state.copy(page = InspirationPage.List)

                    state.searchOpen ->
                        state = state.copy(searchOpen = false, query = "")

                    else -> { /* root — 不消费 */ }
                }
            }

            InspirationAction.OpenSortSheet ->
                state = state.copy(sheet = InspirationSheet.Sort)

            is InspirationAction.OpenItemActions ->
                state = state.copy(sheet = InspirationSheet.ItemActions(action.inspirationId))

            InspirationAction.CloseSheet ->
                state = state.copy(sheet = InspirationSheet.None)

            is InspirationAction.OpenDetail -> {
                state = state.copy(
                    sheet = InspirationSheet.None,
                    page = InspirationPage.Detail(action.inspirationId),
                )
            }

            is InspirationAction.OpenEditor -> {
                state = state.copy(
                    sheet = InspirationSheet.None,
                    editorDirty = EditorDirtyState.Clean,
                    page = InspirationPage.Editor(inspirationId = action.inspirationId),
                )
            }

            is InspirationAction.EditItem -> {
                state = state.copy(
                    sheet = InspirationSheet.None,
                    editorDirty = EditorDirtyState.Clean,
                    page = InspirationPage.Editor(inspirationId = action.inspirationId),
                )
            }

            InspirationAction.NavigateToList ->
                state = state.copy(page = InspirationPage.List)

            InspirationAction.ToggleSearch ->
                state = state.copy(searchOpen = !state.searchOpen)

            InspirationAction.CloseSearch ->
                state = state.copy(searchOpen = false, query = "")

            is InspirationAction.UpdateQuery ->
                state = state.copy(query = action.query)

            is InspirationAction.UpdateTypeFilter ->
                state = state.copy(typeFilter = action.type)

            is InspirationAction.UpdateStatusFilter ->
                state = state.copy(statusFilter = action.status)

            InspirationAction.ResetFilter ->
                state = state.copy(query = "", typeFilter = "all", statusFilter = "all")

            is InspirationAction.ChangeSort -> {
                viewModel.setInspirationSort(action.value)
                state = state.copy(sheet = InspirationSheet.None)
            }

            InspirationAction.CopyCurrentItem -> {
                state = state.copy(sheet = InspirationSheet.None)
                (actionItem ?: selectedEntity)?.let { copyEntity(it) }
            }

            InspirationAction.OpenCurrentSource -> {
                state = state.copy(sheet = InspirationSheet.None)
                val item = actionItem ?: selectedEntity ?: return
                val src = sourceOf(item)
                if (src?.bookId == null) {
                    message("这条灵感没有关联来源书籍。")
                    return
                }
                val book = books.firstOrNull { it.id == src.bookId }
                if (book == null) {
                    message("来源书籍已不可用，这条灵感仍会保留。")
                } else {
                    // 真跳转：打开来源书（阅读器按已存进度续读定位）
                    onOpenBook(book.id)
                }
            }

            is InspirationAction.RequestDelete -> {
                state = state.copy(
                    sheet = InspirationSheet.None,
                    confirmDelete = ConfirmDeleteState.Pending(action.inspirationId),
                )
            }

            InspirationAction.ConfirmDeleteNow -> {
                val pending = (state.confirmDelete as? ConfirmDeleteState.Pending)
                    ?.inspirationId ?: return
                state = state.copy(confirmDelete = ConfirmDeleteState.None)
                viewModel.deleteInspiration(pending)
                if (selectedEntity?.id == pending) {
                    state = state.copy(page = InspirationPage.List)
                }
                message("灵感已删除。")
            }

            InspirationAction.CancelDelete ->
                state = state.copy(confirmDelete = ConfirmDeleteState.None)

            is InspirationAction.EditorSetDirty -> {
                // 未处于已确认状态时才更新（避免 Dialog 弹出时被覆写）
                if (state.editorDirty !is EditorDirtyState.ConfirmDiscard) {
                    state = state.copy(
                        editorDirty = if (action.dirty) EditorDirtyState.Dirty
                        else EditorDirtyState.Clean,
                    )
                }
            }

            InspirationAction.RequestDiscardEditor ->
                state = state.copy(editorDirty = EditorDirtyState.ConfirmDiscard)

            InspirationAction.DiscardConfirmed -> {
                val id = (state.page as InspirationPage.Editor).inspirationId
                state = state.copy(
                    editorDirty = EditorDirtyState.Clean,
                    page = id?.let { InspirationPage.Detail(it) } ?: InspirationPage.List,
                )
            }

            InspirationAction.ContinueEditing ->
                state = state.copy(editorDirty = EditorDirtyState.Dirty)

            is InspirationAction.EditorSaved -> {
                state = state.copy(
                    editorDirty = EditorDirtyState.Clean,
                    page = InspirationPage.Detail(action.newId),
                )
                message(if (action.wasNew) "灵感已保存。" else "修改已保存。")
            }

            is InspirationAction.CopyVariantText -> {
                clipboard.setText(AnnotatedString(action.text))
                message("候选内容已复制。")
            }

            is InspirationAction.ApplyVariant -> {
                // 变体内容在 detail 组件内直接调用 viewModel.applyVariant，
                // 这里只保留 Action 契约，不额外重复工作。
            }

            is InspirationAction.DeleteVariant ->
                viewModel.deleteVariant(action.variantId)

            is InspirationAction.RunInspirationAi -> {
                val insp = items.firstOrNull { it.id == action.inspirationId } ?: return
                viewModel.runInspirationAction(
                    insp.id, action.action, insp.title, insp.body,
                ) { ok ->
                    message(
                        if (ok) "AI 候选已保存，原文没有被覆盖。"
                        else "生成失败，请检查 AI 设置"
                    )
                }
            }

            is InspirationAction.ShowMessage -> message(action.text)
        }
    }

    CompositionLocalProvider(LocalInspirationSnackbar provides snackbarHostState) {
        InspirationScreen(
            state = state,
            items = items,
            books = books,
            filteredItems = filtered,
            availableTypes = availableTypes,
            typeCounts = typeCounts,
            availableStatuses = availableStatuses,
            statusCounts = statusCounts,
            sortMode = sortMode,
            showSkeleton = showSkeleton,
            selectedEntity = selectedEntity,
            detailResolving = detailResolution is DetailResolution.Loading,
            editingEntity = editingEntity,
            actionSheetItem = actionItem,
            viewModel = viewModel,
            onAction = ::onAction,
            modifier = modifier,
        )
    }
}
