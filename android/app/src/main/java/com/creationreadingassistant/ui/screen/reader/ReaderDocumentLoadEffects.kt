package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent

/**
 * B3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的
 * 「文档装载 / 章节加载代次」状态与 2 个 LaunchedEffect。
 *
 * 沿用 [rememberPagerEngineState] / [rememberReaderProgress] 的 State-holder 模式：
 * 状态声明与 effect 体逐字搬运，effect 内写入的跨域可变状态（runtimeError /
 * pendingInitialPosition / chapterIndex）由主函数以 holder 形式传入，
 * 写操作直接落回主函数持有的真实状态，与原 `var by` delegate 语义完全一致。
 *
 * 返回的 [ReaderDocumentLoadState] 同时暴露 State-holder（xxxState），
 * 供 ReaderRuntimeEffects 等下游按原引用方式消费。
 */
internal data class ReaderDocumentLoadState(
    val chapterBlocks: List<DocBlock>,
    val isChapterLoading: Boolean,
    val txtStreamingDocument: PlainTextDocument?,
    val txtStreamingFileIndex: TxtFileIndex?,
    val chapterBlocksState: MutableState<List<DocBlock>>,
    val isChapterLoadingState: MutableState<Boolean>,
    val txtStreamingDocumentState: MutableState<PlainTextDocument?>,
    val txtStreamingFileIndexState: MutableState<TxtFileIndex?>,
)

@Composable
internal fun rememberReaderDocumentLoadState(
    loadedBook: ReaderLoadedBook?,
    epubContent: ReaderLoadedContent.Epub?,
    textContent: ReaderLoadedContent.Text?,
    markdownDocument: ReaderDocument?,
    chapterLoadResult: ChapterLoadResult?,
    runtimeErrorState: MutableState<String?>,
    pendingInitialPositionState: MutableState<Boolean>,
    chapterIndexState: MutableIntState,
): ReaderDocumentLoadState {
    // 预加载状态（由 ViewModel 章节加载代次管理，主线程只读，杜绝主线程 Zip I/O 导致的 ANR / OOM）
    val chapterBlocksState = remember { mutableStateOf<List<DocBlock>>(emptyList()) }
    var chapterBlocks by chapterBlocksState
    val isChapterLoadingState = remember { mutableStateOf(false) }
    var isChapterLoading by isChapterLoadingState

    // 流式 TXT 大文件状态（统一加载器按实际字节数分流，plainContent 为空串）
    val txtStreamingDocumentState = remember { mutableStateOf<PlainTextDocument?>(null) }
    var txtStreamingDocument by txtStreamingDocumentState
    val txtStreamingFileIndexState = remember { mutableStateOf<TxtFileIndex?>(null) }
    var txtStreamingFileIndex by txtStreamingFileIndexState

    var runtimeError by runtimeErrorState
    var pendingInitialPosition by pendingInitialPositionState
    var chapterIndex by chapterIndexState

    LaunchedEffect(loadedBook) {
        runtimeError = null
        isChapterLoading = false
        pendingInitialPosition = loadedBook != null
        chapterIndex = epubContent?.initialChapterIndex ?: 0
        chapterBlocks = epubContent?.initialChapterBlocks.orEmpty()
        txtStreamingDocument = textContent?.streamingDocument
        txtStreamingFileIndex = textContent?.fileIndex
        // Markdown 滚动模式首章直接同步装载；分页模式由 pagedSource 按需读取
        if (markdownDocument != null) {
            chapterBlocks = markdownDocument.blocks(0)
        }
    }

    // R6：观察 ViewModel 章节加载结果
    LaunchedEffect(chapterLoadResult) {
        when (val r = chapterLoadResult) {
            is ChapterLoadResult.Loading -> {
                isChapterLoading = true
                chapterBlocks = emptyList()
            }
            is ChapterLoadResult.Loaded -> {
                isChapterLoading = false
                chapterBlocks = r.blocks
            }
            is ChapterLoadResult.Error -> {
                isChapterLoading = false
                runtimeError = r.message
            }
            null -> Unit
        }
    }

    return ReaderDocumentLoadState(
        chapterBlocks = chapterBlocks,
        isChapterLoading = isChapterLoading,
        txtStreamingDocument = txtStreamingDocument,
        txtStreamingFileIndex = txtStreamingFileIndex,
        chapterBlocksState = chapterBlocksState,
        isChapterLoadingState = isChapterLoadingState,
        txtStreamingDocumentState = txtStreamingDocumentState,
        txtStreamingFileIndexState = txtStreamingFileIndexState,
    )
}
