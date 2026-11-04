package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.mergeReaderProgress
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.feature.library.deletion.BookDeletionSnapshot
import com.creationreadingassistant.feature.library.deletion.BookRestoreReport
import com.creationreadingassistant.feature.library.deletion.LiveRelationTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.creationreadingassistant.App
import android.content.Context
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书籍仓储 —— 封装书架所需的 Room 读写。
 * P1 仅暴露书架列表与示例数据播种；正文读取走 SAF（见 ReaderScreen）。
 */
@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val bookFileDao: BookFileDao,
    private val progressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao,
    private val noteDao: NoteDao,
    private val highlightDao: HighlightDao,
    private val inspirationDao: InspirationDao,
    private val bookTagDao: BookTagDao,
    private val bookCategoryDao: BookCategoryDao,
    private val shelfBookDao: ShelfBookDao,
    private val chapterReadDao: ChapterReadDao,
    /**
     * 全文索引仓储，用于删除书时清理孤儿索引行。
     * 默认 null 仅用于测试/mock 构造（Hilt 在生产中按类型自动注入真实实例）；
     * 生产路径恒为非空，因此所有调用点都用安全调用 `?.` 兜底，避免测试上下文下空指针。
     */
    private val searchIndexRepository: SearchIndexRepository? = null,
    /**
     * 异步逐书搜索索引刷新协调器（S04-2 / S03-3 接缝）。
     * 用于撤销删除恢复书籍后，在 ApplicationScope 中异步触发该书的索引重建，解除同步阻塞。
     * 默认 null 仅用于测试/mock 构造（Hilt 在生产中按类型自动注入真实实例）。
     */
    private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator? = null,
    @ApplicationContext private val context: Context,
) {
    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAllActive()
    fun observeProgress(): Flow<List<ReadingProgressEntity>> = progressDao.observeAllActive()
    fun observeSessions(): Flow<List<ReadingSessionEntity>> = sessionDao.observeAllActive()
    fun observeSessionsByBook(bookId: String): Flow<List<ReadingSessionEntity>> = sessionDao.observeByBook(bookId)
    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAllActive()
    fun observeHighlights(): Flow<List<HighlightEntity>> = highlightDao.observeAllActive()
    fun observeInspirations(): Flow<List<InspirationEntity>> = inspirationDao.observeAllActive()

    /** 已缓存正文的书籍数量（Profile 存储页）。 */
    fun observeCachedCount(): Flow<Int> = bookContentDao.observeCachedCount()

    /** 已缓存正文的总字节数（Profile 存储页）。 */
    fun observeCachedBytes(): Flow<Long> = bookContentDao.observeCachedBytes()

    suspend fun getById(id: String): BookEntity? = bookDao.getById(id)

    /** 批量按 id 查询（仅活跃记录），供搜索结果收集来源书名等场景消除 N+1。 */
    suspend fun getByIds(ids: Collection<String>): List<BookEntity> = bookDao.getByIds(ids)

    /** 全局搜索：书名/作者/原始文件名/标签名模糊检索（BookDao.search）。 */
    suspend fun search(q: String): List<BookEntity> = bookDao.search(q)

    suspend fun upsert(book: BookEntity) = bookDao.upsert(book)

    /**
     * 阅读器自动进度写入：用 mergeReaderProgress 合并现有业务状态（搁置/读完不回退），
     * 只更新位置与百分比字段，保留累计时长、同步信息等已有数据。
     */
    suspend fun saveProgress(incoming: ReadingProgressEntity) {
        val now = Instant.now().toString()
        progressDao.upsert(
            mergeReaderProgress(
                existing = progressDao.getByBook(incoming.book_id),
                incoming = incoming,
                nowIso = now,
                nowMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** 写入阅读器生成的本地会话；切段与门槛策略由 ReadingSessionRecorder 统一负责。 */
    suspend fun saveReadingSession(session: ReadingSessionEntity) {
        sessionDao.upsert(session)
    }

    /**
     * 删除书籍并级联清理相关记录（阅读进度、会话、笔记、高亮、正文、文件、书单/分类/标签关联）。
     * 所有操作在事务中执行，确保软删除的原子性。
     *
     * 等价于 [deleteBookScoped] 并丢弃快照：调用方不打算撤销时走这里。
     */
    suspend fun deleteBook(id: String) {
        deleteBookScoped(id)
    }

    /**
     * 删除单本书并返回本次操作的作用范围快照。
     *
     * 捕获与写入在同一个事务里完成，所以快照严格等于「本次操作前的活跃状态」：
     * 既不会漏掉并发写入，也不会把本次操作之前就已删除的资料算进撤销范围。
     */
    suspend fun deleteBookScoped(
        bookId: String,
        deletedAt: String = nowIso(),
    ): BookDeletionSnapshot {
        lateinit var snapshot: BookDeletionSnapshot
        bookDao.runInTransaction {
            snapshot = captureDeletionSnapshot(bookId, deletedAt)
            applyDeletionLocked(snapshot)
        }
        return snapshot
    }

    /**
     * 批量删除：一次事务、一次关联表读取，返回每本书各自的快照。
     *
     * 批量撤销的范围由这批快照共同决定，因此入口提示的数量与撤销恢复的数量一致。
     */
    suspend fun deleteBooksScoped(
        bookIds: Collection<String>,
        deletedAt: String = nowIso(),
    ): List<BookDeletionSnapshot> {
        val ids = bookIds.distinct()
        if (ids.isEmpty()) return emptyList()
        val snapshots = ArrayList<BookDeletionSnapshot>(ids.size)
        bookDao.runInTransaction {
            val tagsByBook = bookTagDao.getByBookIds(ids).groupBy { it.book_id }
            val categoriesByBook = bookCategoryDao.getByBookIds(ids).groupBy { it.book_id }
            val shelfLinksByBook = shelfBookDao.getByBookIds(ids).groupBy { it.book_id }
            val chapterReadsByBook = chapterReadDao.getByBookIds(ids).groupBy { it.book_id }
            val notesByBook = noteDao.getActiveByBookIds(ids).groupBy { it.book_id ?: "" }
            val highlightsByBook = highlightDao.getActiveByBookIds(ids).groupBy { it.book_id }
            ids.forEach { bookId ->
                val snapshot = buildDeletionSnapshot(
                    bookId = bookId,
                    deletedAt = deletedAt,
                    tagIds = tagsByBook[bookId].orEmpty().map { it.tag_id },
                    categoryIds = categoriesByBook[bookId].orEmpty().map { it.category_id },
                    shelfLinks = shelfLinksByBook[bookId].orEmpty(),
                    chapterReads = chapterReadsByBook[bookId].orEmpty(),
                    activeNotes = notesByBook[bookId].orEmpty(),
                    activeHighlights = highlightsByBook[bookId].orEmpty(),
                )
                snapshots += snapshot
                applyDeletionLocked(snapshot)
            }
        }
        return snapshots
    }

    /**
     * 捕获一本书在本次删除前的活跃状态。
     *
     * 只记录当前活跃的资料与关系：已经处于软删除状态的笔记/高亮/会话不进快照，
     * 所以撤销不会复活用户此前单独删掉的那些。分类/标签/书单/已读章节是硬删除，
     * 连接表没有 deleted_at 列，快照是它们唯一的恢复依据。
     */
    suspend fun captureDeletionSnapshot(
        bookId: String,
        deletedAt: String = nowIso(),
    ): BookDeletionSnapshot = buildDeletionSnapshot(
        bookId = bookId,
        deletedAt = deletedAt,
        tagIds = bookTagDao.getByBookIds(listOf(bookId)).map { it.tag_id },
        categoryIds = bookCategoryDao.getByBookIds(listOf(bookId)).map { it.category_id },
        shelfLinks = shelfBookDao.getByBookIds(listOf(bookId)),
        chapterReads = chapterReadDao.getByBookIds(listOf(bookId)),
        activeNotes = noteDao.getActiveByBookIds(listOf(bookId)),
        activeHighlights = highlightDao.getActiveByBookIds(listOf(bookId)),
    )

    private suspend fun buildDeletionSnapshot(
        bookId: String,
        deletedAt: String,
        tagIds: List<String>,
        categoryIds: List<String>,
        shelfLinks: List<ShelfBookEntity>,
        chapterReads: List<ChapterReadEntity>,
        activeNotes: List<NoteEntity>,
        activeHighlights: List<HighlightEntity>,
    ): BookDeletionSnapshot {
        val content = bookContentDao.getByBook(bookId)
        return BookDeletionSnapshot(
            bookId = bookId,
            deletedAt = deletedAt,
            book = bookDao.getById(bookId),
            progress = progressDao.getByBook(bookId),
            sessions = sessionDao.observeByBook(bookId).first(),
            notes = activeNotes,
            highlights = activeHighlights,
            file = bookFileDao.getByBook(bookId),
            content = content,
            contentPayloadRetained = content != null,
            tagIds = tagIds,
            categoryIds = categoryIds,
            shelfLinks = shelfLinks,
            chapterReads = chapterReads,
        )
    }

    /** 独立执行一次快照删除；[deleteBookScoped] 内部走不加外层事务的 [applyDeletionLocked]。 */
    suspend fun applyDeletion(snapshot: BookDeletionSnapshot) {
        bookDao.runInTransaction { applyDeletionLocked(snapshot) }
    }

    private suspend fun applyDeletionLocked(snapshot: BookDeletionSnapshot) {
        val now = snapshot.deletedAt
        val id = snapshot.bookId
        bookDao.softDelete(id, now)
        // M-BUG-10：删除书时一并清掉它的全文索引孤儿行（term + coverage）。
        // 否则 search_terms 残留行永久滞留在已删书上，且 searchContent 只查 search_terms
        // 不与活跃书做 JOIN，已删书命中会冒进搜索结果、退化成裸 bookId 行。
        // 调用点在事务内，与软删除原子提交；restoreDeletion 撤销恢复后会再重建索引。
        searchIndexRepository?.deleteIndexForBook(id)
        snapshot.progress?.let { progressDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
        sessionDao.upsertAll(snapshot.sessions.map { it.copy(deleted_at = now, updated_at = now) })
        noteDao.upsertAll(snapshot.notes.map { it.copy(deleted_at = now, updated_at = now) })
        highlightDao.upsertAll(snapshot.highlights.map { it.copy(deleted_at = now, updated_at = now) })
        snapshot.content?.let { bookContentDao.upsert(it.copy(reader_preview = null, epub_json = null)) }
        snapshot.file?.let { bookFileDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
        bookTagDao.clearByBook(id)
        bookCategoryDao.clearByBook(id)
        shelfBookDao.clearByBook(id)
        chapterReadDao.clearForBook(id)
    }

    /**
     * 清理书籍本地正文缓存（保留书架元数据、进度、笔记、标签等）。
     * 用于批量管理中的“清缓存”。
     *
     * 残留保护链（先保护数据，再动数据库）：
     * 1. 预检书籍内容位置是否存在替换/导入残留（`.backup` / `.repairing` / `.importing`），
     *    或位置本身无法可靠判定（目录不可列举）——命中即整本拒绝，**不删任何文件、不写任何库表**；
     * 2. 文件阶段使用残留保护删除器：一旦发现（预检后新出现的）残留标记立即整体中止；
     *    个别正文文件删除失败（如被占用）不中止流程，但结果如实标记"未删净"；
     * 3. 文件阶段完成后（含"未删净"形态），才清空数据库指针。
     *
     * 清理的正文位置共三处（缺一即会留下无主残留、空间不释放）：
     * `books/<id>/`（TXT/MD 正文）、`books/epub/<id>.epub`（EPUB 内部副本）、
     * `books/epub/<id>/`（EPUB 图片缓存，见 [deleteBookContentFiles] 的说明）。
     */
    suspend fun clearBookCache(id: String): BookCacheClearOutcome {
        // ── 第 1 步：保护性预检，先于一切文件删除与数据库写入 ──
        when (val scan = scanBookContentResidue(id)) {
            is ResidueScan.Found -> return BookCacheClearOutcome.RefusedResidue(scan.count)
            ResidueScan.Unsafe -> return BookCacheClearOutcome.RefusedUnsafeScan
            ResidueScan.Clean -> Unit
        }
        // ── 第 2 步：先删本地文件（删除器自带残留兜底）──
        // 残留/不可判定中止 → AbortedMidway（拦截数据库改动，部分执行）；
        // 个别正文文件删除失败 → 流程继续（既有尽力而为边界：指针必须与用户意图一致），
        // 但文件阶段结果如实标记，最终以 ClearedWithRemains 呈现"正文未删净"。
        val fileOutcome = deleteBookContentFiles(id)
        if (fileOutcome is ContentFileDeletionOutcome.Aborted) {
            return BookCacheClearOutcome.AbortedMidway(fileOutcome.cause)
        }
        // ── 第 3 步：文件阶段完成后，清空数据库指针（"未删净"形态亦不例外——
        // 指针与书架状态必须如实反映用户"移除正文"的意图；残留文件成为不可达孤儿，空间未释放） ──
        val now = Instant.now().toString()
        bookDao.getById(id)?.let { bookDao.upsert(it.copy(content_status = "missing", local_uri = null, local_content_path = null, updated_at = now)) }
        bookContentDao.getByBook(id)?.let { bookContentDao.upsert(it.copy(reader_preview = null, epub_json = null)) }
        bookFileDao.getByBook(id)?.let { bookFileDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
        // 「移除正文」（D2 决策 A）：把内部存储里的正文文件一并删掉，而不是只清空 DB 指针；
        // 删除失败时文件成为不可达孤儿，是否提供孤儿清理入口属后续产品决策。
        return when (fileOutcome) {
            is ContentFileDeletionOutcome.RemainsAfterAttempt -> BookCacheClearOutcome.ClearedWithRemains
            else -> BookCacheClearOutcome.Cleared
        }
    }

    /**
     * 删除某本书在内部存储的正文文件，共**三处**：
     * - `filesDir/books/<id>/`（TXT/MD 正文）
     * - `filesDir/books/epub/<id>.epub`（EPUB 内部副本，见 [SearchIndexRepository] 的缓存位置约定）
     * - `filesDir/books/epub/<id>/`（EPUB **图片缓存**目录：`EpubParser` 抽取章节内嵌图片时
     *   创建 `books/epub/<id>/img/` 并写入图片。此前只删 `<id>.epub`，该目录被留成无主文件、
     *   空间不释放 —— 与「清缓存释放空间」的承诺不符）
     *
     * 三处共用同一套残留安全语义：目录走 [deleteTreeSparingResidue]（发现残留标记或不可列举
     * 立即中止），因此「预检之后新出现的残留」同样会被拦下。
     *
     * @return 文件阶段结果：[ContentFileDeletionOutcome.FullyCleared]（全部删净）、
     * [ContentFileDeletionOutcome.RemainsAfterAttempt]（尽力删除后仍有正文文件残留，
     * 如文件被占用）或 [ContentFileDeletionOutcome.Aborted]（残留/不可判定中止，剩余
     * 文件与全部标记原样保留，调用方不得继续改数据库）。Remains 不中止流程：
     * 数据库指针照常清理（既有尽力而为边界），但必须以"正文未删净"呈现给用户。
     *
     * 全程 runCatching：文件缺失或删除失败只留告警，**不得**让「移除正文」整体失败或崩溃。
     * 唯一的例外是残留/不可判定：删除器发现异常状态时立即中止并由调用方拦截数据库改动。
     */
    private fun deleteBookContentFiles(bookId: String): ContentFileDeletionOutcome {
        var abortCause: MidwayAbortCause? = null
        var remainsAfterAttempt = false

        /**
         * 用残留保护删除器清理一个**目录**位置，把结果折叠进上面两个累加标记。
         * @return 需要中止时的原因；null 表示可继续处理后续位置。
         */
        fun clearDirSparingResidue(target: File): MidwayAbortCause? {
            if (!target.exists()) return null
            return when (deleteTreeSparingResidue(target).outcome) {
                TreeDeletionOutcome.COMPLETED -> null
                // 个别正文文件删除失败（如被占用）：不中止、不重试、不扩大删除，
                // 继续清理其余内容；阶段结果如实标记"未删净"。
                TreeDeletionOutcome.COMPLETED_WITH_REMAINS -> {
                    remainsAfterAttempt = true
                    null
                }
                TreeDeletionOutcome.ABORTED_RESIDUE -> MidwayAbortCause.RESIDUE_APPEARED
                TreeDeletionOutcome.ABORTED_UNREADABLE -> MidwayAbortCause.LOCATION_UNREADABLE
            }
        }

        runCatching {
            // ① TXT/MD 正文目录
            val txtAbort = clearDirSparingResidue(File(context.filesDir, "books/$bookId"))
            if (txtAbort != null) {
                abortCause = txtAbort
                return@runCatching
            }
            // ② EPUB：删除前复查同书残留标记——预检与删除之间新出现的标记同样中止。
            val epubDir = File(context.filesDir, "books/epub")
            if (epubDir.isDirectory) {
                val children = epubDir.listFiles()
                if (children == null) {
                    abortCause = MidwayAbortCause.LOCATION_UNREADABLE
                    return@runCatching
                }
                if (children.any { it.name.startsWith("$bookId.") && it.name.hasResidueSuffix() }) {
                    abortCause = MidwayAbortCause.RESIDUE_APPEARED
                    return@runCatching
                }
            }
            // ③ EPUB 图片缓存目录 `books/epub/<id>/`。刻意排在删除 `<id>.epub` **之前**：
            // 在此中止时仍保留更重要的正文副本。只处理本书自己的 `<id>` 目录
            // （`books/epub/` 下其他条目一概不碰）。
            val imgAbort = clearDirSparingResidue(File(context.filesDir, "books/epub/$bookId"))
            if (imgAbort != null) {
                abortCause = imgAbort
                return@runCatching
            }
            // ④ EPUB 内部副本文件
            val epub = File(context.filesDir, "books/epub/$bookId.epub")
            if (epub.exists() && !epub.delete() && epub.exists()) {
                // EPUB 正文删除失败同样计入"未删净"。
                remainsAfterAttempt = true
            }
        }.onFailure {
            AppLog.e(
                "BookRepository",
                "移除正文时删除文件失败（book=${bookId.take(8)}）：${it.message ?: it.javaClass.simpleName}",
            )
        }
        abortCause?.let { return ContentFileDeletionOutcome.Aborted(it) }
        return if (remainsAfterAttempt) {
            ContentFileDeletionOutcome.RemainsAfterAttempt
        } else {
            ContentFileDeletionOutcome.FullyCleared
        }
    }

    /**
     * 预检一本书的内容位置是否存在替换/导入残留。覆盖即将被清理的**全部三个位置**：
     * - TXT/MD：递归扫描 `books/<id>/`；
     * - EPUB 内部副本：检查 `books/epub/<id>.<marker>` 同书标记；
     * - EPUB 图片缓存：递归扫描 `books/epub/<id>/`（与删除阶段处理的位置一一对应，
     *   避免「预检说干净、删除器才发现残留」这种靠删除器兜底的情况）。
     * 任何位置不可列举（`listFiles()` 返回 null）都按「不能可靠判定安全」处理。
     */
    private fun scanBookContentResidue(bookId: String): ResidueScan {
        return try {
            val contentDir = File(context.filesDir, "books/$bookId")
            if (contentDir.exists()) {
                val dirScan = scanDirForResidue(contentDir)
                if (dirScan !is ResidueScan.Clean) return dirScan
            }
            val epubDir = File(context.filesDir, "books/epub")
            if (epubDir.isDirectory) {
                val children = epubDir.listFiles() ?: return ResidueScan.Unsafe
                val prefix = "$bookId."
                val epubResidue = children.count { it.name.startsWith(prefix) && it.name.hasResidueSuffix() }
                if (epubResidue > 0) return ResidueScan.Found(epubResidue)
            }
            // EPUB 图片缓存目录 `books/epub/<id>/`：与删除阶段的位置对应。
            // 只扫本书自己的目录；`books/epub/` 下其他条目一概不碰。
            val imgCacheDir = File(context.filesDir, "books/epub/$bookId")
            if (imgCacheDir.exists()) {
                val imgScan = scanDirForResidue(imgCacheDir)
                if (imgScan !is ResidueScan.Clean) return imgScan
            }
            ResidueScan.Clean
        } catch (e: Exception) {
            AppLog.w(
                "BookRepository",
                "清缓存残留预检失败，按不安全处理（book=${bookId.take(8)}）：${e.message ?: e.javaClass.simpleName}",
            )
            ResidueScan.Unsafe
        }
    }

    /** 递归统计 [dir] 内的残留标记文件；目录不可列举时返回 [ResidueScan.Unsafe]。 */
    private fun scanDirForResidue(dir: File): ResidueScan {
        val children = dir.listFiles() ?: return ResidueScan.Unsafe
        var found = 0
        for (child in children) {
            if (child.name.hasResidueSuffix()) {
                found++
                continue
            }
            if (child.isDirectory) {
                when (val sub = scanDirForResidue(child)) {
                    is ResidueScan.Found -> found += sub.count
                    ResidueScan.Unsafe -> return ResidueScan.Unsafe
                    ResidueScan.Clean -> Unit
                }
            }
        }
        return if (found > 0) ResidueScan.Found(found) else ResidueScan.Clean
    }

    /**
     * 更新书籍阅读进度与完成状态（用于首页继续阅读面板的「标记已读完 / 标记未读」）。
     */
    suspend fun updateReadingProgress(
        bookId: String,
        progressPercent: Float,
        completionState: String,
    ) {
        val now = Instant.now().toString()
        val existing = progressDao.getByBook(bookId)
        val requestedState = ReadingCompletionState.fromStorage(completionState)
        val state = if (progressPercent >= 99.5f) {
            ReadingCompletionState.FINISHED
        } else {
            requestedState
        }
        val finished = state == ReadingCompletionState.FINISHED
        progressDao.upsert(
            (existing ?: ReadingProgressEntity(
                book_id = bookId,
                updated_at = now,
            )).copy(
                progress_percent = progressPercent.coerceIn(0f, 100f),
                completion_state = state.storageValue,
                last_read_at = if (progressPercent > 0) now else existing?.last_read_at,
                updated_at = now,
                // 已读完写入完成时间，未读/在读置 null（对齐网页 completedAt）
                completed_at = if (finished) existing?.completed_at ?: System.currentTimeMillis() else null,
            ),
        )
    }

    /**
     * 只改变阅读生命周期状态，不改进度、位置、阅读时长、笔记或灵感。
     * 搁置与恢复在读都必须走这里，避免把“管理状态”误当成一次阅读进度更新。
     *
     * S05-① 契约：状态列是书架分组的唯一权威（见 `ShelfModels.bookStatus`）。
     * 取消读完（传 [ReadingCompletionState.READING]）只清 `completed_at`；若此前
     * `markFinished` 把 `progress_percent` 抬到了 100，该残余会保留——真实阅读位置仍在
     * `current_location_json` 中，下一次真实阅读保存进度时自愈回落。展示层不得据
     * 「高百分比 + reading」判完成，因此取消后书架正确归入「在读」。
     */
    suspend fun setReadingState(bookId: String, state: ReadingCompletionState) {
        val now = Instant.now().toString()
        val existing = progressDao.getByBook(bookId)
        val base = existing ?: ReadingProgressEntity(book_id = bookId, updated_at = now)
        progressDao.upsert(
            base.copy(
                completion_state = state.storageValue,
                completed_at = when (state) {
                    ReadingCompletionState.FINISHED -> base.completed_at ?: System.currentTimeMillis()
                    ReadingCompletionState.READING,
                    ReadingCompletionState.SHELVED -> null
                },
                updated_at = now,
            ),
        )
    }

    /**
     * 用户显式「标记为已读完」。
     *
     * 与 [updateReadingProgress] 的关键区别：**不刷新 `last_read_at`**。
     * 后者会在标记时把最近阅读时间改成当下，导致一本从未打开过的书跳到
     * 「最近阅读」排序的榜首——那不是阅读行为，是管理操作。
     * 同时保留 `current_location_json`，用户之后仍能从原位置继续读。
     *
     * 进度只升不降：已读 30% 的书标记读完会写成 100%。该抬高的值在本函数内**不回退**——
     * 取消读完请走 [setReadingState] 并传 `READING`，它会清空 `completed_at` 并保留位置；
     * 书卡的「完」标识与书架分组由状态列决定（S05-①，见 `ShelfModels.bookStatus`），
     * 取消后残余的 100% 不再触发任何完成展示，并在下一次真实阅读时自愈。
     */
    suspend fun markFinished(bookId: String) {
        val now = Instant.now().toString()
        val existing = progressDao.getByBook(bookId)
        val base = existing ?: ReadingProgressEntity(book_id = bookId, updated_at = now)
        progressDao.upsert(
            base.copy(
                progress_percent = base.progress_percent.coerceAtLeast(100f),
                completion_state = ReadingCompletionState.FINISHED.storageValue,
                // 首次完成时间保留，重复标记不覆盖
                completed_at = base.completed_at ?: System.currentTimeMillis(),
                updated_at = now,
            ),
        )
    }

    /**
     * 恢复被删除的书籍及其关联数据（用于没有快照时的撤销兜底）。
     *
     * 只恢复与书籍行同一个 deleted_at 时间戳的记录：[deleteBook] 会把本次操作影响的
     * 全部软删除行打上同一个戳，因此这个戳就是「本次删除的作用范围」。用户在此之前
     * 单独删掉的笔记/高亮/会话带着别的时间戳，不会被笼统复活。
     *
     * 分类/标签/书单/已读章节在删除时是硬删除，库里没有时间戳可比对，这条路径恢复不了；
     * 需要完整撤销请走 [restoreDeletion]（由删除快照驱动）。
     */
    suspend fun restoreBook(bookId: String) {
        var restored = false
        bookDao.runInTransaction {
            val deletedBook = bookDao.getDeleted().find { it.id == bookId } ?: return@runInTransaction
            val stamp = deletedBook.deleted_at ?: return@runInTransaction
            bookDao.upsert(deletedBook.copy(deleted_at = null))
            restored = true
            progressDao.getDeleted()
                .find { it.book_id == bookId && it.deleted_at == stamp }
                ?.let { progressDao.upsert(it.copy(deleted_at = null)) }
            sessionDao.upsertAll(
                sessionDao.getDeleted()
                    .filter { it.book_id == bookId && it.deleted_at == stamp }
                    .map { it.copy(deleted_at = null) },
            )
            bookFileDao.getDeleted()
                .find { it.book_id == bookId && it.deleted_at == stamp }
                ?.let { bookFileDao.upsert(it.copy(deleted_at = null)) }
            noteDao.upsertAll(
                noteDao.getDeleted()
                    .filter { it.book_id == bookId && it.deleted_at == stamp }
                    .map { it.copy(deleted_at = null) },
            )
            highlightDao.upsertAll(
                highlightDao.getDeleted()
                    .filter { it.book_id == bookId && it.deleted_at == stamp }
                    .map { it.copy(deleted_at = null) },
            )
        }
        if (restored) {
            indexRefreshCoordinator?.request(bookId)
        }
    }

    /** 撤销一次删除：按快照恢复资料、关系与正文缓存，返回逐项结果。 */
    suspend fun restoreDeletion(
        snapshot: BookDeletionSnapshot,
        liveTargets: LiveRelationTargets,
    ): BookRestoreReport = restoreDeletions(listOf(snapshot), liveTargets).first()

    /**
     * 批量撤销，一次事务完成。
     *
     * 每一项都单独判定是否该写回，三条规则：
     * 1. 当前已是活跃行 → 本次删除之后有人写过它，快照让路，不覆盖合法新改动；
     * 2. 当前软删除行的时间戳不是本次操作的 → 之后又被别的操作删了，同样让路；
     * 3. 关系目标（标签/分类/书单）已不在 [liveTargets] 里 → 跳过，不造孤儿关系。
     *
     * 连接行与已读章节按主键 REPLACE 写入，且只补当前不存在的，因此重复撤销不会产生
     * 重复关系，也不会把用户撤销前重新加过的关联改回旧排序。
     */
    suspend fun restoreDeletions(
        snapshots: List<BookDeletionSnapshot>,
        liveTargets: LiveRelationTargets,
    ): List<BookRestoreReport> {
        if (snapshots.isEmpty()) return emptyList()
        val reports = ArrayList<BookRestoreReport>(snapshots.size)
        bookDao.runInTransaction {
            val bookIds = snapshots.map { it.bookId }.distinct()
            val activeBooks = bookDao.getByIds(bookIds).associateBy { it.id }
            val deletedBooks = bookDao.getDeleted().associateBy { it.id }
            val activeProgress = progressDao.getByBooks(bookIds).associateBy { it.book_id }
            val deletedProgress = progressDao.getDeleted().associateBy { it.book_id }
            val deletedFiles = bookFileDao.getDeleted().associateBy { it.book_id }
            val sessionIds = snapshots.flatMap { it.sessions.map { s -> s.id } }
            val sessionsById = if (sessionIds.isEmpty()) emptyMap() else sessionDao.getByIds(sessionIds).associateBy { it.id }
            val noteIds = snapshots.flatMap { snapshot -> snapshot.notes.map { it.id } }.distinct()
            val currentNotesById = if (noteIds.isEmpty()) emptyMap() else noteDao.getByIds(noteIds).associateBy { it.id }
            val activeNoteIds = currentNotesById.values.filter { it.deleted_at == null }.mapTo(mutableSetOf()) { it.id }
            val deletedNotes = currentNotesById.values.filter { it.deleted_at != null }.associateBy { it.id }
            val highlightIds = snapshots.flatMap { snapshot -> snapshot.highlights.map { it.id } }.distinct()
            val currentHighlightsById = if (highlightIds.isEmpty()) emptyMap() else highlightDao.getByIds(highlightIds).associateBy { it.id }
            val activeHighlightIds = currentHighlightsById.values.filter { it.deleted_at == null }.mapTo(mutableSetOf()) { it.id }
            val deletedHighlights = currentHighlightsById.values.filter { it.deleted_at != null }.associateBy { it.id }
            val contentByBook = bookContentDao.getPreviewsByBookIds(bookIds).associateBy { it.book_id }
            val tagLinksByBook = bookTagDao.getByBookIds(bookIds).groupBy { it.book_id }
            val categoryLinksByBook = bookCategoryDao.getByBookIds(bookIds).groupBy { it.book_id }
            val shelfLinksByBook = shelfBookDao.getByBookIds(bookIds).groupBy { it.book_id }
            val chapterReadsByBook = chapterReadDao.getByBookIds(bookIds).groupBy { it.book_id }

            snapshots.forEach { snapshot ->
                reports += restoreOneSnapshot(
                    snapshot = snapshot,
                    liveTargets = liveTargets,
                    activeBooks = activeBooks,
                    deletedBooks = deletedBooks,
                    activeProgress = activeProgress,
                    deletedProgress = deletedProgress,
                    deletedFiles = deletedFiles,
                    sessionsById = sessionsById,
                    activeNoteIds = activeNoteIds,
                    deletedNotes = deletedNotes,
                    activeHighlightIds = activeHighlightIds,
                    deletedHighlights = deletedHighlights,
                    contentByBook = contentByBook,
                    tagLinksByBook = tagLinksByBook,
                    categoryLinksByBook = categoryLinksByBook,
                    shelfLinksByBook = shelfLinksByBook,
                    chapterReadsByBook = chapterReadsByBook,
                )
            }
        }
        // M-BUG-10 / S03-3（撤销自愈）：被删的书在删除时已清掉全文索引，撤销恢复后必须重建，
        // 否则该书在下次全量重建前不可全文搜索（增量索引游标只前进，不会回退到它）。
        // 索引器幂等（先删该书行再重建），这里只对「确实被本次撤销恢复」的书触发，
        // 避免把已活跃或未恢复的书的索引也重扫一遍。
        // S03-3：优先通过 indexRefreshCoordinator 提交异步请求（解除同步阻塞）；
        // 若未注入协调器（如旧测试夹具），回退至 searchIndexRepository 同步重建以维持测试兼容。
        if (indexRefreshCoordinator != null) {
            reports
                .filter { it.restoredBook }
                .map { it.bookId }
                .forEach { bookId ->
                    indexRefreshCoordinator.request(bookId)
                }
        } else {
            searchIndexRepository?.let { repo ->
                reports
                    .filter { it.restoredBook }
                    .map { it.bookId }
                    .forEach { bookId ->
                        runCatching {
                            withContext(Dispatchers.IO) { repo.reindexBookById(bookId) }
                        }.onFailure { e ->
                            AppLog.w(
                                "BookRepository",
                                "撤销后重建搜索索引失败（book=${bookId.take(8)}），将在下次后台扫描自愈：${e.message ?: e.javaClass.simpleName}",
                            )
                        }
                    }
            }
        }
        return reports
    }

    @Suppress("LongParameterList")
    private suspend fun restoreOneSnapshot(
        snapshot: BookDeletionSnapshot,
        liveTargets: LiveRelationTargets,
        activeBooks: Map<String, BookEntity>,
        deletedBooks: Map<String, BookEntity>,
        activeProgress: Map<String, ReadingProgressEntity>,
        deletedProgress: Map<String, ReadingProgressEntity>,
        deletedFiles: Map<String, BookFileEntity>,
        // getByIds 不过滤软删除，这里拿到的是全部现存行，靠 deleted_at 戳区分「本次删的」和「之后动过的」。
        sessionsById: Map<String, ReadingSessionEntity>,
        activeNoteIds: Set<String>,
        deletedNotes: Map<String, NoteEntity>,
        activeHighlightIds: Set<String>,
        deletedHighlights: Map<String, HighlightEntity>,
        contentByBook: Map<String, BookContentEntity>,
        tagLinksByBook: Map<String, List<BookTagEntity>>,
        categoryLinksByBook: Map<String, List<BookCategoryEntity>>,
        shelfLinksByBook: Map<String, List<ShelfBookEntity>>,
        chapterReadsByBook: Map<String, List<ChapterReadEntity>>,
    ): BookRestoreReport {
        val bookId = snapshot.bookId
        var restoredBook = false
        var bookAlreadyActive = false
        var skippedNewer = 0

        // ── 书籍主行 ────────────────────────────────────────────
        val deletedBook = deletedBooks[bookId]
        val laterDeleteConflict = when {
            activeBooks.containsKey(bookId) -> {
                bookAlreadyActive = true
                false
            }
            deletedBook == null -> {
                // 行已不在库里：只有快照带着书籍行才补得回来，否则子表会变成外键孤儿。
                if (snapshot.book != null) {
                    bookDao.upsert(snapshot.book.copy(deleted_at = null))
                    restoredBook = true
                }
                false
            }
            deletedBook.deleted_at == snapshot.deletedAt -> {
                bookDao.upsert((snapshot.book ?: deletedBook).copy(deleted_at = null))
                restoredBook = true
                false
            }
            else -> {
                // 本次删除之后又被另一次删除覆盖，撤销不能反过来撤掉那一次。
                skippedNewer++
                true
            }
        }
        if (laterDeleteConflict) {
            return BookRestoreReport(
                bookId = bookId,
                skippedNewerChangeCount = skippedNewer,
                bookAlreadyActive = bookAlreadyActive,
            )
        }
        val bookRowPresent = restoredBook || bookAlreadyActive || deletedBook != null

        // ── 阅读进度 ────────────────────────────────────────────
        var restoredProgress = false
        snapshot.progress?.let { wanted ->
            when {
                activeProgress.containsKey(bookId) -> skippedNewer++
                deletedProgress[bookId]?.deleted_at == snapshot.deletedAt -> {
                    progressDao.upsert(wanted.copy(deleted_at = null))
                    restoredProgress = true
                }
                deletedProgress.containsKey(bookId) -> skippedNewer++
                bookRowPresent -> {
                    progressDao.upsert(wanted.copy(deleted_at = null))
                    restoredProgress = true
                }
            }
        }

        // 三条子表共用一套判定，避免会话/笔记/高亮的冲突规则各自漂移：
        // 当前活跃，或被别的戳软删 → 本次删除之后有人动过，快照让路；
        // 带本次戳，或库里已无此行 → 恢复，但必须有书籍主行兜住外键。
        fun restorable(activeNow: Boolean, currentStamp: String?): Boolean = when {
            activeNow -> {
                skippedNewer++
                false
            }
            currentStamp == snapshot.deletedAt -> bookRowPresent
            currentStamp != null -> {
                skippedNewer++
                false
            }
            else -> bookRowPresent
        }

        // ── 阅读会话 ────────────────────────────────────────────
        val sessionsToRestore = snapshot.sessions.filter { session ->
            val current = sessionsById[session.id]
            restorable(
                activeNow = current != null && current.deleted_at == null,
                currentStamp = current?.deleted_at,
            )
        }
        if (sessionsToRestore.isNotEmpty()) {
            sessionDao.upsertAll(sessionsToRestore.map { it.copy(deleted_at = null) })
        }

        // ── 笔记 / 高亮 ─────────────────────────────────────────
        val notesToRestore = snapshot.notes.filter { note ->
            restorable(
                activeNow = activeNoteIds.contains(note.id),
                currentStamp = deletedNotes[note.id]?.deleted_at,
            )
        }
        if (notesToRestore.isNotEmpty()) {
            noteDao.upsertAll(notesToRestore.map { it.copy(deleted_at = null) })
        }

        val highlightsToRestore = snapshot.highlights.filter { highlight ->
            restorable(
                activeNow = activeHighlightIds.contains(highlight.id),
                currentStamp = deletedHighlights[highlight.id]?.deleted_at,
            )
        }
        if (highlightsToRestore.isNotEmpty()) {
            highlightDao.upsertAll(highlightsToRestore.map { it.copy(deleted_at = null) })
        }

        // ── 文件关联 ────────────────────────────────────────────
        var restoredFile = false
        snapshot.file?.let { wanted ->
            val currentDeleted = deletedFiles[bookId]
            when {
                bookFileDao.getByBook(bookId) != null -> skippedNewer++
                currentDeleted == null || currentDeleted.deleted_at == snapshot.deletedAt -> {
                    bookFileDao.upsert(wanted.copy(deleted_at = null))
                    restoredFile = true
                }
                else -> skippedNewer++
            }
        }

        // ── 正文缓存 ────────────────────────────────────────────
        var restoredContent = false
        var contentPayloadDropped = false
        val wantedContent = snapshot.content
        if (wantedContent != null) {
            if (!snapshot.contentPayloadRetained) {
                contentPayloadDropped = true
            } else {
                val current = contentByBook[bookId]
                if (current == null || (current.reader_preview == null && current.epub_json == null)) {
                    bookContentDao.upsert(wantedContent)
                    restoredContent = true
                } else {
                    // 删除之后重新缓存过正文，保留新缓存。
                    skippedNewer++
                }
            }
        }

        // ── 分类 / 标签 / 书单关系 ──────────────────────────────
        val existingTagIds = tagLinksByBook[bookId].orEmpty().map { it.tag_id }.toSet()
        val tagCandidates = snapshot.tagIds.filterNot { existingTagIds.contains(it) }
        val (liveTags, deadTags) = tagCandidates.partition { liveTargets.tagIds.contains(it) }
        if (bookRowPresent && liveTags.isNotEmpty()) {
            bookTagDao.upsertAll(liveTags.map { BookTagEntity(book_id = bookId, tag_id = it) })
        }

        val existingCategoryIds = categoryLinksByBook[bookId].orEmpty().map { it.category_id }.toSet()
        val categoryCandidates = snapshot.categoryIds.filterNot { existingCategoryIds.contains(it) }
        val (liveCategories, deadCategories) = categoryCandidates.partition { liveTargets.categoryIds.contains(it) }
        if (bookRowPresent && liveCategories.isNotEmpty()) {
            bookCategoryDao.upsertAll(liveCategories.map { BookCategoryEntity(book_id = bookId, category_id = it) })
        }

        val existingShelfIds = shelfLinksByBook[bookId].orEmpty().map { it.shelf_id }.toSet()
        val shelfCandidates = snapshot.shelfLinks.filterNot { existingShelfIds.contains(it.shelf_id) }
        val (liveShelves, deadShelves) = shelfCandidates.partition { liveTargets.shelfIds.contains(it.shelf_id) }
        if (bookRowPresent && liveShelves.isNotEmpty()) {
            shelfBookDao.upsertAll(liveShelves)
        }

        // ── 已读章节 ────────────────────────────────────────────
        val existingChapters = chapterReadsByBook[bookId].orEmpty().map { it.chapter_index }.toSet()
        val chaptersToRestore = snapshot.chapterReads.filterNot { existingChapters.contains(it.chapter_index) }
        if (bookRowPresent && chaptersToRestore.isNotEmpty()) {
            chapterReadDao.upsertAll(chaptersToRestore)
        }

        return BookRestoreReport(
            bookId = bookId,
            restoredBook = restoredBook,
            restoredProgress = restoredProgress,
            restoredSessionCount = sessionsToRestore.size,
            restoredNoteCount = notesToRestore.size,
            restoredHighlightCount = highlightsToRestore.size,
            restoredTagCount = if (bookRowPresent) liveTags.size else 0,
            restoredCategoryCount = if (bookRowPresent) liveCategories.size else 0,
            restoredShelfLinkCount = if (bookRowPresent) liveShelves.size else 0,
            restoredChapterReadCount = if (bookRowPresent) chaptersToRestore.size else 0,
            restoredFile = restoredFile,
            restoredContent = restoredContent,
            skippedNewerChangeCount = skippedNewer,
            skippedRelationTargetIds = deadTags + deadCategories + deadShelves.map { it.shelf_id },
            contentPayloadDropped = contentPayloadDropped,
            bookAlreadyActive = bookAlreadyActive,
        )
    }

    /** 更新书籍基础信息（书名/作者/简介）。 */
    suspend fun updateBookInfo(bookId: String, title: String, author: String?, description: String? = null) {
        val now = nowIso()
        bookDao.getById(bookId)?.let {
            bookDao.upsert(it.copy(title = title, author = author, description = description, updated_at = now))
        }
    }

    /** 更新书籍封面（Uri 或生成的文字封面 DataURL 均可）。 */
    suspend fun updateBookCover(bookId: String, coverUrl: String?) {
        val now = nowIso()
        bookDao.getById(bookId)?.let {
            bookDao.upsert(it.copy(cover_data_url = coverUrl, updated_at = now))
        }
    }

    private fun nowIso(): String =
        java.time.Instant.now().toString()
}

// ── 残留保护：共享标记、结果类型与纯文件层辅助 ──────────────────────────────
//
// 依据 docs/code-review/2026-09-25-s04-epub-residue-ui-flow-audit.md §2.6：
// 「清空所有离线书籍与资源」与「清缓存」都会把未完成替换（K2 崩溃窗口）的残留
// `.backup` 一并删掉——而那份备份可能是该书仅存的正文。此处的保护链对两条路径
// 统一生效：先判定残留（或判定不了），再动文件，最后才允许改数据库。

/** 替换/导入流程的残留标记后缀：删除与清空操作绝不触碰携带这些后缀的文件。 */
internal val RESIDUE_SUFFIXES = listOf(".backup", ".repairing", ".importing")

/** [RESIDUE_SUFFIXES] 的单文件名判定（接收文件名字符串，而非 File）。 */
internal fun String.hasResidueSuffix(): Boolean = RESIDUE_SUFFIXES.any { endsWith(it) }

/** 递归扫描目录内的残留标记。返回 null 表示目录不可列举（无法可靠判定安全）。 */
internal fun containsResidueRecursively(dir: File): Boolean? {
    val children = dir.listFiles() ?: return null
    for (child in children) {
        if (child.name.hasResidueSuffix()) return true
        if (child.isDirectory) {
            when (val sub = containsResidueRecursively(child)) {
                true -> return true
                null -> return null
                false -> Unit
            }
        }
    }
    return false
}

/** 逐目录预检结果（供 [BookRepository.scanBookContentResidue] 使用）。 */
internal sealed interface ResidueScan {
    /** 确认无残留。 */
    data object Clean : ResidueScan
    /** 发现残留，[count] 为标记文件数。 */
    data class Found(val count: Int) : ResidueScan
    /** 目录不可列举等无法可靠判定的情形。 */
    data object Unsafe : ResidueScan
}

/**
 * 单本书「清理本地正文缓存」的结果。状态与实际文件/数据库状态严格对应：
 * - **完全未动**：[BookCacheClearOutcome.RefusedResidue] / [BookCacheClearOutcome.RefusedUnsafeScan]
 *   —— 预检阶段拒绝，未删除任何文件、未写任何库表，残留标记与书籍指针原样保留；
 * - **部分执行**：[BookCacheClearOutcome.AbortedMidway] —— 预检通过、文件删除开始后
 *   遇到新出现的残留标记或位置变得不可判定而立即中止：部分文件可能已删除，但残留
 *   标记与数据库指针均原样保留；调用方必须把它作为「未完成」呈现，不得报成功；
 * - **完成**：[BookCacheClearOutcome.Cleared]（正文已全部删除）与
 *   [BookCacheClearOutcome.ClearedWithRemains]（指针已清空但正文未删净）——
 *   两者数据库状态相同，仅本地文件清理彻底程度不同，提示必须区分。
 */
sealed interface BookCacheClearOutcome {
    /** 已完成：本地正文文件已全部删除，数据库指针已清空。 */
    data object Cleared : BookCacheClearOutcome

    /**
     * 已完成但正文未删净：数据库指针已清空、书架状态已更新，但仍有正文文件留在本地
     * （删除失败，如文件被占用），空间未完全释放。残留保护不受影响；
     * 用户提示必须说明"未删净"，不得与 [Cleared] 混同。
     */
    data object ClearedWithRemains : BookCacheClearOutcome

    /** 预检发现替换/导入残留而整本拒绝（完全未动）。[residueCount] 为发现的残留文件数。 */
    data class RefusedResidue(val residueCount: Int) : BookCacheClearOutcome

    /** 预检无法可靠判定书籍内容位置的状态（目录不可读/不可列举等），保守拒绝（完全未动）。 */
    data object RefusedUnsafeScan : BookCacheClearOutcome

    /**
     * 文件删除开始后中止（部分执行）：[cause] 指明触发原因；残留标记与数据库指针
     * 均原样保留，已删除的部分不做恢复。
     */
    data class AbortedMidway(val cause: MidwayAbortCause) : BookCacheClearOutcome
}

/**
 * 单书正文文件删除阶段的结果（[BookRepository.deleteBookContentFiles] 内部使用）。
 */
private sealed interface ContentFileDeletionOutcome {
    /** 全部正文文件已删除。 */
    data object FullyCleared : ContentFileDeletionOutcome

    /** 已尽力删除但仍有正文文件残留（非保护原因，如被占用）；残留标记与剩余文件不受影响。 */
    data object RemainsAfterAttempt : ContentFileDeletionOutcome

    /** 因（新出现的）残留或位置不可判定而中止：剩余文件与全部标记原样保留。 */
    data class Aborted(val cause: MidwayAbortCause) : ContentFileDeletionOutcome
}

/** 文件删除中途停止的原因（都按保守策略停止，不放宽删除边界）。 */
enum class MidwayAbortCause {
    /** 删除过程中新出现替换/导入残留标记。 */
    RESIDUE_APPEARED,
    /** 书籍内容位置（或其子目录）变得不可列举，无法继续可靠判定安全。 */
    LOCATION_UNREADABLE,
}

/** 子树删除结果：完成（可能带未删净文件）/ 因发现残留中止 / 因位置不可判定中止。 */
internal enum class TreeDeletionOutcome {
    /** 子树内容已全部删除。 */
    COMPLETED,
    /** 已尝试删除全部内容，但存在未能删除的文件（非保护原因，如被占用）；不触发任何中止。 */
    COMPLETED_WITH_REMAINS,
    /** 因发现（新出现的）残留标记而立即中止。 */
    ABORTED_RESIDUE,
    /** 因某个目录不可列举、无法继续可靠判定而立即中止。 */
    ABORTED_UNREADABLE,
}

/**
 * [deleteTreeSparingResidue] 的结果。[outcome] 之外还带 [deletedAny]——中止前是否
 * 已实际删除过文件。二者共同区分「整条未动即中止」（deletedAny=false，条目按
 * 保留计数）与「目录内部已部分删除后中止」（deletedAny=true，条目按部分执行计数），
 * 保证清空离线的报告不把部分删除虚报为「未执行/未删除」。
 */
internal data class TreeDeletionResult(
    val outcome: TreeDeletionOutcome,
    val deletedAny: Boolean,
)

/**
 * 尽力删除 [root] 整棵子树；遇到残留标记文件、或某个目录不可列举时**立即中止**，
 * 剩余内容（含标记）原样保留。单个普通文件删除失败不中止、也不扩大重试，
 * 但会体现在 [TreeDeletionOutcome.COMPLETED_WITH_REMAINS] 中，供报告如实计数。
 * 目录壳（空目录）删除失败不计入——其内容已全部清除。
 */
internal fun deleteTreeSparingResidue(root: File): TreeDeletionResult {
    var deletedAny = false
    var anyDeleteFailed = false
    fun walk(dir: File): TreeDeletionOutcome {
        val children = dir.listFiles() ?: return TreeDeletionOutcome.ABORTED_UNREADABLE
        for (child in children) {
            if (child.name.hasResidueSuffix()) return TreeDeletionOutcome.ABORTED_RESIDUE
            if (child.isDirectory) {
                when (val subOutcome = walk(child)) {
                    // 中止类结果立即向上传播，停止删除后续内容。
                    TreeDeletionOutcome.ABORTED_RESIDUE,
                    TreeDeletionOutcome.ABORTED_UNREADABLE,
                    -> return subOutcome
                    // 子目录存在未删净文件：继续清理其余内容，但保留"有残留"的事实。
                    TreeDeletionOutcome.COMPLETED_WITH_REMAINS -> anyDeleteFailed = true
                    TreeDeletionOutcome.COMPLETED -> Unit
                }
            } else {
                if (child.delete() || !child.exists()) {
                    deletedAny = true
                } else {
                    anyDeleteFailed = true
                }
            }
        }
        // 目录壳删除失败不改变结果：内容已全部清除，仅空壳可能残留。
        dir.delete()
        return if (anyDeleteFailed) {
            TreeDeletionOutcome.COMPLETED_WITH_REMAINS
        } else {
            TreeDeletionOutcome.COMPLETED
        }
    }
    val outcome = walk(root)
    return TreeDeletionResult(outcome, deletedAny)
}

/**
 * 「清空所有离线书籍与资源」的文件层清理报告。整体状态由六个计数字段唯一确定，
 * 与实际文件状态严格对应，[userNotice] 据此生成提示，不得虚报：
 * - **完成**：clearedEntries>0 且其余计数全为 0；
 * - **完全未动**：clearedEntries=0 且无任何部分执行（partial 或 remains）条目；
 * - **部分执行**：存在已确认删除的条目，或存在"目录内部已部分删除后中止"的条目，
 *   或存在删除后仍有残留文件的条目。
 * 其中 partialResidueEntries / partialUnreadableEntries 专指"目录内部已先删除了
 * 部分文件、随后因新残留/目录不可判定而中止"的条目——它们既不是"原样保留"，
 * 也不是"完整清理"，必须单独呈现。
 */
data class OfflineBooksClearReport(
    /** 已完整清理（全部内容确认删除）的条目数。 */
    val clearedEntries: Int = 0,
    /** 完全未动·残留保护：预检/枚举发现替换/导入残留而整条保留的条目数。 */
    val protectedEntries: Int = 0,
    /** 完全未动·不可判定：目录不可列举、无法可靠判定安全而保守保留的条目数。 */
    val unreadableEntries: Int = 0,
    /** 部分执行：目录内部已删除部分文件后，因新出现残留标记而中止的条目数。 */
    val partialResidueEntries: Int = 0,
    /** 部分执行：目录内部已删除部分文件后，因目录变得不可判定而中止的条目数。 */
    val partialUnreadableEntries: Int = 0,
    /** 已尝试完整删除但仍残留文件（非保护原因，如文件被占用）的条目数。 */
    val remainsEntries: Int = 0,
    /** true = books/ 目录本身不可读/不可列举，未枚举任何条目、未删除任何文件。 */
    val rootUnreadable: Boolean = false,
) {
    /** 生成与实际文件状态一致的清空结果说明（供 StorageSubPage 直接展示）。 */
    fun userNotice(): String {
        if (rootUnreadable) {
            return "无法读取本地书籍目录，清空操作未执行、未删除任何文件。"
        }
        val total = clearedEntries + protectedEntries + unreadableEntries +
            partialResidueEntries + partialUnreadableEntries + remainsEntries
        if (total == 0) return "没有需要清理的离线书籍文件。"
        val anyKeep = protectedEntries > 0 || unreadableEntries > 0 ||
            partialResidueEntries > 0 || partialUnreadableEntries > 0 || remainsEntries > 0
        if (!anyKeep) {
            return "已清空所有离线书籍与资源（共清理 ${clearedEntries} 个条目）。"
        }
        // 只要发生过任何实际删除（完整条目或目录内部部分删除），就不得称"未执行"。
        val nothingDeletedSoFar = clearedEntries == 0 &&
            partialResidueEntries == 0 && partialUnreadableEntries == 0
        val head = when {
            nothingDeletedSoFar && remainsEntries == 0 -> "清空未执行"
            nothingDeletedSoFar -> "清空未完成"
            else -> "清空已部分执行"
        }
        val parts = mutableListOf(head)
        if (clearedEntries > 0) parts += "已清理 ${clearedEntries} 个离线条目"
        if (partialResidueEntries > 0) {
            parts += "${partialResidueEntries} 个条目在清理中发现新的替换残留、已中止" +
                "（该条目部分文件已被清理，残留备份与剩余文件均原样保留）"
        }
        if (partialUnreadableEntries > 0) {
            parts += "${partialUnreadableEntries} 个条目在清理中遇到不可读取的目录、已中止" +
                "（该条目部分文件已被清理，剩余文件原样保留）"
        }
        if (protectedEntries > 0) {
            parts += "${protectedEntries} 个条目包含未完成替换的残留备份（可能是该书仅存的正文），" +
                "已原样保留，未删除也未自动恢复"
        }
        if (unreadableEntries > 0) {
            parts += "${unreadableEntries} 个条目无法读取，已按保守策略保留"
        }
        if (remainsEntries > 0) {
            parts += "${remainsEntries} 个条目未能完全删除（部分文件可能被系统占用）"
        }
        return parts.joinToString("；") + "；书架信息未改动。"
    }
}

/**
 * 残留保护版「清空所有离线书籍」的文件层实现（供 StorageSubPage 危险确认调用）。
 *
 * 与既有行为一致：**纯文件层操作，不读不写数据库**——书架条目与其本地指针保持原样。
 * 保护粒度：
 * - `books/<id>/` 目录内（任意层级）存在残留标记，或目录不可列举 → 整个目录保留；
 * - `books/epub/` 内：标记文件（`<id>.backup` 等）保留；与其同书的 `<id>.epub` 一并保留；
 * - 其余无残留条目照常删除（保持原有清理功能）。
 * 遇到不可读取的条目时**继续处理其余条目**（各自独立判定），但绝不放宽删除边界；
 * 计数严格区分三种条目状态：完整清理 / 完全未动（保留）/ 目录内部部分删除后中止，
 * 以及"尝试删除后仍有文件未删"的形态（[OfflineBooksClearReport.remainsEntries]）。
 */
internal object OfflineBooksResidueClear {

    /** clear/clearEpubDir 共享的逐条目计数器。 */
    private class ClearCounts {
        var cleared = 0
        var protectedEntries = 0
        var unreadable = 0
        var partialResidue = 0
        var partialUnreadable = 0
        var remains = 0

        /** 目录条目：按删除器结果与 deletedAny 区分"未动中止"与"部分删除后中止"。 */
        fun countTreeDeletion(result: TreeDeletionResult) {
            when (result.outcome) {
                TreeDeletionOutcome.COMPLETED -> cleared++
                TreeDeletionOutcome.COMPLETED_WITH_REMAINS -> remains++
                TreeDeletionOutcome.ABORTED_RESIDUE ->
                    if (result.deletedAny) partialResidue++ else protectedEntries++
                TreeDeletionOutcome.ABORTED_UNREADABLE ->
                    if (result.deletedAny) partialUnreadable++ else unreadable++
            }
        }
    }

    fun clear(booksDir: File): OfflineBooksClearReport {
        if (!booksDir.exists()) return OfflineBooksClearReport()
        if (!booksDir.isDirectory) return OfflineBooksClearReport(rootUnreadable = true)
        val topLevel = booksDir.listFiles() ?: return OfflineBooksClearReport(rootUnreadable = true)
        val counts = ClearCounts()
        for (entry in topLevel) {
            if (entry.isDirectory && entry.name == "epub") {
                clearEpubDir(entry, counts)
            } else if (entry.isDirectory) {
                when (containsResidueRecursively(entry)) {
                    true -> counts.protectedEntries++
                    null -> counts.unreadable++
                    false -> counts.countTreeDeletion(deleteTreeSparingResidue(entry))
                }
            } else if (entry.name.hasResidueSuffix()) {
                counts.protectedEntries++
            } else {
                if (entry.delete() || !entry.exists()) counts.cleared++ else counts.remains++
            }
        }
        // 全部条目清空后移除 books/ 目录壳，与原有整目录删除的空间释放效果一致；
        // 有保留条目时目录非空，删除自然失败，目录原样保留。
        booksDir.delete()
        return OfflineBooksClearReport(
            clearedEntries = counts.cleared,
            protectedEntries = counts.protectedEntries,
            unreadableEntries = counts.unreadable,
            partialResidueEntries = counts.partialResidue,
            partialUnreadableEntries = counts.partialUnreadable,
            remainsEntries = counts.remains,
        )
    }

    /**
     * 逐文件清理 `books/epub/`。与顶层目录不同，这里按文件粒度保护——
     * 残留标记与其同书 `.epub` 保留，其余删除；计数写入共享的 [counts]。
     */
    private fun clearEpubDir(epubDir: File, counts: ClearCounts) {
        val children = epubDir.listFiles() ?: run {
            // epub 目录本身不可列举：整条保留，按不可判定计数。
            counts.unreadable++
            return
        }
        for (file in children) {
            when {
                file.name.hasResidueSuffix() -> counts.protectedEntries++
                file.isDirectory -> when (containsResidueRecursively(file)) {
                    true -> counts.protectedEntries++
                    null -> counts.unreadable++
                    false -> counts.countTreeDeletion(deleteTreeSparingResidue(file))
                }
                file.name.endsWith(".epub") && hasResidueSibling(file) -> counts.protectedEntries++
                else -> if (file.delete() || !file.exists()) counts.cleared++ else counts.remains++
            }
        }
        // 子项清空后移除目录壳，保持与整目录删除一致的空间释放效果。
        if (epubDir.listFiles()?.isEmpty() == true) epubDir.delete()
    }

    /** `<id>.epub` 的同书替换/导入标记（`<id>.backup` 等）是否存在。 */
    private fun hasResidueSibling(epubFile: File): Boolean {
        val bookId = epubFile.name.removeSuffix(".epub")
        val parent = epubFile.parentFile ?: return false
        return RESIDUE_SUFFIXES.any { File(parent, "$bookId$it").exists() }
    }
}
