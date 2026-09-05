package com.creationreadingassistant.ui.screen.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.SearchViewModel

private val SearchBookColor = Color(0xFF7C3AED)
private val SearchInspColor = Color(0xFFD97706)
private val SearchNoteColor = Color(0xFF0284C7)
private val SearchHighlightColor = Color(0xFF059669)

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
    val haptic = rememberHaptic(reducedMotion)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) { viewModel.search(query) }

    val totalHits = results.books.size + results.inspirations.size + results.notes.size + results.highlights.size

    AppScreenScaffold(
        title = "全局搜索",
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
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
                shape = RoundedCornerShape(14.dp),
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
                totalHits == 0 -> {
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (tab == "all" || tab == "books") {
                            itemsIndexed(results.books, key = { _, book -> "book-${book.id}" }) { index, book ->
                                SearchResultRow(
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
                                        navController.navigate("reader/${book.id}")
                                    },
                                )
                            }
                        }
                        if (tab == "all" || tab == "inspirations") {
                            itemsIndexed(results.inspirations, key = { _, insp -> "insp-${insp.id}" }) { index, insp ->
                                SearchResultRow(
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
                            itemsIndexed(results.notes, key = { _, note -> "note-${note.id}" }) { index, note ->
                                val source = note.book_id?.let { results.bookTitles[it] }?.let { "《$it》" }
                                SearchResultRow(
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
                            itemsIndexed(results.highlights, key = { _, hl -> "highlight-${hl.id}" }) { index, hl ->
                                val source = results.bookTitles[hl.book_id]?.let { "《$it》" }
                                SearchResultRow(
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

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    title: AnnotatedString,
    typeLabel: String,
    typeColor: Color,
    sourceLabel: String?,
    snippet: String,
    onClick: () -> Unit,
    reducedMotion: Boolean = false,
    entranceDelay: Int = 0,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    SectionCard(
        onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .animateEnter(delayMillis = entranceDelay, reducedMotion = reducedMotion),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = typeColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = typeColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    sourceLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 在文本中高亮匹配子串（SE3）。命中部分使用柔和底衬与主色加粗。 */
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
