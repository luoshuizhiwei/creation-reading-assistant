package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Trace
import android.os.SystemClock
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.LegadoChapterPosition
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
    private val rulesRepository: RulesRepository,
) {
    /**
     * 测试 seam：JVM 单测里 android.net.Uri.parse 恒返回 null
     * （`unitTests.isReturnDefaultValues = true`），注入受控 Uri 以便
     * 验证 TXT 加载的迁移 / profile 链路。
     */
    internal var uriParser: (String) -> Uri = Uri::parse

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
                                uri = uriParser(value),
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

                // P1-A：旧 DataStore 单选一次性迁移到 Room（marker 防重复重放旧值），
                // Room 是目录状态的唯一来源；effectiveToc → TxtTocProfile。
                if (!settingsStore.isTxtTocRuleMigrated(bookId)) {
                    val legacy = settingsStore.loadTxtTocRule(bookId)
                    rulesRepository.execute(bookId, RuleCommand.MigrateLegacyTocRule(legacy))
                    settingsStore.markTxtTocRuleMigrated(bookId)
                }
                val profile = rulesRepository.observe(bookId).first().effectiveTocProfile

                val loadAttempt = localUri?.let { value ->
                    val traceStartNs = SystemClock.elapsedRealtimeNanos()
                    val result = runCatching {
                        Trace.beginSection("TxtDocumentLoad")
                        try {
                            TextStreamLoader(context.cacheDir).load(
                                context = context,
                                uri = uriParser(value),
                                reportedSize = metadata.size.toLong(),
                                profile = profile,
                            )
                        } finally {
                            Trace.endSection()
                        }
                    }
                    val traceEndNs = SystemClock.elapsedRealtimeNanos()
                    AppLog.debug("TxtPerfTrace", "TxtDocumentLoad: ${(traceEndNs - traceStartNs) / 1_000_000} ms")
                    result
                }
                // R6：缓存预览只是导入时的前 20,000 字符，不是完整正文。打开尝试已真实
                // 失败（文件缺失 / provider 权限丢失 / 打开抛错）或本就没有源引用时，
                // 必须把真实原因抛给现有错误展示链，绝不能因 reader_preview 非空就
                // 返回成功的 ReaderLoadedBook——否则截断内容会被当整本书继续保存进度。
                // 只有打开真实成功后的流式路径（fullText 天然为 null）才允许沿用预览兜底。
                val result = loadAttempt?.getOrNull()
                    ?: throw loadAttempt?.exceptionOrNull()
                        ?: IllegalArgumentException("本书暂无可阅读的正文（需重新导入或同步下载）")
                val fullText = result.fullText
                    ?: bookContentDao.getByBook(bookId)?.reader_preview
                    ?: ""
                val streamingDocument = result.takeIf { it.isStreaming }?.document
                if (fullText.isBlank() && streamingDocument == null) {
                    throw IllegalArgumentException("本书暂无可阅读的正文（需重新导入或同步下载）")
                }

                // ── P0 优化：在 IO 线程预检测章节，避免 ReaderScreen 组合时同步阻塞主线程 ──
                // 预检测身份 = profile.key：规则变化后与快照 key 不一致，组合层自动回退重检。
                val preDetected: List<DocChapter> = if (fullText.isNotEmpty() && streamingDocument == null) {
                    val detectStartNs = SystemClock.elapsedRealtimeNanos()
                    Trace.beginSection("TxtChapterDetect")
                    val chapters = try {
                        TxtChapterDetector.detect(fullText, profile.patterns, profile.densityGuard)
                    } finally {
                        Trace.endSection()
                    }
                    val detectEndNs = SystemClock.elapsedRealtimeNanos()
                    AppLog.debug(
                        "TxtPerfSubTrace",
                        "TxtChapterDetect: ${(detectEndNs - detectStartNs) / 1_000_000} ms, preDetect=true, key=${profile.key}",
                    )
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

                // ── S12-P2-2：流式大 TXT 的 Legado 位置换算章节源 ──
                // TxtFileIndex 来自同一 [profile] 的**全文单遍扫描**（TxtFileScanner，
                // detectedRuleId = profile.key），是完整逻辑章节边界（charStart/charCount
                // 覆盖全书），与本书目录/阅读单元同源 —— 不是 ReadingUnit / 分页片段窗口。
                // 仅当 ①规则身份匹配、②真实检测出多章（≥2；「全文」单章 = 没有任何可靠
                // 边界）时才把 Legado 章号映射到该表；否则维持空表，marker 原样保留、
                // 打开落书首，绝不凭不完整边界编造绝对位置。
                val streamingFileIndex = result?.takeIf { it.isStreaming }?.fileIndex
                val streamingChapters: List<DocChapter> = streamingFileIndex
                    ?.takeIf { it.detectedRuleId == profile.key && it.chapters.size >= 2 }
                    ?.chapters
                    ?.map { entry ->
                        DocChapter(
                            index = entry.index,
                            title = entry.title,
                            startOffset = entry.charStart.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                            charCount = entry.charCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                            charCountIsEstimated = false,
                        )
                    }
                    ?: emptyList()
                val conversionChapters = if (streamingDocument != null) streamingChapters else preDetected
                val conversionTextLength = streamingFileIndex?.totalCharCount
                    ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                    ?.toInt()
                    ?: fullText.length
                // S12-P2-2 用户提示接缝：存在 Legado 导入位置、但本次打开未能换算
                // （流式目录兜底「全文」单章 / 章号越界 → 拿不到可靠边界）时非空。
                // 数据标记保持原样（未被伪造成原生进度）；UI 层可据此提示
                // 「导入的阅读位置暂未生效」（消费端见协调报告 SEAM REQUEST）。
                val importedLegadoPosition = progress?.current_location_json
                    ?.let { LegadoProgressMarker.decode(it) }
                val legadoPositionConverted = importedLegadoPosition != null &&
                    conversionChapters.getOrNull(importedLegadoPosition.chapterIndex) != null

                ReaderLoadedContent.Text(
                    fullText = fullText,
                    streamingDocument = streamingDocument,
                    fileIndex = streamingFileIndex,
                    sourceFile = result?.takeIf { it.isStreaming }?.sourceFile,
                    ownedTempFile = result?.takeIf { it.isStreaming }?.tempFile,
                    initialAbsoluteOffset = resolveImportedTextOffset(
                        locationJson = progress?.current_location_json,
                        chapters = conversionChapters,
                        textLength = conversionTextLength,
                    ),
                    preDetectedChapters = preDetected,
                    preDetectedRuleId = profile.key,
                    unconvertedLegadoPosition = importedLegadoPosition?.takeIf { !legadoPositionConverted },
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
        /**
         * 可重扫的 backing/source 文件：直接 file:// 源文件或复制到 cache 的临时副本。
         * 仅流式大文件保留；release 绝不删除它（直接源文件归用户所有）。
         */
        internal val sourceFile: File?,
        /** release 时可删除的 owned 临时文件；只有 cache 临时副本非空，直接源文件恒为 null。 */
        internal val ownedTempFile: File?,
        val initialAbsoluteOffset: Int,
        /** 在 IO 线程预检测的章节列表（仅小文件路径非空）。 */
        val preDetectedChapters: List<DocChapter> = emptyList(),
        /** 预检测使用的规则 ID。ReaderScreen 规则不匹配时 fallback 到同步检测。 */
        val preDetectedRuleId: String = "builtin",
        /**
         * S12-P2-2 用户提示接缝（UI 消费端尚未接线，见协调报告 SEAM REQUEST）：
         * 存在 Legado 导入位置、但本次打开**未能**换算（流式目录兜底「全文」单章 /
         * 章号越界，即拿不到可靠章节边界）时非空，携带未换算的 marker 坐标。
         * 此时 [initialAbsoluteOffset] 恒为 0（书首），数据标记原样保留；
         * UI 层可据此提示「导入的阅读位置暂未生效，已从书首打开」。
         * 换算成功、无导入位置或位置已是原生 locator 时为 null。
         */
        val unconvertedLegadoPosition: LegadoChapterPosition? = null,
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

internal fun parseStoredAbsoluteOffset(locationJson: String?): Int {
    // 优先读取统一 v2 locator 的绝对偏移（与写入时一致）；旧数据无 v2 时回退 legacy "offset"
    val v2 = LocatorCodec.locatorFromProgress(locationJson)
    if (v2?.legacyOffset != null) return v2.legacyOffset.coerceAtLeast(0)
    return parseStoredOffset(locationJson).offset
}

/**
 * 开源阅读的 TXT 进度是「章号 + 章内偏移」，不能直接当作本应用的全书偏移。
 *
 * [chapters] 必须是**完整逻辑章节边界**：小文件路径为 [TxtChapterDetector.detect]
 * 的预检测结果；流式大文件路径为同一 profile 全文扫描的 [TxtFileIndex] 章节表
 * （见调用处的身份与 ≥2 章门控）。任一路径拿不到可靠边界时返回 0（书首），
 * Legado marker 原样保留在库里（不被伪造成已恢复的原生位置），由后续原生保存
 * 在真正定位后才覆盖；EPUB 在 EpubRepository 读取 locator v2，不走本函数。
 */
internal fun resolveImportedTextOffset(
    locationJson: String?,
    chapters: List<DocChapter>,
    textLength: Int,
): Int {
    val legado = LegadoProgressMarker.decode(locationJson) ?: return parseStoredAbsoluteOffset(locationJson)
    val chapter = chapters.getOrNull(legado.chapterIndex) ?: return 0
    return (chapter.startOffset + legado.charOffset).coerceIn(0, textLength.coerceAtLeast(0))
}

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
