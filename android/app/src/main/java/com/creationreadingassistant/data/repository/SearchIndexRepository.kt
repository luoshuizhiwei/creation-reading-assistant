package com.creationreadingassistant.data.repository

import android.content.Context
import android.net.Uri
import android.os.StrictMode
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateRow
import com.creationreadingassistant.data.local.dao.SearchTermDao
import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.search.SearchTokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2-FTS5 MVP：中文 Bigram 全文索引仓储。
 *
 * MVP 阶段先不切到 SQLite FTS5 虚拟表 + custom tokenizer（JNI 工作量单独一批），
 * 用普通 search_terms 表（Room entity + 手工写的索引）承担索引写入与查询，
 * 并对 ShelfViewModel 暴露一套与当前 LIKE 搜索返回结构完全对齐的接口，
 * 后续切换到 FTS5 只需替换仓储内部实现。
 *
 * 索引覆盖范围：
 *   - 元数据（chapter_index = 0）：书名 title ×2 权重 + 作者/描述 ×1
 *   - TXT 小文件（≤20MB）：逐章切分，每章一个独立 chapter_index（从 1 开始），
 *     chapter_index = i 对应 TxtChapterDetector 识别出的第 (i-1) 章（含正文前楔子/序言）；
 *     阈值从 10MB 提高到 20MB，覆盖主流网文单文件（5-15MB）
 *   - TXT 大文件 + EPUB：暂时仍只索引 reader_preview（正文前 2 万字，chapter_index = 0），
 *     后续批次追加 EPUB 条目正文解包、TXT 流式分章续扫。
 */
@Singleton
class SearchIndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val termDao: SearchTermDao,
    private val stateDao: SearchIndexStateDao,
    private val tokenizer: SearchTokenizer,
) {

    data class Progress(
        val indexedBooks: Long,
        val totalBooks: Int,
        val isRunning: Boolean,
    )

    private val _progress = MutableStateFlow(Progress(0L, 0, false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    companion object {
        /** 分词器规则版本：升级 tokenizer 时自增，仓储会触发全盘重建。 */
        const val TOKENIZER_VERSION = 2

        /** 单次增量扫描一次处理多少本书（防止协程阻塞太久无法取消）。 */
        private const val CHUNK_SIZE = 20

        /** 书名/作者/描述命中额外权重倍率（叠加到 hits 计数）。 */
        private const val META_MULTIPLIER = 2

        /** 章节标题比正文多一次权重（标题命中的段落更容易直接跳到章节首）。 */
        private const val CHAPTER_TITLE_MULTIPLIER = 2

        /** 小文件按章索引的字节阈值（与导入阶段 inline 阈值对齐，避免再次流式解包）。 */
        const val TXT_CHAPTER_INDEX_BYTES = 20 * 1024 * 1024

        /** 单章正文超长截断（索引不求无损，超长章只索前 4 万字，避免单章分词撑爆内存）。 */
        private const val MAX_CHARS_PER_CHAPTER = 40_000
    }

    /** 章节切片：切分后的一章正文标题+文本，供逐章索引用。 */
    private data class TxtChapterSlice(
        val logicalIndex: Int, // 逻辑章序，0 基
        val title: String,
        val body: String,
    )

    /**
     * 用 [TxtChapterDetector] 把纯文本正文按逻辑章切分。
     * 即使识别失败、densityGuard 作废、整段退化，也保证至少返回一片「全文」，
     * 下游永远不会拿到空列表。
     */
    private fun splitTxtIntoChapters(fullText: String, tocRuleId: String?): List<TxtChapterSlice> {
        val detected = TxtChapterDetector.detect(fullText, tocRuleId ?: "builtin")
        if (detected.isEmpty()) return listOf(TxtChapterSlice(0, "全文", fullText.take(MAX_CHARS_PER_CHAPTER)))
        return detected.mapIndexed { i, c ->
            val endExclusive = (c.startOffset + c.charCount).coerceAtMost(fullText.length)
            val safeStart = c.startOffset.coerceIn(0, fullText.length)
            val bodyRaw = if (safeStart < endExclusive) fullText.substring(safeStart, endExclusive) else ""
            TxtChapterSlice(
                logicalIndex = i,
                title = c.title,
                body = bodyRaw.take(MAX_CHARS_PER_CHAPTER),
            )
        }
    }

    /**
     * 局部放开 StrictMode 的磁盘读/写/文件大小检测，用于仓储内部的磁盘读取。
     * 入口处的 allowThreadDiskWrites 防止 PlainTextDecoder 解码时的临时文件 / ZIP 条目解压的内部
     * 写操作被判违规；出口处严格恢复原策略，避免把泄露的放松状态带回调用栈。
     */
    private inline fun <T> allowDiskReads(block: () -> T): T {
        val old = StrictMode.getThreadPolicy()
        return try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(old)
                    .permitDiskReads()
                    .permitDiskWrites()
                    .build(),
            )
            block()
        } finally {
            StrictMode.setThreadPolicy(old)
        }
    }

    /**
     * 推断 EPUB 的本地缓存文件路径（importEpub / EpubParser.parse 两处都用同一约定落盘）。
     * 查不到文件返回 null，调用方负责降级为 reader_preview-only。
     *
     * StrictMode：内部通过 [allowDiskReads] 局部放开 File.isFile / File.length 的磁盘读。
     */
    private fun resolveEpubCacheFile(book: BookEntity): File? {
        val candidates = sequence {
            if (!book.local_content_path.isNullOrBlank()) yield(File(book.local_content_path))
            if (!book.local_uri.isNullOrBlank()) {
                runCatching { Uri.parse(book.local_uri) }
                    .getOrNull()
                    ?.takeIf { it.scheme == "file" }
                    ?.path
                    ?.let { yield(File(it)) }
            }
            yield(File(context.filesDir, "books/epub/${book.id}.epub"))
        }
        return allowDiskReads {
            candidates.firstOrNull { f -> f.isFile && f.length() > 0L }
        }
    }

    /** 外部入口：应用空闲/启动时触发一次增量扫描，不阻塞 UI。 */
    suspend fun ensureIndexedIncremental() {
        if (_progress.value.isRunning) return
        val total = bookDao.countActive()
        if (total == 0) {
            _progress.value = Progress(0L, 0, false)
            return
        }
        val cur = stateDao.get()
        // tokenizer 版本变更 → 先清旧索引再从头扫
        if (cur != null && cur.tokenizer_version != TOKENIZER_VERSION) {
            termDao.countIndexedBooks().let { n ->
                if (n > 0) wipeAllTermsForAllBooks()
            }
        }
        // observeAllActive 返回 Flow<List<BookEntity>>；首帧就是 DB 最新的活跃书架快照
        val activeFlow: kotlinx.coroutines.flow.Flow<List<BookEntity>> = bookDao.observeAllActive()
        val snapshotUnordered: List<BookEntity> = activeFlow.first()
        val snapshot: List<BookEntity> = snapshotUnordered.sortedBy { b: BookEntity -> b.id }
        val startState = cur?.takeIf { s -> s.tokenizer_version == TOKENIZER_VERSION }
        val startBookId: String? = startState?.last_scanned_book_id
        val matchIndex = snapshot.indexOfFirst { b: BookEntity -> b.id == startBookId }
        val startIdx: Int = if (startBookId == null) {
            0
        } else {
            (if (matchIndex < 0) 0 else matchIndex + 1).coerceIn(0, snapshot.size)
        }
        _progress.value = Progress(termDao.countIndexedBooks(), total, true)
        try {
            processChunked(snapshot, startIdx, snapshot.size)
        } finally {
            _progress.value = Progress(termDao.countIndexedBooks(), total, false)
        }
    }

    /** 存储页/设置页调用：强制清库+重建，用于「修复搜索」兜底入口。 */
    suspend fun rebuildAll() {
        wipeAllTermsForAllBooks()
        stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = TOKENIZER_VERSION,
                last_scanned_book_id = null,
                last_scanned_chapter_index = 0,
                built_at = 0,
            ),
        )
        ensureIndexedIncremental()
    }

    suspend fun countIndexedBooks(): Long = termDao.countIndexedBooks()
    suspend fun countTerms(): Long = termDao.countTerms()

    /**
     * 导入/修复成功后立即为单本书建索引，不等冷启动 5s 延迟的 [ensureIndexedIncremental]。
     * 内部与 [indexOneBook] 逻辑完全一致：删旧章 → 分章写入 → 分批 upsert，天然幂等。
     * 由 [ShelfImporter] 在每本书 ImportOutcome.Success 后通过 ViewModel 回调触发。
     */
    suspend fun indexSingleBook(book: BookEntity) {
        val total = bookDao.countActive().coerceAtLeast(1)
        val before = termDao.countIndexedBooks()
        _progress.value = Progress(before, total, true)
        try {
            indexOneBook(book)
        } finally {
            _progress.value = Progress(termDao.countIndexedBooks(), total, false)
        }
    }

    // ========= 正文全文搜索 =========

    /**
     * 单条查询结果：书 + 命中 hits 计数 + 命中书里「第一处」预览片段抽取坐标偏移。
     * hits 已按 Bigram 命中重叠累计，多 token 覆盖越多分越高，用于与现有 LIKE 搜索结果同序排序。
     */
    data class ContentHit(
        val bookId: String,
        val score: Int,
        /** 正文预览中第一处粗略命中偏移；若仅命中 title/author 则为 null。 */
        val previewFirstOffset: Int?,
    )

    /**
     * 全文搜索：按 CJK Bigram 分词 query，逐 token 查 search_terms 合并打分，
     * 返回 (bookId → ContentHit)。调用方用 bookIds 去 BookRepository.getByIds 拿书实体。
     *
     * 权重规则（纯函数，便于离线测试）：
     *   1. 每个 term 命中该书某章的 hits，按 query 中该 term 的出现次数线性叠加。
     *   2. 书名/作者命中过的 token 计入 META_MULTIPLIER 加成（因为索引侧已经在入 term 时叠了一次，
     *      所以这里不需要额外乘；命中 reader_preview 的 token 纯自然 hits）。
     *   3. 未命中所有 term 的书直接过滤，避免 LIKE 搜索式的半命中「凑结果」。
     */
    suspend fun searchContent(query: String, limit: Int = 50): Map<String, ContentHit> {
        val q = query.trim()
        if (q.isBlank()) return emptyMap()
        // 查询侧：使用 tokenizeForQuery（Bigram + 短查询补单字）提升召回；
        // 因为 tokenizeForQuery 只返回 List<String>，手动转成 (term, count=1) 的聚合形式。
        val queryTerms = tokenizer.tokenizeForQuery(q)
            .groupBy { it }
            .mapValues { (_, list) -> list.size }
        if (queryTerms.isEmpty()) return emptyMap()
        val scoreByBook = hashMapOf<String, Int>()
        for ((term, qCount) in queryTerms) {
            val rows = queryRowsByTerm(term) ?: continue
            for (r in rows) {
                scoreByBook[r.book_id] = (scoreByBook[r.book_id] ?: 0) + r.hits * qCount
            }
        }
        if (scoreByBook.isEmpty()) return emptyMap()
        val top = scoreByBook.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
        // 拿 preview 统一算片段偏移（仅取第一处命中的词边界，后续 ShelfViewModel 里精确抽取高亮 range）
        val previews = bookContentDao.getPreviewsByBookIds(top.map { it.key })
            .mapNotNull { c -> c.reader_preview?.let { p -> c.book_id to p } }
            .toMap()
        val lower = q.lowercase()
        val result = hashMapOf<String, ContentHit>()
        for ((bookId, score) in top) {
            val preview = previews[bookId]
            val firstOffset = preview?.lowercase()?.indexOf(lower)
                ?.takeIf { it >= 0 }
            result[bookId] = ContentHit(bookId = bookId, score = score, previewFirstOffset = firstOffset)
        }
        return result
    }

    // ========= 内部 =========

    private suspend fun queryRowsByTerm(term: String): List<SearchTermRow>? {
        // 由于 MVP 不引入动态 @Query，直接手写 raw SupportSQLiteQuery via Room DAO 没有简洁入口；
        // 采用最直接的实现：把 query 作为 WHERE term = ? 单独写一个 DAO 方法即可。
        return termDao.rowsByTerm(term).takeIf { it.isNotEmpty() }
    }

    private suspend fun wipeAllTermsForAllBooks() {
        // MVP 没有整表 wipe DAO，逐个遍历已索引书籍删除（因为 deleteByBook 存在，且索引中书籍数有限）。
        // 对于超大规模书架，下次可补 TRUNCATE 级整表清理接口。
        val allTermBooks = distinctBookIdsInTerms()
        for (bid in allTermBooks) termDao.deleteByBook(bid)
    }

    private suspend fun distinctBookIdsInTerms(): List<String> {
        return termDao.distinctBookIds()
    }

    private suspend fun processChunked(books: List<BookEntity>, from: Int, until: Int) {
        var i = from
        while (i < until) {
            val endExclusive = (i + CHUNK_SIZE).coerceAtMost(until)
            val chunk = books.subList(i, endExclusive)
            for (b in chunk) indexOneBook(b)
            i = endExclusive
            // 每 20 本写一次锚点，杀掉进程不会从头再来
            val last = chunk.last()
            stateDao.set(
                SearchIndexStateRow(
                    tokenizer_version = TOKENIZER_VERSION,
                    last_scanned_book_id = last.id,
                    last_scanned_chapter_index = 0,
                    built_at = System.currentTimeMillis(),
                ),
            )
            _progress.value = _progress.value.copy(indexedBooks = termDao.countIndexedBooks())
        }
    }

    /**
     * 为单本书建索引。
     * 先按书删旧行（整删，避免 chapter_index 变化产生残留），再分批次 upsert，保证「重扫单本书」幂等。
     *
     * 结构：
     *   chapter_index = 0：纯元数据（title ×2 + author + description），不挂正文 offsets；
     *   chapter_index >= 1：TXT 小文件的逐章正文 + 章标题（1-based，与 splitTxtIntoChapters 顺序对齐）；
     *   EPUB / 大型 TXT：降级为只挂 reader_preview 到 chapter_index = 0，不逐章。
     */
    private suspend fun indexOneBook(book: BookEntity) {
        termDao.deleteByBook(book.id)
        val pending = ArrayList<SearchTermRow>(600)

        // ========= chapter_index = 0：元数据（所有格式统一） =========
        run {
            val metaAgg = hashMapOf<String, AggregatedTerm>()
            val titleTokens = tokenizer.tokenizeDocument(book.title)
            addWeightedTokens(metaAgg, titleTokens, weight = META_MULTIPLIER)
            book.author?.takeIf { it.isNotBlank() }?.let { a ->
                addWeightedTokens(metaAgg, tokenizer.tokenizeDocument(a), weight = 1)
            }
            book.description?.takeIf { it.isNotBlank() }?.let { d ->
                addWeightedTokens(metaAgg, tokenizer.tokenizeDocument(d), weight = 1)
            }
            pending.ensureCapacity(pending.size + metaAgg.size)
            metaAgg.entries.mapTo(pending) { (term, a) ->
                SearchTermRow(
                    term = term,
                    book_id = book.id,
                    chapter_index = 0,
                    hits = a.hits,
                    offsets = null,
                )
            }
        }

        // ========= 正文：TXT 逐章 / EPUB 逐章 / 大文件降级 preview =========
        val isTxt = book.format.equals("txt", ignoreCase = true) || book.format.equals("md", ignoreCase = true)
        val isEpub = book.format.equals("epub", ignoreCase = true)
        val preview = bookContentDao.getByBook(book.id)?.reader_preview
        var chapterIndexedOk = false

        // ---- 路径 1：TXT / Markdown 小文件逐章 ----
        if (isTxt && book.size in 1..TXT_CHAPTER_INDEX_BYTES && !book.local_content_path.isNullOrBlank()) {
            val fullText: String? = allowDiskReads {
                runCatching {
                    val f = File(book.local_content_path)
                    if (!f.isFile) null
                    else PlainTextDecoder.decode(f.readBytes()).text
                }.getOrNull()
            }
            val slices = fullText?.let { text ->
                // 注：BookEntity 当前未持久化 toc_rule_id，自定义规则场景退化到内置标准规则；
                // 若后续将规则选择落库 books，可在此处补上。
                splitTxtIntoChapters(text, tocRuleId = null)
            }
            if (!slices.isNullOrEmpty()) {
                // 逐章：chapter_index = logicalIndex + 1（1 基，避开 chapter 0 元数据）
                for (slice in slices) {
                    val chapterAgg = hashMapOf<String, AggregatedTerm>()
                    if (slice.title.isNotBlank()) {
                        val titleTokens = tokenizer.tokenizeDocument(slice.title)
                        addWeightedTokens(chapterAgg, titleTokens, weight = CHAPTER_TITLE_MULTIPLIER)
                    }
                    if (slice.body.isNotEmpty()) {
                        val bodyTokens = tokenizer.tokenizeDocument(slice.body)
                        addWeightedTokens(chapterAgg, bodyTokens, weight = 1, offsets = true, fullText = slice.body)
                    }
                    val chapterIdx = 1 + slice.logicalIndex
                    chapterAgg.entries.mapTo(pending) { (term, a) ->
                        SearchTermRow(
                            term = term,
                            book_id = book.id,
                            chapter_index = chapterIdx,
                            hits = a.hits,
                            offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
                        )
                    }
                }
                chapterIndexedOk = true
            }
        }

        // ---- 路径 2：EPUB 逐章索引（按 chapters 顺序 chapter_index = 1..N） ----
        if (!chapterIndexedOk && isEpub) {
            val epubFile = resolveEpubCacheFile(book)
            if (epubFile != null) {
                // parse 缓存位置的 EPUB：文件已在 books/epub/{id}.epub，parse 不会做复制，
                // 仅解析 OPF + spine + TOC，拿到 chapter 列表（title / entryPath / chapterDir / cachedEpubPath）。
                val epubBook = allowDiskReads {
                    runCatching {
                        EpubParser.parse(
                            context = context,
                            uri = Uri.fromFile(epubFile),
                            expectedBookId = book.id,
                            fallbackLocalPath = epubFile.absolutePath,
                        )
                    }.getOrNull()
                }
                val chapters = epubBook?.chapters
                if (!chapters.isNullOrEmpty()) {
                    for ((chSeq, ch) in chapters.withIndex()) {
                        // 单章纯文本（与渲染侧共用 extractStreaming → 去标签保证一致）
                        val chapterText = allowDiskReads {
                            runCatching {
                                EpubParser.loadChapterText(
                                    cachedEpubPath = ch.cachedEpubPath,
                                    entryPath = ch.entryPath,
                                    chapterDir = ch.chapterDir,
                                )
                            }.getOrNull()
                        }?.take(MAX_CHARS_PER_CHAPTER).orEmpty()
                        if (chapterText.isBlank() && ch.title.isBlank()) continue

                        val chapterAgg = hashMapOf<String, AggregatedTerm>()
                        if (ch.title.isNotBlank()) {
                            val titleTokens = tokenizer.tokenizeDocument(ch.title)
                            addWeightedTokens(chapterAgg, titleTokens, weight = CHAPTER_TITLE_MULTIPLIER)
                        }
                        if (chapterText.isNotEmpty()) {
                            val bodyTokens = tokenizer.tokenizeDocument(chapterText)
                            addWeightedTokens(chapterAgg, bodyTokens, weight = 1, offsets = true, fullText = chapterText)
                        }
                        // chapter_index = chSeq + 1（1 基，与 TXT 逐章对齐；chapter 0 留作元数据）
                        val chapterIdx = 1 + chSeq
                        chapterAgg.entries.mapTo(pending) { (term, a) ->
                            SearchTermRow(
                                term = term,
                                book_id = book.id,
                                chapter_index = chapterIdx,
                                hits = a.hits,
                                offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
                            )
                        }
                    }
                    chapterIndexedOk = true
                }
            }
        }

        // ---- 路径 3：逐章都不可行（超大 TXT / EPUB 解析失败 / 格式未知）→ 降级 preview-only ----
        if (!chapterIndexedOk && preview != null && preview.isNotBlank()) {
            val previewAgg = hashMapOf<String, AggregatedTerm>()
            val previewTokens = tokenizer.tokenizeDocument(preview)
            addWeightedTokens(previewAgg, previewTokens, weight = 1, offsets = true, fullText = preview)
            previewAgg.entries.mapTo(pending) { (term, a) ->
                SearchTermRow(
                    term = term,
                    book_id = book.id,
                    chapter_index = 0,
                    hits = a.hits,
                    offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
                )
            }
        }

        if (pending.isEmpty()) return
        // upsert 超过 SQLite 单条参数上限时切块（999 参数硬上限 → 保守每批 200 行，单表 5 列 × 200 = 1000 占位）
        val batch = 200
        var k = 0
        while (k < pending.size) {
            val slice = pending.subList(k, (k + batch).coerceAtMost(pending.size))
            termDao.upsertAll(slice)
            k += batch
        }
    }

    private fun addWeightedTokens(
        agg: HashMap<String, AggregatedTerm>,
        tokens: List<SearchTokenizer.TokenHit>,
        weight: Int,
        offsets: Boolean = false,
        fullText: String? = null,
    ) {
        for (tok in tokens) {
            val a = agg.getOrPut(tok.term) { AggregatedTerm() }
            a.hits += tok.count * weight
            if (offsets && fullText != null) {
                val iter: Iterator<Int> = tok.offsets.iterator()
                while (iter.hasNext()) {
                    val offset: Int = iter.next()
                    if (a.offsetsCsv.length >= 16_000) break // 避免 TEXT 列无限膨胀
                    if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.append(',')
                    a.offsetsCsv.append(offset).append(':').append(tok.term.length)
                }
            }
        }
    }

    private class AggregatedTerm(
        var hits: Int = 0,
        val offsetsCsv: StringBuilder = StringBuilder(),
    )
}
