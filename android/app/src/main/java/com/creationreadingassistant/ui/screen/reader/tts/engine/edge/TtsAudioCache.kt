package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

/**
 * TTS 音频本地 LRU 文件缓存。
 *
 * 容量策略：
 * - 总容量上限 [MAX_TOTAL_BYTES]（默认 128MB）；
 * - 单文件超过 8MB 不缓存（极长句，命中率低）；
 * - 写满时按文件最后修改时间从旧到新删除，直到腾出目标空间。
 * - 7 天前的旧文件在读/写时顺手清理。
 *
 * Key 规则：SHA-256(voiceId + "\u0001" + text + "\u0001" + rate + "\u0001" + pitch)
 * 这样相同文本/音色/语速/音调下，重复听书可直接命中本地 MP3，不重复联网。
 */
internal class TtsAudioCache(
    context: Context,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxFileBytes: Long = 8L * 1024 * 1024,
) {

    private val cacheDir: File = File(context.filesDir, "tts-cache").apply { mkdirs() }
    private val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000

    private val _currentBytes = AtomicLong(0)

    suspend fun currentSizeBytes(): Long = withContext(Dispatchers.IO) {
        recomputeSizeIfDirty()
        _currentBytes.get()
    }

    /** 计算缓存 key。 */
    fun keyOf(voiceId: String, text: String, rate: Float, pitch: Float): String {
        val raw = buildString {
            append(voiceId).append('\u0001')
            append(text).append('\u0001')
            append(rate).append('\u0001')
            append(pitch)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 返回命中的缓存文件；不存在返回 null。会顺手更新 mtime（LRU）。 */
    suspend fun get(key: String): File? = withContext(Dispatchers.IO) {
        val f = File(cacheDir, "$key.mp3")
        if (!f.exists() || !f.isFile) return@withContext null
        val now = System.currentTimeMillis()
        if (now - f.lastModified() > maxAgeMs) {
            runCatching { f.delete() }
            recomputeSizeIfDirty(force = true)
            return@withContext null
        }
        runCatching { f.setLastModified(now) }
        f
    }

    /**
     * 写入新缓存；若文件过大或超过容量上限则放弃写入并返回 null。
     * [writeBlock] 被调用时会传入目标文件，写入方负责 close 输出流。
     */
    suspend fun put(
        key: String,
        writeBlock: suspend (dst: File) -> Long,
    ): File? = withContext(Dispatchers.IO) {
        evictExpiredIfNeeded()
        val target = File(cacheDir, "$key.mp3.tmp")
        runCatching { target.delete() }
        try {
            val size = writeBlock(target)
            if (size <= 0L) return@withContext null
            if (size > maxFileBytes) {
                runCatching { target.delete() }
                return@withContext null
            }
            ensureCapacity(size)
            val final = File(cacheDir, "$key.mp3")
            if (!target.renameTo(final)) {
                target.copyTo(final, overwrite = true)
                runCatching { target.delete() }
            }
            _currentBytes.addAndGet(final.length())
            final
        } catch (t: Throwable) {
            runCatching { target.delete() }
            com.creationreadingassistant.feature.log.AppLog.debug(
                "EdgeTtsCache",
                "cache put failed: ${t.message ?: t::class.java.simpleName}",
            )
            null
        }
    }

    // ── 内部清理逻辑 ─────────────────────────────────────────

    private var dirty: Boolean = true

    private fun recomputeSizeIfDirty(force: Boolean = false) {
        if (!dirty && !force) return
        var total = 0L
        cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "mp3") total += f.length()
        }
        _currentBytes.set(total)
        dirty = false
    }

    private fun evictExpiredIfNeeded() {
        recomputeSizeIfDirty()
        val now = System.currentTimeMillis()
        var changed = false
        cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "mp3" && now - f.lastModified() > maxAgeMs) {
                runCatching { f.delete() }
                changed = true
            }
        }
        if (changed) recomputeSizeIfDirty(force = true)
    }

    private fun ensureCapacity(need: Long) {
        evictExpiredIfNeeded()
        var cur = _currentBytes.get()
        if (cur + need <= maxTotalBytes) return
        val files = cacheDir.listFiles()
            ?.filter { it.isFile && it.extension == "mp3" }
            ?.sortedBy { it.lastModified() }
            ?: LinkedList()
        val iter = files.iterator()
        while (cur + need > maxTotalBytes && iter.hasNext()) {
            val victim = iter.next()
            val len = victim.length()
            if (runCatching { victim.delete() }.getOrDefault(false)) {
                cur -= len
                _currentBytes.addAndGet(-len)
            }
        }
    }

    private companion object {
        private const val MAX_TOTAL_BYTES: Long = 128L * 1024 * 1024 // 128 MB
    }
}
