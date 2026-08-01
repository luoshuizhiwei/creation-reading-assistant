package com.creationreadingassistant.feature.reader.doc

import android.content.Context
import android.net.Uri
import android.os.Trace
import android.os.SystemClock
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * Unified text file loader that handles both small-file and streaming paths.
 * Never calls readBytes() before confirming actual file size is below threshold.
 *
 * When reportedSize is null, 0, or negative:
 *   - Stream-copy to a temp file while counting bytes
 *   - Route to small-file or streaming path based on ACTUAL byte count
 *
 * When reportedSize is positive:
 *   - Still verify by streaming copy (providers may report incorrect sizes)
 *   - Route based on actual byte count
 */
class TextStreamLoader(
    private val cacheDir: File,
    private val streamingThresholdBytes: Long = 5_000_000L, // 5MB
) {
    companion object {
        private val logger = Logger.getLogger("TextStreamLoader")
        private const val TEMP_PREFIX = "txt_stream_"
        private const val TEMP_SUFFIX = ".tmp"
    }

    data class LoadResult(
        val document: PlainTextDocument,
        val tempFile: File?,       // non-null for streaming mode from InputStream
        val isStreaming: Boolean,   // true if routed to streaming path
        val actualSizeBytes: Long, // actual file size in bytes
        val fileIndex: TxtFileIndex? = null, // non-null for streaming mode
        val fullText: String? = null, // non-null for small-file mode
        /** 实际被读取的源文件；InputStream 模式为临时文件，file URI 模式为原文件。 */
        val sourceFile: File? = null,
    )

    /**
     * Load from URI using ContentResolver.
     */
    fun load(context: Context, uri: Uri, reportedSize: Long?): LoadResult {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val directFile = uri.path?.let(::File)
            if (directFile != null && directFile.isFile && directFile.canRead()) {
                return loadDirectFile(directFile)
            }
        }
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法打开文件")
        return inputStream.use { load(it, reportedSize) }
    }

    /**
     * 应用内部文件无需先复制到 cache。小文件直接解码，大文件直接建立流式索引；
     * 返回结果不拥有源文件，关闭阅读器时不会删除它。
     */
    fun loadDirectFile(file: File): LoadResult {
        require(file.isFile && file.canRead()) { "无法读取文件" }
        val actualSize = file.length()
        require(actualSize > 0L) { "文件内容为空" }
        return if (actualSize <= streamingThresholdBytes) {
            val decoded = PlainTextDecoder.decode(file.readBytes())
            LoadResult(
                document = PlainTextDocument(decoded.text),
                tempFile = null,
                isStreaming = false,
                actualSizeBytes = actualSize,
                fullText = decoded.text,
                sourceFile = file,
            )
        } else {
            val indexStartNs = SystemClock.elapsedRealtimeNanos()
            val index = try {
                Trace.beginSection("TxtIndexLoad")
                TxtFileIndexCache(cacheDir).getOrBuild(file)
            } finally {
                Trace.endSection()
            }
            val indexEndNs = SystemClock.elapsedRealtimeNanos()
            android.util.Log.d("TxtPerfTrace", "TxtIndexLoad: ${(indexEndNs - indexStartNs) / 1_000_000} ms")
            LoadResult(
                document = PlainTextDocument.fromFileIndex(file, index),
                tempFile = null,
                isStreaming = true,
                actualSizeBytes = actualSize,
                fileIndex = index,
                sourceFile = file,
            )
        }
    }

    /**
     * Load from InputStream. This method takes ownership of copying the stream
     * to a temp file and routing to the appropriate loading path.
     * The caller is responsible for closing [inputStream]; this method does NOT
     * close it — use a `.use {}` block at the call site.
     */
    fun load(inputStream: InputStream, reportedSize: Long?): LoadResult {
        val tempFile = File(cacheDir, "${TEMP_PREFIX}${System.nanoTime()}${TEMP_SUFFIX}")
        try {
            // Always stream-copy to temp file first, counting actual bytes
            var actualSize = 0L
            tempFile.outputStream().use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (inputStream.read(buf).also { n = it } > 0) {
                    output.write(buf, 0, n)
                    actualSize += n
                }
            }

            return if (actualSize <= streamingThresholdBytes) {
                // Small-file path: read entire temp file into memory
                val bytes = tempFile.readBytes()
                val decoded = PlainTextDecoder.decode(bytes)
                val document = PlainTextDocument(decoded.text)
                // Delete temp file — small-file mode doesn't need it
                tempFile.delete()
                LoadResult(
                    document = document,
                    tempFile = null,
                    isStreaming = false,
                    actualSizeBytes = actualSize,
                    fullText = decoded.text,
                    sourceFile = tempFile,
                )
            } else {
                // Streaming path: scan with TxtFileScanner
                val index = TxtFileScanner.scan(tempFile)
                val document = PlainTextDocument.fromFileIndex(tempFile, index)
                LoadResult(
                    document = document,
                    tempFile = tempFile,
                    isStreaming = true,
                    actualSizeBytes = actualSize,
                    fileIndex = index,
                    sourceFile = tempFile,
                )
            }
        } catch (e: Exception) {
            // Clean up temp file on failure
            tempFile.delete()
            throw e
        }
    }

    /**
     * Clean up stale temp files older than [maxAgeMs] milliseconds.
     * Safe to call on app startup — won't delete files in active use.
     */
    fun cleanupStaleTempFiles(maxAgeMs: Long = 3_600_000L) { // 1 hour default
        val now = System.currentTimeMillis()
        var deletedCount = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX)) {
                if (now - file.lastModified() > maxAgeMs) {
                    if (file.delete()) deletedCount++
                }
            }
        }
        if (deletedCount > 0) {
            logger.info("Cleaned up $deletedCount stale temp file(s)")
        }
    }
}
