package com.creationreadingassistant.feature.dictionary

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import kotlin.math.min

/**
 * StarDict 词典（离线，R3-X1）。
 *
 * 支持的磁盘形态（同名同目录）：
 * - `<name>.ifo` 元数据（必需）
 * - `<name>.idx` 词条索引（必需）
 * - `<name>.dict` 词条数据，或 `<name>.dict.dz`（gzip 压缩的 .dict）
 *
 * 设计要点（内存有界）：
 * - `.idx` 以**原始字节**常驻（通常几 MB），另建 4 个定长数组做 O(1) 定位，
 *   按字节比较做二分查找 —— 不为几十万词条各建一个 String；
 * - `.dict` 用 [RandomAccessFile] **按需随机读**，绝不整文件载入；
 * - `.dict.dz` 不能随机访问，导入时一次性流式解压成 `.dict`（磁盘换内存），
 *   解压失败/无写权限时回退到缓存目录。
 *
 * 词条内容按 `sametypesequence` 解码；文本类类型（m/l/g/x/t/y/k/w/h/n）直接可读，
 * 二进制类（W/P/M 等）只给占位符，避免把音频/图片塞进 UI。
 */
internal data class StarDictInfo(
    val version: String,
    val bookName: String,
    val wordCount: Int,
    val indexFileSize: Long,
    /** 空串 = 数据块内联类型字节；否则按序给出各字段类型。 */
    val sameTypeSequence: String,
    /** 32 或 64（`.idx` 里偏移量的位宽）。 */
    val indexOffsetBits: Int,
    val description: String,
)

/** 一条词条释义。 */
internal data class StarDictEntry(
    val word: String,
    val content: String,
    /** 原始类型码（'m' 纯文本、'g' Pango、'x' XDXF…），便于 UI 提示来源。 */
    val typeCode: Char,
)

/** 单条释义的可读性上限：挡住畸形词库里的超大块，避免 UI 卡死。 */
private const val MAX_ENTRY_CHARS = 20_000
private const val MAX_ENTRY_BYTES = 1 shl 20

internal object StarDictFormat {

    const val CHARSET_UTF8 = "UTF-8"

    /** 文本类类型码（其余按二进制处理）。 */
    private val TEXT_TYPES = setOf('m', 'l', 'g', 'x', 't', 'y', 'k', 'w', 'h', 'n', 'r')

    fun isTextType(code: Char): Boolean = code in TEXT_TYPES

    /** 解析 `.ifo`；缺 bookname/wordcount/idxfilesize 一律视为非法词典。 */
    fun parseInfo(text: String): StarDictInfo? {
        val map = HashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            map[line.substring(0, eq).trim().lowercase()] = line.substring(eq + 1).trim()
        }
        val bookName = map["bookname"]?.takeIf { it.isNotBlank() } ?: return null
        val wordCount = map["wordcount"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val idxSize = map["idxfilesize"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val bits = map["idxoffsetbits"]?.toIntOrNull()?.takeIf { it == 64 } ?: 32
        return StarDictInfo(
            version = map["version"].orEmpty(),
            bookName = bookName,
            wordCount = wordCount,
            indexFileSize = idxSize,
            sameTypeSequence = map["sametypesequence"].orEmpty(),
            indexOffsetBits = bits,
            description = map["description"].orEmpty(),
        )
    }

    /** `.idx` 条目定长尾部字节数：offset(bits/8) + size(4)。 */
    fun entryTailBytes(indexOffsetBits: Int): Int = if (indexOffsetBits == 64) 12 else 8

    /**
     * 把 `.dict.dz`（gzip 流）流式解压成 `.dict`。
     * 用「临时文件 + 原子改名」避免中断留下半截 `.dict` 被当成有效词库。
     */
    fun decompressDictDz(dz: File, out: File): Boolean {
        if (!dz.isFile || dz.length() <= 0L) return false
        val tmp = File(out.parentFile, "${out.name}.part")
        return try {
            GZIPInputStream(dz.inputStream().buffered()).use { input ->
                tmp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            out.delete()
            tmp.renameTo(out)
        } catch (_: Throwable) {
            tmp.delete()
            false
        }
    }

    /** 去标记 + 反转义，把 Pango/XDXF/HTML 类内容压成可读纯文本。 */
    fun stripMarkup(raw: String): String {
        var s = raw
        if ('<' in s) s = s.replace(Regex("<[^>]{0,400}>"), " ")
        s = s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        // 连续空白压一行，避免 XDXF 换行把释义撑成几十屏
        s = s.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ").replace(Regex("\\n{3,}"), "\n\n")
        return s.trim().take(MAX_ENTRY_CHARS)
    }

    /**
     * 按 `sametypesequence` / 内联类型字节解码一个数据块。
     * 返回 (文本, 首个类型码)；无法解码时返回空文本。
     */
    fun decodeEntry(block: ByteArray, sameTypeSequence: String): Pair<String, Char> {
        if (block.isEmpty()) return "" to 'm'
        if (sameTypeSequence.isEmpty()) return decodeInlineTyped(block)
        var cursor = 0
        var firstCode: Char? = null
        val out = StringBuilder()
        sameTypeSequence.forEachIndexed { index, code ->
            val isLast = index == sameTypeSequence.lastIndex
            var dataStart = cursor
            // 规则：最后一位若为小写字母则该字段不再带类型字节；否则每个字段前置 1 字节类型码
            val hasTypeByte = !(isLast && code.isLowerCase())
            var actualCode = code
            if (hasTypeByte) {
                if (cursor >= block.size) return@forEachIndexed
                actualCode = block[cursor].toInt().toChar()
                dataStart = cursor + 1
            }
            val end = if (!isLast) {
                // 非末位字段只能是「小写 + \0 结尾」形式（大小写混排的二进制字段极少且无法定界）
                val zero = block.indexOf(0, dataStart)
                if (zero < 0) block.size else zero
            } else {
                block.size
            }
            if (firstCode == null) firstCode = actualCode
            appendField(out, actualCode, block, dataStart, end)
            cursor = if (end < block.size && block[end].toInt() == 0) end + 1 else end
        }
        return out.toString() to (firstCode ?: 'm')
    }

    private fun decodeInlineTyped(block: ByteArray): Pair<String, Char> {
        var cursor = 0
        var firstCode: Char? = null
        val out = StringBuilder()
        while (cursor < block.size) {
            val code = block[cursor].toInt().toChar()
            cursor += 1
            if (firstCode == null) firstCode = code
            val end = if (code.isLowerCase()) {
                val zero = block.indexOf(0, cursor)
                if (zero < 0) block.size else zero
            } else {
                block.size
            }
            appendField(out, code, block, cursor, end)
            cursor = if (end < block.size && block[end].toInt() == 0) end + 1 else end
        }
        return out.toString() to (firstCode ?: 'm')
    }

    private fun appendField(out: StringBuilder, code: Char, block: ByteArray, from: Int, to: Int) {
        if (from >= to) return
        if (!isTextType(code)) {
            if (out.isNotEmpty()) out.append('\n')
            out.append(binaryPlaceholder(code))
            return
        }
        val text = String(block, from, to - from, StandardCharsets.UTF_8)
        val cleaned = if (code == 'm') text.trim() else stripMarkup(text)
        if (cleaned.isEmpty()) return
        if (out.isNotEmpty()) out.append('\n')
        out.append(cleaned)
    }

    private fun binaryPlaceholder(code: Char): String = when (code) {
        'W', 'P' -> "[音频]"
        'M' -> "[图片]"
        'X' -> "[二进制数据]"
        else -> "[非文本内容]"
    }

    private fun ByteArray.indexOf(target: Int, from: Int): Int {
        for (i in from until size) if (this[i].toInt() and 0xFF == target) return i
        return -1
    }
}

/**
 * `.idx` 的内存索引：原始字节 + 定长定位数组。
 *
 * 词条按字节序升序排列（StarDict 规范要求 `strcmp` 序），因此可二分；
 * 比较直接对 UTF-8 字节做逐字节比较，与 `strcmp` 语义一致，且**不产生临时字符串**。
 */
internal class StarDictIndex(
    private val raw: ByteArray,
    private val wordStarts: IntArray,
    private val wordEnds: IntArray,
    private val dataOffsets: LongArray,
    private val dataSizes: IntArray,
) {
    val size: Int get() = wordStarts.size

    fun wordAt(i: Int): String = String(raw, wordStarts[i], wordEnds[i] - wordStarts[i], StandardCharsets.UTF_8)

    fun dataOffsetAt(i: Int): Long = dataOffsets[i]

    fun dataSizeAt(i: Int): Int = dataSizes[i]

    /** 精确（字节序）查找，返回命中区间；未命中返回 null。 */
    fun findExact(target: ByteArray): IntRange? {
        var lo = 0
        var hi = size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = compareAt(mid, target)
            when {
                cmp == 0 -> {
                    found = mid
                    hi = mid - 1
                }

                cmp < 0 -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        if (found < 0) return null
        var end = found
        while (end + 1 < size && compareAt(end + 1, target) == 0) end++
        return found..end
    }

    private fun compareAt(index: Int, target: ByteArray): Int {
        val start = wordStarts[index]
        val length = wordEnds[index] - start
        val n = min(length, target.size)
        for (k in 0 until n) {
            val a = raw[start + k].toInt() and 0xFF
            val b = target[k].toInt() and 0xFF
            if (a != b) return a - b
        }
        return length - target.size
    }

    companion object {
        /** 从原始 `.idx` 字节构建索引；结构损坏（尾部残缺）时丢弃最后一个不完整条目。 */
        fun parse(bytes: ByteArray, info: StarDictInfo): StarDictIndex? {
            val tail = StarDictFormat.entryTailBytes(info.indexOffsetBits)
            val starts = ArrayList<Int>(info.wordCount)
            val ends = ArrayList<Int>(info.wordCount)
            val offsets = ArrayList<Long>(info.wordCount)
            val sizes = ArrayList<Int>(info.wordCount)
            var cursor = 0
            while (cursor < bytes.size) {
                val zero = indexOfZero(bytes, cursor)
                if (zero < 0 || zero + tail > bytes.size) break
                starts.add(cursor)
                ends.add(zero)
                var p = zero + 1
                val dataOffset = if (info.indexOffsetBits == 64) {
                    readLongBe(bytes, p).also { p += 8 }
                } else {
                    readIntBe(bytes, p).toLong().also { p += 4 }
                }
                val dataSize = readIntBe(bytes, p)
                if (dataSize < 0) break
                offsets.add(dataOffset)
                sizes.add(dataSize)
                cursor = zero + 1 + tail
            }
            if (starts.isEmpty()) return null
            return StarDictIndex(
                raw = bytes,
                wordStarts = starts.toIntArray(),
                wordEnds = ends.toIntArray(),
                dataOffsets = offsets.toLongArray(),
                dataSizes = sizes.toIntArray(),
            )
        }

        private fun indexOfZero(bytes: ByteArray, from: Int): Int {
            for (i in from until bytes.size) if (bytes[i].toInt() == 0) return i
            return -1
        }

        private fun readIntBe(bytes: ByteArray, at: Int): Int {
            if (at + 4 > bytes.size) return -1
            return ((bytes[at].toInt() and 0xFF) shl 24) or
                ((bytes[at + 1].toInt() and 0xFF) shl 16) or
                ((bytes[at + 2].toInt() and 0xFF) shl 8) or
                (bytes[at + 3].toInt() and 0xFF)
        }

        private fun readLongBe(bytes: ByteArray, at: Int): Long {
            var v = 0L
            for (i in 0 until 8) {
                if (at + i >= bytes.size) return 0L
                v = (v shl 8) or (bytes[at + i].toLong() and 0xFF)
            }
            return v
        }
    }
}

/**
 * 已打开的 StarDict 词典。
 *
 * 用法：`StarDictDictionary.open(dir, baseName)?.use { it.lookup("词") }`。
 */
internal class StarDictDictionary private constructor(
    val info: StarDictInfo,
    private val index: StarDictIndex,
    private val dict: RandomAccessFile,
) : Closeable {

    /** 精确命中（含同形词）；查不到返回空表。 */
    fun lookup(word: String, limit: Int = 8): List<StarDictEntry> {
        val key = word.trim()
        if (key.isEmpty()) return emptyList()
        val range = index.findExact(key.toByteArray(StandardCharsets.UTF_8))
            ?: index.findExact(key.lowercase().toByteArray(StandardCharsets.UTF_8))
            ?: return emptyList()
        val out = ArrayList<StarDictEntry>(min(limit, range.count()))
        for (i in range) {
            if (out.size >= limit) break
            val block = readBlock(i) ?: continue
            val (text, code) = StarDictFormat.decodeEntry(block, info.sameTypeSequence)
            if (text.isBlank()) continue
            out.add(StarDictEntry(word = index.wordAt(i), content = text, typeCode = code))
        }
        return out
    }

    private fun readBlock(i: Int): ByteArray? {
        val size = index.dataSizeAt(i)
        if (size <= 0 || size > MAX_ENTRY_BYTES) return null
        val offset = index.dataOffsetAt(i)
        return try {
            val bytes = ByteArray(size)
            dict.seek(offset)
            dict.readFully(bytes)
            bytes
        } catch (_: Throwable) {
            null
        }
    }

    override fun close() {
        runCatching { dict.close() }
    }

    companion object {
        /**
         * 打开 `<dir>/<baseName>.{ifo,idx,dict}`。
         * 只有 `.dict.dz` 时会尝试解压（优先原地，失败则写入 [fallbackCacheDir]）。
         */
        fun open(dir: File, baseName: String, fallbackCacheDir: File? = null): StarDictDictionary? {
            val ifo = File(dir, "$baseName.ifo")
            val idx = File(dir, "$baseName.idx")
            var dictFile = File(dir, "$baseName.dict")
            if (!ifo.isFile || !idx.isFile) return null
            if (!dictFile.isFile) {
                val dz = File(dir, "$baseName.dict.dz")
                if (!dz.isFile) return null
                if (!StarDictFormat.decompressDictDz(dz, dictFile) && fallbackCacheDir != null) {
                    fallbackCacheDir.mkdirs()
                    val cached = File(fallbackCacheDir, "$baseName.dict")
                    if (StarDictFormat.decompressDictDz(dz, cached)) dictFile = cached else return null
                }
                if (!dictFile.isFile) return null
            }
            val info = runCatching { StarDictFormat.parseInfo(ifo.readText()) }.getOrNull() ?: return null
            val indexBytes = runCatching { idx.readBytes() }.getOrNull() ?: return null
            val index = StarDictIndex.parse(indexBytes, info) ?: return null
            val raf = runCatching { RandomAccessFile(dictFile, "r") }.getOrNull() ?: return null
            return StarDictDictionary(info, index, raf)
        }
    }
}
