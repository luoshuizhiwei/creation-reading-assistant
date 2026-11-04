package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 任务112-v1：L1 书架动态视图（保存的筛选组合）真实 UI 保存→套用→删除 闭环。
 *
 * 与「书单」（静态书籍集合）不同：动态视图是筛选条件快照——本批在真实
 * FilterSheet/ShelfRoute/ShelfViewModel/ShelfSavedViewsStore 生产链上：
 * 1. 生产 DAO 种两本中性书（甲=txt+标签T112，乙=epub，无标签）——不冒充导入/阅读全过程；
 * 2. 真实 UI 配置两项相互独立筛选（格式=TXT + 标签），核正反对照可见性；
 * 3. 命名保存本批唯一目标视图（真实「保存」胶囊 + 唯一可编辑输入框）；
 * 4. 真实改变筛选（EPUB + 取消标签）核反向可见性；再真实点击目标视图套用（pill），
 *    核筛选组合整体恢复（甲回到可见/乙回到隐藏）；
 * 5. 另建一条独立对照视图后，按目标行内「删除」精确删除目标；重开面板核目标缺席/对照仍在；
 * 6. 只读核真实 DataStore 文件（shelf_saved_views.preferences_pb）非空且含对照不含目标；
 * 7. 书/标签/连接表原实体关系前后逐行不变（保存动态视图不重写静态书单/分类学）。
 *
 * 边界：不改生产 FilterSheet/ShelfRoute/VM/Store；不加进程 kill/restart 耐久矩阵
 * （107 真实文件重建已充分）；成功标准是正向断言，不是无崩溃。
 */
@RunWith(AndroidJUnit4::class)
class ShelfSavedViewsJourneyDeviceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var lastPhase: String = "start"

    private var bookAId: String = ""
    private var bookBId: String = ""
    private var tagId: String = ""
    private lateinit var bookATitle: String
    private lateinit var bookBTitle: String
    private lateinit var tagName: String
    private lateinit var targetViewName: String
    private lateinit var controlViewName: String

    private companion object {
        const val TAG = "CRA112A"
        const val UI_TIMEOUT_MS = 20_000L
        const val NAME_FIELD_LABEL = "给当前筛选起个名字"
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = requireNotNull(InstrumentationRegistry.getArguments().getString("craRunId")) {
            "Root must bind craRunId"
        }
        require(runId.matches(Regex("T112V1-[A-Za-z0-9-]+"))) { "unexpected runId: $runId" }
        val root = requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" }
        resultsDir = File(root, "task112a-results-$runId")
        check(!resultsDir.exists()) { "本批结果目录已存在，保留并阻塞：$resultsDir" }
        check(resultsDir.mkdirs()) { "无法创建本批结果目录：$resultsDir" }
        db = J1RealReaderFixtures.openDatabase(context)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        seedFixture()
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
            File(resultsDir, "t112a_99_summary.json").writeText(
                JSONObject()
                    .put("runId", runId)
                    .put("lastPhase", lastPhase)
                    .put("bookA", bookAId)
                    .put("bookB", bookBId)
                    .put("tag", tagId)
                    .put("targetView", targetViewName)
                    .put("controlView", controlViewName)
                    .put("fixtureLevel", "production DAO seed (BookEntity+TagEntity+book_tag); not an import/reading action")
                    .put("cleanup", "本批仅 app 私有数据；外层卸载产品包并恢复原 test 数据")
                    .toString(2),
            )
        }
        attempt { db.close() }
        if (errors.isNotEmpty()) {
            val failure = AssertionError("tearDown 失败 ${errors.size} 项（原始异常保留在 suppressed）")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }

    // ── fixture（生产 DAO 直接落库，不冒充导入/阅读全过程） ──────────────────

    private fun seedFixture() {
        bookAId = "t112a-book-a-$runId"
        bookBId = "t112a-book-b-$runId"
        tagId = "t112a-tag-$runId"
        bookATitle = "T112中性书甲-$runId"
        bookBTitle = "T112中性书乙-$runId"
        tagName = "标签T112-$runId"
        targetViewName = "T112目标视图-$runId"
        controlViewName = "T112对照视图-$runId"

        kotlinx.coroutines.runBlocking {
            assertTrue("本轮书 ID 不得预先存在", rawOne("SELECT id FROM books WHERE id IN ('$bookAId','$bookBId')") == null)
            assertTrue("本轮标签 ID 不得预先存在", rawOne("SELECT id FROM tags WHERE id='$tagId'") == null)
            val now = Instant.now().toString()
            db.bookDao().upsert(
                BookEntity(id = bookAId, title = bookATitle, author = "中性测试作者", format = "txt",
                    original_file_name = "t112a-a-$runId.txt", imported_at = now, updated_at = now),
            )
            db.bookDao().upsert(
                BookEntity(id = bookBId, title = bookBTitle, author = "中性测试作者", format = "epub",
                    original_file_name = "t112a-b-$runId.epub", imported_at = now, updated_at = now),
            )
            db.tagDao().upsert(TagEntity(id = tagId, name = tagName, created_at = now, updated_at = now))
            db.bookTagDao().upsert(BookTagEntity(book_id = bookAId, tag_id = tagId))
        }
        writeEvidence(
            "00_fixture_created",
            mapOf("bookA" to bookAId, "bookB" to bookBId, "tag" to tagId, "tagName" to tagName,
                "level" to "production DAO seed; no import/reading action"),
        )
    }

    // ── 旅程 ─────────────────────────────────────────────────────────────────

    @Test
    fun saveApplyDeleteSavedViewKeepsTaxonomy() {
        markPhase("t112a_10_launch")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        // 113导航修正：生产起始页固定为首页（AppNavigation startDestination=Home），先等任一顶层页，
        // 再真实点击「书架」tab（111 已验证模式），然后等 shelf-route。
        assertTrue("顶层页面必须出现", waitFor(UI_TIMEOUT_MS, "顶层页面") { topLevelPage() != null })
        clickTopLevelTab("书架")
        assertTrue("书架页面必须出现", waitFor(UI_TIMEOUT_MS, "shelf-route") { bookShelfPresent() })
        assertTrue("两本中性书初始都必须可见", waitFor(UI_TIMEOUT_MS, "两本书") { bookPresent(bookAId) && bookPresent(bookBId) })

        val snapshotBefore = taxonomySnapshot()

        // 打开筛选面板（真实入口：工具栏筛选摘要）
        clickUnique("shelf-filter-summary", byTag = true)
        assertTrue("筛选面板必须打开", waitFor(UI_TIMEOUT_MS, "动态视图区") { textVisible("动态视图（保存的筛选）") })

        // 1) 配置两项相互独立筛选：格式=TXT + 标签=tagA（均为真实 chips 点击）
        markPhase("t112a_20_filter_applied")
        clickUnique("TXT", inScrollablePanel = true)
        clickUnique(tagName, inScrollablePanel = true)
        assertTrue("组合筛选后甲书可见", waitFor(UI_TIMEOUT_MS, "甲书可见") { bookPresent(bookAId) })
        assertTrue("组合筛选后乙书必须被排除", waitFor(UI_TIMEOUT_MS, "乙书排除") { !bookPresent(bookBId) })
        assertTrue(
            "工具栏摘要必须显示两维度筛选生效（1 本 · 2 项筛选）",
            waitFor(UI_TIMEOUT_MS, "两维度摘要") { textVisible("1 本 · 2 项筛选") },
        )
        writeEvidence(
            "20_filter_applied",
            mapOf("filters" to listOf("format=txt", "tag=$tagName"),
                "bookA" to bookPresent(bookAId), "bookB" to bookPresent(bookBId)),
        )

        // 2) 命名保存目标视图（真实输入框 + 「保存」胶囊）
        markPhase("t112a_30_saved")
        typeIntoNameField(targetViewName)
        clickUnique("保存", inScrollablePanel = true)
        assertTrue("保存后面板必须出现目标视图 pill", waitFor(UI_TIMEOUT_MS, "目标视图 pill") { clickableTextPresent(targetViewName) })
        assertTrue("真实 Store 文件必须包含目标视图名", waitFor(UI_TIMEOUT_MS, "store 含目标") { storeBytesContain(targetViewName) })
        writeEvidence(
            "30_saved",
            mapOf("name" to targetViewName,
                "pillPresent" to clickableTextPresent(targetViewName),
                "storeContainsTarget" to storeBytesContain(targetViewName)),
        )

        // 3) 真实改变筛选：格式改 EPUB、取消标签 → 反向可见性
        markPhase("t112a_40_changed")
        clickUnique("EPUB", inScrollablePanel = true)
        clickUnique(tagName, inScrollablePanel = true)
        assertTrue("改变筛选后乙书可见", waitFor(UI_TIMEOUT_MS, "乙书可见") { bookPresent(bookBId) })
        assertTrue("改变筛选后甲书必须被排除", waitFor(UI_TIMEOUT_MS, "甲书排除") { !bookPresent(bookAId) })
        writeEvidence(
            "40_changed",
            mapOf("filters" to listOf("format=epub", "tag=none"),
                "bookA" to bookPresent(bookAId), "bookB" to bookPresent(bookBId)),
        )

        // 4) 真实点击目标视图 pill 套用 → 面板关闭，筛选组合整体恢复
        markPhase("t112a_50_reapplied")
        composeRule.onNode(hasText(targetViewName) and hasClickAction())
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("套用后面板必须关闭", waitFor(UI_TIMEOUT_MS, "面板关闭") { !textVisible("动态视图（保存的筛选）") })
        assertTrue("套用后甲书必须恢复可见", waitFor(UI_TIMEOUT_MS, "甲恢复") { bookPresent(bookAId) })
        assertTrue("套用后乙书必须恢复排除", waitFor(UI_TIMEOUT_MS, "乙排除") { !bookPresent(bookBId) })
        assertTrue(
            "套用后工具栏摘要必须恢复两维度（1 本 · 2 项筛选），不得仅以视图名存在等同组合恢复",
            waitFor(UI_TIMEOUT_MS, "套用后两维度摘要") { textVisible("1 本 · 2 项筛选") },
        )
        writeEvidence(
            "50_reapplied",
            mapOf("panelClosed" to !textVisible("动态视图（保存的筛选）"),
                "bookA" to bookPresent(bookAId), "bookB" to bookPresent(bookBId),
                "storeContainsTarget" to storeBytesContain(targetViewName)),
        )

        // 5) 重开面板建独立对照视图（EPUB、无标签）
        markPhase("t112a_60_control_saved")
        clickUnique("shelf-filter-summary", byTag = true)
        assertTrue("筛选面板必须重新打开", waitFor(UI_TIMEOUT_MS, "面板重开") { textVisible("动态视图（保存的筛选）") })
        // 113修正：套用后 tag 仍生效；必须先在真实 UI 里再次点击该 tag chip 取消（toggle off），
        // 得到真实的 EPUB+无标签 状态，再断言对照可见性并保存对照视图。
        clickUnique("EPUB", inScrollablePanel = true)
        clickUnique(tagName, inScrollablePanel = true)
        assertTrue("对照筛选（EPUB+无标签）下乙书可见", waitFor(UI_TIMEOUT_MS, "乙可见") { bookPresent(bookBId) })
        assertTrue("对照筛选下甲书必须被排除", waitFor(UI_TIMEOUT_MS, "甲排除") { !bookPresent(bookAId) })
        assertTrue(
            "对照筛选摘要必须显示单维度（1 本 · EPUB）",
            waitFor(UI_TIMEOUT_MS, "单维度摘要") { textVisible("1 本 · EPUB") },
        )
        typeIntoNameField(controlViewName)
        clickUnique("保存", inScrollablePanel = true)
        assertTrue("保存后面板必须出现对照视图 pill", waitFor(UI_TIMEOUT_MS, "对照 pill") { clickableTextPresent(controlViewName) })
        assertTrue("真实 Store 文件必须包含对照视图名", waitFor(UI_TIMEOUT_MS, "store 含对照") { storeBytesContain(controlViewName) })
        writeEvidence(
            "60_control_saved",
            mapOf("name" to controlViewName,
                "pillPresent" to clickableTextPresent(controlViewName),
                "storeContainsControl" to storeBytesContain(controlViewName)),
        )

        // 6) 按目标行内「删除」精确删除目标视图（不碰对照行）。
        // 114：删除观测前置——71 滚动+重解析后全景取证；72 单次真实点击后立即取证（含语义树）；
        // 73 两秒后复采；74 原 20 秒结局先落盘再断言，唯一失败证据不丢失。
        markPhase("t112a_70_deleted")
        assertTrue("目标视图 pill 删除前必须在场", clickableTextPresent(targetViewName))
        scrollTargetDeleteIntoView(targetViewName)
        writeStoreSnapshot("71_pre_delete")
        writeObservation(
            "71_pre_delete_observation",
            captureDeleteObservation(includeTree = true).put("pairingsBeforeClick", pairingsJson()),
        )
        val clicked = clickTargetDeleteOnce(targetViewName)
        writeStoreSnapshot("72_post_click")
        writeObservation(
            "72_post_click_observation",
            captureDeleteObservation(includeTree = true)
                .put("clickedDeleteId", clicked.second.toString())
                .put("targetPillIdAtClick", clicked.first.toString()),
        )
        Thread.sleep(2_000)
        composeRule.waitForIdle()
        writeStoreSnapshot("73_two_seconds")
        writeObservation("73_two_seconds_observation", captureDeleteObservation(includeTree = true))
        val targetVanished = waitFor(UI_TIMEOUT_MS, "目标消失") { !clickableTextPresent(targetViewName) }
        writeStoreSnapshot("74_final")
        writeObservation("74_final_observation", captureDeleteObservation(includeTree = true).put("targetVanished", targetVanished))
        assertTrue("删除后目标 pill 必须从面板消失", targetVanished)
        assertTrue("删除后对照 pill 必须仍在面板", waitFor(UI_TIMEOUT_MS, "对照仍在") { clickableTextPresent(controlViewName) })
        assertTrue("真实 Store 文件必须不再含目标视图名", waitFor(UI_TIMEOUT_MS, "store 去目标") { !storeBytesContain(targetViewName) })
        assertTrue("真实 Store 文件必须仍含对照视图名", storeBytesContain(controlViewName))
        assertTrue("删除后当前筛选（对照组合）乙书仍可见", bookPresent(bookBId))
        writeEvidence(
            "70_deleted",
            mapOf("targetPillPresent" to clickableTextPresent(targetViewName),
                "controlPillPresent" to clickableTextPresent(controlViewName),
                "storeContainsTarget" to storeBytesContain(targetViewName),
                "storeContainsControl" to storeBytesContain(controlViewName)),
        )

        // 7) 套用对照关闭后重开，核目标缺席/对照仍在。
        // 116导航修正：不使用系统返回键（115实测 pressBack 后入口 0 节点、根因未明且未验），
        // 改走 50 步已验证的真实关闭路径——点击保留的对照视图 pill 套用；当前筛选（EPUB+无标签）
        // 与对照组合相同，套用为状态中性的真实 UI 动作，且 onApplySaved 固定 showFilterPanel=false。
        markPhase("t112a_80_reopen_check")
        // 80a 关闭前边界：确认当前筛选确与对照组合一致（乙可见/甲排除 + 单维度摘要），再落全景证据。
        assertTrue("关闭前对照组合下乙书必须可见", waitFor(UI_TIMEOUT_MS, "乙可见80") { bookPresent(bookBId) })
        assertTrue("关闭前对照组合下甲书必须被排除", waitFor(UI_TIMEOUT_MS, "甲排除80") { !bookPresent(bookAId) })
        assertTrue(
            "关闭前工具栏摘要必须仍为对照单维度（1 本 · EPUB）",
            waitFor(UI_TIMEOUT_MS, "对照摘要80") { textVisible("1 本 · EPUB") },
        )
        writeStoreSnapshot("81_pre_close")
        writeObservation(
            "81_pre_close_observation",
            captureDeleteObservation(includeTree = true).put("panelOpen", textVisible("动态视图（保存的筛选）"))
                .put("closePath", "apply-control-pill"),
        )
        composeRule.onNode(hasText(controlViewName) and hasClickAction()).performScrollTo().performClick()
        composeRule.waitForIdle()
        // 80b 关闭后边界：先落证据再断言——面板关闭、Shelf 页与工具栏存在、摘要/正反书状态仍同。
        writeStoreSnapshot("82_post_close")
        val panelClosedByApply = waitFor(UI_TIMEOUT_MS, "面板关闭(套用对照)") { !textVisible("动态视图（保存的筛选）") }
        writeObservation(
            "82_post_close_observation",
            captureDeleteObservation(includeTree = true).put("panelClosedByApply", panelClosedByApply)
                .put("shelfScreenPresent", topLevelPage() != null)
                .put("toolbarSummaryPresent", composeRule.onAllNodesWithTag("shelf-filter-summary").fetchSemanticsNodes().isNotEmpty())
                .put("toolbarSummaryText", toolbarSummaryText()),
        )
        assertTrue("套用对照后面板必须关闭", panelClosedByApply)
        assertTrue(
            "关闭后 Shelf 页与工具栏必须存在",
            waitFor(UI_TIMEOUT_MS, "工具栏存在") {
                composeRule.onAllNodesWithTag("shelf-filter-summary").fetchSemanticsNodes().isNotEmpty()
            },
        )
        assertTrue(
            "关闭后工具栏摘要必须仍为对照组合（1 本 · EPUB）",
            waitFor(UI_TIMEOUT_MS, "关闭后摘要") { textVisible("1 本 · EPUB") },
        )
        assertTrue("关闭后乙书必须仍可见", waitFor(UI_TIMEOUT_MS, "关闭后乙可见") { bookPresent(bookBId) })
        assertTrue("关闭后甲书必须仍被排除", waitFor(UI_TIMEOUT_MS, "关闭后甲排除") { !bookPresent(bookAId) })
        // 80c 真实点击工具栏重开面板
        clickUnique("shelf-filter-summary", byTag = true)
        val panelReopened = waitFor(UI_TIMEOUT_MS, "面板重开2") { textVisible("动态视图（保存的筛选）") }
        writeStoreSnapshot("83_post_reopen")
        writeObservation(
            "83_post_reopen_observation",
            captureDeleteObservation(includeTree = true).put("panelReopened", panelReopened),
        )
        assertTrue("重开后面板必须打开", panelReopened)
        val targetAbsentStable = stableAbsent(2000) { clickableTextPresent(targetViewName) }
        writeObservation(
            "84_reopen_verdict",
            JSONObject()
                .put("targetPillPresent", clickableTextPresent(targetViewName))
                .put("controlPillPresent", clickableTextPresent(controlViewName))
                .put("targetAbsentStable", targetAbsentStable)
                .put("storeContainsTarget", storeBytesContain(targetViewName))
                .put("storeContainsControl", storeBytesContain(controlViewName)),
        )
        assertTrue("重开后目标视图必须缺席（稳定断言）", targetAbsentStable)
        assertTrue("重开后对照视图必须仍在", clickableTextPresent(controlViewName))
        writeEvidence(
            "80_reopen_check",
            mapOf("targetPillPresent" to clickableTextPresent(targetViewName),
                "controlPillPresent" to clickableTextPresent(controlViewName),
                "storeFileBytes" to storeFileBytes()),
        )

        // 8) 原实体关系不被「保存动态视图」改写（动态快照≠静态书单/分类学）
        markPhase("t112a_90_integrity")
        val snapshotAfter = taxonomySnapshot()
        assertEquals("书/标签/连接表原实体关系必须前后逐行一致", snapshotBefore, snapshotAfter)
        writeEvidence(
            "90_integrity",
            mapOf("rowsBefore" to snapshotBefore, "rowsAfter" to snapshotAfter, "equal" to (snapshotBefore == snapshotAfter)),
        )
    }

    // ── UI 工具 ──────────────────────────────────────────────────────────────

    /** 111 已验证口径：顶层页以 shelf-screen/home-screen 标签判定（生产起始页固定 Home）。 */
    /** 工具栏筛选摘要原文（无节点时空串），只读观测，不参与断言逻辑。 */
    private fun toolbarSummaryText(): String {
        val nodes = composeRule.onAllNodesWithTag("shelf-filter-summary").fetchSemanticsNodes()
        return nodes.firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""
    }

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun clickTopLevelTab(label: String) {
        val tabs = composeRule.onAllNodes(hasText(label) and isSelectable())
        val nodes = tabs.fetchSemanticsNodes()
        assertTrue("顶层 tab「$label」必须唯一可选中（实得 ${nodes.size}）", nodes.size == 1)
        tabs[0].performClick()
        composeRule.waitForIdle()
    }

    private fun bookShelfPresent(): Boolean =
        composeRule.onAllNodesWithTag("shelf-route").fetchSemanticsNodes().isNotEmpty()

    private fun bookPresent(id: String): Boolean =
        composeRule.onAllNodesWithTag("book-tile-$id").fetchSemanticsNodes().isNotEmpty()

    private fun textVisible(text: String): Boolean =
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun clickableTextPresent(text: String): Boolean =
        composeRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()

    private fun clickUnique(text: String, byTag: Boolean = false, inScrollablePanel: Boolean = false) {
        if (byTag) {
            val nodes = composeRule.onAllNodesWithTag(text).fetchSemanticsNodes()
            assertTrue("节点 $text 必须唯一（实得 ${nodes.size}）", nodes.size == 1)
            composeRule.onAllNodesWithTag(text)[0].performClick()
        } else {
            val interaction = composeRule.onAllNodes(hasText(text) and hasClickAction())
            val nodes = interaction.fetchSemanticsNodes()
            assertTrue("可点击文本「$text」必须唯一（实得 ${nodes.size}）", nodes.size == 1)
            // FilterSheet 内容在 verticalScroll Column 内：滚动到位再点击，查到节点不等于触摸送达。
            if (inScrollablePanel) interaction[0].performScrollTo().performClick()
            else interaction[0].performClick()
        }
        composeRule.waitForIdle()
    }

    /** 面板内唯一可编辑节点（占位文案「给当前筛选起个名字」在子树内）——沿用 94 呈现门禁口径。 */
    private fun typeIntoNameField(value: String) {
        assertTrue(
            "命名输入框必须在现有预算内真实呈现",
            waitFor(UI_TIMEOUT_MS, "命名输入框呈现") {
                composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                    .any { n -> subtreeTexts(n).any { it.contains(NAME_FIELD_LABEL) } }
            },
        )
        val editables = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .filter { n -> subtreeTexts(n).any { it.contains(NAME_FIELD_LABEL) } }
        assertEquals("命名输入框必须唯一匹配（实得 ${editables.size}）", 1, editables.size)
        val target = editables.single()
        val all = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val idx = all.indexOfFirst { it.boundsInRoot == target.boundsInRoot }
        assertTrue("已解析输入框必须仍在可编辑集合中", idx >= 0)
        composeRule.onAllNodes(hasSetTextAction())[idx].performTextClearance()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[idx].performTextInput(value)
        composeRule.waitForIdle()
    }

    /**
     * 115 精确配对：布局 Row 被语义树剪除后，pill Surface 节点与其行内 TextButton(删除)
     * 在共享 parent（滚动 Column）的 children 中相邻——pill 的紧邻后继即其行内删除。
     * 只认有序紧邻关系，不做 any-sibling 泛匹配、不取第一个/最近距离回退。
     * 返回 (pill 节点, 该 pill 紧邻后继删除节点)；结构不符即抛出并带 parent children 全序。
     */
    private fun pairPillWithDelete(viewName: String): Pair<SemanticsNode, SemanticsNode> {
        val pills = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { pill -> pill.config.getOrNull(SemanticsProperties.Text)?.any { it.text == viewName } == true }
        assertTrue("名称为「$viewName」的 pill 必须唯一（实得 ${pills.size}）", pills.size == 1)
        val pill = pills.single()
        val parent = pill.parent
        assertTrue("pill 必须有语义 parent", parent != null)
        val children = parent!!.children
        val pillIndex = children.indexOfFirst { it.id == pill.id }
        assertTrue("pill 必须在其 parent children 中（index=$pillIndex）", pillIndex >= 0)
        val next = children.getOrNull(pillIndex + 1)
        val nextIsDelete = next != null &&
            next.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "删除" } == true
        val structure = children.joinToString(" | ") { c ->
            "#" + c.id + ":" + subtreeTexts(c).take(3).joinToString(",")
        }
        assertTrue(
            "pill「$viewName」的紧邻后继必须是本行「删除」（实际后继=${next?.let { subtreeTexts(it).take(2) }}；parent children 全序：$structure）",
            nextIsDelete,
        )
        return pill to next!!
    }

    /** 两个视图各自的 pill-删除配对必须同时可解析且互不相同（排除另一 pill/删除的交叉匹配）。 */
    private fun resolveBothPairings(): Pair<Pair<SemanticsNode, SemanticsNode>, Pair<SemanticsNode, SemanticsNode>> {
        val targetPair = pairPillWithDelete(targetViewName)
        val controlPair = pairPillWithDelete(controlViewName)
        assertTrue(
            "目标行与对照行的删除必须是不同节点（目标删除 #${targetPair.second.id}，对照删除 #${controlPair.second.id}）",
            targetPair.second.id != controlPair.second.id,
        )
        return targetPair to controlPair
    }

    private fun pairingsJson(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        val (t, c) = resolveBothPairings()
        arr.put(pairJson("target", t))
        arr.put(pairJson("control", c))
        return arr
    }

    private fun pairJson(label: String, pair: Pair<SemanticsNode, SemanticsNode>): org.json.JSONObject =
        org.json.JSONObject()
            .put("pair", label)
            .put("pillId", pair.first.id.toString())
            .put("pillText", subtreeTexts(pair.first).firstOrNull() ?: "")
            .put("pillBounds", pair.first.boundsInRoot.toString())
            .put("deleteId", pair.second.id.toString())
            .put("deleteBounds", pair.second.boundsInRoot.toString())
            .put("parentId", pair.first.parent?.id.toString())

    /** 115：滚动前用精确配对定位目标删除并滚入视口（滚动后由调用方重新解析核同）。 */
    private fun scrollTargetDeleteIntoView(viewName: String) {
        val (_, deleteNode) = pairPillWithDelete(viewName)
        composeRule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes().indexOfFirst { it.id == deleteNode.id }.also {
                assertTrue("配对删除节点必须仍在可点击集合中", it >= 0)
                composeRule.onAllNodes(hasClickAction())[it].performScrollTo()
            }
        composeRule.waitForIdle()
    }

    /** 115：滚动后重新配对（核 pill 名与 delete id），单次真实点击目标行删除；返回 (pillId, deleteId)。 */
    private fun clickTargetDeleteOnce(viewName: String): Pair<Int, Int> {
        val (pill, deleteNode) = pairPillWithDelete(viewName)
        composeRule.onAllNodes(hasText("删除") and hasClickAction())
            .fetchSemanticsNodes().indexOfFirst { it.id == deleteNode.id }.also {
                assertTrue("滚动后配对删除节点必须仍可定位", it >= 0)
                composeRule.onAllNodes(hasText("删除") and hasClickAction())[it].performClick()
            }
        composeRule.waitForIdle()
        return pill.id to deleteNode.id
    }

    /** 114：删除边界全景观测——双方 pill、全部删除候选（id/bounds/尺寸/可见高/行归属名）、窗口与真实 IME/焦点状态。 */
    private fun captureDeleteObservation(includeTree: Boolean = false): JSONObject {
        val obs = JSONObject()
            .put("targetPillPresent", clickableTextPresent(targetViewName))
            .put("controlPillPresent", clickableTextPresent(controlViewName))
        val deletes = composeRule.onAllNodes(hasText("删除") and hasClickAction()).fetchSemanticsNodes()
        // 115：候选归属来自精确配对（紧邻后继关系），并落盘 parent children 全序供审查。
        val pairedDeleteIds = try {
            val (t, c) = resolveBothPairings()
            mapOf(t.second.id to targetViewName, c.second.id to controlViewName)
        } catch (t: Throwable) {
            obs.put("pairingProbeError", t.toString())
            emptyMap()
        }
        val arr = JSONArray()
        for (d in deletes) {
            arr.put(
                JSONObject()
                    .put("nodeId", d.id.toString())
                    .put("boundsInRoot", d.boundsInRoot.toString())
                    .put("boundsInWindow", d.boundsInWindow.toString())
                    .put("sizePx", d.size.width.toString() + "x" + d.size.height)
                    .put("visibleHeightPx", visibleHeightPx(d))
                    .put("pairedViewName", pairedDeleteIds[d.id] ?: "unpaired")
                    .put("precedingSiblingTexts", d.parent?.children?.indexOfFirst { it.id == d.id }?.takeIf { it > 0 }?.let { i ->
                        subtreeTexts(d.parent!!.children[i - 1]).take(2).joinToString(",")
                    } ?: "unknown"),
            )
        }
        obs.put("deleteCandidates", arr)
        try {
            val (tp, cp) = resolveBothPairings()
            val parent = tp.first.parent
            if (parent != null) {
                obs.put(
                    "sharedParentChildrenOrder",
                    parent.children.joinToString(" | ") { c -> "#" + c.id + ":" + subtreeTexts(c).take(2).joinToString(",") },
                )
            }
        } catch (t: Throwable) {
            obs.put("parentOrderProbeError", t.toString())
        }
        obs.put("windowPx", windowSizePx())
        try {
            scenario?.onActivity { activity ->
                obs.put("currentFocus", activity.currentFocus?.javaClass?.simpleName ?: "null")
                val imm = activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                obs.put("imeActive", imm.isActive)
            }
        } catch (t: Throwable) {
            obs.put("imeProbeError", t.toString())
        }
        if (includeTree) {
            // 115：模态弹层下存在多个 root（主窗口+弹层窗口），逐 root 打印全部树，不做唯一性断言、不截断。
            // printToString 公开 API 在 SemanticsNodeInteraction 上（SemanticsNode 版本为 internal）。
            val rootInteractions = composeRule.onAllNodes(isRoot())
            val rootNodes = rootInteractions.fetchSemanticsNodes()
            val treeTexts = (0 until rootNodes.size).map { i ->
                "==== root[$i] id=" + rootNodes[i].id + " ====\n" + rootInteractions[i].printToString(maxDepth = 60)
            }
            obs.put("rootCount", rootNodes.size)
            obs.put("treeChars", treeTexts.sumOf { it.length })
            obs.put("tree", treeTexts.joinToString("\n\n"))
        }
        return obs
    }

    private fun windowSizePx(): String {
        var size = "unavailable"
        scenario?.onActivity { activity ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            size = metrics.widthPixels.toString() + "x" + metrics.heightPixels
        }
        return size
    }

    private fun visibleHeightPx(node: SemanticsNode): Int {
        var windowHeight = 0f
        scenario?.onActivity { activity ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            windowHeight = metrics.heightPixels.toFloat()
        }
        if (windowHeight <= 0f) return -1
        val b = node.boundsInRoot
        val top = b.top.coerceAtLeast(0f)
        val bottom = b.bottom.coerceAtMost(windowHeight)
        return (bottom - top).coerceAtLeast(0f).toInt()
    }

    /** 114：单一真实 shelf_saved_views 文件原字节落盘+sizeSHA；缺文件与读失败分清，探测失败不当缺席。 */
    private fun writeStoreSnapshot(tag: String) {
        val f = storeFile()
        val meta = JSONObject().put("tag", tag)
        when {
            !f.exists() -> meta.put("status", "missing")
            else -> try {
                val bytes = f.readBytes()
                File(resultsDir, "t112a_" + tag + "_store.preferences_pb").writeBytes(bytes)
                val text = bytes.toString(Charsets.UTF_8)
                meta.put("status", "ok")
                    .put("bytes", bytes.size)
                    .put("sha256", sha256Hex(bytes))
                    .put("containsTarget", text.contains(targetViewName))
                    .put("containsControl", text.contains(controlViewName))
            } catch (t: Throwable) {
                meta.put("status", "read-error").put("error", t.toString())
            }
        }
        File(resultsDir, "t112a_" + tag + "_store_meta.json").writeText(meta.toString(2))
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format("%02x", it) }

    private fun subtreeTexts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { out.add(it.text) }
        node.children.forEach { child -> out.addAll(subtreeTexts(child)) }
        return out
    }

    private fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun waitFor(timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (block()) return true
            Thread.sleep(120)
        }
        return block()
    }

    /** 稳定缺席：连续窗口内多次采样均为 false，避免把瞬时未呈现误判为缺席。 */
    private fun stableAbsent(settleMs: Long, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + settleMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return false
            Thread.sleep(200)
        }
        return !block()
    }

    // ── 只读 Store / DB ──────────────────────────────────────────────────────

    /** 只读核真实 DataStore 原始文件（不构造第二个 DataStore 实例，避免单文件多实例冲突）。 */
    private fun storeFile(): File = File(File(context.filesDir, "datastore"), "shelf_saved_views.preferences_pb")

    private fun storeBytesContain(s: String): Boolean =
        storeFile().takeIf { it.isFile }?.readBytes()?.toString(Charsets.UTF_8)?.contains(s) == true

    private fun storeFileBytes(): Long = storeFile().takeIf { it.isFile }?.length() ?: -1L

    /** 本批相关实体行 + 分类学全局计数的只读快照（保存动态视图不得改写这些行）。 */
    private fun taxonomySnapshot(): List<String> {
        val rows = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "SELECT id||'|'||title||'|'||format||'|'||ifnull(deleted_at,'null') FROM books WHERE id LIKE '%$runId%' ORDER BY id",
        ).use { c ->
            while (c.moveToNext()) rows += "book:${c.getString(0)}"
        }
        db.openHelper.readableDatabase.query(
            "SELECT id||'|'||name||'|'||ifnull(deleted_at,'null') FROM tags WHERE id LIKE '%$runId%' ORDER BY id",
        ).use { c ->
            while (c.moveToNext()) rows += "tag:${c.getString(0)}"
        }
        db.openHelper.readableDatabase.query(
            "SELECT book_id||'|'||tag_id FROM book_tag WHERE book_id LIKE '%$runId%' ORDER BY book_id,tag_id",
        ).use { c ->
            while (c.moveToNext()) rows += "book_tag:${c.getString(0)}"
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM shelves").use { c ->
            if (c.moveToFirst()) rows += "count:shelves=${c.getInt(0)}"
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM categories").use { c ->
            if (c.moveToFirst()) rows += "count:categories=${c.getInt(0)}"
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM shelf_book").use { c ->
            if (c.moveToFirst()) rows += "count:shelf_book=${c.getInt(0)}"
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM book_category").use { c ->
            if (c.moveToFirst()) rows += "count:book_category=${c.getInt(0)}"
        }
        return rows.sorted()
    }

    private fun rawOne(sql: String): String? =
        db.openHelper.readableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
        }

    // ── 证据 ─────────────────────────────────────────────────────────────────

    private fun writeEvidence(name: String, data: Map<String, Any?>) {
        val json = JSONObject()
        json.put("runId", runId)
        json.put("phase", lastPhase)
        json.put("fixtureLevel", "production DAO seed; real UI save/apply/delete from FilterSheet")
        for ((k, v) in data) {
            json.put(k, if (v is List<*>) JSONArray(v) else v)
        }
        File(resultsDir, "t112a_$name.json").writeText(json.toString(2))
    }

    /** 114：JSONObject 观测直接落盘（writeEvidence 只收 Map，观测为嵌套结构）。 */
    private fun writeObservation(name: String, json: JSONObject) {
        json.put("runId", runId)
        json.put("phase", lastPhase)
        json.put("fixtureLevel", "production DAO seed; real UI save/apply/delete from FilterSheet")
        File(resultsDir, "t112a_" + name + ".json").writeText(json.toString(2))
    }

    private fun markPhase(phase: String) {
        lastPhase = phase
        File(resultsDir, "t112a_phase_marker.json").writeText(JSONObject().put("phase", phase).put("at", Instant.now().toString()).toString(2))
    }
}
