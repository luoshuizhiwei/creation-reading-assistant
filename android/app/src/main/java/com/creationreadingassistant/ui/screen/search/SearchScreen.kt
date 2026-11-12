package com.creationreadingassistant.ui.screen.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.creationreadingassistant.feature.search.SearchCoverageState
import com.creationreadingassistant.feature.search.SearchHit
import com.creationreadingassistant.feature.search.SearchTextBasis
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SearchResultCard
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.buildHighlighted
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.SearchNotice
import com.creationreadingassistant.ui.viewmodel.SearchNoticeKind
import com.creationreadingassistant.ui.viewmodel.SearchViewModel

/** J1-CROSSBOOK-v2：命中无法形成可靠 source 定位时的用户可见反馈文案。 */
internal const val SEARCH_NO_SOURCE_POSITION_MESSAGE = "该结果没有可定位的原文位置"

/**
 * J1-CROSSBOOK-v2 §四.1：搜索「正文命中」的**入口判定**（真实调用点，不是平行 helper）。
 *
 * 正文命中是「去看一眼」而不是「把阅读进度搬过去」，因此唯一合法的成功形态是**临时查阅**
 * route；换算不出全书偏移（如大 TXT 取不到精确 offset）时必须**只反馈、不导航**。
 * 旧实现把它降级成 `reader/{bookId}` 普通打开目标书，B 定位完成后 recordNormalReading(B)
 * 会把普通阅读位置覆盖成 B —— 用「普通打开另一本书」冒充「临时查阅成功」，并丢掉原阅读现场。
 *
 * 判据直接复用既有 [readerTemporaryRouteForSource]（拒绝空书 ID / 负 offset / 仅章节元组），
 * 不引入平行协议。
 */
internal sealed interface SearchContentHitNavigation {
    /** 可临时查阅：携带 navigationMode=temporary 的 route。 */
    data class TemporaryInspection(val route: String) : SearchContentHitNavigation

    /** 无可靠 source 定位：只反馈，不导航。类型上不存在可导航目标。 */
    data object NoReliableSource : SearchContentHitNavigation
}

/** 正文命中入口判定：能造出临时查阅 route 才返回 [SearchContentHitNavigation.TemporaryInspection]。 */
internal fun searchContentHitNavigation(
    bookId: String,
    preciseOffset: Int?,
): SearchContentHitNavigation {
    val offset = preciseOffset ?: return SearchContentHitNavigation.NoReliableSource
    val route = readerTemporaryRouteForSource(
        bookId = bookId,
        legacyOffset = offset,
        chapterIndex = null,
        charOffset = null,
    ) ?: return SearchContentHitNavigation.NoReliableSource
    return SearchContentHitNavigation.TemporaryInspection(route)
}

/**
 * J1-CROSSBOOK-v2 §四.1：搜索「书籍命中」是用户主动的**普通打开书籍**，保持既有语义不变 ——
 * 不携带 navigationMode、不产生临时查阅会话，也不受正文命中失败降级规则影响。
 */
internal fun searchBookHitRoute(bookId: String): String = "reader/$bookId"

private val SearchBookColor = Color(0xFF7C3AED)
private val SearchInspColor = Color(0xFFD97706)
private val SearchNoteColor = Color(0xFF0284C7)
private val SearchHighlightColor = Color(0xFF059669)
private val SearchContentColor = Color(0xFFBE185D)

/**
 * 测试观察接缝：全局搜索结果列表的只读快照。
 *
 * 只暴露 [LazyListState] 的公开只读属性与当前 Tab/query；不改变列表行为、key、排序或导航。
 * 供 androidTest 精确断言返回后的滚动位置，产品 UI 不消费该语义。
 */
data class SearchListSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val tab: String,
    val query: String,
    val topItemKey: String?,
    val itemCount: Int,
)

val SearchListSnapshotKey = SemanticsPropertyKey<SearchListSnapshot>("SearchListSnapshot")
var SemanticsPropertyReceiver.searchListSnapshot by SearchListSnapshotKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    // R2-S1.5：query / tab / 列表滚动状态都提升到 VM（见 SearchViewModel 说明），
    // 使其在「搜索页 → 阅读器临时查阅 → 返回」的导航往返中存活，返回后词与滚动都保留。
    var query by viewModel.queryState
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val indexProgress by viewModel.indexProgress.collectAsStateWithLifecycle()
    var tab by viewModel.tabState
    val focusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val scope = rememberCoroutineScope()
    // J1-CROSSBOOK-v2 §四.1：命中无法形成可靠 source 定位时只做明确反馈，不导航。
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) { viewModel.search(query) }

    // 「全部」页里，正文命中与元数据命中会指向同一本书；这里按去重后的行数计总数，
    // 避免 tab 上的数字比列表实际行数多。
    val dedupedBookRows = if (tab == "all") {
        results.books.count { it.id !in results.booksAlsoMatchedInContent }
    } else {
        results.books.size
    }
    val totalHits = dedupedBookRows +
        results.inspirations.size +
        results.notes.size +
        results.highlights.size +
        results.contentHits.size

    // 空态判定必须按**当前 tab** 计，否则「切到一个没有结果的分类」会既不显示空态、
    // 也不显示任何行（列表空白但看不出为什么）。
    val visibleHits = when (tab) {
        "books" -> results.books.size
        "content" -> results.contentHits.size
        "inspirations" -> results.inspirations.size
        "notes" -> results.notes.size
        "highlights" -> results.highlights.size
        else -> totalHits
    }

    AppScreenScaffold(
        title = "全局搜索",
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 顶部优雅搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索书籍、灵感、笔记、高亮") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = layout.pageHorizontal,
                        vertical = 6.dp,
                    )
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = spec.listItemShape,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            query = ""
                        }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "清除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) viewModel.addHistory(query) }),
            )

            // 全库全文搜索索引状态透明感知胶囊
            val showIndexCapsule = indexProgress.isRunning ||
                (indexProgress.totalBooks > 0 && indexProgress.indexedBooks < indexProgress.totalBooks)
            if (showIndexCapsule) {
                SearchIndexProgressCapsule(
                    progress = indexProgress,
                    onRebuild = { viewModel.triggerRebuildIndex() },
                    modifier = Modifier.padding(horizontal = layout.pageHorizontal, vertical = 2.dp),
                )
            }

            // Tab 筛选分类微胶囊轨
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = layout.pageHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryFilterPill(
                    label = "全部",
                    count = totalHits,
                    selected = tab == "all",
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { tab = "all" },
                )
                CategoryFilterPill(
                    label = "书籍",
                    count = results.books.size,
                    selected = tab == "books",
                    activeColor = SearchBookColor,
                    onClick = { tab = "books" },
                )
                CategoryFilterPill(
                    label = "正文",
                    count = results.contentHits.size,
                    selected = tab == "content",
                    activeColor = SearchContentColor,
                    onClick = { tab = "content" },
                )
                CategoryFilterPill(
                    label = "灵感",
                    count = results.inspirations.size,
                    selected = tab == "inspirations",
                    activeColor = SearchInspColor,
                    onClick = { tab = "inspirations" },
                )
                CategoryFilterPill(
                    label = "笔记",
                    count = results.notes.size,
                    selected = tab == "notes",
                    activeColor = SearchNoteColor,
                    onClick = { tab = "notes" },
                )
                CategoryFilterPill(
                    label = "高亮",
                    count = results.highlights.size,
                    selected = tab == "highlights",
                    activeColor = SearchHighlightColor,
                    onClick = { tab = "highlights" },
                )
            }

            // 完整性提示：索引没建 / 命中的书不是全量覆盖。
            // 宁可明说「可能不全」，也不要让用户在不知情的情况下以为这就是全部结果。
            if (!loading && query.isNotBlank()) {
                results.notice?.let { notice ->
                    SearchNoticeBanner(
                        notice = notice,
                        modifier = Modifier.padding(
                            horizontal = layout.pageHorizontal,
                            vertical = 4.dp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                loading -> {
                    ListSkeleton(modifier = Modifier.fillMaxWidth().padding(layout.cardPadding), reducedMotion = reducedMotion)
                }
                query.isBlank() -> {
                    if (history.isNotEmpty()) {
                        // 搜索历史流
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = layout.pageHorizontal,
                                    vertical = layout.relatedGap,
                                ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "搜索历史",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    haptic(HapticFeedbackType.LongPress)
                                    viewModel.clearHistory()
                                }) {
                                    Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            FlowRow(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                history.forEach { term ->
                                    Surface(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            query = term
                                            viewModel.addHistory(term)
                                        },
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Outlined.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = term,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 空历史探索引导微岛
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = layout.pageHorizontal),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                Text(
                                    "搜索书库与灵感创作",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "支持按书名、作者、灵感片段、笔记正文或高亮划线快速检索",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                visibleHits == 0 -> {
                    FullEmptyState(
                        icon = { LineArtBook(sizeDp = 72.dp) },
                        title = "未找到匹配结果",
                        body = "未找到与「$query」相关的内容，换个关键词再试一次吧。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(layout.pageHorizontal),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search-results-list")
                            .semantics {
                                searchListSnapshot = SearchListSnapshot(
                                    firstVisibleItemIndex = viewModel.listState.firstVisibleItemIndex,
                                    firstVisibleItemScrollOffset = viewModel.listState.firstVisibleItemScrollOffset,
                                    tab = tab,
                                    query = query,
                                    topItemKey = viewModel.listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull()?.key as? String,
                                    itemCount = viewModel.listState.layoutInfo.totalItemsCount,
                                )
                            },
                        state = viewModel.listState,
                    ) {
                        if (tab == "all" || tab == "books") {
                            // 「全部」页排除掉同时有正文命中的书 —— 正文那行信息更丰富（带位置与覆盖说明），
                            // 留它即可；「书籍」页仍显示全部元数据命中。
                            val bookRows = if (tab == "all") {
                                results.books.filter { it.id !in results.booksAlsoMatchedInContent }
                            } else {
                                results.books
                            }
                            itemsIndexed(
                                items = bookRows,
                                key = { _, book -> "book-${book.id}" },
                                contentType = { _, _ -> "search-book" },
                            ) { index, book ->
                                SearchResultCard(
                                    icon = Icons.Outlined.AutoStories,
                                    title = buildHighlighted(book.title, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "书籍",
                                    typeColor = SearchBookColor,
                                    sourceLabel = null,
                                    reducedMotion = reducedMotion,
                                    entranceDelay = index * 40,
                                    snippet = (book.author ?: "未知作者") + " · ${book.format.uppercase()}",
                                    onClick = {
                                        viewModel.addHistory(query)
                                        // J1-CROSSBOOK-v2 §四.1：书籍命中 = 用户主动普通打开书籍，
                                        // 保持既有语义（见 searchBookHitRoute），不因正文命中降级规则受影响。
                                        navController.navigate(searchBookHitRoute(book.id))
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "content") {
                            itemsIndexed(
                                items = results.contentHits,
                                key = { _, hit -> "content-${hit.bookId}-${hit.textBasis.wire}" },
                                contentType = { _, _ -> "search-content" },
                            ) { index, hit ->
                                val bookTitle = results.bookTitles[hit.bookId]
                                SearchResultCard(
                                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                                    title = buildHighlighted(
                                        bookTitle ?: hit.bookId,
                                        query,
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                    typeLabel = "正文",
                                    typeColor = SearchContentColor,
                                    // 「两者都搜并标注来源」：每条命中都标明它来自哪种文本。
                                    sourceLabel = contentSourceLabel(hit),
                                    reducedMotion = reducedMotion,
                                    entranceDelay = index * 40,
                                    snippet = contentSnippet(hit),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        val targetBookId = hit.bookId
                                        // 精确跳转（R2-S1.4）：走**临时查阅** route —— 命中是「去看一眼」
                                        // 而不是「把阅读进度搬过去」，返回行为由既有临时返回栈接管。
                                        // J1-CROSSBOOK-v2 §四.1：换算不出全书偏移时只反馈、不导航
                                        // （判定见 searchContentHitNavigation，绝不降级成普通打开目标书）。
                                        scope.launch {
                                            // 磁盘换算保持在既有 IO 路径上，不搬动、不复制。
                                            val offset = viewModel.resolvePreciseOffset(hit)
                                            // 导航与反馈的**主线程亲和性**保证：resolvePreciseOffset
                                            // 返回后，续体可能落在后台调度线程上（见 v6 first_failure：
                                            // CoroutineScheduler$Worker 线程调用 NavController.navigate →
                                            // LifecycleRegistry.setCurrentState 拒绝非主线程状态更新）。
                                            // Main.immediate：已在主线程时不额外派发，不在时切回主线程。
                                            // 只包住导航/反馈分支，不改 route 构造、不改 null 偏移判定、
                                            // 不吞异常（navigate 的异常照常向上传播）。
                                            withContext(Dispatchers.Main.immediate) {
                                                when (val navigation = searchContentHitNavigation(targetBookId, offset)) {
                                                    is SearchContentHitNavigation.TemporaryInspection -> navController.navigate(navigation.route)

                                                    SearchContentHitNavigation.NoReliableSource -> snackbarHostState.showSnackbar(SEARCH_NO_SOURCE_POSITION_MESSAGE)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "inspirations") {
                            itemsIndexed(
                                items = results.inspirations,
                                key = { _, insp -> "insp-${insp.id}" },
                                contentType = { _, _ -> "search-inspiration" },
                            ) { index, insp ->
                                SearchResultCard(
                                    icon = Icons.Outlined.Lightbulb,
                                    title = buildHighlighted(insp.title, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "灵感",
                                    typeColor = SearchInspColor,
                                    sourceLabel = null,
                                    reducedMotion = reducedMotion,
                                    entranceDelay = index * 40,
                                    snippet = (insp.body.ifBlank { insp.title }).take(100),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        navController.navigate("inspiration?inspId=${insp.id}")
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "notes") {
                            itemsIndexed(
                                items = results.notes,
                                key = { _, note -> "note-${note.id}" },
                                contentType = { _, _ -> "search-note" },
                            ) { index, note ->
                                val source = note.book_id?.let { results.bookTitles[it] }?.let { "《$it》" }
                                SearchResultCard(
                                    icon = Icons.Outlined.Description,
                                    title = buildHighlighted(note.title.ifBlank { note.body }, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "笔记",
                                    typeColor = SearchNoteColor,
                                    sourceLabel = source,
                                    reducedMotion = reducedMotion,
                                    entranceDelay = index * 40,
                                    snippet = (note.body.ifBlank { note.excerpt ?: "" }).take(100),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        note.book_id?.let { navController.navigate("reader/$it?highlightId=${note.id}") }
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "highlights") {
                            itemsIndexed(
                                items = results.highlights,
                                key = { _, hl -> "highlight-${hl.id}" },
                                contentType = { _, _ -> "search-highlight" },
                            ) { index, hl ->
                                val source = results.bookTitles[hl.book_id]?.let { "《$it》" }
                                SearchResultCard(
                                    icon = Icons.Outlined.Highlight,
                                    title = buildHighlighted(hl.text.take(40), query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "高亮",
                                    typeColor = SearchHighlightColor,
                                    sourceLabel = source,
                                    reducedMotion = reducedMotion,
                                    entranceDelay = index * 40,
                                    snippet = (hl.note ?: "划线摘录").take(100),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        navController.navigate("reader/${hl.book_id}?highlightId=${hl.id}")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整性提示条。
 *
 * 只做「说明」，不给操作 —— 索引构建由后台 worker 负责，这里一旦给出「立即重建」之类的
 * 按钮，就会诱使用人在搜索页上触发重 I/O。
 */
@Composable
private fun SearchNoticeBanner(
    notice: SearchNotice,
    modifier: Modifier = Modifier,
) {
    val text = when (notice.kind) {
        SearchNoticeKind.INDEX_NOT_BUILT ->
            "全文索引尚未建立，当前只搜到了书名、作者等元数据"
        SearchNoticeKind.COVERAGE_INCOMPLETE ->
            "有 ${notice.affectedBooks} 本书只索引了部分内容，正文搜索结果可能不全"
        SearchNoticeKind.CONTENT_SEARCH_FAILED ->
            "正文搜索失败，以下仅显示书名、作者、笔记等其他搜索结果"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * 全库全文索引进度胶囊：状态感知与手动唤起重建。
 */
@Composable
private fun SearchIndexProgressCapsule(
    progress: com.creationreadingassistant.data.repository.SearchIndexRepository.Progress,
    onRebuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = progress.isRunning
    val total = progress.totalBooks
    val indexed = progress.indexedBooks
    val pct = if (total > 0) ((indexed.toFloat() / total) * 100f).toInt().coerceIn(0, 100) else 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isRunning) {
                            "全文索引构建中：$indexed / $total 本 ($pct%)"
                        } else {
                            "全文索引待完成：已索引 $indexed / $total 本"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isRunning) {
                    TextButton(
                        onClick = onRebuild,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text(
                            text = "立即构建",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (isRunning && total > 0) {
                LinearProgressIndicator(
                    progress = { (indexed.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(PillShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                )
            }
        }
    }
}

/**
 * 正文命中的来源标注：「哪种文本 · 在哪里」。
 *
 * 文本基准是 R2-S1 的既定口径（两者都搜并标注来源），即使当前只建了原文通道也要标，
 * 否则将来显示文通道上线时，用户无从分辨一行结果是原文里找到的还是替换后找到的。
 */
private fun contentSourceLabel(hit: SearchHit): String {
    val basis = when (hit.textBasis) {
        SearchTextBasis.ORIGINAL -> "原文"
        SearchTextBasis.DISPLAY -> "替换显示文"
    }
    // 捕获成局部 val：[SearchHit.readerChapterIndex] 有自定义 getter，判空后无法智能转型。
    val readerChapter = hit.readerChapterIndex
    val where = when {
        readerChapter != null -> "第 ${readerChapter + 1} 章"
        hit.isPreviewHit -> "正文开头"
        else -> "书名/作者"
    }
    return "$basis · $where"
}

/**
 * 正文命中的副标题：位置 + 该书的索引覆盖说明。
 *
 * 覆盖说明直接读 [SearchHit.coverage]（S1.1 落库的真实状态），**不按 format 猜测**。
 * 真正的上下文片段（带高亮的一段原文）需要按行懒加载章节文本，
 * 属于后续接线范围，这里先给出不撒谎的替代信息。
 */
private fun contentSnippet(hit: SearchHit): String {
    val parts = ArrayList<String>(2)
    hit.charOffset?.let { parts += "第 $it 字附近" }
    when (hit.coverage) {
        SearchCoverageState.FULL -> Unit // 全量覆盖无需额外说明
        SearchCoverageState.PREVIEW_ONLY -> parts += "仅索引正文开头"
        SearchCoverageState.PARTIAL -> parts += "仅索引部分章节"
        SearchCoverageState.METADATA_ONLY -> parts += "仅索引书名/作者"
        SearchCoverageState.FAILED -> parts += "正文索引失败"
        SearchCoverageState.PENDING -> parts += "该基准索引尚未完成（历史数据）"
        SearchCoverageState.NOT_APPLICABLE -> Unit // 显示文与原文一致，无需额外说明
        null -> parts += "索引覆盖未知"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun CategoryFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = PillShape,
        color = if (selected) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (selected) BorderStroke(1.dp, activeColor.copy(alpha = 0.4f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count > 0) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            if (selected) activeColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

