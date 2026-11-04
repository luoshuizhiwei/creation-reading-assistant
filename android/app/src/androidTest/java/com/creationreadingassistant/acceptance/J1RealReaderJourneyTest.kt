package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.navigation.decodeSourceLocatorFromRoute
import com.creationreadingassistant.ui.navigation.readerNavigationMode
import com.creationreadingassistant.ui.navigation.readerReturnRoute
import com.creationreadingassistant.ui.navigation.readerTemporaryRoute
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * CRA-J1-REAL-READER-ARTIFACTS-REPAIR-v6
 *
 * J1「跨书临时查阅」的**真实生产阅读器旅程**验收测试。
 *
 * ## v6 定点修正合同
 * 1. 已有持久位置观察（SAVE_CAUSE=UNATTRIBUTED）：
 *    - 从真实生产 UI 及非空正文 marker 证明当前阅读位置；
 *    - 依据实际夹具和生产 locator 合同检查目标位置，使用有界等待核验 DAO 行解码 locator 与目标位置相符；
 *    - 证据明确标注 SAVE_CAUSE=UNATTRIBUTED，不声称"本次 Back 退出落盘已确认"；
 *    - 坚决杜绝在旅程内直接向 reading_progress 写入数据伪造保存结果。
 * 2. 临时查阅冻结与返回后真实推进：
 *    - 在已核实的持久位置上取得完整 12 列基线；
 *    - 临时 B 期间核验正文 marker 与返回按钮，严格断言 A 的 12 列完全冻结、B 未写入普通进度；
 *    - 返回 A 后核验身份恢复与临时按钮消失，记录返回后基线；
 *    - 翻页后等待条件【同时】检查真实 locator 位置变化与合法持久保存（拒绝仅时间变新假绿）；
 *    - 保留正反 marker 和 12 列原始证据。
 * 3. 分页前置无副作用核实：
 *    - 实际读取 Activity 注入的 readerSettings，核实真实分页模式（readerMode=paged, pagerEngineMode=on）；
 *    - 登记"新宿主包、无旧数据、无书级覆盖"前置合同供后续唯一冻结/设备 owner 验证；
 *    - 实际模式不符明确拒绝并停止依赖步骤。
 * 4. 退出辅助检查与诚实未覆盖项声明：
 *    - journey03 仅观察正常退出导航与持久层已有位置自洽，不凭前后 notNull/bookId 相同冒充保存通过；
 *    - 明确声明 EXIT_SAVE_REQUEST_COMPLETION 与 ALL_PENDING_SAVES_DRAINED 为 NOT_COVERED；
 *    - 彻底删除"本次 Back 退出落盘已确认"等超出证据的表述。
 * 5. 生命周期账本与所有权隔离（保留 Root 补正）：
 *    - 全量预检冲突，冲突即停且不删未知对象；
 *    - 搜索卡片按默认 merged 语义节点自身的 title/location/原文及 click action 匹配；
 *    - A/B Book upsert 成功立即登记 ledger，再创建关联行；
 *    - 精确按 ledger 已登记 ID 清理，tearDown 多层 finally 保证首选项还原且异常显式向外抛出。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class J1RealReaderJourneyTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var bookIdA: String
    private lateinit var bookIdB: String
    private lateinit var bookIdUnreadable: String
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private lateinit var contentB: J1RealReaderFixtures.BookBContent

    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /** 最近一次已进入的步骤标记（首个异常取证用）。 */
    private var lastStep: String = "start"

    /** 首个测试主体异常是否已被记录（避免被后续/清理阶段异常覆盖）。 */
    private var firstFailureRecorded: Boolean = false

    /**
     * 生命周期 / route 代理快照：Activity 生命周期状态、ActivityScenario 状态、
     * 可取得的销毁标志与窗口焦点，以及可见屏幕标记。
     *
     * 说明：本应用只有一个 Activity（`MainActivity`），`NavController` 由 Compose
     * `rememberNavController()` 持有（`AppNavigation.kt:184`），**无法**从 Activity 侧
     * 直接读取当前 route 或 NavBackStackEntry 栈；此处如实记录「不可直接取得」，
     * 并用可见屏幕标记（首页搜索 / 阅读器控件 / 返回阅读处 / marker）作为 route 代理，
     * 不作为 route 本身的证明。
     */
    private fun routeLifecycleSnapshot(tag: String): JSONObject = JSONObject().apply {
        put("tag", tag)
        put("atMillis", System.currentTimeMillis())
        put("lastStep", lastStep)
        try {
            put("scenarioState", scenario?.state?.toString() ?: JSONObject.NULL)
        } catch (t: Throwable) {
            put("scenarioState", "<unavailable: $t>")
        }
        try {
            scenario?.onActivity { a ->
                put("activityLifecycleState", a.lifecycle.currentState.toString())
                put("isFinishing", a.isFinishing)
                put("isDestroyed", a.isDestroyed)
                put("hasWindowFocus", a.hasWindowFocus())
                put("activityIdentity", a.javaClass.simpleName + "@" + Integer.toHexString(a.hashCode()))
            }
        } catch (t: Throwable) {
            put("activityProbe", "<unavailable: $t>")
        }
        put("routeDirectlyObtainable", false)
        put("routeNote", "NavController 由 Compose rememberNavController 持有（AppNavigation.kt:184），Activity 侧不可读；以下为可见屏幕代理标记")
        try {
            put("homeSearchCount", homeSearchNodeCount())
            put("readerChromeBackCount", readerChromeBackCount())
            put("returnToReadingCount",
                composeRule.onAllNodesWithContentDescription("返回阅读处").fetchSemanticsNodes().size)
            put("marker1Count",
                composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().size)
        } catch (t: Throwable) {
            put("uiProbe", "<unavailable: $t>")
        }
    }

    @Before
    fun setUp() {
        runId = "run_" + UUID.randomUUID().toString().take(8)
        bookIdA = "${J1RealReaderFixtures.PREFIX_BOOK_A}-$runId"
        bookIdB = "${J1RealReaderFixtures.PREFIX_BOOK_B}-$runId"
        bookIdUnreadable = "${J1RealReaderFixtures.PREFIX_BOOK_UNREADABLE}-$runId"
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)

        db = J1RealReaderFixtures.openDatabase(context)

        // 1. 在 Activity 启动前保存引导基线并设为已完成，防止首帧异步读取未就绪弹出引导遮罩
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)

        // 2. 全量预检冲突并注入本 run 专属夹具与精确生产索引（BookEntity 插入成功立即记账）
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = context,
            db = db,
            runId = runId,
            bookIdA = bookIdA,
            bookIdB = bookIdB,
            bookIdUnreadable = bookIdUnreadable,
            ledger = ledger,
        )
        contentA = contents.first
        contentB = contents.second

        // 3. 准备就绪后再拉起真实 MainActivity，避免启动时序竞态
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        var cleanError: Throwable? = null
        var restoreError: Throwable? = null
        // v6：单独记录 tearDown / 主动 close 入口的时间戳与异常，
        // 用于区分「测试主体先失败」与「关闭 Activity 时失败」，不覆盖首个测试异常。
        val closeEnterAt = System.currentTimeMillis()
        var closeThrowable: Throwable? = null
        try {
            scenario?.close()
        } catch (t: Throwable) {
            closeThrowable = t
        }
        val closeExitAt = System.currentTimeMillis()
        try {
            if (::runId.isInitialized) {
                J1RealReaderFixtures.recordV6Diagnostics(
                    context, runId, "teardown_timeline", JSONObject().apply {
                        put("closeEnterAt", closeEnterAt)
                        put("closeExitAt", closeExitAt)
                        put("closeDurationMs", closeExitAt - closeEnterAt)
                        put("closeThrowableClass", closeThrowable?.javaClass?.name ?: JSONObject.NULL)
                        put("closeThrowableMessage", closeThrowable?.message ?: JSONObject.NULL)
                        closeThrowable?.let {
                            val w = java.io.StringWriter()
                            it.printStackTrace(java.io.PrintWriter(w))
                            put("closeThrowableStackTrace", w.toString())
                        }
                        put("scenarioStateAfterClose",
                            try { scenario?.state?.toString() } catch (t: Throwable) { "<unavailable: $t>" }
                                ?: JSONObject.NULL)
                        put("lastStep", lastStep)
                        put("firstFailureRecordedInBody", firstFailureRecorded)
                    })
            }
        } catch (t: Throwable) {
            Log.w(J1RealReaderFixtures.TAG, "teardown timeline record failed: $t")
        }
        // 注意：close 异常**不在清理之前**抛出（否则会取消正常的夹具清理与首选项还原），
        // 而是在清理完成之后如实外抛，绝不吞掉、也绝不靠跳过 Activity 销毁来隐藏。
        try {
                if (::db.isInitialized && db.isOpen) {
                    // 只清理本 run 成功创建的对象
                    J1RealReaderFixtures.cleanRunFixtures(context, db, ledger)
                    db.close()
                }
            } catch (t: Throwable) {
                cleanError = t
            } finally {
                try {
                    // finally 保证即使清理失败也必须执行引导状态基线恢复，失败可见不吞错
                    J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
                } catch (t: Throwable) {
                    restoreError = t
                }
            }
        if (cleanError != null) throw cleanError
        if (restoreError != null) throw restoreError
        // 清理完成后如实外抛 close 阶段异常（销毁 Activity 时的失败不再被静默吞掉）
        if (closeThrowable != null) throw closeThrowable
    }

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ──────────────────────────────────────────────────────────────────────────
    // 真实 Back 状态诊断采集（不改产品源码；只读语义树 / 关键节点 / 截图）
    // ──────────────────────────────────────────────────────────────────────────

    /** 首页「搜索」入口节点数量（>0 视为已回到首页 Shelf/Home）。 */
    private fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription("搜索", substring = true)
            .fetchSemanticsNodes().size

    /** 阅读器顶栏「返回」控件节点数量（>0 视为阅读器控件可见）。 */
    private fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size

    /**
     * 采集一次真实页面状态：关键节点计数 + merged/unmerged 语义树全文。
     * 关键节点用于客观区分阶段（首页 / 阅读器正文 / 阅读器控件可见性）。
     */
    private fun captureBackState(tag: String): JSONObject = JSONObject().apply {
        put("tag", tag)
        put("timestampMillis", System.currentTimeMillis())
        put("homeSearchCount", homeSearchNodeCount())
        put("readerChromeBackCount", readerChromeBackCount())
        put("returnToReadingCount",
            composeRule.onAllNodesWithContentDescription("返回阅读处").fetchSemanticsNodes().size)
        put("marker1Count",
            composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().size)
        put("marker2Count",
            composeRule.onAllNodesWithText(contentA.marker2, substring = true).fetchSemanticsNodes().size)
        put("mergedTree", composeRule.onRoot(useUnmergedTree = false).printToString())
        put("unmergedTree", composeRule.onRoot(useUnmergedTree = true).printToString())
    }

    /** 采集当前真实 UI 截图；失败只记录日志，不改变旅程判定。 */
    private fun captureScreenshot(tag: String) {
        try {
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val dir = J1RealReaderFixtures.getBackDiagDir(context)
            java.io.File(dir, "${runId}__${tag}.png").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (t: Throwable) {
            Log.w(J1RealReaderFixtures.TAG, "captureScreenshot($tag) failed: $t")
        }
    }

    /** 采集当前真实 UI 截图到 v5 专属诊断目录（搜索/位置诊断与 Back 诊断分开归档）。 */
    private fun captureV5Screenshot(tag: String) {
        try {
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val dir = J1RealReaderFixtures.getV5DiagDir(context)
            java.io.File(dir, "${runId}__${tag}.png").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (t: Throwable) {
            Log.w(J1RealReaderFixtures.TAG, "captureV5Screenshot($tag) failed: $t")
        }
    }

    /** 遍历语义树收集全部可读文本（Text / ContentDescription），用于正向识别结果卡片实际渲染内容。 */
    private fun collectSemanticsTexts(useUnmerged: Boolean, limit: Int = 120): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.let { list ->
                out.add("T:" + list.joinToString("") { it.text })
            }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { list ->
                out.add("CD:" + list.joinToString("") { it.toString() })
            }
            node.children.forEach { walk(it) }
        }
        try {
            walk(composeRule.onRoot(useUnmergedTree = useUnmerged).fetchSemanticsNode())
        } catch (t: Throwable) {
            out.add("<semantics walk failed: $t>")
        }
        return out.take(limit)
    }

    private fun clickableNodeCount(): Int =
        try {
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        } catch (t: Throwable) {
            -1
        }

    /**
     * 位置阶段证据：同一本书 A 的完整 12 列进度行 + updated_at + 解码后的可读 locator
     * + 对应 UI marker 计数 + 分页前置条件 + 当前可见文本采样。
     */
    private fun capturePositionStage(
        tag: String,
        pagedReport: J1RealReaderFixtures.PagedPreconditionReport?,
    ): JSONObject {
        val row = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        val decoded = row?.current_location_json?.let {
            com.creationreadingassistant.feature.reader.locator.LocatorCodec.locatorFromProgress(it)
        }
        return JSONObject().apply {
            put("tag", tag)
            put("timestampMillis", System.currentTimeMillis())
            put("progressRow", row?.toJson() ?: JSONObject.NULL)
            put("legacyOffset", row?.legacyOffset ?: -1)
            put("updated_at", row?.updated_at ?: JSONObject.NULL)
            put("locator", JSONObject().apply {
                put("legacyOffset", decoded?.legacyOffset ?: JSONObject.NULL)
                put("chapterIndex", decoded?.chapterIndex ?: JSONObject.NULL)
                put("charOffset", decoded?.charOffset ?: JSONObject.NULL)
                put("excerptFingerprint", decoded?.excerptFingerprint ?: JSONObject.NULL)
            })
            put("marker1Count",
                composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().size)
            put("marker2Count",
                composeRule.onAllNodesWithText(contentA.marker2, substring = true).fetchSemanticsNodes().size)
            put("homeSearchCount", homeSearchNodeCount())
            put("readerChromeBackCount", readerChromeBackCount())
            put("pagedPrecondition", pagedReport?.toJson() ?: JSONObject.NULL)
            put("visibleTexts", JSONArray(collectSemanticsTexts(false)))
        }
    }

    /**
     * 搜索诊断快照：一次采齐查询词、UI 关键节点计数、全部可读文本、merged/unmerged 语义树，
     * 以及同一实际 DB 中 B 的书籍行 / search_terms 原始行 / coverage 行。
     */
    private fun collectSearchSnapshot(
        tag: String,
        expectedLocationSnippetB: String?,
        cardMatcherB: SemanticsMatcher?,
    ): JSONObject {
        val texts = collectSemanticsTexts(false)
        val unmergedTexts = collectSemanticsTexts(true)
        val termRowsB = J1RealReaderFixtures.captureSearchTermRows(db, bookIdB)
        val betaRowsAll = J1RealReaderFixtures.captureSearchTermRowsByTerm(
            db, J1RealReaderFixtures.SEARCH_QUERY_B.lowercase())
        val coverageRowsB = J1RealReaderFixtures.captureCoverageRows(db, bookIdB)
        val bookRowB = J1RealReaderFixtures.captureBookRow(db, bookIdB)
        val arr = JSONArray()
        termRowsB.forEach { arr.put(it) }
        val arrBeta = JSONArray()
        betaRowsAll.forEach { arrBeta.put(it) }
        val arrCov = JSONArray()
        coverageRowsB.forEach { arrCov.put(it) }
        return JSONObject().apply {
            put("tag", tag)
            put("timestampMillis", System.currentTimeMillis())
            put("query", J1RealReaderFixtures.SEARCH_QUERY_B)
            put("searchFieldCount",
                composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮")
                    .fetchSemanticsNodes().size)
            put("expectedLocationSnippetB", expectedLocationSnippetB ?: JSONObject.NULL)
            put("bookTitleBNodeCount",
                composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_B, substring = true)
                    .fetchSemanticsNodes().size)
            put("expectedLocationNodeCount", expectedLocationSnippetB?.let {
                composeRule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().size
            } ?: -1)
            put("originalBasisNodeCount",
                composeRule.onAllNodesWithText("原文", substring = true).fetchSemanticsNodes().size)
            put("cardMatcherBCount", cardMatcherB?.let {
                composeRule.onAllNodes(it).fetchSemanticsNodes().size
            } ?: -1)
            put("clickableNodeCount", clickableNodeCount())
            put("noticeTexts", JSONArray(texts.filter { it.contains("索引") || it.contains("搜索") }))
            put("visibleTexts", JSONArray(texts))
            put("unmergedTexts", JSONArray(unmergedTexts))
            put("mergedTree", composeRule.onRoot(useUnmergedTree = false).printToString())
            put("unmergedTree", composeRule.onRoot(useUnmergedTree = true).printToString())
            put("dbBookRowB", bookRowB ?: JSONObject.NULL)
            put("dbSearchTermRowsB", arr)
            put("dbBetaTermRowsAllBooks", arrBeta)
            put("dbCoverageRowsB", arrCov)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 旅程 1：真实生产 UI 旅程（普通 A → 目标位置持久核实 SAVE_CAUSE=UNATTRIBUTED 建立冻结基线 → 搜索命中进入临时 B → 12 列冻结核验 → 返回 A → 水平翻页同时检查位置变化与合法保存）
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * v9：按生产命中规则推导同书某查询的期望展示坐标（与 journey01 同一判据）。
     * 生产 SearchIndexWorker 可能重建索引（deleteByBook 后按章重建），故有界等待推导值连续 3 次一致。
     */
    private fun awaitStableDerived(
        bookId: String,
        query: String,
        bodyText: String,
        timeoutMs: Long = 15_000,
    ): J1RealReaderFixtures.DerivedSearchCoordinate {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Int? = null
        var same = 0
        var latest = J1RealReaderFixtures.deriveSearchCoordinate(db, bookId, query, bodyText)
        while (System.currentTimeMillis() < deadline) {
            latest = J1RealReaderFixtures.deriveSearchCoordinate(db, bookId, query, bodyText)
            same = if (latest.expectedCharOffset != null && latest.expectedCharOffset == last) same + 1 else 1
            last = latest.expectedCharOffset
            if (latest.expectedCharOffset != null && same >= 3) break
            Thread.sleep(400)
        }
        return latest
    }

    @Test
    fun journey01_realProductionUi_normalAToTemporaryB_frozenDbThenReturnAndAdvance() {
        val stepRecord = mutableMapOf<String, Any>()
        val positionStages = JSONArray()
        val searchSnapshots = JSONArray()
        var searchDiagSaved = false
        var expectedLocationSnippetB: String? = null
        var cardMatcherB: SemanticsMatcher? = null
        var indexWaitJson: JSONObject = JSONObject()

        fun saveSearchDiagnostics(reason: String) {
            try {
                val payload = JSONObject().apply {
                    put("reason", reason)
                    put("query", J1RealReaderFixtures.SEARCH_QUERY_B)
                    put("expectedLocationSnippetB", expectedLocationSnippetB ?: JSONObject.NULL)
                    put("cardMatcherBDescription", cardMatcherB?.description ?: JSONObject.NULL)
                    put("indexWait", indexWaitJson)
                    put("snapshots", searchSnapshots)
                }
                val file = J1RealReaderFixtures.recordV5Diagnostics(context, runId, "search_diagnostics", payload)
                Log.i(J1RealReaderFixtures.TAG, "search diagnostics saved($reason): ${file.name}")
                searchDiagSaved = true
            } catch (t: Throwable) {
                Log.w(J1RealReaderFixtures.TAG, "saveSearchDiagnostics($reason) failed: $t")
            }
        }

        val stepMarkers = JSONArray()

        fun markStep(name: String) {
            lastStep = name
            try {
                stepMarkers.put(JSONObject().put("step", name).put("at", System.currentTimeMillis()))
                J1RealReaderFixtures.recordV5Diagnostics(
                    context, runId, "step_markers", JSONObject().put("markers", stepMarkers))
            } catch (t: Throwable) {
                Log.w(J1RealReaderFixtures.TAG, "markStep($name) failed: $t")
            }
        }

        fun savePositionDiagnostics(reason: String) {
            try {
                val payload = JSONObject().apply {
                    put("reason", reason)
                    put("bookIdA", bookIdA)
                    put("offset1", contentA.offset1)
                    put("offset2", contentA.offset2)
                    put("marker1", contentA.marker1)
                    put("stages", positionStages)
                }
                J1RealReaderFixtures.recordV5Diagnostics(context, runId, "position_diagnostics", payload)
            } catch (t: Throwable) {
                Log.w(J1RealReaderFixtures.TAG, "savePositionDiagnostics($reason) failed: $t")
            }
        }

        try {
        markStep("start")
        // 阶段 S0：夹具完成后、尚未进入阅读器（同一本书 A 的完整进度行）
        positionStages.put(capturePositionStage("S0_fixture_completed", null))
        markStep("S0_fixture_completed")

        // 步骤 1.1：通过真实首页「继续阅读」UI 入口点击书籍 A 进入普通阅读
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)).performClick()
        composeRule.waitForIdle()

        // 验证真实 ReaderRoute 加载正文 A，首个正文 Marker 可见，证明当前阅读位置
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()

        // 阶段 S1：进入普通 Reader 后（目标 marker1 已可见）
        positionStages.put(capturePositionStage("S1_reader_entered_marker1_visible", null))
        markStep("entered_A_marker1_visible")

        // 步骤 1.2：无副作用核验分页前置条件（读取实际注入的 readerSettings）
        var pagedReport: J1RealReaderFixtures.PagedPreconditionReport? = null
        scenario?.onActivity { activity ->
            val settings = activity.settingsStore.reader.value
            pagedReport = J1RealReaderFixtures.verifyPagedModePrecondition(
                readerMode = settings.readerMode,
                pagerEngineMode = settings.pagerEngineMode,
            )
        }
        assertNotNull("分页前置检查报告必须成功生成", pagedReport)
        assertTrue(
            "必须满足分页前置契约 (readerMode=${pagedReport!!.readerMode}, pagerEngineMode=${pagedReport!!.pagerEngineMode})",
            pagedReport!!.satisfiesContract
        )
        stepRecord["pagedPrecondition"] = pagedReport!!.toJson().toString()

        // 等待生产分页位置落库：分页引擎首帧上报即落库（5s 节流首次必命中）+ 500ms 防抖补写。
        // 有界等待进度行发生变化，再静置覆盖防抖窗口，取「进入阅读器后」的稳定行。
        val rowBeforeSettle = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        val settleDeadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < settleDeadline) {
            val now = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            if (now != null && rowBeforeSettle != null &&
                (now.current_location_json != rowBeforeSettle.current_location_json ||
                    now.updated_at != rowBeforeSettle.updated_at)
            ) break
            Thread.sleep(200)
        }
        Thread.sleep(800)
        positionStages.put(capturePositionStage("S1b_after_persist_settle", pagedReport))
        savePositionDiagnostics("entered_reader")
        markStep("persist_settle")

        // 步骤 1.3：有状态有上限 Back 流程 —— 诊断证据确认 ReaderScreen BackHandler 优先级链
        // (ReaderScreen.kt:300-316)：controlsVisible=true 时第一次 Back 被 BackHandler 消费用于
        // 隐藏控件 (ToggleControls(false))，不退出阅读器；controlsVisible=false 且非临时查阅时
        // BackHandler.enabled=false，第二次 Back 由系统处理，自然返回首页。
        val backDiag = mutableListOf<JSONObject>()
        backDiag.add(captureBackState("01_pre_back"))
        captureScreenshot("01_pre_back")

        val controlsVisibleBefore = readerChromeBackCount() > 0
        if (controlsVisibleBefore) {
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            Thread.sleep(500)
            backDiag.add(captureBackState("02_post_back_1_hide_controls"))
            captureScreenshot("02_post_back_1_hide_controls")
        }

        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        var homeSeen = false
        val observeDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < observeDeadline) {
            if (homeSearchNodeCount() > 0) {
                homeSeen = true
                break
            }
            Thread.sleep(250)
        }
        val timeoutTag = if (homeSeen) "03_home_detected" else "03_timeout_10s"
        backDiag.add(captureBackState(timeoutTag))
        captureScreenshot(timeoutTag)
        J1RealReaderFixtures.recordBackDiagnostics(context, runId, backDiag)
        stepRecord["back_diagnostics"] = J1RealReaderFixtures.summarizeBackDiagnostics(backDiag)

        assertTrue("Back 流程后应回到首页（搜索节点可见）", homeSeen)
        markStep("back_to_home")

        // 步骤 1.4：持久位置精确核验（SAVE_CAUSE=UNATTRIBUTED，不归因于特定 Back 事件）
        // 生产分页合同：持久化值是「当前页首字符的全书偏移」
        // （TxtPagedController.currentPageStartAbs 注释："当前页首字符的全书偏移。进度持久化以它为准。"），
        // 不是入参 offset 本身。故正确预期不是写死的某个数，而是「包含目标位点 offset1 的那一页的页起始」，
        // 独立判据为：① 非零（既有 J1RecoveryContract 反例表把 R=0 清零列为必须拒绝）；
        // ② 不超过目标位点 offset1；③ 该页 UI 实际渲染出 marker1（步骤 1.1 已断言可见）；
        // ④ 翻页后的下一页页起始严格大于 offset1（步骤 1.11 之后判定），
        //    即 offset1 ∈ [页起始, 下一页页起始)，与 marker / 页面映射一致。
        val snapA_frozenBaseline = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("书籍 A 持久层进度行必须存在", snapA_frozenBaseline)
        val pageStartA = snapA_frozenBaseline!!.legacyOffset
        assertTrue(
            "书籍 A 持久位置必须为非零页起始（实际=$pageStartA；清零被既有 J1 恢复合同列为必须拒绝）",
            pageStartA > 0,
        )
        assertTrue(
            "书籍 A 持久页起始不得超过目标位点 offset1（页起始=$pageStartA, offset1=${contentA.offset1}）",
            pageStartA <= contentA.offset1,
        )
        positionStages.put(capturePositionStage("S2_after_back", pagedReport))
        savePositionDiagnostics("after_back")
        markStep("position_asserted")
        stepRecord["snapA_frozenBaseline"] = snapA_frozenBaseline.toJson().toString()
        stepRecord["position_evidence_kind"] = "EXISTING_POSITION_OBSERVATION"
        stepRecord["position_contract"] = "PAGED_PAGE_START"
        stepRecord["page_start_offset"] = pageStartA
        stepRecord["save_cause"] = J1RealReaderFixtures.SAVE_CAUSE_UNATTRIBUTED
        stepRecord["save_cause_note"] = "仅确认已有持久位置（可能来自初始夹具或阅读器进入后按生产页起始合同重写）；不证明本轮新保存、Back完成或队列排空"

        // 步骤 1.5 前置：等待生产索引对 B 稳定（只读）。
        // 生产 App 会自行调度 SearchIndexWorker 重建索引（先 deleteByBook 再按章重建），
        // 夹具写入的索引行可能被重算；因此卡片显示的命中位置必须由**当前实际索引行**
        // 按生产合同推导（SearchOffsets.parse(offsets).first().start → SearchScreen 渲染"第 N 字附近"），
        // 而不是沿用夹具写入时的全书偏移。
        val indexWait = J1RealReaderFixtures.awaitStableIndexedCharOffset(
            db = db,
            bookId = bookIdB,
            term = J1RealReaderFixtures.SEARCH_QUERY_B,
            timeoutMs = 30_000,
        )
        indexWaitJson = JSONObject().apply {
            put("resolvedOffset", indexWait.resolvedOffset ?: JSONObject.NULL)
            put("stable", indexWait.stable)
            put("pollCount", indexWait.polls.size)
            put("polls", JSONArray(indexWait.polls))
            put("finalRows", JSONArray(indexWait.finalRows))
        }
        assertNotNull(
            "书籍 B 必须有可用的正文索引行（term=beta 且 offsets 可解析）；缺失即属于没有正确 hit 的情形",
            indexWait.resolvedOffset,
        )
        expectedLocationSnippetB = "第 ${indexWait.resolvedOffset} 字附近"
        stepRecord["index_wait"] = indexWaitJson.toString()
        markStep("index_wait_done")

        // 步骤 1.5：在首页点击顶部搜索按钮进入真实 SearchScreen
        composeRule.onNodeWithContentDescription("搜索", substring = true).performClick()
        composeRule.waitForIdle()
        markStep("search_opened")

        // 步骤 1.6：在搜索输入框输入查询词 "BETA"（先清空确保不拼接）
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val searchBox = composeRule.onNode(hasText("搜索书籍、灵感、笔记、高亮"))
        searchBox.performTextClearance()
        searchBox.performTextInput(J1RealReaderFixtures.SEARCH_QUERY_B)
        composeRule.waitForIdle()
        markStep("query_input")

        // 步骤 1.7（v6 修正）：正向识别 B 的正文命中卡片 —— 书名 + 定位信息（第 N 字附近）+ 原文来源 + 真实 click action。
        //
        // expected 必须由「预先已知查询 + 夹具正文 + 生产查询命中规则」推导，推导链路全部使用生产类本体：
        //   ① SearchTokenizer.tokenizeForQuery("BETA") 给出本次查询的合法 term 集合
        //      （长度 ≤6 会额外补单字 unigram：beta / b / e / t / a），即 beta 与单字结果的关系来源；
        //   ② 取 B 在 original 基准下的真实索引行，只保留 term ∈ ① 的行；
        //   ③ 用生产 SearchHitSelection.pickCoordinate 复刻生产排序与选点
        //      （先按章内命中的不同查询词数降序、再取章号最小；章内取偏移最小的区间）；
        //   ④ SearchScreen.contentSnippet 渲染为「第 N 字附近」。
        // UI 实际渲染值随后只被用来与推导值做**严格相等**校验，绝不反向改写 expected。
        var derived = J1RealReaderFixtures.deriveSearchCoordinate(
            db = db,
            bookId = bookIdB,
            query = J1RealReaderFixtures.SEARCH_QUERY_B,
            bodyText = contentB.bodyText,
        )
        // 生产 SearchIndexWorker 可能重建索引（deleteByBook 后按章重建），有界等待推导值连续 3 次一致
        val deriveDeadline = System.currentTimeMillis() + 15_000
        var lastDerived: Int? = null
        var sameDerived = 0
        while (System.currentTimeMillis() < deriveDeadline) {
            derived = J1RealReaderFixtures.deriveSearchCoordinate(
                db = db,
                bookId = bookIdB,
                query = J1RealReaderFixtures.SEARCH_QUERY_B,
                bodyText = contentB.bodyText,
            )
            sameDerived = if (derived.expectedCharOffset != null && derived.expectedCharOffset == lastDerived) {
                sameDerived + 1
            } else 1
            lastDerived = derived.expectedCharOffset
            if (derived.expectedCharOffset != null && sameDerived >= 3) break
            Thread.sleep(400)
        }
        stepRecord["search_derived"] = derived.toJson().toString()
        assertNotNull(
            "必须由生产命中规则推导出 B 的期望展示坐标（query=${derived.query}, 查询词=${derived.queryTerms}, " +
                "命中词=${derived.matchedTerms}, 索引行数=${derived.termRowCount}）",
            derived.expectedCharOffset,
        )
        val expectedOffsetB = derived.expectedCharOffset!!
        assertTrue(
            "推导坐标必须属于本次查询的合法命中偏移集合（derived=$expectedOffsetB, legal=${derived.legalOffsets}）",
            derived.legalOffsets.contains(expectedOffsetB),
        )
        assertTrue(
            "推导坐标必须可回溯到 B 正文中的真实词项（term=${derived.traceTerm}, chapter=${derived.traceChapterIndex}, " +
                "offset=$expectedOffsetB, 正文片段=${derived.traceBodySnippet}）",
            derived.traceBodyMatch,
        )
        assertNotNull(
            "B 必须存在 term=beta 的正文索引行（beta 与单字 unigram 的关系据此说明）",
            derived.betaRowOffset,
        )
        // 如实登记 beta 行偏移与展示坐标的关系：展示坐标由生产排序选点决定，未必等于 beta 行偏移
        stepRecord["search_beta_vs_displayed"] =
            "betaRowOffset=${derived.betaRowOffset}, displayedOffset=$expectedOffsetB, " +
                "displayedTerm=${derived.traceTerm}, winningChapter=${derived.winningChapterIndex}, " +
                "distinctTermsInWinningChapter=${derived.distinctTermsInWinningChapter}, legalOffsets=${derived.legalOffsets}"
        expectedLocationSnippetB = derived.expectedLocationSnippet
        markStep("expected_derived")

        searchSnapshots.put(collectSearchSnapshot("01_after_input", expectedLocationSnippetB, cardMatcherB))

        var cardTextB: String? = null
        var uiLocationOffset: Int? = null
        val cardDeadline = System.currentTimeMillis() + 10_000
        var lastSampleAt = 0L
        while (System.currentTimeMillis() < cardDeadline) {
            val found = collectSemanticsTexts(false)
                .filter { it.startsWith("T:") }
                .map { it.removePrefix("T:") }
                .firstOrNull {
                    it.contains(J1RealReaderFixtures.BOOK_TITLE_B) &&
                        it.contains("正文") &&
                        it.contains("字附近")
                }
            if (found != null) {
                val m = Regex("第 (\\d+) 字附近").find(found)
                if (m != null) {
                    cardTextB = found
                    uiLocationOffset = m.groupValues[1].toIntOrNull()
                    break
                }
            }
            val nowAt = System.currentTimeMillis()
            if (nowAt - lastSampleAt >= 2_000) {
                lastSampleAt = nowAt
                searchSnapshots.put(
                    collectSearchSnapshot("wait_${searchSnapshots.length()}", expectedLocationSnippetB, cardMatcherB))
            }
            Thread.sleep(200)
        }
        if (cardTextB == null || uiLocationOffset == null) {
            searchSnapshots.put(collectSearchSnapshot("timeout_final", expectedLocationSnippetB, cardMatcherB))
            captureV5Screenshot("search_timeout")
            saveSearchDiagnostics("card_not_rendered")
            assertTrue("搜索正文命中卡片未渲染；完整诊断已归档到宿主 j1_v5_diag 目录", false)
        }

        markStep("card_found")
        // 卡片内容正向识别：书名 B + 类型标签「正文」+ 原文来源「原文」+ 定位信息「第 N 字附近」
        assertTrue(
            "命中卡片必须同时具备书名 B、类型标签与原文来源（实际=$cardTextB）",
            cardTextB!!.contains("原文"),
        )
        // v6 关键门禁：UI 实际渲染的定位信息必须与**生产命中规则推导值**严格相等。
        // 这是「目标查询命中」的判据，不是「任意 B 卡片可点击」；观察值不得反向改写 expected。
        assertEquals(
            "卡片实际渲染的定位信息必须与生产命中规则推导值一致（derived=$expectedOffsetB, ui=$uiLocationOffset, " +
                "cardText=$cardTextB, derivedTerm=${derived.traceTerm}, legal=${derived.legalOffsets}）",
            expectedOffsetB, uiLocationOffset!!,
        )
        // 展示坐标仍须可追溯到 B 的真实正文索引行
        val rowsForUiOffset = J1RealReaderFixtures.findIndexedRowsWithStart(db, bookIdB, uiLocationOffset)
        assertTrue(
            "展示坐标「第 $uiLocationOffset 字附近」必须可追溯到 B 的正文索引行",
            rowsForUiOffset.isNotEmpty(),
        )
        val betaRowOffsets = J1RealReaderFixtures.captureSearchTermRows(db, bookIdB)
            .firstOrNull { it.optString("term") == J1RealReaderFixtures.SEARCH_QUERY_B.lowercase() }
            ?.optString("offsets")
        assertNotNull("B 必须存在 term=beta 的正文索引行", betaRowOffsets)
        assertTrue(
            "term=beta 的索引行 offsets 必须可解析（实际=$betaRowOffsets）",
            com.creationreadingassistant.feature.search.SearchOffsets
                .parse(betaRowOffsets!!).isNotEmpty(),
        )

        stepRecord["search_card_text"] = cardTextB
        stepRecord["search_ui_location_offset"] = uiLocationOffset
        stepRecord["search_index_rows_for_ui_offset"] = rowsForUiOffset.toString()
        stepRecord["search_index_beta_row_offsets"] = betaRowOffsets
        // expectedLocationSnippetB 保持为推导值（derived.expectedLocationSnippet），不在此处由 UI 观察值覆盖

        cardMatcherB = hasClickAction() and
            hasText(J1RealReaderFixtures.BOOK_TITLE_B, substring = true) and
            hasText(expectedLocationSnippetB!!, substring = true) and
            hasText("原文", substring = true)

        searchSnapshots.put(collectSearchSnapshot("02_card_found", expectedLocationSnippetB, cardMatcherB))
        assertTrue(
            "必须命中具备真实 click action 的 B 正文卡片（书名 + 定位 + 原文）",
            composeRule.onAllNodes(cardMatcherB!!).fetchSemanticsNodes().isNotEmpty(),
        )
        // v6：点击前后记录 Activity 生命周期 / route 代理 / 关键 UI（用于区分「点击导致」
        // 与「销毁 Activity 时」的失败，不据最终栈猜修导航）
        val preClickSnapshot = routeLifecycleSnapshot("pre_click")
        stepRecord["pre_click_lifecycle"] = preClickSnapshot.toString()
        J1RealReaderFixtures.recordV6Diagnostics(
            context, runId, "click_window", JSONObject().apply {
                put("preClick", preClickSnapshot)
            })
        composeRule.onNode(cardMatcherB!!).performClick()
        markStep("card_clicked")
        composeRule.waitForIdle()
        try {
            val postClickSnapshot = routeLifecycleSnapshot("post_click")
            stepRecord["post_click_lifecycle"] = postClickSnapshot.toString()
            J1RealReaderFixtures.recordV6Diagnostics(
                context, runId, "click_window", JSONObject().apply {
                    put("preClick", preClickSnapshot)
                    put("postClick", postClickSnapshot)
                })
        } catch (t: Throwable) {
            Log.w(J1RealReaderFixtures.TAG, "post click lifecycle snapshot failed: $t")
        }
        try {
            searchSnapshots.put(collectSearchSnapshot("03_after_click_B", expectedLocationSnippetB, cardMatcherB))
        } catch (t: Throwable) {
            Log.w(J1RealReaderFixtures.TAG, "after click B snapshot failed: $t")
        }

        // 步骤 1.8：验证 B 正文成功渲染，且 UI 出现"返回阅读处"图标按钮
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentB.markerB, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentB.markerB, substring = true)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed()
        markStep("B_rendered_return_button_visible")

        // 步骤 1.9：严格持久化断言 —— 临时查阅 B 期间，普通阅读 A 的全部 12 列绝对冻结
        val snapA_duringB = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        snapA_frozenBaseline.assertExactEquals(snapA_duringB, "临时阅读 B 期间，普通阅读 A 的 12 列进度行绝对不得发生任何改动")

        val snapB_during = J1RealReaderFixtures.captureProgressRow(db, bookIdB)
        assertNull("临时查阅期间不得将临时位置写入 B 的普通阅读进度", snapB_during)
        stepRecord["snapA_duringB"] = snapA_duringB!!.toJson().toString()
        markStep("A_frozen_during_B_verified")

        // 步骤 1.10：点击"返回阅读处"图标按钮，按生产合同直接退回 A
        composeRule.onNodeWithContentDescription("返回阅读处").performClick()
        composeRule.waitForIdle()

        // 验证成功返回 A：正文 Marker A 重新可见，"返回阅读处"按钮自动消失
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("返回阅读处").assertCountEquals(0)
        markStep("returned_A")

        // 记录返回 A 之后的稳定基线
        val snapA_returned = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("返回 A 后的稳定进度行必须存在", snapA_returned)
        stepRecord["snapA_returned"] = snapA_returned!!.toJson().toString()

        // 步骤 1.11：在返回 A 之后，通过水平拖动翻页手势（swipeLeft）改变阅读位置
        composeRule.onNode(hasText(contentA.marker1, substring = true)).performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        // 等待条件【同时】检查真实 locator 位置变化与合法持久保存（拒绝仅时间变新假绿）
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val latest = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            latest != null &&
                latest.updated_at > snapA_returned.updated_at &&
                latest.current_location_json != snapA_returned.current_location_json &&
                latest.legacyOffset >= 0 && latest.legacyOffset != snapA_returned.legacyOffset
        }

        // 读取生产保存生成的完整 12 列行快照，断言时间推进与位置实质变更
        val snapA_afterAdvance = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("手势操作后生产保存生成的进度行必须存在", snapA_afterAdvance)
        snapA_afterAdvance!!.assertLegalProgressAdvance(snapA_returned, "返回 A 翻页后生产保存")
        markStep("advance_verified")

        // 双向断言：翻页后原有 Marker A1 已翻出可视区域
        composeRule.onAllNodesWithText(contentA.marker1, substring = true).assertCountEquals(0)
        stepRecord["snapA_afterAdvance"] = snapA_afterAdvance.toJson().toString()

        // 页区间闭合判定：offset1 必须落在 [冻结页起始, 翻页后页起始) 内，
        // 证明持久化的页起始正是「包含目标 marker1 的那一页」的起始，而不是任意非负位置。
        val nextPageStart = snapA_afterAdvance.legacyOffset
        assertTrue(
            "目标位点必须落在冻结页起始与翻页后页起始之间 (页起始=${snapA_frozenBaseline.legacyOffset}, offset1=${contentA.offset1}, 下一页起始=$nextPageStart)",
            snapA_frozenBaseline.legacyOffset <= contentA.offset1 && contentA.offset1 < nextPageStart,
        )
        stepRecord["page_interval_check"] =
            "pageStart=${snapA_frozenBaseline.legacyOffset} <= offset1=${contentA.offset1} < nextPageStart=$nextPageStart"

        searchSnapshots.put(collectSearchSnapshot("03_after_journey", expectedLocationSnippetB, cardMatcherB))
        saveSearchDiagnostics("journey01_success")
        positionStages.put(capturePositionStage("S3_after_advance", pagedReport))
        savePositionDiagnostics("after_advance")

        // 记录结构化证据，并如实声明未覆盖项
        J1RealReaderFixtures.recordStepEvidence(context, runId, "journey01_success") {
            put("status", "PASS")
            put("steps", JSONObject(stepRecord as Map<*, *>))
            put("not_covered", listOf("EXIT_SAVE_REQUEST_COMPLETION", "ALL_PENDING_SAVES_DRAINED", "NESTED_TEMPORARY_INSPECTION"))
        }
        } catch (t: Throwable) {
            // v6：捕获**首个**导致测试主体退出的异常 —— 先记录原始 Throwable、完整栈与时间戳，
            // 随后原样抛出（不吞、不降级、不因取证改变判定）。
            val at = System.currentTimeMillis()
            if (!firstFailureRecorded) {
                firstFailureRecorded = true
                try {
                    J1RealReaderFixtures.recordFirstFailure(
                        context = context,
                        runId = runId,
                        step = lastStep,
                        throwable = t,
                        atMillis = at,
                        extra = routeLifecycleSnapshot("first_failure"),
                    )
                } catch (rec: Throwable) {
                    Log.w(J1RealReaderFixtures.TAG, "recordFirstFailure failed: $rec")
                }
            }
            throw t
        } finally {
            // 失败诊断 finally：必须在 tearDown 删除夹具之前落盘；
            // 采集失败单独记录，绝不覆盖上面已记录的首个测试异常。
            if (!searchDiagSaved) {
                try {
                    searchSnapshots.put(
                        collectSearchSnapshot("failure_finally", expectedLocationSnippetB, cardMatcherB))
                    captureV5Screenshot("search_failure_finally")
                    saveSearchDiagnostics("failure_finally")
                } catch (t: Throwable) {
                    Log.w(J1RealReaderFixtures.TAG, "failure finally search diagnostics failed: $t")
                }
            }
            try {
                savePositionDiagnostics("finally")
            } catch (t: Throwable) {
                Log.w(J1RealReaderFixtures.TAG, "failure finally position diagnostics failed: $t")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 旅程 2：同书不同 locator 真实落点检验（搜索分别进入 A@offset1 与 A@offset2 真实落点）
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun journey02_sameBookDifferentLocators_landsOnDistinctMarkers() {
        // 步骤 2.1：在首页点击搜索图标
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("搜索", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("搜索", substring = true).performClick()
        composeRule.waitForIdle()

        // 步骤 2.2：搜索 "ALPHA"（命中 offset1）并点击正文命中卡片
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮").fetchSemanticsNodes().isNotEmpty()
        }
        var searchBox = composeRule.onNode(hasText("搜索书籍、灵感、笔记、高亮"))
        searchBox.performTextClearance()
        searchBox.performTextInput(J1RealReaderFixtures.SEARCH_QUERY_A1)
        composeRule.waitForIdle()

        // v9：按生产命中规则正向推导 A1 的期望展示坐标（与 journey01 同判据）。
        // 生产卡片展示的是**索引行给出的章内偏移**（SearchHit.charOffset → contentSnippet
        // 「第 N 字附近」），不是夹具里预先算好的全书偏移；因此期望值必须由生产规则推导，
        // UI 实际渲染值仅用于严格相等校验，绝不用夹具全局偏移硬编码。
        val derivedA1 = awaitStableDerived(bookIdA, J1RealReaderFixtures.SEARCH_QUERY_A1, contentA.bodyText)
        assertNotNull(
            "必须由生产命中规则推导出 A1 的期望展示坐标（query=${derivedA1.query}, 命中词=${derivedA1.matchedTerms}）: " +
                derivedA1.toJson().toString(),
            derivedA1.expectedCharOffset,
        )
        val snippetA1 = derivedA1.expectedLocationSnippet!!

        // 按 Root 审查采纳的 merged 语义卡片选择器精准定位 A1 卡片（用推导出的展示坐标）
        val cardMatcherA1 = hasClickAction() and
            hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true) and
            hasText(snippetA1, substring = true) and
            hasText("原文", substring = true)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(cardMatcherA1).fetchSemanticsNodes().isNotEmpty()
        }
        // cardMatcherA1 要求卡片 merged 文本包含 hasText(snippetA1)，即 UI 实际渲染的定位信息
        // 必须与生产推导值一致 —— 观察值不反向改写 expected。
        composeRule.onNode(cardMatcherA1).performClick()
        composeRule.waitForIdle()

        // 验证阅读器首个位点落点正确：渲染 Marker A1，Marker A2 不可见
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        composeRule.onAllNodesWithText(contentA.marker2, substring = true).assertCountEquals(0)

        // 步骤 2.3：返回原搜索页并替换查询
        // 生产 BackHandler 优先级链（ReaderScreen.kt:300-316）：进入阅读器后控件默认可见，
        // 第一次 Back 被消费用于隐藏控件（ToggleControls(false)）；控件隐藏后再按一次才离开
        // 阅读器（本旅程从未记录普通阅读位置，临时返回栈为空，故 Back 走 NavController 回退到
        // 原 SearchScreen，不是首页）。直接复用既有搜索输入框，不再等待/点击首页搜索图标。
        if (readerChromeBackCount() > 0) {
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            Thread.sleep(500)
        }
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        // 步骤 2.4：替换输入搜索 "SECOND"（命中 offset2），彻底避免追加拼接。
        // 返回搜索页后查询词仍保留（VM 按会话持有），placeholder 不再渲染，故按
        // 「可输入语义」定位该 TextField，而不是按 placeholder 文本。
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        searchBox = composeRule.onNode(hasSetTextAction())
        searchBox.performTextClearance()
        searchBox.performTextInput(J1RealReaderFixtures.SEARCH_QUERY_A2)
        composeRule.waitForIdle()

        // v9：同样按生产规则推导 A2 的期望展示坐标
        val derivedA2 = awaitStableDerived(bookIdA, J1RealReaderFixtures.SEARCH_QUERY_A2, contentA.bodyText)
        assertNotNull(
            "必须由生产命中规则推导出 A2 的期望展示坐标（query=${derivedA2.query}, 命中词=${derivedA2.matchedTerms}）",
            derivedA2.expectedCharOffset,
        )
        val snippetA2 = derivedA2.expectedLocationSnippet!!
        assertTrue(
            "A1 与 A2 的推导展示坐标必须互斥（A1=${derivedA1.expectedCharOffset}, A2=${derivedA2.expectedCharOffset}）",
            derivedA1.expectedCharOffset != derivedA2.expectedCharOffset,
        )

        // 按 Root 审查采纳的 merged 语义卡片选择器精准定位 A2 卡片（用推导出的展示坐标）
        val cardMatcherA2 = hasClickAction() and
            hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true) and
            hasText(snippetA2, substring = true) and
            hasText("原文", substring = true)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(cardMatcherA2).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(cardMatcherA2).performClick()
        composeRule.waitForIdle()

        // 验证阅读器第二个位点落点正确：渲染相距充分且互斥的 Marker A2，Marker A1 不可见
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentA.marker2, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker2, substring = true)).assertIsDisplayed()
        composeRule.onAllNodesWithText(contentA.marker1, substring = true).assertCountEquals(0)

        J1RealReaderFixtures.recordStepEvidence(context, runId, "journey02_locators_verified") {
            put("status", "PASS")
            put("detail", "同书不同 locator 能够分别精准落点在对应的 Marker A1 与 Marker A2")
            put("offset1", contentA.offset1)
            put("offset2", contentA.offset2)
            put("queryOffset1", contentA.queryOffset1)
            put("queryOffset2", contentA.queryOffset2)
            put("derivedA1", derivedA1.toJson())
            put("derivedA2", derivedA2.toJson())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 旅程 3：退出辅助检查与诚实未覆盖声明（仅观察正常退出导航与持久层已有位置自洽）
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun journey03_normalReadingExit_observesSubmittedPositionWithoutExitAttribution() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 正常按 Back 退出到首页。
        // 生产 BackHandler 优先级链（ReaderScreen.kt:300-316）：进入阅读器后控件默认可见，
        // 第一次 Back 被消费用于隐藏控件（ToggleControls(false)），不退出阅读器；
        // 控件隐藏后再按一次 Back 才离开阅读器 —— 与 journey01 步骤 1.3 同一合同。
        if (readerChromeBackCount() > 0) {
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            Thread.sleep(500)
        }
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("搜索", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 观察持久层已有位置行
        val snapAfterExit = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull(snapAfterExit)
        assertEquals(bookIdA, snapAfterExit!!.book_id)
        J1RealReaderFixtures.assertObservedPersistedLocation(
            snapshot = snapAfterExit,
            expectedOffset = contentA.offset1,
            label = "退出后观察持久层已有位置",
        )

        // 明确记录证据：此项只能证明正常退出导航与持久层实际位置自洽，不证明退出保存专属完成
        J1RealReaderFixtures.recordStepEvidence(context, runId, "journey03_normal_exit_observed") {
            put("status", "PASS")
            put("coverage", "NORMAL_EXIT_NAVIGATION_AND_OBSERVED_POSITION")
            put("position_evidence_kind", "EXISTING_POSITION_OBSERVATION")
            put("save_cause", J1RealReaderFixtures.SAVE_CAUSE_UNATTRIBUTED)
            put("snapshot", snapAfterExit.toJson())
            put("not_covered", listOf("EXIT_SAVE_REQUEST_COMPLETION", "ALL_PENDING_SAVES_DRAINED"))
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 附：生产 Route 编解码自洽纯逻辑门禁
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun journey04_productionRouteEncodingRoundTripsBookAndLocator() {
        val a = target(bookIdA, 100)
        val temp = requireNotNull(readerTemporaryRoute(a))
        val normal = requireNotNull(readerReturnRoute(a))
        assertTrue("临时 route 必须携带 navigationMode=temporary", temp.contains("navigationMode=temporary"))
        assertEquals(ReaderNavigationMode.TEMPORARY, readerNavigationMode("temporary"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(null))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("normal"))
        assertEquals(100, decodeSourceLocatorFromRoute(temp)?.legacyOffset)
        assertEquals(100, decodeSourceLocatorFromRoute(normal)?.legacyOffset)
    }
}
