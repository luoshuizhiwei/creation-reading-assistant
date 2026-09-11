package com.creationreadingassistant.data.repository

import android.content.Context
import android.net.Uri
import android.os.StrictMode
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageRow
import com.creationreadingassistant.data.local.dao.SearchIndexStateDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateRow
import com.creationreadingassistant.data.local.dao.SearchTermDao
import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.search.DisplayChannelIndexer
import com.creationreadingassistant.feature.search.SearchOffsetResolver
import com.creationreadingassistant.feature.search.IndexUnit
import com.creationreadingassistant.feature.search.SearchContext
import com.creationreadingassistant.feature.search.SearchCoveragePolicy
import com.creationreadingassistant.feature.search.SearchCoverageReason
import com.creationreadingassistant.feature.search.SearchCoverageState
import com.creationreadingassistant.feature.search.SearchHit
import com.creationreadingassistant.feature.search.SearchHitContext
import com.creationreadingassistant.feature.search.SearchHitSelection
import com.creationreadingassistant.feature.search.SearchOffsets
import com.creationreadingassistant.feature.search.SearchTextBasis
import com.creationreadingassistant.feature.search.SearchTokenAggregator
import com.creationreadingassistant.feature.search.RawTermMatch
import com.creationreadingassistant.feature.search.SearchTokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次增量扫描的结果，供 worker 决定 WorkManager 的处置。
 *
 * 存在的理由：后台作业受系统执行时限约束，全库构建**必须**能跨多个作业窗口分批完成。
 * 旧实现把整库塞进一次 `doWork`，被系统掐断后 work 仍记为 SUCCEEDED、且进度锚点粒度
 * 太粗（每 20 本一次）导致进度全丢 —— 表现为索引永远建不完，且外部完全看不到失败。
 */
enum class IndexSweepResult {
    /** 书架快照已全部扫完（游标到达末尾）。 */
    COMPLETED,

    /** 本次扫了一部分，还有剩余；应当重新入队继续。 */
    PROGRESSED,

    /**
     * 一本都没扫完就耗尽了预算 —— 说明**单本书本身**超出一次作业窗口。
     *
     * 此时继续重试只会无限循环，因此调用方应停止自动重试并记录告警。
     * 根治需要章节级续建（`SearchIndexStateRow.last_scanned_chapter_index` 已预留该字段）。
     */
    STALLED,
}

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
 *   - EPUB：按 spine 顺序逐章解包，chapter_index = 1..N
 *   - 以上逐章路径都不可行时降级为 reader_preview（正文前 2 万字，chapter_index = 0）
 *
 * 覆盖状态（R2-S1.1）：每次建完索引都会把**实际走通的路径**与完成度写进
 * `search_index_coverage`（每本书 × 每种文本基准一行）。判定规则集中在纯函数
 * [SearchCoveragePolicy]，仓储只负责如实上报，不在这里做「大概算全量」的猜测 ——
 * 因为「部分索引不是全文完成」，UI 必须有据可查。
 *
 * 文本基准（R2-S1.1）：`SearchTermRow.text_basis` 区分 `original`（原文）与
 * `display`（替换显示文）。两套通道都会建：显示文通道复用原文同一组文本单元，
 * 套用 [com.creationreadingassistant.feature.reader.rules.RuleEngine] 的生效替换规则投影，
 * offsets 落在显示文坐标空间；无生效替换规则的书不建显示文索引
 * （[SearchCoverageState.NOT_APPLICABLE]，与原文逐字一致，再建只是重复）。
 */

@Singleton
class SearchIndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val termDao: SearchTermDao,
    private val stateDao: SearchIndexStateDao,
    private val coverageDao: SearchIndexCoverageDao,
    private val tokenizer: SearchTokenizer,
    private val rulesRepository: RulesRepository,
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

        /**
         * 单次构建允许占用的时间预算（毫秒）。
         *
         * 后台作业受系统执行时限约束：JobScheduler 超时后进程被直接掐掉（实测真机 26 本书
         * 只建到 9 本就被打断，且 work 仍被记为 SUCCEEDED、`stop_reason=-256`，失败完全不可见）。
         * 因此这里主动在预算耗尽前让出，由 worker 重新入队续建，而不是等着被系统杀掉。
         * 取 8 分钟：明显低于系统上限（约 10 分钟），给收尾与落锚点留余量。
         */
        const val INDEX_BUILD_BUDGET_MS = 8 * 60 * 1000L

        /** 书名/作者/描述命中额外权重倍率（叠加到 hits 计数）。 */
        private const val META_MULTIPLIER = 2

        /** 章节标题比正文多一次权重（标题命中的段落更容易直接跳到章节首）。 */
        private const val CHAPTER_TITLE_MULTIPLIER = 2

        /** 小文件按章索引的字节阈值（与导入阶段 inline 阈值对齐，避免再次流式解包）。 */
        const val TXT_CHAPTER_INDEX_BYTES = 20 * 1024 * 1024

        /** 单章正文超长截断（索引不求无损，超长章只索前 4 万字，避免单章分词撑爆内存）。 */
        private const val MAX_CHARS_PER_CHAPTER = 40_000

        /**
         * 单次 upsert 的行数上限。
         *
         * Room 的批量 @Insert 会把整批行拼成**一条** `INSERT ... VALUES (?,…),(?,…)`，
         * 绑定参数个数 = 行数 × 列数；而 SQLite 的 SQLITE_MAX_VARIABLE_NUMBER 在旧版本
         * （Android 11 / API 30 及以下，SQLite 3.28）只有 999。search_terms 现为 6 列，
         * 因此 150 × 6 = 900 是安全取值。
         *
         * 注：原值 200 × 5 = 1000 已经越界，只是此前仅在新版 SQLite（上限 32766）的设备上
         * 跑过而没暴露；本次给表加列时一并收紧，避免在 API 30 上「too many SQL variables」。
         */
        private const val UPSERT_BATCH_ROWS = 150

        /**
         * 为「抽取命中上下文」而整本解码 TXT 的体积上限。
         *
         * 上下文只需要命中所在那一章，但 TXT 没有字节级章偏移缓存，只能先解码全文再分章。
         * 20MB 的文件解码后是 ~40MB 的 UTF-16 String，超过这个量级就退化为「不显示片段」
         * （仍可精确跳转），绝不为一片上下文去冒 ANR / OOM 的风险。
         */
        private const val TXT_CONTEXT_MAX_BYTES = 8 * 1024 * 1024
    }

    /** 逐章路径的一次执行结果，供覆盖率判定（[SearchCoveragePolicy]）使用。 */
    private data class ChapterIndexRun(
        /** 实际把正文写入索引的章节数。 */
        val indexedChapters: Int,
        /** 本次认定的章节总数（已排除标题与正文均为空的章节）。 */
        val totalChapters: Int,
        /** 是否有章节因超过 [MAX_CHARS_PER_CHAPTER] 被截断。 */
        val truncated: Boolean,
    )

    /**
     * 单本书一次索引尝试的结果（章节级续建，§6.1）。
     *
     * @param completed 整本书是否建完（元数据 + 全部章节）。false 表示因预算耗尽停在半途，
     *                  已建章节均已落盘，下次从 [lastChapter] + 1 续建。
     * @param lastChapter 已完成的最大章节号（1 基，与 `chapter_index` 同口径）；
     *                    0 表示只到元数据 / 该书无章节。
     */
    private data class BookChapterOutcome(
        val completed: Boolean,
        val lastChapter: Int,
    )

    /**
     * 一轮扫描的推进结果。
     *
     * @param reached 实际推进到的书级下标；等于 `until` 表示整轮扫完。
     * @param advancedWithinBook 停在 [reached] 这本书上时，书内章节是否有推进。
     *                           章节级续建下这是「本轮没换书但确实前进了」的唯一证据。
     */
    private data class SweepOutcome(
        val reached: Int,
        val advancedWithinBook: Boolean,
    )

    /** 章节切片：切分后的一章正文标题+文本，供逐章索引用。 */
    private data class TxtChapterSlice(
        val logicalIndex: Int, // 逻辑章序，0 基
        val title: String,
        val body: String,
        /**
         * 该章在**全书文本**中的起始字符偏移。
         *
         * 索引侧的命中偏移是「章内」的，要把命中换算成 `ReaderLocator.legacyOffset`
         * 就靠它（S1.4 精确跳转）。真实字符量纲，不是估算值。
         */
        val startOffset: Int = 0,
        /** 该章正文是否因超过 [MAX_CHARS_PER_CHAPTER] 被截断（截断即意味着内容搜不全）。 */
        val truncated: Boolean = false,
    )

    /**
     * 用 [TxtChapterDetector] 把纯文本正文按逻辑章切分。
     * 即使识别失败、densityGuard 作废、整段退化，也保证至少返回一片「全文」，
     * 下游永远不会拿到空列表。
     */
    private fun splitTxtIntoChapters(fullText: String, tocRuleId: String?): List<TxtChapterSlice> {
        val detected = TxtChapterDetector.detect(fullText, tocRuleId ?: "builtin")
        if (detected.isEmpty()) {
            return listOf(
                TxtChapterSlice(
                    logicalIndex = 0,
                    title = "全文",
                    body = fullText.take(MAX_CHARS_PER_CHAPTER),
                    startOffset = 0,
                    truncated = fullText.length > MAX_CHARS_PER_CHAPTER,
                ),
            )
        }
        return detected.mapIndexed { i, c ->
            val endExclusive = (c.startOffset + c.charCount).coerceAtMost(fullText.length)
            val safeStart = c.startOffset.coerceIn(0, fullText.length)
            val bodyRaw = if (safeStart < endExclusive) fullText.substring(safeStart, endExclusive) else ""
            TxtChapterSlice(
                logicalIndex = i,
                title = c.title,
                body = bodyRaw.take(MAX_CHARS_PER_CHAPTER),
                startOffset = safeStart,
                truncated = bodyRaw.length > MAX_CHARS_PER_CHAPTER,
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

    /**
     * 外部入口：应用空闲/启动时触发一次增量扫描，不阻塞 UI。
     *
     * 返回本次扫描的结果，供 worker 决定是否重新入队续建（见 [IndexSweepResult]）。
     * [deadlineMs] 是本次允许占用的截止时刻，默认按 [INDEX_BUILD_BUDGET_MS] 推算；
     * 预算耗尽时**在书与书之间**让出，已完成的最后一本已落锚点，下次从它之后继续。
     */
    suspend fun ensureIndexedIncremental(
        deadlineMs: Long = System.currentTimeMillis() + INDEX_BUILD_BUDGET_MS,
    ): IndexSweepResult {
        // 已有扫描在跑：不并发，按「还有剩余」处理，让调用方稍后重试。
        if (_progress.value.isRunning) return IndexSweepResult.PROGRESSED
        val total = bookDao.countActive()
        if (total == 0) {
            _progress.value = Progress(0L, 0, false)
            return IndexSweepResult.COMPLETED
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
        // 章节级续建（§6.1）：锚点书的 last_scanned_chapter_index > 0 表示它**只建到该章**，
        // 本次必须停在这本书上从下一章继续（startIdx = matchIndex，而不是 +1）。
        val resumeChapter: Int = if (startBookId != null && matchIndex >= 0) {
            (startState?.last_scanned_chapter_index ?: 0).coerceAtLeast(0)
        } else {
            0
        }
        val startIdx: Int = when {
            startBookId == null -> 0
            matchIndex < 0 -> 0
            resumeChapter > 0 -> matchIndex // 该书未完成 → 停在它身上续建
            else -> (matchIndex + 1).coerceIn(0, snapshot.size) // 已完成 → 下一本
        }
        _progress.value = Progress(countIndexedBooksCheap(), total, true)
        return try {
            val outcome = processChunked(snapshot, startIdx, snapshot.size, deadlineMs, resumeChapter)
            indexSweepResult(
                reached = outcome.reached,
                from = startIdx,
                until = snapshot.size,
                advancedWithinBook = outcome.advancedWithinBook,
            )
        } finally {
            // ⚠️ 这个 finally 里**绝不能**出现 suspend 调用（哪怕是 `COUNT(...)`）。
            //
            // 被 JobScheduler 超时停止时，被停的是**作业**而不是进程：协程已处于取消态，
            // 任何 suspend 调用都会立刻抛 CancellationException，于是赋值语句永远不生效，
            // `isRunning` 就永久停在 true。此后每次 worker 启动都会被上面的守卫短路成
            // 8 毫秒空转重试 —— 表现为「无写入、无日志、无失败、attempts 与退避却一直涨」
            // 的无限重试。真机已复现：作业 #33 跑满 10 分钟被 `timeout` 停止，
            // 紧接着的 #34 仅 8ms 就 `jobFinished`，且应用内诊断日志 0 条 ERROR。
            _progress.value = _progress.value.copy(isRunning = false)
        }
    }

    /**
     * 强制清库 + 从第 0 本重建，用于「修复搜索」兜底入口。
     *
     * 注意：本方法目前**没有任何调用方**（旧注释称「存储页/设置页调用」，实测为零）。
     * 需要 UI 入口时再接；未扫完时调用方应继续重新入队。
     */
    suspend fun rebuildAll(): IndexSweepResult {
        wipeAllTermsForAllBooks()
        stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = TOKENIZER_VERSION,
                last_scanned_book_id = null,
                last_scanned_chapter_index = 0,
                built_at = 0,
            ),
        )
        return ensureIndexedIncremental()
    }

    /**
     * 规则变更后重建单本书的搜索索引（P1）。
     *
     * 替换规则变了，该书 display 通道的旧索引行即失真；[indexSingleBook] 幂等（先删该书行再重建），
     * 调用方负责放到 IO / 应用级作用域，避免阻塞阅读器交互。
     */
    suspend fun reindexBookById(bookId: String) {
        val book = bookDao.getById(bookId) ?: return
        indexSingleBook(book)
    }

    /**
     * 影响全库的规则变更（GLOBAL 作用域）置脏：把增量扫描游标重置到起点，
     * 使下一次 worker 扫描**从第 0 本重建全库** —— 惰性、不阻塞调用方。
     *
     * 游标只前进（见 [ensureIndexedIncremental]），因此「让已建过的书被重扫」唯一途径
     * 就是重置它；重建由后台 [SearchIndexWorker] 跨多个作业窗口完成。
     */
    suspend fun invalidateSweepForFullRebuild() {
        stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = TOKENIZER_VERSION,
                last_scanned_book_id = null,
                last_scanned_chapter_index = 0,
                built_at = 0,
            ),
        )
    }

    suspend fun countIndexedBooks(): Long = termDao.countIndexedBooks()
    suspend fun countTerms(): Long = termDao.countTerms()

    /**
     * 读取单本书在每种文本基准上的索引覆盖状态。
     *
     * 搜索 UI 要回答「这条结果背后是不是全量」只能读这里 —— 不允许由 format、
     * 命中数或章节数反推（README：「索引仓储不能据类名推断全覆盖」）。
     */
    suspend fun coverageOf(bookId: String): List<SearchIndexCoverageRow> =
        coverageDao.rowsForBook(bookId)

    /**
     * 全库覆盖状态计数（按状态聚合）。
     *
     * 注意分母是「行」而非「书」：每本书在 `original` / `display` 两个基准上各占一行。
     */
    suspend fun coverageCountsByState(): Map<SearchCoverageState, Int> =
        coverageDao.all()
            .groupingBy { SearchCoverageState.fromWire(it.coverage) }
            .eachCount()
            .mapNotNull { (state, count) -> state?.let { it to count } }
            .toMap()

    /**
     * 在指定基准上「仍有可索引内容未纳入」的书籍 id，供「续建 / 重试」入口使用。
     *
     * 默认只看 `original` 基准：display 基准只对「有生效替换规则」的书产生行，其余记
     * [SearchCoverageState.NOT_APPLICABLE]（由 [SearchCoveragePolicy.isIncomplete] 排除，不计入未完成），
     * 故以 original 作为「尚未完成」的默认扫描基准最稳妥。
     */
    suspend fun incompleteBookIds(
        basis: SearchTextBasis = SearchTextBasis.ORIGINAL,
    ): List<String> {
        val incompleteStates = SearchCoverageState.entries
            .filter { SearchCoveragePolicy.isIncomplete(it) }
            .map { it.wire }
        return coverageDao.rowsByStates(incompleteStates)
            .filter { it.text_basis == basis.wire }
            .map { it.book_id }
            .distinct()
            .sorted()
    }

    /**
     * 导入/修复成功后立即为单本书建索引，不等冷启动 5s 延迟的 [ensureIndexedIncremental]。
     * 内部与 [indexOneBook] 逻辑完全一致：删旧章 → 分章写入 → 分批 upsert，天然幂等。
     * 由 [ShelfImporter] 在每本书 ImportOutcome.Success 后通过 ViewModel 回调触发。
     */
    suspend fun indexSingleBook(book: BookEntity) {
        val total = bookDao.countActive().coerceAtLeast(1)
        _progress.value = Progress(countIndexedBooksCheap(), total, true)
        var finalCount = _progress.value.indexedBooks
        try {
            indexOneBook(
                book = book,
                startChapter = 0, // 导入成功后立即建索引：整本重建，不接续建断点
                deadlineMs = System.currentTimeMillis() + INDEX_BUILD_BUDGET_MS,
            )
            finalCount = countIndexedBooksCheap()
        } finally {
            // 与 §6.5.1 同源的坑：finally 里**绝不能**出现 suspend 调用（这里原本是
            // termDao.countIndexedBooks()）。取消态下它会立刻抛 CancellationException，
            // 使 isRunning=false 永远不生效，此后所有扫描都被顶部守卫短路成空转。
            // 计数一律在 try 内取好，finally 只做非 suspend 赋值。
            _progress.value = Progress(finalCount, total, false)
        }
    }

    // ========= 正文全文搜索 =========

    /**
     * 全文搜索：按 CJK Bigram 分词 query，逐 token 按「词 + 文本基准」查 search_terms，
     * 合并打分后返回**带坐标**的命中列表。
     *
     * 与旧实现的三处实质差异：
     *  1. **返回 [SearchHit] 而不是 `Map<String, ContentHit>`** —— 命中现在携带
     *     文本基准 / 章节 / 章内偏移 / 覆盖率，够 UI 直接标注来源并跳位置；
     *     同一本书在两种基准上各有命中时会各出一条（这正是「两者都搜并标注来源」的口径）。
     *  2. **真正消费 `search_terms.offsets`**（v11 起一直在写、查询侧从没读过），
     *     命中坐标由此而来，不再是「拿整串 query 在正文预览里 indexOf」的近似。
     *  3. **带上覆盖率**：调用方据此说明「这本书只索引了预览」，不再让 UI 靠 format 猜。
     *
     * 打分规则（详见 [SearchHitSelection]）：
     *   1. 每个 term 命中该书某章的 hits，按 query 中该 term 的出现次数线性叠加；
     *   2. 书名/作者/章标题的加权已在索引侧计入 hits，此处不再重复加权；
     *   3. 未命中任何 term 的书直接过滤，避免 LIKE 搜索式的半命中「凑结果」。
     *
     * @param bases 参与搜索的文本基准，默认只搜原文；调用方（如 [com.creationreadingassistant.ui.viewmodel.SearchViewModel]）
     *   可显式传入 `{ORIGINAL, DISPLAY}` 同时搜两条通道。显示文通道现已真正构建，是否纳入由调用方决定。
     */
    suspend fun searchContent(
        query: String,
        bases: Set<SearchTextBasis> = setOf(SearchTextBasis.ORIGINAL),
        limit: Int = 50,
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank() || limit <= 0) return@withContext emptyList()
        // 查询侧用 tokenizeForQuery（Bigram + 短查询补单字）提升召回；
        // 它只返回 List<String>，这里手动聚合成 (term → 出现次数)。
        val queryTerms = tokenizer.tokenizeForQuery(q)
            .groupBy { it }
            .mapValues { (_, list) -> list.size }
        if (queryTerms.isEmpty()) return@withContext emptyList()

        val raw = ArrayList<RawTermMatch>()
        for (basis in bases) {
            for ((term, qCount) in queryTerms) {
                for (row in termDao.rowsByTermForBasis(term, basis.wire)) {
                    raw += RawTermMatch(
                        bookId = row.book_id,
                        textBasis = basis,
                        chapterIndex = row.chapter_index,
                        term = term,
                        hits = row.hits,
                        queryTermCount = qCount,
                        span = SearchOffsets.parse(row.offsets, limit = 1).firstOrNull(),
                    )
                }
            }
        }
        if (raw.isEmpty()) return@withContext emptyList()

        val coverageByKey = coverageDao.all().associateBy { row -> row.book_id to row.text_basis }
        raw.groupBy { it.bookId to it.textBasis }
            .map { (key, matches) ->
                val (bookId, basis) = key
                val (chapterIndex, span) = SearchHitSelection.pickCoordinate(matches)
                val coverage = coverageByKey[bookId to basis.wire]
                SearchHit(
                    bookId = bookId,
                    score = SearchHitSelection.scoreOf(matches),
                    textBasis = basis,
                    indexChapterIndex = chapterIndex,
                    charOffset = span?.start,
                    matchLength = span?.length,
                    coverage = coverage?.let { SearchCoverageState.fromWire(it.coverage) },
                    coverageReason = coverage?.reason,
                )
            }
            .sortedWith(
                compareByDescending<SearchHit> { it.score }
                    .thenBy { it.bookId }
                    .thenBy { it.textBasis.wire },
            )
            .take(limit)
    }

    /**
     * 抽取命中附近的上下文文本。
     *
     * **必须在 IO 线程调用**（内部已 [withContext]）：TXT 会整文件解码、EPUB 会解包单章。
     * 请**逐条懒调用**（只给可见行调用），不要对整页结果批量调用 ——
     * 重 I/O 与常驻全本 String 都是这个项目的 ANR/OOM 红线。
     *
     * @return null 表示拿不到上下文（大文件保护、章节缺失、纯元数据命中、偏移越界）。
     *         此时 UI 应退化为「只显示书名 + 可跳位置」，而不是显示错位的文本。
     */
    suspend fun contextFor(
        hit: SearchHit,
        radius: Int = SearchContext.DEFAULT_RADIUS,
    ): SearchHitContext? = withContext(Dispatchers.IO) {
        val offset = hit.charOffset ?: return@withContext null
        // 捕获成局部 val：[SearchHit.readerChapterIndex] 有自定义 getter，直接判空后无法智能转型。
        val readerChapter = hit.readerChapterIndex
        val body = when {
            hit.isPreviewHit ->
                bookContentDao.getByBook(hit.bookId)?.reader_preview?.takeIf { it.isNotBlank() }
            readerChapter != null -> loadChapterTextForContext(hit.bookId, readerChapter)
            else -> null
        } ?: return@withContext null
        SearchContext.extract(body, offset, hit.matchLength ?: 0, radius)
    }

    /**
     * 把命中的「章内坐标」换算成**全书字符偏移**（与 `ReaderLocator.legacyOffset` 同口径），
     * 供精确跳转使用（R2-S1.4，R2-S1.6 扩展显示文基准）。
     *
     * **返回 null 表示换算不了，调用方必须降级为「只打开书」—— 绝不拿 0 冒充位置。**
     * 这是 `ReaderTemporaryRoute` 系列的既定纪律（伪造 offset=0 会让用户跳到书的最开头，
     * 还以为跳转成功了）。
     *
     * ### 各格式的偏移口径（不在这里「修正」，只如实复刻）
     *
     * - **TXT / MD**：章起始取 [TxtChapterDetector] 的 `startOffset`，是**真实字符偏移**，精确。
     *   预览命中（索引章号 0）的偏移**本身**就是全书偏移 —— 因为 `reader_preview`
     *   就是正文前 2 万字（见 `ShelfImporter`），并不是另一段文本。
     * - **EPUB**：章起始沿用 [LegacyOffsetCodec.chapterStartOffsets]，是 **ZIP 字节估算量纲**
     *   （中文约真实字符 3 倍）。这是阅读器**既有**的口径（历史书签/笔记存的同样是这个值），
     *   本函数只是原样复刻 —— 一旦在这里「改成真实字符」就会让所有历史定位整体漂移。
     *
     * ### 替换显示文基准（R2-S1.6）
     *
     * 命中 `textBasis == DISPLAY` 时，[SearchHit.charOffset] 处在**显示文坐标空间**
     * （索引侧对原文套用生效替换规则后重新分词得到的偏移）。要换算到全书原文偏移，
     * 必须先对**同一段原文**重放同一组生效替换规则，用返回的 [TextOffsetMap.toSource]
     * 把显示文偏移反查回原文章内偏移，再叠加全书章起始（坐标判定见 [SearchOffsetResolver]，
     * 章文本取数见 [loadSourceChapterForResolve]）。
     * 规则读取失败 / 正则失效一律按 null 降级（只打开书），绝不拿错位的 source 偏移冒充精确位置。
     *
     * ### 为什么只返回全局偏移、不顺便返回 (章, 章内偏移)
     *
     * `SourceNavigationContract.resolveChapteredPosition` 在「全局偏移」与「章节元组」**不一致**时
     * 会**直接拒绝跳转**。只给全局偏移就没有这个冲突面：阅读器会用同一套章起始表
     * 反解出章节，结果一致且不会被拒。
     *
     * **必须在 IO 线程调用**（内部已 [withContext]）：TXT 会整文件解码、EPUB 会解析 OPF/spine。
     */
    suspend fun resolveLegacyOffset(hit: SearchHit): Int? = withContext(Dispatchers.IO) {
        val book = bookDao.getById(hit.bookId) ?: return@withContext null
        val isDisplay = hit.textBasis == SearchTextBasis.DISPLAY
        val txtLike = isTxtLike(book.format)

        // 预览命中：preview 就是正文前缀，偏移即全书偏移。
        // EPUB 导入时不写 reader_preview，所以这里只认 TXT/MD，其余宁可返回 null。
        if (hit.isPreviewHit) {
            val preview = bookContentDao.getByBook(hit.bookId)
                ?.reader_preview?.takeIf { it.isNotBlank() }
            return@withContext SearchOffsetResolver.resolve(
                charOffset = hit.charOffset,
                isPreviewHit = true,
                isTxtLike = txtLike,
                isDisplayBasis = isDisplay,
                readerChapterIndex = null,
                sourceText = preview,
                chapterStart = 0,
                effectiveRules = if (isDisplay) effectiveReplaceRules(book) else emptyList(),
            )
        }

        val chapter = hit.readerChapterIndex ?: return@withContext null
        if (chapter < 0) return@withContext null

        // 取出「命中所在章」的**原文**文本串与它在全书中的起始偏移。
        // 索引侧投影的正是这段文本（同一组分章/解包口径），resolve 侧再套同样规则才能精确反查。
        val (sourceBody, chapterStart) = loadSourceChapterForResolve(book, chapter)
            ?: return@withContext null

        // 坐标判定全部委托给纯函数 [SearchOffsetResolver]，此处只负责取数。
        SearchOffsetResolver.resolve(
            charOffset = hit.charOffset,
            isPreviewHit = false,
            isTxtLike = txtLike,
            isDisplayBasis = isDisplay,
            readerChapterIndex = chapter,
            sourceText = sourceBody,
            chapterStart = chapterStart,
            effectiveRules = if (isDisplay) effectiveReplaceRules(book) else emptyList(),
        )
    }

    /**
     * 加载「命中所在章」的**原文**文本串与它在全书中的起始偏移，供显示文命中反查 source 偏移。
     *
     * 与索引写入侧（逐章切分 / EPUB 逐章解包）同款口径，保证两段文本一致、偏移映射可逆。
     * 大 TXT 整本解码受 [TXT_CONTEXT_MAX_BYTES] 保护，超过直接放弃（宁可没有精确位置）。
     */
    private suspend fun loadSourceChapterForResolve(
        book: BookEntity,
        readerChapterIndex: Int,
    ): Pair<String, Int>? {
        if (isTxtLike(book.format)) {
            if (book.size > TXT_CONTEXT_MAX_BYTES) return null
            val path = book.local_content_path?.takeIf { it.isNotBlank() } ?: return null
            val fullText = allowDiskReads {
                runCatching {
                    val f = File(path)
                    if (!f.isFile) null else PlainTextDecoder.decode(f.readBytes()).text
                }.getOrNull()
            } ?: return null
            val slice = splitTxtIntoChapters(fullText, tocRuleId = null)
                .getOrNull(readerChapterIndex) ?: return null
            return slice.body to slice.startOffset
        }
        if (book.format.equals("epub", ignoreCase = true)) {
            val file = resolveEpubCacheFile(book) ?: return null
            val chapters = allowDiskReads {
                runCatching {
                    EpubParser.parse(
                        context = context,
                        uri = Uri.fromFile(file),
                        expectedBookId = book.id,
                        fallbackLocalPath = file.absolutePath,
                    )
                }.getOrNull()
            }?.chapters ?: return null
            if (readerChapterIndex !in chapters.indices) return null
            val chapter = chapters[readerChapterIndex]
            val chapterText = allowDiskReads {
                runCatching {
                    EpubParser.loadChapterText(
                        cachedEpubPath = chapter.cachedEpubPath,
                        entryPath = chapter.entryPath,
                        chapterDir = chapter.chapterDir,
                    )
                }.getOrNull()
            }?.take(MAX_CHARS_PER_CHAPTER)?.takeIf { it.isNotBlank() } ?: return null
            val starts = LegacyOffsetCodec.chapterStartOffsets(chapters.map { it.estimatedTextLength })
            return chapterText to starts[readerChapterIndex]
        }
        return null
    }

    // ========= 内部 =========

    private fun isTxtLike(format: String): Boolean =
        format.equals("txt", ignoreCase = true) || format.equals("md", ignoreCase = true)

    /**
     * 取一本书当前生效的替换规则（[RulesRepository] 已过滤 enabled）。
     *
     * 显示文通道用它把原文投影成用户实际读到的文本（见 [DisplayChannelIndexer]）。
     * 规则仓库读取失败（理论上不应发生）按「无规则」处理，显示文通道退化为 NOT_APPLICABLE。
     */
    private suspend fun effectiveReplaceRules(book: BookEntity): List<ReplaceRule> =
        runCatching { rulesRepository.observe(book.id).first().effectiveReplace }.getOrDefault(emptyList())

    /**
     * 为上下文抽取加载「命中所在章」的正文。
     *
     * 与索引写入侧共用同一套分章/解包口径，保证坐标对得上。
     * 大 TXT 直接放弃（不整本解码），宁可没有片段也不能 ANR / OOM。
     */
    private suspend fun loadChapterTextForContext(bookId: String, readerChapterIndex: Int): String? {
        val book = bookDao.getById(bookId) ?: return null
        val isTxt = book.format.equals("txt", ignoreCase = true) ||
            book.format.equals("md", ignoreCase = true)
        if (isTxt) {
            val path = book.local_content_path?.takeIf { it.isNotBlank() } ?: return null
            if (book.size > TXT_CONTEXT_MAX_BYTES) return null
            val fullText = allowDiskReads {
                runCatching {
                    val f = File(path)
                    if (!f.isFile) null else PlainTextDecoder.decode(f.readBytes()).text
                }.getOrNull()
            } ?: return null
            return splitTxtIntoChapters(fullText, tocRuleId = null)
                .getOrNull(readerChapterIndex)
                ?.body
                ?.takeIf { it.isNotBlank() }
        }
        if (book.format.equals("epub", ignoreCase = true)) {
            val file = resolveEpubCacheFile(book) ?: return null
            val chapter = allowDiskReads {
                runCatching {
                    EpubParser.parse(
                        context = context,
                        uri = Uri.fromFile(file),
                        expectedBookId = book.id,
                        fallbackLocalPath = file.absolutePath,
                    )
                }.getOrNull()
            }?.chapters?.getOrNull(readerChapterIndex) ?: return null
            return allowDiskReads {
                runCatching {
                    EpubParser.loadChapterText(
                        cachedEpubPath = chapter.cachedEpubPath,
                        entryPath = chapter.entryPath,
                        chapterDir = chapter.chapterDir,
                    )
                }.getOrNull()
            }?.take(MAX_CHARS_PER_CHAPTER)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private suspend fun wipeAllTermsForAllBooks() {
        // MVP 没有整表 wipe DAO，逐个遍历已索引书籍删除（因为 deleteByBook 存在，且索引中书籍数有限）。
        // 对于超大规模书架，下次可补 TRUNCATE 级整表清理接口。
        val allTermBooks = distinctBookIdsInTerms()
        for (bid in allTermBooks) termDao.deleteByBook(bid)
        // 覆盖状态与分词结果同生共死：只清 search_terms 不清 coverage，
        // 会让「重建未完成索引」误判已完成。这里直接整表清掉，重建时逐本重写。
        coverageDao.deleteAll()
    }

    private suspend fun distinctBookIdsInTerms(): List<String> {
        return termDao.distinctBookIds()
    }

    /**
     * 「已建成本数」的**廉价**读法：数覆盖率表里 `original` 基准的行数。
     *
     * 覆盖率表每本书每基准至多一行（全库几十行），读它的代价与书数同阶；
     * 而 [SearchTermDao.countIndexedBooks] 是 `SELECT COUNT(DISTINCT book_id) FROM search_terms`，
     * 在千万行的 search_terms 上是一次全表扫描。旧实现**每建完一本**就调用它刷进度，
     * 单次构建因此要多扫几十遍全表 —— 真机实测这是吞吐偏低（约 5 分钟/本）的主因之一。
     *
     * 语义仍然准确：`indexOneBook` 结束时必然写入该书的 `original` 覆盖率行，
     * 因此这个计数就等于「已完成一轮索引的书数」，不是估算值。
     */
    private suspend fun countIndexedBooksCheap(): Long =
        coverageDao.all().count { row -> row.text_basis == SearchTextBasis.ORIGINAL.wire }.toLong()

    /**
     * 逐本建索引，每建完一本立刻落一次进度锚点。
     *
     * 锚点粒度必须等于「已完成的最后一本」：旧实现每 20 本才写一次，而真机书库常见规模
     * 只有几十本、构建又几乎必然被系统执行时限打断 —— 结果是 26 本书的场景**永远写不出
     * 锚点**，中断即进度全丢、下次从第 0 本重来。单本落锚点只是一次单行 upsert，代价可忽略。
     *
     * 预算检查放在**书与书之间**：单本书中途无法安全中断（[indexOneBook] 先整删后重写），
     * 但即使某本超窗被杀，锚点仍停在它的前一本，下次幂等重扫该书即可。
     *
     * **失败也逐本隔离**：单本抛异常只记一条告警并跳到下一本，不终止整轮扫描 ——
     * 否则书库里只要有一本坏书，全库索引就永远建不完。详见循环体内的注释。
     *
     * @return 实际推进到的下标；等于 [until] 表示本批已扫完。
     */
    private suspend fun processChunked(
        books: List<BookEntity>,
        from: Int,
        until: Int,
        deadlineMs: Long,
        firstBookStartChapter: Int,
    ): SweepOutcome {
        var i = from
        var advancedWithinBook = false
        while (i < until) {
            if (System.currentTimeMillis() >= deadlineMs) break
            val book = books[i]
            // 只有本轮的第一本书可能需要从章节断点续建；其余都从 0（整本）开始。
            val startChapter = if (i == from) firstBookStartChapter else 0
            // 逐本隔离失败：单本抛异常**不得**中断整库扫描。
            //
            // 旧实现让异常一路冒到 worker，被记成一次作业失败并终止整轮 —— 只要书库里
            // 存在一本解析/落库异常的书，全库索引就**永远建不完**，且用户侧看不到是哪一本。
            // 真机实测：扫到第 22 本抛异常后整轮终止，其后 4 本再未被索引。
            //
            // ⚠️ CancellationException 必须继续上抛：它是「作业被系统停止」的正常信号，
            // 不是单本失败，吞掉会让被取消的作业伪装成成功、还会掩盖真正的取消语义。
            val outcome: BookChapterOutcome? = try {
                indexOneBook(book, startChapter, deadlineMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppLog.e(
                    "SearchIndex",
                    "单本索引失败，已跳过并继续扫描：book=${book.id.take(8)} format=${book.format} " +
                        "cause=${error.javaClass.simpleName}: ${error.message}",
                )
                null
            }
            when {
                outcome == null -> {
                    // 失败：仍落锚点（chapter=0 视为该书本轮结束），否则这本永远卡住游标。
                    stateDao.set(
                        SearchIndexStateRow(
                            tokenizer_version = TOKENIZER_VERSION,
                            last_scanned_book_id = book.id,
                            last_scanned_chapter_index = 0,
                            built_at = System.currentTimeMillis(),
                        ),
                    )
                    i++
                }
                !outcome.completed -> {
                    // 章节级续建（§6.1）：这本书只建了一部分就让出预算。落**章节锚点**并停在
                    // 这本书上，下次从 lastChapter + 1 继续 —— 书级下标**不前进**，
                    // 因此必须靠 advancedWithinBook 把「确实前进了」传给上层判定。
                    stateDao.set(
                        SearchIndexStateRow(
                            tokenizer_version = TOKENIZER_VERSION,
                            last_scanned_book_id = book.id,
                            last_scanned_chapter_index = outcome.lastChapter,
                            built_at = System.currentTimeMillis(),
                        ),
                    )
                    advancedWithinBook = outcome.lastChapter > startChapter
                    _progress.value = _progress.value.copy(indexedBooks = countIndexedBooksCheap())
                    break
                }
                else -> {
                    // 整本完成：chapter=0 表示「该书已完整建完」，下次从它的下一本开始。
                    stateDao.set(
                        SearchIndexStateRow(
                            tokenizer_version = TOKENIZER_VERSION,
                            last_scanned_book_id = book.id,
                            last_scanned_chapter_index = 0,
                            built_at = System.currentTimeMillis(),
                        ),
                    )
                    i++
                }
            }
            _progress.value = _progress.value.copy(indexedBooks = countIndexedBooksCheap())
        }
        return SweepOutcome(reached = i, advancedWithinBook = advancedWithinBook)
    }

    /**
     * 为单本书建索引。
     * 先按书删旧行（整删，避免 chapter_index 变化产生残留），再分批次 upsert，保证「重扫单本书」幂等。
     *
     * 结构：
     *   chapter_index = 0：元数据（title ×2 + author + description），不挂正文 offsets；
     *                     逐章路径走不通时，正文预览也落在这一档；
     *   chapter_index >= 1：逐章正文 + 章标题（1-based，TXT 与 EPUB 共用同一编号空间）。
     *
     * 结束前把**实际走通的路径**写进 [SearchIndexCoverageRow]：原文基准一行记真实完成度，
     * 显示文基准一行记真实完成度（无生效替换规则时记 [SearchCoverageState.NOT_APPLICABLE]、
     * 规则失效时记 [SearchCoverageState.FAILED]，均不再用占位 [SearchCoverageState.PENDING]）。
     * 覆盖率不由 format/行数反推，判定规则集中在纯函数 [SearchCoveragePolicy]。
     */
    private suspend fun indexOneBook(
        book: BookEntity,
        startChapter: Int,
        deadlineMs: Long,
    ): BookChapterOutcome {
        // 章节级续建（§6.1）：startChapter > 0 表示上一窗口只建到该章，本次从它的下一章继续。
        // 此时**绝不能整删** —— 旧实现「先整删后重写」意味着单本超窗就整本白做，下次重来
        // 依然超窗，形成「这本书永远建不完」的死局（真机 #22 大 EPUB 即如此）。
        val fresh = startChapter <= 0
        if (fresh) {
            termDao.deleteByBook(book.id)
            coverageDao.deleteByBook(book.id)
        }
        val pending = ArrayList<SearchTermRow>(600)
        val basisWire = SearchTextBasis.ORIGINAL.wire
        // 显示文通道改为**逐章即时投影**：不再先收集全书文本单元再统一投影。
        // 既让断点续建可增量追加，也把大书的内存峰值从「整本正文」降到「单章」。
        val displayRules = effectiveReplaceRules(book)
        var displayFailed = false
        /**
         * 显示文投影失效处理（§6.6.6）：立刻停止后续章节的 display 投影，并**精确回收**
         * 该基准的行 —— 待落盘的直接从 [pending] 摘掉，已落盘的按 (book, basis) 删除，
         * original 基准一行都不动。回收失败只告警，不抛出：残留行会在该书下次整本重建
         * （fresh）时被 [SearchTermDao.deleteByBook] 清掉，且覆盖率行已诚实记为
         * [SearchCoverageState.FAILED]，不会被当成完整索引。
         */
        suspend fun abandonDisplay() {
            if (displayFailed) return
            displayFailed = true
            discardDisplayRows(book.id, pending)
        }
        /** 已完成的最大章节号（1 基）；续建时从断点起，建完一章前进一次。 */
        var lastChapter = startChapter
        /** 是否因预算耗尽而中途让出（false 表示整本建完）。 */
        var interrupted = false
        // 续建基线：上一轮已统计的章节数从覆盖率行读回并累加，避免重复或漏计。
        var baseIndexed = 0
        var baseTotal = 0
        var baseTruncated = false
        if (!fresh) {
            coverageDao.rowsForBook(book.id)
                .firstOrNull { it.text_basis == SearchTextBasis.ORIGINAL.wire }
                ?.let { prev ->
                    baseIndexed = prev.indexed_chapters
                    baseTotal = prev.total_chapters
                    baseTruncated = prev.reason == SearchCoverageReason.CHAPTER_TRUNCATED
                }
        }

        // ========= chapter_index = 0：元数据（所有格式统一） =========
        // 续建时元数据已在首轮写入且未被删除，不再重复生成（避免重复行与无谓开销）。
        if (fresh) {
            val metaAgg = hashMapOf<String, SearchTokenAggregator.AggregatedTerm>()
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
                    text_basis = basisWire,
                    hits = a.hits,
                    offsets = null,
                )
            }
            // 显示文元数据单元：标题按 ×2、作者+描述合并按 ×1，与原文加权对齐。
            if (displayRules.isNotEmpty() && !displayFailed) {
                val metaBody = listOfNotNull(book.author, book.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                try {
                    pending += DisplayChannelIndexer.project(
                        IndexUnit(chapterIndex = 0, title = book.title, body = "", withOffsets = false),
                        book.id,
                        displayRules,
                        tokenizer,
                    )
                    if (metaBody.isNotBlank()) {
                        pending += DisplayChannelIndexer.project(
                            IndexUnit(chapterIndex = 0, title = "", body = metaBody, withOffsets = false),
                            book.id,
                            displayRules,
                            tokenizer,
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    abandonDisplay()
                }
            }
        }

        // ========= 正文：TXT 逐章 / EPUB 逐章 / 降级 preview =========
        val isTxt = book.format.equals("txt", ignoreCase = true) || book.format.equals("md", ignoreCase = true)
        val isEpub = book.format.equals("epub", ignoreCase = true)
        val preview = bookContentDao.getByBook(book.id)?.reader_preview

        /** 逐章路径的执行结果；null 表示尚无任何路径产出可逐章索引的正文。 */
        var chapterRun: ChapterIndexRun? = null

        /** 逐章路径尝试失败的原因，仅在最终连预览都拿不到时才作为覆盖率理由上报。 */
        var failureReason: String? = null

        // ---- 路径 1：TXT / Markdown 小文件逐章 ----
        val txtChapterEligible = isTxt &&
            book.size in 1..TXT_CHAPTER_INDEX_BYTES &&
            !book.local_content_path.isNullOrBlank()
        if (txtChapterEligible) {
            val fullText: String? = allowDiskReads {
                runCatching {
                    val f = File(book.local_content_path)
                    if (!f.isFile) null
                    else PlainTextDecoder.decode(f.readBytes()).text
                }.getOrNull()
            }
            if (fullText == null) {
                failureReason = SearchCoverageReason.TXT_DECODE_FAILED
            } else {
                // 注：BookEntity 当前未持久化 toc_rule_id，自定义规则场景退化到内置标准规则；
                // 若后续将规则选择落库 books，可在此处补上。
                val slices = splitTxtIntoChapters(fullText, tocRuleId = null)
                var indexedChapters = 0
                // 全书章节数 = 分章结果里「有内容」的片数。
                // 分母必须是**全书口径**：旧实现在循环里 ++，只统计本轮真正处理的章 ——
                // 续建时本轮只遍历断点之后，分母就退化成「本轮建了几章」，中断即偏小、
                // 与历史基线相加又虚高（真机实测 1250 / 3023，全书实为 1774 章）。
                val totalChapters = slices.count { it.title.isNotBlank() || it.body.isNotBlank() }
                var anyTruncated = false
                for (slice in slices) {
                    val hasTitle = slice.title.isNotBlank()
                    val hasBody = slice.body.isNotBlank()
                    if (!hasTitle && !hasBody) continue
                    // 逐章：chapter_index = logicalIndex + 1（1 基，避开 chapter 0 元数据）
                    val chapterIdx = 1 + slice.logicalIndex
                    // 章节级续建：已建过的章节直接跳过（它们的统计已在 base* 基线里，不重复计）。
                    if (chapterIdx <= startChapter) continue
                    // 预算让出：检查放在**章节之间**。已建章节均已随 flush 落盘，断点有效。
                    if (System.currentTimeMillis() >= deadlineMs) {
                        interrupted = true
                        break
                    }
                    if (hasBody) indexedChapters++
                    if (slice.truncated) anyTruncated = true

                    val chapterAgg = hashMapOf<String, SearchTokenAggregator.AggregatedTerm>()
                    if (hasTitle) {
                        val titleTokens = tokenizer.tokenizeDocument(slice.title)
                        addWeightedTokens(chapterAgg, titleTokens, weight = CHAPTER_TITLE_MULTIPLIER)
                    }
                    if (hasBody) {
                        val bodyTokens = tokenizer.tokenizeDocument(slice.body)
                        addWeightedTokens(chapterAgg, bodyTokens, weight = 1, offsets = true, fullText = slice.body)
                    }
                    chapterAgg.entries.mapTo(pending) { (term, a) ->
                        SearchTermRow(
                            term = term,
                            book_id = book.id,
                            chapter_index = chapterIdx,
                            text_basis = basisWire,
                            hits = a.hits,
                            offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
                        )
                    }
                    // 显示文逐章单元：即时投影（不再积压到全书结束统一投影）。
                    if (displayRules.isNotEmpty() && !displayFailed) {
                        try {
                            pending += DisplayChannelIndexer.project(
                                IndexUnit(
                                    chapterIndex = chapterIdx,
                                    title = slice.title,
                                    body = slice.body,
                                    withOffsets = true,
                                ),
                                book.id,
                                displayRules,
                                tokenizer,
                            )
                        } catch (e: IllegalArgumentException) {
                            abandonDisplay()
                        }
                    }
                    lastChapter = chapterIdx
                    // 分批落盘：让已建章节及时持久化，同时把内存占用压在单批规模。
                    if (pending.size >= UPSERT_BATCH_ROWS) {
                        termDao.upsertAll(pending.toList())
                        pending.clear()
                    }
                }
                // 只要解码成功就认定逐章路径成立（分章函数保证至少返回一片「全文」），
                // 与旧行为一致：不再降级到 preview。
                chapterRun = ChapterIndexRun(indexedChapters, totalChapters, anyTruncated)
            }
        } else if (isTxt) {
            failureReason = if (book.local_content_path.isNullOrBlank()) {
                SearchCoverageReason.NO_BODY_SOURCE
            } else if (book.size > TXT_CHAPTER_INDEX_BYTES) {
                SearchCoverageReason.FILE_TOO_LARGE
            } else {
                // size 非正（脏数据）等边界：归为无可用正文来源
                SearchCoverageReason.NO_BODY_SOURCE
            }
        }

        // ---- 路径 2：EPUB 逐章索引（按 chapters 顺序 chapter_index = 1..N） ----
        if (chapterRun == null && isEpub) {
            val epubFile = resolveEpubCacheFile(book)
            if (epubFile == null) {
                failureReason = SearchCoverageReason.EPUB_FILE_MISSING
            } else {
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
                when {
                    epubBook == null -> failureReason = SearchCoverageReason.EPUB_PARSE_FAILED
                    chapters.isNullOrEmpty() -> failureReason = SearchCoverageReason.EPUB_NO_CHAPTERS
                    else -> {
                        var indexedChapters = 0
                        // 全书章节数 = 分章结果长度（全书口径，与续建起点无关，理由见 TXT 分支）
                        val totalChapters = chapters.size
                        var anyTruncated = false
                        for ((chSeq, ch) in chapters.withIndex()) {
                            // chapter_index = chSeq + 1（1 基，与 TXT 逐章对齐；chapter 0 留作元数据）
                            val chapterIdx = 1 + chSeq
                            // 章节级续建：已建过的章节直接跳过。
                            // 关键是**不加载**该章正文 —— EPUB 单章的解压 + 解码是主要开销，
                            // 跳过即把上一轮已完成的工作从本轮成本里彻底移除，这是超大 EPUB
                            // 能跨窗口最终建完的前提。
                            if (chapterIdx <= startChapter) continue
                            // 预算让出：检查放在章节之间，已建章节均已随 flush 落盘。
                            if (System.currentTimeMillis() >= deadlineMs) {
                                interrupted = true
                                break
                            }
                            // 单章纯文本（与渲染侧共用 extractStreaming → 去标签保证一致）
                            val chapterRaw: String = allowDiskReads {
                                runCatching {
                                    EpubParser.loadChapterText(
                                        cachedEpubPath = ch.cachedEpubPath,
                                        entryPath = ch.entryPath,
                                        chapterDir = ch.chapterDir,
                                    )
                                }.getOrNull()
                            }.orEmpty()
                            val isTruncated = chapterRaw.length > MAX_CHARS_PER_CHAPTER
                            val chapterText = chapterRaw.take(MAX_CHARS_PER_CHAPTER)
                            if (chapterText.isBlank() && ch.title.isBlank()) continue
                            if (chapterText.isNotBlank()) indexedChapters++
                            if (isTruncated) anyTruncated = true

                            val chapterAgg = hashMapOf<String, SearchTokenAggregator.AggregatedTerm>()
                            if (ch.title.isNotBlank()) {
                                val titleTokens = tokenizer.tokenizeDocument(ch.title)
                                addWeightedTokens(chapterAgg, titleTokens, weight = CHAPTER_TITLE_MULTIPLIER)
                            }
                            if (chapterText.isNotEmpty()) {
                                val bodyTokens = tokenizer.tokenizeDocument(chapterText)
                                addWeightedTokens(
                                    chapterAgg,
                                    bodyTokens,
                                    weight = 1,
                                    offsets = true,
                                    fullText = chapterText,
                                )
                            }
                            chapterAgg.entries.mapTo(pending) { (term, a) ->
                                SearchTermRow(
                                    term = term,
                                    book_id = book.id,
                                    chapter_index = chapterIdx,
                                    text_basis = basisWire,
                                    hits = a.hits,
                                    offsets = if (a.offsetsCsv.isNotBlank()) a.offsetsCsv.toString() else null,
                                )
                            }
                            // 显示文逐章单元：即时投影。
                            if (displayRules.isNotEmpty() && !displayFailed) {
                                try {
                                    pending += DisplayChannelIndexer.project(
                                        IndexUnit(
                                            chapterIndex = chapterIdx,
                                            title = ch.title,
                                            body = chapterText,
                                            withOffsets = true,
                                        ),
                                        book.id,
                                        displayRules,
                                        tokenizer,
                                    )
                                } catch (e: IllegalArgumentException) {
                                    abandonDisplay()
                                }
                            }
                            lastChapter = chapterIdx
                            // 分批落盘。
                            if (pending.size >= UPSERT_BATCH_ROWS) {
                                termDao.upsertAll(pending.toList())
                                pending.clear()
                            }
                        }
                        chapterRun = ChapterIndexRun(indexedChapters, totalChapters, anyTruncated)
                    }
                }
            }
        }

        // ---- 路径 3：逐章都不可行（超大 TXT / EPUB 解析失败 / 格式未知）→ 降级 preview-only ----
        // 仅在首轮（fresh）尝试：续建时逐章路径必然已成立，不该退化到预览。
        var previewIndexed = false
        if (chapterRun == null && fresh && preview != null && preview.isNotBlank()) {
            val previewAgg = hashMapOf<String, SearchTokenAggregator.AggregatedTerm>()
            val previewTokens = tokenizer.tokenizeDocument(preview)
            addWeightedTokens(previewAgg, previewTokens, weight = 1, offsets = true, fullText = preview)
            previewAgg.entries.mapTo(pending) { (term, a) ->
                SearchTermRow(
                    term = term,
                    book_id = book.id,
                    chapter_index = 0,
                    text_basis = basisWire,
                    hits = a.hits,
                    offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
                )
            }
            // 显示文预览单元：chapter_index = 0，带偏移（预览即正文前缀）。
            if (displayRules.isNotEmpty() && !displayFailed) {
                try {
                    pending += DisplayChannelIndexer.project(
                        IndexUnit(chapterIndex = 0, title = "", body = preview, withOffsets = true),
                        book.id,
                        displayRules,
                        tokenizer,
                    )
                } catch (e: IllegalArgumentException) {
                    abandonDisplay()
                }
            }
            previewIndexed = true
        }

        // ========= 显示文通道 =========
        // 已改为**逐章即时投影**（见元数据段与各章节循环内）：不再先收集全书文本单元再统一投影。
        // 这样既能增量追加（配合章节级续建），也把大书的内存峰值从「整本正文」降到「单章」。
        // [displayRules] 与 [displayFailed] 在方法开头初始化、在章节循环内维护。
        //
        // 投影失败走 [abandonDisplay]（§6.6.6）：立即停止后续章节的 display 投影，并按
        // (book, display) 精确回收**已落盘**行 + 摘掉**待落盘**行，只动 display、不动 original；
        // 覆盖率行同时记为 [SearchCoverageState.FAILED]，不会被误当成完整索引。

        // ========= 覆盖率落库（原文真实结果 + 显示文真实结果） =========
        val run = chapterRun
        val originalState: SearchCoverageState
        val originalReason: String?
        val indexedChapters: Int
        val totalChapters: Int
        when {
            run != null -> {
                // 续建时叠加基线：本轮只统计新增章节，base* 是上一轮已落库的计数。
                // 计数规则集中在纯函数 [resumeChapterCounts]（§6.8）：indexed 累加基线、
                // total 用全书口径且不累加 —— 两者混在一起加过一次，真机上表现为分母虚高
                // 到 3023（全书 1774）、整本建完仍被永久判 PARTIAL。
                val counts = resumeChapterCounts(
                    runIndexed = run.indexedChapters,
                    runTotal = run.totalChapters,
                    baseIndexed = baseIndexed,
                    baseTotal = baseTotal,
                )
                indexedChapters = counts.indexed
                totalChapters = counts.total
                val truncated = run.truncated || baseTruncated
                originalState = if (interrupted) {
                    // 跨窗口让出：后面还有章节没建，诚实记为「部分完成」，绝不伪装成 FULL。
                    SearchCoverageState.PARTIAL
                } else {
                    SearchCoveragePolicy.forChapterIndex(
                        indexedChapters = indexedChapters,
                        totalChapters = totalChapters,
                        truncated = truncated,
                    )
                }
                originalReason = when {
                    interrupted -> SearchCoverageReason.CHAPTERS_SKIPPED
                    originalState == SearchCoverageState.PARTIAL ->
                        if (truncated) SearchCoverageReason.CHAPTER_TRUNCATED
                        else SearchCoverageReason.CHAPTERS_SKIPPED
                    originalState == SearchCoverageState.METADATA_ONLY -> SearchCoverageReason.CHAPTERS_SKIPPED
                    else -> null
                }
            }
            previewIndexed -> {
                indexedChapters = 0
                totalChapters = 0
                originalState = SearchCoverageState.PREVIEW_ONLY
                originalReason = failureReason ?: SearchCoverageReason.PREVIEW_ONLY
            }
            else -> {
                indexedChapters = 0
                totalChapters = 0
                originalState = if (failureReason != null) {
                    SearchCoverageState.FAILED
                } else {
                    SearchCoverageState.METADATA_ONLY
                }
                originalReason = failureReason ?: if (!isTxt && !isEpub) {
                    SearchCoverageReason.UNSUPPORTED_FORMAT
                } else {
                    SearchCoverageReason.NO_BODY_SOURCE
                }
            }
        }

        // 显示文覆盖态：无生效规则 → NOT_APPLICABLE（与原文一致，无需重复建索引）；
        // 规则失效 → FAILED；其余镜像原文完成度（同一组文本单元、同一套分章）。
        val displayState = when {
            displayRules.isEmpty() -> SearchCoverageState.NOT_APPLICABLE
            displayFailed -> SearchCoverageState.FAILED
            else -> originalState
        }
        val displayReason = when {
            displayRules.isEmpty() -> SearchCoverageReason.NO_REPLACE_RULES
            displayFailed -> SearchCoverageReason.DISPLAY_RULE_FAILED
            else -> originalReason
        }
        val displayIndexedChapters = if (displayRules.isEmpty() || displayFailed) 0 else indexedChapters
        val now = System.currentTimeMillis()
        val formatWire = book.format.lowercase()
        val displayBuiltAt = if (displayState == SearchCoverageState.NOT_APPLICABLE || displayFailed) 0L else now
        coverageDao.upsertAll(
            listOf(
                SearchIndexCoverageRow(
                    book_id = book.id,
                    text_basis = SearchTextBasis.ORIGINAL.wire,
                    format = formatWire,
                    coverage = originalState.wire,
                    indexed_chapters = indexedChapters,
                    total_chapters = totalChapters,
                    tokenizer_version = TOKENIZER_VERSION,
                    indexed_at = now,
                    reason = originalReason,
                ),
                SearchIndexCoverageRow(
                    book_id = book.id,
                    text_basis = SearchTextBasis.DISPLAY.wire,
                    format = formatWire,
                    coverage = displayState.wire,
                    indexed_chapters = displayIndexedChapters,
                    total_chapters = totalChapters,
                    tokenizer_version = TOKENIZER_VERSION,
                    indexed_at = displayBuiltAt,
                    reason = displayReason,
                ),
            ),
        )

        if (pending.isNotEmpty()) {
            var k = 0
            while (k < pending.size) {
                val slice = pending.subList(k, (k + UPSERT_BATCH_ROWS).coerceAtMost(pending.size))
                termDao.upsertAll(slice)
                k += UPSERT_BATCH_ROWS
            }
        }
        return BookChapterOutcome(completed = !interrupted, lastChapter = lastChapter)
    }

    /**
     * 显示文通道的**精确**回收（§6.7）：只删 `display` 基准的行，`original` 一行不动。
     *
     * 抽成 internal 方法而不是塞在 `indexOneBook` 的局部函数里，是为了让这条不变量能被
     * JVM 单测直接覆盖 —— 逐章即时投影后「投影失败要把已落盘的半截 display 行收干净」
     * 只靠真机偶发很难验（要复现得先有一条合法规则、再把它改成非法，还得跨窗口续建）。
     *
     * 为什么续建留下的旧 display 行也要一并删：此时该书 display 覆盖率行记为
     * [SearchCoverageState.FAILED]、`indexed_chapters = 0`，即「显示文索引不可用」；
     * 留半截行反而会让搜索命中到不完整且坐标口径不一致的结果，删掉才与覆盖率自洽。
     *
     * @param pending 本批**待落盘**的行，其中的 display 行就地摘除（不落盘自然不会残留）。
     */
    internal suspend fun discardDisplayRows(
        bookId: String,
        pending: MutableList<SearchTermRow>,
    ) {
        val displayWire = SearchTextBasis.DISPLAY.wire
        pending.removeAll { it.text_basis == displayWire }
        runCatching { termDao.deleteByBookAndBasis(bookId, displayWire) }
            .onFailure { e ->
                AppLog.w("SearchIndex", "display 行清理失败，残留将在下次整本重建时回收：${e.message}")
            }
    }

    /**
     * 词项加权聚合：委托给纯函数 [SearchTokenAggregator.aggregateInto]，
     * 原文通道与显示文通道共用同一套权重 / 偏移口径。
     */
    private fun addWeightedTokens(
        agg: HashMap<String, SearchTokenAggregator.AggregatedTerm>,
        tokens: List<SearchTokenizer.TokenHit>,
        weight: Int,
        offsets: Boolean = false,
        fullText: String? = null,
    ) {
        SearchTokenAggregator.aggregateInto(agg, tokens, weight, offsets, fullText)
    }
}
