package com.creationreadingassistant.feature.sync

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.mergeReaderProgress
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仅导入 HapeLee/legado-with-MD3 WebDAV 备份中的阅读位置。
 *
 * 上游根目录 backup*.zip 内的 bookshelf.json 是 Book 列表，记录了 name/author 和
 * durChapterIndex、durChapterPos、durChapterTime、durChapterTitle。这里刻意不读取
 * 同包的书源、配置、替换规则、书架分组或正文文件；只在本地已有且唯一匹配的书上
 * 写入 progress 表。
 *
 * 支持的格式只有 EPUB 与纯文本：Markdown 的章节坐标无法与开源阅读对齐，导入时按
 * 「暂不支持」计入 [Report.unsupported]，不会被静默写成打不开的位置。
 */
@Singleton
class LegadoReadingProgressImporter @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
) {
    data class Report(
        val scanned: Int,
        val imported: Int,
        val unchanged: Int,
        val unmatched: Int,
        val ambiguous: Int,
        val unsupported: Int,
        val invalid: Int,
    ) {
        val skipped: Int get() = unchanged + unmatched + ambiguous + unsupported + invalid

        fun summary(): String = buildString {
            append("已导入 ").append(imported).append(" 条开源阅读记录")
            if (skipped > 0) {
                append("；跳过 ").append(skipped).append(" 条")
                val details = buildList {
                    if (unmatched > 0) add("未匹配 $unmatched")
                    if (ambiguous > 0) add("歧义 $ambiguous")
                    if (unsupported > 0) add("暂不支持导入 $unsupported")
                    if (unchanged > 0) add("本地位置更靠后 $unchanged")
                    if (invalid > 0) add("格式无效 $invalid")
                }
                if (details.isNotEmpty()) append("（").append(details.joinToString("、")).append('）')
            }
        }
    }

    data class LegadoProgress(
        val name: String,
        val author: String,
        val chapterIndex: Int,
        val charOffset: Int,
        val chapterTimeMillis: Long,
        val chapterTitle: String?,
        val totalChapters: Int?,
    )

    /** 解析并原子写入一份 Legado backup*.zip；ZIP 内只读取 bookshelf.json。 */
    suspend fun importArchive(zipBytes: ByteArray): Report {
        val parsed = parseArchive(zipBytes)
        val localByKey = bookDao.getAllActive()
            .groupBy { BookKey.of(it.title, it.author) }
        val progressBySourceKey = parsed.valid
            .groupBy { BookKey.of(it.name, it.author) }
            .mapValues { (_, values) -> values.maxWithOrNull(REMOTE_ORDER)!! }

        var unmatched = 0
        var ambiguous = 0
        var unsupported = 0
        val matches = mutableListOf<Pair<BookEntity, LegadoProgress>>()
        progressBySourceKey.forEach { (key, remote) ->
            val candidates = localByKey[key].orEmpty()
            when {
                candidates.isEmpty() -> unmatched++
                // Markdown 的章号来自 H1/H2、偏移是规范阅读文本空间，与开源阅读自己的
                // 目录规则没有等价证明。宁可如实跳过并计数，也不写入一个打开时无法换算
                // （或换算到错误位置）的位置。歧义判定保持原语义，不因过滤而放宽。
                candidates.none(::supportsImportedPosition) -> unsupported++
                candidates.size == 1 -> matches += candidates.single() to remote
                else -> ambiguous++
            }
        }

        var imported = 0
        var unchanged = 0
        val now = Instant.now()
        bookDao.runInTransaction {
            // 读取现有进度必须与判断、写入处于同一事务；否则阅读器并发自动保存时，
            // 可能在导入开始与实际 upsert 之间写入更靠后的本地位置。
            val existingByBook = if (matches.isEmpty()) {
                emptyMap()
            } else {
                progressDao.getByBooks(matches.map { it.first.id }).associateBy { it.book_id }
            }
            matches.forEach { (book, remote) ->
                val existing = existingByBook[book.id]
                if (!shouldApply(book, existing, remote)) {
                    unchanged++
                    return@forEach
                }
                val incoming = ReadingProgressEntity(
                    book_id = book.id,
                    progress_percent = remote.approximatePercent(),
                    last_read_at = remote.chapterTimeMillis
                        .takeIf { it > 0L }
                        ?.let { Instant.ofEpochMilli(it).toString() }
                        ?: now.toString(),
                    completion_state = existing?.completion_state ?: ReadingCompletionState.READING.storageValue,
                    current_location_json = locationFor(book, remote),
                    updated_at = now.toString(),
                )
                val merged = mergeReaderProgress(
                    existing = existing,
                    incoming = incoming,
                    nowIso = now.toString(),
                    nowMillis = now.toEpochMilli(),
                )
                // 导入边界防线：外部导入位置与估算百分比绝不能推导完成态或生成完成时间。
                // 用户未明确标记完成的书籍必须保持既有状态（READING / SHELVED），绝不擅自变更为 FINISHED。
                val safeProgress = if (existing?.readingState != ReadingCompletionState.FINISHED &&
                    merged.readingState == ReadingCompletionState.FINISHED
                ) {
                    merged.copy(
                        completion_state = existing?.completion_state ?: ReadingCompletionState.READING.storageValue,
                        completed_at = null,
                    )
                } else {
                    merged
                }
                progressDao.upsert(safeProgress)
                imported++
            }
        }

        return Report(
            scanned = parsed.valid.size + parsed.invalid,
            imported = imported,
            unchanged = unchanged,
            unmatched = unmatched,
            ambiguous = ambiguous,
            unsupported = unsupported,
            invalid = parsed.invalid,
        )
    }

    /**
     * HapeLee 分支的 Backup.backup() 写入的 bookshelf.json 是 JSON 数组。
     * 只接受本功能所需的字段；未知字段一律忽略，避免把上游的书源 URL 或注释
     * 带入本地业务数据。
     */
    internal fun parseArchive(zipBytes: ByteArray): ParseResult {
        require(zipBytes.size <= MAX_ARCHIVE_BYTES) { "开源阅读备份超过 64 MiB，已拒绝读取" }
        val bookshelf = ByteArrayInputStream(zipBytes).use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entries = 0
                var entry = zip.nextEntry
                while (entry != null) {
                    entries++
                    require(entries <= MAX_ZIP_ENTRIES) { "备份文件条目过多，已拒绝读取" }
                    if (!entry.isDirectory && entry.name.replace('\\', '/') == BOOKSHELF_ENTRY) {
                        return@use readAtMost(zip, MAX_BOOKSHELF_BYTES)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                null
            }
        } ?: throw IllegalArgumentException("不是开源阅读备份：缺少 $BOOKSHELF_ENTRY")

        val array = runCatching {
            json.parseToJsonElement(bookshelf.toString(Charsets.UTF_8)) as? JsonArray
        }.getOrNull() ?: throw IllegalArgumentException("$BOOKSHELF_ENTRY 不是有效的书架列表")

        val valid = mutableListOf<LegadoProgress>()
        var invalid = 0
        array.forEach { element ->
            val value = element as? JsonObject
            val parsed = value?.let(::parseProgress)
            if (parsed == null) invalid++ else valid += parsed
        }
        return ParseResult(valid, invalid)
    }

    internal data class ParseResult(val valid: List<LegadoProgress>, val invalid: Int)

    private fun parseProgress(value: JsonObject): LegadoProgress? {
        val name = value.string("name")?.trim().orEmpty()
        val author = value.string("author")?.trim().orEmpty()
        val chapterIndex = value.int("durChapterIndex") ?: return null
        val charOffset = value.int("durChapterPos") ?: return null
        if (name.isBlank() || chapterIndex < 0 || charOffset < 0) return null
        return LegadoProgress(
            name = name,
            author = author,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            chapterTimeMillis = value.long("durChapterTime")?.coerceAtLeast(0L) ?: 0L,
            chapterTitle = value.string("durChapterTitle")?.takeIf { it.isNotBlank() },
            totalChapters = value.int("totalChapterNum")?.takeIf { it > 0 },
        )
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    private fun LegadoProgress.approximatePercent(): Float = totalChapters
        ?.takeIf { it > 0 }
        ?.let { (chapterIndex.toFloat() / it.toFloat() * 100f).coerceIn(0f, MAX_IMPORTED_PERCENT) }
        ?: 0f

    /**
     * 只有 EPUB 与纯文本（非 Markdown）能安全承载开源阅读的位置。
     *
     * Markdown 按 H1/H2 切章、章节偏移基于规范阅读文本，而开源阅读的章号来自它自己的
     * 目录正则、偏移基于源文本；两者既不同目录规则也不同坐标空间，打开时无法换算。
     * 若强行按章号取值，结果不是回到书首，而是跳到错误位置，因此这里直接排除。
     */
    private fun supportsImportedPosition(book: BookEntity): Boolean =
        book.format.lowercase() !in MARKDOWN_FORMATS

    private fun isEpub(book: BookEntity): Boolean =
        book.format.equals("epub", ignoreCase = true)

    private fun locationFor(book: BookEntity, remote: LegadoProgress): String =
        if (isEpub(book)) {
            // EPUB 的章节 + 章内偏移与本阅读器的 locator v2 同构，可直接恢复。
            // globalOffset 必须省略（locator v2 单一写入契约，S12-①）：导入时未解析
            // EPUB 正文、拿不到章起点表，若把章内偏移写进 v2 "offset" 冒充全书偏移，
            // 会违反 `offset == chapterStart[ci] + co` 不变式，导入后的所有回源跳转
            // （SourceNavigationContract 一致性校验）都被判为不一致而拒绝。缺省后由
            // 读侧按章元组派生、天然自洽；首次原生保存会补上真实的全书偏移。
            //
            // 直接构造 JSON 以避免 globalOffset 传 null（joint tree 的
            // LocatorBuilder.progressJson 的 globalOffset 非空，LocatorCodec.encode
            // 的 legacyOffset 也非空；工作树将两者改为可空但那两个文件不在本候选
            // 所有权内）。产物结构与 LocatorBuilder.progressJson(globalOffset=null)
            // 逐字一致：legacy offset + chapter + locator_v2 无 offset 字段。
            buildString {
                append('{')
                append("\"chapter\":").append(remote.chapterIndex.coerceAtLeast(0)).append(',')
                append("\"offset\":").append(remote.charOffset.coerceAtLeast(0))
                append(",\"locator_v2\":{\"v\":2,\"ci\":").append(remote.chapterIndex.coerceAtLeast(0))
                append(",\"co\":").append(remote.charOffset.coerceAtLeast(0)).append('}')
                append('}')
            }
        } else {
            // 纯文本需等打开本地正文并取得目录边界后再换算成绝对偏移；Markdown 已在
            // 匹配阶段被 [supportsImportedPosition] 排除，不会走到这里。
            LegadoProgressMarker.encode(remote.chapterIndex, remote.charOffset)
        }

    private fun shouldApply(
        book: BookEntity,
        existing: ReadingProgressEntity?,
        remote: LegadoProgress,
    ): Boolean {
        if (existing == null || existing.current_location_json.isNullOrBlank()) return true
        if (existing.readingState == ReadingCompletionState.FINISHED) return false

        val local = if (isEpub(book)) {
            val locator = LocatorCodec.locatorFromProgress(existing.current_location_json)
            val chapter = locator?.chapterIndex ?: Regex("\"chapter\"\\s*:\\s*(\\d+)")
                .find(existing.current_location_json)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val offset = locator?.charOffset ?: Regex("\"offset\"\\s*:\\s*(\\d+)")
                .find(existing.current_location_json)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            chapter?.let { ci -> offset?.let { co -> ci to co } }
        } else {
            LegadoProgressMarker.decode(existing.current_location_json)
                ?.let { it.chapterIndex to it.charOffset }
        }

        // 对非 Legado 坐标的纯文本进度，本项目无从证明两个目录规则等价，
        // 因而绝不以外部章内偏移覆盖本地精确全书偏移。
        if (local == null) return false
        return remote.chapterIndex > local.first ||
            (remote.chapterIndex == local.first && remote.charOffset > local.second)
    }

    private data class BookKey(val title: String, val author: String) {
        companion object {
            fun of(title: String, author: String?): BookKey = BookKey(normalize(title), normalize(author.orEmpty()))

            private fun normalize(value: String): String =
                value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        }
    }

    internal companion object {
        private const val BOOKSHELF_ENTRY = "bookshelf.json"

        /** 与 ReaderDocumentLoader 的 Markdown 分支判定保持一致（见 `normalizedFormat`）。 */
        private val MARKDOWN_FORMATS = setOf("md", "markdown")
        private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
        private const val MAX_BOOKSHELF_BYTES = 16 * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 256

        /**
         * 导入位置估算百分比上限。
         *
         * 外部导入仅知章号与总章数，属于粗略估算，天然不能代替用户的完成态决定。
         * 阅读器共享的 [mergeReaderProgress] 及书架以 99.5% 作为自动推导 FINISHED / COMPLETED
         * 的阈值。导入边界将其安全钳制在 99.4%，绝不因末章估算而将书籍静默改写为 FINISHED
         * 或生成 completed_at，确保分类与后续导入行为正确。
         */
        internal const val MAX_IMPORTED_PERCENT = 99.4f
        private val json = Json { ignoreUnknownKeys = true }
        private val REMOTE_ORDER = compareBy<LegadoProgress> { it.chapterIndex }
            .thenBy { it.charOffset }
            .thenBy { it.chapterTimeMillis }

        private fun readAtMost(input: ZipInputStream, maxBytes: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > maxBytes) {
                    throw IllegalArgumentException("$BOOKSHELF_ENTRY 超过 16 MiB，已拒绝读取")
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
