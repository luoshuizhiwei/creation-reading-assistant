package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.repository.ChapterReadRepository
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 章节异步内容加载、块提取与纯文本提取协同器。
 *
 * 负责 EPUB / Markdown / TXT 章节内容流式加载、代次递增防护与历史进度更新。
 */
internal class ReaderChapterLoaderCoordinator {

    private val chapterLoadGeneration = AtomicInteger(0)
    private var chapterLoadJob: Job? = null

    private val _chapterLoadState = MutableStateFlow<ChapterLoadResult?>(null)
    val chapterLoadState: StateFlow<ChapterLoadResult?> = _chapterLoadState.asStateFlow()

    fun loadChapter(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        loadedBookProvider: () -> ReaderLoadedBook?,
        bookId: String,
        chapterIndex: Int,
        persistProgress: Boolean = true,
        chapterReadRepository: ChapterReadRepository,
        epubRepository: EpubRepository,
        /**
         * CRA-READER-DISPATCH-ORDER-MINIMAL-v6：本次章节导航在**派发边界**取好的事件序号
         * （见 `ReaderProgressSaveOrder`）。随章切换落库一起交给持久入口，
         * 使慢完成的旧章加载不能覆盖它之后的精确位置保存。
         */
        saveOrder: Long,
    ) {
        chapterLoadJob?.cancel()
        val gen = chapterLoadGeneration.incrementAndGet()
        _chapterLoadState.value = ChapterLoadResult.Loading(bookId, chapterIndex)
        chapterLoadJob = scope.launch(dispatcher) {
            try {
                val book = loadedBookProvider() ?: return@launch
                if (book.id != bookId) return@launch
                val blocks = when (val content = book.content) {
                    is ReaderLoadedContent.Epub -> content.document.blocks(chapterIndex)
                    is ReaderLoadedContent.Markdown -> content.document.blocks(chapterIndex)
                    is ReaderLoadedContent.Text -> return@launch
                }
                if (gen != chapterLoadGeneration.get()) return@launch
                _chapterLoadState.value = ChapterLoadResult.Loaded(bookId, chapterIndex, blocks)
                runCatching { chapterReadRepository.markRead(bookId, chapterIndex) }
                    .onFailure { AppLog.w("ReaderVM", "mark chapter read failed: ${it.message}") }

                // 保存 epub 进度（章号即位置的 legacy 语义）。只知章号、不给偏移：
                // locator v2 缺省全局 offset（EpubRepository 单一写入契约），读侧按
                // chapterStart[ci] + 0 派生为本章起点，自洽；分页防抖/退出保存随后
                // 以真实全书偏移覆盖。
                if (persistProgress && book.content is ReaderLoadedContent.Epub) {
                    val epub = book.content
                    val percent = if (epub.book.chapters.isEmpty()) {
                        0f
                    } else {
                        ((chapterIndex + 1).toFloat() / epub.book.chapters.size) * 100f
                    }
                    epubRepository.saveProgress(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        percent = percent,
                        order = saveOrder,
                    )
                }
            } catch (_: CancellationException) {
                // stale load, ignore
            } catch (e: Exception) {
                if (gen == chapterLoadGeneration.get()) {
                    _chapterLoadState.value = ChapterLoadResult.Error(bookId, chapterIndex, e.message ?: "章节加载失败")
                }
            }
        }
    }

    /** 一次性加载章节块（供搜索/高亮定位等 UI 跳转场景使用，不改变当前章节状态）。 */
    suspend fun loadChapterBlocks(
        loadedBookProvider: () -> ReaderLoadedBook?,
        bookId: String,
        chapterIndex: Int,
    ): List<DocBlock> {
        val book = loadedBookProvider() ?: return emptyList()
        if (book.id != bookId) return emptyList()
        val document = when (val content = book.content) {
            is ReaderLoadedContent.Epub -> content.document
            is ReaderLoadedContent.Markdown -> content.document
            is ReaderLoadedContent.Text -> return emptyList()
        }
        return try {
            document.blocks(chapterIndex)
        } catch (e: Exception) {
            AppLog.w("ReaderVM", "loadChapterBlocks failed: ${e.message}")
            emptyList()
        }
    }

    /** 提取章节纯文本（供锚点解析等场景使用）。 */
    suspend fun extractChapterText(
        loadedBookProvider: () -> ReaderLoadedBook?,
        bookId: String,
        chapterIndex: Int,
    ): String {
        val book = loadedBookProvider() ?: return ""
        if (book.id != bookId) return ""
        val document = when (val content = book.content) {
            is ReaderLoadedContent.Epub -> content.document
            is ReaderLoadedContent.Markdown -> content.document
            is ReaderLoadedContent.Text -> return ""
        }
        return try {
            document.text(chapterIndex)
        } catch (e: Exception) {
            AppLog.w("ReaderVM", "extractChapterText failed: ${e.message}")
            ""
        }
    }

    fun clear() {
        chapterLoadJob?.cancel()
        chapterLoadJob = null
        chapterLoadGeneration.incrementAndGet()
    }
}
