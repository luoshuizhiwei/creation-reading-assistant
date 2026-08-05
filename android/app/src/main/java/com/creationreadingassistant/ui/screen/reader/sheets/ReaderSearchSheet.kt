package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.screen.reader.BookSearchResult
import com.creationreadingassistant.ui.screen.reader.computeBookSearch
import com.creationreadingassistant.ui.screen.reader.computeEpubSearch
import com.creationreadingassistant.ui.screen.reader.computeStreamingTxtSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onJump: (BookSearchResult) -> Unit,
) {
    var results by remember { mutableStateOf(emptyList<BookSearchResult>()) }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(query, document, txtDocument, plainContent) {
        delay(250)
        results = withContext(Dispatchers.IO) {
            when {
                // 大文件流式 TXT：逐 ReadingUnit 流式搜索
                txtDocument != null ->
                    computeStreamingTxtSearch(txtDocument, txtDocument.readingUnits, query, totalChars)
                // 小文件 TXT：全文搜索
                isTxt ->
                    computeBookSearch(plainContent, query, chapterStartOffsets, chapterTitles, isTxt)
                // EPUB：逐章流式搜索
                else ->
                    document?.let { computeEpubSearch(it, query, chapterStartOffsets, chapterTitles, totalChars) }
                        ?: emptyList()
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (query.isNotBlank()) {
            Text(
                "找到 ${results.size} 处，最多显示前 80 条。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (query.isNotBlank() && results.isEmpty()) {
            FullEmptyState(
                modifier = Modifier.fillMaxWidth().weight(1f),
                icon = { LineArtBook(sizeDp = 72.dp) },
                title = "未找到匹配结果",
                body = "换个关键词或检查拼写试试。",
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(results, key = { _, r -> r.occurrenceIndex }) { index, r ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(r) }
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
