package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.AnnotationFilterState
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.feature.annotations.LocalAnnotationActions
import com.creationreadingassistant.feature.annotations.annotationsExportFileName
import com.creationreadingassistant.feature.annotations.buildAnnotationEntries
import com.creationreadingassistant.feature.annotations.buildAnnotationsExportMarkdown
import com.creationreadingassistant.feature.annotations.filterAnnotations
import com.creationreadingassistant.feature.annotations.sortAnnotationEntries
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.navigation.hasTemporaryInspectionSource
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDateTime

// ============================== 阅读与笔记 ==============================

/**
 * 「我的」阅读档案 / 阅读笔记子页。
 * - READING：按最近阅读展示书籍档案（原行为不变；当前导航实际跳转 my-reading，保留兜底）。
 * - NOTES：聚合「划线高亮」「书内批注」「书签」，提供类型多选过滤、单书筛选、
 *   关键词全文检索、多选批量导出 Markdown / 分享 / 批量删除。
 *
 * 笔记数据与书内面板共享底层 Flow：书内增删改，返回「我的」即可见最新状态；
 * 跨书列表点击条目回源直达阅读器正文对应选区（带高光聚焦动效）。
 */
@Composable
internal fun ReadingNotesSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
    page: ProfileSubPage,
) {
    if (page == ProfileSubPage.READING) {
        ReadingArchiveList(state, onAction, scaffoldPadding)
    } else {
        AnnotationNotesPage(state, onAction, scaffoldPadding)
    }
}

// ============================== 统一阅读笔记（NOTES） ==============================

/**
 * 统一阅读笔记页：高亮 / 批注 / 书签同一份来源（highlights + notes 两表 Flow 投影），
 * 与书内笔记面板数据一致 —— 编辑 / 删除后 Flow 自动刷新，重启持久化由 Room 保证。
 */
@Composable
private fun AnnotationNotesPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val actions = LocalAnnotationActions.current
    val library = state.libraryState
    val entries = remember(library.highlights, library.notes) {
        buildAnnotationEntries(library.highlights, library.notes)
    }
    val bookMap = remember(library.books) { library.books.associateBy { it.id } }
    val booksWithEntries = remember(entries, bookMap) {
        entries.mapNotNull { it.bookId }.distinct().mapNotNull { bookMap[it] }.sortedBy { it.title }
    }

    // ---- 筛选状态（进程内保持；types/bookId/keyword 均可 Saveable）----
    var typesRaw by rememberSaveable { mutableStateOf(encodeTypes(AnnotationType.entries.toSet())) }
    var bookIdRaw by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    val filter = remember(typesRaw, bookIdRaw, keyword) {
        AnnotationFilterState(
            types = decodeTypes(typesRaw),
            bookId = bookIdRaw.ifBlank { null },
            keyword = keyword,
        )
    }
    val filtered = remember(entries, filter) { sortAnnotationEntries(filterAnnotations(entries, filter), filter) }

    // ---- 选择模式 ----
    var selectMode by rememberSaveable { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    // 筛选变化后选中集可能与可见集脱节，直接清空避免「看不见的选中项」被批量删除
    LaunchedEffect(typesRaw, bookIdRaw, keyword) { selectedIds.clear() }
    // 选中集快照（内容相等即视为同一 key）参与 remember：
    // 只用 selectedIds.size 会漏掉「取消一项 + 勾选另一项」这类数量不变的替换，
    // 批量导出 / 分享 / 删除会作用在陈旧选择上。
    val selectedSnapshot = selectedIds.toList()
    val visibleSelected = remember(filtered, selectedSnapshot) {
        filtered.filter { it.stableId in selectedSnapshot }
    }

    // ---- 编辑批注对话框 ----
    var editingEntry by remember { mutableStateOf<AnnotationEntry?>(null) }
    var editDraft by remember { mutableStateOf("") }
    val reducedMotion = rememberReducedMotion()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
    ) {
        AnnotationFilterBar(
            types = filter.types,
            onTypesChange = { typesRaw = encodeTypes(it) },
            books = booksWithEntries,
            selectedBookId = bookIdRaw,
            onBookChange = { bookIdRaw = it },
            keyword = keyword,
            onKeywordChange = { keyword = it },
            selectMode = selectMode,
            onToggleSelectMode = {
                selectMode = !selectMode
                if (!selectMode) selectedIds.clear()
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                entries.isEmpty() -> PageLazyColumn {
                    item {
                        FullEmptyState(
                            icon = { LineArtBookmark(sizeDp = 64.dp) },
                            title = stringResource(R.string.annotations_empty_no_records_title),
                            body = stringResource(R.string.annotations_empty_no_records_body),
                        )
                    }
                }

                filtered.isEmpty() -> PageLazyColumn {
                    item {
                        FullEmptyState(
                            icon = { LineArtBookmark(sizeDp = 64.dp) },
                            title = stringResource(R.string.annotations_empty_filtered_title),
                            body = stringResource(R.string.annotations_empty_filtered_body),
                            primaryAction = stringResource(R.string.annotations_empty_filtered_clear) to {
                                typesRaw = encodeTypes(AnnotationType.entries.toSet())
                                bookIdRaw = ""
                                keyword = ""
                            },
                        )
                    }
                }

                else -> PageLazyColumn {
                    filtered.forEachIndexed { index, entry ->
                        item(key = entry.stableId) {
                            Box(Modifier.fillMaxWidth().listItemEnter(index, reducedMotion)) {
                                AnnotationEntryCard(
                                    entry = entry,
                                    book = entry.bookId?.let { bookMap[it] },
                                    selectMode = selectMode,
                                    selected = entry.stableId in selectedIds,
                                    onClick = {
                                        if (selectMode) {
                                            if (entry.stableId in selectedIds) {
                                                selectedIds.remove(entry.stableId)
                                            } else {
                                                selectedIds.add(entry.stableId)
                                            }
                                        } else {
                                            // 回源定位；无 actions 时降级为打开书籍
                                            val bookId = entry.bookId
                                            if (actions != null) {
                                                actions.jumpToEntry(entry)
                                            } else if (bookId != null) {
                                                onAction(ProfileAction.OpenBook(bookId))
                                            }
                                        }
                                    },
                                    onEdit = {
                                        editDraft = entry.annotation ?: ""
                                        editingEntry = entry
                                    },
                                    onDelete = { actions?.deleteEntries(listOf(entry)) },
                                    onColorChange = { color -> actions?.changeHighlightColor(entry, color) },
                                    actionsAvailable = actions != null,
                                    // J1-I.2：只有「绑定了书籍 + 带有效全局 source locator」的条目才出现
                                    // 临时查阅入口；可见性用与 route 构造同一判据派生，避免出现
                                    // 「按钮显示但点了没反应」。其余条目连按钮都不渲染。
                                    onInspectSource = if (hasTemporaryInspectionSource(entry.bookId, entry.legacyOffset)) {
                                        { actions?.inspectSourceTemporarily(entry) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (selectMode) {
                AnnotationSelectionBar(
                    selectedCount = visibleSelected.size,
                    onExport = {
                        val now = LocalDateTime.now()
                        val markdown = buildAnnotationsExportMarkdown(
                            entries = visibleSelected,
                            bookTitles = booksWithEntries.associate { it.id to it.title },
                            exportedAt = now,
                        )
                        actions?.exportMarkdown(markdown, annotationsExportFileName(now))
                    },
                    onShare = {
                        val markdown = buildAnnotationsExportMarkdown(
                            entries = visibleSelected,
                            bookTitles = booksWithEntries.associate { it.id to it.title },
                            exportedAt = LocalDateTime.now(),
                        )
                        actions?.shareMarkdown(markdown)
                    },
                    onDelete = {
                        actions?.deleteEntries(visibleSelected.toList())
                        selectedIds.clear()
                        selectMode = false
                    },
                    enabled = actions != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    editingEntry?.let { entry ->
        AnnotationEditDialog(
            entry = entry,
            draft = editDraft,
            onDraftChange = { editDraft = it },
            onSave = {
                actions?.editAnnotation(entry, editDraft)
                editingEntry = null
            },
            onDismiss = { editingEntry = null },
        )
    }
}