package com.creationreadingassistant.feature.sync.legado

import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.LegadoImportDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LegadoBatchItemKind
import com.creationreadingassistant.data.local.entity.LegadoBookBindingEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalBookSnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalDaySnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalSessionEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchItemEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSourceEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSubjectEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoader
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legado 记录导入管线（LEGADO-IMPORT-PLAN-v2 §3/§4/§6/§9）。
 *
 * - [buildPreview]：解析归档 → bookshelf 逐条按 §4 阶梯定类（精确/重定位/章级/
 *   未匹配/冲突/重复/缺失）→ 三层历史汇总与同键异值冲突清点。文档身份在预览期
 *   按书缓存（章起点表+总字符 hash），释放底层资源。
 * - [commit]：先在事务外重载选中书文档并比对身份（正文漂移 → 零写入要求重预览），
 *   再进入**单一数据库事务**：建来源库/批次 → 写主体 → 逐书重读进度行核对预览期
 *   摘要（并发自动保存 → 抛异常整体回滚）→ 写进度与绑定 → 写三层外部账本
 *   （IGNORE 幂等；同键异值按用户选择替换或记冲突证据）→ 待关联位置 → 批次项。
 *   进度写入**不走 mergeReaderProgress**（通用合并会在空时间补 now 并重算完成态），
 *   直接满足契约 §6 不变量：本地位置默认保留、last_read_at 旧保新 null、
 *   FINISHED 全保护（含空位置行）、软删进度不复活、99.4% 钳制、禁百分比推导 FINISHED。
 * - [undoBatch]：一次性撤销；「导入后基准」内容指纹失配即保留当前值并报告；
 *   外部新建行仅在无其他批次引用时物理删除；重试同批返回原结果；失败保持可撤销。
 *
 * 坐标约定（契约 §4.1）只有经合成夹具验证后才允许「精确」类；适配器默认未验证，
 * 真实数据一律走「章级」阶梯（TXT=本地章首绝对偏移，EPUB=locator v2 章内 0）。
 */
@Singleton
/** open：仅暴露 [inTransaction] 事务接缝供 JVM 测试覆写（真实 Room 事务由设备批覆盖）。 */
open class LegadoImportPipeline @Inject constructor(
    private val db: AppDatabase,
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val legadoDao: LegadoImportDao,
    private val documentLoader: ReaderDocumentLoader,
) {

    /** 事务接缝：默认 Room withTransaction；测试可覆写为直接执行（契约 §6/§9 同事务语义）。 */
    internal open suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)

    // ============================== 模型 ==============================

    /** 坐标约定开关：仅当合成夹具证明上游偏移单位与本地空间一致时置 true。 */
    data class AdapterConvention(
        val txtOffsetVerified: Boolean = false,
        val epubOffsetVerified: Boolean = false,
    )

    enum class PositionCategory(val label: String) {
        PRECISE("精确"),
        RELOCATED("精确·重定位"),
        CHAPTER("章级"),
        UNMATCHED("未匹配"),
        CONFLICT("冲突"),
        DUPLICATE("重复"),
        MISSING("缺失"),
    }

    data class SubjectKey(val name: String, val author: String, val bookUrl: String) {
        val isWorkLevel: Boolean get() = bookUrl.isEmpty()
    }

    data class BookCandidate(
        val bookId: String,
        val title: String,
        val author: String?,
        val format: String,
        val contentPresent: Boolean,
        val docIdentity: String?,
        val chapters: List<DocChapter>,
        val uniqueChapterIndex: Int?,
        val unsupportedPositionFormat: Boolean,
    )

    data class LandingPlan(
        val kind: LandingKind,
        /** TXT：已核验的本地绝对字符偏移（章级=章首，精确=章首+已验证章内偏移）。 */
        val txtAbsoluteOffset: Int?,
        /** EPUB：章节号。 */
        val chapterIndex: Int?,
        /** 进度百分比估算所用本地总章数（无章数时 0）。 */
        val localChapterCount: Int,
        val estimatedChapterIndex: Int,
    ) {
        enum class LandingKind { PRECISE_TXT, PRECISE_EPUB, CHAPTER_TXT, CHAPTER_EPUB }
    }

    data class PositionPreviewRow(
        val ordinal: Int,
        val subjectKey: SubjectKey,
        val entry: LegadoBackupParser.RawBookshelfEntry,
        val category: PositionCategory,
        val detail: String,
        val candidates: List<BookCandidate>,
        val selectedBookId: String?,
        val landing: LandingPlan?,
        val localPositionSummary: String?,
        val protection: String?,
        val importable: Boolean,
    )

    data class SnapshotConflict(
        val rowKey: String,
        val layer: String,
        val existingSummary: String,
        val incomingSummary: String,
    )

    data class HistoryPreview(
        val sessionCount: Int,
        val dayCount: Int,
        val bookCount: Int,
        val dayConflicts: List<SnapshotConflict>,
        val bookConflicts: List<SnapshotConflict>,
        val devices: Int,
    )

    data class LegadoPreview(
        val archiveSha256: String,
        val archiveLabel: String,
        val adapterVersion: String,
        val sourceId: String?,
        val sources: List<LegadoImportSourceEntity>,
        val newSourceLabelDefault: String,
        val positions: List<PositionPreviewRow>,
        val history: HistoryPreview,
        val rowErrors: LegadoBackupParser.RowErrors,
        val parsed: LegadoBackupParser.ParsedArchive,
        val convention: AdapterConvention,
    )

    data class CommitSelection(
        val sourceId: String?,
        val newSourceLabel: String?,
        val importPositionOrdinals: Set<Int>,
        val importHistory: Boolean,
        val replaceSnapshotRowKeys: Set<String>,
        /** 同名候选的预览手选：ordinal → 本地书 ID（契约 §3 不隐式消歧）。 */
        val candidateOverrides: Map<Int, String> = emptyMap(),
    )

    data class LayerCounts(var sessions: Int = 0, var refs: Int = 0, var daysCreated: Int = 0, var daysReplaced: Int = 0, var booksCreated: Int = 0, var booksReplaced: Int = 0)

    data class CommitResult(
        val ok: Boolean,
        val batchId: String?,
        val importedPositions: Int,
        val pendingPositions: Int,
        val counts: LayerCounts,
        val bindingsCreated: Int,
        val message: String,
    ) {
        companion object {
            fun fail(message: String) = CommitResult(
                false, null, 0, 0, LayerCounts(), 0, message,
            )
        }
    }

    data class UndoResult(
        val ok: Boolean,
        val status: String,
        val reverted: Int,
        val kept: Int,
        val message: String,
    )

    // ============================== 预览 ==============================

    suspend fun buildPreview(
        zipBytes: ByteArray,
        archiveLabel: String,
        sourceId: String?,
        convention: AdapterConvention = AdapterConvention(),
    ): LegadoPreview = withContext(Dispatchers.IO) {
        val parsed = LegadoBackupParser.parse(zipBytes)
        val sources = legadoDao.getSources()
        val nowIso = Instant.now().toString()
        val localBooks = bookDao.getAllActive()
        val byKey = localBooks.groupBy { bookKey(it.title, it.author) }
        val docCache = HashMap<String, DocInfo?>()

        suspend fun docOf(bookId: String): DocInfo? =
            docCache.getOrPut(bookId) { runCatching { loadDocInfo(bookId) }.getOrNull() }

        val rows = parsed.bookshelf.mapIndexed { ordinal, entry ->
            buildPositionRow(ordinal, entry, sourceId, byKey, localBooks, ::docOf, convention)
        }
        LegadoPreview(
            archiveSha256 = parsed.archiveSha256,
            archiveLabel = archiveLabel,
            adapterVersion = ADAPTER_VERSION,
            sourceId = sourceId,
            sources = sources,
            newSourceLabelDefault = archiveLabel.substringBeforeLast('.').ifBlank { "来源库" },
            positions = rows,
            history = buildHistoryPreview(parsed, sourceId),
            rowErrors = parsed.rowErrors,
            parsed = parsed,
            convention = convention,
        )
    }

    private suspend fun buildPositionRow(
        ordinal: Int,
        entry: LegadoBackupParser.RawBookshelfEntry,
        sourceId: String?,
        byKey: Map<Pair<String, String>, List<BookEntity>>,
        localBooks: List<BookEntity>,
        docOf: suspend (String) -> DocInfo?,
        convention: AdapterConvention,
    ): PositionPreviewRow {
        val subject = SubjectKey(entry.name, entry.author, entry.bookUrl)
        val key = bookKey(entry.name, entry.author)

        // 绑定复用：仅既有来源库下生效（新库不可能有绑定）。
        val boundBookId = sourceId?.let { sid ->
            val sidOfSubject = LegadoFingerprint.subjectId(sid, SUBJECT_COPY, entry.name, entry.author, entry.bookUrl)
            legadoDao.getActiveBindings(listOf(sidOfSubject)).firstOrNull()?.book_id
        }
        val candidateBooks = if (boundBookId != null) {
            localBooks.filter { it.id == boundBookId }
        } else {
            byKey[key].orEmpty()
        }

        val candidates = candidateBooks.map { b ->
            val md = isMarkdown(b.format)
            val info = if (md) null else docOf(b.id)
            BookCandidate(
                bookId = b.id,
                title = b.title,
                author = b.author,
                format = b.format,
                contentPresent = info != null,
                docIdentity = info?.identity,
                chapters = info?.chapters ?: emptyList(),
                uniqueChapterIndex = info?.let { uniqueChapterFor(entry, it.chapters) },
                unsupportedPositionFormat = md,
            )
        }

        val usable = candidates.filter { !it.unsupportedPositionFormat && it.contentPresent }
        val mdOnly = candidates.isNotEmpty() && candidates.all { it.unsupportedPositionFormat }
        val noContent = candidates.isNotEmpty() && usable.isEmpty() && !mdOnly

        var category = PositionCategory.UNMATCHED
        var detail = ""
        var selectedBookId: String? = null
        var landing: LandingPlan? = null
        var localSummary: String? = null
        var protection: String? = null
        var importable = false

        when {
            mdOnly -> {
                category = PositionCategory.MISSING
                detail = "格式暂不支持位置导入（Markdown 章节坐标与开源阅读无等价证明）；外部历史可导入"
            }
            candidates.isEmpty() -> {
                category = PositionCategory.UNMATCHED
                detail = "本地书架无书名作者匹配的书，待关联"
            }
            noContent -> {
                category = PositionCategory.UNMATCHED
                detail = "候选书正文不可读（文件缺失或损坏），待关联"
            }
            usable.size > 1 -> {
                category = PositionCategory.CONFLICT
                detail = "同名候选 ${usable.size} 本，不隐式消歧，请预览手选"
            }
            else -> {
                val candidate = usable.single()
                selectedBookId = candidate.bookId
                val uniqueChapter = candidate.uniqueChapterIndex
                if (uniqueChapter == null) {
                    category = PositionCategory.UNMATCHED
                    detail = "无法凭章标题/可信映射唯一确认本地章节，待核对"
                } else {
                    val progress = progressDao.getByBook(candidate.bookId)
                    val active = progress?.takeIf { it.deleted_at == null }
                    localSummary = active?.let { decodeLocalPosition(it)?.let(::describeLocalPosition) }
                    val target = landingFor(entry, candidate, uniqueChapter, convention)
                    landing = target
                    when {
                        active?.completion_state == ReadingCompletionState.FINISHED.storageValue -> {
                            category = PositionCategory.CONFLICT
                            protection = "FINISHED：位置与读完状态全保护（含空位置行），仅可导入外部历史"
                            detail = protection!!
                        }
                        progress?.deleted_at != null -> {
                            category = PositionCategory.CONFLICT
                            protection = "本地进度已删除（软删除），不复活；仅可导入外部历史"
                            detail = protection!!
                        }
                        localSummary != null && !sameTarget(active!!, target) -> {
                            category = PositionCategory.CONFLICT
                            detail = "本地已有有效位置（${localSummary ?: "?"}）；默认保留，确认后可替换"
                            importable = true
                        }
                        localSummary != null -> {
                            category = PositionCategory.DUPLICATE
                            detail = "目标位置与本地一致，无需写入"
                        }
                        else -> {
                            category = target.category()
                            detail = if (category == PositionCategory.PRECISE || category == PositionCategory.RELOCATED) {
                                "坐标约定经合成夹具验证，写入已核验落点"
                            } else {
                                "章级落点：只写已核验章首，不冒充精确位置"
                            }
                            importable = true
                        }
                    }
                }
            }
        }

        return PositionPreviewRow(
            ordinal = ordinal,
            subjectKey = subject,
            entry = entry,
            category = category,
            detail = detail,
            candidates = candidates,
            selectedBookId = selectedBookId,
            landing = landing,
            localPositionSummary = localSummary,
            protection = protection,
            importable = importable,
        )
    }

    private suspend fun buildHistoryPreview(
        parsed: LegadoBackupParser.ParsedArchive,
        sourceId: String?,
    ): HistoryPreview {
        val dayConflicts = ArrayList<SnapshotConflict>()
        val bookConflicts = ArrayList<SnapshotConflict>()
        if (sourceId != null) {
            val dayIds = parsed.days.map {
                LegadoFingerprint.daySnapshotId(sourceId, it.deviceId, it.bookName, it.bookAuthor, it.date)
            }
            if (dayIds.isNotEmpty()) {
                val existing = legadoDao.getDaySnapshotsByIds(dayIds).associateBy { it.snapshot_id }
                parsed.days.forEach { raw ->
                    val id = LegadoFingerprint.daySnapshotId(sourceId, raw.deviceId, raw.bookName, raw.bookAuthor, raw.date)
                    val old = existing[id] ?: return@forEach
                    if (old.read_time_ms != raw.readTime || old.read_words != raw.readWords ||
                        old.first_read_ms != raw.firstReadTime || old.last_read_ms != raw.lastReadTime
                    ) {
                        dayConflicts += SnapshotConflict(
                            id, "day",
                            "旧 ${old.read_time_ms}ms/${old.read_words}",
                            "新 ${raw.readTime}ms/${raw.readWords}（${raw.date}）",
                        )
                    }
                }
            }
            val bookIds = parsed.books.map {
                LegadoFingerprint.bookSnapshotId(sourceId, it.deviceId, it.bookName, it.bookAuthor)
            }
            if (bookIds.isNotEmpty()) {
                val existing = legadoDao.getBookSnapshotsByIds(bookIds).associateBy { it.snapshot_id }
                parsed.books.forEach { raw ->
                    val id = LegadoFingerprint.bookSnapshotId(sourceId, raw.deviceId, raw.bookName, raw.bookAuthor)
                    val old = existing[id] ?: return@forEach
                    if (old.read_time_ms != raw.readTime || old.last_read_ms != raw.lastRead) {
                        bookConflicts += SnapshotConflict(
                            id, "book",
                            "旧 ${old.read_time_ms}ms",
                            "新 ${raw.readTime}ms",
                        )
                    }
                }
            }
        }
        val devices = (parsed.sessions.map { it.deviceId } + parsed.days.map { it.deviceId } + parsed.books.map { it.deviceId }).toSet().size
        return HistoryPreview(parsed.sessions.size, parsed.days.size, parsed.books.size, dayConflicts, bookConflicts, devices)
    }

    // ============================== 提交 ==============================

    suspend fun commit(preview: LegadoPreview, selection: CommitSelection): CommitResult =
        withContext(Dispatchers.IO) {
            val nowIso = Instant.now().toString()
            val json = payloadJson
            val counts = LayerCounts()

            // 内容重验（事务外 IO）：选中书重新加载，身份与预览不一致 → 零写入。
            val selectedRows = preview.positions.filter { it.ordinal in selection.importPositionOrdinals }
            val freshLandings = HashMap<Int, LandingPlan>()
            for (row in selectedRows) {
                val bookId = selection.candidateOverrides[row.ordinal] ?: row.selectedBookId ?: continue
                val candidate = row.candidates.firstOrNull { it.bookId == bookId } ?: continue
                if (candidate.unsupportedPositionFormat) continue
                val fresh = runCatching { loadDocInfo(bookId) }.getOrNull()
                if (fresh == null || fresh.identity != candidate.docIdentity) {
                    return@withContext CommitResult.fail(
                        "《${neutralTitle(candidate.title)}》正文在预览后发生变化，本批零写入；请重新预览",
                    )
                }
                // 手选候选的落点在提交期用新鲜文档重建（预览期无从选择）。
                val unique = uniqueChapterFor(row.entry, fresh.chapters)
                    ?: return@withContext CommitResult.fail(
                        "《${neutralTitle(candidate.title)}》无法唯一确认章节，本批零写入；请重新预览",
                    )
                freshLandings[row.ordinal] = landingFor(row.entry, candidate.copy(chapters = fresh.chapters), unique, preview.convention)
            }
            if (selection.sourceId != null && legadoDao.getSource(selection.sourceId) == null) {
                return@withContext CommitResult.fail("所选来源库不存在，请重新预览")
            }

            val batchId = UUID.randomUUID().toString()
            var imported = 0
            var bindingsCreated = 0
            val items = ArrayList<LegadoImportBatchItemEntity>()
            val pendingRows = ArrayList<Pair<PositionPreviewRow, String>>()
            val sourceId = selection.sourceId ?: UUID.randomUUID().toString()

            try {
                inTransaction {
                    // 来源库（新建时）与批次先行，FK 可满足。
                    if (selection.sourceId == null) {
                        legadoDao.insertSource(
                            LegadoImportSourceEntity(
                                source_id = sourceId,
                                label = selection.newSourceLabel?.trim().takeUnless { it.isNullOrBlank() }
                                    ?: preview.newSourceLabelDefault,
                                adapter_version = preview.adapterVersion,
                                created_at = nowIso,
                            ),
                        )
                    }
                    legadoDao.insertBatch(
                        LegadoImportBatchEntity(
                            batch_id = batchId,
                            source_id = sourceId,
                            adapter_version = preview.adapterVersion,
                            archive_fingerprint = preview.archiveSha256,
                            archive_label = preview.archiveLabel,
                            created_at = nowIso,
                            status = BATCH_COMMITTED,
                            undo_note = null,
                        ),
                    )

                    // ---- 主体 ----
                    val subjectRows = ArrayList<LegadoImportSubjectEntity>()
                    preview.positions.forEach { row ->
                        subjectRows += subjectOf(sourceId, row.subjectKey, row.entry.origin.takeUnless { row.subjectKey.isWorkLevel }, nowIso)
                    }
                    if (selection.importHistory) historySubjects(preview.parsed, sourceId, nowIso, subjectRows)
                    legadoDao.insertSubjectsIfAbsent(subjectRows)
                    fun subjectIdOf(key: SubjectKey): String = LegadoFingerprint.subjectId(
                        sourceId,
                        if (key.isWorkLevel) SUBJECT_WORK else SUBJECT_COPY,
                        key.name, key.author, key.bookUrl,
                    )

                    // ---- 位置 ----
                    preview.positions.forEach { row ->
                        val subjectId = subjectIdOf(row.subjectKey)
                        val overriddenBookId = selection.candidateOverrides[row.ordinal]
                        val resolvedBookId = overriddenBookId ?: row.selectedBookId
                        val effectiveLanding = overriddenBookId?.let { freshLandings[row.ordinal] } ?: row.landing
                        val placed = row.ordinal in selection.importPositionOrdinals &&
                            resolvedBookId != null && effectiveLanding != null &&
                            (row.importable || overriddenBookId != null)
                        if (!placed) {
                            pendingRows += row to subjectId
                            return@forEach
                        }
                        val bookId = resolvedBookId!!
                        val prior = progressDao.getByBook(bookId)
                        val priorActive = prior?.takeIf { it.deleted_at == null }
                        // 事务内重验：预览期摘要与当前一致（并发保存/新增行 → 整体回滚）。
                        // 对所有类别生效（契约 §6：发现影响已选操作的变化，事务零写入）。
                        val currentSummary = priorActive?.let { decodeLocalPosition(it)?.let(::describeLocalPosition) }
                        if (currentSummary != row.localPositionSummary) {
                            throw IllegalStateException("本地进度在预览后被修改（并发保存），本批零写入")
                        }
                        if (priorActive?.completion_state == ReadingCompletionState.FINISHED.storageValue) {
                            throw IllegalStateException("FINISHED 书位置受保护，不应出现在选中集")
                        }
                        val landing = effectiveLanding!!
                        val locatorJson = locatorFor(landing)
                        val incoming = ReadingProgressEntity(
                            book_id = bookId,
                            progress_percent = percentFor(landing),
                            last_read_at = priorActive?.last_read_at,
                            total_reading_time_ms = priorActive?.total_reading_time_ms ?: 0L,
                            completion_state = priorActive?.completion_state
                                ?: ReadingCompletionState.READING.storageValue,
                            current_location_json = locatorJson,
                            payload = priorActive?.payload,
                            revision = (prior?.revision ?: 0) + 1,
                            device_id = priorActive?.device_id,
                            updated_at = nowIso,
                            completed_at = priorActive?.completed_at,
                            deleted_at = null,
                        )
                        progressDao.upsert(incoming)
                        imported++
                        items += LegadoImportBatchItemEntity(
                            item_id = UUID.randomUUID().toString(),
                            batch_id = batchId,
                            subject_id = subjectId,
                            kind = if (priorActive == null) {
                                LegadoBatchItemKind.PROGRESS_CREATED
                            } else {
                                LegadoBatchItemKind.PROGRESS_REPLACED
                            },
                            book_id = bookId,
                            row_key = null,
                            payload_json = json.encodeToString(
                                ProgressPayload.serializer(),
                                ProgressPayload(
                                    prior = prior?.let { json.encodeToString(ReadingProgressEntity.serializer(), it) },
                                    afterFingerprint = LegadoFingerprint.contentFingerprint(
                                        bookId, locatorJson ?: "", incoming.revision.toString(),
                                    ),
                                ),
                            ),
                        )
                        if (legadoDao.getBinding(subjectId) == null) {
                            legadoDao.insertBinding(
                                LegadoBookBindingEntity(
                                    binding_id = subjectId,
                                    subject_id = subjectId,
                                    book_id = bookId,
                                    confirmed_at = nowIso,
                                    confirmation = CONFIRMATION,
                                    content_identity = row.candidates.firstOrNull { it.bookId == bookId }?.docIdentity,
                                    content_version = 1,
                                    needs_review = 0,
                                ),
                            )
                            bindingsCreated++
                            items += LegadoImportBatchItemEntity(
                                item_id = UUID.randomUUID().toString(),
                                batch_id = batchId,
                                subject_id = subjectId,
                                kind = LegadoBatchItemKind.BINDING_CREATED,
                                book_id = bookId,
                                row_key = null,
                                payload_json = json.encodeToString(
                                    BindingPayload.serializer(), BindingPayload(bookId),
                                ),
                            )
                        }
                    }

                    // ---- 三层历史 ----
                    if (selection.importHistory) {
                        writeHistory(preview.parsed, selection, sourceId, batchId, nowIso, items, counts)
                    }

                    // ---- 待关联位置 ----
                    pendingRows.forEach { (row, subjectId) ->
                        items += LegadoImportBatchItemEntity(
                            item_id = UUID.randomUUID().toString(),
                            batch_id = batchId,
                            subject_id = subjectId,
                            kind = LegadoBatchItemKind.PENDING_POSITION,
                            book_id = null,
                            row_key = null,
                            payload_json = json.encodeToString(
                                PendingPositionPayload.serializer(),
                                PendingPositionPayload(
                                    bookName = row.subjectKey.name,
                                    bookAuthor = row.subjectKey.author,
                                    bookUrl = row.subjectKey.bookUrl,
                                    chapterIndex = row.entry.durChapterIndex,
                                    chapterPos = row.entry.durChapterPos,
                                    durChapterTime = row.entry.durChapterTime,
                                    durChapterTitle = row.entry.durChapterTitle,
                                    reason = "${row.category.label}｜${row.detail}",
                                ),
                            ),
                        )
                    }

                    legadoDao.insertBatchItems(items)
                }
            } catch (t: Throwable) {
                return@withContext CommitResult.fail("导入事务失败，零写入：${t.message ?: t::class.simpleName}")
            }

            CommitResult(
                ok = true,
                batchId = batchId,
                importedPositions = imported,
                pendingPositions = pendingRows.size,
                counts = counts,
                bindingsCreated = bindingsCreated,
                message = buildString {
                    append("已导入 ").append(imported).append(" 条位置")
                    if (selection.importHistory) {
                        append("；外部历史：会话 +").append(counts.sessions)
                        append("/日 +").append(counts.daysCreated)
                        if (counts.daysReplaced > 0) append("/日替换 ").append(counts.daysReplaced)
                        append("/书 +").append(counts.booksCreated)
                        if (counts.booksReplaced > 0) append("/书替换 ").append(counts.booksReplaced)
                        if (counts.refs > 0) append("（幂等引用 ").append(counts.refs).append('）')
                    }
                    if (pendingRows.isNotEmpty()) append("；待关联 ").append(pendingRows.size)
                },
            )
        }

    // ---- 只读委托（UI 订阅） ----

    fun observePendingPositions() = legadoDao.observePendingPositions()

    fun observeBatches() = legadoDao.observeBatches()

    fun observeSources() = legadoDao.observeSources()

    suspend fun deletePendingPosition(itemId: String) = legadoDao.deletePendingPosition(itemId)

    suspend fun externalCounts(sourceId: String): Triple<Long, Long, Long> = Triple(
        legadoDao.countSessionsOf(sourceId),
        legadoDao.countDaySnapshotsOf(sourceId),
        legadoDao.countBookSnapshotsOf(sourceId),
    )

    // ============================== 撤销 ==============================

    suspend fun undoBatch(batchId: String): UndoResult = withContext(Dispatchers.IO) {
        val json = payloadJson
        val existing = legadoDao.getBatch(batchId)
            ?: return@withContext UndoResult(false, BATCH_COMMITTED, 0, 0, "批次不存在")
        if (existing.status != BATCH_COMMITTED) {
            return@withContext UndoResult(
                true, existing.status, 0, 0, existing.undo_note ?: "批次已撤销（重试返回原结果）",
            )
        }
        var reverted = 0
        var kept = 0
        val notes = ArrayList<String>()
        inTransaction {
            val items = legadoDao.getBatchItems(batchId)
            val nowIso = Instant.now().toString()

            items.groupBy { it.kind }.forEach { (kind, group) ->
                when (kind) {
                    LegadoBatchItemKind.PROGRESS_REPLACED, LegadoBatchItemKind.PROGRESS_CREATED -> {
                        group.forEach { item ->
                            val payload = runCatching {
                                json.decodeFromString(ProgressPayload.serializer(), item.payload_json)
                            }.getOrNull()
                            val bookId = item.book_id
                            if (payload == null || bookId == null) {
                                kept++; notes += "批次项载荷损坏，保留现状"; return@forEach
                            }
                            val current = progressDao.getByBook(bookId)
                            if (current == null || current.deleted_at != null) {
                                kept++; notes += "书或进度已不在，保留现状"
                                return@forEach
                            }
                            val afterFp = LegadoFingerprint.contentFingerprint(
                                bookId, current.current_location_json ?: "", current.revision.toString(),
                            )
                            if (afterFp != payload.afterFingerprint) {
                                kept++; notes += "《${neutralTitle(item.subjectKeyOr("书"))}》导入后已被阅读活动修改，保留当前值"
                                return@forEach
                            }
                            val priorEntity = payload.prior?.let {
                                runCatching { json.decodeFromString(ReadingProgressEntity.serializer(), it) }.getOrNull()
                            }
                            if (priorEntity == null) {
                                // 原无行：按项目既有删除/同步语义软删除本批新建行。
                                progressDao.upsert(
                                    current.copy(deleted_at = nowIso, revision = current.revision + 1, updated_at = nowIso),
                                )
                            } else {
                                progressDao.upsert(priorEntity)
                            }
                            reverted++
                        }
                    }
                    LegadoBatchItemKind.DAY_REPLACED, LegadoBatchItemKind.BOOK_REPLACED -> {
                        group.forEach { item ->
                            val payload = runCatching {
                                json.decodeFromString(SnapshotPayload.serializer(), item.payload_json)
                            }.getOrNull()
                            val rowKey = item.row_key
                            if (payload == null || rowKey == null) { kept++; return@forEach }
                            val prior = runCatching {
                                json.decodeFromString(SnapshotRowPayload.serializer(), payload.priorJson)
                            }.getOrNull() ?: run { kept++; return@forEach }
                            if (kind == LegadoBatchItemKind.DAY_REPLACED) {
                                val fresh = legadoDao.getDaySnapshotsByIds(listOf(rowKey)).firstOrNull()
                                    ?: run { kept++; return@forEach }
                                val afterFp = LegadoFingerprint.contentFingerprint(fresh.read_time_ms.toString(), fresh.updated_at)
                                if (afterFp != payload.afterFingerprint) { kept++; return@forEach }
                                legadoDao.restoreDaySnapshot(prior.toDay())
                            } else {
                                val fresh = legadoDao.getBookSnapshotsByIds(listOf(rowKey)).firstOrNull()
                                    ?: run { kept++; return@forEach }
                                val afterFp = LegadoFingerprint.contentFingerprint(fresh.read_time_ms.toString(), fresh.updated_at)
                                if (afterFp != payload.afterFingerprint) { kept++; return@forEach }
                                legadoDao.restoreBookSnapshot(prior.toBook())
                            }
                            reverted++
                        }
                    }
                    LegadoBatchItemKind.SESSION_CREATED, LegadoBatchItemKind.DAY_CREATED, LegadoBatchItemKind.BOOK_CREATED -> {
                        // 该 kind 聚合一次；每行独立判断其他批次引用（一行被引用不拖累整组）。
                        val rowKeys = group.mapNotNull { it.row_key }
                        var removedHere = 0
                        var keptHere = 0
                        rowKeys.forEach { key ->
                            val referenced = legadoDao.countOtherRefs(listOf(key), batchId) > 0
                            if (referenced) {
                                keptHere++
                            } else {
                                when (kind) {
                                    LegadoBatchItemKind.SESSION_CREATED -> legadoDao.deleteSessions(listOf(key))
                                    LegadoBatchItemKind.DAY_CREATED -> legadoDao.deleteDaySnapshots(listOf(key))
                                    else -> legadoDao.deleteBookSnapshots(listOf(key))
                                }
                                removedHere++
                            }
                        }
                        reverted += removedHere
                        kept += keptHere
                        if (keptHere > 0) notes += "部分外部行被其他批次引用，保留"
                    }
                    LegadoBatchItemKind.BINDING_CREATED -> {
                        group.forEach { item ->
                            val binding = item.subject_id?.let { legadoDao.getBinding(it) }
                            val payload = runCatching {
                                json.decodeFromString(BindingPayload.serializer(), item.payload_json)
                            }.getOrNull()
                            if (binding != null && payload != null &&
                                binding.book_id == payload.bookId && binding.confirmation == CONFIRMATION
                            ) {
                                legadoDao.deleteBinding(binding.binding_id)
                                reverted++
                            } else {
                                kept++
                            }
                        }
                    }
                    // SESSION_REF / DAY_REF / BOOK_REF / SNAPSHOT_CONFLICT / PENDING_POSITION：
                    // 随批次项物理删除（该批次的贡献与证据整体回退）。
                }
            }
            val status = if (kept == 0) BATCH_UNDONE_FULL else BATCH_UNDONE_PARTIAL
            val note = buildString {
                append("已撤销 ").append(reverted).append(" 项")
                if (kept > 0) {
                    append("；保留 ").append(kept).append(" 项：")
                    append(notes.distinct().take(4).joinToString("；"))
                }
            }
            legadoDao.finishBatch(batchId, status, note)
        }
        val after = legadoDao.getBatch(batchId)
        UndoResult(true, after?.status ?: BATCH_UNDONE_FULL, reverted, kept, notes.distinct().joinToString("；").ifEmpty { "撤销完成" })
    }

    private fun groupRowKeys(items: List<LegadoImportBatchItemEntity>, kind: String): List<String> =
        items.filter { it.kind == kind }.mapNotNull { it.row_key }

    // ============================== 内部 ==============================

    private data class DocInfo(val chapters: List<DocChapter>, val totalChars: Int, val identity: String)

    private suspend fun loadDocInfo(bookId: String): DocInfo {
        val loaded = documentLoader.load(bookId)
        try {
            val (chapters, totalChars) = when (val content = loaded.content) {
                is ReaderLoadedContent.Text ->
                    content.streamingDocument?.let { it.chapters to it.totalChars }
                        ?: (content.preDetectedChapters to content.fullText.length)
                is ReaderLoadedContent.Epub ->
                    content.document.chapters to content.document.totalChars
                is ReaderLoadedContent.Markdown ->
                    content.document.chapters to content.document.totalChars
            }
            return DocInfo(chapters, totalChars, LegadoFingerprint.docIdentity(totalChars, chapters.map { it.startOffset }))
        } finally {
            loaded.content.release()
        }
    }

    /** 唯一章确认：章标题精确匹配唯一命中；无标题时仅当上游总章数与本地一致才信索引。 */
    private fun uniqueChapterFor(entry: LegadoBackupParser.RawBookshelfEntry, chapters: List<DocChapter>): Int? {
        val title = entry.durChapterTitle?.trim()
        if (!title.isNullOrBlank()) {
            val hits = chapters.filter { it.title.trim() == title }
            if (hits.size == 1) return hits.single().index
            if (hits.size > 1) return null
        }
        if (entry.durChapterIndex !in chapters.indices) return null
        val structureAgrees = entry.totalChapterNum == null || entry.totalChapterNum == chapters.size
        return if (structureAgrees) entry.durChapterIndex else null
    }

    private fun landingFor(
        entry: LegadoBackupParser.RawBookshelfEntry,
        candidate: BookCandidate,
        uniqueChapter: Int,
        convention: AdapterConvention,
    ): LandingPlan {
        val chapters = candidate.chapters
        val start = chapters.getOrNull(uniqueChapter)?.startOffset ?: 0
        val estimatedChapterIndex = uniqueChapter
        return if (candidate.format.equals("epub", ignoreCase = true)) {
            if (convention.epubOffsetVerified) {
                LandingPlan(LandingPlan.LandingKind.PRECISE_EPUB, null, uniqueChapter, chapters.size, estimatedChapterIndex)
            } else {
                LandingPlan(LandingPlan.LandingKind.CHAPTER_EPUB, null, uniqueChapter, chapters.size, estimatedChapterIndex)
            }
        } else {
            if (convention.txtOffsetVerified) {
                val inChapter = entry.durChapterPos
                val charCount = chapters.getOrNull(uniqueChapter)?.charCount ?: 0
                val precise = charCount <= 0 || inChapter <= charCount
                if (precise) {
                    LandingPlan(LandingPlan.LandingKind.PRECISE_TXT, start + inChapter, uniqueChapter, chapters.size, estimatedChapterIndex)
                } else {
                    // 越界 → 降级章级（禁止钳制伪造命中）。
                    LandingPlan(LandingPlan.LandingKind.CHAPTER_TXT, start, uniqueChapter, chapters.size, estimatedChapterIndex)
                }
            } else {
                LandingPlan(LandingPlan.LandingKind.CHAPTER_TXT, start, uniqueChapter, chapters.size, estimatedChapterIndex)
            }
        }
    }

    private fun LandingPlan.category(): PositionCategory = when (kind) {
        LandingPlan.LandingKind.PRECISE_TXT, LandingPlan.LandingKind.PRECISE_EPUB -> PositionCategory.PRECISE
        else -> PositionCategory.CHAPTER
    }

    private fun sameTarget(active: ReadingProgressEntity, target: LandingPlan): Boolean {
        val local = decodeLocalPosition(active) ?: return false
        return when (target.kind) {
            LandingPlan.LandingKind.PRECISE_TXT, LandingPlan.LandingKind.CHAPTER_TXT ->
                local.absoluteOffset != null && local.absoluteOffset == target.txtAbsoluteOffset
            else ->
                local.chapterIndex == target.chapterIndex && local.charOffset == 0
        }
    }

    private data class DecodedLocalPosition(val absoluteOffset: Int?, val chapterIndex: Int?, val charOffset: Int?)

    private fun decodeLocalPosition(progress: ReadingProgressEntity): DecodedLocalPosition? {
        val json = progress.current_location_json ?: return null
        LocatorCodec.locatorFromProgress(json)?.let { v2 ->
            return DecodedLocalPosition(v2.legacyOffset, v2.chapterIndex, v2.charOffset)
        }
        // legacy-only 进度（如 {"offset":N}）：顶层字段正则解码。
        LocatorCodec.decode(json)?.let { legacy ->
            return DecodedLocalPosition(legacy.legacyOffset, legacy.chapterIndex, legacy.charOffset)
        }
        val marker = LegadoProgressMarker.decode(json) ?: return null
        return DecodedLocalPosition(null, marker.chapterIndex, marker.charOffset)
    }

    private fun describeLocalPosition(p: DecodedLocalPosition): String = when {
        p.chapterIndex != null && p.chapterIndex > 0 && p.charOffset != null ->
            "第 ${p.chapterIndex + 1} 章 第 ${p.charOffset} 字"
        p.absoluteOffset != null -> "TXT 全书偏移 ${p.absoluteOffset}"
        p.chapterIndex != null && p.charOffset != null -> "第 ${p.chapterIndex + 1} 章 第 ${p.charOffset} 字"
        else -> "未知坐标"
    }

    private fun locatorFor(landing: LandingPlan): String = when (landing.kind) {
        LandingPlan.LandingKind.PRECISE_TXT, LandingPlan.LandingKind.CHAPTER_TXT ->
            LocatorBuilder.progressJson(
                legacyOffset = landing.txtAbsoluteOffset ?: 0,
                chapterIndex = 0,
                charOffset = landing.txtAbsoluteOffset ?: 0,
                globalOffset = landing.txtAbsoluteOffset ?: 0,
                legacyChapter = null,
            )
        LandingPlan.LandingKind.CHAPTER_EPUB ->
            LocatorBuilder.progressJson(
                legacyOffset = 0,
                chapterIndex = landing.chapterIndex ?: 0,
                charOffset = 0,
                globalOffset = null,
                legacyChapter = landing.chapterIndex ?: 0,
            )
        LandingPlan.LandingKind.PRECISE_EPUB ->
            LocatorBuilder.progressJson(
                legacyOffset = 0,
                chapterIndex = landing.chapterIndex ?: 0,
                charOffset = 0,
                globalOffset = null,
                legacyChapter = landing.chapterIndex ?: 0,
            )
    }

    private fun percentFor(landing: LandingPlan): Float {
        val total = landing.localChapterCount
        if (total <= 0) return 0f
        return ((landing.estimatedChapterIndex.toFloat() / total.toFloat()) * 100f)
            .coerceIn(0f, MAX_IMPORTED_PERCENT)
    }

    private suspend fun writeHistory(
        parsed: LegadoBackupParser.ParsedArchive,
        selection: CommitSelection,
        sourceId: String,
        batchId: String,
        nowIso: String,
        items: MutableList<LegadoImportBatchItemEntity>,
        counts: LayerCounts,
    ) {
        val json = payloadJson

        // ---- 会话 ----
        val sessionRows = parsed.sessions.map { raw ->
            val subjectId = LegadoFingerprint.subjectId(
                sourceId, if (raw.bookUrl.isBlank()) SUBJECT_WORK else SUBJECT_COPY,
                raw.bookName, raw.bookAuthor, raw.bookUrl,
            )
            subjectId to LegadoExternalSessionEntity(
                session_id = LegadoFingerprint.sessionRowId(
                    sourceId,
                    LegadoFingerprint.sessionFingerprint(
                        raw.deviceId, raw.bookName, raw.bookAuthor, raw.bookUrl,
                        raw.startTime, raw.endTime, raw.words,
                    ),
                ),
                source_id = sourceId,
                subject_id = subjectId,
                device_id = raw.deviceId,
                start_ms = raw.startTime,
                end_ms = raw.endTime,
                chapter_index = raw.words,
                created_by_batch = batchId,
                created_at = nowIso,
            )
        }
        if (sessionRows.isNotEmpty()) {
            val results = legadoDao.insertSessionsIfAbsent(sessionRows.map { it.second })
            results.forEachIndexed { i, rc ->
                val kind = if (rc >= 0) LegadoBatchItemKind.SESSION_CREATED else LegadoBatchItemKind.SESSION_REF
                items += batchItem(batchId, sessionRows[i].first, kind, sessionRows[i].second.session_id, "{}")
                if (rc >= 0) counts.sessions++ else counts.refs++
            }
        }

        // ---- 按日快照 ----
        val dayRows = parsed.days.map { raw ->
            val subjectId = LegadoFingerprint.subjectId(sourceId, SUBJECT_WORK, raw.bookName, raw.bookAuthor, "")
            subjectId to LegadoExternalDaySnapshotEntity(
                snapshot_id = LegadoFingerprint.daySnapshotId(sourceId, raw.deviceId, raw.bookName, raw.bookAuthor, raw.date),
                source_id = sourceId,
                subject_id = subjectId,
                device_id = raw.deviceId,
                date = raw.date,
                read_time_ms = raw.readTime,
                read_words = raw.readWords,
                first_read_ms = raw.firstReadTime,
                last_read_ms = raw.lastReadTime,
                created_by_batch = batchId,
                updated_at = nowIso,
            )
        }
        if (dayRows.isNotEmpty()) {
            val results = legadoDao.insertDaySnapshotsIfAbsent(dayRows.map { it.second })
            results.forEachIndexed { i, rc ->
                val (subjectId, row) = dayRows[i]
                when {
                    rc >= 0 -> {
                        items += batchItem(batchId, subjectId, LegadoBatchItemKind.DAY_CREATED, row.snapshot_id, "{}")
                        counts.daysCreated++
                    }
                    else -> {
                        val existing = legadoDao.getDaySnapshotsByIds(listOf(row.snapshot_id)).firstOrNull()
                        val same = existing != null &&
                            existing.read_time_ms == row.read_time_ms && existing.read_words == row.read_words &&
                            existing.first_read_ms == row.first_read_ms && existing.last_read_ms == row.last_read_ms
                        when {
                            same -> items += batchItem(batchId, subjectId, LegadoBatchItemKind.DAY_REF, row.snapshot_id, "{}")
                            row.snapshot_id in selection.replaceSnapshotRowKeys -> {
                                val priorJson = existing?.let {
                                    json.encodeToString(SnapshotRowPayload.serializer(), SnapshotRowPayload.of(it))
                                } ?: "{}"
                                legadoDao.replaceDaySnapshot(
                                    row.snapshot_id, row.read_time_ms, row.read_words,
                                    row.first_read_ms, row.last_read_ms, batchId, nowIso,
                                )
                                items += batchItem(
                                    batchId, subjectId, LegadoBatchItemKind.DAY_REPLACED, row.snapshot_id,
                                    json.encodeToString(
                                        SnapshotPayload.serializer(),
                                        SnapshotPayload(
                                            priorJson = priorJson,
                                            afterFingerprint = LegadoFingerprint.contentFingerprint(row.read_time_ms.toString(), nowIso),
                                        ),
                                    ),
                                )
                                counts.daysReplaced++
                            }
                            else -> items += batchItem(
                                batchId, subjectId, LegadoBatchItemKind.SNAPSHOT_CONFLICT, row.snapshot_id,
                                json.encodeToString(
                                    ConflictPayload.serializer(),
                                    ConflictPayload(layer = "day", rowKey = row.snapshot_id, note = "同键异值，未替换（保留来源证据）"),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ---- 书级快照 ----
        val bookRows = parsed.books.map { raw ->
            val subjectId = LegadoFingerprint.subjectId(sourceId, SUBJECT_WORK, raw.bookName, raw.bookAuthor, "")
            subjectId to LegadoExternalBookSnapshotEntity(
                snapshot_id = LegadoFingerprint.bookSnapshotId(sourceId, raw.deviceId, raw.bookName, raw.bookAuthor),
                source_id = sourceId,
                subject_id = subjectId,
                device_id = raw.deviceId,
                read_time_ms = raw.readTime,
                last_read_ms = raw.lastRead,
                created_by_batch = batchId,
                updated_at = nowIso,
            )
        }
        if (bookRows.isNotEmpty()) {
            val results = legadoDao.insertBookSnapshotsIfAbsent(bookRows.map { it.second })
            results.forEachIndexed { i, rc ->
                val (subjectId, row) = bookRows[i]
                when {
                    rc >= 0 -> {
                        items += batchItem(batchId, subjectId, LegadoBatchItemKind.BOOK_CREATED, row.snapshot_id, "{}")
                        counts.booksCreated++
                    }
                    else -> {
                        val existing = legadoDao.getBookSnapshotsByIds(listOf(row.snapshot_id)).firstOrNull()
                        val same = existing != null &&
                            existing.read_time_ms == row.read_time_ms && existing.last_read_ms == row.last_read_ms
                        when {
                            same -> items += batchItem(batchId, subjectId, LegadoBatchItemKind.BOOK_REF, row.snapshot_id, "{}")
                            row.snapshot_id in selection.replaceSnapshotRowKeys -> {
                                val priorJson = existing?.let {
                                    json.encodeToString(SnapshotRowPayload.serializer(), SnapshotRowPayload.of(it))
                                } ?: "{}"
                                legadoDao.replaceBookSnapshot(row.snapshot_id, row.read_time_ms, row.last_read_ms, batchId, nowIso)
                                items += batchItem(
                                    batchId, subjectId, LegadoBatchItemKind.BOOK_REPLACED, row.snapshot_id,
                                    json.encodeToString(
                                        SnapshotPayload.serializer(),
                                        SnapshotPayload(
                                            priorJson = priorJson,
                                            afterFingerprint = LegadoFingerprint.contentFingerprint(row.read_time_ms.toString(), nowIso),
                                        ),
                                    ),
                                )
                                counts.booksReplaced++
                            }
                            else -> items += batchItem(
                                batchId, subjectId, LegadoBatchItemKind.SNAPSHOT_CONFLICT, row.snapshot_id,
                                json.encodeToString(
                                    ConflictPayload.serializer(),
                                    ConflictPayload(layer = "book", rowKey = row.snapshot_id, note = "同键异值，未替换（保留来源证据）"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun historySubjects(
        parsed: LegadoBackupParser.ParsedArchive,
        sourceId: String,
        nowIso: String,
        into: MutableList<LegadoImportSubjectEntity>,
    ) {
        parsed.sessions.forEach { raw ->
            into += subjectOf(sourceId, SubjectKey(raw.bookName, raw.bookAuthor, raw.bookUrl), null, nowIso)
        }
        parsed.days.forEach { raw ->
            into += subjectOf(sourceId, SubjectKey(raw.bookName, raw.bookAuthor, ""), null, nowIso)
        }
        parsed.books.forEach { raw ->
            into += subjectOf(sourceId, SubjectKey(raw.bookName, raw.bookAuthor, ""), null, nowIso)
        }
    }

    private fun subjectOf(sourceId: String, key: SubjectKey, origin: String?, nowIso: String) =
        LegadoImportSubjectEntity(
            subject_id = LegadoFingerprint.subjectId(
                sourceId,
                if (key.isWorkLevel) SUBJECT_WORK else SUBJECT_COPY,
                key.name, key.author, key.bookUrl,
            ),
            source_id = sourceId,
            kind = if (key.isWorkLevel) SUBJECT_WORK else SUBJECT_COPY,
            book_name = key.name,
            book_author = key.author,
            book_url = key.bookUrl,
            origin = origin,
            created_at = nowIso,
        )

    private fun batchItem(batchId: String, subjectId: String?, kind: String, rowKey: String?, payload: String) =
        LegadoImportBatchItemEntity(
            item_id = UUID.randomUUID().toString(),
            batch_id = batchId,
            subject_id = subjectId,
            kind = kind,
            book_id = null,
            row_key = rowKey,
            payload_json = payload,
        )

    private fun LegadoImportBatchItemEntity.subjectKeyOr(fallback: String): String =
        subject_id?.takeLast(6) ?: fallback

    // ---- 载荷 ----

    @kotlinx.serialization.Serializable
    data class ProgressPayload(val prior: String?, val afterFingerprint: String)

    @kotlinx.serialization.Serializable
    data class BindingPayload(val bookId: String)

    @kotlinx.serialization.Serializable
    data class PendingPositionPayload(
        val bookName: String,
        val bookAuthor: String,
        val bookUrl: String,
        val chapterIndex: Int?,
        val chapterPos: Int?,
        val durChapterTime: Long?,
        val durChapterTitle: String?,
        val reason: String,
    )

    @kotlinx.serialization.Serializable
    data class SnapshotPayload(val priorJson: String, val afterFingerprint: String)

    @kotlinx.serialization.Serializable
    data class ConflictPayload(val layer: String, val rowKey: String, val note: String)

    @kotlinx.serialization.Serializable
    data class SnapshotRowPayload(
        val snapshot_id: String,
        val source_id: String,
        val subject_id: String,
        val device_id: String,
        val date: String? = null,
        val read_time_ms: Long,
        val read_words: Long? = null,
        val first_read_ms: Long? = null,
        val last_read_ms: Long? = null,
        val created_by_batch: String?,
        val updated_at: String,
    ) {
        fun toDay() = LegadoExternalDaySnapshotEntity(
            snapshot_id = snapshot_id, source_id = source_id, subject_id = subject_id,
            device_id = device_id, date = date ?: "", read_time_ms = read_time_ms,
            read_words = read_words ?: 0, first_read_ms = first_read_ms ?: 0,
            last_read_ms = last_read_ms ?: 0, created_by_batch = created_by_batch, updated_at = updated_at,
        )

        fun toBook() = LegadoExternalBookSnapshotEntity(
            snapshot_id = snapshot_id, source_id = source_id, subject_id = subject_id,
            device_id = device_id, read_time_ms = read_time_ms,
            last_read_ms = last_read_ms ?: 0, created_by_batch = created_by_batch, updated_at = updated_at,
        )

        companion object {
            fun of(e: LegadoExternalDaySnapshotEntity) = SnapshotRowPayload(
                e.snapshot_id, e.source_id, e.subject_id, e.device_id, e.date,
                e.read_time_ms, e.read_words, e.first_read_ms, e.last_read_ms, e.created_by_batch, e.updated_at,
            )

            fun of(e: LegadoExternalBookSnapshotEntity) = SnapshotRowPayload(
                e.snapshot_id, e.source_id, e.subject_id, e.device_id, null,
                e.read_time_ms, null, null, e.last_read_ms, e.created_by_batch, e.updated_at,
            )
        }
    }

    companion object {
        const val ADAPTER_VERSION = "md3@4efe2496"
        const val SUBJECT_COPY = "copy"
        const val SUBJECT_WORK = "work"
        const val CONFIRMATION = "preview-user-confirm"
        const val BATCH_COMMITTED = "committed"
        const val BATCH_UNDONE_FULL = "undone_full"
        const val BATCH_UNDONE_PARTIAL = "undone_partial"
        const val MAX_IMPORTED_PERCENT = 99.4f
        private val payloadJson = Json { ignoreUnknownKeys = true }

        private fun bookKey(title: String, author: String?): Pair<String, String> =
            normalize(title) to normalize(author.orEmpty())

        private fun normalize(value: String): String =
            value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

        private fun isMarkdown(format: String): Boolean = format.lowercase() in setOf("md", "markdown")

        /** 报告/日志用中性代称：不落私人书名（契约 §8）。 */
        private fun neutralTitle(title: String): String =
            title.take(2) + "…(" + title.length + "字)"
    }
}
