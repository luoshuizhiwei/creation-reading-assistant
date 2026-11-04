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
 * 任务207-r3：真实生产组合（Hilt 宿主 + 生产 ReaderRoute/ReaderScreen）的 reader 内临时
 * 查阅导航链验收。模式只读参考冻结 [ReaderInternalTemporaryLifoTest]（createEmptyComposeRule +
 * ActivityScenario&lt;MainActivity&gt; + J1RealReaderFixtures），不改其源码、不改 fixture/host、
 * 不造伪钩子、不吞异常。
 *
 * ## 本轮状态（不得超证据宣称）
 * **本轮仅交付编译（compileDebugAndroidTestKotlin），未在任何设备/模拟器执行 —— device NOT_RUN，
 * 编译交付可供Root审查，不声称行为通过。**
 *
 * ## 覆盖（生产链，无壳/镜像）
 * 一个 @Test 走完整真实 UI：书架普通打开（S0 真实落库）→ reader 内 NotesSheet 条目「临时查看来源」
 * 真实 T1（生产 onInspectSourceTemporarily → navigateReaderRouteSingleTop）→ T1 内再入 T2 →
 * 「返回阅读处」逐层 T2→T1→S0（生产 performTemporaryReturn → dispatchTemporaryReturn）→
 * 真实普通退出（ReaderChromeExitSupport.exitViaBack，含生产 awaitProgressSaves 保存边界）回书架，
 * 且普通持久行恢复精确 S0 偏移。
 *
 * ## 失败/取消场景的确切缺口（不伪造、不停止可完成项）
 * 1. 生产 reader 内全部临时入口（NotesSheet NoteItemCard 与「我的」AnnotationEntryCard 的
 *    「临时查看来源」）目标恒为**条目自身 book**（note.book_id 书内 locator）——不存在指向
 *    损坏书/他书的 reader 内临时入口。J1RealReaderFixtures 的 bookIdUnreadable 无法经由任何
 *    合法 reader 内入口成为 T1 目标（损坏书自身也无法建立 S0）。
 *    **建议（范围外，须Root另定）**：为卡片动作模型新增「指向他书/可注入加载失败」的目标变体
 *    （ReaderNotesComponents.kt 卡片目标模型 + J1RealReaderFixtures 失败目标 fixture）。
 * 2. pending 取消窗口（请求登记→加载完成之间按返回）在真实 UI 上是 racy 微窗口，现有生产
 *    未提供暂停加载的合法测试钩子；不造伪钩子（合同禁止），故取消场景本轮仍由图测试
 *    （seam 级，NOT_RUN）+ JVM 纯函数承担。
 */
class ReaderTemporaryNavigationProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T207R3-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra207r3-$runId-book-a"

    private var sourceOffset1: Int = 0
    private var sourceOffset2: Int = 0
    private lateinit var target1Text: String
    private lateinit var target2Text: String

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    /** 条目标题（NoteItemCard 渲染 note.title 精确 Text）；与本类 runId 绑定，sheet 内唯一定位。 */
    private val noteTitle1 = "测试 TXT 甲 源点一"
    private val noteTitle2 = "测试 TXT 甲 源点二"

    private val noteBody1 = "测试 TXT 笔记正文（r3 源点1）"
    private val noteBody2 = "测试 TXT 笔记正文（r3 源点2）"

    /** A 新动作用户名（生产合同名）：reader 内笔记条目上的「临时查看来源」。 */
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
            bookIdB = "cra207r3-$runId-book-b",
            bookIdUnreadable = "cra207r3-$runId-book-unreadable",
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

    private fun seedSourceNotes() {
        runBlocking {
            db.noteDao().upsert(noteEntityFor("src1", sourceOffset1, noteTitle1, noteBody1))
            db.noteDao().upsert(noteEntityFor("src2", sourceOffset2, noteTitle2, noteBody2))
        }
    }

    private fun noteEntityFor(tag: String, offset: Int, title: String, body: String): NoteEntity = NoteEntity(
        id = "cra207r3-$runId-note-$tag",
        book_id = bookIdA,
        title = title,
        body = body,
        locator_json = LocatorCodec.encode(
            legacyOffset = offset, chapterIndex = null, charOffset = null, excerpt = null,
        ),
        created_at = "2026-10-31T00:00:00Z",
        updated_at = "2026-10-31T00:00:00Z",
        kind = "note",
    )

    /** 夹具定位关系自检（187/203 同判据，本类独立复核）。 */
    private fun checkFixtureLocatorRelationship() {
        val text = contentA.bodyText
        check(sourceOffset1 >= 0) { "target1 必须在正文中存在 (got $sourceOffset1)" }
        check(sourceOffset2 in 0 until text.length) { "target2 位置必须合法 (got $sourceOffset2)" }
        check(sourceOffset1 != sourceOffset2 && target1Text != target2Text) { "两目标必须互异" }
        check(countOccurrences(text, target1Text) == 1 && countOccurrences(text, target2Text) == 1) {
            "两目标必须全书唯一"
        }
        check(sourceOffset1 != contentA.offset1 && sourceOffset2 != contentA.offset1) {
            "两目标 offset 均不得等于普通初始位点 S0 (offset1=${contentA.offset1})"
        }
        check(!target1Text.contains(contentA.marker1) && !target2Text.contains(contentA.marker1)) {
            "两目标正文均不得包含 marker1"
        }
        check(!target1Text.contains('\n') && !target2Text.contains('\n')) { "目标不得跨自然段" }
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

    // ── 真实 UI 导航（203 同模式，本类独立实现）─────────────────────────

    /** Home/书架 → 精确 book-tile → 普通正文 marker1 就绪。 */
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

    /** 更多 → 笔记与标注 → NotesSheet，两条种子条目标题都已渲染。 */
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
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("笔记 / 书签").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(noteTitle1).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(noteTitle2).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 条目级「临时查看来源」胶囊：卡片局部语义祖先绑定，恰好一匹配（203 同判据）。 */
    private fun inspectSourcePillFor(title: String) {
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
            "inspect-source pill for '$title' must bind to exactly one node in its card container (got $count)",
            count == 1,
        )
        nodes[0].performClick()
        composeRule.waitForIdle()
    }

    // ── 当前页正文语义（203 同口径）───────────────────────────────────

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

    /** 临时层就位：本层目标在页、对立目标与 marker1 不在页、「返回阅读处」在屏。 */
    private fun awaitTemporaryLayer(label: String, targetText: String, otherTargetText: String) {
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

    /** 最终普通层：marker1 恢复、两临时目标不在页、「返回阅读处」消失。 */
    private fun awaitFinalOrdinaryLayer(label: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        assertTrue(
            "$label: temporary target1 must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target1Text),
        )
        assertTrue(
            "$label: temporary target2 must not be on ordinary page; pageText=[${pageTextSampleForDiagnostics()}]",
            !pageShowsTargetText(target2Text),
        )
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()
    }

    // ── 普通持久行（203 同口径：排除种子行冒充，真实保存边界）───────────

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

    /** 真实 reader 首次普通落位保存就绪并捕获 S0（时间戳/locator/范围三重排除种子行）。 */
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
        return s0!!
    }

    private fun rowOffset(row: J1RealReaderFixtures.ProgressRowSnapshot?): Int? =
        row?.let {
            LocatorCodec.locatorFromProgress(it.current_location_json)?.legacyOffset
        }

    /** 普通退出后持久行恢复精确 S0 偏移。 */
    private fun awaitFinalOrdinaryRow(s0: J1RealReaderFixtures.ProgressRowSnapshot): J1RealReaderFixtures.ProgressRowSnapshot {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val latest = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
            latest != null && rowOffset(latest) == rowOffset(s0)
        }
        val after = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary progress row must exist after final return", after)
        return after!!
    }

    // ── 用例：真实生产链 S0→T1→T2→T1→S0→真实退出→书架────────────────────

    @Test
    fun productionChain_readerInternalInspect_lifoReturn_realExitBackToShelf() {
        val seedRow = captureSeedRow()

        openBookAOrdinarily()
        // 普通 reader 未发起临时查阅：栈必空，「返回阅读处」不存在（错误入口排除）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()
        val s0 = awaitOrdinaryS0(seedRow)
        val s0Offset = rowOffset(s0)

        // T1：reader 内笔记条目「临时查看来源」（生产 onInspectSourceTemporarily 分派）。
        openReaderNotesSheet()
        inspectSourcePillFor(noteTitle1)
        awaitTemporaryLayer("T1", target1Text, target2Text)

        // T2：在 T1 层内再入不同来源（生产 singleTop 分派，id 保留、参数切换）。
        openReaderNotesSheet()
        inspectSourcePillFor(noteTitle2)
        awaitTemporaryLayer("T2", target2Text, target1Text)

        // 返回 T1（生产 performTemporaryReturn → dispatchTemporaryReturn）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        awaitTemporaryLayer("returnToT1", target1Text, target2Text)

        // 返回 S0（LIFO 最后一层 → 普通阅读处，TemporaryReturnSaveGuard 武装）。
        composeRule.onNodeWithContentDescription("返回阅读处").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        awaitFinalOrdinaryLayer("backToNormal")
        val after = awaitFinalOrdinaryRow(s0)
        assertEquals(
            "afterFinalReturn: ordinary position must restore the exact S0 offset",
            s0Offset, rowOffset(after),
        )

        // 真实普通退出（生产 awaitProgressSaves 保存边界 + 导航），回到书架。
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
        assertEquals(
            "afterNormalExit: ordinary position must keep exact S0 offset",
            s0Offset,
            rowOffset(postExitRow),
        )
    }
}
