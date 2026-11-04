package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.search.SearchTokenizer
import com.creationreadingassistant.feature.search.TxtChapterSplitter
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务129-v1：F1 配置重建回归 —— 临时阅读会话 × Activity 配置重建（[ActivityScenario.recreate]）。
 *
 * ## 被保护的生产合同（源码实读 2026-10-26，全部真实生产入口驱动）
 *
 * - AppNavigation.kt:196-213（F1 修复 7a6046a5）：会话结束判据只认**真实非 null** 的
 *   back stack destination.route。Activity 重建首帧 navBackStackEntry==null 只是导航状态尚未
 *   恢复，不是用户离开 reader，不得借底栏显示用的 Home fallback 调 endReaderSession。
 * - TemporaryReadingNavigationViewModel：endReaderSession 清 active/临时返回栈/在途请求，
 *   并**整体作废已提交请求身份 committedRequestIds**；该 VM 是 Activity 作用域 hiltViewModel，
 *   配置重建时随 ViewModelStore 存活。
 * - ReaderRoute.kt:58-117：TEMPORARY route（重）组合时重新 requestTemporaryInspection，
 *   文档 ready 后 commitPendingInspection；目的地已是 active 时提交为去重 no-op。
 * - SearchScreen.searchContentHitNavigation：正文命中 →
 *   resolveLegacyOffset 先把索引章内偏移换算为全书 offset，再
 *   readerTemporaryRouteForSource(bookId, 全书offset, chapterIndex=null, charOffset=null)；
 *   协调器目的地 == sourceTarget(route)（本测试按同一生产链推导精确期望）。
 * - 120-v1 会话边界：所有临时查阅入口（搜索/我的-阅读笔记/灵感）都在 reader 域外，
 *   进入前 endReaderSession 已清空会话 ⇒ 搜索入口的临时查阅必然**空栈**、无「返回阅读处」
 *   按钮（119/128 设备已证），返回交还导航回退到搜索页。
 *
 * ## F1 判别器（本测试核心，非「不崩溃」空壳）
 *
 * 协调器新建后 requestTemporaryInspection 是唯一 `++inspectionRequestSeq` 来源，本旅程在
 * 重建前恰好进入一次临时查阅 ⇒ 首次请求 rid=1、重建后恢复的 route 重新请求 rid=2（均由代码
 * 合同确定，非时序采样）。rid=1 成功提交后 committedRequestIds 含 1：
 * - **F1 修复路径**：重建首帧不调 endReaderSession ⇒ `isRequestCommitted(1)` 重建后仍为 true。
 * - **F1 缺陷路径**（借 Home fallback 结束会话）：首帧清空 committedRequestIds，重建后重提交
 *   只标记 rid=2，rid=1 永久丢失 ⇒ `isRequestCommitted(1) == false`。
 *
 * 即：在当前生产可达的（空栈）场景里，active/栈/pending 在缺陷路径会被重提交收敛、UI 无差异，
 * 已提交请求身份是缺陷**唯一的持久可观察痕迹**；本测试对它做确定断言。若未来产品加入
 * reader 域内的临时查阅入口（非空栈成为可达态），应另补「返回阅读处按钮跨重建存活」的
 * UI 级断言（见 task129 REPORT 的下一授权缺口）。
 *
 * ## 旅程（单一 @Test；全部真实 UI 动作，无直接 NavController/协调器写入）
 *
 * 0a. 宿主必须以 `-e craRunId T136V1-YYYYMMDD-HHMMSS` 传入 runId；无本机随机 fallback，
 *     缺失/格式不符在打开 DB / onboarding / fixture 写入之前失败。
 * 0. 只读预检（bookId / 查询词 token 全库 / 目录无冲突）先于本轮 onboarding、夹具与 DAO 写入
 *    → 生产 DAO 建书 + 真实 TXT 夹具 + 生产单书索引入口（SearchIndexRepository.indexSingleBook）
 *    → 索引后严格核验 token 全库恰一行且归属本轮 book；预置普通进度行作为污染对照基线。
 *    每个阶段 JSON 含 runId/bookId；临时落点、重建后、Back 后另存实际 progressRow（12 列），
 *    供宿主独立比对基线，不只依赖 status PASS。
 * 1. 启动 MainActivity → 等 Home 生产搜索入口可见并真实点击（CD=搜索）→ 输入查询词 → 唯一正文命中卡。
 * 2. 点击命中卡 → 临时阅读器真实落点（marker1 可见 / marker2 不可见）+ 协调器合同断言
 *    （active==期望目的地、空栈、normalReading==null、rid=1 已提交、12 列进度冻结）。
 * 3. [ActivityScenario.recreate]（配置重建）→ 同一协调器实例（assertSame）→ 阅读器重落点 +
 *    **F1 核心断言 isRequestCommitted(1) 仍为 true** + active 原样存活 + 12 列仍冻结。
 * 4. 真实 Back → 落搜索页（query/命中卡保留）→ 协调器会话由真实 route 变更**合法**结束
 *    （active==null）→ 12 列终冻结。
 *
 * 仪器内不删除任何本轮创建物（登记后由外层隔离合同处理）；失败先取证后原样重抛。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TemporaryReadingRecreationRegressionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var bookId: String
    private lateinit var bodyText: String
    private lateinit var fixtureFile: File
    private lateinit var resultsDir: File
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /**
     * 是否已捕获 onboarding 基线。只读预检先于 prepareOnboardingState 执行；预检失败时
     * [originalOnboarded] 的 null 只表示「未捕获」，tearDown 不得把它当作「原本不存在」删除。
     */
    private var onboardingStateCaptured = false
    private lateinit var baselineRow: J1RealReaderFixtures.ProgressRowSnapshot

    /** 本轮创建物登记（不删除，交外层隔离合同）。 */
    private val createdAssets = org.json.JSONArray()

    private var lastPhase: String = "start"

    /** 重建前观察到的协调器（Activity 作用域 hiltViewModel 同一实例）。 */
    private var coordinator: TemporaryReadingNavigationViewModel? = null

    // ── 常量（与既有 119 命名风格一致；token/markers 独立，不与历史夹具串线） ──

    companion object {
        const val TAG = "t129"

        /** 宿主必须以 `am instrument -e craRunId T136V1-YYYYMMDD-HHMMSS` 传入；无本机随机 fallback。 */
        const val RUN_ID_ARG = "craRunId"
        val RUN_ID_REGEX = Regex("""^T136V1-\d{8}-\d{6}$""")

        const val TITLE = "测试 TXT 壹贰玖F1"
        const val AUTHOR = "测试作者壹贰玖"

        /** 查询词 10 字符：tokenizeForQuery 对 length ≤ 6 才补单字 unigram，查询侧只一个 token。 */
        const val QUERY = "CRAQX129FL"
        const val SNIPPET = "唯一检索词 $QUERY 出现于此段。"
        const val MARKER_1 = "【CRA129FA1】"
        const val MARKER_2 = "【CRA129FB2】"

        const val SEARCH_FIELD_PLACEHOLDER = "搜索书籍、灵感、笔记、高亮"
        const val HOME_SEARCH_CD = "搜索"
        const val READER_CHROME_BACK_CD = "返回"
        const val RETURN_TO_READING_CD = "返回阅读处"
        const val ORIGIN_LABEL = "原文"

        /**
         * F1 判别 rid：协调器实例内 inspectionRequestSeq 从 0 起、requestTemporaryInspection
         * 是唯一自增来源；本旅程重建前恰好一次临时查阅请求 ⇒ 首请求 rid=1。
         */
        const val FIRST_REQUEST_ID = 1L

        const val UI_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 300L
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private fun fixtureRunDir(): File = File(context.filesDir, "cra129_isolated_fixtures/$runId")

    /** 正文：查询词全书唯一、marker1 紧随其后、marker2 相距 ≥4000 字（落点互斥判别）。 */
    private fun buildBody(): String {
        val sb = StringBuilder()
        sb.append("《").append(TITLE).append("》\n\n")
        sb.append("第一章 序章\n")
        while (sb.length < 4000) sb.append("这是用于填充壹书前置段落的内容文本。")
        val snippetOffset = sb.length
        sb.append(SNIPPET)
        check(sb.indexOf(QUERY) == snippetOffset + "唯一检索词 ".length) {
            "查询词 offset 必须落在 snippet 内部（=$snippetOffset）"
        }
        val marker1Offset = sb.length
        sb.append(MARKER_1)
        sb.append("\n\n第二章 中段\n")
        while (sb.length < marker1Offset + 4000) sb.append("填充壹书中段内容以充分拉开两个位点之间距离的文本。")
        sb.append(MARKER_2)
        sb.append("\n\n第三章 完结尾声\n后续正文结尾内容。\n")
        return sb.toString()
    }

    // ── UI 代理观测（与既有旅程同一口径） ─────────────────────────────────────

    /** Home 生产搜索入口（顶部 IconButton，CD="搜索"）的可见节点数。 */
    private fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription(HOME_SEARCH_CD, substring = true)
            .fetchSemanticsNodes().size

    private fun markerCount(marker: String): Int =
        composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().size

    private fun returnToReadingCount(): Int =
        composeRule.onAllNodesWithContentDescription(RETURN_TO_READING_CD, substring = true)
            .fetchSemanticsNodes().size

    private fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription(READER_CHROME_BACK_CD, substring = true)
            .fetchSemanticsNodes().size

    /** 搜索框 EditableText（key 类型 AnnotatedString，取 .text）。 */
    private fun searchFieldText(): String? =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun await(phase: String, timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val observed = try {
                block()
            } catch (t: Throwable) {
                false
            }
            if (observed) return true
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$phase 等待超时（$timeoutMs ms）：$description")
        return false
    }

    /** 正文命中卡 merged 选择器（书名 + 定位 snippet + 原文来源 + 真实 click action）。 */
    private fun contentCardMatcher(locationSnippet: String): SemanticsMatcher =
        hasClickAction() and hasText(TITLE, substring = true) and
            hasText(locationSnippet, substring = true) and
            hasText(ORIGIN_LABEL, substring = true)

    // ── 协调器观察（只读；Activity 作用域 ViewModelStore 与 hiltViewModel 同 store 同 key） ──

    private fun observeCoordinator() {
        scenario!!.onActivity { activity ->
            coordinator = ViewModelProvider(activity)[TemporaryReadingNavigationViewModel::class.java]
        }
    }

    private fun pressSystemBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    // ── 证据 ─────────────────────────────────────────────────────────────────

    private fun recordEvidence(phase: String, payload: JSONObject.() -> Unit) {
        lastPhase = phase
        val obj = JSONObject()
            .put("phase", phase)
            .put("at", Instant.now().toString())
            .put("runId", runId)
            .put("bookId", bookId)
            .put("packageName", context.packageName)
        payload(obj)
        File(resultsDir, "$phase.json").writeText(obj.toString(2))
        android.util.Log.i(TAG, "$phase ${obj.toString(0)}")
    }

    private fun recordAsset(kind: String, path: String, reason: String) {
        createdAssets.put(JSONObject().put("kind", kind).put("path", path).put("retainReason", reason))
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // 1. craRunId 只接受宿主传入（am instrument -e craRunId T136V1-YYYYMMDD-HHMMSS）；
        //    无本机随机 fallback；缺失/格式不符必须在打开 DB / onboarding / fixture 写入之前失败。
        val rawRunId = requireNotNull(
            InstrumentationRegistry.getArguments().getString(RUN_ID_ARG),
        ) { "宿主必须以 -e $RUN_ID_ARG 传入 runId（T136V1-YYYYMMDD-HHMMSS）" }
        require(RUN_ID_REGEX.matches(rawRunId)) {
            "runId 格式不符（实际=$rawRunId，期望 T136V1-\\d{8}-\\d{6}）"
        }
        runId = rawRunId
        bookId = "cra129-txt-a-$runId"

        db = J1RealReaderFixtures.openDatabase(context)
        val token = QUERY.lowercase()

        // 全量预检（只读、无写入）：既有索引冲突必须先于本轮 onboarding / fixture / DAO 写入
        // 与 indexSingleBook 判定——否则本轮索引自身会让「全库无既有行」恒假。
        // bookId / 查询词 token / run 目录 / 结果目录任一存在即拒绝，绝不删除未知对象。
        val conflict = runBlocking { db.bookDao().getById(bookId) }
        val preExistingIndexRows = J1RealReaderFixtures.captureSearchTermRowsByTerm(db, token)
        val runDir = fixtureRunDir()
        resultsDir = File(context.getExternalFilesDir(null), "t129-results-$runId")
        if (conflict != null || preExistingIndexRows.isNotEmpty() || runDir.exists() || resultsDir.exists()) {
            error(
                "检测到冲突记录或残留（book=${conflict != null}, " +
                    "indexRows=${preExistingIndexRows.size}, runDir=${runDir.exists()}, " +
                    "resultsDir=${resultsDir.exists()}），拒绝启动以防混淆现有数据。",
            )
        }
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        onboardingStateCaptured = true
        check(runDir.mkdirs()) { "创建夹具专属目录失败: ${runDir.absolutePath}" }
        check(resultsDir.mkdirs()) { "创建结果目录失败: ${resultsDir.absolutePath}" }
        recordAsset("dir", runDir.absolutePath, "本轮夹具目录（登记不删除）")
        recordAsset("dir", resultsDir.absolutePath, "本轮阶段证据目录（登记不删除）")

        bodyText = buildBody()
        fixtureFile = File(runDir, "$bookId.txt")
        check(!fixtureFile.exists()) { "夹具文件初始必须缺席: ${fixtureFile.absolutePath}" }
        fixtureFile.writeText(bodyText)
        recordAsset("file", fixtureFile.absolutePath, "中性 TXT 夹具（导入源副本），sha=${J1RealReaderFixtures.sha256(bodyText)}")

        val nowIso = Instant.now().toString()
        val queryOffset = bodyText.indexOf(QUERY)
        val book = BookEntity(
            id = bookId,
            title = TITLE,
            author = AUTHOR,
            format = "txt",
            size = bodyText.toByteArray(Charsets.UTF_8).size,
            local_content_path = fixtureFile.absolutePath,
            content_status = "available",
            imported_at = nowIso,
            updated_at = nowIso,
        )
        runBlocking {
            db.bookDao().upsert(book)
            // 预置普通进度行：作为「临时阅读/重建不污染普通进度」的 12 列冻结对照基线。
            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    book_id = bookId,
                    progress_percent = queryOffset.toFloat() / bodyText.length * 100f,
                    last_read_at = nowIso,
                    total_reading_time_ms = 60000L,
                    completion_state = "reading",
                    current_location_json = LocatorBuilder.progressJson(
                        legacyOffset = queryOffset,
                        chapterIndex = 0,
                        charOffset = queryOffset,
                        globalOffset = queryOffset,
                    ),
                    updated_at = nowIso,
                ),
            )
            db.bookContentDao().upsert(
                BookContentEntity(book_id = bookId, reader_preview = bodyText.take(2000)),
            )
            // 索引只经生产单书索引入口（不直接 INSERT search_terms）。
            SearchIndexRepository(
                context = context,
                bookDao = db.bookDao(),
                bookContentDao = db.bookContentDao(),
                termDao = db.searchTermDao(),
                stateDao = db.searchIndexStateDao(),
                coverageDao = db.searchIndexCoverageDao(),
                tokenizer = SearchTokenizer(),
                rulesRepository = RulesRepository(db.readerTextRuleDao(), db.readerCorrectionDao()),
            ).indexSingleBook(book)
        }
        recordAsset("book", bookId, "本轮生产 DAO 创建的书行（登记不删除）")

        // 后置严格核验：本轮索引写入后，查询词 token 全库恰一行且归属本轮 book
        // （既有冲突已在本轮写入前拒绝；此处不放松为忽略未知行、不删除未知行）。
        val postRows = J1RealReaderFixtures.captureSearchTermRowsByTerm(db, token)
        assertTrue(
            "索引后查询词 token（$token）必须全库恰一行，实得 ${postRows.size} 行",
            postRows.size == 1,
        )
        assertEquals("唯一索引行必须归属本轮 book", bookId, postRows.single().optString("book_id"))
        val myRows = J1RealReaderFixtures.captureSearchTermRows(db, bookId)
            .filter { it.optString("term") == token }
        assertTrue("本轮书必须恰有一行查询词索引行，实得 ${myRows.size}", myRows.size == 1)

        baselineRow = requireNotNull(J1RealReaderFixtures.captureProgressRow(db, bookId)) {
            "预置进度行必须可捕获"
        }
        recordEvidence("t129_00_fixture") {
            put("bookId", bookId)
            put("bodySha256", J1RealReaderFixtures.sha256(bodyText))
            put("bodyBytes", bodyText.toByteArray(Charsets.UTF_8).size)
            put("queryToken", token)
            put("baselineRow", baselineRow.toJson().toString())
        }
    }

    @After
    fun tearDown() {
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                errors += t
            }
        }

        attempt { scenario?.close() }
        attempt {
            // 只读预检失败时尚未捕获基线，不得把「未捕获」当成「原本不存在」而删除首选项。
            if (onboardingStateCaptured) {
                J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            }
        }
        attempt {
            // runId 缺失/格式不符会在 setUp 最早失败，此时尚无结果目录可写。
            if (::resultsDir.isInitialized) {
                File(resultsDir, "t129_99_assets.json").writeText(
                    JSONObject()
                        .put("runId", runId)
                        .put("lastPhase", lastPhase)
                        .put("policy", "仪器内不删除本轮数据；由外层隔离实例+恢复合同处理")
                        .put("assets", createdAssets)
                        .toString(2)
                )
            }
        }
        attempt { db.close() }

        if (errors.isNotEmpty()) {
            val failure = AssertionError("tearDown 失败 ${errors.size} 项（全部原始异常保留在 suppressed）")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }

    // ── 旅程（单条测试，顺序执行） ───────────────────────────────────────────

    @Test
    fun f1_recreationDuringTemporaryReading_preservesSessionIdentityAndProgress() {
        try {
            // ── 阶段 1：启动 → Home 生产搜索入口真实点击 → 输入查询词 → 唯一正文命中卡 ──
            scenario = ActivityScenario.launch(MainActivity::class.java)
            composeRule.waitForIdle()
            // 按既有生产入口驱动：等 Home 顶部搜索按钮（CD="搜索"）可见后真实点击；
            // 不得直接导航到 search 或修改初始 route 绕过 UI。
            assertTrue(
                "Home 生产搜索入口必须可见（CD=$HOME_SEARCH_CD）",
                await("t129_10_home", UI_TIMEOUT_MS, "Home 搜索入口可见") { homeSearchNodeCount() > 0 },
            )
            composeRule.onNodeWithContentDescription(HOME_SEARCH_CD, substring = true).performClick()
            composeRule.waitForIdle()
            assertTrue(
                "点击 Home 搜索入口后搜索框必须出现",
                await("t129_10_open", UI_TIMEOUT_MS, "搜索输入框出现") {
                    composeRule.onAllNodesWithText(SEARCH_FIELD_PLACEHOLDER).fetchSemanticsNodes().isNotEmpty()
                },
            )
            val searchBox = composeRule.onNode(hasText(SEARCH_FIELD_PLACEHOLDER))
            searchBox.performTextClearance()
            searchBox.performTextInput(QUERY)
            composeRule.waitForIdle()

            // 生产命中规则正向推导期望坐标（生产类本体，同 119 链路）。
            val derived = J1RealReaderFixtures.deriveSearchCoordinate(db, bookId, QUERY, bodyText)
            // 索引 span start 与 UI 卡片「第 N 字附近」都是**章内**偏移，不能直接当全书 offset。
            val chapterOffset = requireNotNull(derived.expectedCharOffset) {
                "必须由生产命中规则推导出唯一命中偏移（legalOffsets=${derived.legalOffsets}）"
            }
            assertTrue(
                "查询词必须唯一合法命中（legalOffsets=${derived.legalOffsets}）",
                derived.legalOffsets.size == 1 && derived.legalOffsets[0] == chapterOffset,
            )
            // 导航目标用**全书** offset：与生产 SearchIndexRepository.resolveLegacyOffset 的
            // chapterStart + charOffset 同口径。章号取命中索引行 chapter_index（1 基），
            // 章首用生产 TxtChapterSplitter（与索引侧同一分章口径；见 119 旅程 955-1020）。
            val chapterIndex = requireNotNull(derived.traceChapterIndex) {
                "必须由命中索引行确定章号（traceChapterIndex=null）"
            }
            val chapterSlices = TxtChapterSplitter.split(bodyText)
            val chapterStart = requireNotNull(chapterSlices.getOrNull(chapterIndex - 1)) {
                "命中章 $chapterIndex 必须能由生产 TxtChapterSplitter 定位（slices=${chapterSlices.size}）"
            }.startOffset
            val globalOffset = chapterStart + chapterOffset
            assertEquals(
                "全书 offset 必须与正文查询词真实位置一致" +
                    "（chapterIndex=$chapterIndex, chapterStart=$chapterStart, chapterOffset=$chapterOffset）",
                bodyText.indexOf(QUERY),
                globalOffset,
            )
            val snippet = requireNotNull(derived.expectedLocationSnippet) {
                "必须推导出命中卡定位 snippet"
            }
            // 协调器目的地的精确生产期望：搜索正文命中经生产 resolveLegacyOffset 换算为
            // 全书 offset 后，searchContentHitNavigation 以
            // (bookId, 全书offset, null, null) 构造临时 route；目的地 == sourceTarget(route)。
            val expectedTarget = requireNotNull(
                SourceNavigationContract.target(
                    bookId,
                    ReaderLocator(
                        legacyOffset = globalOffset,
                        chapterIndex = null,
                        charOffset = null,
                        excerptFingerprint = null,
                    ),
                )
            )
            assertTrue("目的地必须落在本轮书", expectedTarget.bookId == bookId)

            val cardMatcher = contentCardMatcher(snippet)
            assertTrue(
                "正文命中卡必须唯一出现",
                await("t129_10_search", UI_TIMEOUT_MS, "唯一正文命中卡出现") {
                    composeRule.onAllNodes(cardMatcher).fetchSemanticsNodes().size == 1
                },
            )
            recordEvidence("t129_10_search_hit") {
                put("chapterIndex", chapterIndex)
                put("chapterOffset", chapterOffset)
                put("chapterStart", chapterStart)
                put("globalOffset", globalOffset)
                put("chapterOffsetBasis", "chapter-relative index span start (UI snippet uses this)")
                put("globalOffsetBasis", "TxtChapterSplitter chapterStart + chapterOffset")
                put("expectedSnippet", snippet)
                put("expectedTargetLocator", "legacyOffset=$globalOffset,chapter=null,char=null")
                put("cardCount", composeRule.onAllNodes(cardMatcher).fetchSemanticsNodes().size)
            }

            // ── 阶段 2：点击命中卡 → 临时阅读器真实落点 + 会话成立 ──
            composeRule.onNode(cardMatcher).performClick()
            assertTrue(
                "临时阅读器必须真实落到命中位点（marker1 可见）",
                await("t129_20_landing", UI_TIMEOUT_MS, "marker1 可见") { markerCount(MARKER_1) > 0 },
            )
            assertEquals(
                "落点必须互斥（marker2 距 marker1 ≥4000 字，不得同屏）",
                0, markerCount(MARKER_2),
            )
            observeCoordinator()
            val vmBefore = requireNotNull(coordinator) { "必须能观察到 Activity 作用域协调器" }
            // 经验绑定：观察到的实例就是 ReaderRoute 提交会话用的实例（状态必须已是本轮目的地）。
            assertEquals("临时会话必须已提交到本轮命中目的地", expectedTarget, vmBefore.state.value.active)
            assertTrue(
                "F1 前置：首次请求 rid=1 必须已成功提交（会话成立判据）",
                vmBefore.isRequestCommitted(FIRST_REQUEST_ID),
            )
            assertTrue(
                "搜索入口临时查阅必须空栈（120-v1 会话边界合同）",
                vmBefore.state.value.temporaryReturnStack.isEmpty(),
            )
            assertFalse("空栈时不得存在可返回目标", vmBefore.hasReturnableTarget)
            assertEquals(
                "空栈时「返回阅读处」按钮不得出现（119/128 设备同证）",
                0, returnToReadingCount(),
            )
            assertNull(
                "临时模式不得写普通阅读位置（normalReading 恒 null）",
                vmBefore.state.value.normalReading,
            )
            assertNull("提交后不得残留在途请求", vmBefore.pendingInspection.value)
            val progressAtTempLanding = J1RealReaderFixtures.captureProgressRow(db, bookId)
            baselineRow.assertExactEquals(
                progressAtTempLanding,
                "临时查阅期间基线 12 列冻结",
            )
            recordEvidence("t129_20_temp_landing") {
                put("marker1Count", markerCount(MARKER_1))
                put("marker2Count", markerCount(MARKER_2))
                put("returnToReadingCount", returnToReadingCount())
                put("activeEqualsExpected", vmBefore.state.value.active == expectedTarget)
                put("firstRequestCommitted", vmBefore.isRequestCommitted(FIRST_REQUEST_ID))
                put("stackSize", vmBefore.state.value.temporaryReturnStack.size)
                // 实际 12 列快照：宿主独立比对基线，不只信 PASS 布尔。
                put("progressRow", progressAtTempLanding?.toJson() ?: JSONObject.NULL)
            }

            // ── 阶段 3：Activity 配置重建（F1 目标场景） ──
            scenario!!.recreate()
            composeRule.waitForIdle()
            observeCoordinator()
            val vmAfter = requireNotNull(coordinator) { "重建后必须能重新观察到协调器" }
            assertSame(
                "协调器必须是同一 Activity 作用域实例（会话真源跨配置重建存活）",
                vmBefore, vmAfter,
            )
            assertTrue(
                "重建后阅读器必须重落命中位点（导航状态恢复 + 文档重载）",
                await("t129_30_recreated", UI_TIMEOUT_MS, "marker1 再可见") { markerCount(MARKER_1) > 0 },
            )
            assertEquals(0, markerCount(MARKER_2))
            // F1 核心：重建首帧 entry==null 不得触发 endReaderSession（会误作废已提交请求身份）。
            assertTrue(
                "F1：配置重建不得结束临时会话（首次请求 rid=1 的已提交身份必须跨重建存活）",
                await("t129_30_f1", UI_TIMEOUT_MS, "isRequestCommitted(1)==true") {
                    vmAfter.isRequestCommitted(FIRST_REQUEST_ID)
                },
            )
            assertEquals("F1：active 必须原样存活（不得被 null-entry 首帧清空）", expectedTarget, vmAfter.state.value.active)
            assertTrue("重建后临时栈仍为空（搜索入口合同不变）", vmAfter.state.value.temporaryReturnStack.isEmpty())
            assertNull("重建后 normalReading 仍必须为 null（不污染普通阅读位置）", vmAfter.state.value.normalReading)
            assertNull("重建后重提交必须完成（无卡死在途请求）", vmAfter.pendingInspection.value)
            assertEquals("重建后「返回阅读处」按钮仍不得出现", 0, returnToReadingCount())
            val progressAfterRecreate = J1RealReaderFixtures.captureProgressRow(db, bookId)
            baselineRow.assertExactEquals(
                progressAfterRecreate,
                "配置重建期间基线 12 列冻结",
            )
            recordEvidence("t129_30_recreated") {
                put("sameCoordinatorInstance", vmBefore === vmAfter)
                put("marker1Count", markerCount(MARKER_1))
                put("firstRequestCommittedAfterRecreate", vmAfter.isRequestCommitted(FIRST_REQUEST_ID))
                put("activeEqualsExpected", vmAfter.state.value.active == expectedTarget)
                put("stackSize", vmAfter.state.value.temporaryReturnStack.size)
                put("progressRow", progressAfterRecreate?.toJson() ?: JSONObject.NULL)
            }

            // ── 阶段 4：真实 Back → 搜索页 + 会话由真实 route 变更合法结束 ──
            if (readerChromeBackCount() > 0) {
                pressSystemBack()
                Thread.sleep(500)
            }
            pressSystemBack()
            assertTrue(
                "Back 后必须落全局搜索页（query 保留）",
                await("t129_40_back", UI_TIMEOUT_MS, "搜索框恢复本轮 query") { searchFieldText() == QUERY },
            )
            assertTrue(
                "返回后正文命中卡必须仍在",
                await("t129_40_back", UI_TIMEOUT_MS, "命中卡仍在") {
                    composeRule.onAllNodes(cardMatcher).fetchSemanticsNodes().size == 1
                },
            )
            assertTrue(
                "真实 route 变更（reader→search）必须合法结束会话（F1 修复不得破坏真实结束路径）",
                await("t129_40_session_end", UI_TIMEOUT_MS, "active==null") {
                    requireNotNull(coordinator).state.value.active == null
                },
            )
            val progressAfterBack = J1RealReaderFixtures.captureProgressRow(db, bookId)
            baselineRow.assertExactEquals(
                progressAfterBack,
                "临时查阅全程结束后基线 12 列冻结",
            )
            recordEvidence("t129_40_back_search") {
                put("searchFieldEditableText", searchFieldText())
                put("sessionEndedActiveNull", coordinator?.state?.value?.active == null)
                put("returnToReadingCount", returnToReadingCount())
                put("progressRow", progressAfterBack?.toJson() ?: JSONObject.NULL)
            }
            recordEvidence("journey_success") {
                put("status", "PASS")
                put("f1_discriminator", "isRequestCommitted(1) 跨 ActivityScenario.recreate 存活")
                put("not_covered", JSONArray().apply {
                    put("UI 级返回按钮跨重建存活（生产当前无 reader 域内临时入口，空栈场景按钮恒不出现）")
                    put("非空临时返回栈场景（需产品新增 reader 域内入口后另批覆盖）")
                })
            }
        } catch (t: Throwable) {
            // 首错先取证后原样重抛；取证失败 addSuppressed（不吞、不覆盖）。
            // 先取失败前所在阶段：recordEvidence 会先把 lastPhase 改成失败阶段名。
            val failedAtPhase = lastPhase
            try {
                recordEvidence("t129_first_failure") {
                    put("type", t.javaClass.name)
                    put("message", t.message ?: "")
                    put("stackTrace", t.stackTraceToString().take(8000))
                    put("lastPhase", failedAtPhase)
                }
            } catch (rec: Throwable) {
                t.addSuppressed(rec)
            }
            throw t
        }
    }
}
