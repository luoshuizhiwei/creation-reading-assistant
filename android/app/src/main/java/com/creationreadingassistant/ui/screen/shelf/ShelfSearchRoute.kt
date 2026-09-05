@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfSearchRoute(
    navController: NavHostController,
    viewModel: ShelfViewModel,
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val privateMode by viewModel.privateSearch.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSearchMenu by remember { mutableStateOf(false) }
    val haptic = rememberHaptic(rememberReducedMotion())

    val closeSearch = {
        viewModel.clearSearchQuery()
        navController.popBackStack()
        Unit
    }
    BackHandler(onBack = closeSearch)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        withFrameNanos { }
        delay(300)
        keyboard?.show()
    }
    val normalized = query.trim().lowercase()
    val results = remember(source.library.books, normalized) {
        if (normalized.isEmpty()) emptyList() else source.library.books.filter { book ->
            listOf(book.title, book.author.orEmpty(), book.original_file_name.orEmpty())
                .any { it.lowercase().contains(normalized) }
        }
    }
    AppScreenScaffold(
        title = "搜索书架",
        navigationIcon = { BackButton(closeSearch) },
        actions = {
            Box {
                IconButton(onClick = { showSearchMenu = true }) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "搜索设置")
                }
                DropdownMenu(expanded = showSearchMenu, onDismissRequest = { showSearchMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("无痕搜索") },
                        onClick = {
                            viewModel.setPrivateSearch(!privateMode)
                            showSearchMenu = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = { Switch(checked = privateMode, onCheckedChange = null) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        topBarSupportingContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val adaptive = adaptivePageMetrics(maxWidth, LocalLayoutTokens.current)
                Column(
                    modifier = Modifier
                        .widthIn(max = adaptive.contentWidth)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = adaptive.horizontalPadding, vertical = 6.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索书名、作者或文件名") },
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
                                    viewModel.clearSearchQuery()
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.recordSearch() }),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    )
                    if (privateMode) {
                        Surface(
                            onClick = { viewModel.setPrivateSearch(false) },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = PillShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.padding(start = adaptive.horizontalPadding, bottom = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "无痕搜索已开启 · 点按关闭",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { viewport ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(viewport)) {
            val adaptive = adaptivePageMetrics(maxWidth, LocalLayoutTokens.current)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = adaptive.contentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (normalized.isEmpty()) {
                    if (recent.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "最近搜索",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    haptic(HapticFeedbackType.LongPress)
                                    val snapshot = recent
                                    viewModel.clearRecentSearches()
                                    scope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = "已清空最近搜索",
                                            actionLabel = "撤销",
                                            withDismissAction = true,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreRecentSearches(snapshot)
                                    }
                                }) {
                                    Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                recent.forEach { term ->
                                    Surface(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            viewModel.setSearchQuery(term)
                                        },
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp),
                                        )
                                    }
                                    Text(
                                        "搜索书架中的藏书",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "输入书名、作者或文件名即可快速筛选定位",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                } else if (results.isEmpty()) {
                    item {
                        FullEmptyState(
                            icon = { LineArtBook(sizeDp = 72.dp) },
                            title = "没有找到匹配的书",
                            body = "未找到与「$query」匹配的藏书，换个书名、作者或文件名试试吧。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "搜索结果",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "找到 ${results.size} 本",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    items(results, key = { it.id }) { book ->
                        SearchResultRow(
                            book = book,
                            progress = source.library.progressById[book.id],
                            query = normalized,
                            onOpen = {
                                val unavailable = bookNotReadyLabel(book)
                                if (unavailable == null) {
                                    viewModel.recordSearch()
                                    keyboard?.hide()
                                    navController.navigate("reader/${book.id}")
                                } else scope.launch { snackbar.showSnackbar(unavailable) }
                            },
                            onManage = {
                                viewModel.recordSearch()
                                keyboard?.hide()
                                navController.navigate("shelf/detail/${Uri.encode(book.id)}")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    query: String,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val progressPercent = (progress?.progress_percent ?: 0f).coerceIn(0f, 100f)
    val status = bookStatus(book, progressPercent)
    val reason = when {
        book.title.lowercase().contains(query) -> "匹配书名"
        book.author.orEmpty().lowercase().contains(query) -> "匹配作者"
        else -> "匹配文件名"
    }

    val statusColor = when (status) {
        ShelfStatusFilter.READING -> Color(0xFF7C3AED)
        ShelfStatusFilter.COMPLETED -> AppSuccess
        ShelfStatusFilter.SHELVED -> AppWarning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    SectionCard(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onOpen()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "打开书籍" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                percent = progressPercent,
                modifier = Modifier
                    .size(50.dp, 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                fallback = { ShelfCoverFallback(book) },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = buildHighlighted(book.title, query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildHighlighted(book.author ?: "未知作者", query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 状态微胶囊
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = statusLabel(status),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                        )
                    }

                    // 进度百分比
                    Text(
                        text = "${progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 匹配原因胶囊
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onManage) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "管理书籍",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 在文本中高亮匹配子串。命中部分使用柔和底衬与主色加粗。 */
private fun buildHighlighted(text: String, query: String, highlightColor: Color): AnnotatedString {
    val keyword = query.trim()
    if (keyword.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val lowerKeyword = keyword.lowercase()
    val builder = AnnotatedString.Builder()
    var index = 0
    while (index < text.length) {
        val found = lower.indexOf(lowerKeyword, index)
        if (found < 0) {
            builder.append(text.substring(index))
            break
        }
        if (found > index) builder.append(text.substring(index, found))
        builder.pushStyle(
            SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                background = highlightColor.copy(alpha = 0.14f),
            ),
        )
        builder.append(text.substring(found, (found + keyword.length).coerceAtMost(text.length)))
        builder.pop()
        index = found + keyword.length
    }
    return builder.toAnnotatedString()
}
