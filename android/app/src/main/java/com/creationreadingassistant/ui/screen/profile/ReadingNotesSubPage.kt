package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.AnnotationFilterState
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.feature.annotations.LocalAnnotationActions
import com.creationreadingassistant.feature.annotations.annotationsExportFileName
import com.creationreadingassistant.feature.annotations.buildAnnotationEntries
import com.creationreadingassistant.feature.annotations.buildAnnotationsExportMarkdown
import com.creationreadingassistant.feature.annotations.filterAnnotations
import com.creationreadingassistant.feature.annotations.sortAnnotationEntries
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.components.MutedCoverFallback
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.navigation.hasTemporaryInspectionSource
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDateTime
import kotlin.math.roundToInt

// ============================== 阅读与笔记 ==============================

/**
 * 「我的」阅读档案 / 阅读笔记子页。
 * - READING：按最近阅读展示书籍档案（原行为不变；当前导航实际跳转 my-reading，保留兜底）。
 * - NOTES：统一阅读笔记 —— 高亮（书摘）/ 批注 / 书签同库展示，支持类型 / 书籍 / 关键词
 *   筛选、精确回源定位、编辑批注、改色、删除（可撤销）与批量导出 / 分享。
 *   副作用经 [LocalAnnotationActions] 由 ProfileRoute 提供（与 LocalProfileSnackbar 同模式），
 *   未提供时降级为纯展示（跳转回退 ProfileAction.OpenBook）。
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

// ============================== 阅读档案（READING，原行为） ==============================

@Composable
private fun ReadingArchiveList(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val libraryState = state.libraryState
    val books = libraryState.books
    val bookMap = remember(books) { books.associateBy { it.id } }
    val progressMap = libraryState.progressByBook
    val sessionsByBook = libraryState.readingDurationByBook
    val readingBooks = remember(books, progressMap) {
        books.sortedByDescending { progressMap[it.id]?.last_read_at ?: it.updated_at }
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (readingBooks.isEmpty()) {
            item {
                FullEmptyState(
                    icon = { LineArtBook(sizeDp = 72.dp) },
                    title = "还没有阅读记录",
                    body = "打开任意书籍开始阅读后，这里会按最近阅读时间展示档案。",
                )
            }
        } else {
            readingBooks.forEachIndexed { index, book ->
                item(key = book.id) {
                    val p = progressMap[book.id]
                    Box(Modifier.fillMaxWidth().listItemEnter(index, reducedMotion)) {
                        ReadingBookItem(
                            book = book,
                            progress = p,
                            totalMs = sessionsByBook[book.id] ?: 0L,
                            onClick = { onAction(ProfileAction.OpenBook(book.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingBookItem(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    totalMs: Long,
    onClick: () -> Unit,
) {
    val pct = (progress?.progress_percent ?: 0f).roundToInt().coerceIn(0, 100)
    val spec = LocalComponentSpec.current
    IslandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 书脊 3D 微阴影封面
                Box(
                    modifier = Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(5.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    ),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .width(46.dp)
                            .height(64.dp),
                        shape = RoundedCornerShape(5.dp),
                        fallback = {
                            MutedCoverFallback(book = book, showFormat = false)
                        },
                    )
                }

                // 书籍信息 + 胶囊栏
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!book.author.isNullOrBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // 徽章与微胶囊栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 进度高亮微胶囊
                        Surface(
                            shape = spec.pillShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "进度 $pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // 累计阅读时长暖琥珀微徽章
                        if (totalMs > 0L) {
                            Surface(
                                shape = spec.pillShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(spec.hairlineBorderWidth, Color(0xFFF59E0B).copy(alpha = spec.hairlineAlpha)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFD97706),
                                    )
                                    Text(
                                        text = "累计 ${formatDuration(totalMs)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp,
                                        ),
                                        color = Color(0xFFD97706),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 底部细长进度条与上次阅读时间
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = progress?.last_read_at?.let { "上次阅读：${formatDateTime(it)}" } ?: "尚未开始阅读",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
// ============================== 统一阅读笔记（NOTES） ==============================

/** 高亮 5 色在「我的」外壳下的可视色（对齐阅读器白纸档的随纸批注实色）。 */
private val ANNOTATION_COLOR_VISUALS = linkedMapOf(
    "yellow" to Color(0xFFE6C95A),
    "red" to Color(0xFFD08B7A),
    "green" to Color(0xFF7FA86B),
    "blue" to Color(0xFF6E8FC0),
    "purple" to Color(0xFFA884B0),
)

private fun annotationVisualColor(name: String?): Color =
    ANNOTATION_COLOR_VISUALS[name] ?: ANNOTATION_COLOR_VISUALS.values.first()

/** 类型筛选的 Saveable 编码（顺序无关的名称集合 → 逗号串）。 */
private fun encodeTypes(types: Set<AnnotationType>): String =
    types.joinToString(",") { it.name }

private fun decodeTypes(raw: String): Set<AnnotationType> =
    raw.split(",").mapNotNull { name -> AnnotationType.entries.firstOrNull { it.name == name } }.toSet()
        .ifEmpty { AnnotationType.entries.toSet() }

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
    val visibleSelected = remember(filtered, selectedIds.size) {
        filtered.filter { it.stableId in selectedIds }
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
// ============================== 筛选栏 ==============================

@Composable
private fun AnnotationFilterBar(
    types: Set<AnnotationType>,
    onTypesChange: (Set<AnnotationType>) -> Unit,
    books: List<BookEntity>,
    selectedBookId: String,
    onBookChange: (String) -> Unit,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    selectMode: Boolean,
    onToggleSelectMode: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allTypes = AnnotationType.entries.toSet()
            AnnotationFilterChip(
                label = stringResource(R.string.annotations_filter_all_types),
                selected = types == allTypes,
                onClick = { onTypesChange(allTypes) },
            )
            AnnotationType.entries.forEach { type ->
                AnnotationFilterChip(
                    label = annotationTypeLabel(type),
                    selected = type in types,
                    onClick = {
                        onTypesChange(if (type in types) types - type else types + type)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            AnnotationFilterChip(
                label = stringResource(
                    if (selectMode) R.string.annotations_select_exit else R.string.annotations_select_enter,
                ),
                selected = selectMode,
                onClick = onToggleSelectMode,
                icon = Icons.Outlined.Checklist,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 书籍筛选（仅列出有笔记条目的书）
            Box {
                var bookMenuOpen by remember { mutableStateOf(false) }
                val selectedTitle = books.firstOrNull { it.id == selectedBookId }?.title
                    ?: stringResource(R.string.annotations_filter_all_books)
                Surface(
                    onClick = { bookMenuOpen = true },
                    shape = spec.pillShape,
                    color = if (selectedBookId.isBlank()) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        spec.hairlineBorderWidth,
                        if (selectedBookId.isBlank()) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Book,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = selectedTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(
                    expanded = bookMenuOpen,
                    onDismissRequest = { bookMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.annotations_filter_all_books)) },
                        onClick = {
                            bookMenuOpen = false
                            onBookChange("")
                        },
                    )
                    books.forEach { book ->
                        DropdownMenuItem(
                            text = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                bookMenuOpen = false
                                onBookChange(book.id)
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.annotations_filter_keyword_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = RoundedCornerShape(spec.hintRadius),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun annotationTypeLabel(type: AnnotationType): String = stringResource(
    when (type) {
        AnnotationType.HIGHLIGHT -> R.string.annotations_type_highlight
        AnnotationType.NOTE -> R.string.annotations_type_note
        AnnotationType.BOOKMARK -> R.string.annotations_type_bookmark
    },
)

@Composable
private fun AnnotationFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val spec = LocalComponentSpec.current
    Surface(
        onClick = onClick,
        shape = spec.pillShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(
            spec.hairlineBorderWidth,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
// ============================== 条目卡片 ==============================

/**
 * 统一笔记条目卡片：类型徽标（高亮取批注色 / 批注取主色 / 书签取琥珀）+ 来源书 +
 * 章节行 + 原文摘录（引用块）+ 个人批注（气泡）+ 高亮改色圆点 + 操作微胶囊。
 * 无 locator 的历史条目显示「无定位」徽标，定位按钮降级为「打开书籍」。
 */
@Composable
private fun AnnotationEntryCard(
    entry: AnnotationEntry,
    book: BookEntity?,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onColorChange: (String) -> Unit,
    actionsAvailable: Boolean,
    /** J1-I.2 临时查阅来源入口；为 null 时不渲染（无有效 source locator 的条目传 null）。 */
    onInspectSource: (() -> Unit)? = null,
) {
    val spec = LocalComponentSpec.current
    val typeColor = when (entry.type) {
        AnnotationType.HIGHLIGHT -> annotationVisualColor(entry.color)
        AnnotationType.NOTE -> MaterialTheme.colorScheme.primary
        AnnotationType.BOOKMARK -> Color(0xFFD97706)
    }
    val typeLabel = annotationTypeLabel(entry.type)
    IslandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                // 左侧 3dp 类型墨线竖标（延续原 NoteItem 的视觉语言）
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(listOf(typeColor, typeColor.copy(alpha = 0.4f))),
                        ),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // 顶部：类型徽标 + 无定位徽标 + 时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = spec.pillShape,
                        color = typeColor.copy(alpha = 0.14f),
                        border = BorderStroke(spec.hairlineBorderWidth, typeColor.copy(alpha = spec.hairlineAlpha)),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                            ),
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (entry.bookId != null && !entry.hasLocator) {
                        Surface(
                            shape = spec.pillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                spec.hairlineBorderWidth,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.annotations_badge_no_locator),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                // 来源书 + 章节
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = spec.pillShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Book,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = book?.title?.let { "《$it》" }
                                    ?: stringResource(R.string.annotations_badge_no_book),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val chapter = entry.chapterTitle
                    if (chapter != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Icon(
                                Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 原文摘录：引用块（与个人批注分区展示）
                entry.excerpt?.let { excerpt ->
                    Surface(
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.FormatQuote,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = typeColor.copy(alpha = 0.75f),
                            )
                            Text(
                                text = excerpt,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 19.sp,
                                    fontSize = 12.5.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 个人批注气泡
                entry.annotation?.let { annotation ->
                    Surface(
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = annotation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (!selectMode && actionsAvailable) {
                    // 高亮改色圆点（仅高亮；改色不动定位信息）
                    if (entry.type == AnnotationType.HIGHLIGHT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ANNOTATION_COLOR_VISUALS.forEach { (name, cColor) ->
                                val isSelected = entry.color == name
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable { onColorChange(name) }
                                        .then(
                                            if (isSelected) {
                                                Modifier
                                                    .background(cColor.copy(alpha = 0.25f), CircleShape)
                                                    .border(1.5.dp, cColor, CircleShape)
                                            } else {
                                                Modifier.border(
                                                    0.6.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                    CircleShape,
                                                )
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 10.dp else 12.dp)
                                            .background(cColor, CircleShape),
                                    )
                                }
                            }
                        }
                    }

                    // 操作微胶囊：定位（无 locator 降级为打开书籍）· 编辑批注 · 删除
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnnotationActionPill(
                            text = stringResource(
                                if (entry.hasLocator) R.string.annotations_action_jump
                                else R.string.annotations_action_jump_degraded,
                            ),
                            icon = Icons.Outlined.NearMe,
                            onClick = onClick,
                        )
                        // J1-I.2：仅对携带有效 source locator 的条目提供「临时查看来源」——
                        // 进入可回退的一次性查阅（返回阅读处 / Back 回到进入前位置），
                        // 与上面的普通跳转语义区分开。
                        if (onInspectSource != null) {
                            AnnotationActionPill(
                                text = stringResource(R.string.annotations_action_temporary_inspect),
                                icon = Icons.Outlined.AutoStories,
                                onClick = onInspectSource,
                            )
                        }
                        if (entry.type != AnnotationType.BOOKMARK) {
                            AnnotationActionPill(
                                text = stringResource(R.string.annotations_action_edit),
                                icon = Icons.Outlined.EditNote,
                                onClick = onEdit,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        AnnotationActionPill(
                            text = stringResource(R.string.annotations_action_delete),
                            icon = Icons.Outlined.DeleteOutline,
                            onClick = onDelete,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 条目操作微胶囊（定位 / 编辑 / 删除），对齐 ReaderNotesSheet 的 NotesActionPill 视觉。 */
@Composable
private fun AnnotationActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val spec = LocalComponentSpec.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = spec.pillShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.bounceable(interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = tint,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}

// ============================== 批量选择底栏 ==============================

/** 选择模式底栏：已选计数 + 导出 / 分享 / 删除（SAF 取消不产生任何数据修改）。 */
@Composable
private fun AnnotationSelectionBar(
    selectedCount: Int,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    val actionsEnabled = enabled && selectedCount > 0
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.annotations_selected_count, selectedCount),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            AnnotationActionPill(
                text = stringResource(R.string.annotations_action_export),
                icon = Icons.Outlined.FileDownload,
                onClick = { if (actionsEnabled) onExport() },
                tint = if (actionsEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
            AnnotationActionPill(
                text = stringResource(R.string.annotations_action_share),
                icon = Icons.Outlined.Share,
                onClick = { if (actionsEnabled) onShare() },
                tint = if (actionsEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
            AnnotationActionPill(
                text = stringResource(R.string.annotations_action_delete),
                icon = Icons.Outlined.DeleteOutline,
                onClick = { if (actionsEnabled) onDelete() },
                tint = if (actionsEnabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
    }
}

// ============================== 编辑批注对话框 ==============================

/**
 * 编辑个人批注（高亮 note / 笔记 body）：摘录只读预览 + 批注草稿输入。
 * 保存经 [com.creationreadingassistant.feature.annotations.AnnotationActions.editAnnotation]，
 * 仓储层 copy 保留既有定位信息。
 */
@Composable
private fun AnnotationEditDialog(
    entry: AnnotationEntry,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.annotations_edit_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${stringResource(R.string.annotations_edit_dialog_excerpt_prefix)}${excerpt.take(60)}${if (excerpt.length > 60) "…" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text(stringResource(R.string.annotations_edit_dialog_field_label)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.annotations_edit_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.annotations_edit_dialog_cancel))
            }
        },
    )
}