package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * 任务65-v3 集成修正版（源于 66-v1 [MdReadingResumeAcceptTest]，66 原件不覆盖）。
 *
 * ## 相对 66 原件的六项必改（逐条）
 *
 * 1. **所有权**：不再用 `title/original_file_name LIKE '%测试 MD%'` 认领书。
 *    改为「导入前快照全部未删书 id → 真实生产导入 → 导入后差集必须恰为 1 行 md 书」，
 *    该行即本轮创建物；不把同名的既有书籍当成本轮资产。
 * 2. **清理边界**：仪器内**不做任何递归删除**。本轮创建物（书行、内部正文副本、
 *    证据目录）全部保留并登记，由外层既有「隔离实例 + 卸载包」恢复合同处理；
 *    不新增清理框架。
 * 3. **退出/重开不吞异常**：返回、书架、卡片、重进等关键动作直接调用，失败即抛出；
 *    并额外断言「正文就绪标记消失（确已离开阅读器）」与「续读正文重新就绪」。
 * 4. **真断言代替 JSON 字段**：退出后断言持久偏移不回退、章节不倒退；
 *    续读后断言位置容差（远小于章长）**且章节一致**、所属段落标记可见、
 *    首章首段标记不可见；`monotonic` 不再只是写入 JSON。
 * 5. **保留原始 Throwable**：tearDown 收集全部异常，以 `AssertionError` 汇总并逐条
 *    `addSuppressed`，不只转字符串、不只保留第一条。
 * 6. **Hilt 合同实测**：qahost 变体 applicationId 后缀为 `.qahostj1persistv2`，
 *    而 `CraIsolatedTestRunner` 只对 `com.creationreadingassistant.qahost` 走隔离分支；
 *    本变体走「清单 Application 透传」（生产 `App`）。仓库现无 `HiltTestApplication`
 *    或 `CustomTestApplication`，因此 `@HiltAndroidTest`/`HiltAndroidRule` 的测试组件
 *    并不成立。本类按既有启动合同（不切 runner / 不换 Application）改用
 *    **生产类公开构造器**组装 [ShelfImporter]（与 D2RelinkPersistenceAcceptTest 同一做法），
 *    导入仍走同一条生产 `importFiles` 实现。
 *
 * ## 任务65-v4 修正（只修入口，不动其余断言）
 *
 * 65-v3 设备日志（logs/09-md-instrument.log）在 `mdr_10_open` 失败：
 * 启动后直接等 `book-tile-<id>`，但生产 `NavHost.startDestination = Home`
 * （AppNavigation.kt:401），书架卡片在启动页不可见。
 *
 * 本版改为显式导航并逐步证明页面：
 * 启动 → 证明所在顶层页面（`home-screen`/`shelf-screen` testTag）
 * → 若在首页，点底部/侧边导航项「书架」（用 `hasText("书架") and isSelectable()`
 * 精确命中 Role.Tab；**不能**用 `onNodeWithText("书架")`，书架页标题本身
 * clickable 会打开整理器）→ 证明 `shelf-screen` → 等本轮 `book-tile-<id>`
 * → 点击进入阅读器 → 断言正文就绪 **且** 本轮夹具章节标记可见（正文身份）。
 *
 * 未增加等待时长、未放宽断言、未使用模糊匹配；失败仍然直接抛出。
 *
 * ## 任务69-v1-A 修正（只动位移判定与失败取证）
 *
 * 68-v1 失败：MD 导入/打开通过，`offsetOnOpen=-1`；8 次 `swipeUp()` 后等待谓词
 * `now != null && (offsetBefore == null || now > offsetBefore + MIN_DELTA_CHARS)`
 * 在 `offsetBefore == null`、`now == 0` 时提前成立，跳过「下一章」兜底；随后严格断言
 * 以 0 为基准拒绝 delta=0，失败于 542 行。
 *
 * 本版：
 * 1. 判定统一为 [strictDisplacementExceeded]（起点 null 视作 0，必须严格超过 1200）；
 * 2. 先识别真实阅读模式：`pagerEngineMode` 默认 "on"，Markdown 的 paged source 是
 *    `MarkdownChapterSource`，默认走分页；分页用水平滑动，滚动用可滚动节点上的纵向滑动，
 *    不再对分页正文发无效的 `swipeUp()`；
 * 3. 等待同时要求持久 offset 超过门槛 **且** 可见正文标记位置前移超过门槛，互证
 *    「未发生阅读位移」与「已位移但保存未形成」；
 * 4. 位移失败先写 `mdr_20_displace` 失败证据（初始/最终进度、实际动作、UI 位置、失败阶段、
 *    归类），再抛原断言；证据写失败只作为 suppressed，不覆盖原错。
 *
 * 退出、重开、续读的严格断言保持不变。
 */
@RunWith(AndroidJUnit4::class)
class MdReadingResumeAcceptTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private lateinit var importer: ShelfImporter
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /** 只登记本轮创建物；不删除，保留给外层隔离合同。 */
    private val createdAssets = JSONArray()

    private var lastPhase: String = "start"

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private fun fixtureFile(): File =
        File(context.getExternalFilesDir(null), "mdr-fixtures/$FIXTURE_NAME").apply {
            parentFile?.mkdirs()
        }

    private fun marker(chapter: Int, paragraph: Int): String =
        "第${chapter}章第${paragraph}段中性标记"

    /**
     * 8 个 H1 章节，每章 40 段，约 3.5 万字符——保证：
     * - 真实滚动能产生远超判别门槛的持久位移；
     * - 续读容差（600）远小于章长（约 4400），"退回起点"不可能通过。
     */
    private fun mdBody(): String = buildString {
        (1..CHAPTER_COUNT).forEach { ch ->
            appendLine("# 第 $ch 章")
            appendLine()
            (1..PARAGRAPH_PER_CHAPTER).forEach { p ->
                appendLine(
                    "${marker(ch, p)}：这是用于真实阅读位移与续读验收的中性正文段落，" +
                        "重复若干次以保证本章具有足够的可滚动长度，不涉及任何真实作品内容。"
                )
                appendLine()
            }
        }
    }

    // ── 生产同源解析 ─────────────────────────────────────────────────────────

    private data class ChapterRow(val index: Int, val title: String, val startOffset: Int, val charCount: Int)

    private data class ParsedDoc(val rows: List<ChapterRow>, val markerOffsets: Map<String, Int>, val path: String)

    private fun parseFixture(): ParsedDoc {
        val file = fixtureFile()
        check(file.isFile) { "夹具文件缺失：${file.absolutePath}" }
        val result = TextStreamLoader(context.cacheDir).load(
            context = context,
            uri = Uri.fromFile(file),
            reportedSize = file.length(),
        )
        val doc = if (result.isStreaming) {
            MarkdownDocument.fromFileIndex(result.sourceFile!!, result.fileIndex!!)
        } else {
            MarkdownDocument(result.fullText ?: "")
        }
        return try {
            val rows = doc.chapters.map { ChapterRow(it.index, it.title, it.startOffset, it.charCount) }
            val offsets = LinkedHashMap<String, Int>()
            rows.forEach { r ->
                val body = doc.text(r.index)
                (1..PARAGRAPH_PER_CHAPTER).forEach { p ->
                    val m = marker(r.index + 1, p)
                    val local = body.indexOf(m)
                    if (local >= 0) offsets[m] = r.startOffset + local
                }
            }
            ParsedDoc(
                rows = rows,
                markerOffsets = offsets,
                path = if (result.isStreaming) "streamingFileIndex" else "wholeDocumentText",
            )
        } finally {
            doc.close()
        }
    }

    private fun chapterIndexForOffset(rows: List<ChapterRow>, offset: Int): Int =
        rows.indexOfLast { it.startOffset <= offset }.coerceAtLeast(0)

    private fun owningMarker(parsed: ParsedDoc, offset: Int): String? =
        parsed.markerOffsets.entries.filter { it.value <= offset }.maxByOrNull { it.value }?.key

    // ── 真实持久进度读取 ─────────────────────────────────────────────────────

    private fun rawOne(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private fun persistedOffset(bookId: String): Int? {
        val json = rawOne(
            "SELECT COALESCE(current_location_json,'') FROM reading_progress " +
                "WHERE book_id='$bookId' AND deleted_at IS NULL"
        )
        if (json.isNullOrBlank()) return null
        return Regex("\"offset\":(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun activeBookIds(): Set<String> {
        val ids = LinkedHashSet<String>()
        db.openHelper.readableDatabase
            .query("SELECT id FROM books WHERE deleted_at IS NULL")
            .use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        return ids
    }

    private fun waitFor(phase: String, timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var observed = false
        while (System.currentTimeMillis() < deadline) {
            observed = try {
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

    private fun readerReady(): Boolean =
        composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isNotEmpty()

    private fun readerGone(): Boolean =
        composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isEmpty()

    private fun textVisible(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    // ── 顶层导航入口（65-v4） ────────────────────────────────────────────────
    //
    // 原失败（logs/09-md-instrument.log）：启动后直接等 `book-tile-<id>`，
    // 但生产 NavHost 的 startDestination 是 Home（AppNavigation.kt:401），
    // 书架卡片在启动页根本不可见。修正不是"多等一会"，而是显式执行
    // 首页 → 书架 的真实导航，并在每一步证明所在页面。
    //
    // 注意：书架页的标题「书架」本身是 clickable（打开整理器，ShelfScreen.kt:169），
    // 因此**不能**用 onNodeWithText("书架") 盲点；必须用底部/侧边导航项独有的
    // selectable 语义（Role.Tab）定位。

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun clickTopLevelTab(label: String) {
        composeRule.onNode(hasText(label) and isSelectable()).performClick()
        composeRule.waitForIdle()
    }

    /**
     * 确保停在书架：先证明当前顶层页面，再按需点导航项，最后证明书架页出现。
     * 返回 false 表示未能证明到达书架（调用方断言失败，不静默继续）。
     */
    private fun ensureOnShelf(phase: String): Boolean {
        val appeared = waitFor(phase, UI_TIMEOUT_MS, "首页或书架页面出现") { topLevelPage() != null }
        if (!appeared) return false
        val startPage = topLevelPage()
        if (startPage == "shelf") return true
        clickTopLevelTab("书架")
        return waitFor(phase, UI_TIMEOUT_MS, "书架页面出现") { topLevelPage() == "shelf" }
    }

    /**
     * 入口失败时的**有界**诊断：记录检测到的页面、关键节点计数与截断的语义树。
     * 只在失败时写，成功路径零开销；不改变断言结论、不吞异常。
     */
    private fun writeEntryDiagnostics(phase: String, bookId: String) {
        val details = linkedMapOf<String, Any?>(
            "detectedPage" to (topLevelPage() ?: "<none>"),
            "homeScreenNodes" to composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().size,
            "shelfScreenNodes" to composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().size,
            "bookTileNodes" to composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().size,
            "selectableNodes" to composeRule.onAllNodes(isSelectable()).fetchSemanticsNodes().size,
            "homeTabVisible" to composeRule.onAllNodes(hasText("首页") and isSelectable())
                .fetchSemanticsNodes().isNotEmpty(),
            "shelfTabVisible" to composeRule.onAllNodes(hasText("书架") and isSelectable())
                .fetchSemanticsNodes().isNotEmpty(),
            "semanticsTreeHead" to runCatching {
                composeRule.onRoot().printToString(maxDepth = 5).take(4000)
            }.getOrElse { "<unavailable: $it>" },
        )
        writeEvidence(phase, false, details)
    }

    private fun shelfEntryProven(phase: String, bookId: String): Boolean {
        val ok = ensureOnShelf(phase)
        if (!ok) writeEntryDiagnostics(phase, bookId)
        return ok
    }

    /**
     * 正文身份：本轮夹具的章节标记是否出现在当前语义树里。
     * 只抽样少量段落（每章首段 + 首章中后段），避免每次轮询扫全量标记。
     */
    private fun anyBookMarkerVisible(parsed: ParsedDoc): Boolean {
        val samples = buildList {
            (1..parsed.rows.size).take(4).forEach { ch -> add(marker(ch, 1)) }
            add(marker(1, 20)); add(marker(1, PARAGRAPH_PER_CHAPTER))
        }
        return samples.any { textVisible(it) }
    }

    // ── 69-v1-A：真实阅读模式 / 触摸目标 / 正文位置互证 ──────────────────────

    /**
     * 真实阅读模式：`pagerEngineMode` 默认 "on"，Markdown 的 paged source 是
     * `MarkdownChapterSource`，因此默认走分页；滚动分支只在分页引擎关闭时出现。
     * 手势必须匹配模式，否则「没翻页」会被误当成「没保存」。
     */
    private fun pagedReaderActive(): Boolean =
        composeRule.onAllNodesWithContentDescription("分页正文已就绪").fetchSemanticsNodes().isNotEmpty()

    /** 滚动模式下的真实可滚动节点；优先于根节点，避免把触摸发到系统手势区。 */
    private fun scrollableNode() =
        composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            .takeIf { it.isNotEmpty() }
            ?.let { composeRule.onAllNodes(hasScrollAction()).onFirst() }

    /**
     * UI 可观察位置：当前可见正文节点里出现的最大夹具标记 offset。
     * 只读语义树，不写数据库；用于与持久 offset 互证。
     */
    private fun visibleMarkerPosition(parsed: ParsedDoc): Pair<String, Int>? {
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("\n") { it.text }
            }
        if (texts.isEmpty()) return null
        val joined = texts.joinToString("\n")
        return parsed.markerOffsets.entries
            .filter { joined.contains(it.key) }
            .maxByOrNull { it.value }
            ?.let { it.key to it.value }
    }

    /** 按真实模式执行一次阅读推进；返回实际触摸目标，供证据记录。 */
    private fun performReadingAdvance(paged: Boolean): String {
        if (paged) {
            composeRule.onRoot().performTouchInput {
                swipe(
                    start = Offset(width * 0.82f, centerY),
                    end = Offset(width * 0.18f, centerY),
                    durationMillis = 300,
                )
            }
            composeRule.waitForIdle()
            return "root-horizontal-swipe"
        }
        val scrollable = scrollableNode()
        if (scrollable != null) {
            scrollable.performTouchInput {
                val h = height.toFloat()
                swipe(
                    start = Offset(centerX, h * 0.82f),
                    end = Offset(centerX, h * 0.18f),
                    durationMillis = 300,
                )
            }
            composeRule.waitForIdle()
            return "scrollable-node-vertical-swipe"
        }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        return "root-vertical-swipe"
    }

    // ── 证据 ─────────────────────────────────────────────────────────────────

    private fun writeEvidence(phase: String, ok: Boolean, details: Map<String, Any?>, screenshot: Boolean = true) {
        lastPhase = phase
        val obj = JSONObject().put("phase", phase).put("pass", ok)
            .put("at", Instant.now().toString())
            .put("runId", runId)
            .put("pid", Process.myPid())
            .put("packageName", context.packageName)
        details.forEach { (k, v) -> obj.put(k, v?.toString() ?: "") }
        File(resultsDir, "$phase.json").writeText(obj.toString(2))
        if (screenshot) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?.let { bmp: Bitmap ->
                        File(resultsDir, "$phase.png").outputStream()
                            .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle()
                    }
            }
        }
        android.util.Log.i(TAG, "$phase pass=$ok ${obj.toString(0)}")
    }

    private fun recordAsset(kind: String, path: String, reason: String) {
        createdAssets.put(JSONObject().put("kind", kind).put("path", path).put("retainReason", reason))
    }

    // ── 生产导入器的公开构造（无 Hilt 测试组件） ─────────────────────────────

    private object RealFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }

    private fun buildImporter(): ShelfImporter {
        val bookDao = db.bookDao()
        val contentDao = db.bookContentDao()
        val fileDao = db.bookFileDao()
        val progressDao = db.readingProgressDao()
        val repository = BookRepository(
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            progressDao = progressDao,
            sessionDao = db.readingSessionDao(),
            noteDao = db.noteDao(),
            highlightDao = db.highlightDao(),
            inspirationDao = db.inspirationDao(),
            bookTagDao = db.bookTagDao(),
            bookCategoryDao = db.bookCategoryDao(),
            shelfBookDao = db.shelfBookDao(),
            chapterReadDao = db.chapterReadDao(),
            context = context,
        )
        val epubRepository = EpubRepository(
            context = context,
            bookDao = bookDao,
            readingProgressDao = progressDao,
            ioDispatcher = Dispatchers.IO,
            fileOps = RealFileOps,
        )
        return ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            epubRepository = epubRepository,
            importHistoryStore = ImportHistoryStore(context, CoroutineScope(Dispatchers.IO)),
            sourceIndex = LibrarySourceIndex(db.librarySourceRefDao()),
            rootStore = LibraryRootStore(context, CoroutineScope(Dispatchers.IO)),
            ioDispatcher = Dispatchers.IO,
            indexRefreshCoordinator = mockk<SearchIndexBookRefreshCoordinator>(relaxed = true),
        )
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = "mdr-" + java.util.UUID.randomUUID().toString().take(8)
        resultsDir = File(context.getExternalFilesDir(null), "mdr-results-$runId").apply { mkdirs() }
        recordAsset("dir", resultsDir.absolutePath, "本轮阶段证据（JSON+截图），验收后原样保留")
        db = J1RealReaderFixtures.openDatabase(context)
        importer = buildImporter()
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
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
        attempt { J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded) }
        attempt {
            File(resultsDir, "mdr_99_assets.json").writeText(
                JSONObject().put("runId", runId)
                    .put("lastPhase", lastPhase)
                    .put("policy", "仪器内不删除本轮数据；由外层隔离实例+卸载包恢复合同处理")
                    .put("assets", createdAssets)
                    .toString(2)
            )
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
    fun mdr_fullJourney_import_open_displace_exit_resume() = runBlocking {
        // ── 阶段 0：真实生产导入（所有权 = 导入前后差集） ──────────────────
        val file = fixtureFile()
        file.writeText(mdBody())
        recordAsset("file", file.absolutePath, "中性 MD 夹具（导入源副本），保留供字节核对")
        val bodySha = J1RealReaderFixtures.sha256(file.readText())

        val beforeIds = activeBookIds()
        importer.importFiles(listOf(Uri.fromFile(file)), IMPORT_SOURCE_LABEL)
        val imported = waitFor("mdr_00_import", IMPORT_TIMEOUT_MS, "导入后恰好多出一行 md 书") {
            val added = activeBookIds() - beforeIds
            added.size == 1 && rawOne(
                "SELECT format FROM books WHERE id='${added.first()}' AND deleted_at IS NULL"
            ) == "md"
        }
        assertTrue("真实生产导入必须且只能新增一行本轮 md 书（导入前 ${beforeIds.size} 行）", imported)

        val newIds = activeBookIds() - beforeIds
        assertTrue("导入差集必须恰为 1 行，实得 ${newIds.size}", newIds.size == 1)
        val bookId = newIds.first()
        recordAsset("book", bookId, "本轮生产导入创建的书行；不删除，交外层隔离恢复")

        val contentPath = rawOne("SELECT COALESCE(local_content_path,'') FROM books WHERE id='$bookId'") ?: ""
        val status = rawOne("SELECT COALESCE(content_status,'') FROM books WHERE id='$bookId'") ?: ""
        val importedBytes = File(contentPath).takeIf { it.isFile }?.length() ?: -1L
        assertTrue("导入必须保存本地正文副本且非空", importedBytes > 0)
        assertTrue("导入状态必须为 available，实得 $status", status == "available")
        recordAsset("dir", File(context.filesDir, "books/$bookId").absolutePath,
            "生产导入的正文副本；仪器内不递归删除")
        writeEvidence(
            "mdr_00_import", true,
            mapOf(
                "entry" to "ShelfImporter.importFiles（生产公开构造，与 ShelfRoute.onSelectFiles 同一实现）",
                "ownership" to "导入前 activeBookIds 快照 → 导入后差集唯一",
                "preImportActiveBooks" to beforeIds.size,
                "bookId" to bookId,
                "fixtureSha256" to bodySha,
                "importedBytes" to importedBytes,
                "contentPath" to contentPath,
                "contentStatus" to status,
                "runnerContract" to RUNNER_CONTRACT_NOTE,
            )
        )

        // ── 阶段 1：真实打开（首页 → 书架 → 本轮书卡 → 阅读器） ─────────────
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue(
            "启动后必须能证明所在顶层页面（首页/书架）",
            waitFor("mdr_10_open", UI_TIMEOUT_MS, "顶层页面出现") { topLevelPage() != null }
        )
        val pageBeforeNav = topLevelPage()
        assertTrue("必须能显式进入书架（当前页 ${pageBeforeNav ?: "<none>"}）", shelfEntryProven("mdr_10_open", bookId))
        assertTrue("书架必须出现本轮书籍卡片", waitFor("mdr_10_open", UI_TIMEOUT_MS, "书架卡片出现") {
            composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        composeRule.waitForIdle()
        assertTrue("阅读器正文必须真实就绪", waitFor("mdr_10_open", UI_TIMEOUT_MS, "阅读正文已就绪") {
            readerReady()
        })
        val parsed = parseFixture()
        assertTrue(
            "阅读器必须显示本轮导入书籍的正文（任一章节标记可见）",
            waitFor("mdr_10_open", UI_TIMEOUT_MS, "本轮书籍正文标记") { anyBookMarkerVisible(parsed) }
        )
        writeEvidence(
            "mdr_10_open", true,
            mapOf(
                "entry" to "MainActivity → 首页/书架导航项 → 书架 → book-tile-$bookId（真实 UI 阅读入口）",
                "pageAfterLaunch" to (pageBeforeNav ?: "<none>"),
                "navigationPerformed" to (pageBeforeNav != "shelf"),
                "pageAfterNavigation" to (topLevelPage() ?: "<none>"),
                "bookId" to bookId,
                "bodyIdentityProven" to true,
                "parsePath" to parsed.path,
                "chapterCount" to parsed.rows.size,
                "markerCount" to parsed.markerOffsets.size,
                "offsetOnOpen" to (persistedOffset(bookId) ?: -1),
            )
        )

        // ── 阶段 2：真实阅读位移 ─────────────────────────────────────────────
        // 真实模式：pagerEngineMode 默认 "on"，Markdown 走 MarkdownChapterSource 分页；
        // 滚动分支只在分页引擎关闭时出现。手势必须匹配当前模式，否则「没翻页」会被
        // 误当成「没保存」。等待谓词统一为 strictDisplacementExceeded（真实超过门槛），
        // 并以正文标记位置与持久 offset 互证。
        val pagedMode = pagedReaderActive()
        val offsetBefore = persistedOffset(bookId)
        val baseOffset = offsetBefore ?: 0
        val uiBefore = visibleMarkerPosition(parsed)
        var touchTarget = "unset"
        repeat(SWIPE_COUNT) {
            touchTarget = performReadingAdvance(pagedMode)
            Thread.sleep(SWIPE_GAP_MS)
        }

        fun displacementReached(): Boolean {
            val now = persistedOffset(bookId)
            if (!strictDisplacementExceeded(offsetBefore, now, MIN_DELTA_CHARS)) return false
            // UI 互证：可观察时，正文标记位置也必须真实前移超过门槛。
            val uiNow = visibleMarkerPosition(parsed)
            return uiBefore == null || (uiNow != null && uiNow.second - uiBefore.second > MIN_DELTA_CHARS)
        }

        var moved = waitFor("mdr_20_displace", SAVE_SETTLE_TIMEOUT_MS,
            "分页/滚动后持久进度真实超过门槛且正文位置互证") { displacementReached() }
        var displacementSource = if (pagedMode) "pagedSwipe" else "scrollSwipe"
        var fallbackState = "not-needed"
        if (!moved) {
            val nextChapterNodes =
                composeRule.onAllNodesWithContentDescription("下一章").fetchSemanticsNodes()
            if (nextChapterNodes.isNotEmpty()) {
                fallbackState = "clicked"
                composeRule.onNodeWithContentDescription("下一章").performClick()
                composeRule.waitForIdle()
                moved = waitFor("mdr_20_displace", SAVE_SETTLE_TIMEOUT_MS,
                    "章切换后持久进度真实超过门槛且正文位置互证") { displacementReached() }
                displacementSource = "nextChapterButton"
            } else {
                fallbackState = "next-chapter-not-visible"
            }
        }

        val offsetAfterRead = persistedOffset(bookId)
        val uiAfter = visibleMarkerPosition(parsed)
        val persistedDelta = offsetAfterRead?.minus(baseOffset)
        val uiDelta = if (uiBefore != null && uiAfter != null) uiAfter.second - uiBefore.second else null

        if (!moved) {
            // 失败证据先落盘；证据写失败不得覆盖原断言错误。
            val evidenceError = runCatching {
                writeEvidence(
                    "mdr_20_displace", false,
                    mapOf(
                        "failureStage" to "mdr_20_displace",
                        "readingMode" to if (pagedMode) "paged" else "scroll",
                        "touchTarget" to touchTarget,
                        "swipeCount" to SWIPE_COUNT,
                        "nextChapterFallback" to fallbackState,
                        "offsetBefore" to (offsetBefore ?: -1),
                        "offsetAfterRead" to (offsetAfterRead ?: -1),
                        "persistedDelta" to (persistedDelta ?: -1),
                        "uiBeforeMarker" to (uiBefore?.first ?: "<none>"),
                        "uiBeforeOffset" to (uiBefore?.second ?: -1),
                        "uiAfterMarker" to (uiAfter?.first ?: "<none>"),
                        "uiAfterOffset" to (uiAfter?.second ?: -1),
                        "uiDelta" to (uiDelta ?: -1),
                        "classification" to classifyDisplacementFailure(
                            uiBefore?.second, uiAfter?.second, persistedDelta, MIN_DELTA_CHARS),
                        "minDeltaChars" to MIN_DELTA_CHARS,
                        "note" to "失败证据先落盘；断言原文随后抛出，证据写失败不覆盖原错",
                    )
                )
            }.exceptionOrNull()
            val failure = AssertionError(
                "真实阅读必须产生可核的持久位移（实得 $offsetAfterRead，起始 $offsetBefore，" +
                    "模式 ${if (pagedMode) "paged" else "scroll"}，动作 $touchTarget）")
            if (evidenceError != null) failure.addSuppressed(evidenceError)
            throw failure
        }
        val offsetAfterReadValue = offsetAfterRead ?: 0
        assertTrue(
            "位移必须超过判别门槛 $MIN_DELTA_CHARS，实得 ${offsetAfterReadValue - baseOffset}",
            offsetAfterReadValue - baseOffset > MIN_DELTA_CHARS)
        val chapterAfterRead = chapterIndexForOffset(parsed.rows, offsetAfterReadValue)
        writeEvidence(
            "mdr_20_displace", true,
            mapOf(
                "displacementSource" to displacementSource,
                "readingMode" to if (pagedMode) "paged" else "scroll",
                "touchTarget" to touchTarget,
                "nextChapterFallback" to fallbackState,
                "offsetBefore" to (offsetBefore ?: -1),
                "offsetAfterRead" to offsetAfterReadValue,
                "persistedDelta" to (offsetAfterReadValue - baseOffset),
                "uiBeforeMarker" to (uiBefore?.first ?: "<none>"),
                "uiBeforeOffset" to (uiBefore?.second ?: -1),
                "uiAfterMarker" to (uiAfter?.first ?: "<none>"),
                "uiAfterOffset" to (uiAfter?.second ?: -1),
                "uiDelta" to (uiDelta ?: -1),
                "chapterIndexAfterRead" to chapterAfterRead,
                "chapterTitleAfterRead" to parsed.rows[chapterAfterRead].title,
                "minDeltaChars" to MIN_DELTA_CHARS,
                "note" to "位移来自真实分页滑动/滚动/章切换；未调用保存 helper、未直写进度",
            )
        )

        // ── 阶段 3：退出（真实返回；不吞异常，并证明已离开阅读器） ──────────
        // 生产 reducer 在 PageTurn 后把 controlsVisible 置 false，顶栏「返回」随之离开语义树。
        // 真实用户入口：中央轻点唤出工具栏，再点击已确认的返回节点；仍不可见才走生产系统返回。
        val offsetBeforeExit = persistedOffset(bookId)
        val exitAttempt = ReaderChromeExitSupport.exitViaBack(composeRule)
        if (exitAttempt.failure != null) {
            val failure = exitAttempt.failure
            runCatching {
                writeEvidence(
                    "mdr_30_exit", false,
                    exitAttempt.diagnostics() + linkedMapOf(
                        "bookId" to bookId,
                        "readerReady" to readerReady(),
                        "readingMode" to if (pagedMode) "paged" else "scroll",
                        "offsetBeforeExit" to (offsetBeforeExit ?: -1),
                        "chapterBeforeExit" to chapterIndexForOffset(parsed.rows, offsetBeforeExit ?: 0),
                        "originalException" to failure.toString(),
                    )
                )
            }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
        composeRule.waitForIdle()
        // 73-v1-B：动作已发出但页面仍在时，先留失败证据再断言（成功记录仍在断言之后）。
        val leftReader = waitFor("mdr_30_exit", UI_TIMEOUT_MS, "离开阅读器") { readerGone() }
        if (!leftReader) {
            writeEvidence(
                "mdr_30_exit", false,
                exitAttempt.diagnostics() + mapOf(
                    "bookId" to bookId,
                    "readerReady" to readerReady(),
                    "readingMode" to if (pagedMode) "paged" else "scroll",
                    "offsetBeforeExit" to (offsetBeforeExit ?: -1),
                    "chapterBeforeExit" to chapterIndexForOffset(parsed.rows, offsetBeforeExit ?: 0),
                    "originalException" to "退出动作已发出但阅读页面仍在（未离开阅读器）",
                )
            )
        }
        assertTrue("点击返回后必须真实离开阅读器（正文就绪标记消失）", leftReader)
        val offsetAfterExit = persistedOffset(bookId)
        assertTrue("退出后持久进度必须仍在（不因离开清零）", offsetAfterExit != null && offsetAfterExit > 0)
        assertTrue("退出不得把位置退回起点（offset=$offsetAfterExit 必须超过门槛 $MIN_DELTA_CHARS）",
            offsetAfterExit!! > MIN_DELTA_CHARS)
        val chapterAfterExit = chapterIndexForOffset(parsed.rows, offsetAfterExit)
        assertTrue("退出落盘不得倒退到更早章节（阅读时 $chapterAfterRead，退出后 $chapterAfterExit）",
            chapterAfterExit >= chapterAfterRead)
        assertTrue("退出落盘与阅读时的偏差必须小于 $RESUME_TOLERANCE_CHARS（阅读时 $offsetAfterRead，退出后 $offsetAfterExit）",
            offsetAfterExit >= offsetAfterReadValue - RESUME_TOLERANCE_CHARS)
        writeEvidence(
            "mdr_30_exit", true,
            mapOf(
                "exitEntry" to exitAttempt.entryUsed,
                "exitEntryDetail" to "中央轻点唤出工具栏后点击生产返回；仍不可见才走生产系统返回",
                "backVisibleBeforeExit" to exitAttempt.backVisibleBefore,
                "centerTapPerformed" to exitAttempt.centerTapPerformed,
                "systemBackUsed" to exitAttempt.systemBackUsed,
                "leftReaderProven" to true,
                "offsetAfterRead" to offsetAfterReadValue,
                "offsetAfterExit" to offsetAfterExit,
                "chapterAfterRead" to chapterAfterRead,
                "chapterAfterExit" to chapterAfterExit,
                "assertions" to "非零、不回起点、不倒退章节、偏差<=容差（均为真断言）",
            )
        )

        // ── 阶段 4：续读（重进同一本书；不吞异常） ──────────────────────────
        // 退出后可能落在书架（正常）或首页；统一走"证明页面 → 按需导航 → 再证明"。
        // 不盲点文本「书架」：书架页标题本身 clickable，会误开整理器。
        val pageAfterExit = topLevelPage()
        assertTrue("退出后必须能回到书架（当前页 ${pageAfterExit ?: "<none>"}）",
            shelfEntryProven("mdr_40_resume", bookId))
        assertTrue("退出后必须能回到书架并看到本轮书籍",
            waitFor("mdr_40_resume", UI_TIMEOUT_MS, "书架卡片出现") {
                composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
            })
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        composeRule.waitForIdle()
        assertTrue("必须能重新进入本书并就绪", waitFor("mdr_40_resume", UI_TIMEOUT_MS, "续读正文就绪") {
            readerReady()
        })
        val offsetAfterResume = persistedOffset(bookId)
        assertTrue(
            "续读后持久记录必须保留同一位置（退出前 $offsetAfterExit，续读后 $offsetAfterResume，容差 $RESUME_TOLERANCE_CHARS）",
            offsetAfterResume != null &&
                kotlin.math.abs(offsetAfterResume - offsetAfterExit) <= RESUME_TOLERANCE_CHARS
        )
        val resumeChapter = chapterIndexForOffset(parsed.rows, offsetAfterResume!!)
        assertTrue("续读章节必须与退出章节一致（退出 $chapterAfterExit，续读 $resumeChapter）",
            resumeChapter == chapterAfterExit)

        val expectedMarker = owningMarker(parsed, offsetAfterResume)
        val markerVisible = expectedMarker != null && textVisible(expectedMarker)
        val firstChapterMarkerVisible = textVisible(marker(1, 1))
        assertTrue("续读必须停在持久位置所属正文（期望标记 $expectedMarker 可见）",
            expectedMarker != null && markerVisible)
        assertTrue("续读不得退回开篇（第 1 章第 1 段不应可见，当前章 index=$resumeChapter）",
            resumeChapter == 0 || !firstChapterMarkerVisible)
        writeEvidence(
            "mdr_40_resume", true,
            mapOf(
                "entry" to "退出阅读器 → 证明页面 → 按需导航到书架 → book-tile-$bookId",
                "pageAfterExit" to (pageAfterExit ?: "<none>"),
                "navigationPerformed" to (pageAfterExit != "shelf"),
                "offsetAfterExit" to offsetAfterExit,
                "offsetAfterResume" to offsetAfterResume,
                "resumeChapterIndex" to resumeChapter,
                "resumeChapterTitle" to parsed.rows[resumeChapter].title,
                "expectedOwningMarker" to expectedMarker,
                "owningMarkerVisible" to markerVisible,
                "firstChapterMarkerVisible" to firstChapterMarkerVisible,
                "note" to "位置容差、章节一致、正文身份三项同时断言；未预填进度",
            )
        )

        // ── 阶段 5：终态记录（不删除） ──────────────────────────────────────
        writeEvidence(
            "mdr_90_finalState", true,
            mapOf(
                "bookId" to bookId,
                "booksActive" to rawOne("SELECT COUNT(*) FROM books WHERE id='$bookId' AND deleted_at IS NULL"),
                "progressRows" to rawOne("SELECT COUNT(*) FROM reading_progress WHERE book_id='$bookId' AND deleted_at IS NULL"),
                "highlights" to rawOne("SELECT COUNT(*) FROM highlights WHERE book_id='$bookId' AND deleted_at IS NULL"),
                "notes" to rawOne("SELECT COUNT(*) FROM notes WHERE book_id='$bookId' AND deleted_at IS NULL"),
                "retainedAssetCount" to createdAssets.length(),
                "deletionInInstrument" to false,
            ),
            screenshot = false
        )
    }

    private companion object {
        const val TAG = "MdReadingResumeAccept"

        /** 中性夹具文件名（"测试 MD"），导入后书名即取自文件名。 */
        const val FIXTURE_NAME = "测试 MD.md"

        const val IMPORT_SOURCE_LABEL = "任务65-v3 仪器导入"

        const val CHAPTER_COUNT = 8
        const val PARAGRAPH_PER_CHAPTER = 40

        /** 位移判别门槛：小于该值无法区分"真实阅读前进"与"规范化清零"。 */
        const val MIN_DELTA_CHARS = 1200

        /** 续读/退出偏差容差：远小于单章长度（约 4400 字符），退到起点不可能通过。 */
        const val RESUME_TOLERANCE_CHARS = 600

        const val SWIPE_COUNT = 8
        const val SWIPE_GAP_MS = 250L
        const val POLL_INTERVAL_MS = 300L

        const val IMPORT_TIMEOUT_MS = 30_000L
        const val UI_TIMEOUT_MS = 20_000L
        const val SETTLE_TIMEOUT_MS = 10_000L
        const val SAVE_SETTLE_TIMEOUT_MS = SETTLE_TIMEOUT_MS

        /**
         * runner/Application/Hilt 合同（源码实读，2026-10-20）：
         * - qahost applicationIdSuffix = ".qahostj1persistv2"（app/build.gradle.kts:119）；
         * - CraIsolatedTestRunner.QA_HOST_PACKAGE = "com.creationreadingassistant.qahost"；
         *   ⇒ 本变体目标包不等于该常量，runner 走「清单 Application 透传」；
         * - CraIsolatedTestApplication 为裸 Application，且本变体不经过隔离分支；
         * - 仓库无 HiltTestApplication/CustomTestApplication ⇒ Hilt 测试组件不成立。
         */
        const val RUNNER_CONTRACT_NOTE =
            "target=com.creationreadingassistant.qahostj1persistv2 != runner.QA_HOST_PACKAGE(.qahost) " +
                "→ 清单 Application 透传；无 HiltTestApplication ⇒ 不依赖 Hilt 测试注入，" +
                "ShelfImporter 走生产公开构造器"
    }
}
