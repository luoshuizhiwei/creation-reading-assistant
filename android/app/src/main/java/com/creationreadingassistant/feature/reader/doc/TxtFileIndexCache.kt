package com.creationreadingassistant.feature.reader.doc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * 大型 TXT 索引的磁盘快照。缓存键包含规范路径，文件长度和修改时间写入文件头校验；
 * 源文件变化或缓存损坏时直接重建，不参与 Locator 或正文语义。
 */
class TxtFileIndexCache(private val cacheDir: File) {
    private val indexDir = File(cacheDir, "txt_index_v1")

    fun getOrBuild(source: File, ruleId: String = "builtin"): TxtFileIndex {
        val target = cacheFile(source, ruleId)
        read(target, source, ruleId)?.let { return it }
        val index = TxtFileScanner.scan(source, ruleId)
        write(target, source, ruleId, index)
        return index
    }

    private fun cacheFile(source: File, ruleId: String): File {
        indexDir.mkdirs()
        val identity = "${source.canonicalPath}\u0000$ruleId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(indexDir, "$digest.idx")
    }

    private fun read(target: File, source: File, ruleId: String): TxtFileIndex? {
        if (!target.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(target.inputStream())).use { input ->
                require(input.readInt() == MAGIC)
                require(input.readInt() == VERSION)
                require(input.readUTF() == source.canonicalPath)
                require(input.readLong() == source.length())
                require(input.readLong() == source.lastModified())
                require(input.readUTF() == ruleId)
                val encoding = input.readUTF()
                val detectedRule = input.readNullableUtf()
                val totalChars = input.readLong()
                val chapterCount = input.readInt().also { require(it in 0..MAX_ITEMS) }
                val chapters = ArrayList<ChapterEntry>(chapterCount)
                repeat(chapterCount) {
                    chapters += ChapterEntry(
                        index = input.readInt(),
                        title = input.readUTF(),
                        charStart = input.readLong(),
                        byteStart = input.readLong(),
                        byteLength = input.readInt(),
                        charCount = input.readLong(),
                    )
                }
                val checkpointCount = input.readInt().also { require(it in 0..MAX_ITEMS) }
                val checkpoints = ArrayList<CharByteCheckpoint>(checkpointCount)
                repeat(checkpointCount) {
                    checkpoints += CharByteCheckpoint(
                        charOffset = input.readLong(),
                        byteOffset = input.readLong(),
                    )
                }
                TxtFileIndex(
                    chapters = chapters,
                    totalCharCount = totalChars,
                    encoding = encoding,
                    detectedRuleId = detectedRule,
                    checkpoints = checkpoints,
                )
            }
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun write(
        target: File,
        source: File,
        ruleId: String,
        index: TxtFileIndex,
    ) {
        runCatching {
            val pending = File(target.parentFile, "${target.name}.tmp")
            DataOutputStream(BufferedOutputStream(pending.outputStream())).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(source.canonicalPath)
                output.writeLong(source.length())
                output.writeLong(source.lastModified())
                output.writeUTF(ruleId)
                output.writeUTF(index.encoding)
                output.writeNullableUtf(index.detectedRuleId)
                output.writeLong(index.totalCharCount)
                output.writeInt(index.chapters.size)
                index.chapters.forEach { chapter ->
                    output.writeInt(chapter.index)
                    output.writeUTF(chapter.title.take(MAX_UTF_CHARS))
                    output.writeLong(chapter.charStart)
                    output.writeLong(chapter.byteStart)
                    output.writeInt(chapter.byteLength)
                    output.writeLong(chapter.charCount)
                }
                output.writeInt(index.checkpoints.size)
                index.checkpoints.forEach { checkpoint ->
                    output.writeLong(checkpoint.charOffset)
                    output.writeLong(checkpoint.byteOffset)
                }
            }
            if (!pending.renameTo(target)) {
                target.delete()
                pending.renameTo(target)
            }
        }
    }

    private fun DataInputStream.readNullableUtf(): String? =
        if (readBoolean()) readUTF() else null

    private fun DataOutputStream.writeNullableUtf(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private companion object {
        const val MAGIC = 0x43524149 // CRAI
        const val VERSION = 1
        const val MAX_ITEMS = 1_000_000
        const val MAX_UTF_CHARS = 16_000
    }
}
