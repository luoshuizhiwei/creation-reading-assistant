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
import com.creationreadingassistant.feature.search.SearchHitRanker
import com.creationreadingassistant.feature.search.SearchTokenizer
import com.creationreadingassistant.feature.search.TxtChapterSlice
import com.creationreadingassistant.feature.search.TxtChapterSplitter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
 * 显式全量重建（[SearchIndexRepository.triggerFullRebuildNow]）的实际结果。
 *
 * 反馈必须如实：取消与重置/排队的先后关系不同，用户看到的结果也不同 ——
 * 「已重置并排队」「取消先到、断点保留」「已重置但未排队」是三件不同的事，
 * 不能一律说成「已重置索引进度」（v3 缺陷 B）。
 */
enum class FullRebuildLaunch {
    /** 游标已重置到起点，且已请求后台重建（正常显式重建）。 */
    RESET_QUEUED,

    /** 取消先于重置到达：游标**没有**被动（断点保留），也没有重新排队。 */
    SKIPPED_CANCELLED,

    /** 重置已提交，但排队之前又被取消：游标已重置（不得声称断点保留），但**没有**排队。 */
    RESET_NOT_QUEUED,
}

/**
 * [SearchIndexRepository.cancelIndexing] 的结果：如实区分三种取消，供 UI 反馈。
 */
data class CancelIndexingResult(
    /** 取消前确有构建在跑（持有执行权）。 */
    val wasRunning: Boolean,
    /** 取消前确有已排队 / 等待重试的构建。 */
    val wasQueued: Boolean,
    /**
     * 扫描断点（游标）是否已确认保留。重置提交中或已重置时为 false；
     * 提交中必须优先按 [resetPending] 反馈，不承诺保留或已清除。
     */
    val anchorPreserved: Boolean,
    /** 重置已取得提交资格，但 DAO 尚未返回；断点以提交结果为准。 */
    val resetPending: Boolean = false,
) {
    /** 是否确实停止/撤下了什么：false 表示本来就没有构建，不得谎报「已停止」。 */
    val stoppedSomething: Boolean get() = wasRunning || wasQueued
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
    private val scheduler: SearchIndexScheduler? = null,
) {

    data class Progress(
        val indexedBooks: Long,
        val totalBooks: Int,
        val isRunning: Boolean,
        val totalTerms: Long = 0L,
        /**
         * 已请求后台扫描但**尚未取得执行权**（WorkManager 排队中 / 预算耗尽等待重试）。
         *
         * 与 [isRunning] 是两种不同状态：排队时并没有任何协程在扫书，但用户看得见
         * 「还有一次构建在等」，且**必须**能取消它 —— 只在 [isRunning] 时给停止入口
         * 会让排队/等待重试阶段的构建无法被取消（CRA-…-v3 缺陷 C）。
         */
        val isQueued: Boolean = false,
    ) {
        /** 是否可以取消：在跑、或已排队等待，两者都必须给用户停止入口。 */
        val canCancel: Boolean get() = isRunning || isQueued
    }

    private val _progress = MutableStateFlow(Progress(0L, 0, false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    /** 增量全库扫描与全量重建的互斥锁（S03-1） */
    private val sweepLock = Mutex()

    /** 全量重建串行化互斥锁，确保多次并发重建严格串行化执行（S03-1） */
    private val rebuildLock = Mutex()

    /**
     * 全库扫描与重建的统一并发协调状态（S03-1 统一原子协议）。
     * 将代次（generation）、活跃任务所有权（activeJobId）与重建接管标记（isRebuilding）
     * 封装为一个不可变状态，通过 AtomicReference 的原子 CAS 驱动，消除「占权」与「读代次」之间的时序撕裂。
     */
    private data class SweepCoordinationState(
        val generation: Long,
        val activeJobId: Long?,
        val isRebuilding: Boolean,
    )

    private val coordinationState = AtomicReference(
        SweepCoordinationState(generation = 1L, activeJobId = null, isRebuilding = false)
    )

    /** 递增的任务 ID 发生器 */
    private val jobIdSequence = AtomicLong(1L)
    /** 活跃的单本书即时构建任务计数 */
    private val singleBookRunningCount = AtomicInteger(0)

    /**
     * 取消归属（§6.9-v3）：取消事实挂在**具体执行**上，不再用全局共享布尔标志。
     *
     * v2 的共享 `AtomicBoolean` 有个致命竞态：[cancelIndexing] 置 true 后，任何**新**占权的
     * 扫描都会把它清成 false；被取消的旧扫描恢复后读到 false，就不再被认成取消 ——
     * 取消事实被新任务擦掉，旧扫描退回 `indexSweepResult` 的判定（PROGRESSED / STALLED），
     * 于是 Worker 要么 `Result.retry()` 立刻重试，要么误报「单本超窗」后 `Result.failure()`。
     *
     * 现在一次取消记录两件事，缺一不可：
     * - [cancelledJobIds]：取消当时**持有执行权**的那个具体 job（在跑的扫描或重建）。
     *   只有它自己结束时会读到「我被取消了」，新任务占权不会改写这条事实；
     * - [cancelEpoch]：单调递增的取消计数，覆盖「已排队、尚未占权」的执行 ——
     *   一次扫描在占权时快照它，结束时只和**自己的快照**比，天然区分「取消发生在我之前」与「之后」。
     */
    private val cancelledJobIds: MutableSet<Long> =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    private val cancelEpoch = AtomicLong(0L)

    /**
     * 取消与「真正动作」之间的**串行门槛**（v4）。
     *
     * 存在理由：光有 [cancelEpoch] 不够 —— 「读一次 epoch」与「真的去做」之间只要还有
     * 挂起点（等锁、挂起 DAO），取消就能插进缝隙。于是出现两种复活：
     * - 清断点：检查通过后去等 `sweepLock`，取消在此期间到达 ⇒ 断点照样被清；
     * - 入队：检查通过后挂起 `bookDao.countActive()`，取消在此期间先 `cancelUniqueWork`，
     *   旧请求随后又 `triggerNow` ⇒ 刚取消的重建被重新排上队。
     *
     * 因此每个不可逆动作都必须把**检查与动作放进同一个临界区**：
     * - 清断点的线性化点 = 取得 `sweepLock`（提交资格）后在**锁内**再校验一次；
     * - 入队的线性化点 = 取得本门槛后在**门槛内**再校验一次再 `triggerNow`。
     *
     * 门槛内只做非挂起操作（一次 `AtomicLong` 读 + 一次 WorkManager 入队调用），
     * 挂起的 DAO 一律放在门槛之外 —— 不制造「锁内挂起」的死锁面。
     */
    private val cancelGate = Mutex()

    /** 断点状态以一次原子快照发布，提交中不能承诺已保留或已清除。 */
    private enum class AnchorState { PRESERVED, RESET_PENDING, RESET_COMMITTED }
    private val anchorState = AtomicReference(AnchorState.PRESERVED)

    /** 一次执行是否已被用户取消：[jobId] 被点名，或快照之后又发生了新的取消。 */
    private fun isCancelRequestedFor(jobId: Long, epochAtStart: Long): Boolean =
        cancelledJobIds.contains(jobId) || cancelEpoch.get() != epochAtStart

    /** 忘记某次执行的取消事实（执行已结束，事实不再需要保留，避免集合无界增长）。 */
    private fun forgetCancel(jobId: Long) {
        cancelledJobIds.remove(jobId)
    }

    /** 仅供确定性并发测试注入的无锁协作拦截点（在短临界区完全释放后触发） */
    internal var testHookAfterStep: (suspend (step: String) -> Unit)? = null

    /** 仅供确定性并发测试注入的无锁写前拦截点（在短临界区之前调用） */
    internal var testHookBeforeWrite: (suspend (point: String, bookId: String) -> Unit)? = null

    /** 仅供确定性并发测试注入的无锁拦截点：扫描已占权、尚未读代次与执行后续工作 */
    internal var testHookAfterAcquireOwnership: (suspend () -> Unit)? = null

    /** 仅供确定性并发测试注入的无锁拦截点：重建已占权、尚未获取锁重置状态 */
    internal var testHookBeforeRebuildReset: (suspend (rebuildJobId: Long) -> Unit)? = null

    /** 仅供确定性并发测试注入的无锁拦截点：重建游标重置**已提交**、尚未重新排队 */
    internal var testHookAfterRebuildReset: (suspend (rebuildJobId: Long) -> Unit)? = null

    internal fun getCurrentGeneration(): Long = coordinationState.get().generation

    /**
     * 原子取得全库扫描执行权与当前代次（S03-1 统一原子协议）。
     * 返回 Pair(myJobId, generation)；若已有任务在跑或正在全量重建则返回 null。
     */
    private fun tryAcquireIncrementalSweep(): Pair<Long, Long>? {
        val myJobId = jobIdSequence.incrementAndGet()
        while (true) {
            val current = coordinationState.get()
            if (current.activeJobId != null || current.isRebuilding) {
                return null
            }
            val next = current.copy(activeJobId = myJobId)
            if (coordinationState.compareAndSet(current, next)) {
                return Pair(myJobId, next.generation)
            }
        }
    }

    /**
     * 校验当前任务是否仍合法持有扫描所有权且代次未失效。
     */
    private fun isSweepActiveAndValid(myJobId: Long, myGeneration: Long): Boolean {
        val current = coordinationState.get()
        return current.activeJobId == myJobId && current.generation == myGeneration && !current.isRebuilding
    }

    /**
     * 安全归还全库扫描执行权（防范抢占误释放）。
     */
    private fun releaseSweepOwnership(expectedJobId: Long): Boolean {
        while (true) {
            val current = coordinationState.get()
            if (current.activeJobId == expectedJobId) {
                val next = current.copy(activeJobId = null)
                if (coordinationState.compareAndSet(current, next)) {
                    return true
                }
            } else {
                return false
            }
        }
    }

    private fun beginRebuild(rebuildJobId: Long): Long {
        while (true) {
            val current = coordinationState.get()
            val oldGen = current.generation
            val next = SweepCoordinationState(
                generation = oldGen + 1,
                activeJobId = rebuildJobId,
                isRebuilding = true,
            )
            if (coordinationState.compareAndSet(current, next)) {
                return oldGen
            }
        }
    }

    private fun rollbackRebuild(rebuildJobId: Long, rollbackGen: Long) {
        while (true) {
            val current = coordinationState.get()
            if (current.activeJobId == rebuildJobId) {
                val next = SweepCoordinationState(
                    // §6.9 回滚不得恢复旧 generation：cancel 可能在 rebuild 期间推进过代次，
                    // 恢复旧值会让被取消的旧扫描通过代次校验 → 迟到写入复活。
                    // 保持当前代次，只清重建标记与活跃任务。
                    generation = current.generation,
                    activeJobId = null,
                    isRebuilding = false,
                )
                if (coordinationState.compareAndSet(current, next)) {
                    return
                }
            } else {
                return
            }
        }
    }

    private fun completeRebuild(rebuildJobId: Long) {
        while (true) {
            val current = coordinationState.get()
            if (current.activeJobId == rebuildJobId) {
                val next = current.copy(activeJobId = null, isRebuilding = false)
                if (coordinationState.compareAndSet(current, next)) {
                    return
                }
            } else {
                return
            }
        }
    }

    /**
     * 让**当前在跑的扫描任务立即失效**并归还执行权（用户取消专用）。
     *
     * 与 [beginRebuild] 共用「接管 + 推进代次」的同一套原子协议，但语义不同：
     * 本方法只推进代次并清空活跃任务，**不置 `isRebuilding`**，也**不触碰**任何
     * 游标、章节断点或索引行。在跑任务此后调用 [isSweepActiveAndValid] 一律为
     * false，其后续写入被 [runIfCurrentGeneration] 拒绝 —— 因此「取消后立刻发起
     * 的新任务」不会被旧任务的迟到写入覆盖。
     *
     * 全量重建正在重置游标时（`isRebuilding == true`）**不抢它的执行权**，
     * 只推进代次让旧扫描失效，避免把重建的活跃任务清掉后 `isRebuilding` 悬挂。
     */
    private fun invalidateActiveSweep() {
        while (true) {
            val current = coordinationState.get()
            val next = if (current.isRebuilding) {
                current.copy(generation = current.generation + 1)
            } else {
                SweepCoordinationState(
                    generation = current.generation + 1,
                    activeJobId = null,
                    isRebuilding = false,
                )
            }
            if (coordinationState.compareAndSet(current, next)) {
                return
            }
        }
    }

    private fun isAnyIndexingRunning(): Boolean {
        val current = coordinationState.get()
        return current.activeJobId != null || current.isRebuilding || singleBookRunningCount.get() > 0
    }

    private fun updateProgressRunningState(indexedBooks: Long? = null, totalBooks: Int? = null) {
        val cur = _progress.value
        _progress.value = Progress(
            indexedBooks = indexedBooks ?: cur.indexedBooks,
            totalBooks = totalBooks ?: cur.totalBooks,
            isRunning = isAnyIndexingRunning(),
            totalTerms = cur.totalTerms,
            isQueued = cur.isQueued,
        )
    }

    /** 记为「已请求、尚未取得执行权」：worker 会重新入队，用户必须看得到并能取消。 */
    private fun markQueued() {
        val cur = _progress.value
        if (!cur.isQueued) _progress.value = cur.copy(isQueued = true)
    }

    /** 清除排队标记：已真正开跑、已跑完，或已被取消。 */
    private fun clearQueued() {
        val cur = _progress.value
        if (cur.isQueued) _progress.value = cur.copy(isQueued = false)
    }

    /**
     * 仅当指定代次仍等于当前活跃代次时，才在短临界区内执行给定的挂起操作。
     * 若代次已作废，直接返回 false，绝不执行任何 DAO 调用。
     *
     * @param expectedGeneration 全库扫描传入的任务代次；若为 null 则表示不受代次约束（如单本独立索引）
     */
    private suspend inline fun <T> runIfCurrentGeneration(
        expectedGeneration: Long?,
        crossinline action: suspend () -> T,
    ): Boolean {
        if (expectedGeneration == null) {
            action()
            return true
        }
        return sweepLock.withLock {
            if (expectedGeneration == coordinationState.get().generation) {
                action()
                true
            } else {
                false
            }
        }
    }

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

    /**
     * 用 [TxtChapterDetector] 把纯文本正文按逻辑章切分。
     * 委托给纯算法组件 [TxtChapterSplitter.split]，确保纯粹字符串分章与边界保护。
     */
    private fun splitTxtIntoChapters(fullText: String, tocRuleId: String?): List<TxtChapterSlice> =
        TxtChapterSplitter.split(fullText, tocRuleId, MAX_CHARS_PER_CHAPTER)

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
        // 原子取得全库扫描执行权与当前代次（S03-1 统一原子协议）
        val acquired = tryAcquireIncrementalSweep() ?: run {
            // 执行权在别人手上（在跑的扫描 / 正在重置的重建）：本次**没有**执行，
            // worker 会重新入队 ⇒ 如实记为「已排队」，用户才看得到也取消得掉。
            markQueued()
            return IndexSweepResult.PROGRESSED
        }
        val (myJobId, myGeneration) = acquired
        // 取消归属快照：本轮只认**自己开始之后**发生的取消。取消发生在本次占权之前时
        // 属于上一次执行的事实，绝不能让「取消后重新触发的续建」替旧执行背取消。
        val cancelEpochAtStart = cancelEpoch.get()
        clearQueued() // 已真正取得执行权，不再是「排队中」

        return try {
            testHookAfterAcquireOwnership?.invoke()
            if (!isSweepActiveAndValid(myJobId, myGeneration)) {
                return stopOrWaitRetry(myJobId, cancelEpochAtStart)
            }
            val total = bookDao.countActive()
            if (total == 0) {
                clearQueued()
                updateProgressRunningState(indexedBooks = 0L, totalBooks = 0)
                return IndexSweepResult.COMPLETED
            }
            val cur = stateDao.get()
            if (!isSweepActiveAndValid(myJobId, myGeneration)) {
                return stopOrWaitRetry(myJobId, cancelEpochAtStart)
            }
            // tokenizer 版本变更 → 先清旧索引再从头扫
            if (cur != null && cur.tokenizer_version != TOKENIZER_VERSION) {
                termDao.countIndexedBooks().let { n ->
                    if (n > 0) wipeAllTermsForAllBooks(myGeneration)
                }
            }
            if (!isSweepActiveAndValid(myJobId, myGeneration)) {
                return stopOrWaitRetry(myJobId, cancelEpochAtStart)
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
            updateProgressRunningState(indexedBooks = countIndexedBooksCheap(), totalBooks = total)
            val outcome = processChunked(snapshot, startIdx, snapshot.size, deadlineMs, resumeChapter, myGeneration)
            when {
                // §6.9-v3 取消归属：只有**本轮**被用户取消才优雅停止（COMPLETED：Worker 既不
                // 重试也不记失败）。新任务占权不会改写这条事实 —— 旧扫描的取消不会被擦掉。
                isCancelRequestedFor(myJobId, cancelEpochAtStart) -> {
                    clearQueued()
                    IndexSweepResult.COMPLETED
                }
                else -> {
                    val result = indexSweepResult(
                        reached = outcome.reached,
                        from = startIdx,
                        until = snapshot.size,
                        advancedWithinBook = outcome.advancedWithinBook,
                    )
                    // 预算耗尽确有推进 → PROGRESSED：Worker 正常重试续建（不因取消机制被误伤），
                    // 同时如实标记「等待重试」；STALLED 则停止自动重试，不留排队标记。
                    if (result == IndexSweepResult.PROGRESSED) markQueued() else clearQueued()
                    result
                }
            }
        } finally {
            // 本轮执行结束，取消事实随之作废（集合不无界增长）
            forgetCancel(myJobId)
            // 安全归还执行权：若当前仍是自己持有，归还并刷新 running 状态；
            // 若已被重建或新作业替换，绝不把全局 isRunning 刷成 false（S03-1）
            releaseSweepOwnership(myJobId)
            updateProgressRunningState()
        }
    }

    /**
     * 扫描中途失去执行权时的处置（取消归属：只问**自己**是否被取消）。
     *
     * - 被用户取消 → [IndexSweepResult.COMPLETED]：优雅停止。Worker 既不 `Result.retry()`，
     *   也不 `Result.failure()` —— 被取消的任务不得因为「reached > from」被当成预算推进而重试；
     * - 其他原因（被重建接管、被更新的扫描替换）→ [IndexSweepResult.PROGRESSED]：等下个窗口续建。
     */
    private fun stopOrWaitRetry(myJobId: Long, epochAtStart: Long): IndexSweepResult {
        val cancelled = isCancelRequestedFor(myJobId, epochAtStart)
        if (cancelled) clearQueued() else markQueued()
        return if (cancelled) IndexSweepResult.COMPLETED else IndexSweepResult.PROGRESSED
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
     * 删除单本书的全文索引（term 行 + coverage 行），供书籍删除链路清理孤儿行。
     *
     * 与 [indexOneBook] 内的 fresh 路径（先删该书行再重建）同一组 DAO 调用，区别是这里
     * 只删不清、不触发重建 —— 书都删了，内容不要了，重建没有意义。调用方（[BookRepository]）
     * 负责在事务内与软删除一并提交，保证原子性。
     */
    suspend fun deleteIndexForBook(bookId: String) {
        termDao.deleteByBook(bookId)
        coverageDao.deleteByBook(bookId)
    }

    /**
     * 影响全库的规则变更（GLOBAL 作用域）置脏：把增量扫描游标重置到起点，
     * 使下一次 worker 扫描**从第 0 本重建全库** —— 惰性、不阻塞调用方。
     *
     * 游标只前进（见 [ensureIndexedIncremental]），因此「让已建过的书被重扫」唯一途径
     * 就是重置它；重建由后台 [SearchIndexWorker] 跨多个作业窗口完成。
     */
    suspend fun invalidateSweepForFullRebuild(): Boolean =
        invalidateSweepForFullRebuildFrom(cancelEpoch.get())

    /**
     * [invalidateSweepForFullRebuild] 的实现体，[cancelEpochAtStart] 是**本次重建请求起点**
     * 的取消归属快照。
     *
     * 为什么必须由调用方把请求起点的快照传进来（CRA-SEARCH-REBUILD-CANCEL-EPOCH-FIX-v1）：
     * 取消归属判据是「本次请求之后发生的取消才算数」。若在**取得重置资格的那一刻**才去读
     * [cancelEpoch]，则请求起点到这一刻之间发生的取消会被漏掉 —— 而这个区间里有两个真实挂起窗口：
     *   · [triggerFullRebuildNow] 在请求起点之后先挂起 `purgeOrphanIndexRows()`；
     *   · 第二次重建请求可能一直排在 `rebuildLock` 上等前一次重建。
     * 两处的取消都会在「重新取快照」时被洗掉，于是旧请求照样清断点。
     * 直接调用本函数（如 `ReaderSearchIndexCoordinator`）时请求起点就是调用起点，由
     * [invalidateSweepForFullRebuild] 就地取快照，语义与调用方传入一致。
     */
    private suspend fun invalidateSweepForFullRebuildFrom(cancelEpochAtStart: Long): Boolean {
        return rebuildLock.withLock {
            val rebuildJobId = jobIdSequence.incrementAndGet()
            // 接管全库执行权与推进代次，阻止任何新扫描在重置完成前启动并读取旧游标
            val oldGen = beginRebuild(rebuildJobId)
            var committed = false
            try {
                testHookBeforeRebuildReset?.invoke(rebuildJobId)
                // ── 线性化点（v4）──────────────────────────────────────────────
                // 校验与「清断点」必须在**同一个**临界区（sweepLock）内完成。
                // v3 把校验放在取锁之前：校验通过后去等锁的这段时间里，取消可以先到达，
                // 但取到锁之后仍会照样清断点 —— 检查与动作之间有缝。
                // 现在取到锁（= 取得提交资格）后再校验一次：
                //   · 取消的 epoch 自增发生在本次校验之前 ⇒ 取消赢，断点保留；
                //   · 发生在之后 ⇒ 重置已取得提交资格，DAO 返回前只报告「提交中」。
                sweepLock.withLock {
                    val previousAnchor = cancelGate.withLock {
                        if (isCancelRequestedFor(rebuildJobId, cancelEpochAtStart)) null
                        else anchorState.getAndSet(AnchorState.RESET_PENDING)
                    } ?: return@withLock
                    try {
                        stateDao.set(
                            SearchIndexStateRow(
                                tokenizer_version = TOKENIZER_VERSION,
                                last_scanned_book_id = null,
                                last_scanned_chapter_index = 0,
                                built_at = 0,
                            ),
                        )
                        committed = true
                        anchorState.set(AnchorState.RESET_COMMITTED)
                    } finally {
                        if (!committed) anchorState.set(previousAnchor)
                    }
                }
                if (!committed) {
                    // 取消先取得资格 ⇒ 不得清断点：回滚接管，如实返回「未提交」。
                    // （回滚放在 sweepLock 之外，门槛内不做任何挂起操作。）
                    rollbackRebuild(rebuildJobId, oldGen)
                    forgetCancel(rebuildJobId)
                    updateProgressRunningState()
                    return@withLock false
                }
                testHookAfterRebuildReset?.invoke(rebuildJobId)
            } catch (e: Throwable) {
                // 失败恢复策略：回滚代次并释放接管锁，防止系统进入「新代次 + 旧游标」腐化状态。
                // 保留 v2 的修复：回滚**不恢复旧 generation**，被取消的旧扫描不得因此复活。
                rollbackRebuild(rebuildJobId, oldGen)
                forgetCancel(rebuildJobId)
                updateProgressRunningState()
                throw e
            }
            // 重置完成，释放执行权供后续新扫描使用
            completeRebuild(rebuildJobId)
            forgetCancel(rebuildJobId)
            updateProgressRunningState()
            committed
        }
    }

    suspend fun countIndexedBooks(): Long = termDao.countIndexedBooks()

    /**
     * 重置增量扫描游标，回收孤儿索引行，并通知调度器立即唤起后台 Worker 全量重建索引。
     *
     * 两个「重建索引」用户入口都收敛到这里：搜索结果页的索引状态胶囊
     * （`SearchViewModel.triggerRebuildIndex`）与「存储 / 日志与诊断」页的按钮
     * （`ProfileViewModel.rebuildSearchIndex`）。
     */
    suspend fun triggerFullRebuildNow(overrideScheduler: SearchIndexScheduler? = null): FullRebuildLaunch {
        // 取消归属快照：本次重建**请求之后**发生的取消才算数（请求之前那次取消属于上一次执行）。
        // 这个快照必须一路带到真正的重置资格检查 —— 中间隔着 purgeOrphanIndexRows 的多次挂起
        // 与可能的 rebuildLock 排队，在那里重新取快照会把窗口内的取消洗掉
        //（CRA-SEARCH-REBUILD-CANCEL-EPOCH-FIX-v1）。
        val cancelEpochAtStart = cancelEpoch.get()
        purgeOrphanIndexRows()
        if (!invalidateSweepForFullRebuildFrom(cancelEpochAtStart)) {
            // 取消先于本次重置资格：本次请求没有清断点，也不得排队复活刚被取消的请求。
            // 但断点可能已由**更早**的重置清除（anchor 仍是 RESET_COMMITTED）—— 此时如实报
            // 「重置已提交但未排队」，不得声称断点原样保留。
            clearQueued()
            updateProgressRunningState()
            return if (anchorState.get() == AnchorState.RESET_COMMITTED) {
                FullRebuildLaunch.RESET_NOT_QUEUED
            } else {
                FullRebuildLaunch.SKIPPED_CANCELLED
            }
        }
        // 挂起的 DAO 读取放在门槛**之外**（门槛内不做挂起操作）
        val total = bookDao.countActive()
        // ── 线性化点（v4）──────────────────────────────────────────────────
        // 校验与「真正入队」必须在**同一个**临界区（cancelGate）内完成，且两者之间
        // 不再有任何挂起点。v3 把校验放在挂起的 `bookDao.countActive()` 之前：
        // 取消可以在 countActive 挂起期间先跑完 `cancelUniqueWork`，随后旧请求仍会
        // `triggerNow` 重新排队 —— 刚被取消的重建被复活。
        // 现在：取到门槛后再校验一次，不成立就直接返回，绝不入队。
        //   · 取消先取得资格 ⇒ RESET_NOT_QUEUED（游标已重置是既成事实，如实报告）；
        //   · 入队先取得资格 ⇒ RESET_QUEUED，用户随后取消仍走 `cancelPendingSweep()`。
        val queued = cancelGate.withLock {
            if (cancelEpoch.get() != cancelEpochAtStart) {
                false
            } else {
                _progress.value = Progress(
                    0L, total, isRunning = isAnyIndexingRunning(), totalTerms = 0L, isQueued = true,
                )
                (overrideScheduler ?: scheduler)?.triggerNow(force = true)
                true
            }
        }
        if (!queued) {
            clearQueued()
            updateProgressRunningState()
            return FullRebuildLaunch.RESET_NOT_QUEUED
        }
        return FullRebuildLaunch.RESET_QUEUED
    }

    /**
     * 用户显式取消后台索引构建（S1「可取消、续建」合同，
     * CRA-SEARCH-INDEX-CANCEL-CONTROL-v1）。
     *
     * 语义边界（逐条对应 S1 验收要求）：
     * 1. **只取消在跑 / 排队的一次性扫描**，并让在跑任务立即失效（推进代次）；
     * 2. **不把取消说成完成**：本方法不写任何完成标记，`progress.isRunning` 按真实
     *    协调状态重算（取消后为 false），已建/未建仍按实际覆盖度呈现；
     * 3. **保留合法已提交章节与断点**：不读写 `search_index_state`（`last_scanned_book_id`
     *    / `last_scanned_chapter_index` 原样保留），也不删除任何 `search_terms` /
     *    `search_index_coverage` 行；
     * 4. **后续续建走既有语义**：下一次 [ensureIndexedIncremental]（冷启动 / 周期任务 /
     *    [enqueueOnce]）从保留的锚点继续，**不是**清空后重建；
     * 5. **显式全量重建保持原语义**：取消不改变 [triggerFullRebuildNow] 的行为 ——
     *    它仍然会重置游标并从第 0 本重扫；
     * 6. **重试与取消竞态**：代次已推进 ⇒ 被取消任务的迟到写入一律被拒，
     *    新任务（续建或重建）不会被旧任务覆盖。
     *
     * 7. **取消归属具体执行（v3）**：取消事实挂在取消当时持有执行权的 job 上，并推进
     *    [cancelEpoch] 覆盖「已排队尚未占权」的执行。新任务占权**不会**擦掉旧任务的
     *    取消事实 —— v2 的共享布尔标志会被新扫描清零，导致被取消的旧扫描恢复后退回
     *    `PROGRESSED` / `STALLED` 判定，Worker 于是重试或误报失败。
     *
     * @return [CancelIndexingResult]（供 UI **如实**反馈：区分在跑 / 排队 / 本来就没事，
     *         以及断点是否仍然保留，不夸大为「已完成」）。
     */
    suspend fun cancelIndexing(): CancelIndexingResult {
        val wasRunning = isAnyIndexingRunning()
        val wasQueued = _progress.value.isQueued
        // 取消的**线性化点**：整段「点名 job + 推进 epoch + 作废执行权 + 撤下 WorkManager
        // 排队」都在 [cancelGate] 内一次做完，与「入队」动作互斥 —— 于是
        // 「先 cancelUniqueWork、随后旧请求又 enqueue」这类复活不再可能发生。
        // 门槛内只有非挂起操作，不会与任何锁形成死锁面。
        cancelGate.withLock {
            // 取消归属：点名取消「取消当时持有执行权的那个 job」，再推进 epoch 覆盖排队执行。
            // 顺序要紧：必须先读到 jobId，[invalidateActiveSweep] 之后就取不到了。
            val activeJobId = coordinationState.get().activeJobId
            if (activeJobId != null) cancelledJobIds.add(activeJobId)
            cancelEpoch.incrementAndGet()
            invalidateActiveSweep()
            scheduler?.cancelPendingSweep()
        }
        clearQueued()
        updateProgressRunningState()
        val anchorSnapshot = anchorState.get()
        val result = CancelIndexingResult(
            wasRunning = wasRunning,
            wasQueued = wasQueued,
            anchorPreserved = anchorSnapshot == AnchorState.PRESERVED,
            resetPending = anchorSnapshot == AnchorState.RESET_PENDING,
        )
        AppLog.event(
            "SearchIndex",
            "用户取消后台索引构建（在跑=$wasRunning，排队=$wasQueued，断点保留=${result.anchorPreserved}，重置提交中=${result.resetPending}）；" +
                "已提交章节保留，下次按当前锚点继续或从头重扫",
        )
        return result
    }

    /**
     * 回收「孤儿索引行」：`search_terms` / `search_index_coverage` 里出现的 book_id
     * 已不在活跃书集合。
     *
     * 为什么需要它：写入侧（[com.creationreadingassistant.data.repository.BookRepository]
     * 的删除链路 → [deleteIndexForBook]）只保证**今后**删除的书不再留孤儿行。修复上线前
     * 已删的书留下的行不会自行消失 —— [ensureIndexedIncremental] 只遍历活跃书，永远回访
     * 不到它们；[wipeAllTermsForAllBooks] 也帮不上忙 —— 它只在 tokenizer 版本变更时
     * 才被调用（见 [ensureIndexedIncremental]），跟「书被删掉」这件事无关。
     * ⇒ 这些行此前**没有任何用户可达的回收路径**（真机实测残留 13,953 行）。
     *
     * 挂在 [triggerFullRebuildNow] 里而不是每次后台扫描开头：孤儿清理只需在「确实删过书」
     * 之后跑，而 book_id 枚举在千万行表上是一次索引扫描 —— 不该变成每次扫描的常态成本。
     *
     * 注意：删行**不会**缩小 DB 文件（释放的页进 freelist，不还给 OS），所以本方法的收益是
     * 「数据卫生 + 与读取侧过滤互为双保险」，不是省空间。
     *
     * @return 实际回收的**书籍数**（不是删除的数据库行数）；0 表示没有孤儿。
     */
    internal suspend fun purgeOrphanIndexRows(): Int {
        // 候选必须是两张表 book_id 的**并集**：只扫 search_terms 会漏掉「term 已空、
        // 只剩 coverage 行」的已删书，那类孤儿 coverage 会永久残留。
        val indexed = (termDao.distinctBookIds() + coverageDao.distinctBookIds()).toHashSet()
        if (indexed.isEmpty()) return 0
        val active = bookDao.activeIds().toHashSet()
        val orphans = indexed.filterNot { it in active }
        if (orphans.isEmpty()) return 0
        for (bookId in orphans) {
            termDao.deleteByBook(bookId)
            coverageDao.deleteByBook(bookId)
        }
        AppLog.i(
            "SearchIndex",
            "已回收 ${orphans.size} 本已删书的残留索引行：${orphans.joinToString { it.take(8) }}",
        )
        return orphans.size
    }

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
        singleBookRunningCount.incrementAndGet()
        var total: Int? = null
        var finalCount: Long? = null
        try {
            total = bookDao.countActive().coerceAtLeast(1)
            updateProgressRunningState(indexedBooks = countIndexedBooksCheap(), totalBooks = total)
            indexOneBook(
                book = book,
                startChapter = 0, // 导入成功后立即建索引：整本重建，不接续建断点
                deadlineMs = System.currentTimeMillis() + INDEX_BUILD_BUDGET_MS,
                generation = null, // 单本独立构建不受全库增量扫描代次约束
            )
            finalCount = countIndexedBooksCheap()
        } finally {
            singleBookRunningCount.decrementAndGet()
            updateProgressRunningState(indexedBooks = finalCount, totalBooks = total)
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

        // M-BUG-10 读取侧防御：命中必须属于**活跃书**，否则一律丢弃。
        //
        // 为什么光靠写入侧不够：`deleteIndexForBook` 只保证「今后」删除的书不再留孤儿行。
        // 修复上线前已删的书（真机实测 `cra-update-fixture` 仍占 13,953 行）不会自行消失 ——
        // `ensureIndexedIncremental` 只遍历活跃书，永远回访不到它们。这些残留行会命中，
        // 而 `BookDao.getByIds` 带 `deleted_at IS NULL` 拿不到书名 ⇒ 结果行退化成裸 bookId
        // （真机复现：搜 `cra` 出现标题为 `b6dd8663-…` 的结果行）。
        //
        // 过滤放在 groupBy 之前：这样 `limit` 截断的是「活跃书命中」而非被残留行挤占的名额。
        val activeBookIds = bookDao.activeIds().toHashSet()
        val coverageByKey = coverageDao.all().associateBy { row -> row.book_id to row.text_basis }
        val ranked = SearchHitRanker.rank(
            rawMatches = raw,
            activeBookIds = activeBookIds,
            coverageProvider = { bookId, basisWire ->
                coverageByKey[bookId to basisWire]?.let {
                    SearchCoverageState.fromWire(it.coverage) to it.reason
                }
            },
            limit = limit,
        )
        ranked
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

    private suspend fun wipeAllTermsForAllBooks(generation: Long?) {
        testHookBeforeWrite?.invoke("wipe_all_terms", "")
        runIfCurrentGeneration(generation) {
            val allTermBooks = distinctBookIdsInTerms()
            for (bid in allTermBooks) termDao.deleteByBook(bid)
            // 覆盖状态与分词结果同生共死：只清 search_terms 不清 coverage，
            // 会让「重建未完成索引」误判已完成。这里直接整表清掉，重建时逐本重写。
            coverageDao.deleteAll()
        }
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
        generation: Long? = null,
    ): SweepOutcome {
        var i = from
        var advancedWithinBook = false
        while (i < until) {
            if (System.currentTimeMillis() >= deadlineMs) break
            if (generation != null && generation != coordinationState.get().generation) break
            val book = books[i]
            // 只有本轮的第一本书可能需要从章节断点续建；其余都从 0（整本）开始。
            val startChapter = if (i == from) firstBookStartChapter else 0
            val outcome: BookChapterOutcome? = try {
                indexOneBook(book, startChapter, deadlineMs, generation = generation)
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
            testHookAfterStep?.invoke("after_index_one_book_${book.id}")
            if (generation != null && generation != coordinationState.get().generation) break

            when {
                outcome == null -> {
                    // 失败：仍落锚点（chapter=0 视为该书本轮结束），受代次保护
                    testHookBeforeWrite?.invoke("cursor", book.id)
                    val updated = runIfCurrentGeneration(generation) {
                        stateDao.set(
                            SearchIndexStateRow(
                                tokenizer_version = TOKENIZER_VERSION,
                                last_scanned_book_id = book.id,
                                last_scanned_chapter_index = 0,
                                built_at = System.currentTimeMillis(),
                            ),
                        )
                        anchorState.set(AnchorState.PRESERVED)
                    }
                    if (!updated) break
                    i++
                }
                !outcome.completed -> {
                    // 章节级续建（§6.1）：受代次保护
                    testHookBeforeWrite?.invoke("cursor", book.id)
                    val updated = runIfCurrentGeneration(generation) {
                        stateDao.set(
                            SearchIndexStateRow(
                                tokenizer_version = TOKENIZER_VERSION,
                                last_scanned_book_id = book.id,
                                last_scanned_chapter_index = outcome.lastChapter,
                                built_at = System.currentTimeMillis(),
                            ),
                        )
                        anchorState.set(AnchorState.PRESERVED)
                    }
                    if (!updated) break
                    advancedWithinBook = outcome.lastChapter > startChapter
                    _progress.value = _progress.value.copy(indexedBooks = countIndexedBooksCheap())
                    break
                }
                else -> {
                    // 整本完成：受代次保护
                    testHookBeforeWrite?.invoke("cursor", book.id)
                    val updated = runIfCurrentGeneration(generation) {
                        stateDao.set(
                            SearchIndexStateRow(
                                tokenizer_version = TOKENIZER_VERSION,
                                last_scanned_book_id = book.id,
                                last_scanned_chapter_index = 0,
                                built_at = System.currentTimeMillis(),
                            ),
                        )
                        anchorState.set(AnchorState.PRESERVED)
                    }
                    if (!updated) break
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
        generation: Long? = null,
    ): BookChapterOutcome {
        // 章节级续建（§6.1）：startChapter > 0 表示上一窗口只建到该章，本次从它的下一章继续。
        // 此时**绝不能整删** —— 旧实现「先整删后重写」意味着单本超窗就整本白做，下次重来
        // 依然超窗，形成「这本书永远建不完」的死局（真机 #22 大 EPUB 即如此）。
        val fresh = startChapter <= 0
        if (fresh) {
            testHookBeforeWrite?.invoke("initial_delete", book.id)
            val deleted = runIfCurrentGeneration(generation) {
                termDao.deleteByBook(book.id)
                coverageDao.deleteByBook(book.id)
            }
            if (!deleted) {
                return BookChapterOutcome(completed = false, lastChapter = startChapter)
            }
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
            testHookBeforeWrite?.invoke("discard_display", book.id)
            runIfCurrentGeneration(generation) {
                discardDisplayRows(book.id, pending)
            }
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
                    // 预算让出与代次失效检查：检查放在**章节之间**。已建章节均已随 flush 落盘，断点有效。
                    if (generation != null && generation != coordinationState.get().generation) {
                        interrupted = true
                        break
                    }
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
                                    // slice.startOffset 是该章全书真实字符起点，与 resolve 侧同口径
                                    chapterSourceStart = slice.startOffset,
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
                        testHookBeforeWrite?.invoke("term_batch", book.id)
                        val flushed = runIfCurrentGeneration(generation) {
                            termDao.upsertAllIfBookActive(pending.toList())
                        }
                        if (!flushed) {
                            pending.clear()
                            return BookChapterOutcome(completed = false, lastChapter = lastChapter)
                        }
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
                        // 单处纠错锚点的全书基址表（估算量纲）：与阅读器 EpubDocument /
                        // resolve 侧 LegacyOffsetCodec 完全同源（同输入同函数），保证
                        // 索引侧与阅读器侧对同一锚点的局部化结果一致。
                        val epubChapterStarts = LegacyOffsetCodec.chapterStartOffsets(
                            chapters.map { it.estimatedTextLength },
                        )
                        var anyTruncated = false
                        for ((chSeq, ch) in chapters.withIndex()) {
                            // chapter_index = chSeq + 1（1 基，与 TXT 逐章对齐；chapter 0 留作元数据）
                            val chapterIdx = 1 + chSeq
                            // 章节级续建：已建过的章节直接跳过。
                            // 关键是**不加载**该章正文 —— EPUB 单章的解压 + 解码是主要开销，
                            // 跳过即把上一轮已完成的工作从本轮成本里彻底移除，这是超大 EPUB
                            // 能跨窗口最终建完的前提。
                            if (chapterIdx <= startChapter) continue
                            // 预算让出与代次失效检查：检查放在章节之间，已建章节均已随 flush 落盘。
                            if (generation != null && generation != coordinationState.get().generation) {
                                interrupted = true
                                break
                            }
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
                                            chapterSourceStart = epubChapterStarts.getOrNull(chSeq) ?: 0,
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
                                testHookBeforeWrite?.invoke("term_batch", book.id)
                                val flushed = runIfCurrentGeneration(generation) {
                                    termDao.upsertAllIfBookActive(pending.toList())
                                }
                                if (!flushed) {
                                    pending.clear()
                                    return BookChapterOutcome(completed = false, lastChapter = lastChapter)
                                }
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
        // §6.9 词项先落盘，再写覆盖率：取消发生在两者之间时，
        // coverage 不得先于 terms 记 FULL —— 否则取消后留下「虚假 FULL」。
        if (pending.isNotEmpty()) {
            var k = 0
            while (k < pending.size) {
                val slice = pending.subList(k, (k + UPSERT_BATCH_ROWS).coerceAtMost(pending.size))
                testHookBeforeWrite?.invoke("term_tail", book.id)
                val termsWritten = runIfCurrentGeneration(generation) {
                    termDao.upsertAllIfBookActive(slice)
                }
                if (!termsWritten) {
                    pending.clear()
                    return BookChapterOutcome(completed = false, lastChapter = lastChapter)
                }
                k += UPSERT_BATCH_ROWS
            }
        }

        testHookBeforeWrite?.invoke("coverage", book.id)
        val coverageWritten = runIfCurrentGeneration(generation) {
            coverageDao.upsertAllIfBookActive(
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
        }
        if (!coverageWritten) {
            pending.clear()
            return BookChapterOutcome(completed = false, lastChapter = lastChapter)
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
