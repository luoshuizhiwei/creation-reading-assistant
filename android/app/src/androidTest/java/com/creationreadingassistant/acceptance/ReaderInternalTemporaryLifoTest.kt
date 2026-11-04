package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 191-v1：reader 内临时查阅 LIFO 的真实 UI 验收（新独立测试文件；修正 S0 真实保存判据）。
 *
 * ## 191-v1 修复清单（修正 S0 保存判据与真实落地合同 2026-10-29）：
 * 1. 修正 S0 真实保存判据（消除未经证明的 `< offset1` 严格小于假设）：
 *    - 种子行在启动前即写入（legacyOffset == contentA.offset1 = 4014，入参 target 位点本身）；
 *    - 依据生产实际写入规则（TxtPagedController.currentPageStartAbs、BookRepository.saveProgress、mergeReaderProgress），
 *      以及 J1RealReaderFixtures.assertObservedPersistedLocation 合同，真实页首偏移可能恰好对齐到 offset1，
 *      因此合法范围为 legacyOffset > 0 && legacyOffset <= contentA.offset1；
 *    - 彻底排除未落库种子行冒充 S0 的真实依据是：
 *      a) updated_at 严格晚于种子行（生产落库生成最新 UTC 墙钟时间戳）；
 *      b) current_location_json 实质变更（生产 locator 去除预写种子中的 globalOffset 字段并规范化）；
 *      c) legacyOffset > 0 && legacyOffset <= contentA.offset1（符合生产持久化范围）；
 *      d) 屏幕视觉渲染 contentA.marker1 锁定当前页包含目标；
 *    - 杜绝在 Compose 刚启动、真实保存尚未到达前直接接受预写种子行冒充 S0。
 * 2. 保留已接受的全部强断言：
 *    - awaitTemporaryLayer 与 awaitFinalOrdinaryLayer 彻底分离；
 *    - 临时层精确正文正断言、对立目标与普通 marker1 负断言、返回按钮在屏；
 *    - 最终普通正文正断言、两临时目标负断言、返回按钮消失；
 *    - 卡片局部语义祖先绑定（hasAnyAncestor(hasAnyDescendant(hasText(title)) and !hasAnyDescendant(hasText(otherTitle))))，
 *      恰一动作，不恢复 Y±8 几何匹配；
 *    - T1 / T2 / 回 T1 期间普通行 12 列 exact 全字段冻结隔离；
 *    - 不经过外部 Profile Notes 冒充 reader 内 LIFO。
 * 3. 规范化最终普通行恢复断言：
 *    - awaitFinalOrdinaryRow 断言返回 S0 后数据库位置恢复为与 s0 完全一致的页首偏移。
 *
 * ## 合同来源（复用既有结论，不发明行为）
 *
 * 1. LIFO 语义（[com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract]，
 *    行级实核 2026-10-29）：
 *    - `beginTemporaryInspection` 把**当前在屏的 active 阅读点**压入返回栈、active 置为新目标；
 *      栈超 [maxTemporaryHistory] 弹最旧；
 *    - `returnFromTemporaryInspection` 弹栈顶为 active；**栈空时回 durable normalReading**；
 *    - 「返回阅读处」按钮（ReaderChrome.kt:622-633，contentDescription="返回阅读处"）仅在
 *      `temporaryInspection && hasReturnableTarget`（临时栈非空，ReaderRoute.kt:230-231）时组合。
 * 2. reader 内真实入口链（本批 A 会话交付产品接线；lane-b 只消费 UI 合同名）：
 *    底栏「更多」（ReaderChrome.kt:650 contentDescription，不受 temporary 模式门控）→ 溢出菜单
 *    「笔记与标注」（Text，ReaderChrome.kt:681）→ `OpenSheet(ReaderSheet.NOTES)` →
 *    ReaderSheetHost.kt:369 NotesSheet —— 条目卡 NoteItemCard（ReaderNotesComponents.kt:349-430）
 *    **渲染 note.title 精确 Text**（与「我的」页 AnnotationEntryCard 不同，后者不渲染标题——187 结论），
 *    并带「跳转/删除」胶囊；A 在条目上新增用户名「临时查看来源」动作。
 * 3. 外部 Notes 合同 B（188 设备验收）不在本文件重复；reader 内发起临时查阅的 LIFO 返回链是
 *    173 结论中的既有未覆盖缺口，本文件即其验收。
 *
 * ## 诚实边界
 *
 * - 测试源码交付 ≠ 编译/设备通过：本批无 Gradle/设备授权，**NOT_RUN**；
 * - 不修改产品源码/既有测试/fixture；
 * - 生产在读状态未暴露公开的 progressSavesIdle UI 信号，S0 依据 Room SQLite 真实落库判定。
 */
class ReaderInternalTemporaryLifoTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T189B1-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra189b1-$runId-book-a"

    private var sourceOffset1: Int = 0
    private var sourceOffset2: Int = 0
    private lateinit var target1Text: String
    private lateinit var target2Text: String

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /** 条目标题（NoteItemCard 渲染 note.title 精确 Text）；跨测试类不撞名，sheet 内按此唯一定位。 */
    private val noteTitle1 = "测试 TXT 甲 内源一"
    private val noteTitle2 = "测试 TXT 甲 内源二"

    /** 条目批注体（种子 body；本类不依赖其渲染，仅作库内记录与诊断输出）。 */
    private val noteBody1 = "测试 TXT 笔记正文（内源1）"
    private val noteBody2 = "测试 TXT 笔记正文（内源2）"

    /** A 新动作用户名（合同名）：reader 内笔记条目上的「临时查看来源」。 */
    private val inspectSourceActionName = "临时查看来源"

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = appContext,
            db = db,
            runId = runId,
            bookIdA = bookIdA,
            bookIdB = "cra189b1-$runId-book-b",
            bookIdUnreadable = "cra189b1-$runId-book-unreadable",
            ledger = ledger,
        )
        contentA = contents.first
        target1Text = "第一章 序章"
        sourceOffset1 = contentA.bodyText.indexOf(target1Text)
        target2Text = contentA.snippet2
        sourceOffset2 = contentA.offset2
        checkFixtureLocatorRelationship()
        seedSourceNotes()
        // 夹具就绪后再拉起真实 MainActivity（J1 既有模式，避免启动时序竞态）。
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    /** 两条甲书来源笔记（同书不同目标，locator 用生产 LocatorCodec 嵌套口径；kind=note）。 */
    private fun seedSourceNotes() {
        runBlocking {
            db.noteDao().upsert(noteEntityFor("src1", sourceOffset1, noteTitle1, noteBody1))
            db.noteDao().upsert(noteEntityFor("src2", sourceOffset2, noteTitle2, noteBody2))
        }
    }

    private fun noteEntityFor(tag: String, offset: Int, title: String, body: String): NoteEntity = NoteEntity(
        id = "cra189b1-$runId-note-$tag",
        book_id = bookIdA,
        title = title,
        body = body,
        locator_json = LocatorCodec.encode(
            legacyOffset = offset, chapterIndex = null, charOffset = null, excerpt = null,
        ),
        created_at = "2026-10-29T00:00:00Z",
        updated_at = "2026-10-29T00:00:00Z",
        kind = "note",
    )

    /**
     * 夹具定位关系自检（与 187/183 同批夹具同源，本类独立复核）：
     * 两目标全书唯一、互异、均非普通初始位点 S0 且不含 marker1、无换行、互不包含。
     */
    private fun checkFixtureLocatorRelationship() {
        val text = contentA.bodyText
        check(target1Text.isNotBlank() && target2Text.isNotBlank()) { "目标正文不能为空" }
        check(sourceOffset1 >= 0) { "target1 必须在正文中存在 (got $sourceOffset1)" }
        check(sourceOffset2 in 0 until text.length) { "target2 位置必须合法 (got $sourceOffset2)" }
        check(countOccurrences(text, target1Text) == 1) { "target1 必须全书唯一" }
        check(countOccurrences(text, target2Text) == 1) { "target2 必须全书唯一" }
        check(sourceOffset1 != sourceOffset2 && target1Text != target2Text) { "两目标必须互异" }
        check(sourceOffset1 != contentA.offset1 && sourceOffset2 != contentA.offset1) {
            "两目标 offset 均不得等于普通初始位点 S0 (offset1=${contentA.offset1})"
        }
        check(!target1Text.contains(contentA.marker1) && !target2Text.contains(contentA.marker1)) {
            "两目标正文均不得包含 marker1"
        }
        check(!target1Text.contains('\n') && !target2Text.contains('\n')) { "目标不得跨自然段" }
        check(!target1Text.contains(target2Text) && !target2Text.contains(target1Text)) {
            "两目标不得互相包含"
        }
    }

    private fun countOccurrences(fullText: String, sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = fullText.indexOf(sub, idx)
            if (found == -1) break
            count++
            idx = found + sub.length
        }
        return count
    }

    // ── 真实 UI：建立普通基线 ────────────────────────────────────────────

    /** Home → 底栏「书架」→ 精确 book-tile → 普通正文 marker1 就绪。 */
    private fun openBookAOrdinarily() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookIdA").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-$bookIdA").assertExists().performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    // ── reader 内 NotesSheet：更多 → 笔记与标注 ─────────────────────────

    /** 生产 chrome 合同（183/187 实核）：reader 打开即控件层可见；控件可见时首次系统 Back
     *  只藏控件不导航。本类不用 Back 开关 sheet，全部经显式按钮。 */
    private fun openReaderNotesSheet() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("更多")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("更多").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("笔记与标注").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("笔记与标注").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        // sheet 真实身份：笔记/书签小节 + 本类两条种子条目标题都已渲染（NoteItemCard 标题 Text）。
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("笔记 / 书签").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(noteTitle1).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(noteTitle2).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 【与 A 的接缝·条目级「临时查看来源」动作选择器（189-B-r1 语义重构保留）】
     * 废弃脆弱的 Y±8 几何重叠假定，基于 A 生产卡片语义结构绑定（Card/Row 容器与层级包含）：
     * 动作节点 = Text 或 ContentDescription 等于合同名（"临时查看来源"）；
     * 绑定条件 = 节点位于包含指定条目标题 (hasAnyDescendant(hasText(title))) 的条目卡片局部容器内，
     *            且不包含对立条目标题（排除 LazyColumn / Root 等全局祖先）。
     * 要求恰好一个匹配节点，否则携带诊断失败——不任意 first/last。
     */
    private fun readerInternalInspectSourcePillFor(title: String) {
        val otherTitle = if (title == noteTitle1) noteTitle2 else noteTitle1
        val pillMatcher = (hasText(inspectSourceActionName) or hasContentDescription(inspectSourceActionName)) and
            hasAnyAncestor(hasAnyDescendant(hasText(title)) and !hasAnyDescendant(hasText(otherTitle)))

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(pillMatcher).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(pillMatcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val nodes = if (composeRule.onAllNodes(pillMatcher).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodes(pillMatcher)
        } else {
            composeRule.onAllNodes(pillMatcher, useUnmergedTree = true)
        }
        val count = nodes.fetchSemanticsNodes().size
        assertTrue(
            "inspect-source pill for '$title' must bind to exactly one node in its card container " +
                "(got $count, otherTitle='$otherTitle'); A-lane card semantics misaligned",
            count == 1,
        )
        nodes[0].performClick()
        composeRule.waitForIdle()
    }

    // ── 当前页正文语义（锁定正文容器祖先，排除页眉/页脚/Chrome；仅当前 PageLayer 暴露 text）────

    private fun displayedPageTexts(): List<String> =
        composeRule.onAllNodes(
            hasAnyAncestor(hasContentDescription("分页正文已就绪")) and
                SemanticsMatcher("exposesTextSemantics") { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
                },
        ).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(separator = "") { it.text }
                ?.takeIf { it.isNotBlank() }
        }

    private fun pageShowsTargetText(targetText: String): Boolean =
        displayedPageTexts().any { page -> page.replace("\n", "").contains(targetText) }

    private fun pageTextSampleForDiagnostics(): String =
        displayedPageTexts().joinToString(separator = " | ") { it.take(80) }.take(400)

    /**
     * 某一临时 LIFO 层的就位断言（T1、T2、逐层回 T1）：
     * - 本层目标 targetText 精确显示在当前页；
     * - 对立临时目标 otherTargetText 不在当前页；
     * - 普通初始页 marker1 不在当前页；
     * - 临时栈非空，断言「返回阅读处」存在且可见。
     */
    private fun awaitTemporaryLayer(
        label: String,
        targetText: String,
        otherTargetText: String,
    ) {
        composeRule.waitUntil(timeoutMillis = 15_000) { pageShowsTargetText(targetText) }
        assertTrue(
            "$label: page must display unique target '$targetText'; pageText=[${pageTextSampleForDiagnostics()}]",
            pageShowsTargetText(targetText),
        )
        assertTrue(
            "$label: other target '$otherTargetText' must not be on current page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(otherTargetText),
        )
        assertTrue(
            "$label: initial-page marker1 must not be on current page; pageText=[${pageTextSampleForDiagnostics()}]",
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed()
    }

    /**
     * 最终回到普通层 S0 断言（189-B-r1 解耦，消除旧 341 行 marker1 矛盾）：
     * - 普通正文 marker1 恢复且可见；
     * - 两个临时目标（target1Text 与 target2Text）均不在当前页；
     * - 临时栈清空，断言「返回阅读处」按钮消失（assertDoesNotExist）。
     */
    private fun awaitFinalOrdinaryLayer(label: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        assertTrue(
            "$label: temporary target1 '$target1Text' must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target1Text),
        )
        assertTrue(
            "$label: temporary target2 '$target2Text' must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target2Text),
        )
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()
    }

    // ── 普通持久行隔离与保存合同 ─────────────────────────────────────────

    /**
     * 捕获 setUpFixtures 阶段预写的种子行：
     * 种子行的 legacyOffset 恰等于入参 contentA.offset1 (4014)，
     * 作为对照基准，用于在 awaitOrdinaryS0 中严格排查"未发生真实保存、直接接受种子行"的伪绿。
     */
    private fun captureSeedRow(): J1RealReaderFixtures.ProgressRowSnapshot {
        val seed = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("fixture seed row must exist before opening reader", seed)
        assertEquals(
            "fixture seed row must have legacyOffset == contentA.offset1",
            contentA.offset1,
            seed!!.legacyOffset,
        )
        return seed
    }

    /**
     * 等待并核实真实 reader 首次普通阅读落位保存完成并捕获 S0 快照（189-B-r2 真实保存边界修正）：
     *
     * 【排查种子行假通过的核心依据】
     * 1. 生产分页合同（TxtPagedController.currentPageStartAbs 与 J1RealReaderFixtures 注释）：
     *    真实 reader 分页持久化的是「包含目标位点的当前页首字符全书偏移」。
     *    页首字符偏移 currentPageStartAbs 满足 0 < legacyOffset <= contentA.offset1，
     *    排除种子行不依赖排版巧合的严格小于，而依靠时间戳推进、locator JSON 实质变更与 marker1 屏幕渲染。
     * 2. 时间与定位 JSON 实质推进：
     *    真实 reader 执行 SaveProgress -> mergeReaderProgress，写入当前时间戳 nowIso() 与规范化 locator，
     *    因此 updated_at 必须严格晚于种子行，current_location_json 必须实质发生改变。
     * 3. 页面渲染绑定：
     *    openBookAOrdinarily 已断言当前页渲染 contentA.marker1，二者结合锁定此页首正是包含 marker1 的那一页。
     */
    private fun awaitOrdinaryS0(seedRow: J1RealReaderFixtures.ProgressRowSnapshot): J1RealReaderFixtures.ProgressRowSnapshot {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val latest = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            latest != null &&
                latest.updated_at > seedRow.updated_at &&
                latest.current_location_json != seedRow.current_location_json &&
                latest.legacyOffset > 0 &&
                latest.legacyOffset <= contentA.offset1
        }
        val s0 = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary S0 progress row must exist", s0)
        assertTrue(
            "S0 must be a real reader save, not fixture seed " +
                "(s0.offset=${s0!!.legacyOffset}, seed.offset=${seedRow.legacyOffset}, offset1=${contentA.offset1})",
            s0.legacyOffset <= contentA.offset1 && s0.legacyOffset > 0,
        )
        assertTrue(
            "S0 updated_at must strictly advance past seed timestamp (s0=${s0.updated_at}, seed=${seedRow.updated_at})",
            s0.updated_at > seedRow.updated_at,
        )
        assertTrue(
            "S0 current_location_json must differ from seed JSON",
            s0.current_location_json != seedRow.current_location_json,
        )
        assertRowPositionLegal(s0, "s0Baseline")
        return s0
    }

    /**
     * 等待最终返回普通层 S0 后的持久行：
     * 验证普通位置恢复为与进入临时查阅前一致的 S0 页首偏移，且仍严格小于 offset1、不落在两临时源偏移处。
     */
    private fun awaitFinalOrdinaryRow(s0: J1RealReaderFixtures.ProgressRowSnapshot): J1RealReaderFixtures.ProgressRowSnapshot {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val latest = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            latest != null &&
                rowOffset(latest) == rowOffset(s0) &&
                latest.legacyOffset > 0 &&
                latest.legacyOffset <= contentA.offset1
        }
        val after = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary progress row must exist after final return", after)
        assertRowPositionLegal(after, "afterFinalReturn")
        assertEquals(
            "afterFinalReturn: ordinary position must restore the exact S0 offset",
            rowOffset(s0), rowOffset(after),
        )
        return after!!
    }

    private fun rowOffset(row: J1RealReaderFixtures.ProgressRowSnapshot?): Int? =
        row?.let {
            LocatorCodec.locatorFromProgress(it.current_location_json)?.legacyOffset
        }

    private fun assertRowPositionLegal(row: J1RealReaderFixtures.ProgressRowSnapshot?, label: String) {
        assertNotNull("$label: progress row must exist", row)
        J1RealReaderFixtures.assertObservedPersistedLocation(row!!, contentA.offset1, label)
        val off = rowOffset(row)
        assertTrue(
            "$label: row position must not sit at temporary source offsets (got $off)",
            off != sourceOffset1 && off != sourceOffset2,
        )
        assertTrue(
            "$label: row position must be before or at offset1 (got $off, offset1=${contentA.offset1})",
            off != null && off <= contentA.offset1,
        )
    }

    private fun assertOrdinaryRowUnchanged(
        s0: J1RealReaderFixtures.ProgressRowSnapshot,
        during: J1RealReaderFixtures.ProgressRowSnapshot?,
        label: String,
    ) {
        assertNotNull("$label: ordinary row must not vanish", during)
        s0.assertExactEquals(during, label)
        val off = rowOffset(during)
        assertTrue(
            "$label: ordinary row must never sit at temporary source offsets (got $off)",
            off == null || (off != sourceOffset1 && off != sourceOffset2),
        )
    }

    // ── 用例：reader 内 T1 → T2 → 返回阅读处逐层 T2→T1→S0 ────────────────

    @Test
    fun readerInternalLifo_twoSources_returnChain_preservesOrdinaryRow() {
        // 步骤 0：记录 setUpFixtures 写入的初始种子行（此时 legacyOffset == offset1 = 4014）
        val seedRow = captureSeedRow()

        openBookAOrdinarily()
        // 普通 reader 未发起临时查阅：栈必空，「返回阅读处」不存在（错误入口排除）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()

        // 真实 reader 首次落位保存就位（排除种子行，确保真实 reader 保存已落库）
        val s0 = awaitOrdinaryS0(seedRow)
        val s0Offset = rowOffset(s0)

        // T1：reader 内笔记条目「临时查看来源」（A 接缝）→ 临时层 1。
        openReaderNotesSheet()
        readerInternalInspectSourcePillFor(noteTitle1)
        awaitTemporaryLayer("T1", target1Text, target2Text)
        assertOrdinaryRowUnchanged(s0, J1RealReaderFixtures.captureProgressRow(db, bookIdA), "duringT1")

        // T2：在 T1 层内再次经 reader 内条目进入不同来源（push 当前 active=T1 → 栈两层）。
        openReaderNotesSheet()
        readerInternalInspectSourcePillFor(noteTitle2)
        awaitTemporaryLayer("T2", target2Text, target1Text)
        assertOrdinaryRowUnchanged(s0, J1RealReaderFixtures.captureProgressRow(db, bookIdA), "duringT2")

        // 返回第 1 层：T2 → T1（弹栈顶，栈仍剩一层 → 按钮仍在）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        awaitTemporaryLayer("returnToT1", target1Text, target2Text)
        assertOrdinaryRowUnchanged(
            s0, J1RealReaderFixtures.captureProgressRow(db, bookIdA), "afterReturnToT1",
        )

        // 返回第 2 层：T1 → 普通 S0（栈空 → 按钮消失；普通页与持久位置恢复）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        awaitFinalOrdinaryLayer("backToNormal")
        val after = awaitFinalOrdinaryRow(s0)
        assertEquals(
            "afterFinalReturn: ordinary position must restore the exact S0 offset",
            s0Offset, rowOffset(after),
        )

        // 真实普通退出至书架（真实 UI 退出边界，保存完成并导航到书架）
        val exitAttempt = ReaderChromeExitSupport.exitViaBack(composeRule)
        if (exitAttempt.failure != null) {
            throw exitAttempt.failure!!
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val postExitRow = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary progress row must exist after normal exit", postExitRow)
        assertRowPositionLegal(postExitRow, "afterNormalExit")
        assertEquals(
            "afterNormalExit: ordinary position must keep exact S0 offset",
            s0Offset,
            rowOffset(postExitRow),
        )

        // 从书架使用 book-tile 重新打开原书，验证正文 marker1 屏显且「返回阅读处」按钮不存在
        openBookAOrdinarily()
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()
        assertTrue(
            "afterReopen: temporary target1 '$target1Text' must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target1Text),
        )
        assertTrue(
            "afterReopen: temporary target2 '$target2Text' must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target2Text),
        )

        composeRule.waitUntil(timeoutMillis = 15_000) {
            val r = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            r != null && rowOffset(r) == s0Offset
        }
        val reopenedRow = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary progress row must exist after reopen", reopenedRow)
        assertRowPositionLegal(reopenedRow, "afterReopen")
        assertEquals(
            "afterReopen: ordinary position must match exact S0 offset",
            s0Offset,
            rowOffset(reopenedRow),
        )
    }
}
