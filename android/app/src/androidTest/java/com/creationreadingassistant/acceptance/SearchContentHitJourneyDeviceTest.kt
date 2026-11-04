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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
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
import com.creationreadingassistant.feature.search.SearchOffsets
import com.creationreadingassistant.feature.search.SearchTokenizer
import com.creationreadingassistant.feature.search.TxtChapterSplitter
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
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * 119-v1 修订1：全局搜索回源临时查阅仪器（网吧开发，原电脑验证；同 owner 修订，不开新任务）。
 *
 * ## 旅程（单一 Activity 会话；全部真实 UI 动作：performClick / 输入 / 手势 / 系统返回）
 * Home 继续阅读进入甲书 → 真实翻页推进到不同于命中点的合法持久位置 → 真实 Back 回 Home →
 * 捕获冻结基线（完整 12 列 reading_progress 行）→ Home 真实点击全局 search 入口 →
 * 输入查询词 → 生产规则推导期望坐标并断言全库唯一归属 → 真实点击「正文」Tab（点前/点后/
 * 返回后三个时点采集列表身份非退化对照）→ 真实点击唯一甲书正文 hit → TEMPORARY 阅读器落到
 * 正文目标 marker → 真实 Back → 观测实际落点：
 *  - 落点为全局搜索页：query 保留（搜索框 EditableText）+ 正文 Tab 保留（乙书元数据书籍卡
 *    在「全部」页出现而在「正文」页消失的可观测对照）+ 甲书 hit 仍在且唯一 + 甲书 12 列
 *    持久行与基线逐列相同 → PASS；
 *  - 落点为普通阅读器：先保存实际目的地、可见屏幕代理标记（route/协调器不可直接取得，沿
 *    J1 routeLifecycleSnapshot 口径如实登记）、完整树与持久行，再让断言**如实失败** ——
 *    G1 为「待设备证明的疑似接线缺口」，本仪器不重启 Activity、不改生产、不弱化期望。
 *
 * ## 关于返回落点（G1，源码推导 ≠ 设备证明）
 * 源码推导（ReaderScreen BackHandler 优先级链 + ReaderRoute.performTemporaryReturn +
 * SourceNavigationContract.beginTemporaryInspection）：本进程已存在普通阅读记录时，临时查阅
 * 提交会把 active/normalReading 压入临时返回栈，阅读器内真实 Back 会 navigate 回普通阅读器
 * 而非 popBackStack 回搜索页。重组合/重注册是否真实发生**没有生命周期证据**，故本仪器不把
 * 推导写成「必然导航环」，落点交由设备实测：搜索页则 PASS；普通阅读器则按上述取证后失败。
 * 修订1 已删除旧版「关闭并重启 Activity 清空协调器」的双会话设计（属 TASK119 禁止的
 * Activity 重启绕过）。
 *
 * ## fixture 层级与索引入口
 * 生产 DAO 建书（books / book_content / reading_progress 初始行）+ 真实 TXT 落盘；索引**只**经
 * 生产单书索引入口 `SearchIndexRepository.indexSingleBook(book)`（生产 DAO + 生产
 * RulesRepository + 生产 SearchTokenizer 手工装配，不注入、不平行实现、不直接 INSERT
 * search_terms）。索引结束观测真实产物，并按生产 Tokenizer 实际规则核验查询词 token 的
 * 全库唯一归属（term 行全库仅甲书一行；未知命中即停，不删未知记录）。
 *
 * ## 非退化 Tab 对照（乙书 = LIKE 命中 / 分词排除）
 * 乙书标题含查询词的子串变体 `CRAQX119LMX`：BookDao.search 的 LIKE '%q%' 子串命中 →
 * 「全部」页渲染乙书「书籍」卡；而生产 SearchTokenizer 把 `craqx119lmx` 切成独立 latin
 * token（≠ `craqx119lm`），索引与正文通道均无该查询词行 → 「正文」页乙书卡为 0。
 * 三个时点（点 Tab 前 = all 页、点 Tab 后、返回后）采集乙书书籍卡计数即可区分「仍在正文
 * Tab」与「退回全部 Tab」。甲书标题/作者不含查询词（纯中文），甲书只有正文 hit。
 * 滚动位置（LazyList offset）在生产语义中无 testTag/选中语义可观察，按 Root 意见声明
 * NOT_COVERED，不总称 query/tab/list 保留。
 *
 * ## craRunId（宿主传入，无本机生成）
 * `InstrumentationRegistry.getArguments().getString("craRunId")` 必须提供且严格匹配
 * `T119V1-\d{8}-\d{6}`；夹具 bookId、TXT 路径、结果目录、证据文件、报告全部绑定同值；
 * run 目录与结果目录初始必须缺席（冲突即停，不删除）。
 *
 * ## 取证与异常纪律
 * 成功路径的关键证据（步骤标记 / UI 状态 / 完整语义树 / 截图）取得失败即测试失败（不
 * 「日志一条继续 PASS」）；完整树沿 117 现有实现：`onAllNodes(isRoot())` 逐 root 枚举 +
 * `printToString(maxDepth = 60)`（不假设 onRoot 唯一、不静默截断）。失败路径：首个业务
 * 异常先取证后原样重抛，取证/清理/恢复错误 `addSuppressed` 到首错（不覆盖、不吞）。
 * 清理只处理**登记过原始字节 size/SHA 的两个具体 fixture TXT**（逐文件核当前内容一致、
 * 路径规范化在本 run 根内、删后复核缺席；出现未知文件/内容漂移/读错即 BLOCKED 保留现场），
 * 禁止 deleteRecursively；数据库只按账本登记 ID 精确 DELETE。绝不显示或上传私钥。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchContentHitJourneyDeviceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var bookIdA: String
    private lateinit var bookIdB: String
    private lateinit var contentA: FixtureContent
    private lateinit var contentB: FixtureContent
    private lateinit var ledger: RunLedger

    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /** 最近一次已进入的步骤标记（首个异常取证用）。 */
    private var lastStep: String = "start"

    /** 首个测试主体异常（tearDown 的清理/恢复错误 addSuppressed 到它，不覆盖首错）。 */
    private var firstFailure: Throwable? = null

    /**
     * catch 内取证是否失败（true = 证据链未闭合）：tearDown 据此 BLOCKED 依赖清理，
     * 保留 fixture 文件与 DB 行（取证/归属未闭合前不得先删业务证据）。
     */
    private var evidenceCaptureFailed: Boolean = false

    // ────────────────────────────────────────────────────────────────────────
    // 夹具常量
    //
    // 查询词 10 字符（tokenizeForQuery 对 length ≤ 6 才补单字 unigram，故查询侧只有一个
    // token）；**不把自造长词当数学保证** —— 唯一性由全库 term 行归属断言实测保证。
    // 乙书标题含子串变体 CRAQX119LMX：LIKE 子串命中（all 页对照）、分词不产生查询词
    // token（正文页排除）。
    // ────────────────────────────────────────────────────────────────────────

    companion object {
        const val TASK_ID = "CRA-119-V1-SEARCH-CONTENT-HIT-TEMPORARY-LOOKUP"
        const val TAG = "t119"

        /** 宿主必须以 -e craRunId 传入；严格格式见 [RUN_ID_REGEX]。 */
        const val RUN_ID_ARG = "craRunId"
        val RUN_ID_REGEX = Regex("""^T119V1-\d{8}-\d{6}$""")

        const val TITLE_A = "测试 TXT 甲壹壹玖"
        const val TITLE_B = "测试 TXT 贰 CRAQX119LMX"
        const val QUERY_A = "CRAQX119LM"

        const val MARKER_A1 = "【CRA119LMA1】"
        const val MARKER_A2 = "【CRA119LMA2】"
        const val MARKER_B = "【CRA119LMB1】"
        const val SNIPPET_A1 = "唯一检索词 $QUERY_A 出现于此段。"
        const val RESULTS_DIR_PREFIX = "task119-results-"
        const val FIXTURES_PARENT_DIR = "cra119_isolated_fixtures"

        const val TAB_CONTENT_LABEL = "正文"
        const val BOOK_CARD_LABEL = "书籍"
        const val SEARCH_FIELD_PLACEHOLDER = "搜索书籍、灵感、笔记、高亮"
        const val HOME_SEARCH_CD = "搜索"
        const val READER_CHROME_BACK_CD = "返回"
        const val RETURN_TO_READING_CD = "返回阅读处"
        const val ORIGIN_LABEL = "原文"
    }

    /** 一次夹具书（正文 + 唯一性前置核验）的持有结构。 */
    class FixtureContent(
        val bodyText: String,
        val marker: String,
        val markerFar: String?,
    )

    /** 登记的 fixture TXT：创建后立即记录原始字节 size/SHA，清理前逐文件核验一致才删。 */
    class TrackedFile(val path: String, val bytes: Long, val sha256: String)

    /** 运行生命周期账本：登记本次 run 创建的资源，清理只按账本进行。 */
    class RunLedger(val runId: String) {
        private val _createdBookIds = mutableListOf<String>()
        val createdBookIds: List<String> get() = _createdBookIds.toList()
        var runDirCreated: Boolean = false
            internal set

        fun recordBookCreated(bookId: String) {
            if (!_createdBookIds.contains(bookId)) _createdBookIds.add(bookId)
        }

        fun recordDirCreated() {
            runDirCreated = true
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 夹具正文生成（真实 TXT，生产索引 / resolve / 阅读器消费同一段字节）
    // ────────────────────────────────────────────────────────────────────────

    /** 甲书正文：查询词全书唯一、marker1 紧随其后、marker2 相距充分（≥4000 字）。 */
    fun generateBodyA(): FixtureContent {
        val sb = StringBuilder()
        sb.append("《").append(TITLE_A).append("》\n\n")
        sb.append("第一章 序章\n")
        while (sb.length < 4000) sb.append("这是用于填充甲书前置段落的内容文本。")
        val queryOffset = sb.length
        sb.append(SNIPPET_A1)
        check(sb.indexOf(QUERY_A) == queryOffset + "唯一检索词 ".length) {
            "查询词 offset 必须落在 snippet 内部（=$queryOffset）"
        }
        val marker1Offset = sb.length
        sb.append(MARKER_A1)
        sb.append("\n\n第二章 中段\n")
        // 以 marker1 实际 offset + 4000 为填充目标，而不是全文绝对 8000：marker1 已占去前段长度，
        // 绝对目标只留下 <4000 的间隔（原 gap 3996）。4000 阈值与跨页互斥断言保持不变。
        while (sb.length < marker1Offset + 4000) sb.append("填充甲书中段内容以充分拉开两个位点之间的距离文本。")
        sb.append(MARKER_A2)
        sb.append("\n\n第三章 完结尾声\n后续正文结尾内容。\n")
        val text = sb.toString()
        check(text.indexOf(QUERY_A) == text.lastIndexOf(QUERY_A)) { "查询词必须全书唯一" }
        check(text.indexOf(MARKER_A1) == text.lastIndexOf(MARKER_A1)) { "MARKER_A1 必须全书唯一" }
        check(text.indexOf(MARKER_A2) == text.lastIndexOf(MARKER_A2)) { "MARKER_A2 必须全书唯一" }
        check(text.indexOf(MARKER_A1) - text.indexOf(QUERY_A) in 1..40) {
            "marker1 必须紧随查询词（同页落点合同）"
        }
        check(text.indexOf(MARKER_A2) - text.indexOf(MARKER_A1) >= 4000) {
            "marker2 与 marker1 必须相距充分（跨页互斥断言依据）"
        }
        return FixtureContent(bodyText = text, marker = MARKER_A1, markerFar = MARKER_A2)
    }

    /** 乙书正文：不含查询词与其子串变体；排除是语义排除（真实索引后无查询词行）。 */
    fun generateBodyB(): FixtureContent {
        val sb = StringBuilder()
        // 正文页首必须中性：标题元数据 TITLE_B 保留查询词子串变体（CRAQX119LMX）用于
        // BookDao LIKE 子串命中，但正文不能包含 QUERY_A 或其变体，否则本书自身的
        // “正文不含查询词”断言与标题常量冲突。
        sb.append("《测试 TXT 贰》\n\n")
        sb.append("第一章 检索验证\n")
        while (sb.length < 150) sb.append("填充乙书前置段落的文本内容以对齐偏移。")
        sb.append("查询关键词不出现于此书正文。")
        sb.append(MARKER_B)
        sb.append("\n\n第二章 持续探索\n后续文本内容。\n")
        val text = sb.toString()
        check(!text.contains(QUERY_A)) { "乙书正文不得包含查询词" }
        check(!text.contains("${QUERY_A}X")) { "乙书正文不得包含查询词子串变体" }
        check(text.indexOf(MARKER_B) == text.lastIndexOf(MARKER_B)) { "MARKER_B 必须全书唯一" }
        return FixtureContent(bodyText = text, marker = MARKER_B, markerFar = null)
    }

    // ────────────────────────────────────────────────────────────────────────
    // t119 证据落盘。取证失败即抛（成功路径必须取得关键证据，不「日志一条继续 PASS」）；
    // 失败路径对本组函数的调用一律 try-catch 并把错误 addSuppressed 到首错。
    // ────────────────────────────────────────────────────────────────────────

    /** 结果目录路径解析（无副作用：不创建目录；创建只在 setUp / 取证动作内显式执行）。 */
    fun resultsDirPath(context: Context): File =
        File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "$RESULTS_DIR_PREFIX$runId",
        )

    /**
     * 取证动作内的目录确保（取证函数是动作，允许创建；创建失败显式失败，不静默）。
     * setUp 预检使用无副作用的 [resultsDirPath]，避免「先建目录再查存在」的自冲突。
     */
    fun ensureResultsDir(): File {
        val dir = resultsDirPath(context)
        if (!dir.isDirectory) {
            check(dir.mkdirs()) { "结果目录创建失败: ${dir.absolutePath}" }
        }
        return dir
    }

    fun recordEvidence(step: String, payloadBuilder: JSONObject.() -> Unit): File {
        val file = File(ensureResultsDir(), "${runId}__$step.json")
        val json = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", step)
            put("packageName", context.packageName)
            put("timestamp", Instant.now().toString())
            put("timestampMillis", System.currentTimeMillis())
            put("lastStep", lastStep)
            val p = JSONObject().apply(payloadBuilder)
            put("payload", p)
        }
        file.writeText(json.toString(2))
        Log.i(TAG, "evidence recorded: $step -> ${file.name}")
        return file
    }

    /** 首个导致测试主体退出的异常：类、消息、完整栈、当时步骤。调用方随后必须原样抛出。 */
    fun recordFirstFailure(throwable: Throwable) {
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        recordEvidence("first_failure") {
            put("throwableClass", throwable.javaClass.name)
            put("message", throwable.message ?: JSONObject.NULL)
            put("stackTrace", sw.toString())
            put("causeClass", throwable.cause?.javaClass?.name ?: JSONObject.NULL)
        }
    }

    /**
     * 完整多 root 语义树逐 root 枚举（沿 117 现有实现：`onAllNodes(isRoot())` +
     * `printToString(maxDepth = 60)`）：不假设 onRoot 唯一，不静默截断；
     * merged 与 unmerged 各一份。取证失败向上抛。
     */
    fun saveRoots(tag: String) {
        val rootInteractions = composeRule.onAllNodes(isRoot(), useUnmergedTree = false)
        val mergedNodes = rootInteractions.fetchSemanticsNodes()
        val mergedTexts = (0 until mergedNodes.size).map { i ->
            "==== root[$i] id=${mergedNodes[i].id} ====\n" + rootInteractions[i].printToString(maxDepth = 60)
        }
        val unmergedInteractions = composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
        val unmergedNodes = unmergedInteractions.fetchSemanticsNodes()
        val unmergedTexts = (0 until unmergedNodes.size).map { i ->
            "==== root[$i] id=${unmergedNodes[i].id} ====\n" +
                unmergedInteractions[i].printToString(maxDepth = 60)
        }
        recordEvidence("semantics_trees") {
            put("tag", tag)
            put("mergedRootCount", mergedNodes.size)
            put("unmergedRootCount", unmergedNodes.size)
            put("treeChars", mergedTexts.sumOf { it.length } + unmergedTexts.sumOf { it.length })
            put("mergedTrees", mergedTexts.joinToString("\n\n"))
            put("unmergedTrees", unmergedTexts.joinToString("\n\n"))
        }
    }

    /**
     * 截图：多 root 场景仅截取第一个 root（`captureToImage` 按 interaction 逐 root 截取），
     * 证据中如实登记 rootCount —— 单 root 截图不冒充完整屏幕；完整屏幕证据以
     * [saveRoots] 的逐 root 树为准。取证失败向上抛。
     */
    fun captureScreenshot(tag: String) {
        val roots = composeRule.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "captureScreenshot($tag): 无可截取的 semantics root" }
        val bitmap = roots[0].captureToImage().asAndroidBitmap()
        val file = File(ensureResultsDir(), "${runId}__$tag.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        recordEvidence("screenshot") {
            put("tag", tag)
            put("capturedRootIndex", 0)
            put("rootCountAtCapture", nodes.size)
            put("note", "单 root 截图；完整屏幕证据以 semantics_trees 逐 root 枚举为准")
            put("file", file.name)
        }
    }

    /** 遍历全部 root 的语义树收集可读文本（Text / ContentDescription）；取证失败向上抛。 */
    fun collectSemanticsTexts(useUnmerged: Boolean): List<String> {
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
        val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = useUnmerged)
            .fetchSemanticsNodes()
        check(roots.isNotEmpty()) { "collectSemanticsTexts: 无可遍历的 semantics root" }
        roots.forEach { walk(it) }
        return out
    }

    // ────────────────────────────────────────────────────────────────────────
    // UI 代理观测（首页 / 阅读器 chrome / 临时按钮 / marker / 搜索框）
    // ────────────────────────────────────────────────────────────────────────

    fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription(HOME_SEARCH_CD, substring = true)
            .fetchSemanticsNodes().size

    fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription(READER_CHROME_BACK_CD, substring = true)
            .fetchSemanticsNodes().size

    fun returnToReadingCount(): Int =
        composeRule.onAllNodesWithContentDescription(RETURN_TO_READING_CD, substring = true)
            .fetchSemanticsNodes().size

    fun markerCount(marker: String): Int =
        composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().size

    /** 搜索框 EditableText：key 值类型为 AnnotatedString，取 `.text` 得 String。 */
    fun searchFieldText(): String? =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    /** 甲书正文命中卡 merged 选择器（书名 + 定位 + 原文来源 + 真实 click action）。 */
    fun contentCardMatcher(locationSnippet: String?): SemanticsMatcher {
        var m = hasClickAction() and hasText(TITLE_A, substring = true)
        if (locationSnippet != null) m = m and hasText(locationSnippet, substring = true)
        return m and hasText(ORIGIN_LABEL, substring = true)
    }

    /** 乙书元数据「书籍」卡选择器（typeLabel=书籍；正文卡为「正文」+「原文」，不互配）。 */
    fun bookCardMatcherB(): SemanticsMatcher =
        hasClickAction() and hasText(TITLE_B, substring = true) and
            hasText(BOOK_CARD_LABEL, substring = true)

    fun recordUiState(tag: String, extra: JSONObject.() -> Unit = {}) {
        recordEvidence(tag) {
            put("homeSearchCount", homeSearchNodeCount())
            put("readerChromeBackCount", readerChromeBackCount())
            put("returnToReadingCount", returnToReadingCount())
            put("marker1Count", if (::contentA.isInitialized) markerCount(MARKER_A1) else -1)
            put("marker2Count", if (::contentA.isInitialized) markerCount(MARKER_A2) else -1)
            put("markerBCount", if (::contentB.isInitialized) markerCount(MARKER_B) else -1)
            put("searchFieldEditableText", searchFieldText() ?: JSONObject.NULL)
            put("visibleTexts", JSONArray(collectSemanticsTexts(false)))
            extra()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 夹具装载：craRunId（宿主传入）→ 冲突预检 → 生产 DAO 建书 + 真实 TXT（登记字节指纹）
    // → 生产单书索引入口 → 真实产物观测 + 全库唯一归属断言
    // ────────────────────────────────────────────────────────────────────────

    fun fixtureRunDir(): File = File(context.filesDir, "$FIXTURES_PARENT_DIR/$runId")

    /** 本 run 登记的 fixture TXT（创建时记录原始字节指纹；清理前逐文件核验）。 */
    private val trackedFiles = mutableListOf<TrackedFile>()

    private fun writeTrackedFixture(dir: File, fileName: String, body: String): File {
        val file = File(dir, fileName)
        check(!file.exists()) { "夹具文件初始必须缺席: ${file.absolutePath}" }
        file.writeText(body)
        val bytes = file.readBytes()
        trackedFiles += TrackedFile(
            path = file.absolutePath,
            bytes = bytes.size.toLong(),
            sha256 = J1RealReaderFixtures.sha256(bytes),
        )
        return file
    }

    @Before
    fun setUp() {
        // 1. craRunId 只接受宿主传入（am instrument -e craRunId T119V1-YYYYMMDD-HHMMSS）；
        //    无宽松 fallback，格式不符立即失败
        val rawRunId = requireNotNull(
            InstrumentationRegistry.getArguments().getString(RUN_ID_ARG),
        ) { "宿主必须以 -e $RUN_ID_ARG 传入 runId（T119V1-YYYYMMDD-HHMMSS）" }
        require(RUN_ID_REGEX.matches(rawRunId)) {
            "runId 格式不符（实际=$rawRunId，期望 T119V1-\\d{8}-\\d{6}）"
        }
        runId = rawRunId
        bookIdA = "cra119-txt-a-$runId"
        bookIdB = "cra119-txt-b-$runId"
        ledger = RunLedger(runId)

        db = J1RealReaderFixtures.openDatabase(context)

        // 2. 引导基线：保存原值并置为已完成（防止首帧引导遮罩），tearDown 按原值还原
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)

        // 3. 启动全量预检（无副作用路径检查）：bookId / run 目录 / 结果目录任一存在即拒绝，
        //    绝不删除未知对象；craRunId 无效时已在上方终止，不生成任何目录
        val conflictA = runBlocking { db.bookDao().getById(bookIdA) }
        val conflictB = runBlocking { db.bookDao().getById(bookIdB) }
        val runDir = fixtureRunDir()
        val resultsDirFile = resultsDirPath(context)
        if (conflictA != null || conflictB != null || runDir.exists() || resultsDirFile.exists()) {
            error(
                "检测到冲突记录或残留（conflictA=${conflictA != null}, conflictB=${conflictB != null}, " +
                    "dirExists=${runDir.exists()}, resultsDirExists=${resultsDirFile.exists()}），" +
                    "拒绝启动以防混淆现有数据。",
            )
        }
        if (!runDir.mkdirs() && !runDir.exists()) error("创建夹具专属目录失败: ${runDir.absolutePath}")
        ledger.recordDirCreated()
        // 结果目录：初始缺席已在预检确证，此处明确创建一次并 check 成功
        // （失败即失败，不复核第二次成功盖原失败）
        check(resultsDirFile.mkdirs()) { "创建结果目录失败: ${resultsDirFile.absolutePath}" }

        // 首错取证：仅在本 run 结果目录已成功创建后包住 setup 主体；
        // 目录创建前的 runId/冲突预检失败无法写未知路径，仍由 tearDown 保守 BLOCKED。
        try {
            val nowIso = Instant.now().toString()
            contentA = generateBodyA()
            contentB = generateBodyB()
            val fileA = writeTrackedFixture(runDir, "$bookIdA.txt", contentA.bodyText)
            val fileB = writeTrackedFixture(runDir, "$bookIdB.txt", contentB.bodyText)

            runBlocking {
                // 甲书：生产 DAO 建书 + 初始进度行（locator=查询词所在位点，percent 与之一致）
                val bookA = BookEntity(
                    id = bookIdA,
                    title = TITLE_A,
                    author = "测试作者甲壹壹玖",
                    format = "txt",
                    size = contentA.bodyText.toByteArray(Charsets.UTF_8).size,
                    local_content_path = fileA.absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
                db.bookDao().upsert(bookA)
                ledger.recordBookCreated(bookIdA)
                val queryOffsetA = contentA.bodyText.indexOf(QUERY_A)
                db.readingProgressDao().upsert(
                    ReadingProgressEntity(
                        book_id = bookIdA,
                        progress_percent = queryOffsetA.toFloat() / contentA.bodyText.length * 100f,
                        last_read_at = nowIso,
                        total_reading_time_ms = 60000L,
                        completion_state = "reading",
                        current_location_json = LocatorBuilder.progressJson(
                            legacyOffset = queryOffsetA,
                            chapterIndex = 0,
                            charOffset = queryOffsetA,
                            globalOffset = queryOffsetA,
                        ),
                        updated_at = nowIso,
                    ),
                )
                db.bookContentDao().upsert(
                    BookContentEntity(book_id = bookIdA, reader_preview = contentA.bodyText.take(2000)),
                )

                // 乙书：标题含查询词子串变体（LIKE 命中 / 分词排除），无进度行
                val bookB = BookEntity(
                    id = bookIdB,
                    title = TITLE_B,
                    author = "测试作者贰",
                    format = "txt",
                    size = contentB.bodyText.toByteArray(Charsets.UTF_8).size,
                    local_content_path = fileB.absolutePath,
                    content_status = "available",
                    imported_at = nowIso,
                    updated_at = nowIso,
                )
                db.bookDao().upsert(bookB)
                ledger.recordBookCreated(bookIdB)
                db.bookContentDao().upsert(
                    BookContentEntity(book_id = bookIdB, reader_preview = contentB.bodyText.take(2000)),
                )

                // 4. 索引只经生产单书索引入口（不直接 INSERT search_terms）：生产仓储以生产 DAO +
                //    生产 RulesRepository + 生产 SearchTokenizer 手工装配；scheduler 留默认 null，
                //    不触碰全库 sweep（indexSingleBook 不取得 sweep 执行权）
                val indexRepository = SearchIndexRepository(
                    context = context,
                    bookDao = db.bookDao(),
                    bookContentDao = db.bookContentDao(),
                    termDao = db.searchTermDao(),
                    stateDao = db.searchIndexStateDao(),
                    coverageDao = db.searchIndexCoverageDao(),
                    tokenizer = SearchTokenizer(),
                    rulesRepository = RulesRepository(db.readerTextRuleDao(), db.readerCorrectionDao()),
                )
                indexRepository.indexSingleBook(bookA)
                indexRepository.indexSingleBook(bookB)
            }

            // 5. 真实产物观测 + 全库唯一归属断言（生产 Tokenizer 实际规则，非长度/自造保证）：
            //    全库 search_terms 中 term=查询词 token 的行必须恰好一行且归属甲书；
            //    乙书索引行非空（排除是语义排除而非索引缺失）且全库无乙书查询词行。
            val queryToken = QUERY_A.lowercase()
            val rowsForToken = J1RealReaderFixtures.captureSearchTermRowsByTerm(db, queryToken)
            val rowsA = J1RealReaderFixtures.captureSearchTermRows(db, bookIdA)
            val rowsB = J1RealReaderFixtures.captureSearchTermRows(db, bookIdB)
            val queryRowA = rowsA.filter { it.optString("term") == queryToken }
            recordEvidence("fixture_index_products") {
                put("bookIdA", bookIdA)
                put("bookIdB", bookIdB)
                put("queryToken", queryToken)
                put("allRowsForQueryToken", JSONArray(rowsForToken))
                put("rowsA", JSONArray(rowsA))
                put("rowsB", JSONArray(rowsB))
            }
            assertTrue(
                "查询词 token（$queryToken）必须全库唯一归属甲书：实际全库 ${rowsForToken.size} 行，" +
                    "归属=${rowsForToken.map { it.optString("book_id") }}；未知其他命中即停（不删除未知记录）",
                rowsForToken.size == 1 && rowsForToken[0].optString("book_id") == bookIdA,
            )
            assertEquals(
                "甲书查询词索引行必须恰好一行（实际=${queryRowA.size}）",
                1, queryRowA.size,
            )
            val offsets = queryRowA[0].optString("offsets")
            assertTrue(
                "查询词索引行 offsets 必须可解析（实际=$offsets）",
                SearchOffsets.parse(offsets).isNotEmpty(),
            )
            assertTrue("乙书必须真实经过生产索引（search_terms 行不得为空）", rowsB.isNotEmpty())
            assertTrue(
                "乙书索引行不得包含查询词 token=${queryToken}（分词排除的实际产物核验）",
                rowsB.none { it.optString("term") == queryToken },
            )
        } catch (t: Throwable) {
            firstFailure = t
            try {
                recordFirstFailure(t)
            } catch (rec: Throwable) {
                evidenceCaptureFailed = true
                t.addSuppressed(rec)
            }
            throw t
        }
    }

    @After
    fun tearDown() {
        // 顺序合同（job0001）：取证 → 已登记资产预检 → 全部通过才清理；
        // 取证失败 / 未知文件 / 内容漂移 / 读取失败 / 归属不明 ⇒ BLOCKED：保留 fixture 文件与
        // DB 行（唯一证据未保全则保留依赖资产），只继续独立安全恢复（onboarding 原值、
        // Activity/DB 资源关闭）。各阶段错误分列聚合（addSuppressed），不覆盖首错。
        val errors = mutableListOf<Throwable>()
        try {
            scenario?.close()
        } catch (t: Throwable) {
            errors += t
        }
        // 1. 取证阶段（setUp 早期失败守卫：runId 未初始化时不制造新错误）
        var teardownEvidenceFailed = false
        try {
            if (::runId.isInitialized) {
                recordEvidence("teardown_timeline") {
                    put("lastStep", lastStep)
                    put("firstFailureClass", firstFailure?.javaClass?.name ?: JSONObject.NULL)
                    put("evidenceCaptureFailed", evidenceCaptureFailed)
                    put("trackedFiles", JSONArray(trackedFiles.map {
                        JSONObject().put("path", it.path).put("bytes", it.bytes)
                            .put("sha256", it.sha256)
                    }))
                }
                // 业务失败时先记录原始持久行（业务证据优先于任何清理；充分取证后清理不被阻止）
                if (firstFailure != null && ::db.isInitialized && db.isOpen && ::bookIdA.isInitialized) {
                    recordEvidence("final_rows_on_failure") {
                        put("rowA", J1RealReaderFixtures.captureProgressRow(db, bookIdA)?.toJson()
                            ?: JSONObject.NULL)
                        if (::bookIdB.isInitialized) {
                            put("rowB", J1RealReaderFixtures.captureProgressRow(db, bookIdB)?.toJson()
                                ?: JSONObject.NULL)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            teardownEvidenceFailed = true
            errors += t
        }
        // 2. 预检与清理决策（fixture 删改前完成全部检查）
        var blocked: String? = null
        if (evidenceCaptureFailed) {
            blocked = "取证未闭合（catch 内取证失败已 addSuppressed 到首错）；保留 fixture 文件与 DB 行"
        } else if (teardownEvidenceFailed) {
            blocked = "tearDown 取证失败（证据未保全）；保留 fixture 文件与 DB 行"
        } else if (::ledger.isInitialized && ::runId.isInitialized) {
            try {
                blocked = verifyTrackedFixtures()
            } catch (t: Throwable) {
                blocked = "清理预检异常（保留现场）: $t"
            }
        }
        if (blocked == null) {
            // 3. 预检通过：仅对初始缺席 + ledger/路径/字节指纹确证的对象精确清理（不递归）
            try {
                if (::db.isInitialized && db.isOpen) {
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
                }
            } catch (t: Throwable) {
                errors += t
            }
            try {
                if (::ledger.isInitialized) deleteTrackedFixtures()
            } catch (t: Throwable) {
                errors += t
            }
        } else {
            // BLOCKED：明确原因，保留依赖 fixture 与 DB 行；错误分列不吞
            try {
                if (::runId.isInitialized) {
                    recordEvidence("cleanup_blocked") { put("reason", blocked) }
                }
            } catch (t: Throwable) {
                errors += t
            }
            errors += IllegalStateException("BLOCKED: $blocked")
        }
        try {
            if (::db.isInitialized && db.isOpen) db.close()
        } catch (t: Throwable) {
            errors += t
        } finally {
            try {
                J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            } catch (t: Throwable) {
                errors += t
            }
        }
        if (errors.isEmpty()) return
        val primary = firstFailure ?: errors.removeAt(0)
        errors.forEach { primary.addSuppressed(it) }
        Log.w(TAG, "tearDown errors aggregated=${errors.size} (suppressed onto primary)")
        if (firstFailure == null) throw primary
        // 主体已失败：首错已抛给框架，这里只聚合不重复抛，避免覆盖首错报告
    }

    /**
     * 清理预检（纯检查，不删除）：null = 通过；非 null = BLOCKED 原因。
     * 覆盖：目录存在性、未知文件、路径规范化在本 run 根内、逐文件当前 size/SHA 与登记一致；
     * 读取失败如实返回原因（不当作不存在）。
     */
    private fun verifyTrackedFixtures(): String? {
        if (!ledger.runDirCreated) return null // setUp 未走到目录创建（无已登记文件）
        return verifyTrackedFixtureDirectory(fixtureRunDir(), trackedFiles)
    }

    /**
     * 清理预检本体（纯检查，不删除；可离线用真实函数驱动）：
     * null = 通过；非 null = BLOCKED 原因。
     * 实际目录内容必须与登记集合严格一致；未知文件/缺登记文件/路径越界/读取失败/size-SHA 漂移
     * 均 BLOCKED。登记过的文件存在且校验通过是正常状态，不是不一致。
     */
    fun verifyTrackedFixtureDirectory(runDir: File, tracked: List<TrackedFile>): String? {
        if (!runDir.exists()) return "登记过的 run 目录不存在（未知状态）: ${runDir.absolutePath}"
        val canonicalRoot = runDir.canonicalFile.toPath()
        val actualFiles = runDir.listFiles()?.map { it.absolutePath }?.toSet()
            ?: return "无法列出 run 目录（读取失败，保留现场）: ${runDir.absolutePath}"
        val trackedPaths = tracked.map { it.path }.toSet()
        val unknown = actualFiles - trackedPaths
        if (unknown.isNotEmpty()) return "run 目录存在未登记文件（保留现场）: $unknown"
        for (tf in tracked) {
            val file = File(tf.path)
            val canonical = file.canonicalFile.toPath()
            if (!canonical.startsWith(canonicalRoot) || canonical == canonicalRoot) {
                return "登记路径越出本 run 根（保留现场）: ${tf.path}"
            }
            if (!file.exists()) return "登记文件缺失（保留现场）: ${tf.path}"
            val bytes = try {
                file.readBytes()
            } catch (t: Throwable) {
                return "登记文件读取失败（保留现场）: ${tf.path}: $t"
            }
            if (bytes.size.toLong() != tf.bytes || J1RealReaderFixtures.sha256(bytes) != tf.sha256) {
                return "登记文件内容漂移（保留现场）: ${tf.path}"
            }
        }
        val actualNames = actualFiles.map { File(it).name }.toSet()
        val trackedNames = tracked.map { File(it.path).name }.toSet()
        if (actualNames != trackedNames) {
            return "目录内文件与登记集合不一致（保留现场）: actual=$actualNames, tracked=$trackedNames"
        }
        return null
    }

    /** 预检通过后的精确删除：逐文件删 + 复核缺席；目录非空校验后删除空目录（非递归）。 */
    private fun deleteTrackedFixtures() {
        if (!ledger.runDirCreated) return
        val runDir = fixtureRunDir()
        for (tf in trackedFiles) {
            val file = File(tf.path)
            if (!file.delete()) error("登记文件删除失败: ${tf.path}")
            if (file.exists()) error("删除后文件仍存在: ${tf.path}")
        }
        val remaining = runDir.listFiles()
        if (remaining != null && remaining.isNotEmpty()) {
            error("逐文件删除后目录非空: ${remaining.map { it.name }}")
        }
        if (!runDir.delete()) error("空 run 目录删除失败: ${runDir.absolutePath}")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 主旅程（唯一 @Test：真实业务旅程；路由编解码往返已由既有 JVM 测试覆盖，不重复）
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun searchContentHitJourney_contentHitTemporaryLookup_preservesSearchStateAndProgress() {
        val stepRecord = mutableMapOf<String, Any>()
        try {
            markStep("start")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            markStep("session_home")

            // ── 阶段 1：Home 真实点击甲书卡进入普通阅读 ──
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText(TITLE_A, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText(TITLE_A, substring = true)).performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 15_000) { markerCount(MARKER_A1) > 0 }
            // 先取证后断言（P4）：完整树与 UI 状态先落盘，再 assert 可见性
            saveRoots("stage1_reader_entered")
            recordUiState("stage1_reader_entered")
            composeRule.onNode(hasText(MARKER_A1, substring = true)).assertIsDisplayed()
            markStep("stage1_reader_entered")

            // 无副作用核验分页前置（实际读取 Activity 注入的 readerSettings；模式不符显式失败）
            var pagedReport: J1RealReaderFixtures.PagedPreconditionReport? = null
            scenario?.onActivity { activity ->
                val settings = activity.settingsStore.reader.value
                pagedReport = J1RealReaderFixtures.verifyPagedModePrecondition(
                    readerMode = settings.readerMode,
                    pagerEngineMode = settings.pagerEngineMode,
                )
            }
            assertNotNull("分页前置检查报告必须成功生成", pagedReport)
            stepRecord["pagedPrecondition"] = pagedReport!!.toJson().toString()
            assertTrue(
                "必须满足分页前置契约 (readerMode=${pagedReport!!.readerMode}, " +
                    "pagerEngineMode=${pagedReport!!.pagerEngineMode})",
                pagedReport!!.satisfiesContract,
            )
            markStep("stage1_paged_precondition")

            // 等待生产分页位置落库（首帧上报 + 500ms 防抖），取进入后的稳定行
            val rowFixture = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            val settleDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < settleDeadline) {
                val now = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
                if (now != null && rowFixture != null &&
                    (now.current_location_json != rowFixture.current_location_json ||
                        now.updated_at != rowFixture.updated_at)
                ) {
                    break
                }
                Thread.sleep(200)
            }
            Thread.sleep(800)
            val rowP1 = stableProgressRow(after = rowFixture, stage = "stage1_persist_settled")
            recordUiState("stage1_persist_settled") { put("pageStartP1", rowP1.legacyOffset) }
            assertTrue(
                "页起始必须非零（清零被 J1 恢复合同列为必须拒绝，实际=${rowP1.legacyOffset}）",
                rowP1.legacyOffset > 0,
            )
            val queryOffsetA = contentA.bodyText.indexOf(QUERY_A)
            assertTrue(
                "页起始不得超过查询词位点（页起始=${rowP1.legacyOffset}, queryOffset=$queryOffsetA）",
                rowP1.legacyOffset <= queryOffsetA,
            )
            markStep("stage1_persist_settled")

            // ── 阶段 2：真实翻页推进到不同于命中点的合法持久位置 ──
            composeRule.onNode(hasText(MARKER_A1, substring = true)).performTouchInput {
                swipeLeft()
            }
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                val latest = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
                latest != null && latest.updated_at > rowP1.updated_at &&
                    latest.current_location_json != rowP1.current_location_json &&
                    latest.legacyOffset >= 0 && latest.legacyOffset != rowP1.legacyOffset
            }
            val rowP2 = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            assertNotNull("真实翻页后生产保存的进度行必须存在", rowP2)
            // 先取证后断言
            saveRoots("stage2_advanced")
            recordUiState("stage2_advanced") {
                put("pageStartP1", rowP1.legacyOffset)
                put("pageStartP2", rowP2!!.legacyOffset)
            }
            rowP2!!.assertLegalProgressAdvance(rowP1, "真实翻页推进")
            assertTrue(
                "推进后页起始必须严格前进（${rowP2.legacyOffset} > ${rowP1.legacyOffset}）",
                rowP2.legacyOffset > rowP1.legacyOffset,
            )
            composeRule.onAllNodesWithText(MARKER_A1, substring = true).assertCountEquals(0)
            assertTrue(
                "查询词位点必须落在推进前后两页之间（页起始区间合同）" +
                    "(${rowP1.legacyOffset} <= $queryOffsetA < ${rowP2.legacyOffset})",
                rowP1.legacyOffset <= queryOffsetA && queryOffsetA < rowP2.legacyOffset,
            )
            markStep("stage2_advanced")

            // ── 阶段 3：真实 Back 回 Home（生产 BackHandler 链：先隐藏控件）──
            if (readerChromeBackCount() > 0) {
                scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                composeRule.waitForIdle()
                Thread.sleep(500)
            }
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 15_000) { homeSearchNodeCount() > 0 }
            markStep("stage3_back_home")

            // 退出后静置覆盖防抖窗口，取稳定冻结基线（只观察，不归因退出保存）
            Thread.sleep(800)
            val baselineRow = stableProgressRow(after = rowP2, stage = "stage3_baseline")
            // 先取证后断言：基线证据先落盘，再核页起始一致
            recordEvidence("stage3_baseline_captured") {
                put("baseline", baselineRow.toJson())
                put("rowP2", rowP2.toJson())
                put("position_evidence_kind", "EXISTING_POSITION_OBSERVATION")
                put("save_cause", J1RealReaderFixtures.SAVE_CAUSE_UNATTRIBUTED)
                put("not_covered", listOf("EXIT_SAVE_REQUEST_COMPLETION", "ALL_PENDING_SAVES_DRAINED"))
            }
            stepRecord["baselineRow"] = baselineRow.toJson().toString()
            stepRecord["position_evidence_kind"] = "EXISTING_POSITION_OBSERVATION"
            stepRecord["save_cause"] = J1RealReaderFixtures.SAVE_CAUSE_UNATTRIBUTED
            assertEquals(
                "基线页起始必须与推进落点一致（位置观察，非保存归因）",
                rowP2.legacyOffset, baselineRow.legacyOffset,
            )
            markStep("stage3_baseline_captured")

            // ── 阶段 4：Home 真实点击全局 search 入口（同一 Activity 会话，不重启）──
            composeRule.onNodeWithContentDescription(HOME_SEARCH_CD, substring = true).performClick()
            composeRule.waitForIdle()
            markStep("stage4_search_opened")

            // 真实输入唯一正文查询词（先清空确保不拼接）
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText(SEARCH_FIELD_PLACEHOLDER).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            val searchBox = composeRule.onNode(hasText(SEARCH_FIELD_PLACEHOLDER))
            searchBox.performTextClearance()
            searchBox.performTextInput(QUERY_A)
            composeRule.waitForIdle()
            markStep("stage4_query_input")

            // 生产命中规则正向推导期望坐标（全部使用生产类本体，UI 值只做严格相等校验）
            val derived = awaitStableDerived(bookIdA, QUERY_A, contentA.bodyText)
            val derivedB = J1RealReaderFixtures.deriveSearchCoordinate(
                db, bookIdB, QUERY_A, contentB.bodyText,
            )
            // 生产索引的 offset 是「该章正文内」偏移（SearchHit/SearchOffsetResolver 合同）。
            // 回溯到全书正文必须用生产同一分章口径（TxtChapterSplitter）取章起始再相加；
            // 不能把章内 offset 当全书 offset，也不能硬编码本样本的 15。UI 期望仍保留章内口径。
            val chapterSlices = TxtChapterSplitter.split(contentA.bodyText)
            val traceChapterIndex = derived.traceChapterIndex
            val traceChapterStart = traceChapterIndex
                ?.let { chapterIndex -> chapterSlices.getOrNull(chapterIndex - 1)?.startOffset }
            val chapterOffset = derived.expectedCharOffset
            val expectedGlobalOffset = if (chapterOffset != null && traceChapterStart != null) {
                traceChapterStart + chapterOffset
            } else {
                null
            }
            val traceTerm = derived.traceTerm
            val traceMatchesWinningChapter = traceChapterIndex != null &&
                traceChapterIndex == derived.winningChapterIndex
            val traceBodyMatchGlobal = (expectedGlobalOffset != null && traceTerm != null &&
                traceMatchesWinningChapter &&
                contentA.bodyText.length >= expectedGlobalOffset + traceTerm.length &&
                contentA.bodyText.regionMatches(
                    expectedGlobalOffset, traceTerm, 0, traceTerm.length, ignoreCase = true,
                ))
            val traceBodySnippetGlobal = expectedGlobalOffset?.let { start ->
                contentA.bodyText.substring(
                    start, (start + 16).coerceAtMost(contentA.bodyText.length),
                )
            }
            recordEvidence("stage4_derived_coordinate") {
                put("derivedA", derived.toJson())
                put("derivedB", derivedB.toJson())
                put("winningChapterIndex", derived.winningChapterIndex ?: JSONObject.NULL)
                put("traceChapterIndex", traceChapterIndex ?: JSONObject.NULL)
                put("traceChapterStart", traceChapterStart ?: JSONObject.NULL)
                put("chapterOffset", chapterOffset ?: JSONObject.NULL)
                put("expectedGlobalOffset", expectedGlobalOffset ?: JSONObject.NULL)
                put("traceTerm", traceTerm ?: JSONObject.NULL)
                put("traceMatchesWinningChapter", traceMatchesWinningChapter)
                put("traceBodyMatchGlobal", traceBodyMatchGlobal)
                put("traceBodySnippetGlobal", traceBodySnippetGlobal ?: JSONObject.NULL)
                put(
                    "note",
                    "索引 offset 为章内口径；全书回溯按生产 TxtChapterSplitter 章起始换算；" +
                        "UI 期望保持章内口径",
                )
            }
            assertNotNull(
                "必须由生产命中规则推导出甲书期望展示坐标（queryTerms=${derived.queryTerms}, " +
                    "matchedTerms=${derived.matchedTerms}, rows=${derived.termRowCount}）",
                derived.expectedCharOffset,
            )
            val expectedOffset = derived.expectedCharOffset!!
            assertTrue(
                "甲书查询词必须是唯一合法命中偏移（legalOffsets=${derived.legalOffsets}）",
                derived.legalOffsets.size == 1 && derived.legalOffsets[0] == expectedOffset,
            )
            assertEquals(
                "traceTerm 必须来自 winningChapter（winning=${derived.winningChapterIndex}, " +
                    "trace=$traceChapterIndex）",
                derived.winningChapterIndex,
                traceChapterIndex,
            )
            assertNotNull("必须能按生产分章口径取到回溯章起始", traceChapterStart)
            assertNotNull("必须能换算全书回溯偏移", expectedGlobalOffset)
            assertTrue(
                "推导坐标必须可回溯到甲书正文真实词项（chapter=${derived.winningChapterIndex}, " +
                    "chapterStart=$traceChapterStart, chapterOffset=$expectedOffset, " +
                    "global=$expectedGlobalOffset, term=$traceTerm, 片段=$traceBodySnippetGlobal）",
                traceBodyMatchGlobal,
            )
            assertNull(
                "乙书必须被生产正文命中规则排除（expected=${derivedB.expectedCharOffset}, " +
                    "matched=${derivedB.matchedTerms}）",
                derivedB.expectedCharOffset,
            )
            val expectedSnippet = derived.expectedLocationSnippet!!
            stepRecord["derived"] = derived.toJson().toString()
            markStep("stage4_expected_derived")

            // ── 阶段 5：非退化 Tab 对照，时点 1 = 点 Tab 前（默认「全部」页）──
            // 全部页：乙书元数据「书籍」卡（LIKE 子串命中）+ 甲书正文 hit 卡同时可见
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodes(bookCardMatcherB()).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            val bookCardBCountAll = composeRule.onAllNodes(bookCardMatcherB()).fetchSemanticsNodes().size
            val cardCountAll = composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                .fetchSemanticsNodes().size
            recordUiState("stage5_all_page_observed") {
                put("bookCardBCount", bookCardBCountAll)
                put("contentCardACount", cardCountAll)
                put("note", "时点1=全部页：乙书LIKE命中书籍卡与甲书正文hit并存（非退化对照基线）")
            }
            saveRoots("stage5_all_page")
            assertEquals(
                "「全部」页乙书元数据书籍卡必须出现（LIKE 子串命中对照，实际=$bookCardBCountAll）",
                1, bookCardBCountAll,
            )
            assertEquals(
                "「全部」页甲书正文 hit 卡必须唯一（实际=$cardCountAll）",
                1, cardCountAll,
            )
            markStep("stage5_all_page_verified")

            // ── 阶段 6：真实点击「正文」Tab，时点 2 = 点 Tab 后 ──
            // 胶囊选择器：可点击 + 含「正文」文本 + 不含「原文」（命中卡必有来源段
            // 「原文 · 第N章」，胶囊无；不用 count 数字 —— 命中卡 snippet 的字数可能含相同数字字符）
            // not 为 SemanticsMatcher 成员（无顶层 androidx.compose.ui.test.not 可导入）
            val tabPillMatcher = hasClickAction() and
                hasText(TAB_CONTENT_LABEL, substring = true) and
                hasText(ORIGIN_LABEL, substring = true).not()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodes(tabPillMatcher).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            val tabPillCount = composeRule.onAllNodes(tabPillMatcher).fetchSemanticsNodes().size
            recordEvidence("stage6_tab_pill_observed") {
                put("tabPillMatcherCount", tabPillCount)
                put("visibleTexts", JSONArray(collectSemanticsTexts(false)))
                put("note", "胶囊选择器=click+含『正文』+不含『原文』；Tab 选择态无语义，" +
                    "以列表身份对照证明 Tab 生效")
            }
            assertEquals(
                "「正文」Tab 胶囊必须唯一可定位（实际=$tabPillCount）",
                1, tabPillCount,
            )
            composeRule.onAllNodes(tabPillMatcher)[0].performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val bookCardBCountContent = composeRule.onAllNodes(bookCardMatcherB())
                .fetchSemanticsNodes().size
            val cardCountContent = composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                .fetchSemanticsNodes().size
            recordUiState("stage6_content_tab_state") {
                put("bookCardBCount", bookCardBCountContent)
                put("contentCardACount", cardCountContent)
                put("expectedSnippet", expectedSnippet)
                put("expectedOffset", expectedOffset)
                put("searchFieldEditableText", searchFieldText() ?: JSONObject.NULL)
                put("note", "时点2=正文页：乙书书籍卡消失（分词排除）、甲书正文hit唯一")
            }
            saveRoots("stage6_content_tab")
            assertEquals(
                "「正文」页乙书元数据书籍卡必须消失（实际=$bookCardBCountContent；" +
                    "非退化的 Tab 生效证据：全部页=1 → 正文页=0）",
                0, bookCardBCountContent,
            )
            assertEquals(
                "「正文」页甲书正文 hit 卡必须唯一（实际=$cardCountContent）",
                1, cardCountContent,
            )
            markStep("stage6_content_tab_verified")

            // ── 阶段 7：真实点击正文 hit 卡 → TEMPORARY 阅读器落到目标 marker ──
            val cardMatcherA = contentCardMatcher(expectedSnippet)
            val rowPreHop = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            recordEvidence("stage7_pre_hop_row") {
                put("row", rowPreHop?.toJson() ?: JSONObject.NULL)
                put("baseline", baselineRow.toJson())
            }
            baselineRow.assertExactEquals(rowPreHop, "临时查阅前基线 12 列冻结")
            composeRule.onNode(cardMatcherA).performClick()
            markStep("stage7_card_clicked")
            composeRule.waitUntil(timeoutMillis = 15_000) { markerCount(MARKER_A1) > 0 }
            // 先取证后断言：落点证据先落盘
            saveRoots("stage7_temp_reader_landed")
            recordUiState("stage7_temp_reader_landed") {
                put("landingMarker", MARKER_A1)
                put("note", "落点=marker1可见+marker2不可见（相距≥4000字）；非仅bookId正确冒充")
            }
            captureScreenshot("stage7_temp_reader_landed")
            composeRule.onNode(hasText(MARKER_A1, substring = true)).assertIsDisplayed()
            composeRule.onAllNodesWithText(MARKER_A2, substring = true).assertCountEquals(0)
            // 空栈临时查阅：返回按钮不得出现（hasReturnableTarget=false 的可观测证据）
            composeRule.onAllNodesWithContentDescription(RETURN_TO_READING_CD, substring = true)
                .assertCountEquals(0)
            val rowDuringTemp = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            recordEvidence("stage7_during_temp_row") {
                put("row", rowDuringTemp?.toJson() ?: JSONObject.NULL)
                put("rowB", J1RealReaderFixtures.captureProgressRow(db, bookIdB)?.toJson()
                    ?: JSONObject.NULL)
            }
            baselineRow.assertExactEquals(rowDuringTemp, "临时查阅期间基线 12 列冻结")
            val rowB = J1RealReaderFixtures.captureProgressRow(db, bookIdB)
            assertNull("临时查阅不得给乙书写入普通进度行", rowB)
            markStep("stage7_temp_landing_verified")

            // ── 阶段 8：真实返回（先隐藏控件；一次 Back）──
            if (readerChromeBackCount() > 0) {
                scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                composeRule.waitForIdle()
                Thread.sleep(500)
            }
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            markStep("stage8_back_pressed")

            // 观测实际落点：搜索页（搜索框 + hit 卡）或普通阅读器（甲书正文 marker）。
            // 不预设、不重启、不弱化期望；G1 为待设备证明的疑似接线缺口。
            val outcome = awaitReturnOutcome(expectedSnippet)
            if (outcome == ReturnOutcome.AT_SEARCH) {
                markStep("stage8_at_search")
                // ── 时点 3 = 返回后：query / Tab / hit 保留 + 12 列冻结 ──
                val fieldText = searchFieldText()
                val bookCardBCountReturned = composeRule.onAllNodes(bookCardMatcherB())
                    .fetchSemanticsNodes().size
                val cardAfterReturn = composeRule.onAllNodes(cardMatcherA).fetchSemanticsNodes().size
                recordUiState("stage8_search_state_preserved") {
                    put("searchFieldEditableText", fieldText ?: JSONObject.NULL)
                    put("bookCardBCount", bookCardBCountReturned)
                    put("contentCardACount", cardAfterReturn)
                    put("note", "时点3=返回后：查询词保留 + 乙书书籍卡仍为0（仍在正文Tab）+ 甲书hit唯一")
                }
                saveRoots("stage8_search_state_preserved")
                captureScreenshot("stage8_search_state_preserved")
                assertEquals(
                    "返回后查询词必须保留（搜索框 EditableText，实际=$fieldText）",
                    QUERY_A, fieldText,
                )
                assertEquals(
                    "返回后必须仍在正文 Tab：乙书元数据书籍卡必须仍为 0" +
                        "（若退回全部页该卡会重新出现，实际=$bookCardBCountReturned）",
                    0, bookCardBCountReturned,
                )
                assertEquals(
                    "返回后正文 hit 必须仍在且唯一（实际=$cardAfterReturn）",
                    1, cardAfterReturn,
                )
                val rowFinal = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
                val rowBFinal = J1RealReaderFixtures.captureProgressRow(db, bookIdB)
                baselineRow.assertExactEquals(rowFinal, "临时查阅全程结束后基线 12 列冻结")
                assertNull("全程结束后乙书不得出现普通进度行", rowBFinal)
                // 全部断言通过后才写 PASS 结论证据（先取证后断言；结论不先于断言）
                recordEvidence("journey_success") {
                    put("status", "PASS")
                    put("returnOutcome", "AT_SEARCH")
                    put("steps", JSONObject(stepRecord as Map<*, *>))
                    put("baseline", baselineRow.toJson())
                    put("final", rowFinal?.toJson() ?: JSONObject.NULL)
                    put("rowB", rowBFinal?.toJson() ?: JSONObject.NULL)
                    put("not_covered", listOf(
                        "EXIT_SAVE_REQUEST_COMPLETION",
                        "ALL_PENDING_SAVES_DRAINED",
                        "NESTED_TEMPORARY_INSPECTION",
                        "LIST_SCROLL_OFFSET_PRESERVATION (LazyList offset 无 testTag/选中语义可观察)",
                    ))
                }
                markStep("journey_success")
            } else {
                // 落点为普通阅读器（或无法判定）：保存实际目的地、可见屏幕代理、完整树、
                // 持久行后如实失败 —— G1 待设备证明的疑似接线缺口，不重启、不改生产。
                val rowAtFail = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
                saveRoots("stage8_g1_back_to_normal_reader")
                recordUiState("stage8_g1_back_to_normal_reader") {
                    put("returnOutcome", outcome.name)
                    put("row", rowAtFail?.toJson() ?: JSONObject.NULL)
                    put("routeDirectlyObtainable", false)
                    put("routeNote", "NavController 由 Compose rememberNavController 持有，" +
                        "Activity 侧不可读；协调器为 Activity 作用域 hiltViewModel 同样不可直读；" +
                        "以可见屏幕代理标记（marker/返回按钮/搜索框计数）记录实际落点")
                }
                captureScreenshot("stage8_g1_back_to_normal_reader")
                recordEvidence("stage8_g1_assertion_failed") {
                    put("status", "FAIL")
                    put("expectedLanding", "AT_SEARCH（全局搜索页：搜索框+hit卡）")
                    put("actualLanding", outcome.name)
                    put("g1", "待设备证明的疑似接线缺口：源码推导临时提交压栈后 Back navigate 回" +
                        "普通阅读器（ReaderRoute.performTemporaryReturn），搜索页不可经 Back 直达；" +
                        "重组合/重注册无生命周期证据。交 Root 裁决，不弱化期望、不重启、不改生产")
                }
                error(
                    "G1 待设备证明：真实 Back 后落点为 $outcome 而非全局搜索页。" +
                        "完整取证已保存（stage8_g1_*）；期望不弱化，不改生产。",
                )
            }
        } catch (t: Throwable) {
            firstFailure = t
            // 首个异常先取证（类/消息/完整栈/当时步骤 + 完整树 + 截图），随后原样抛出；
            // 取证本身的失败 addSuppressed 到首错（不 runCatching 丢弃），并置位
            // evidenceCaptureFailed —— tearDown 将 BLOCKED 依赖清理，保留 fixture 与 DB 行。
            try {
                recordFirstFailure(t)
            } catch (rec: Throwable) {
                evidenceCaptureFailed = true
                t.addSuppressed(rec)
            }
            try {
                saveRoots("first_failure")
            } catch (rec: Throwable) {
                evidenceCaptureFailed = true
                t.addSuppressed(rec)
            }
            try {
                captureScreenshot("first_failure")
            } catch (rec: Throwable) {
                evidenceCaptureFailed = true
                t.addSuppressed(rec)
            }
            throw t
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 辅助
    // ────────────────────────────────────────────────────────────────────────

    fun markStep(name: String) {
        // 证据链基础设施：写入失败即失败（不「日志一条继续 PASS」）
        lastStep = name
        recordEvidence("step_markers") { put("marker", name) }
    }

    /**
     * 有界等待持久行稳定：连续两次采样（间隔覆盖防抖窗口）一致即返回。
     * 超时**不回退**输入行：先保存实测样本证据，再明确失败（未稳不得冒充稳定基线）。
     */
    fun stableProgressRow(
        after: J1RealReaderFixtures.ProgressRowSnapshot?,
        stage: String,
    ): J1RealReaderFixtures.ProgressRowSnapshot {
        val deadline = System.currentTimeMillis() + 8_000
        var last = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(600)
            val now = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            if (now != null && last != null &&
                now.updated_at == last.updated_at &&
                now.current_location_json == last.current_location_json
            ) {
                return now
            }
            last = now
        }
        recordEvidence("progress_row_unstable") {
            put("stage", stage)
            put("lastSample", last?.toJson() ?: JSONObject.NULL)
            put("inputRow", after?.toJson() ?: JSONObject.NULL)
        }
        error(
            "$stage: 进度行在窗口内未稳定（最后样本见 progress_row_unstable 证据）；" +
                "不得以旧输入行冒充稳定基线",
        )
    }

    /** 有界等待生产规则推导坐标连续 3 次一致（吸收任何重算/异步写点）。 */
    fun awaitStableDerived(
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
            same = if (latest.expectedCharOffset != null && latest.expectedCharOffset == last) {
                same + 1
            } else {
                1
            }
            last = latest.expectedCharOffset
            if (latest.expectedCharOffset != null && same >= 3) break
            Thread.sleep(400)
        }
        return latest
    }

    /** 阶段 8 返回落点观测结果。 */
    enum class ReturnOutcome { AT_SEARCH, AT_NORMAL_READER, UNKNOWN_TIMEOUT }

    /**
     * 有界等待真实 Back 后的实际落点：搜索页（可输入节点 + hit 卡）或普通阅读器
     * （甲书正文 marker 可见）。双信号轮询，先到者返回；超时取证后返回 UNKNOWN_TIMEOUT。
     */
    fun awaitReturnOutcome(expectedSnippet: String): ReturnOutcome {
        val deadline = System.currentTimeMillis() + 15_000
        var lastSearchSignal = false
        var lastReaderSignal = false
        while (System.currentTimeMillis() < deadline) {
            lastSearchSignal = composeRule.onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(contentCardMatcher(expectedSnippet))
                    .fetchSemanticsNodes().isNotEmpty()
            lastReaderSignal = markerCount(MARKER_A1) > 0
            if (lastSearchSignal) return ReturnOutcome.AT_SEARCH
            if (lastReaderSignal) return ReturnOutcome.AT_NORMAL_READER
            Thread.sleep(200)
        }
        recordEvidence("stage8_return_outcome_timeout") {
            put("searchSignal", lastSearchSignal)
            put("readerSignal", lastReaderSignal)
        }
        return ReturnOutcome.UNKNOWN_TIMEOUT
    }
}
