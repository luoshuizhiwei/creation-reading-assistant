package com.creationreadingassistant.feature.sync.legado

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Legado backup*.zip 白名单解析器（契约 §7/§8）。
 *
 * 防护语义：
 *  - 只消费四个大小写精确的根目录白名单文件；重名整包拒绝；大小写或子目录
 *    「冒充」白名单的条目（含目录条目）整包拒绝；绝对路径/盘符/反斜杠/空组件/
 *    点/上级路径整包拒绝。
 *  - 其余合法条目只受预算约束地排空计量，不提取到文件系统。
 *  - 流式计量：条目数、逐文件展开字节、全部展开字节都按实际读到的字节计数，
 *    不依赖 ZIP 声明值（压缩炸弹在此被拒）。
 *  - 必须读完整归档（走到归档末端）才返回成功，供提交前置门使用。
 *  - 行级无效（缺必需字段/类型/范围错误）计数并显式报告，绝不静默当有效数据；
 *    结构损坏/超限/无白名单条目 → 整包拒绝（[RejectedException]）。
 *  - 严格类型：字符串字段必须为 JSON 字符串，数字字段必须为 JSON 数字；
 *    `words` 缺失保留为 null，不默填 0（契约附录 A）。
 */
object LegadoBackupParser {

    // ---- 白名单与预算（v2 §7 首版防护预算，样本超限时带测量另议） ----
    const val FILE_BOOKSHELF = "bookshelf.json"
    const val FILE_SESSION = "readRecordSession.json"
    const val FILE_DAY = "readRecordDetail.json"
    const val FILE_BOOK = "readRecord.json"
    val WHITELIST = listOf(FILE_BOOKSHELF, FILE_SESSION, FILE_DAY, FILE_BOOK)

    const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 256
    const val MAX_TOTAL_EXPANDED_BYTES = 192L * 1024 * 1024
    val MAX_FILE_BYTES = mapOf(
        FILE_BOOKSHELF to 16L * 1024 * 1024,
        FILE_SESSION to 64L * 1024 * 1024,
        FILE_DAY to 32L * 1024 * 1024,
        FILE_BOOK to 16L * 1024 * 1024,
    )
    val MAX_ARRAY_ELEMENTS = mapOf(
        FILE_BOOKSHELF to 50_000L,
        FILE_SESSION to 500_000L,
        FILE_DAY to 250_000L,
        FILE_BOOK to 100_000L,
    )

    // ---- 解析产物 ----

    data class RawBookshelfEntry(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val durChapterIndex: Int,
        val durChapterPos: Int,
        val durChapterTime: Long?,
        val durChapterTitle: String?,
        val totalChapterNum: Int?,
    )

    data class RawSession(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String,
        val bookUrl: String,
        val startTime: Long,
        val endTime: Long,
        val words: Long?,
    )

    data class RawDayRecord(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String,
        val date: String,
        val readTime: Long,
        val readWords: Long,
        val firstReadTime: Long,
        val lastReadTime: Long,
    )

    data class RawBookRecord(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String,
        val readTime: Long,
        val lastRead: Long,
    )

    data class RowErrors(
        val bookshelf: Int,
        val sessions: Int,
        val days: Int,
        val books: Int,
    ) {
        val total: Int get() = bookshelf + sessions + days + books
        val any: Boolean get() = total > 0
    }

    data class FileStats(val present: Boolean, val expandedBytes: Long)

    data class ParsedArchive(
        val archiveSha256: String,
        val bookshelf: List<RawBookshelfEntry>,
        val sessions: List<RawSession>,
        val days: List<RawDayRecord>,
        val books: List<RawBookRecord>,
        val rowErrors: RowErrors,
        val fileStats: Map<String, FileStats>,
        val otherEntries: Int,
        val otherExpandedBytes: Long,
    ) {
        val presentFiles: Set<String> get() = fileStats.filterValues { it.present }.keys
        val historyRows: Int get() = sessions.size + days.size + books.size
    }

    /** 结构性拒绝（整包不接受）；消息为面向用户的原因。 */
    class RejectedException(message: String) : IllegalArgumentException(message)

    // ---- 入口 ----

    fun parse(zipBytes: ByteArray): ParsedArchive {
        if (zipBytes.size > MAX_ARCHIVE_BYTES) {
            throw RejectedException("备份超过 ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MiB，已拒绝读取")
        }
        val archiveSha256 = LegadoFingerprint.sha256Hex(zipBytes)

        val found = LinkedHashMap<String, ByteArray>()
        var entries = 0
        var otherEntries = 0
        var otherExpanded = 0L

        ZipInputStream(ByteArrayInputStream(zipBytes).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw RejectedException("备份条目超过 $MAX_ARCHIVE_ENTRIES 个，已拒绝读取")
                }
                checkCanonicalPath(entry.name)
                val canonical = entry.name.replace('\\', '/').trim('/')
                val isDirectory = entry.isDirectory || entry.name.endsWith('/')
                val basename = canonical.substringAfterLast('/').lowercase()
                val impersonating = !WHITELIST.contains(canonical) &&
                    WHITELIST.any { it == basename || it.lowercase() == basename }
                if (impersonating) {
                    throw RejectedException("备份内出现大小写或子目录冒充白名单文件的条目，整包拒绝")
                }
                if (isDirectory) continue

                if (canonical in WHITELIST) {
                    if (found.containsKey(canonical)) {
                        throw RejectedException("白名单文件出现重名条目：$canonical，整包拒绝")
                    }
                    val cap = MAX_FILE_BYTES.getValue(canonical)
                    val bytes = readBounded(zip, cap, canonical)
                    found[canonical] = bytes
                } else {
                    otherEntries++
                    otherExpanded += drain(zip)
                }
            }
        }

        val whitelistBytes = found.values.sumOf { it.size.toLong() }
        if (whitelistBytes + otherExpanded > MAX_TOTAL_EXPANDED_BYTES) {
            throw RejectedException("备份全部条目展开超过 ${MAX_TOTAL_EXPANDED_BYTES / (1024 * 1024)} MiB，已拒绝读取")
        }
        if (found.isEmpty()) {
            throw RejectedException("不是可识别的开源阅读备份：缺少白名单文件（如 $FILE_BOOKSHELF）")
        }

        var badBookshelf = 0
        var badSessions = 0
        var badDays = 0
        var badBooks = 0

        val bookshelf = found[FILE_BOOKSHELF]?.let {
            val (rows, bad) = parseArray(it, FILE_BOOKSHELF, ::parseBookshelfEntry)
            badBookshelf = bad
            rows
        } ?: emptyList()
        val sessions = found[FILE_SESSION]?.let {
            val (rows, bad) = parseArray(it, FILE_SESSION, ::parseSession)
            badSessions = bad
            rows
        } ?: emptyList()
        val days = found[FILE_DAY]?.let {
            val (rows, bad) = parseArray(it, FILE_DAY, ::parseDay)
            badDays = bad
            rows
        } ?: emptyList()
        val books = found[FILE_BOOK]?.let {
            val (rows, bad) = parseArray(it, FILE_BOOK, ::parseBookRecord)
            badBooks = bad
            rows
        } ?: emptyList()

        return ParsedArchive(
            archiveSha256 = archiveSha256,
            bookshelf = bookshelf,
            sessions = sessions,
            days = days,
            books = books,
            rowErrors = RowErrors(badBookshelf, badSessions, badDays, badBooks),
            fileStats = WHITELIST.associateWith { file ->
                FileStats(present = found.containsKey(file), expandedBytes = found[file]?.size?.toLong() ?: 0L)
            },
            otherEntries = otherEntries,
            otherExpandedBytes = otherExpanded,
        )
    }

    // ---- 行解析（严格 schema：必需字段缺失/类型/范围错误 → 行无效并计数） ----

    private fun parseBookshelfEntry(o: JsonObject): RawBookshelfEntry? {
        val name = o.strictString("name")?.trim().orEmpty()
        val chapterIndex = o.strictInt("durChapterIndex")
        val chapterPos = o.strictInt("durChapterPos")
        if (name.isBlank() || chapterIndex == null || chapterPos == null) return null
        if (chapterIndex < 0 || chapterPos < 0) return null
        return RawBookshelfEntry(
            name = name,
            author = o.strictString("author")?.trim().orEmpty(),
            bookUrl = o.strictString("bookUrl")?.trim().orEmpty(),
            origin = o.strictString("origin")?.takeIf { it.isNotBlank() },
            durChapterIndex = chapterIndex,
            durChapterPos = chapterPos,
            durChapterTime = o.strictLong("durChapterTime")?.takeIf { it >= 0 },
            durChapterTitle = o.strictString("durChapterTitle")?.takeIf { it.isNotBlank() },
            totalChapterNum = o.strictInt("totalChapterNum")?.takeIf { it > 0 },
        )
    }

    private fun parseSession(o: JsonObject): RawSession? {
        val name = o.strictString("bookName")?.trim().orEmpty()
        val start = o.strictLong("startTime")
        val end = o.strictLong("endTime")
        if (name.isBlank() || start == null || end == null) return null
        // 契约 §5：有效会话必须有可表示起止且 end > start；words 缺失不默填 0。
        if (start < 0 || end <= start) return null
        return RawSession(
            deviceId = o.strictString("deviceId")?.trim().orEmpty(),
            bookName = name,
            bookAuthor = o.strictString("bookAuthor")?.trim().orEmpty(),
            bookUrl = o.strictString("bookUrl")?.trim().orEmpty(),
            startTime = start,
            endTime = end,
            words = o.strictLong("words")?.takeIf { it >= 0 },
        )
    }

    private fun parseDay(o: JsonObject): RawDayRecord? {
        val name = o.strictString("bookName")?.trim().orEmpty()
        val date = o.strictString("date")?.trim().orEmpty()
        val readTime = o.strictLong("readTime")
        val readWords = o.strictLong("readWords")
        val first = o.strictLong("firstReadTime")
        val last = o.strictLong("lastReadTime")
        if (name.isBlank() || date.isBlank()) return null
        if (readTime == null || readWords == null || first == null || last == null) return null
        if (readTime < 0 || readWords < 0 || first < 0 || last < 0) return null
        return RawDayRecord(
            deviceId = o.strictString("deviceId")?.trim().orEmpty(),
            bookName = name,
            bookAuthor = o.strictString("bookAuthor")?.trim().orEmpty(),
            date = date,
            readTime = readTime,
            readWords = readWords,
            firstReadTime = first,
            lastReadTime = last,
        )
    }

    private fun parseBookRecord(o: JsonObject): RawBookRecord? {
        val name = o.strictString("bookName")?.trim().orEmpty()
        val readTime = o.strictLong("readTime")
        val lastRead = o.strictLong("lastRead")
        if (name.isBlank() || readTime == null || lastRead == null) return null
        if (readTime < 0 || lastRead < 0) return null
        return RawBookRecord(
            deviceId = o.strictString("deviceId")?.trim().orEmpty(),
            bookName = name,
            bookAuthor = o.strictString("bookAuthor")?.trim().orEmpty(),
            readTime = readTime,
            lastRead = lastRead,
        )
    }

    // ---- 通用工具 ----

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <T> parseArray(
        bytes: ByteArray,
        file: String,
        row: (JsonObject) -> T?,
    ): Pair<List<T>, Int> {
        val element = runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw RejectedException("$file 不是有效 JSON") }
        val array = element as? JsonArray ?: throw RejectedException("$file 顶层不是 JSON 数组")
        if (array.size.toLong() > MAX_ARRAY_ELEMENTS.getValue(file)) {
            throw RejectedException("$file 数组元素超过 ${MAX_ARRAY_ELEMENTS.getValue(file)} 上限")
        }
        val valid = ArrayList<T>(array.size)
        var bad = 0
        array.forEach { item ->
            val parsed = (item as? JsonObject)?.let(row)
            if (parsed == null) bad++ else valid += parsed
        }
        return valid to bad
    }

    /** 非规范路径整包拒绝：绝对/盘符/反斜杠/空组件/点/上级。 */
    private fun checkCanonicalPath(rawName: String) {
        val unix = rawName.replace('\\', '/')
        if (unix != rawName) throw RejectedException("备份条目路径含反斜杠，不可信归档，整包拒绝")
        if (unix.startsWith('/') || unix.contains(':')) {
            throw RejectedException("备份条目路径为绝对路径或含盘符，整包拒绝")
        }
        val parts = unix.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw RejectedException("备份条目路径含空组件或点/上级段，整包拒绝")
        }
    }

    private fun readBounded(zip: ZipInputStream, cap: Long, file: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > cap) {
                throw RejectedException("$file 展开超过 ${cap / (1024 * 1024)} MiB 上限，整包拒绝")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun drain(zip: ZipInputStream): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            total += n
        }
        return total
    }

    // ---- 严格类型取值 ----

    private fun JsonObject.strictString(name: String): String? {
        val p = this[name] as? JsonPrimitive ?: return null
        if (!p.isString) return null // 数字/布尔出现于字符串字段 = 类型错误 → 行无效
        return p.contentOrNull
    }

    private fun JsonObject.strictLong(name: String): Long? {
        val p = this[name] as? JsonPrimitive ?: return null
        if (p.isString) return null
        return p.longOrNull
    }

    private fun JsonObject.strictInt(name: String): Int? {
        val p = this[name] as? JsonPrimitive ?: return null
        if (p.isString) return null
        return p.intOrNull
    }
}
