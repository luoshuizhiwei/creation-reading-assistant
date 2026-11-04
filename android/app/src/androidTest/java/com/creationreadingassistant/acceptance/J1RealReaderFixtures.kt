package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.Process
import androidx.room.Room
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageRow
import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.feature.search.RawTermMatch
import com.creationreadingassistant.feature.search.SearchHitSelection
import com.creationreadingassistant.feature.search.SearchOffsets
import com.creationreadingassistant.feature.search.SearchTextBasis
import com.creationreadingassistant.feature.search.SearchTokenizer
import com.creationreadingassistant.ui.onboarding.ONBOARDING_PREFS
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v6
 *
 * J1 真实生产阅读器旅程验收的专属夹具、数据库捕获、生命周期账本与证据记录管理工具。
 *
 * ## v6 核心改进与有限合同
 * 1. 目标位置实际提交（SAVE_CAUSE=UNATTRIBUTED）：
 *    - 从真实生产 UI 及非空正文 marker 证明当前阅读位置；
 *    - 依据实际夹具和生产 locator 合同检查目标位置，使用有界等待核验 DAO 行解码 locator 与目标位置相符；
 *    - 该证据标明 SAVE_CAUSE=UNATTRIBUTED，不假定证明退出保存由特定 Back 事件专属完成；
 *    - 禁止在旅程内直接向 reading_progress 写入数据伪造保存结果。
 * 2. 临时查阅冻结与返回后真实推进：
 *    - 在已核实的持久位置上取得完整 12 列基线；
 *    - 临时查阅 B 期间核验 B 正文 marker 与"返回阅读处"按钮可见，断言 A 的 12 列绝对冻结、B 未写入普通进度；
 *    - 返回 A 后核验身份恢复与临时按钮消失，记录返回基线；
 *    - 翻页后等待条件【同时】检查真实 locator 位置变化与合法持久保存（拒绝仅时间变新假绿）；
 *    - 保留正反 marker 和 12 列原始证据。
 * 3. 分页前置无副作用核实：
 *    - 实际读取 Activity 注入的 readerSettings，核实真实分页模式（readerMode=paged, pagerEngineMode=on）；
 *    - 登记"新宿主包、无旧数据、无书级覆盖"前置合同供后续唯一冻结/设备 owner 验证；
 *    - 实际模式不符明确拒绝并停止依赖步骤，不静默改设置。
 * 4. 退出辅助检查与诚实未覆盖项声明：
 *    - journey03 仅观察正常退出导航与持久层已有位置自洽，不凭前后 notNull/bookId 相同冒充保存通过；
 *    - 明确声明 EXIT_SAVE_REQUEST_COMPLETION 与 ALL_PENDING_SAVES_DRAINED 为 NOT_COVERED；
 *    - 彻底删除"本次 Back 退出落盘已确认"等超出证据的表述。
 * 5. 生命周期账本与所有权隔离（保留 Root 补正）：
 *    - 全量预检冲突，冲突即停且不删未知对象；
 *    - A/B Book upsert 成功立即登记 ledger，再创建关联行；
 *    - 精确按 ledger 已登记 ID 清理，tearDown 多层 finally 保证首选项还原且异常显式向外抛出。
 */
object J1RealReaderFixtures {

    const val TASK_ID = "CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v6"
    const val TAG = "J1RealReaderJourney"

    const val SAVE_CAUSE_UNATTRIBUTED = "UNATTRIBUTED"

    const val PREFIX_BOOK_A = "test-j1-txt-a"
    // v9 夹具非退化：书名不含孤立拉丁字母 "A" —— 生产 SearchTokenizer 对短查询 "ALPHA" 补单字
    // unigram {a,l,p,h}，书名里的 "A" 会与真实关键词 "ALPHA" 竞争命中章；用等长中文替换，
    // 保持全部 offset 与验收定义不变（与 v5「只调夹具正文非退化」先例同类）。
    const val BOOK_TITLE_A = "测试 TXT 甲"
    const val SEARCH_QUERY_A1 = "ALPHA"
    const val SEARCH_QUERY_A2 = "SECOND"
    const val MARKER_A1 = "【ALPHA_BODY_UNIQUE_MARKER_A1】"
    const val MARKER_A2 = "【SECOND_BODY_UNIQUE_MARKER_A2】"

    const val PREFIX_BOOK_B = "test-j1-txt-b"
    const val BOOK_TITLE_B = "测试 TXT B"
    const val SEARCH_QUERY_B = "BETA"
    const val MARKER_B = "【BETA_BODY_UNIQUE_MARKER_B】"

    const val PREFIX_BOOK_UNREADABLE = "test-j1-txt-unreadable"
    const val BOOK_TITLE_UNREADABLE = "测试损坏 TXT"

    const val KEY_ONBOARDED = "onboarded_v1"

    private const val FIXTURES_PARENT_DIR = "j1_isolated_fixtures"
    private const val EVIDENCE_DIR_NAME = "j1_real_reader_evidence"

    /**
     * 分页前置核验报告：记录实际读取的配置状态与前置合同。
     */
    data class PagedPreconditionReport(
        val readerMode: String,
        val pagerEngineMode: String,
        val isPagedMode: Boolean,
        val isPagerEngineOn: Boolean,
        val satisfiesContract: Boolean,
        val freshHostContractNote: String,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("readerMode", readerMode)
            put("pagerEngineMode", pagerEngineMode)
            put("isPagedMode", isPagedMode)
            put("isPagerEngineOn", isPagerEngineOn)
            put("satisfiesContract", satisfiesContract)
            put("freshHostContractNote", freshHostContractNote)
        }
    }

    /** 无副作用核实分页模式前置条件 */
    fun verifyPagedModePrecondition(readerMode: String, pagerEngineMode: String): PagedPreconditionReport {
        val isPaged = readerMode == "paged"
        val isEngineOn = pagerEngineMode == "on" // auto may be disabled by production pager health
        val satisfies = isPaged && isEngineOn
        return PagedPreconditionReport(
            readerMode = readerMode,
            pagerEngineMode = pagerEngineMode,
            isPagedMode = isPaged,
            isPagerEngineOn = isEngineOn,
            satisfiesContract = satisfies,
            freshHostContractNote = "前置合同：要求宿主处于全新安装或无旧数据覆盖的默认分页状态 (readerMode=paged, pagerEngineMode=on)。实际模式不符将显式停止依赖步骤。",
        )
    }

    /**
     * 运行生命周期账本：在成功取得所有权并完成资源创建后按步登记。
     */
    class RunLifecycleLedger(val runId: String) {
        private val _createdBookIds = mutableListOf<String>()
        val createdBookIds: List<String> get() = _createdBookIds.toList()
        var runDirCreated: Boolean = false
            internal set

        fun recordBookCreated(bookId: String) {
            if (!_createdBookIds.contains(bookId)) {
                _createdBookIds.add(bookId)
            }
        }

        fun recordDirCreated() {
            runDirCreated = true
        }
    }

    data class BookAContent(
        val bodyText: String,
        val offset1: Int,
        val offset2: Int,
        val marker1: String,
        val marker2: String,
        val snippet1: String,
        val snippet2: String,
        val queryOffset1: Int,
        val queryLen1: Int,
        val queryOffset2: Int,
        val queryLen2: Int,
        val locationSnippet1: String,
        val locationSnippet2: String,
    )

    data class BookBContent(
        val bodyText: String,
        val offsetB: Int,
        val markerB: String,
        val snippetB: String,
        val queryOffsetB: Int,
        val queryLenB: Int,
        val locationSnippetB: String,
    )

    /**
     * 真实生成书籍 A 正文：互斥查询词、精确计算实际 offset、相距充分的唯一 Marker。
     *
     * v5 夹具非退化修正（只调夹具正文长度，不改验收定义）：
     * 生产分页合同的持久化值是「当前页首字符的全书偏移」（TxtPagedController.currentPageStartAbs）。
     * 旧夹具把首个目标位点放在第 120 字，与第 0 字同属第一页，规范化结果恒为 0，
     * 无法区分「正确页起始」与既有合同明令拒绝的「非零进度被清零」（J1RecoveryContract
     * 反例表把 R=0 列为必须拒绝）。故把首个位点前移到远超过单页容量的位置，
     * 使规范化页起始必然 > 0，位置断言才有判别力。
     */
    fun generateBodyTextA(): BookAContent {
        val sb = StringBuilder()
        sb.append("《").append(BOOK_TITLE_A).append("》\n\n")
        sb.append("第一章 序章\n")
        while (sb.length < 4000) {
            sb.append("这是用于填充段落甲前置文本的内容。")
        }
        val offset1 = sb.length
        val snippet1 = "关键词 ALPHA 首次出现"
        sb.append(snippet1).append("。").append(MARKER_A1)
        sb.append("\n\n第二章 中间深入探索\n")
        while (sb.length < 8000) {
            sb.append("填充段落甲中置文本内容以充分拉开两个不同位点之间的距离。")
        }
        val offset2 = sb.length
        val snippet2 = "关键词 SECOND 再次出现"
        sb.append(snippet2).append("。").append(MARKER_A2)
        sb.append("\n\n第三章 完结尾声\n后续正文结尾内容。\n")
        val text = sb.toString()
        check(text.substring(offset1).startsWith(snippet1)) { "offset1 处内容必须与 snippet1 精确匹配" }
        check(text.substring(offset2).startsWith(snippet2)) { "offset2 处内容必须与 snippet2 精确匹配" }

        val queryOffset1 = text.indexOf(SEARCH_QUERY_A1)
        val queryLen1 = SEARCH_QUERY_A1.length
        val queryOffset2 = text.indexOf(SEARCH_QUERY_A2)
        val queryLen2 = SEARCH_QUERY_A2.length
        check(queryOffset1 >= 0 && queryOffset2 >= 0) { "必须包含待查关键字" }

        val locationSnippet1 = "第 $queryOffset1 字附近"
        val locationSnippet2 = "第 $queryOffset2 字附近"

        return BookAContent(
            bodyText = text,
            offset1 = offset1,
            offset2 = offset2,
            marker1 = MARKER_A1,
            marker2 = MARKER_A2,
            snippet1 = snippet1,
            snippet2 = snippet2,
            queryOffset1 = queryOffset1,
            queryLen1 = queryLen1,
            queryOffset2 = queryOffset2,
            queryLen2 = queryLen2,
            locationSnippet1 = locationSnippet1,
            locationSnippet2 = locationSnippet2,
        )
    }

    /** 真实生成书籍 B 正文：互斥查询词、精确计算实际 offset、唯一 Marker */
    fun generateBodyTextB(): BookBContent {
        val sb = StringBuilder()
        sb.append("《").append(BOOK_TITLE_B).append("》\n\n")
        sb.append("第一章 检索验证\n")
        while (sb.length < 150) {
            sb.append("填充段落B前置文本以精确对齐偏移量。")
        }
        val offsetB = sb.length
        val snippetB = "查询关键词 BETA 出现在此段"
        sb.append(snippetB).append("。").append(MARKER_B)
        sb.append("\n\n第二章 持续探索\n后续文本内容。\n")
        val text = sb.toString()
        check(text.substring(offsetB).startsWith(snippetB)) { "offsetB 处内容必须与 snippetB 精确匹配" }

        val queryOffsetB = text.indexOf(SEARCH_QUERY_B)
        val queryLenB = SEARCH_QUERY_B.length
        check(queryOffsetB >= 0) { "必须包含待查关键字 BETA" }
        val locationSnippetB = "第 $queryOffsetB 字附近"

        return BookBContent(
            bodyText = text,
            offsetB = offsetB,
            markerB = MARKER_B,
            snippetB = snippetB,
            queryOffsetB = queryOffsetB,
            queryLenB = queryLenB,
            locationSnippetB = locationSnippetB,
        )
    }

    /** 夹具存储目录（按 run 隔离在应用私有内部空间） */
    fun getRunFixturesDir(context: Context, runId: String): File =
        File(context.filesDir, "$FIXTURES_PARENT_DIR/$runId")

    /** 证据输出目录 */
    fun getEvidenceDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, EVIDENCE_DIR_NAME).apply { mkdirs() }

    private const val BACK_DIAG_DIR_NAME = "j1_back_diag"

    /** Back 状态诊断输出目录（宿主 external files；卸载前由本任务定向入口归档） */
    fun getBackDiagDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, BACK_DIAG_DIR_NAME).apply { mkdirs() }

    /** 持久化 Back 状态诊断序列（关键节点计数 + merged/unmerged 语义树全文） */
    fun recordBackDiagnostics(context: Context, runId: String, states: List<JSONObject>): File {
        val dir = getBackDiagDir(context)
        val file = File(dir, "${runId}__back_diagnostics.json")
        val arr = JSONArray()
        states.forEach { arr.put(it) }
        val root = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", "back_diagnostics")
            put("packageName", context.packageName)
            put("capturedAt", Instant.now().toString())
            put("states", arr)
        }
        file.writeText(root.toString(2))
        return file
    }

    /** 生成可放入 success JSON 的诊断摘要（避免整棵语义树塞进生产证据） */
    fun summarizeBackDiagnostics(states: List<JSONObject>): String =
        states.joinToString(",") { s ->
            s.optString("tag") +
                "[home=" + s.optInt("homeSearchCount") +
                ",chromeBack=" + s.optInt("readerChromeBackCount") +
                ",marker1=" + s.optInt("marker1Count") +
                ",returnBtn=" + s.optInt("returnToReadingCount") + "]"
        }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    /** 打开真实 Room SQLite 数据库连接 */
    fun openDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries()
            .build()

    /** 保存原有引导状态基线并设置为已完成（在 Activity 启动前调用） */
    fun prepareOnboardingState(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        val original = if (prefs.contains(KEY_ONBOARDED)) prefs.getBoolean(KEY_ONBOARDED, false) else null
        prefs.edit().putBoolean(KEY_ONBOARDED, true).commit()
        return original
    }

    /** 精准恢复原有引导状态基线 */
    fun restoreOnboardingState(context: Context, original: Boolean?) {
        val prefs = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (original == null) {
            editor.remove(KEY_ONBOARDED)
        } else {
            editor.putBoolean(KEY_ONBOARDED, original)
        }
        editor.commit()
    }

    /**
     * 注入隔离夹具与数据库初始状态。
     * 1. 严格全量预检冲突（A、B、Unreadable、run 目录），存在冲突立即停止，绝不执行删除；
     * 2. 创建 run 专属目录并登记账本；
     * 3. 写入真实正文与精确生产 offset 格式（"start:len"）；
     * 4. 显式调用 SearchOffsets.parse 检验生产合同自洽；
     * 5. Root 审查采纳原则：BookEntity upsert 成功立即登记账本，再创建关联行，确保局部失败可精准清理。
     */
    fun setupRunFixtures(
        context: Context,
        db: AppDatabase,
        runId: String,
        bookIdA: String,
        bookIdB: String,
        bookIdUnreadable: String,
        ledger: RunLifecycleLedger,
    ): Pair<BookAContent, BookBContent> {
        // 1. 启动全量预检：确认数据库与文件系统无本次冲突残留（拒绝通配删除，冲突即停）
        val conflictA = runBlocking { db.bookDao().getById(bookIdA) }
        val conflictB = runBlocking { db.bookDao().getById(bookIdB) }
        val conflictUnreadable = runBlocking { db.bookDao().getById(bookIdUnreadable) }
        val runDir = getRunFixturesDir(context, runId)
        if (conflictA != null || conflictB != null || conflictUnreadable != null || runDir.exists()) {
            error("检测到已有冲突记录或残留目录 (conflictA=${conflictA != null}, conflictB=${conflictB != null}, conflictUnreadable=${conflictUnreadable != null}, dirExists=${runDir.exists()})，拒绝启动以防混淆现有数据。")
        }

        // 2. 创建 run 专属目录并登记账本
        if (!runDir.mkdirs() && !runDir.exists()) {
            error("创建夹具专属目录失败: ${runDir.absolutePath}")
        }
        ledger.recordDirCreated()

        val nowIso = Instant.now().toString()

        // 3. 准备真实正文与精确 offset
        val contentA = generateBodyTextA()
        val fileA = File(runDir, "$bookIdA.txt").apply { writeText(contentA.bodyText) }
        val initialLocA = LocatorBuilder.progressJson(
            legacyOffset = contentA.offset1,
            chapterIndex = 0,
            charOffset = contentA.offset1,
            globalOffset = contentA.offset1,
        )

        val contentB = generateBodyTextB()
        val fileB = File(runDir, "$bookIdB.txt").apply { writeText(contentB.bodyText) }

        // 生产 offsets 合同：精确遵循 "start:len" CSV 规范
        val offsetsA1 = "${contentA.queryOffset1}:${contentA.queryLen1}"
        val offsetsA2 = "${contentA.queryOffset2}:${contentA.queryLen2}"
        val offsetsB = "${contentB.queryOffsetB}:${contentB.queryLenB}"

        // 显式校验生产 SearchOffsets.parse 合同自洽
        val parsedA1 = SearchOffsets.parse(offsetsA1)
        check(parsedA1.isNotEmpty() && parsedA1[0].start == contentA.queryOffset1 && parsedA1[0].length == contentA.queryLen1) {
            "SearchOffsets 解析 A1 契约失败: offsets=$offsetsA1, parsed=$parsedA1"
        }
        val parsedA2 = SearchOffsets.parse(offsetsA2)
        check(parsedA2.isNotEmpty() && parsedA2[0].start == contentA.queryOffset2 && parsedA2[0].length == contentA.queryLen2) {
            "SearchOffsets 解析 A2 契约失败: offsets=$offsetsA2, parsed=$parsedA2"
        }
        val parsedB = SearchOffsets.parse(offsetsB)
        check(parsedB.isNotEmpty() && parsedB[0].start == contentB.queryOffsetB && parsedB[0].length == contentB.queryLenB) {
            "SearchOffsets 解析 B 契约失败: offsets=$offsetsB, parsed=$parsedB"
        }

        // 4. 逐个写入数据库，Root 采纳原则：BookEntity 插入成功立即记账，再写关联行
        runBlocking {
            // 书籍 A
            db.bookDao().upsert(
                BookEntity(
                    id = bookIdA,
                    title = BOOK_TITLE_A,
                    author = "测试作者 甲",
                    format = "txt",
                    size = contentA.bodyText.toByteArray(Charsets.UTF_8).size,
                    local_content_path = fileA.absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
            )
            ledger.recordBookCreated(bookIdA)

            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    book_id = bookIdA,
                    // 初始百分比必须与初始 offset 一致（offset1 / 正文长度），
                    // 避免用不一致的百分比冒充移动证据。
                    progress_percent = contentA.offset1.toFloat() / contentA.bodyText.length.toFloat() * 100f,
                    last_read_at = nowIso,
                    total_reading_time_ms = 60000L,
                    completion_state = "reading",
                    current_location_json = initialLocA,
                    updated_at = nowIso,
                )
            )
            db.bookContentDao().upsert(
                BookContentEntity(
                    book_id = bookIdA,
                    reader_preview = contentA.bodyText.take(2000),
                )
            )
            db.searchTermDao().upsertAll(
                listOf(
                    SearchTermRow(
                        term = SEARCH_QUERY_A1.lowercase(),
                        book_id = bookIdA,
                        chapter_index = 0,
                        text_basis = "original",
                        hits = 1,
                        offsets = offsetsA1,
                    ),
                    SearchTermRow(
                        term = SEARCH_QUERY_A2.lowercase(),
                        book_id = bookIdA,
                        chapter_index = 0,
                        text_basis = "original",
                        hits = 1,
                        offsets = offsetsA2,
                    )
                )
            )
            db.searchIndexCoverageDao().upsert(
                SearchIndexCoverageRow(
                    book_id = bookIdA,
                    text_basis = "original",
                    format = "txt",
                    coverage = "full",
                    indexed_chapters = 3,
                    total_chapters = 3,
                    indexed_at = System.currentTimeMillis(),
                )
            )

            // 书籍 B
            db.bookDao().upsert(
                BookEntity(
                    id = bookIdB,
                    title = BOOK_TITLE_B,
                    author = "测试作者 B",
                    format = "txt",
                    size = contentB.bodyText.toByteArray(Charsets.UTF_8).size,
                    local_content_path = fileB.absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
            )
            ledger.recordBookCreated(bookIdB)

            db.bookContentDao().upsert(
                BookContentEntity(
                    book_id = bookIdB,
                    reader_preview = contentB.bodyText.take(2000),
                )
            )
            db.searchTermDao().upsertAll(
                listOf(
                    SearchTermRow(
                        term = SEARCH_QUERY_B.lowercase(),
                        book_id = bookIdB,
                        chapter_index = 0,
                        text_basis = "original",
                        hits = 1,
                        offsets = offsetsB,
                    )
                )
            )
            db.searchIndexCoverageDao().upsert(
                SearchIndexCoverageRow(
                    book_id = bookIdB,
                    text_basis = "original",
                    format = "txt",
                    coverage = "full",
                    indexed_chapters = 2,
                    total_chapters = 2,
                    indexed_at = System.currentTimeMillis(),
                )
            )

            // 书籍 Unreadable（测试加载失败保护，指向不存在的文件）
            db.bookDao().upsert(
                BookEntity(
                    id = bookIdUnreadable,
                    title = BOOK_TITLE_UNREADABLE,
                    author = "损坏作者",
                    format = "txt",
                    size = 9999,
                    local_content_path = File(runDir, "non_existent_unreadable.txt").absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
            )
            ledger.recordBookCreated(bookIdUnreadable)
        }
        return Pair(contentA, contentB)
    }

    /** 精准清理账本中登记的已创建数据与文件，禁止通配删除，异常直接抛出 */
    fun cleanRunFixtures(context: Context, db: AppDatabase, ledger: RunLifecycleLedger) {
        val wdb = db.openHelper.writableDatabase
        ledger.createdBookIds.forEach { bid ->
            wdb.execSQL("DELETE FROM reading_progress WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM reading_sessions WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM highlights WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM notes WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM search_terms WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM search_index_coverage WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM book_content WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM book_files WHERE book_id = ?", arrayOf(bid))
            wdb.execSQL("DELETE FROM books WHERE id = ?", arrayOf(bid))
        }
        if (ledger.runDirCreated) {
            val runDir = getRunFixturesDir(context, ledger.runId)
            if (runDir.exists()) {
                val deleted = runDir.deleteRecursively()
                check(deleted) { "清理 run 目录失败: ${runDir.absolutePath}" }
            }
        }
    }

    /** 目标定位生成辅助 */
    fun createTarget(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ──────────────────────────────────────────────────────────────────────────
    // 真实 SQLite reading_progress 完整 12 列快照与比对
    // ──────────────────────────────────────────────────────────────────────────

    data class ProgressRowSnapshot(
        val book_id: String,
        val progress_percent: Float,
        val last_read_at: String?,
        val total_reading_time_ms: Long,
        val completion_state: String,
        val current_location_json: String?,
        val payload: String?,
        val revision: Int,
        val device_id: String?,
        val updated_at: String,
        val completed_at: Long?,
        val deleted_at: String?,
    ) {
        val legacyOffset: Int
            get() = current_location_json?.let { LocatorCodec.locatorFromProgress(it)?.legacyOffset } ?: -1

        fun toJson(): JSONObject = JSONObject().apply {
            put("book_id", book_id)
            put("progress_percent", progress_percent.toDouble())
            put("last_read_at", last_read_at ?: JSONObject.NULL)
            put("total_reading_time_ms", total_reading_time_ms)
            put("completion_state", completion_state)
            put("current_location_json", current_location_json ?: JSONObject.NULL)
            put("payload", payload ?: JSONObject.NULL)
            put("revision", revision)
            put("device_id", device_id ?: JSONObject.NULL)
            put("updated_at", updated_at)
            put("completed_at", completed_at ?: JSONObject.NULL)
            put("deleted_at", deleted_at ?: JSONObject.NULL)
            put("legacyOffset", legacyOffset)
        }

        /** 断言此行快照与另一快照在全部 12 列字段上逐一严格一致（临时阅读期间完全冻结） */
        fun assertExactEquals(other: ProgressRowSnapshot?, label: String) {
            checkNotNull(other) { "$label: 目标快照不能为 null" }
            check(this.book_id == other.book_id) {
                "$label: 1/12 book_id 不匹配 ($book_id vs ${other.book_id})"
            }
            check(this.progress_percent == other.progress_percent) {
                "$label: 2/12 progress_percent 不匹配 ($progress_percent vs ${other.progress_percent})"
            }
            check(this.last_read_at == other.last_read_at) {
                "$label: 3/12 last_read_at 不匹配 ($last_read_at vs ${other.last_read_at})"
            }
            check(this.total_reading_time_ms == other.total_reading_time_ms) {
                "$label: 4/12 total_reading_time_ms 不匹配 ($total_reading_time_ms vs ${other.total_reading_time_ms})"
            }
            check(this.completion_state == other.completion_state) {
                "$label: 5/12 completion_state 不匹配 ($completion_state vs ${other.completion_state})"
            }
            check(this.current_location_json == other.current_location_json) {
                "$label: 6/12 current_location_json 发生变动！($current_location_json vs ${other.current_location_json})"
            }
            check(this.payload == other.payload) {
                "$label: 7/12 payload 不匹配 ($payload vs ${other.payload})"
            }
            check(this.revision == other.revision) {
                "$label: 8/12 revision 不匹配 ($revision vs ${other.revision})"
            }
            check(this.device_id == other.device_id) {
                "$label: 9/12 device_id 不匹配 ($device_id vs ${other.device_id})"
            }
            check(this.updated_at == other.updated_at) {
                "$label: 10/12 updated_at 发生变动！($updated_at vs ${other.updated_at})"
            }
            check(this.completed_at == other.completed_at) {
                "$label: 11/12 completed_at 不匹配 ($completed_at vs ${other.completed_at})"
            }
            check(this.deleted_at == other.deleted_at) {
                "$label: 12/12 deleted_at 不匹配 ($deleted_at vs ${other.deleted_at})"
            }
        }

        /**
         * 断言合法保存与进度推进（返回普通阅读后发生真实移动：
         * 1. updated_at 必须严格大于基线时间；
         * 2. current_location_json 必须实质发生改变；
         * 3. legacyOffset 必须发生实质位移）。
         */
        fun assertLegalProgressAdvance(previous: ProgressRowSnapshot, label: String) {
            check(this.book_id == previous.book_id) {
                "$label: book_id 必须相同 ($book_id vs ${previous.book_id})"
            }
            check(this.updated_at > previous.updated_at) {
                "$label: updated_at 必须严格推进 (${this.updated_at} > ${previous.updated_at})，不能是等于或旧时间"
            }
            check(this.current_location_json != previous.current_location_json) {
                "$label: current_location_json 必须实质发生改变 (${this.current_location_json} vs ${previous.current_location_json})，拒绝原地保存伪绿！"
            }
            check(this.legacyOffset >= 0 && previous.legacyOffset >= 0) { "Invalid or missing locator cannot prove progress advance" }
            check(this.legacyOffset != previous.legacyOffset) {
                "$label: legacyOffset 必须实质发生位移 (${this.legacyOffset} vs ${previous.legacyOffset})"
            }
        }
    }

    /** 从数据库捕获完整的 reading_progress 全部 12 列快照 */
    fun captureProgressRow(db: AppDatabase, bookId: String): ProgressRowSnapshot? {
        val sql = "SELECT book_id, progress_percent, last_read_at, total_reading_time_ms, " +
            "completion_state, current_location_json, payload, revision, device_id, " +
            "updated_at, completed_at, deleted_at FROM reading_progress WHERE book_id = ? LIMIT 1"
        return db.openHelper.writableDatabase.query(sql, arrayOf(bookId)).use { c ->
            if (c.moveToFirst()) {
                ProgressRowSnapshot(
                    book_id = c.getString(0) ?: "",
                    progress_percent = if (c.isNull(1)) 0f else c.getFloat(1),
                    last_read_at = if (c.isNull(2)) null else c.getString(2),
                    total_reading_time_ms = if (c.isNull(3)) 0L else c.getLong(3),
                    completion_state = c.getString(4) ?: "reading",
                    current_location_json = if (c.isNull(5)) null else c.getString(5),
                    payload = if (c.isNull(6)) null else c.getString(6),
                    revision = if (c.isNull(7)) 1 else c.getInt(7),
                    device_id = if (c.isNull(8)) null else c.getString(8),
                    updated_at = c.getString(9) ?: "",
                    completed_at = if (c.isNull(10)) null else c.getLong(10),
                    deleted_at = if (c.isNull(11)) null else c.getString(11),
                )
            } else null
        }
    }

    /**
     * 仅观察已有持久位置，不证明新保存或退出请求完成。
     *
     * 生产分页合同（与 journey01 的 `PAGED_PAGE_START` 一致）：持久化值是「包含目标位点的
     * 当前页首字符全书偏移」，**不是**入参 offset 本身（TxtPagedController.currentPageStartAbs
     * 注释：「进度持久化以它为准」）。故断言为：
     *   ① 页起始非零（清零被既有 J1 恢复合同列为必须拒绝）；
     *   ② 页起始不得超过目标位点（目标落在该页内 —— 调用方已在进入阅读器时断言目标 marker
     *      实际渲染可见，二者合起来才锁定「持久值正是包含目标的那一页的起始」，而非任意位置）。
     * 不使用「非空 / >0」单独代替位置合同，也不放松为任意非负值。
     */
    fun assertObservedPersistedLocation(
        snapshot: ProgressRowSnapshot?,
        expectedOffset: Int,
        label: String,
    ) {
        checkNotNull(snapshot) { "$label: 进度行快照不能为 null" }
        val decoded = LocatorCodec.locatorFromProgress(snapshot.current_location_json.orEmpty())
        checkNotNull(decoded) { "$label: current_location_json 必须可成功解码为 ReaderLocator" }
        check(snapshot.legacyOffset > 0) {
            "$label: 页起始必须非零 (当前=${snapshot.legacyOffset}；清零被 J1 恢复合同列为必须拒绝)"
        }
        check(snapshot.legacyOffset <= expectedOffset) {
            "$label: 页起始不得超过目标位点 (页起始=${snapshot.legacyOffset}, 目标=$expectedOffset)"
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 证据记录与触碰清单导出
    // ──────────────────────────────────────────────────────────────────────────

    /** 写入带 task/run/step 标识的结构化证据 */
    fun recordStepEvidence(
        context: Context,
        runId: String,
        stepName: String,
        payloadBuilder: JSONObject.() -> Unit,
    ): File {
        val evidenceDir = getEvidenceDir(context)
        val file = File(evidenceDir, "${runId}__${stepName}.json")
        val json = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", stepName)
            put("pid", Process.myPid())
            put("packageName", context.packageName)
            put("timestamp", Instant.now().toString())
            put("timestampMillis", System.currentTimeMillis())
            val p = JSONObject().apply(payloadBuilder)
            put("payload", p)
        }
        file.writeText(json.toString(2))
        return file
    }

    /** 汇总实际 App/Hilt 启动产生的各种附带文件与数据触碰清单（供任务 C 纳入联合恢复合同） */
    fun exportTouchedResourceManifest(context: Context, runId: String, createdBookIds: Collection<String>): JSONObject {
        return JSONObject().apply {
            put("task", TASK_ID)
            put("description", "真实 App/Hilt 启动及 J1 真实阅读器旅程所触碰的资源清单")
            put("database", JSONObject().apply {
                put("dbName", AppDatabase.DB_NAME)
                put("touchedTables", listOf("books", "reading_progress", "book_content", "search_terms", "search_index_coverage"))
                put("isolatedBookIds", createdBookIds.toList())
                put("recoveryAction", "DELETE FROM 相应表 WHERE book_id IN (${createdBookIds.joinToString(",") { "'$it'" }}) 并执行 WAL checkpoint")
            })
            put("filesDir", JSONObject().apply {
                put("isolatedFixturesPath", getRunFixturesDir(context, runId).absolutePath)
                put("recoveryAction", "递归删除 run 专属 j1_isolated_fixtures/$runId 目录")
            })
            put("sharedPreferences", JSONObject().apply {
                put("onboarding", ONBOARDING_PREFS)
                put("key", KEY_ONBOARDED)
                put("recoveryAction", "按原始基线恢复或按默认移除")
            })
            put("workManagerSchedulers", listOf(
                "EpubSizeRepairTask",
                "ReadingGoalScheduler",
                "SearchIndexScheduler"
            ))
            put("cacheDir", JSONObject().apply {
                put("cachePath", context.cacheDir.absolutePath)
                put("possibleFiles", listOf("cra_fatal.log", "TextStreamLoader 临时缓存"))
                put("recoveryAction", "清理测试运行期间生成的临时缓存文件")
            })
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // v5 新增：真实 DB 原始行只读捕获（搜索索引 / 书籍）与本任务诊断落盘。
    // 只读取，不写业务数据；用于区分「没有正确 hit」与「有 hit 但选择器不匹配」。
    // ──────────────────────────────────────────────────────────────────────────

    private const val V5_DIAG_DIR_NAME = "j1_v5_diag"

    /** v5 诊断输出目录（宿主 external files；卸载前由本任务定向入口归档） */
    fun getV5DiagDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, V5_DIAG_DIR_NAME).apply { mkdirs() }

    /** 通用只读查询：把结果集**全部列**按原始值导出，避免列名假设失真 */
    fun queryRowsJson(db: AppDatabase, sql: String, args: Array<String>): List<JSONObject> {
        return db.openHelper.writableDatabase.query(sql, args).use { c ->
            val rows = mutableListOf<JSONObject>()
            val names = c.columnNames
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in names.indices) {
                    val v: Any? = when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> "<blob>"
                        else -> c.getString(i)
                    }
                    row.put(names[i], v ?: JSONObject.NULL)
                }
                rows.add(row)
            }
            rows
        }
    }

    /** 某本书当前的全部 search_terms 原始行（含 term / chapter_index / text_basis / hits / offsets） */
    fun captureSearchTermRows(db: AppDatabase, bookId: String): List<JSONObject> =
        queryRowsJson(db, "SELECT * FROM search_terms WHERE book_id = ?", arrayOf(bookId))

    /** 按精确 term 查询全部原始行（生产 DAO 为 term 精确等值匹配） */
    fun captureSearchTermRowsByTerm(db: AppDatabase, term: String): List<JSONObject> =
        queryRowsJson(db, "SELECT * FROM search_terms WHERE term = ?", arrayOf(term))

    /** 某本书当前的 search_index_coverage 原始行 */
    fun captureCoverageRows(db: AppDatabase, bookId: String): List<JSONObject> =
        queryRowsJson(db, "SELECT * FROM search_index_coverage WHERE book_id = ?", arrayOf(bookId))

    /** 书籍行原始值（含 deleted_at / content_status / local_content_path） */
    fun captureBookRow(db: AppDatabase, bookId: String): JSONObject? =
        queryRowsJson(db, "SELECT * FROM books WHERE id = ?", arrayOf(bookId)).firstOrNull()

    /**
     * 按生产合同从索引行推导 UI 将显示的内容命中字符偏移：
     * `SearchIndexRepository.searchContent` 取 `SearchOffsets.parse(row.offsets).first().start`，
     * `SearchScreen.contentSnippet` 渲染为「第 N 字附近」。
     * 返回 null 表示这本书当前没有可用的正文索引行。
     */
    fun resolveIndexedCharOffset(db: AppDatabase, bookId: String, term: String): Int? {
        val row = captureSearchTermRows(db, bookId)
            .firstOrNull { it.optString("term") == term.lowercase() } ?: return null
        return SearchOffsets.parse(row.optString("offsets")).firstOrNull()?.start
    }

    /**
     * 反查：本书当前哪些正文索引行的首个命中区间起点等于 [start]。
     * 用于把 UI 上显示的「第 N 字附近」追溯到真实索引行（而不是凭空接受一个数字）。
     * 生产查询会把长度 ≤6 的查询词拆出单字 unigram 一起查
     * （SearchTokenizer.tokenizeForQuery），卡片位置提示取 ranking 选中的那条区间，
     * 因此 N 可能来自 "beta" 之外的 unigram 行。
     */
    fun findIndexedRowsWithStart(db: AppDatabase, bookId: String, start: Int): List<JSONObject> =
        captureSearchTermRows(db, bookId).filter {
            SearchOffsets.parse(it.optString("offsets")).firstOrNull()?.start == start
        }

    data class IndexWaitResult(
        val resolvedOffset: Int?,
        val stable: Boolean,
        val polls: List<JSONObject>,
        val finalRows: List<JSONObject>,
    )

    /**
     * 有界等待生产索引对本书稳定（生产 App 会自行重建索引，夹具写入的行可能被重算）。
     * 稳定判据：连续 3 次采样解析出的 charOffset 相同且非空。
     */
    fun awaitStableIndexedCharOffset(
        db: AppDatabase,
        bookId: String,
        term: String,
        timeoutMs: Long,
    ): IndexWaitResult {
        val polls = mutableListOf<JSONObject>()
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Int? = null
        var sameCount = 0
        var rows = captureSearchTermRows(db, bookId)
        while (System.currentTimeMillis() < deadline) {
            val cur = resolveIndexedCharOffset(db, bookId, term)
            rows = captureSearchTermRows(db, bookId)
            val arr = JSONArray()
            rows.forEach { arr.put(it) }
            polls.add(JSONObject().apply {
                put("at", System.currentTimeMillis())
                put("resolvedCharOffset", cur ?: JSONObject.NULL)
                put("rowCount", rows.size)
                put("rows", arr)
            })
            sameCount = if (cur != null && cur == last) sameCount + 1 else 1
            last = cur
            if (cur != null && sameCount >= 3) {
                return IndexWaitResult(cur, true, polls, rows)
            }
            Thread.sleep(500)
        }
        return IndexWaitResult(last, false, polls, rows)
    }

    /** 写入本任务结构化诊断（位置 / 搜索），卸载前由定向入口归档 */
    fun recordV5Diagnostics(context: Context, runId: String, step: String, payload: JSONObject): File {
        val dir = getV5DiagDir(context)
        val file = File(dir, "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", step)
            put("packageName", context.packageName)
            put("capturedAt", Instant.now().toString())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        return file
    }

    // ──────────────────────────────────────────────────────────────────────────
    // v6 新增 A：从「预先已知查询 + 夹具正文 + 生产查询命中规则」正向推导搜索卡片位置。
    //
    // 严禁把 UI 上观察到的任意卡片 N 重写成 expected。这里的所有输入在点击/渲染之前就已确定：
    // 查询词（夹具常量）、生产分词与排序选点（真实生产类）、当前真实索引行、夹具正文
    // （用于把偏移回溯到正文位置）。UI 只用于**验证**推导结果。
    // ──────────────────────────────────────────────────────────────────────────

    private const val V6_DIAG_DIR_NAME = "j1_v6_diag"

    /** v6 诊断输出目录（宿主 external files；卸载前由本任务定向入口归档） */
    fun getV6DiagDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, V6_DIAG_DIR_NAME).apply { mkdirs() }

    /** 写入 v6 结构化诊断 */
    fun recordV6Diagnostics(context: Context, runId: String, step: String, payload: JSONObject): File {
        val dir = getV6DiagDir(context)
        val file = File(dir, "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", step)
            put("packageName", context.packageName)
            put("pid", Process.myPid())
            put("capturedAt", Instant.now().toString())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        return file
    }

    /** 生产查询命中规则正向推导出的坐标（全部字段可追溯，不含任何 UI 观察值）。 */
    data class DerivedSearchCoordinate(
        val query: String,
        val queryTerms: Map<String, Int>,
        val termRowCount: Int,
        val matchedTerms: List<String>,
        val legalOffsets: List<Int>,
        val winningChapterIndex: Int?,
        val distinctTermsInWinningChapter: List<String>,
        val expectedCharOffset: Int?,
        val expectedLocationSnippet: String?,
        val traceTerm: String?,
        val traceChapterIndex: Int?,
        val traceBodyMatch: Boolean,
        val traceBodySnippet: String?,
        val betaRowOffset: Int?,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            val qt = JSONObject()
            queryTerms.forEach { (k, v) -> qt.put(k, v) }
            put("query", query)
            put("queryTerms", qt)
            put("termRowCount", termRowCount)
            put("matchedTerms", JSONArray(matchedTerms))
            put("legalOffsets", JSONArray(legalOffsets))
            put("winningChapterIndex", winningChapterIndex ?: JSONObject.NULL)
            put("distinctTermsInWinningChapter", JSONArray(distinctTermsInWinningChapter))
            put("expectedCharOffset", expectedCharOffset ?: JSONObject.NULL)
            put("expectedLocationSnippet", expectedLocationSnippet ?: JSONObject.NULL)
            put("traceTerm", traceTerm ?: JSONObject.NULL)
            put("traceChapterIndex", traceChapterIndex ?: JSONObject.NULL)
            put("traceBodyMatch", traceBodyMatch)
            put("traceBodySnippet", traceBodySnippet ?: JSONObject.NULL)
            put("betaRowOffset", betaRowOffset ?: JSONObject.NULL)
        }
    }

    /**
     * 按生产链路推导本书应显示的命中字符偏移：
     * `SearchIndexRepository.searchContent`（tokenizeForQuery + 按 term 取行，基准默认 original）
     * → `SearchHitRanker.rank` → `SearchHitSelection.pickCoordinate`
     * → `SearchScreen.contentSnippet` 渲染「第 N 字附近」。
     *
     * 全程使用**生产类本体**，不在测试里重写一份规则。
     */
    fun deriveSearchCoordinate(
        db: AppDatabase,
        bookId: String,
        query: String,
        bodyText: String,
    ): DerivedSearchCoordinate {
        val tokenizer = SearchTokenizer()
        val queryTerms = tokenizer.tokenizeForQuery(query)
            .groupBy { it }.mapValues { (_, list) -> list.size }
        val rows = captureSearchTermRows(db, bookId)
        val matched = rows.filter { row ->
            val term = row.optString("term")
            term.isNotEmpty() && queryTerms.containsKey(term) &&
                row.optString("text_basis") == SearchTextBasis.ORIGINAL.wire
        }
        val raw = ArrayList<RawTermMatch>()
        for (row in matched) {
            val term = row.optString("term")
            raw += RawTermMatch(
                bookId = row.optString("book_id"),
                textBasis = SearchTextBasis.ORIGINAL,
                chapterIndex = row.optInt("chapter_index"),
                term = term,
                hits = row.optInt("hits"),
                queryTermCount = queryTerms[term] ?: 1,
                span = SearchOffsets.parse(row.optString("offsets"), limit = 1).firstOrNull(),
            )
        }
        val legalOffsets = matched
            .flatMap { SearchOffsets.parse(it.optString("offsets"), limit = 16).map { s -> s.start } }
            .distinct().sorted()
        val ranked = if (raw.isEmpty()) null else SearchHitSelection.pickCoordinate(raw)
        val chapter = ranked?.first
        val expected = ranked?.second?.start
        val distinctInWinning =
            if (chapter == null) emptyList()
            else raw.filter { it.chapterIndex == chapter }.map { it.term }.distinct().sorted()

        // 回溯：该偏移必须对应某条真实索引行的 term，且正文该位置确实是这个词
        val traceRow = raw.firstOrNull { it.span?.start == expected }
        val traceTerm = traceRow?.term
        val bodyMatch = if (expected != null && traceTerm != null) {
            bodyText.length >= expected + traceTerm.length &&
                bodyText.regionMatches(expected, traceTerm, 0, traceTerm.length, ignoreCase = true)
        } else false
        val bodySnippet = if (expected != null) {
            bodyText.substring(expected, (expected + 16).coerceAtMost(bodyText.length))
        } else null
        val betaOffset = rows.firstOrNull { it.optString("term") == query.lowercase() }
            ?.let { SearchOffsets.parse(it.optString("offsets")).firstOrNull()?.start }

        return DerivedSearchCoordinate(
            query = query,
            queryTerms = queryTerms,
            termRowCount = rows.size,
            matchedTerms = matched.map { it.optString("term") }.distinct().sorted(),
            legalOffsets = legalOffsets,
            winningChapterIndex = chapter,
            distinctTermsInWinningChapter = distinctInWinning,
            expectedCharOffset = expected,
            expectedLocationSnippet = expected?.let { "第 $it 字附近" },
            traceTerm = traceTerm,
            traceChapterIndex = traceRow?.chapterIndex,
            traceBodyMatch = bodyMatch,
            traceBodySnippet = bodySnippet,
            betaRowOffset = betaOffset,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // v6 新增 B：首个测试异常的原始记录（不吞异常、不覆盖、另记 finally 采集失败）。
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 记录首个导致测试主体退出的异常：类、消息、**完整栈**、时间戳与当时步骤。
     * 仅供取证；调用方必须随后原样抛出该异常，绝不吞掉。
     */
    fun recordFirstFailure(
        context: Context,
        runId: String,
        step: String,
        throwable: Throwable,
        atMillis: Long,
        extra: JSONObject? = null,
    ): File {
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        val causeSw = throwable.cause?.let { c ->
            val w = java.io.StringWriter()
            c.printStackTrace(java.io.PrintWriter(w))
            w.toString()
        }
        return recordV6Diagnostics(context, runId, "first_failure", JSONObject().apply {
            put("step", step)
            put("atMillis", atMillis)
            put("throwableClass", throwable.javaClass.name)
            put("message", throwable.message ?: JSONObject.NULL)
            put("stackTrace", sw.toString())
            put("causeClass", throwable.cause?.javaClass?.name ?: JSONObject.NULL)
            put("causeMessage", throwable.cause?.message ?: JSONObject.NULL)
            put("causeStackTrace", causeSw ?: JSONObject.NULL)
            if (extra != null) put("extra", extra)
        })
    }
}
