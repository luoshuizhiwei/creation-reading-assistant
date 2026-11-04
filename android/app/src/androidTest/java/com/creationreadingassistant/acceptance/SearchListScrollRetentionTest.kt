package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.search.SearchTextBasis
import com.creationreadingassistant.feature.search.SearchTokenizer
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import com.creationreadingassistant.ui.screen.search.SearchListSnapshot
import com.creationreadingassistant.ui.screen.search.SearchListSnapshotKey
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 141-B-v1 草案：全局搜索列表滚动位置在「正文命中 → 临时阅读 → Back」后的保留验收。
 *
 * 唯一 @Test。真实生产 UI 入口：Home 搜索 → 输入查询 → 正文 Tab → 滚动到非零
 * index/offset → 点击目标正文卡 → 临时阅读器 → 真实 Back → 读取同一列表快照。
 *
 * 主断言是 [SearchListSnapshot] 的 index/offset/tab/query/topItemKey/itemCount 前后精确相等，
 * 不是 isDisplayed/截图/固定 sleep。列表状态由 SearchViewModel（search NavBackStackEntry）
 * 持有；本测试不直接写 NavController/协调器，只经 ViewModel 公共 API 观察会话。
 *
 * 依赖 141-B 生产观察接缝（SearchScreen.kt 的 testTag + SearchListSnapshotKey）。该接缝只读，
 * 不改变列表行为/排序/导航。旧产品 APK 74d106b6 不含此接缝；未来必须构建/冻结新产品 APK，
 * 单 assembleDebugAndroidTest 不能把接缝送到设备。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchListScrollRetentionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var fixtureRunDir: File
    private lateinit var resultsDir: File
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var onboardingCaptured = false
    private val createdAssets = JSONArray()
    private var lastPhase = "start"
    private lateinit var bookIds: List<String>
    private lateinit var targetBookId: String
    private lateinit var baselineRows: Map<String, J1RealReaderFixtures.ProgressRowSnapshot>
    private var coordinator: TemporaryReadingNavigationViewModel? = null

    private data class VisibleTarget(
        val bookId: String,
        val ordinal: Int,
        val title: String,
        val marker: String,
        val centerX: Float,
        val centerY: Float,
    )

    companion object {
        const val TAG = "t141b"

        /** 宿主必传：T141BV1-YYYYMMDD-HHMMSS；无随机 fallback。 */
        const val RUN_ID_ARG = "craRunId"
        val RUN_ID_REGEX = Regex("""^T141BV1-\d{8}-\d{6}$""")

        const val QUERY = "CRAQLIST141"
        const val TITLE_PREFIX = "测试 TXT 滚动"
        const val MARKER_PREFIX = "【CRA141B"
        const val MARKER_SUFFIX = "】"
        const val BOOK_COUNT = 25

        const val SEARCH_FIELD_PLACEHOLDER = "搜索书籍、灵感、笔记、高亮"
        const val HOME_SEARCH_CD = "搜索"
        const val READER_CHROME_BACK_CD = "返回"
        const val CONTENT_TAB = "正文"
        const val SEARCH_LIST_TAG = "search-results-list"

        const val FIRST_REQUEST_ID = 1L
        const val UI_TIMEOUT_MS = 20_000L
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────

    private fun buildBody(ordinal: Int): String {
        val sb = StringBuilder()
        sb.append("《测试 TXT 滚动%02d》\n\n".format(Locale.US, ordinal))
        sb.append("第一章 起始\n")
        while (sb.length < 400) {
            sb.append("填充文本用于滚动列表第${ordinal}本。")
        }
        // 先写固定前缀，再记录 QUERY 的真实起点，避免把前缀长度算成 token 偏移。
        sb.append("唯一检索词 ")
        val queryOffset = sb.length
        sb.append(QUERY)
        sb.append(" 出现于第%02d本。".format(Locale.US, ordinal))
        val marker = MARKER_PREFIX + "%02d".format(Locale.US, ordinal) + MARKER_SUFFIX
        val markerOffset = sb.length
        sb.append(marker)
        sb.append("\n后续结尾内容。\n")
        check(sb.indexOf(QUERY) == queryOffset) {
            "查询词必须唯一且位于第 $ordinal 本正文真实 token 起点（queryOffset=$queryOffset）"
        }
        check(sb.indexOf(QUERY, queryOffset + QUERY.length) == -1) {
            "查询词在第 $ordinal 本必须唯一"
        }
        check(sb.indexOf(marker) == markerOffset) { "第 $ordinal 本 marker 必须唯一" }
        return sb.toString()
    }

    // ── UI 观测 ──────────────────────────────────────────────────────────

    private fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription(HOME_SEARCH_CD, substring = true)
            .fetchSemanticsNodes().size

    private fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription(READER_CHROME_BACK_CD, substring = true)
            .fetchSemanticsNodes().size

    private fun readerMarkerVisible(marker: String): Boolean =
        composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()

    private enum class BackState { SEARCH, READER_CHROME_HIDDEN, UNKNOWN }

    /**
     * 首 Back 后只接受两种可观察状态：已回搜索页，或仍在 reader 且 chrome 已收起。
     * 其它状态返回 UNKNOWN，由调用方失败，绝不盲发第二次 Back。
     */
    private fun awaitBackState(marker: String): BackState {
        return try {
            composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
                searchFieldText() == QUERY ||
                    (readerMarkerVisible(marker) && readerChromeBackCount() == 0)
            }
            when {
                searchFieldText() == QUERY -> BackState.SEARCH
                readerMarkerVisible(marker) && readerChromeBackCount() == 0 ->
                    BackState.READER_CHROME_HIDDEN
                else -> BackState.UNKNOWN
            }
        } catch (t: ComposeTimeoutException) {
            BackState.UNKNOWN
        }
    }

    private fun searchFieldText(): String? =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun tabMatcher(label: String, count: Int): SemanticsMatcher =
        hasClickAction() and hasText(label) and hasText(count.toString())

    private fun listSnapshot(): SearchListSnapshot =
        composeRule.onNodeWithTag(SEARCH_LIST_TAG).fetchSemanticsNode()
            .config.getOrNull(SearchListSnapshotKey)
            ?: error("搜索结果列表缺少 SearchListSnapshot 语义")

    private fun await(
        phase: String,
        timeoutMs: Long,
        description: String,
        block: () -> Boolean,
    ): Boolean = try {
        composeRule.waitUntil(timeoutMillis = timeoutMs) { block() }
        true
    } catch (t: ComposeTimeoutException) {
        android.util.Log.w(TAG, "$phase 等待超时（$timeoutMs ms）：$description", t)
        false
    }

    private fun awaitSnapshot(
        phase: String,
        description: String,
        predicate: (SearchListSnapshot) -> Boolean,
    ): SearchListSnapshot {
        try {
            composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) { predicate(listSnapshot()) }
        } catch (t: ComposeTimeoutException) {
            // 超时后再直接读一次：key 缺失/解析失败必须抛真实原因，不能伪装成超时。
            val last = listSnapshot()
            throw AssertionError("$phase 等待列表快照超时：$description, last=$last", t)
        }
        return listSnapshot()
    }

    private fun pressSystemBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun observeCoordinator() {
        scenario!!.onActivity { activity ->
            coordinator = ViewModelProvider(activity)[TemporaryReadingNavigationViewModel::class.java]
        }
    }

    // ── 证据 ─────────────────────────────────────────────────────────────

    private fun recordEvidence(phase: String, payload: JSONObject.() -> Unit) {
        lastPhase = phase
        val obj = JSONObject()
            .put("phase", phase)
            .put("at", Instant.now().toString())
            .put("runId", runId)
            .put("packageName", context.packageName)
        if (::targetBookId.isInitialized) obj.put("targetBookId", targetBookId)
        payload(obj)
        File(resultsDir, "$phase.json").writeText(obj.toString(2))
        android.util.Log.i(TAG, "$phase ${obj.toString(0)}")
    }

    private fun recordAsset(kind: String, path: String, reason: String) {
        createdAssets.put(JSONObject().put("kind", kind).put("path", path).put("retainReason", reason))
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // 1. runId 只接受宿主传入；缺失/格式不符在任何 DB/onboarding/fixture 写入之前失败。
        val rawRunId = requireNotNull(
            InstrumentationRegistry.getArguments().getString(RUN_ID_ARG),
        ) { "宿主必须以 -e $RUN_ID_ARG 传入 runId（T141BV1-YYYYMMDD-HHMMSS）" }
        require(RUN_ID_REGEX.matches(rawRunId)) {
            "runId 格式不符（实际=$rawRunId，期望 T141BV1-\\d{8}-\\d{6}）"
        }
        runId = rawRunId
        bookIds = (1..BOOK_COUNT).map { "cra141b-txt-%02d-%s".format(Locale.US, it, runId) }

        db = J1RealReaderFixtures.openDatabase(context)
        val token = QUERY.lowercase()
        fixtureRunDir = File(context.filesDir, "cra141b_isolated_fixtures/$runId")
        resultsDir = File(context.getExternalFilesDir(null), "t141b-results-$runId")

        // 2. 全库空 term + book/目录冲突预检：全部只读，先于 onboarding/夹具/DAO 写入。
        val preRows = J1RealReaderFixtures.captureSearchTermRowsByTerm(db, token)
        val existingBooks = bookIds.mapNotNull { id -> runBlocking { db.bookDao().getById(id) } }
        if (preRows.isNotEmpty() || existingBooks.isNotEmpty() || fixtureRunDir.exists() || resultsDir.exists()) {
            error(
                "检测到冲突或残留（indexRows=${preRows.size}, books=${existingBooks.size}, " +
                    "runDir=${fixtureRunDir.exists()}, resultsDir=${resultsDir.exists()}），拒绝启动。",
            )
        }

        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        onboardingCaptured = true
        check(fixtureRunDir.mkdirs()) { "创建夹具目录失败: ${fixtureRunDir.absolutePath}" }
        check(resultsDir.mkdirs()) { "创建结果目录失败: ${resultsDir.absolutePath}" }
        recordAsset("dir", fixtureRunDir.absolutePath, "本轮 25 本书夹具目录（登记不删除）")
        recordAsset("dir", resultsDir.absolutePath, "本轮阶段证据目录（登记不删除）")

        val nowIso = Instant.now().toString()
        runBlocking {
            val indexer = SearchIndexRepository(
                context = context,
                bookDao = db.bookDao(),
                bookContentDao = db.bookContentDao(),
                termDao = db.searchTermDao(),
                stateDao = db.searchIndexStateDao(),
                coverageDao = db.searchIndexCoverageDao(),
                tokenizer = SearchTokenizer(),
                rulesRepository = RulesRepository(db.readerTextRuleDao(), db.readerCorrectionDao()),
            )
            bookIds.forEachIndexed { idx, bookId ->
                val ordinal = idx + 1
                val body = buildBody(ordinal)
                val file = File(
                    fixtureRunDir,
                    "测试 TXT 滚动%02d.txt".format(Locale.US, ordinal),
                )
                check(!file.exists()) { "夹具文件初始必须缺席: ${file.absolutePath}" }
                file.writeText(body)
                recordAsset("file", file.absolutePath, "第 $ordinal 本中性 TXT 夹具，sha=${J1RealReaderFixtures.sha256(body)}")

                val queryOffset = body.indexOf(QUERY)
                check(queryOffset >= 0) { "第 $ordinal 本必须含查询词" }
                val book = BookEntity(
                    id = bookId,
                    title = "测试 TXT 滚动%02d".format(Locale.US, ordinal),
                    author = "测试作者%02d".format(Locale.US, ordinal),
                    format = "txt",
                    size = body.toByteArray(Charsets.UTF_8).size,
                    local_content_path = file.absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
                db.bookDao().upsert(book)
                db.readingProgressDao().upsert(
                    ReadingProgressEntity(
                        book_id = bookId,
                        progress_percent = queryOffset.toFloat() / body.length * 100f,
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
                    BookContentEntity(book_id = bookId, reader_preview = body.take(2000)),
                )
                indexer.indexSingleBook(book)
                recordAsset("book", bookId, "第 $ordinal 本生产 DAO 书行（登记不删除）")
            }
        }

        // 3. 索引后严格核验：25 本书各恰一行且归属本轮；不忽略未知行。
        val postRows = J1RealReaderFixtures.captureSearchTermRowsByTerm(db, token)
        assertEquals("25 本书必须各有一行查询词索引行", BOOK_COUNT, postRows.size)
        assertEquals(
            "索引行 book 归属必须是本轮 25 本",
            bookIds.toSet(),
            postRows.map { it.optString("book_id") }.toSet(),
        )
        bookIds.forEach { bookId ->
            val rows = J1RealReaderFixtures.captureSearchTermRows(db, bookId)
                .filter { it.optString("term") == token }
            assertEquals("$bookId 必须恰有一行查询词索引行", 1, rows.size)
        }
        baselineRows = bookIds.associateWith { bookId ->
            requireNotNull(J1RealReaderFixtures.captureProgressRow(db, bookId)) {
                "$bookId 预置进度行必须可捕获"
            }
        }
        val baselineProgressJson = JSONObject()
        bookIds.forEach { bookId ->
            baselineProgressJson.put(bookId, baselineRows.getValue(bookId).toJson())
        }

        recordEvidence("t141b_00_fixture") {
            put("bookCount", BOOK_COUNT)
            put("bookIds", JSONArray(bookIds))
            put("query", QUERY)
            put("queryToken", token)
            put(
                "markers",
                JSONArray(
                    bookIds.indices.map {
                        MARKER_PREFIX + "%02d".format(Locale.US, it + 1) + MARKER_SUFFIX
                    },
                ),
            )
            put("progressRows", baselineProgressJson)
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
            if (onboardingCaptured) {
                J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            }
        }
        attempt {
            if (::resultsDir.isInitialized) {
                File(resultsDir, "t141b_99_assets.json").writeText(
                    JSONObject()
                        .put("runId", runId)
                        .put("lastPhase", lastPhase)
                        .put("policy", "仪器内不删除本轮数据；由外层隔离实例+恢复合同处理")
                        .put("assets", createdAssets)
                        .toString(2),
                )
            }
        }
        attempt { if (::db.isInitialized) db.close() }

        if (errors.isNotEmpty()) {
            val failure = AssertionError("tearDown 失败 ${errors.size} 项（原始异常保留在 suppressed）")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }

    // ── 旅程 ─────────────────────────────────────────────────────────────

    @Test
    fun searchListScroll_retainsIndexOffsetTabQueryAfterTemporaryReaderBack() {
        try {
            // 1. 真实 Home 搜索入口 → 查询 → 正文 Tab。
            scenario = ActivityScenario.launch(MainActivity::class.java)
            composeRule.waitForIdle()
            assertTrue(
                "Home 搜索入口必须可见",
                await("t141b_10_home", UI_TIMEOUT_MS, "Home 搜索入口") { homeSearchNodeCount() > 0 },
            )
            composeRule.onNodeWithContentDescription(HOME_SEARCH_CD, substring = true).performClick()
            composeRule.waitForIdle()
            assertTrue(
                "搜索框必须出现",
                await("t141b_10_open", UI_TIMEOUT_MS, "搜索输入框") {
                    composeRule.onAllNodesWithText(SEARCH_FIELD_PLACEHOLDER).fetchSemanticsNodes().isNotEmpty()
                },
            )
            val searchBox = composeRule.onNode(hasText(SEARCH_FIELD_PLACEHOLDER))
            searchBox.performTextClearance()
            searchBox.performTextInput(QUERY)
            composeRule.waitForIdle()

            val contentTab = tabMatcher(CONTENT_TAB, BOOK_COUNT)
            assertTrue(
                "正文 Tab 必须显示 $BOOK_COUNT 条",
                await("t141b_10_tab", UI_TIMEOUT_MS, "正文 Tab 计数 $BOOK_COUNT") {
                    composeRule.onAllNodes(contentTab).fetchSemanticsNodes().size == 1
                },
            )
            composeRule.onNode(contentTab).performClick()
            composeRule.waitForIdle()
            recordEvidence("t141b_10_search") {
                put("query", QUERY)
                put("tab", "content")
                put("contentHitCount", BOOK_COUNT)
            }

            // 2. 滚到非零 index/offset；不假定排序后某个 index 是某本书。
            val list = composeRule.onNodeWithTag(SEARCH_LIST_TAG)
            list.performScrollToIndex(1)
            composeRule.waitForIdle()
            var pre = listSnapshot()
            if (pre.firstVisibleItemScrollOffset == 0) {
                list.performTouchInput {
                    swipeUp(
                        startY = centerY + 100f,
                        endY = centerY - 100f,
                        durationMillis = 300L,
                    )
                }
                composeRule.waitForIdle()
                pre = listSnapshot()
            }
            assertTrue(
                "必须真实滚到非零 index（实际=${pre.firstVisibleItemIndex}）",
                pre.firstVisibleItemIndex > 0,
            )
            assertTrue(
                "必须真实滚到非零 offset（实际=${pre.firstVisibleItemScrollOffset}）",
                pre.firstVisibleItemScrollOffset > 0,
            )
            assertEquals("Tab 必须是正文", "content", pre.tab)
            assertEquals("query 必须是本轮查询词", QUERY, pre.query)

            // 保持 pre.topItemKey 精确比较；目标卡从当下真实可见且点击中心在列表
            // 可视 bounds 内的本轮卡选择，不假定 sort/index 对应书，也不在记录 pre 后滚动。
            val listBounds = composeRule.onNodeWithTag(SEARCH_LIST_TAG)
                .fetchSemanticsNode().boundsInRoot
            val visibleTargets = bookIds.mapNotNull { id ->
                val ordinal = id.removePrefix("cra141b-txt-").substringBefore("-").toInt()
                val title = TITLE_PREFIX + "%02d".format(Locale.US, ordinal)
                val nodes = composeRule.onAllNodes(hasText(title) and hasClickAction())
                    .fetchSemanticsNodes()
                if (nodes.size != 1) return@mapNotNull null
                val bounds = nodes.single().boundsInRoot
                val centerX = (bounds.left + bounds.right) / 2f
                val centerY = (bounds.top + bounds.bottom) / 2f
                if (centerX < listBounds.left || centerX > listBounds.right ||
                    centerY < listBounds.top || centerY > listBounds.bottom) {
                    return@mapNotNull null
                }
                VisibleTarget(
                    bookId = id,
                    ordinal = ordinal,
                    title = title,
                    marker = MARKER_PREFIX + "%02d".format(Locale.US, ordinal) + MARKER_SUFFIX,
                    centerX = centerX,
                    centerY = centerY,
                )
            }
            val target = visibleTargets.firstOrNull()
                ?: error(
                    "没有点击中心位于列表可视 bounds 内的本轮目标卡" +
                        "（list=$listBounds）",
                )
            targetBookId = target.bookId
            val selectedTitle = target.title
            val selectedMarker = target.marker
            val targetCardKey = "content-${target.bookId}-${SearchTextBasis.ORIGINAL.wire}"
            val targetCard = hasText(selectedTitle) and hasClickAction()
            assertEquals(
                "目标卡必须唯一可见/可点击",
                1,
                composeRule.onAllNodes(targetCard).fetchSemanticsNodes().size,
            )
            recordEvidence("t141b_20_scrolled") {
                put("firstVisibleItemIndex", pre.firstVisibleItemIndex)
                put("firstVisibleItemScrollOffset", pre.firstVisibleItemScrollOffset)
                put("tab", pre.tab)
                put("query", pre.query)
                put("topItemKey", pre.topItemKey)
                put("itemCount", pre.itemCount)
                put("targetCardKey", targetCardKey)
                put("targetBookId", target.bookId)
                put("targetTitle", selectedTitle)
                put("targetMarker", selectedMarker)
                put("targetCenterX", target.centerX)
                put("targetCenterY", target.centerY)
                put("listBoundsLeft", listBounds.left)
                put("listBoundsTop", listBounds.top)
                put("listBoundsRight", listBounds.right)
                put("listBoundsBottom", listBounds.bottom)
                put("targetVisible", true)
                put("contentHitCount", BOOK_COUNT)
            }

            // 3. 真实点击首可见目标卡 → 临时阅读器。
            composeRule.onNode(targetCard).performClick()
            assertTrue(
                "临时阅读器必须落到目标 marker",
                await("t141b_30_reader", UI_TIMEOUT_MS, "目标 marker 可见") {
                    composeRule.onAllNodesWithText(selectedMarker, substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                },
            )
            observeCoordinator()
            val vm = requireNotNull(coordinator) { "必须能观察到 Activity 作用域协调器" }
            assertEquals("临时会话 active 必须是目标书", target.bookId, vm.state.value.active?.bookId)
            assertTrue(
                "首次请求 rid=1 必须已提交（会话成立判据）",
                vm.isRequestCommitted(FIRST_REQUEST_ID),
            )
            recordEvidence("t141b_30_reader") {
                put("targetBookId", target.bookId)
                put("targetTitle", selectedTitle)
                put("targetMarker", selectedMarker)
                put("targetMarkerVisible", true)
                put("activeBookId", vm.state.value.active?.bookId)
                put("firstRequestCommitted", vm.isRequestCommitted(FIRST_REQUEST_ID))
            }

            // 4. 真实 Back → 搜索页；首 Back 后先判明状态，不盲发第二次。
            pressSystemBack()
            when (awaitBackState(selectedMarker)) {
                BackState.SEARCH -> Unit
                BackState.READER_CHROME_HIDDEN -> {
                    pressSystemBack()
                    assertTrue(
                        "第二次 Back 后必须回搜索页并恢复 query",
                        await("t141b_40_back", UI_TIMEOUT_MS, "搜索框恢复 query") {
                            searchFieldText() == QUERY
                        },
                    )
                }
                BackState.UNKNOWN -> error(
                    "Back 后状态不明（searchField=${searchFieldText()}, " +
                        "readerMarker=${readerMarkerVisible(selectedMarker)}, " +
                        "chromeBack=${readerChromeBackCount()}）；不盲发第二次 Back",
                )
            }
            val post = awaitSnapshot("t141b_40_snapshot", "搜索列表快照") {
                it.query == QUERY && it.tab == "content"
            }
            assertEquals(
                "firstVisibleItemIndex 必须跨临时阅读返回精确保留",
                pre.firstVisibleItemIndex,
                post.firstVisibleItemIndex,
            )
            assertEquals(
                "firstVisibleItemScrollOffset 必须跨临时阅读返回精确保留",
                pre.firstVisibleItemScrollOffset,
                post.firstVisibleItemScrollOffset,
            )
            assertEquals("Tab 必须跨返回保留", "content", post.tab)
            assertEquals("query 必须跨返回保留", QUERY, post.query)
            assertEquals("首可见 item key 必须跨返回保留", pre.topItemKey, post.topItemKey)
            assertEquals("itemCount 必须跨返回保留", pre.itemCount, post.itemCount)
            assertEquals(
                "目标卡必须仍在",
                1,
                composeRule.onAllNodes(targetCard).fetchSemanticsNodes().size,
            )
            assertTrue(
                "真实 Back 必须合法结束临时会话",
                await("t141b_40_session_end", UI_TIMEOUT_MS, "active==null") {
                    coordinator?.state?.value?.active == null
                },
            )

            // 5. 12 列普通进度不得被临时阅读污染；落盘 25 本真实值，不只布尔。
            val afterProgressJson = JSONObject()
            bookIds.forEach { bookId ->
                val afterRow = requireNotNull(J1RealReaderFixtures.captureProgressRow(db, bookId)) {
                    "$bookId 返回后进度行必须存在"
                }
                baselineRows.getValue(bookId).assertExactEquals(
                    afterRow,
                    "临时阅读返回后 $bookId 12 列冻结",
                )
                afterProgressJson.put(bookId, afterRow.toJson())
            }
            recordEvidence("t141b_40_back") {
                put("firstVisibleItemIndex", post.firstVisibleItemIndex)
                put("firstVisibleItemScrollOffset", post.firstVisibleItemScrollOffset)
                put("tab", post.tab)
                put("query", post.query)
                put("topItemKey", post.topItemKey)
                put("itemCount", post.itemCount)
                put("targetCardKey", targetCardKey)
                put("targetCardPresent", true)
                put("sessionEndedActiveNull", coordinator?.state?.value?.active == null)
                put("progressRows", afterProgressJson)
                put("progressRowsUnchanged", true)
            }
            recordEvidence("t141b_50_success") {
                put("status", "PASS")
                put("not_covered", JSONArray()
                    .put("process death / search route pop（VM 不再存活时 listState 不保证）")
                    .put("per-tab independent scroll state（当前所有 Tab 共用同一 listState）"))
            }
        } catch (t: Throwable) {
            val failedAtPhase = lastPhase
            try {
                recordEvidence("t141b_first_failure") {
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
