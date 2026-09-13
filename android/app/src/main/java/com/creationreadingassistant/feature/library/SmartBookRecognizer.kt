package com.creationreadingassistant.feature.library

import android.net.Uri
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class RecognitionLimits(
    /** Bounds all SAF rows traversed, including directories, so deep folder-only trees stay finite. */
    val maxTraversalEntries: Int = 2_000,
    val maxCandidates: Int = 500,
    val maxDepth: Int = 16,
    val maxSampleBytes: Int = 64 * 1_024,
) {
    init {
        require(maxTraversalEntries > 0)
        require(maxCandidates > 0)
        require(maxDepth >= 0)
        require(maxSampleBytes in 4..(1_024 * 1_024))
    }
}

data class RecognitionRequest(
    val root: LibraryRoot,
    val limits: RecognitionLimits = RecognitionLimits(),
)

enum class RecognitionConfidence { HIGH, MEDIUM, LOW }

enum class RecognitionDecision { RECOMMENDED, REVIEW, REJECTED }

enum class RecognitionReason(val label: String) {
    EPUB_ZIP_HEADER("EPUB 文件头有效，导入时继续校验容器"),
    MULTIPLE_CHAPTER_HEADINGS("样本中发现多个可靠章节标题"),
    READABLE_TEXT("正文可正常解码"),
    SHORT_TEXT("文本较短，可能是短篇或片段"),
    NO_CHAPTER_HEADINGS("未在样本中发现可靠章节标题"),
    CODE_OR_LOG_LIKE("内容更像代码或日志"),
    EMPTY_FILE("文件为空"),
    BINARY_CONTENT("内容包含大量二进制控制字符"),
    INVALID_EPUB("扩展名为 EPUB，但文件头不是 ZIP"),
    UNSUPPORTED_FORMAT("无法确认支持的书籍格式"),
    UNREADABLE("文件无法读取"),
}

data class RecognizedBookCandidate(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val format: String?,
    val encoding: String?,
    val suggestedTitle: String,
    val suggestedAuthor: String?,
    val sampledChapterHeadings: Int,
    val confidence: RecognitionConfidence,
    val decision: RecognitionDecision,
    val reasons: List<RecognitionReason>,
    /** 候选指纹（>32 MiB 时由 probe 计算），只用于去重提示，不是安全签名。 */
    val fingerprint: String? = null,
) {
    val defaultSelected: Boolean get() = decision == RecognitionDecision.RECOMMENDED
}

data class RecognitionSummary(
    val discovered: Int,
    val processed: Int,
    val recommended: Int,
    val review: Int,
    val rejected: Int,
    val skippedFiles: Int,
    val unreadableFolders: Int,
    val truncated: Boolean,
)

sealed interface RecognitionEvent {
    data class Started(val limits: RecognitionLimits) : RecognitionEvent
    data class CandidateDiscovered(val uri: Uri, val index: Int, val total: Int) : RecognitionEvent
    data class CandidateClassified(val candidate: RecognizedBookCandidate) : RecognitionEvent
    data class Progress(val summary: RecognitionSummary) : RecognitionEvent
    data class Warning(val message: String) : RecognitionEvent
    data class Completed(val summary: RecognitionSummary) : RecognitionEvent
}

/**
 * Cold recognition flow. Cancelling collection stops discovery or probing at the next suspension
 * point. Cancellation propagates as coroutine cancellation, so callers can always distinguish it
 * from a completed scan without depending on a terminal event that a cancelled collector cannot
 * reliably receive.
 */
interface SmartBookRecognizer {
    fun recognize(request: RecognitionRequest): Flow<RecognitionEvent>
}

internal data class RecognitionProbe(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val sample: ByteArray,
    val sampleTruncated: Boolean,
    /** 超大文件的「大小 + 首尾分块哈希」候选指纹；只在去重提示里使用，不影响识别置信。 */
    val fingerprint: String? = null,
)

internal interface BookRecognitionSource {
    suspend fun discover(root: LibraryRoot, limits: RecognitionLimits): BookSourceScanResult
    suspend fun probe(uri: Uri, maxSampleBytes: Int): RecognitionProbe
}

internal class DefaultSmartBookRecognizer(
    private val source: BookRecognitionSource,
    private val classifier: BookSampleClassifier = BookSampleClassifier(),
) : SmartBookRecognizer {

    override fun recognize(request: RecognitionRequest): Flow<RecognitionEvent> = flow {
        emit(RecognitionEvent.Started(request.limits))
        val discovery = try {
            source.discover(request.root, request.limits)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            emit(RecognitionEvent.Warning(error.message ?: "无法读取书籍目录"))
            emit(RecognitionEvent.Completed(emptyRecognitionSummary(unreadableFolders = 1)))
            return@flow
        }

        if (discovery.unreadableFolders > 0) {
            emit(RecognitionEvent.Warning("${discovery.unreadableFolders} 个目录无法读取"))
        }
        if (discovery.truncated) {
            emit(RecognitionEvent.Warning("扫描达到安全上限，结果已截断"))
        }

        var summary = emptyRecognitionSummary(
            discovered = discovery.bookUris.size,
            skippedFiles = discovery.skippedFiles,
            unreadableFolders = discovery.unreadableFolders,
            truncated = discovery.truncated,
        )
        discovery.bookUris.forEachIndexed { index, uri ->
            currentCoroutineContext().ensureActive()
            emit(RecognitionEvent.CandidateDiscovered(uri, index + 1, discovery.bookUris.size))
            val candidate = try {
                classifier.classify(source.probe(uri, request.limits.maxSampleBytes))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                classifier.unreadable(uri)
            }
            emit(RecognitionEvent.CandidateClassified(candidate))
            summary = summary.with(candidate)
            emit(RecognitionEvent.Progress(summary))
        }
        emit(RecognitionEvent.Completed(summary))
    }
}

internal class BookSampleClassifier {

    fun classify(probe: RecognitionProbe): RecognizedBookCandidate {
        if (probe.sample.isEmpty() || probe.sizeBytes == 0L) {
            return rejected(probe, RecognitionReason.EMPTY_FILE)
        }
        val verdict = FormatClassifier.classify(formatHint(probe), probe.displayName, probe.sample)
        if (verdict is FormatClassifier.Verdict.Rejected) {
            val reason = if (verdict.claimedFormat == "epub") {
                RecognitionReason.INVALID_EPUB
            } else {
                RecognitionReason.UNSUPPORTED_FORMAT
            }
            return rejected(probe, reason)
        }
        val format = (verdict as FormatClassifier.Verdict.Accepted).format
        if (format == "epub") {
            return RecognizedBookCandidate(
                uri = probe.uri,
                displayName = probe.displayName,
                sizeBytes = probe.sizeBytes,
                format = format,
                encoding = null,
                suggestedTitle = titleFromFileName(probe.displayName),
                suggestedAuthor = null,
                sampledChapterHeadings = 0,
                confidence = RecognitionConfidence.HIGH,
                decision = RecognitionDecision.RECOMMENDED,
                reasons = listOf(RecognitionReason.EPUB_ZIP_HEADER),
                fingerprint = probe.fingerprint,
            )
        }

        val decoded = PlainTextDecoder.decode(probe.sample)
        if (looksBinary(probe.sample, decoded.encoding, decoded.text)) {
            return rejected(probe, RecognitionReason.BINARY_CONTENT, format)
        }
        val text = decoded.text.trim()
        if (text.isEmpty()) return rejected(probe, RecognitionReason.EMPTY_FILE, format)

        val lines = text.lineSequence().take(MAX_SAMPLE_LINES).toList()
        val chapterHeadings = lines.count { TxtChapterDetector.isChapterTitle(it) }
        val codeOrLogLike = looksLikeCodeOrLog(lines)
        val explicit = explicitMetadata(lines)
        val fallback = metadataFromFileName(probe.displayName)
        val reasons = buildList {
            add(RecognitionReason.READABLE_TEXT)
            when {
                chapterHeadings >= 2 -> add(RecognitionReason.MULTIPLE_CHAPTER_HEADINGS)
                text.length < MIN_SUBSTANTIAL_TEXT_CHARS && !probe.sampleTruncated -> add(RecognitionReason.SHORT_TEXT)
                else -> add(RecognitionReason.NO_CHAPTER_HEADINGS)
            }
            if (codeOrLogLike) add(RecognitionReason.CODE_OR_LOG_LIKE)
        }
        val confidence = when {
            codeOrLogLike -> RecognitionConfidence.LOW
            chapterHeadings >= 2 -> RecognitionConfidence.HIGH
            text.length >= MIN_SUBSTANTIAL_TEXT_CHARS || probe.sampleTruncated -> RecognitionConfidence.MEDIUM
            else -> RecognitionConfidence.LOW
        }
        return RecognizedBookCandidate(
            uri = probe.uri,
            displayName = probe.displayName,
            sizeBytes = probe.sizeBytes,
            format = format,
            encoding = decoded.encoding,
            suggestedTitle = explicit.title ?: fallback.title ?: "未命名书籍",
            suggestedAuthor = explicit.author ?: fallback.author,
            sampledChapterHeadings = chapterHeadings,
            confidence = confidence,
            decision = if (confidence == RecognitionConfidence.HIGH) {
                RecognitionDecision.RECOMMENDED
            } else {
                RecognitionDecision.REVIEW
            },
            reasons = reasons,
            fingerprint = probe.fingerprint,
        )
    }

    fun unreadable(uri: Uri): RecognizedBookCandidate = RecognizedBookCandidate(
        uri = uri,
        displayName = "无法读取的文件",
        sizeBytes = null,
        format = null,
        encoding = null,
        suggestedTitle = "未命名书籍",
        suggestedAuthor = null,
        sampledChapterHeadings = 0,
        confidence = RecognitionConfidence.LOW,
        decision = RecognitionDecision.REJECTED,
        reasons = listOf(RecognitionReason.UNREADABLE),
    )

    private fun rejected(
        probe: RecognitionProbe,
        reason: RecognitionReason,
        format: String? = null,
    ) = RecognizedBookCandidate(
        uri = probe.uri,
        displayName = probe.displayName,
        sizeBytes = probe.sizeBytes,
        format = format,
        encoding = null,
        suggestedTitle = titleFromFileName(probe.displayName),
        suggestedAuthor = null,
        sampledChapterHeadings = 0,
        confidence = RecognitionConfidence.LOW,
        decision = RecognitionDecision.REJECTED,
        reasons = listOf(reason),
    )

    private fun looksBinary(bytes: ByteArray, encoding: String, text: String): Boolean {
        if (!encoding.startsWith("UTF-16") && bytes.count { it == 0.toByte() } * 100 > bytes.size) return true
        if (text.isEmpty()) return false
        val badControls = text.count { char -> char.code < 32 && char !in "\n\r\t\u000C" }
        val replacements = text.count { it == '\uFFFD' }
        return badControls * 100 > text.length || replacements * 50 > text.length
    }

    private fun looksLikeCodeOrLog(lines: List<String>): Boolean {
        val meaningful = lines.map(String::trim).filter(String::isNotEmpty).take(80)
        if (meaningful.size < 3) return false
        val signals = meaningful.count { line ->
            CODE_PREFIX.containsMatchIn(line) ||
                LOG_PREFIX.containsMatchIn(line) ||
                (line.endsWith('{') && line.length < 100)
        }
        return signals >= 3 && signals * 5 >= meaningful.size
    }

    private fun explicitMetadata(lines: List<String>): MetadataSuggestion {
        var title: String? = null
        var author: String? = null
        lines.take(40).forEach { line ->
            val trimmed = line.trim()
            if (title == null) title = TITLE_FIELD.matchEntire(trimmed)?.groupValues?.get(1)?.cleanMetadata()
            if (author == null) author = AUTHOR_FIELD.matchEntire(trimmed)?.groupValues?.get(1)?.cleanMetadata()
        }
        return MetadataSuggestion(title, author)
    }

    private fun metadataFromFileName(fileName: String): MetadataSuggestion {
        val base = titleFromFileName(fileName)
        BOOK_TITLE_AUTHOR.matchEntire(base)?.let { match ->
            return MetadataSuggestion(match.groupValues[1].cleanMetadata(), match.groupValues[2].cleanMetadata())
        }
        DASH_AUTHOR.matchEntire(base)?.let { match ->
            return MetadataSuggestion(match.groupValues[1].cleanMetadata(), match.groupValues[2].cleanMetadata())
        }
        return MetadataSuggestion(base, null)
    }

    private fun titleFromFileName(fileName: String): String = fileName
        .substringBeforeLast('.', fileName)
        .trim()
        .removeSurrounding("《", "》")
        .ifBlank { "未命名书籍" }

    private fun formatFromMimeType(mimeType: String?): String? = when (mimeType?.trim()?.lowercase()) {
        "application/epub+zip" -> "epub"
        "text/markdown", "text/x-markdown" -> "md"
        "text/plain" -> "txt"
        else -> null
    }

    private fun formatHint(probe: RecognitionProbe): String? {
        val extension = probe.displayName.substringAfterLast('.', "").lowercase()
        return if (extension in SUPPORTED_EXTENSIONS) null else formatFromMimeType(probe.mimeType)
    }

    private fun String.cleanMetadata(): String? = trim().takeIf { it.length in 1..80 }

    private data class MetadataSuggestion(val title: String?, val author: String?)

    private companion object {
        const val MAX_SAMPLE_LINES = 2_000
        const val MIN_SUBSTANTIAL_TEXT_CHARS = 1_000
        val SUPPORTED_EXTENSIONS = setOf("epub", "txt", "md", "markdown")
        val TITLE_FIELD = Regex("^(?:书名|作品名)[\\s：:]+(.{1,80})$")
        val AUTHOR_FIELD = Regex("^(?:作者|著者)[\\s：:]+(.{1,80})$")
        val BOOK_TITLE_AUTHOR = Regex("^《(.{1,60})》[\\s_-]*(?:作者[：:]?)?[\\s_-]*(.{1,40})$")
        val DASH_AUTHOR = Regex("^(.{1,60})\\s[-—_]\\s(.{1,40})$")
        val CODE_PREFIX = Regex(
            "^(?:package|import|class|interface|fun|function|const|let|var|SELECT|INSERT|UPDATE|DELETE)\\b",
            RegexOption.IGNORE_CASE,
        )
        val LOG_PREFIX = Regex(
            "^(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}:\\d{2}:\\d{2}|\\[(?:debug|info|warn|error)])",
            RegexOption.IGNORE_CASE,
        )
    }
}

private fun emptyRecognitionSummary(
    discovered: Int = 0,
    skippedFiles: Int = 0,
    unreadableFolders: Int = 0,
    truncated: Boolean = false,
) = RecognitionSummary(
    discovered = discovered,
    processed = 0,
    recommended = 0,
    review = 0,
    rejected = 0,
    skippedFiles = skippedFiles,
    unreadableFolders = unreadableFolders,
    truncated = truncated,
)

private fun RecognitionSummary.with(candidate: RecognizedBookCandidate): RecognitionSummary = copy(
    processed = processed + 1,
    recommended = recommended + if (candidate.decision == RecognitionDecision.RECOMMENDED) 1 else 0,
    review = review + if (candidate.decision == RecognitionDecision.REVIEW) 1 else 0,
    rejected = rejected + if (candidate.decision == RecognitionDecision.REJECTED) 1 else 0,
)
