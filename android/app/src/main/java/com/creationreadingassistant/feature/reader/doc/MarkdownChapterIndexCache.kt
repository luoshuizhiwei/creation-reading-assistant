package com.creationreadingassistant.feature.reader.doc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One bounded snapshot of the last streamed Markdown index, keyed by source bytes. */
internal class MarkdownChapterIndexCache(
    cacheDir: File,
    source: File,
    private val encoding: String,
) {
    private val target = File(cacheDir, "markdown-chapters-v1.idx")
    private val digest = MessageDigest.getInstance("SHA-256").let { hash ->
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        hash.digest()
    }

    data class Chapter(
        val title: String,
        val sourceStart: Int,
        val sourceEnd: Int,
        val canonicalLength: Int,
    )

    fun read(totalChars: Long): List<Chapter>? {
        if (!target.isFile || target.length() > MAX_BYTES) return null
        return try {
            DataInputStream(BufferedInputStream(target.inputStream())).use { input ->
                if (input.readInt() != VERSION || input.readUTF() != encoding) return null
                val storedDigest = ByteArray(digest.size)
                input.readFully(storedDigest)
                if (!storedDigest.contentEquals(digest)) return null
                val count = input.readInt()
                if (count !in 1..MAX_CHAPTERS) return null
                val chapters = ArrayList<Chapter>(count)
                var nextStart = 0
                var canonicalTotal = 0L
                repeat(count) {
                    val start = input.readInt()
                    val end = input.readInt()
                    val length = input.readInt()
                    val title = input.readUTF()
                    if (start != nextStart || end < start || end.toLong() > totalChars || length < 0) return null
                    canonicalTotal += length
                    if (canonicalTotal > Int.MAX_VALUE) return null
                    chapters += Chapter(title, start, end, length)
                    nextStart = end
                }
                if (nextStart.toLong() != totalChars || input.read() != -1) null else chapters
            }
        } catch (_: IOException) {
            // The index is disposable. A truncated or unreadable record is a miss.
            null
        }
    }

    fun write(chapters: List<Chapter>) {
        if (chapters.size !in 1..MAX_CHAPTERS) return
        // writeUTF uses at most three bytes per UTF-16 unit.
        if (chapters.sumOf { 14L + 3L * it.title.length } + 64 > MAX_BYTES ||
            chapters.any { it.title.length > 16_000 }) return
        val pending = try {
            File.createTempFile("markdown-chapters-", ".tmp", target.parentFile)
        } catch (_: IOException) {
            return // Cache storage is optional.
        }
        try {
            DataOutputStream(BufferedOutputStream(pending.outputStream())).use { out ->
                out.writeInt(VERSION)
                out.writeUTF(encoding)
                out.write(digest)
                out.writeInt(chapters.size)
                for (chapter in chapters) {
                    out.writeInt(chapter.sourceStart)
                    out.writeInt(chapter.sourceEnd)
                    out.writeInt(chapter.canonicalLength)
                    out.writeUTF(chapter.title)
                }
            }
            pending.renameTo(target)
        } catch (_: IOException) {
            // Keep the previous index when writing fails.
        } finally {
            pending.delete()
        }
    }

    private companion object {
        const val VERSION = 1
        const val MAX_CHAPTERS = 50_000
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
