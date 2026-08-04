package com.creationreadingassistant.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.viewmodel.SearchViewModel
import com.creationreadingassistant.ui.components.EmptyStateHint
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val layout = LocalLayoutTokens.current
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("all") }
    val focusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) { viewModel.search(query) }

    val totalHits = results.books.size + results.inspirations.size + results.notes.size + results.highlights.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索", style = MaterialTheme.typography.headlineLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索书籍、灵感、笔记、摘录") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = layout.pageHorizontal,
                        vertical = layout.relatedGap,
                    )
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = LocalComponentSpec.current.listItemShape,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) viewModel.addHistory(query) }),
            )

            // Tab 分类：全部 / 书籍 / 灵感 / 笔记 / 高亮（SE5）—— 类型胶囊，对齐 web filter-chip 视觉
            FlowRow(
                Modifier.padding(horizontal = layout.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
                verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
            ) {
                SearchPill(selected = tab == "all", label = "全部", onClick = { tab = "all" })
                SearchPill(selected = tab == "books", label = "书籍 (${results.books.size})", onClick = { tab = "books" })
                SearchPill(selected = tab == "inspirations", label = "灵感 (${results.inspirations.size})", onClick = { tab = "inspirations" })
                SearchPill(selected = tab == "notes", label = "笔记 (${results.notes.size})", onClick = { tab = "notes" })
                SearchPill(selected = tab == "highlights", label = "高亮 (${results.highlights.size})", onClick = { tab = "highlights" })
            }

            when {
                loading -> {
                    ListSkeleton(modifier = Modifier.fillMaxWidth().padding(layout.cardPadding), reducedMotion = reducedMotion)
                }
                query.isBlank() -> {
                    if (history.isNotEmpty()) {
                        // 搜索历史（SE2）
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
                                Text("搜索历史", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { viewModel.clearHistory() }) {
                                    Text("清空")
                                }
                            }
                            FlowRow(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                history.forEach { term ->
                                    SearchPill(
                                        selected = false,
                                        label = term,
                                        onClick = {
                                            query = term
                                            viewModel.addHistory(term)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        EmptyHint(text = "输入关键词开始搜索")
                    }
                }
                totalHits == 0 -> {
                    EmptyStateHint(
                        text = "未找到匹配结果",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(layout.pageHorizontal),
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (tab == "all" || tab == "books") {
                            items(results.books, key = { "book-${it.id}" }) { book ->
                                SearchResultRow(
                                    icon = Icons.Filled.Book,
                                    title = buildHighlighted(book.title, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "书籍",
                                    sourceLabel = null,
                                    reducedMotion = reducedMotion,
                                    snippet = (book.author ?: "未知作者") + " · ${book.format.uppercase()}",
                                    onClick = {
                                        viewModel.addHistory(query)
                                        navController.navigate("reader/${book.id}")
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "inspirations") {
                            items(results.inspirations, key = { "insp-${it.id}" }) { insp ->
                                SearchResultRow(
                                    icon = Icons.Filled.Lightbulb,
                                    title = buildHighlighted(insp.title, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "灵感",
                                    sourceLabel = null,
                                    reducedMotion = reducedMotion,
                                    snippet = (insp.body.ifBlank { insp.title }).take(100),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        navController.navigate("inspiration?inspId=${insp.id}")
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "notes") {
                            items(results.notes, key = { "note-${it.id}" }) { note ->
                                val source = note.book_id?.let { results.bookTitles[it] }?.let { "《$it》" }
                                SearchResultRow(
                                    icon = Icons.Filled.Description,
                                    title = buildHighlighted(note.title.ifBlank { note.body }, query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "笔记",
                                    sourceLabel = source,
                                    reducedMotion = reducedMotion,
                                    snippet = (note.body.ifBlank { note.excerpt ?: "" }).take(100),
                                    onClick = {
                                        viewModel.addHistory(query)
                                        note.book_id?.let { navController.navigate("reader/$it?highlightId=${note.id}") }
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "highlights") {
                            items(results.highlights, key = { "highlight-${it.id}" }) { hl ->
                                val source = results.bookTitles[hl.book_id]?.let { "《$it》" }
                                SearchResultRow(
                                    icon = Icons.Filled.Highlight,
                                    title = buildHighlighted(hl.text.take(40), query, MaterialTheme.colorScheme.primary),
                                    typeLabel = "高亮",
                                    sourceLabel = source,
                                    reducedMotion = reducedMotion,
                                    snippet = (hl.note ?: "无备注").take(100),
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

@Composable
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LineArtBook(modifier = Modifier.size(64.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchPill(selected: Boolean, label: String, onClick: () -> Unit) {
    // 触感已由 SelectablePill 内建，勿在此重复触发
    SelectablePill(
        text = label,
        selected = selected,
        onClick = onClick,
        unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

@Composable
private fun SearchResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: AnnotatedString,
    typeLabel: String,
    sourceLabel: String?,
    snippet: String,
    onClick: () -> Unit,
    reducedMotion: Boolean = false,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    SectionCard(
        onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateEnter(reducedMotion = reducedMotion),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    sourceLabel?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (snippet.isNotBlank()) {
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 在文本中高亮匹配子串（SE3）。命中部分用 error 色加粗，复用主题色，不引入硬编码。 */
private fun buildHighlighted(text: String, query: String, highlightColor: androidx.compose.ui.graphics.Color): AnnotatedString {
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
        builder.pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold))
        builder.append(text.substring(found, (found + keyword.length).coerceAtMost(text.length)))
        builder.pop()
        index = found + keyword.length
    }
    return builder.toAnnotatedString()
}
