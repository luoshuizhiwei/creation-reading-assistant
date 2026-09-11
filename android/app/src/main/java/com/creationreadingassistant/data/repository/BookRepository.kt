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
import com.creationreadingassistant.feature.library.deletion.BookDeletionSnapshot
import com.creationreadingassistant.feature.library.deletion.BookRestoreReport
import com.creationreadingassistant.feature.library.deletion.LiveRelationTargets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     */
    suspend fun clearBookCache(id: String) {
        val now = Instant.now().toString()
        bookDao.getById(id)?.let { bookDao.upsert(it.copy(content_status = "missing", local_uri = null, local_content_path = null, updated_at = now)) }
        bookContentDao.getByBook(id)?.let { bookContentDao.upsert(it.copy(reader_preview = null, epub_json = null)) }
        bookFileDao.getByBook(id)?.let { bookFileDao.upsert(it.copy(deleted_at = now, updated_at = now)) }
        // 「移除正文」（D2 决策 A）：名副其实释放空间 —— 把内部存储里的正文文件一并删掉，
        // 而不是只清空 DB 指针、把磁盘文件留成不可达孤儿。删除后正文需重新导入才能恢复。
        deleteBookContentFiles(id)
    }

    /**
     * 删除某本书在内部存储的正文文件：`filesDir/books/<id>/`（TXT/MD 正文）与
     * `filesDir/books/epub/<id>.epub`（EPUB 缓存，见 [SearchIndexRepository] 的缓存位置约定）。
     *
     * 全程 runCatching：文件缺失或删除失败只留告警，**不得**让「移除正文」整体失败或崩溃 ——
     * DB 指针已在上一步清空，此步失败最多是空间没释放，不影响功能正确性。
     */
    private fun deleteBookContentFiles(bookId: String) = runCatching {
        val dir = File(context.filesDir, "books/$bookId")
        if (dir.exists()) dir.deleteRecursively()
        val epub = File(context.filesDir, "books/epub/$bookId.epub")
        if (epub.exists()) epub.delete()
    }.onFailure {
        AppLog.e(
            "BookRepository",
            "移除正文时删除文件失败（book=${bookId.take(8)}）：${it.message ?: it.javaClass.simpleName}",
        )
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
        bookDao.runInTransaction {
            val deletedBook = bookDao.getDeleted().find { it.id == bookId } ?: return@runInTransaction
            val stamp = deletedBook.deleted_at ?: return@runInTransaction
            bookDao.upsert(deletedBook.copy(deleted_at = null))
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
