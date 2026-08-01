package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Trace
import android.os.SystemClock
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.ui.screen.ReaderSheet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * 阅读器唯一的文档打开入口。
 *
 * 这里负责 IO、初始进度与资源所有权；Composable 不再直接打开 URI、创建 EPUB 缓存
 * 或持有流式 TXT 临时文件。分页、Locator 与进度算法仍留在原有阅读协调层。
 */
class ReaderDocumentLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epubRepository: EpubRepository,
    private val bookRepository: BookRepository,
    private val bookContentDao: BookContentDao,
    private val readingProgressDao: ReadingProgressDao,
    private val settingsStore: SettingsStore,
) {
    suspend fun load(bookId: String): ReaderLoadedBook {
        require(bookId.isNotBlank()) { "未指定书籍" }
        val metadata = bookRepository.getById(bookId)
            ?: throw IllegalArgumentException("书籍不存在或已被删除")
        val progress = readingProgressDao.getByBook(bookId)

        val content = when (metadata.format.lowercase()) {
            "epub" -> {
                val book = epubRepository.cached(bookId)
                    ?: epubRepository.openStoredEpub(metadata)
                if (book.chapters.isEmpty()) {
                    throw IllegalArgumentException("EPUB 中没有可阅读章节")
                }
                val chapterIndex = epubRepository.loadProgress(bookId)
                    .coerceIn(0, book.chapters.lastIndex)
                val document = EpubDocument(book)
                try {
                    ReaderLoadedContent.Epub(
                        book = book,
                        document = document,
                        initialChapterIndex = chapterIndex,
                        initialChapterBlocks = document.blocks(chapterIndex),
                        initialOffsetInChapter = epubRepository.loadProgressOffset(bookId),
                    )
                } catch (error: Throwable) {
                    document.close()
                    throw error
                }
            }

            "md", "markdown" -> {
                val localUri = metadata.local_uri
                    ?: metadata.local_content_path?.let { Uri.fromFile(File(it)).toString() }
                val loadAttempt = localUri?.let { value ->
                    runCatching {
                        Trace.beginSection("MarkdownDocumentLoad")
                        try {
                            TextStreamLoader(context.cacheDir).load(
                                context = context,
                                uri = Uri.parse(value),
                                reportedSize = metadata.size.toLong(),
                            )
                        } finally {
                            Trace.endSection()
                        }
                    }
                }
                val result = loadAttempt?.getOrNull()
                    ?: throw loadAttempt?.exceptionOrNull()
                        ?: IllegalArgumentException("本书暂无可阅读的 Markdown 正文（需重新导入或同步下载）")
                val markdownDocument = if (result.isStreaming) {
                    val file = result.sourceFile
                        ?: throw IllegalStateException("流式 Markdown 缺少源文件")
                    val index = result.fileIndex
                        ?: throw IllegalStateException("流式 Markdown 缺少文件索引")
                    MarkdownDocument.fromFileIndex(file, index)
                } else {
                    MarkdownDocument(result.fullText ?: "")
                }
                val stored = parseStoredOffset(progress?.current_location_json)
                val initialOffset = if (stored.space == "canonical") {
                    stored.offset
                } else {
                    // 旧版纯文本 Markdown 的进度按源文本偏移存储，需迁移到规范文本偏移
                    markdownDocument.migrateLegacyOffset(stored.offset)
                }
                ReaderLoadedContent.Markdown(
                    document = markdownDocument,
                    ownedTempFile = result.takeIf { it.isStreaming }?.tempFile,
                    initialAbsoluteOffset = initialOffset,
                )
            }

            else -> {
                val localUri = metadata.local_uri
                    ?: metadata.local_content_path?.let { Uri.fromFile(File(it)).toString() }
                val loadAttempt = localUri?.let { value ->
                    val traceStartNs = SystemClock.elapsedRealtimeNanos()
                    val result = runCatching {
                        Trace.beginSection("TxtDocumentLoad")
                        try {
                            TextStreamLoader(context.cacheDir).load(
                                context = context,
                                uri = Uri.parse(value),
                                reportedSize = metadata.size.toLong(),
                            )
                        } finally {
                            Trace.endSection()
                        }
                    }
                    val traceEndNs = SystemClock.elapsedRealtimeNanos()
                    android.util.Log.d("TxtPerfTrace", "TxtDocumentLoad: ${(traceEndNs - traceStartNs) / 1_000_000} ms")
                    result
                }
                val result = loadAttempt?.getOrNull()
                val fullText = result?.fullText
                    ?: bookContentDao.getByBook(bookId)?.reader_preview
                    ?: ""
                val streamingDocument = result?.takeIf { it.isStreaming }?.document
                if (fullText.isBlank() && streamingDocument == null) {
                    throw loadAttempt?.exceptionOrNull()
                        ?: IllegalArgumentException("本书暂无可阅读的正文（需重新导入或同步下载）")
                }

                // ── P0 优化：在 IO 线程预检测章节，避免 ReaderScreen 组合时同步阻塞主线程 ──
                val ruleId = settingsStore.loadTxtTocRule(bookId)
                val preDetected: List<DocChapter> = if (fullText.isNotEmpty() && streamingDocument == null) {
                    val detectStartNs = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("TxtChapterDetect")
                    val chapters = try {
                        TxtChapterDetector.detect(fullText, ruleId)
                    } finally {
                        Trace.endSection()
                    }
                    val detectEndNs = SystemClock.elapsedRealtimeNanos()
                    android.util.Log.d("TxtPerfSubTrace", "TxtChapterDetect: ${(detectEndNs - detectStartNs) / 1_000_000} ms, preDetect=true")
                    chapters.mapIndexed { i, c ->
                        DocChapter(
                            index = i,
                            title = c.title,
                            startOffset = c.startOffset,
                            charCount = c.charCount,
                            charCountIsEstimated = false,
                        )
                    }
                } else {
                    emptyList()
                }

                ReaderLoadedContent.Text(
                    fullText = fullText,
                    streamingDocument = streamingDocument,
                    fileIndex = result?.takeIf { it.isStreaming }?.fileIndex,
                    ownedTempFile = result?.takeIf { it.isStreaming }?.tempFile,
                    initialAbsoluteOffset = parseStoredAbsoluteOffset(
                        progress?.current_location_json,
                    ),
                    preDetectedChapters = preDetected,
                    preDetectedRuleId = ruleId,
                )
            }
        }

        val initialProgress = when (content) {
            is ReaderLoadedContent.Epub -> {
                if (content.book.chapters.isEmpty()) {
                    0f
                } else {
                    ((content.initialChapterIndex + 1).toFloat() / content.book.chapters.size) * 100f
                }
            }

            is ReaderLoadedContent.Text -> progress?.progress_percent ?: 0f
            is ReaderLoadedContent.Markdown -> progress?.progress_percent ?: 0f
        }

        return ReaderLoadedBook(
            id = metadata.id,
            title = metadata.title,
            author = metadata.author,
            originalFileName = metadata.original_file_name,
            sizeBytes = metadata.size,
            savedReadingTimeMs = progress?.total_reading_time_ms ?: 0L,
            initialProgressPercent = initialProgress,
            content = content,
        )
    }
}

data class ReaderLoadedBook(
    val id: String,
    val title: String,
    val author: String?,
    val originalFileName: String?,
    val sizeBytes: Int,
    val savedReadingTimeMs: Long,
    val initialProgressPercent: Float,
    val content: ReaderLoadedContent,
)

sealed interface ReaderLoadedContent {
    fun release()

    data class Epub(
        val book: EpubBook,
        val document: EpubDocument,
        val initialChapterIndex: Int,
        val initialChapterBlocks: List<DocBlock>,
        val initialOffsetInChapter: Int,
    ) : ReaderLoadedContent {
        override fun release() {
            document.close()
        }
    }

    data class Text(
        val fullText: String,
        val streamingDocument: PlainTextDocument?,
        val fileIndex: TxtFileIndex?,
        internal val ownedTempFile: File?,
        val initialAbsoluteOffset: Int,
        /** 在 IO 线程预检测的章节列表（仅小文件路径非空）。 */
        val preDetectedChapters: List<DocChapter> = emptyList(),
        /** 预检测使用的规则 ID。ReaderScreen 规则不匹配时 fallback 到同步检测。 */
        val preDetectedRuleId: String = "builtin",
    ) : ReaderLoadedContent {
        override fun release() {
            ownedTempFile?.delete()
        }
    }

    data class Markdown(
        val document: MarkdownDocument,
        internal val ownedTempFile: File?,
        val initialAbsoluteOffset: Int,
    ) : ReaderLoadedContent {
        override fun release() {
            ownedTempFile?.delete()
        }
    }
}

internal fun parseStoredAbsoluteOffset(locationJson: String?): Int =
    parseStoredOffset(locationJson).offset

internal data class StoredOffset(
    val offset: Int,
    val space: String?,
)

internal fun parseStoredOffset(locationJson: String?): StoredOffset {
    if (locationJson.isNullOrBlank()) return StoredOffset(0, null)
    val offset = Regex("\"offset\"\\s*:\\s*(\\d+)")
        .find(locationJson)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.coerceAtLeast(0)
        ?: 0
    val space = Regex("\"space\"\\s*:\\s*\"([^\"]+)\"")
        .find(locationJson)
        ?.groupValues
        ?.getOrNull(1)
    return StoredOffset(offset, space)
}

data class ReaderUiState(
    val requestedBookId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadedBook: ReaderLoadedBook? = null,
) {
    val isReady: Boolean
        get() = loadedBook != null && !isLoading && errorMessage == null
}

// ── 章节加载结果 ─────────────────────────────────────────────
sealed interface ChapterLoadResult {
    data class Loading(val bookId: String, val chapterIndex: Int) : ChapterLoadResult
    data class Loaded(val bookId: String, val chapterIndex: Int, val blocks: List<DocBlock>) : ChapterLoadResult
    data class Error(val bookId: String, val chapterIndex: Int, val message: String) : ChapterLoadResult
}

// ── TXT 规则扫描结果 ─────────────────────────────────────────
data class TxtRuleScanResult(
    val ruleId: String,
    val fileIndex: TxtFileIndex? = null,
    val document: PlainTextDocument? = null,
    val error: String? = null,
)

sealed interface ReaderAction {
    // ── 文档协调 ──
    data class OpenBook(val bookId: String) : ReaderAction
    data object Retry : ReaderAction
    data class LoadChapter(val bookId: String, val chapterIndex: Int) : ReaderAction
    data class ScanTxtTocRule(val filePath: String, val ruleId: String) : ReaderAction

    // ── UI 状态变更 ──
    data class ToggleControls(val visible: Boolean? = null) : ReaderAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderAction
    data object CloseSheet : ReaderAction
    data class SetSelectedText(val text: String, val rangeStart: Int, val globalOffset: Int) : ReaderAction
    data object ClearSelection : ReaderAction
    data class SetShowTts(val show: Boolean) : ReaderAction
    data class SetSearchQuery(val query: String) : ReaderAction
    data class SetShowOverflow(val show: Boolean) : ReaderAction
    data class SetNoteOpen(val open: Boolean) : ReaderAction
    data class SetNoteBody(val body: String) : ReaderAction
    data object ToggleColorRow : ReaderAction
    data class SetSheetOpenGuard(val guard: Boolean) : ReaderAction

    // ── 数据写入 ──
    data class SaveHighlight(val highlight: HighlightEntity) : ReaderAction
    data class DeleteHighlight(val highlightId: String) : ReaderAction
    data class UpdateHighlightColor(val highlightId: String, val color: String) : ReaderAction
    data class UpdateHighlightNote(val highlightId: String, val note: String) : ReaderAction
    data class SaveNote(val note: NoteEntity) : ReaderAction
    data class DeleteNote(val noteId: String) : ReaderAction
    data class AddBookmark(val bookId: String, val offset: Int, val title: String) : ReaderAction
    data class ConvertHighlightToNote(val highlightId: String) : ReaderAction
    data class ConvertHighlightToInspiration(val highlightId: String) : ReaderAction
    data class SaveInspiration(val inspiration: InspirationEntity) : ReaderAction
    data class CreateCategory(val name: String) : ReaderAction
    data class CreateTag(val name: String) : ReaderAction
    data class SaveProgress(val progress: ReadingProgressEntity) : ReaderAction
    data class SaveEpubProgress(val bookId: String, val chapterIndex: Int, val percent: Float, val offsetInChapter: Int = 0) : ReaderAction
    data class DeleteBook(val bookId: String) : ReaderAction

    // ── 设置 ──
    data class LoadTxtTocRule(val bookId: String) : ReaderAction
    data class SaveTxtTocRule(val bookId: String, val ruleId: String) : ReaderAction
    data class SaveTtsResume(val bookId: String, val chapterIndex: Int, val offset: Int) : ReaderAction
}
