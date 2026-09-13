package com.creationreadingassistant.feature.library

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBookRecognizerTest {

    @Test
    fun `chaptered text is recommended with explainable metadata`() = runTest {
        val uri = uri("content://books/story.txt")
        val text = buildString {
            appendLine("书名：远山")
            appendLine("作者：测试作者")
            repeat(3) { chapter ->
                appendLine("第${chapter + 1}章 开始")
                appendLine("这是章节正文。".repeat(80))
            }
        }
        val candidate = BookSampleClassifier().classify(probe(uri, "fallback.txt", text.toByteArray()))

        assertEquals(RecognitionDecision.RECOMMENDED, candidate.decision)
        assertEquals(RecognitionConfidence.HIGH, candidate.confidence)
        assertEquals("远山", candidate.suggestedTitle)
        assertEquals("测试作者", candidate.suggestedAuthor)
        assertTrue(candidate.sampledChapterHeadings >= 2)
        assertTrue(RecognitionReason.MULTIPLE_CHAPTER_HEADINGS in candidate.reasons)
    }

    @Test
    fun `short readable text stays available for manual review`() = runTest {
        val candidate = BookSampleClassifier().classify(
            probe(uri("content://books/essay.txt"), "随笔.txt", "这是一篇没有章节的短篇随笔。".toByteArray()),
        )

        assertEquals(RecognitionDecision.REVIEW, candidate.decision)
        assertEquals(RecognitionConfidence.LOW, candidate.confidence)
        assertFalse(candidate.defaultSelected)
        assertEquals("随笔", candidate.suggestedTitle)
        assertTrue(RecognitionReason.SHORT_TEXT in candidate.reasons)
    }

    @Test
    fun `fake epub is rejected before import`() = runTest {
        val candidate = BookSampleClassifier().classify(
            probe(uri("content://books/fake.epub"), "fake.epub", "plain text".toByteArray()),
        )

        assertEquals(RecognitionDecision.REJECTED, candidate.decision)
        assertEquals(listOf(RecognitionReason.INVALID_EPUB), candidate.reasons)
    }

    @Test
    fun `epub zip header is recommended for importer validation`() = runTest {
        val candidate = BookSampleClassifier().classify(
            probe(
                uri("content://books/valid.epub"),
                "《远山》.epub",
                FormatClassifier.EPUB_MAGIC + byteArrayOf(1, 2, 3, 4),
            ),
        )

        assertEquals("epub", candidate.format)
        assertEquals(RecognitionDecision.RECOMMENDED, candidate.decision)
        assertEquals("远山", candidate.suggestedTitle)
        assertEquals(listOf(RecognitionReason.EPUB_ZIP_HEADER), candidate.reasons)
    }

    @Test
    fun `binary data with a text extension is rejected`() = runTest {
        val candidate = BookSampleClassifier().classify(
            probe(uri("content://books/binary.txt"), "binary.txt", ByteArray(512)),
        )

        assertEquals(RecognitionDecision.REJECTED, candidate.decision)
        assertEquals(listOf(RecognitionReason.BINARY_CONTENT), candidate.reasons)
    }

    @Test
    fun `supported mime type recognizes an extensionless text document`() = runTest {
        val candidate = BookSampleClassifier().classify(
            probe(uri("content://books/document/42"), "无扩展名", "正文内容。".repeat(250).toByteArray()),
        )

        assertEquals("txt", candidate.format)
        assertEquals(RecognitionDecision.REVIEW, candidate.decision)
        assertEquals(RecognitionConfidence.MEDIUM, candidate.confidence)
    }

    @Test
    fun `one unreadable file does not abort the recognition batch`() = runTest {
        val unreadable = uri("content://books/unreadable.txt")
        val readable = uri("content://books/readable.txt")
        val source = FakeRecognitionSource(
            uris = listOf(unreadable, readable),
            probes = mapOf(
                readable.toString() to probe(readable, "readable.txt", "普通文本。".repeat(80).toByteArray()),
            ),
        )

        val events = DefaultSmartBookRecognizer(source).recognize(request()).toList()
        val candidates = events.filterIsInstance<RecognitionEvent.CandidateClassified>().map { it.candidate }
        val completed = events.filterIsInstance<RecognitionEvent.Completed>().single().summary

        assertEquals(2, candidates.size)
        assertEquals(RecognitionReason.UNREADABLE, candidates.first().reasons.single())
        assertEquals(2, completed.processed)
        assertEquals(1, completed.rejected)
        assertEquals(1, completed.review)
    }

    @Test
    fun `coroutine cancellation is propagated instead of reported as completion`() = runTest {
        val target = uri("content://books/cancel.txt")
        val source = object : BookRecognitionSource {
            override suspend fun discover(root: LibraryRoot, limits: RecognitionLimits) =
                BookSourceScanResult(listOf(target), 0, 0, false)

            override suspend fun probe(uri: Uri, maxSampleBytes: Int): RecognitionProbe {
                throw CancellationException("cancelled")
            }
        }

        var failure: Throwable? = null
        try {
            DefaultSmartBookRecognizer(source).recognize(request()).toList()
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is CancellationException)
    }

    private fun request() = RecognitionRequest(
        root = LibraryRoot(
            treeUri = uri("content://books/tree/root"),
            displayName = "书籍",
            lastLocationDocumentId = null,
            sortMode = LibrarySortMode.NAME,
        ),
    )

    private fun probe(uri: Uri, name: String, sample: ByteArray) = RecognitionProbe(
        uri = uri,
        displayName = name,
        mimeType = "text/plain",
        sizeBytes = sample.size.toLong(),
        sample = sample,
        sampleTruncated = false,
    )

    private fun uri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        return uri
    }

    private class FakeRecognitionSource(
        private val uris: List<Uri>,
        private val probes: Map<String, RecognitionProbe>,
    ) : BookRecognitionSource {
        override suspend fun discover(root: LibraryRoot, limits: RecognitionLimits) =
            BookSourceScanResult(uris, skippedFiles = 0, unreadableFolders = 0, truncated = false)

        override suspend fun probe(uri: Uri, maxSampleBytes: Int): RecognitionProbe =
            probes[uri.toString()] ?: error("unreadable")
    }
}
