@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val closeSearch = {
        viewModel.clearSearchQuery()
        navController.popBackStack()
        Unit
    }
    BackHandler(onBack = closeSearch)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // 等待焦点提交和页面入场结束；部分 MIUI 设备会忽略过渡期内的 show()。
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
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester),
                    placeholder = { Text("搜索书名、作者或文件名") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.recordSearch() }),
                    shape = LocalComponentSpec.current.listItemShape,
                )
                if (privateMode) {
                    Surface(
                        onClick = { viewModel.setPrivateSearch(false) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = LocalComponentSpec.current.pillShape,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    ) {
                        Text("无痕搜索已开启 · 点按关闭", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        },
    ) { viewport ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(viewport),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (normalized.isEmpty()) {
                if (recent.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("最近搜索", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
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
                            }) { Text("清空") }
                        }
                    }
                    items(recent, key = { it }) { term ->
                        Text(
                            term,
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.setSearchQuery(term) }).padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("没有找到匹配的书", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("换个书名、作者或文件名试试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text("找到 ${results.size} 本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val reason = when {
        book.title.lowercase().contains(query) -> "匹配书名"
        book.author.orEmpty().lowercase().contains(query) -> "匹配作者"
        else -> "匹配文件名：${book.original_file_name.orEmpty()}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与书架 BookTile 一致的打开语义：供无障碍/自动化（benchmark 找书）识别
            .semantics { contentDescription = "打开书籍" }
            .combinedClickable(onClick = onOpen, onLongClick = onManage)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            book = book,
            percent = (progress?.progress_percent ?: 0f).coerceIn(0f, 100f),
            modifier = Modifier.size(48.dp, 68.dp),
            fallback = { ShelfCoverFallback(book) },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author ?: "作者未知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                "${statusLabel(bookStatus(book, progress?.progress_percent ?: 0f))} · ${progress?.progress_percent?.coerceIn(0f, 100f)?.toInt() ?: 0}% · $reason",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onManage) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "管理书籍") }
    }
}
