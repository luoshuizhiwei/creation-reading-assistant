package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.screen.reader.BookSearchPhase
import com.creationreadingassistant.ui.screen.reader.BookSearchSession
import com.creationreadingassistant.ui.screen.reader.computeBookSearch
import com.creationreadingassistant.ui.screen.reader.computeEpubSearch
import com.creationreadingassistant.ui.screen.reader.computeStreamingTxtSearch
import com.creationreadingassistant.ui.screen.reader.searchContextKeyOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// BookSearchResult, computeBookSearch, computeEpubSearch, computeStreamingTxtSearch → reader/ReaderSearchLogic.kt

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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索本书") },
            placeholder = { Text("输入人名、设定或句子片段") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focusRequester)
                .testTag("reader-search-field"),
        )
        val phase = session.phase
        if (phase == BookSearchPhase.SEARCHING) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                val progress = session.progress
                val total2 = progress.second
                val progressText = if (total2 > 0) {
                    "正在搜索… 已扫描 ${progress.first}/$total2"
                } else {
                    "正在搜索…"
                }
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(
                    onClick = {
                        session.cancel()
                        searchJob?.cancel()
                        searchJob = null
                    },
                ) { Text("取消") }
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        } else if (phase == BookSearchPhase.CANCELLED && query.isNotBlank()) {
            Text(
                "搜索已取消。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else if (query.isNotBlank()) {
            Text(
                "找到 ${session.results.size} 处，最多显示前 80 条。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (query.isNotBlank() && session.results.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                TextButton(onClick = { session.previous() }) { Text("上一处") }
                val current = session.currentIndex
                Text(
                    if (current >= 0) "第 ${current + 1} / ${session.results.size} 处" else "共 ${session.results.size} 处",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = { session.next() }) { Text("下一处") }
            }
        }
        if (query.isNotBlank() && session.results.isEmpty()) {
            FullEmptyState(
                modifier = Modifier.fillMaxWidth().weight(1f),
                icon = { LineArtBook(sizeDp = 72.dp) },
                title = "未找到匹配结果",
                body = "换个关键词或检查拼写试试。",
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(session.results, key = { _, r -> r.occurrenceIndex }) { index, r ->
                    val isCurrent = index == session.currentIndex
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable {
                                session.select(index)
                                onResultSelected(index)
                            }
                            .semantics { this.selected = isCurrent }
                            .testTag("search-result-$index")
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .listItemEnter(index, reducedMotion),
                    ) {
                        Text(r.snippet, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${if (r.chapterIndex >= 0) r.chapterTitle else "全文"} · ${r.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        SectionDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
