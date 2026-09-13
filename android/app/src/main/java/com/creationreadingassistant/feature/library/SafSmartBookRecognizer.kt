package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Production SAF Adapter for the smart-recognition Interface. */
@Singleton
class SafSmartBookRecognizer @Inject constructor(
    @ApplicationContext context: Context,
) : SmartBookRecognizer by DefaultSmartBookRecognizer(SafBookRecognitionSource(context.contentResolver))

@Module
@InstallIn(SingletonComponent::class)
internal interface SmartBookRecognizerModule {
    @Binds
    fun bindSmartBookRecognizer(adapter: SafSmartBookRecognizer): SmartBookRecognizer
}

private class SafBookRecognitionSource(
    private val contentResolver: ContentResolver,
) : BookRecognitionSource {

    override suspend fun discover(root: LibraryRoot, limits: RecognitionLimits): BookSourceScanResult =
        SafBookSourceScanner(
            contentResolver = contentResolver,
            maxVisitedEntries = limits.maxTraversalEntries,
            maxBookFiles = limits.maxCandidates,
            maxDepth = limits.maxDepth,
        ).scan(root.treeUri)

    override suspend fun probe(uri: Uri, maxSampleBytes: Int): RecognitionProbe = withContext(Dispatchers.IO) {
        var displayName = "未命名书籍"
        var sizeBytes: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex).orEmpty().trim().ifBlank { displayName }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                }
            }
        }
        // 超大文件把头部读取扩大到一个指纹块（256 KiB），一次流同时产出分类样本与指纹头块；
        // 读取量仍有明确上界（头尾各 256 KiB），并随协程取消而停止。
        val largeFile = SourceFingerprint.isLargeFile(sizeBytes)
        val headReadBytes = if (largeFile) SourceFingerprint.CHUNK_BYTES else maxSampleBytes + 1
        val sampled = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(headReadBytes)
            val buffer = ByteArray(8 * 1_024)
            var remaining = headReadBytes
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                if (read == 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        } ?: throw IllegalStateException("无法打开文件")
        val fingerprint = if (largeFile && sampled.size >= SourceFingerprint.CHUNK_BYTES) {
            computeTailFingerprint(uri, sizeBytes!!, sampled)
        } else {
            null
        }
        RecognitionProbe(
            uri = uri,
            displayName = displayName,
            mimeType = runCatching { contentResolver.getType(uri) }.getOrNull(),
            sizeBytes = sizeBytes,
            sample = if (sampled.size > maxSampleBytes) sampled.copyOf(maxSampleBytes) else sampled,
            sampleTruncated = sampled.size > maxSampleBytes,
            fingerprint = fingerprint,
        )
    }

    /**
     * 尾块指纹：重新开一条流，跳到 `size - CHUNK` 后读满一个块。provider 上报的大小
     * 与实际不符（读不满一块）时放弃指纹，绝不基于残缺数据生成同内容结论。
     */
    private suspend fun computeTailFingerprint(uri: Uri, sizeBytes: Long, headChunk: ByteArray): String? {
        val headMd5 = SourceFingerprint.md5Hex(headChunk)
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                var remaining = sizeBytes - SourceFingerprint.CHUNK_BYTES
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val skipped = input.skip(remaining)
                    if (skipped > 0) {
                        remaining -= skipped
                    } else if (input.read() < 0) {
                        return@use null
                    } else {
                        remaining -= 1
                    }
                }
                val tail = ByteArray(SourceFingerprint.CHUNK_BYTES)
                var offset = 0
                while (offset < tail.size) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(tail, offset, tail.size - offset)
                    if (read < 0) return@use null
                    offset += read
                }
                SourceFingerprint.fromChunks(sizeBytes, headMd5, SourceFingerprint.md5Hex(tail))
            }
        }.getOrNull()
    }
}
