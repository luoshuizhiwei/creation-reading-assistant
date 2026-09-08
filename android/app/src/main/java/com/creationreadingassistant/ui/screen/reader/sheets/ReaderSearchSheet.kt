package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.screen.reader.BookSearchPhase
import com.creationreadingassistant.ui.screen.reader.BookSearchSession
import com.creationreadingassistant.ui.screen.reader.computeBookSearch
import com.creationreadingassistant.ui.screen.reader.computeEpubSearch
import com.creationreadingassistant.ui.screen.reader.computeStreamingTxtSearch
import com.creationreadingassistant.ui.screen.reader.searchContextKeyOf
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// BookSearchResult, computeBookSearch, computeEpubSearch, computeStreamingTxtSearch → reader/ReaderSearchLogic.kt

internal enum class SearchSheetBodyState {
    PROMPT,
    SEARCHING,
    CANCELLED,
    EMPTY_RESULTS,
    RESULTS,
}

internal fun searchSheetBodyState(
    query: String,
    phase: BookSearchPhase,
    resultCount: Int,
): SearchSheetBodyState = when {
    query.isBlank() -> SearchSheetBodyState.PROMPT
    resultCount > 0 -> SearchSheetBodyState.RESULTS
    phase == BookSearchPhase.SEARCHING -> SearchSheetBodyState.SEARCHING
    phase == BookSearchPhase.CANCELLED -> SearchSheetBodyState.CANCELLED
    else -> SearchSheetBodyState.EMPTY_RESULTS
}

@Composable
internal fun SearchSheet(
    document: ReaderDocument?,
    txtDocument: PlainTextDocument?,
    plainContent: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    totalChars: Int,
    isTxt: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    session: BookSearchSession,
    /**
     * 用户点击具体搜索结果：先 [BookSearchSession.select] 即时选中（列表选中态立刻
     * 更新），再回调关闭搜索 Sheet，让正文命中可见；上一处/下一处不触发本回调。
     */
    onResultSelected: (Int) -> Unit = {},
) {
    // 搜索会话状态机由 ReaderScreen 按书持有（关闭/重开面板不清空 query/results/current hit），
    // 这里只消费 + 触发事件（纯 JVM 可测 seam）。
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val reducedMotion = rememberReducedMotion()
    // 微岛收敛：本 sheet 覆盖在阅读页之上，卡片圆角/边框/阴影统一取 ComponentSpec 令牌，
    // 与已收敛的 ReaderTocSheet/ReaderSettingsSheet 同源；reader surface 恒定零阴影、零装饰。
    val spec = LocalComponentSpec.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchContextKey = searchContextKeyOf(document, txtDocument, plainContent)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // 查询/文档变化 → 会话。面板重开（同一 query 且 COMPLETED/CANCELLED）不重启搜索，
    // 保留上一次的 query/results/current hit；取消只取消 searchJob，不再进入 LaunchedEffect key。
    LaunchedEffect(query, document, txtDocument, plainContent) {
        // 任何查询/文档变化（包括查询清空进入 IDLE）一律先取消在途搜索，
        // 避免旧协程继续在 IO 上白扫；早退路径也必须先停旧任务。
        searchJob?.cancel()
        searchJob = null
        if (!session.shouldRestartSearch(query) && session.matchesSearchContext(searchContextKey)) {
            return@LaunchedEffect
        }
        if (session.matchesSearchContext(searchContextKey)) {
            // 同上下文：查询变化（或查询清空）走常规 onQueryChanged
            session.onQueryChanged(query)
            if (session.phase != BookSearchPhase.SEARCHING) return@LaunchedEffect
        } else if (!session.onContextChanged(query, searchContextKey)) {
            // 文档上下文变化（TXT 规则重扫 / 文档实例变化）：同 query 也必须清旧
            // results/currentIndex 并重启；空 query 直接进入 IDLE。
            return@LaunchedEffect
        }
        val runId = session.beginRun(searchContextKey)
        searchJob = scope.launch {
            delay(250)
            try {
                val newResults = withContext(Dispatchers.IO) {
                    when {
                        // 大文件流式 TXT：逐 ReadingUnit 流式搜索
                        txtDocument != null ->
                            computeStreamingTxtSearch(
                                txtDocument, txtDocument.readingUnits, query, totalChars,
                                onProgress = { done, total2 -> session.onProgress(runId, done, total2) },
                            )
                        // 小文件 TXT：全文搜索
                        isTxt ->
                            computeBookSearch(plainContent, query, chapterStartOffsets, chapterTitles, isTxt)
                        // EPUB：逐章流式搜索
                        else ->
                            document?.let {
                                computeEpubSearch(
                                    it, query, chapterStartOffsets, chapterTitles, totalChars,
                                    onProgress = { done, total2 -> session.onProgress(runId, done, total2) },
                                )
                            }
                                ?: emptyList()
                    }
                }
                session.onSearchCompleted(runId, newResults)
            } catch (e: CancellationException) {
                // 取消只终止协程；阶段/结果由 session.cancel()（用户取消）或
                // 下一次 onQueryChanged（查询变化）驱动，避免过期回调覆盖新运行。
                throw e
            }
        }
    }

    ReaderSheetScaffold(title = "搜索本书") {
        // 搜索输入框微岛化（圆角 16dp，输入微反馈，清除按钮）
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索本书") },
            placeholder = { Text("输入人名、设定或句子片段") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清空输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(spec.islandRadius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .focusRequester(focusRequester)
                .testTag("reader-search-field"),
        )

        val phase = session.phase
        if (phase == BookSearchPhase.SEARCHING) {
            // 搜索进度细腻微岛卡片，带已扫描页数微胶囊与取消微胶囊
            Surface(
                shape = RoundedCornerShape(spec.islandRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val progress = session.progress
                        val total2 = progress.second
                        val progressText = if (total2 > 0) {
                            "正在搜索… 已扫描 ${progress.first}/$total2"
                        } else {
                            "正在搜索…"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        ) {
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            onClick = {
                                session.cancel()
                                searchJob?.cancel()
                                searchJob = null
                            },
                        ) {
                            Text(
                                text = "取消",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        } else if (phase == BookSearchPhase.CANCELLED && query.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(spec.hintRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "搜索已取消。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else if (query.isNotBlank() && session.results.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "共 ${session.results.size} 处结果 · 最多显示前 80 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // 结果导航悬浮微岛：圆角 24dp 悬浮导轨微胶囊，居中高光
        if (query.isNotBlank() && session.results.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(spec.dockRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { session.previous() },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("上一处")
                    }
                    val current = session.currentIndex
                    Surface(
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                    ) {
                        Text(
                            text = if (current >= 0) "当前位置 ${current + 1} / ${session.results.size}" else "共 ${session.results.size} 处",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    TextButton(
                        onClick = { session.next() },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("下一处")
                    }
                }
            }
        }

        when (searchSheetBodyState(query, phase, session.results.size)) {
            SearchSheetBodyState.PROMPT -> {
                Surface(
                    shape = RoundedCornerShape(spec.islandRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                    border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Text(
                            text = "输入关键词开始搜索",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "可搜索人名、设定或句子片段。",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            SearchSheetBodyState.SEARCHING,
            SearchSheetBodyState.CANCELLED,
            -> Unit

            SearchSheetBodyState.EMPTY_RESULTS -> {
                FullEmptyState(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    icon = { LineArtBook(sizeDp = 72.dp) },
                    title = "未找到匹配结果",
                    body = "换个关键词或检查拼写试试。",
                )
            }

            SearchSheetBodyState.RESULTS -> {
                // 搜索结果列表项微岛化（12dp 圆角，发丝边框，点击波纹，紧凑微胶囊章节进度）
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(session.results, key = { _, r -> r.occurrenceIndex }) { index, r ->
                        val isCurrent = index == session.currentIndex
                        val cardBg = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
                        }
                        val cardBorder = if (isCurrent) {
                            BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha))
                        } else {
                            BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha))
                        }
                        Surface(
                            shape = RoundedCornerShape(spec.hintRadius),
                            color = cardBg,
                            border = cardBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    session.select(index)
                                    onResultSelected(index)
                                }
                                .semantics { this.selected = isCurrent }
                                .testTag("search-result-$index")
                                .listItemEnter(index, reducedMotion),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = r.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text(
                                        text = "${if (r.chapterIndex >= 0) r.chapterTitle else "全文"} · ${r.progressPercent.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
