package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
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
import com.creationreadingassistant.ui.screen.reader.sheets.ReaderExportReceipts
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import io.mockk.mockk
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务94-v2：分类编辑与来源保留 + 真实导出入口的**真实设备旅程**。
 *
 * 相对 94-v1 的校正（Root 已指出）：
 *  - 不断言首页进度非空（生产 `buildInspirationPayload` 在进度 ≤0 时写 null）；
 *    改为记录编辑前**完整 source**，仅改分类后断言整对象相等（含 nullable progressPercent）；
 *  - 分类不在编辑器新建：本轮先经真实阅读面板「记为灵感」创建分类，再到编辑器选择已有项；
 *  - 筛选补足排除对照与 AND 组合（关键词 × 分类）；
 *  - 导出走真实用户入口（阅读器 → 笔记与标注 → 导出 → 系统文档选择器），
 *    从系统返回的 URI 读回**实际字节**并保存到本轮证据目录；
 *  - 来源跳转通过真实「查阅原文位置」入口验证，不以 locator 字符串存在代替；
 *  - 等待谓词只用不抛异常的语义查询，意外异常保留根因，不转成 false。
 *
 * 边界：只驱动真实产品入口，不直接向 DB 写用户流程数据；本轮导出文件在保存原始字节与
 * size/SHA 后按精确 URI 删除；其余本轮数据由外层卸载包 + 隔离实例恢复合同处理。
 */
@RunWith(AndroidJUnit4::class)
class InspirationCategorySourceRetentionDeviceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private lateinit var importer: ShelfImporter
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private var lastPhase: String = "start"
    private var bookId: String = ""
    private var itemWithCategory: String = ""
    private var controlItem: String = ""
    private var capturedSelection: String = ""
    private var categoryA: String = ""
    private var categoryB: String = ""
    private var tagName: String = ""

    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        const val TAG = "CRA94V2"
        const val UI_TIMEOUT_MS = 20_000L
        const val SAVE_TIMEOUT_MS = 20_000L
        const val PICKER_TIMEOUT_MS = 25_000L
        const val EXPORT_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 120L
        const val IMPORT_TIMEOUT_MS = 30_000L
        const val IMPORT_SOURCE_LABEL = "task94v2-device"
        const val FIXTURE_NAME = "task94v2-neutral"
        const val MAX_A11Y_NODES = 600
        val SAVE_LABELS = setOf("保存", "Save")
        val DOCUMENTS_UI_PACKAGES = setOf("com.android.documentsui", "com.google.android.documentsui")
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = requireNotNull(InstrumentationRegistry.getArguments().getString("craRunId")) { "Root must bind craRunId" }
        require(runId.matches(Regex("T94V4-[A-Za-z0-9-]+")))
        resultsDir = File(context.getExternalFilesDir(null), "task94v4-results-$runId").apply { mkdirs() }
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
            File(resultsDir, "t94v2_99_summary.json").writeText(
                JSONObject()
                    .put("runId", runId)
                    .put("lastPhase", lastPhase)
                    .put("bookId", bookId)
                    .put("itemWithCategory", itemWithCategory)
                    .put("controlItem", controlItem)
                    .put("categoryA", categoryA)
                    .put("categoryB", categoryB)
                    .put("tag", tagName)
                    .put("policy", "本轮导出文件按精确 URI 清理；其余数据由外层卸载包 + 隔离实例恢复合同处理")
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

    // ── 旅程 ─────────────────────────────────────────────────────────────────

    @Test
    fun categoryOnlyEditKeepsSource_andRealExportContainsTags() {
        runBlocking {
            // P0：真实生产导入
            markPhase("t94v2_00_import")
            val file = fixtureFile()
            file.writeText(txtBody())
            val beforeIds = activeBookIds()
            importer.importFiles(listOf(Uri.fromFile(file)), IMPORT_SOURCE_LABEL)
            assertTrue(
                "真实导入必须且只能新增一行本轮 txt 书",
                waitFor("t94v2_00_import", IMPORT_TIMEOUT_MS, "导入新增一行") {
                    val added = activeBookIds() - beforeIds
                    added.size == 1 &&
                        rawOne("SELECT format FROM books WHERE id='${added.first()}' AND deleted_at IS NULL") == "txt"
                },
            )
            bookId = (activeBookIds() - beforeIds).single()
            writeEvidence("t94v2_00_import", true, mapOf("bookId" to bookId, "fixture" to file.absolutePath))

            // P1：真实打开
            markPhase("t94v2_10_open")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            composeRule.waitForIdle()
            assertTrue("顶层页面必须出现", waitFor("t94v2_10_open", UI_TIMEOUT_MS, "顶层页面") { topLevelPage() != null })
            assertTrue("必须能进入书架", ensureOnShelf("t94v2_10_open"))
            assertTrue(
                "书架必须出现本轮书籍卡片",
                waitFor("t94v2_10_open", UI_TIMEOUT_MS, "书架卡片") {
                    composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
                },
            )
            composeRule.onNodeWithTag("book-tile-$bookId").performClick()
            composeRule.waitForIdle()
            assertTrue("阅读器正文必须就绪", waitFor("t94v2_10_open", UI_TIMEOUT_MS, "正文就绪") { readerReady() })
            assertTrue(
                "必须显示本轮正文标题",
                waitFor("t94v2_10_open", UI_TIMEOUT_MS, "章节标题") {
                    textVisible("取材真实接缝") || textVisible("后续正文")
                },
            )
            writeEvidence("t94v2_10_open", true, mapOf("entry" to "书架 → book-tile → 阅读器"))

            // P2：第一条灵感（带分类 A、标签、想法）
            markPhase("t94v2_20_item_with_category")
            val firstExcerpt = selectAndOpenSheet()
            categoryA = "分类A-$runId"
            categoryB = "分类B-$runId"
            tagName = "标签-$runId"
            val itemTitle = "T94A-$runId"
            // 真实阅读面板创建 A、B 两个分类（均落库），保存前把 B 点掉，使本条只带 A。
            fillSheet(
                title = itemTitle,
                ideas = "个人想法A：$runId",
                tag = tagName,
                createCategories = listOf(categoryA, categoryB),
                deselectCategories = listOf(categoryB),
            )
            saveSheet("t94v2_20_item_with_category")
            itemWithCategory = inspirationIdByTitle(itemTitle)
            assertTrue("带分类条目必须落库", itemWithCategory.isNotBlank())
            writeEvidence(
                "t94v2_20_item_with_category", true,
                mapOf("id" to itemWithCategory, "selection" to firstExcerpt, "category" to categoryA),
            )

            // 重复取材缺陷沿用v3原始诊断，原因仍未确证；不重复制造诊断选区阻塞核心旅程。
            writeEvidence("t94v4_25_diagnostic_reused", true, mapOf(
                "repeatSelection" to "unresolved; reuse v3 evidence, not re-executed or accepted",
            ))

            // P3：第二条对照灵感（无分类）—— 经真实「新建灵感」编辑器创建，
            // 不依赖重复取材路径，避免无关前置阻断核心来源保留检查。
            markPhase("t94v2_30_control_item")
            backToShelf()
            clickTopLevelTab("灵感")
            val controlTitle = "T94B-$runId"
            try {
                createControlViaEditor(controlTitle)
                controlItem = inspirationIdByTitle(controlTitle)
                assertTrue("对照条目必须落库", controlItem.isNotBlank())
            } catch (original: Throwable) {
                // 失败证据不能覆盖原始保存/查行异常；每项取证错误保留为suppressed。
                fun preserve(action: () -> Unit) {
                    try { action() } catch (evidenceError: Throwable) { original.addSuppressed(evidenceError) }
                }
                preserve { File(resultsDir, "t94v6_control_save_failure.txt").writeText(original.stackTraceToString()) }
                preserve {
                    File(resultsDir, "t94v6_control_save_failure.tree.txt").writeText(
                        composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20),
                    )
                }
                preserve { captureControlSaveBoundary("failure", controlTitle) }
                preserve { saveScreenshot("t94v6_control_save_failure") }
                throw original
            }
            writeEvidence("t94v2_30_control_item", true, mapOf("id" to controlItem, "entry" to "灵感 → 新建灵感 → 保存灵感"))
            // 保存后停留在详情：回到列表再切书架，供后续步骤从书架开始
            backToInspirationList()
            clickTopLevelTab("书架")
            assertTrue("对照条目保存后必须返回书架", waitFor(lastPhase, UI_TIMEOUT_MS, "书架") { topLevelPage() == "shelf" })

            // P4：记录编辑前完整 source（进度允许 null）
            markPhase("t94v2_40_capture_source")
            val beforePayload = requireNotNull(
                rawOne("SELECT payload FROM inspirations WHERE id='$itemWithCategory'"),
            ) { "灵感 payload 不得为空" }
            val before = payloadJson.decodeFromString(InspirationPayloadData.serializer(), beforePayload)
            val beforeSource = requireNotNull(before.source) { "真实划词保存必须带来源快照" }
            assertNotNull("来源必须带章节", beforeSource.chapterTitle)
            assertNotNull("来源必须带精确 locator", beforeSource.locatorJson)
            assertEquals("本轮第一条必须带分类 A", 1, before.categoryIds.size)
            assertEquals("本轮第一条必须带标签", true, before.tags.contains(tagName))
            writeEvidence(
                "t94v2_40_capture_source", true,
                mapOf(
                    "sourceChapter" to beforeSource.chapterTitle,
                    "progressPercent" to beforeSource.progressPercent,
                    "locator" to beforeSource.locatorJson,
                    "tags" to before.tags.joinToString(","),
                    "categoryIds" to before.categoryIds.joinToString(","),
                ),
            )

            // P5：灵感 → 详情 → 编辑 → 只选分类 B（来源输入不动）→ 保存修改
            markPhase("t94v2_50_category_only_edit")
            backToShelf()
            clickTopLevelTab("灵感")
            assertTrue("灵感列表必须出现本轮条目", waitFor("t94v2_50_category_only_edit", UI_TIMEOUT_MS, "列表条目") {
                textVisible("T94A-$runId")
            })
            openInspirationDetail("T94A-$runId")
            assertTrue("详情必须显示分类 A", waitFor("t94v2_50_category_only_edit", UI_TIMEOUT_MS, "详情分类") {
                textVisible(categoryA)
            })
            composeRule.onNode(hasContentDescription("编辑灵感")).performClick()
            composeRule.waitForIdle()
            assertTrue(
                "编辑器必须出现已有分类 A 与 B（来自真实字典，不是测试新建）",
                waitFor("t94v2_50_category_only_edit", UI_TIMEOUT_MS, "分类胶囊") {
                    textVisible(categoryA) && textVisible(categoryB)
                },
            )

            // ── 边界 1：分类选择（精确 ID + 唯一/可滚动可见/可点击） ──
            val idA = requireNotNull(categoryIdByName(categoryA)) { "分类A必须存在于本轮分类字典" }
            val idB = requireNotNull(categoryIdByName(categoryB)) { "分类B必须存在于本轮分类字典" }
            assertEquals("编辑前本条必须只带分类A的精确ID", listOf(idA), before.categoryIds)

            assertTrue("编辑器内分类 B 胶囊必须唯一", waitFor("t94v2_50_category_only_edit", UI_TIMEOUT_MS, "分类B胶囊") {
                composeRule.onAllNodesWithText(categoryB).fetchSemanticsNodes().size == 1
            })
            val categoryBNode = composeRule.onNodeWithText(categoryB)
            categoryBNode.performScrollTo()
            composeRule.waitForIdle()
            assertTrue("分类 B 胶囊滚动后必须真实可见", categoryBNode.isDisplayed())
            File(resultsDir, "t94v8_categoryB_before_click.json").writeText(
                JSONObject()
                    .put("categoryB", categoryB)
                    .put("categoryBId", idB)
                    .put("nodeCount", composeRule.onAllNodesWithText(categoryB).fetchSemanticsNodes().size)
                    .put("boundsInRoot", categoryBNode.fetchSemanticsNode().boundsInRoot.toString())
                    .put("boundsInWindow", categoryBNode.fetchSemanticsNode().boundsInWindow.toString())
                    .put("keyboardVisible", controlKeyboardVisible() ?: JSONObject.NULL)
                    .put("tree", composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20).take(20000))
                    .toString(2),
            )
            categoryBNode.performClick()
            composeRule.waitForIdle()
            File(resultsDir, "t94v8_categoryB_after_click.json").writeText(
                JSONObject()
                    .put("categoryB", categoryB)
                    .put("categoryBId", idB)
                    .put("nodeCount", composeRule.onAllNodesWithText(categoryB).fetchSemanticsNodes().size)
                    .put("keyboardVisible", controlKeyboardVisible() ?: JSONObject.NULL)
                    .put("tree", composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20).take(20000))
                    .toString(2),
            )

            // ── 边界 2：保存退出（复用现有门禁） ──
            val beforeUpdatedAt = rowUpdatedAt(itemWithCategory)
            val beforePayloadRaw = requireNotNull(rowPayload(itemWithCategory))
            saveEditorAndWaitExit("t94v2_50_category_only_edit", "保存修改")

            // ── 边界 3：同一精确行的真实异步持久化 ──
            // 生产 saveInspiration 用 viewModelScope.launch 异步 upsert；编辑器退出只证明导航。
            // 有效更新见证 = 同一精确行的 payload 与保存前不同（分类变化必然改 payload）；
            // 不假定 revision 递增，也不重复点击保存。
            var persisted = false
            val persistObserved = waitFor("t94v2_50_category_only_edit", SAVE_TIMEOUT_MS, "同一精确行持久化A+B") {
                val raw = rowPayload(itemWithCategory)
                val parsed = raw?.let {
                    runCatching { payloadJson.decodeFromString(InspirationPayloadData.serializer(), it) }.getOrNull()
                }
                persisted = raw != null && raw != beforePayloadRaw && parsed != null &&
                    parsed.categoryIds.toSet() == setOf(idA, idB)
                persisted
            }
            val afterUpdatedAt = rowUpdatedAt(itemWithCategory)
            val afterPayload = requireNotNull(rowPayload(itemWithCategory))
            val after = payloadJson.decodeFromString(InspirationPayloadData.serializer(), afterPayload)
            File(resultsDir, "t94v8_persist_wait.json").writeText(
                JSONObject()
                    .put("inspirationId", itemWithCategory)
                    .put("beforeUpdatedAt", beforeUpdatedAt ?: JSONObject.NULL)
                    .put("afterUpdatedAt", afterUpdatedAt ?: JSONObject.NULL)
                    .put("updatedAtChanged", beforeUpdatedAt != afterUpdatedAt)
                    .put("payloadChanged", afterPayload != beforePayloadRaw)
                    .put("observedPersisted", persistObserved && persisted)
                    .put("expectedCategoryIds", "$idA,$idB")
                    .put("actualCategoryIds", after.categoryIds.joinToString(","))
                    .put("beforePayload", beforePayloadRaw)
                    .put("afterPayload", afterPayload)
                    .toString(2),
            )
            if (!persistObserved || !persisted) {
                File(resultsDir, "t94v8_category_edit_fail.tree.txt")
                    .writeText(composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20))
                saveScreenshot("t94v8_category_edit_fail")
            }
            assertTrue("必须在现有阶段预算内观察到同一精确行的真实持久化更新", persistObserved && persisted)

            assertEquals("分类必须精确等于 A+B（按ID）", setOf(idA, idB), after.categoryIds.toSet())
            assertEquals("仅改分类后完整来源必须与编辑前完全相等", before.source, after.source)
            assertEquals("标签不得被清掉", before.tags, after.tags)
            writeEvidence(
                "t94v2_50_category_only_edit", true,
                mapOf(
                    "sourceStrictEqual" to true,
                    "categoryIds" to after.categoryIds.joinToString(","),
                    "updatedAtChanged" to (beforeUpdatedAt != afterUpdatedAt),
                    "locator" to after.source?.locatorJson,
                ),
            )

            // P6：再次编辑 → 分类回显
            markPhase("t94v2_60_reopen")
            composeRule.onNode(hasContentDescription("编辑灵感")).performClick()
            composeRule.waitForIdle()
            assertTrue(
                "再次编辑必须回显 A 与 B",
                waitFor("t94v2_60_reopen", UI_TIMEOUT_MS, "分类回显") {
                    textVisible(categoryA) && textVisible(categoryB)
                },
            )
            writeEvidence("t94v2_60_reopen", true, mapOf("echo" to true))
            composeRule.onNode(hasContentDescription("返回")).performClick()
            composeRule.waitForIdle()

            // P7：筛选——匹配项保留、不匹配项排除、关键词 × 分类 AND
            markPhase("t94v2_70_filter")
            backToInspirationList()
            assertTrue("分类筛选导轨必须出现本轮分类 A", waitFor("t94v2_70_filter", UI_TIMEOUT_MS, "分类导轨") {
                textVisible(categoryA)
            })
            assertTrue("筛选前两条都必须可见（对照成立前提）", textVisible("T94A-$runId") && textVisible("T94B-$runId"))
            // 列表条目的分类胶囊与筛选导轨都用分类名；筛选胶囊文本带条目数（"名称 1"），
            // 用精确文本定位导轨胶囊，避免误点条目内的同名胶囊。
            val filterPillText = "$categoryA 1"
            assertTrue("分类筛选胶囊必须唯一可定位：$filterPillText", waitFor(lastPhase, UI_TIMEOUT_MS, "筛选胶囊") {
                composeRule.onAllNodesWithText(filterPillText).fetchSemanticsNodes().size == 1
            })
            composeRule.onNodeWithText(filterPillText).performClick()
            composeRule.waitForIdle()
            // 观察范围限定真实列表卡片：用本轮精确灵感ID的 testTag，避免全屏文本误命中。
            assertTrue(
                "按分类 A 筛选后匹配卡片必须存在",
                waitFor("t94v2_70_filter", UI_TIMEOUT_MS, "匹配卡片存在") { cardPresent(itemWithCategory) },
            )
            assertTrue(
                "按分类 A 筛选后不匹配的对照卡片必须被排除",
                waitFor("t94v2_70_filter", UI_TIMEOUT_MS, "对照卡片消失") { !cardPresent(controlItem) },
            )
            // AND 组合：关键词命中对照项 + 分类 A → 对照项仍被分类条件排除
            // 失败边界证据（仅记录，不改行为）：搜索入口节点、点击后可编辑节点数与语义树。
            File(resultsDir, "t94v8_search_before_click.json").writeText(
                JSONObject()
                    .put("searchIconNodes",
                        composeRule.onAllNodesWithContentDescription("搜索灵感").fetchSemanticsNodes().size)
                    .put("editableNodes", composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                    .put("tree", composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 15).take(12000))
                    .toString(2),
            )
            composeRule.onNode(hasContentDescription("搜索灵感")).performClick()
            composeRule.waitForIdle()
            File(resultsDir, "t94v8_search_after_click.json").writeText(
                JSONObject()
                    .put("searchIconNodes",
                        composeRule.onAllNodesWithContentDescription("搜索灵感").fetchSemanticsNodes().size)
                    .put("editableNodes", composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                    .put("placeholderNodes",
                        composeRule.onAllNodesWithText("搜索标题、正文、标签或来源", substring = true)
                            .fetchSemanticsNodes().size)
                    .put("tree", composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 15).take(12000))
                    .toString(2),
            )
            // 呈现门禁：点击只改变状态，输入框需要一次真实重组才出现在语义树中；
            // 在既有阶段预算内等待可编辑节点出现（不重复点击、不新增全局超时）。
            val searchPresented = waitFor(lastPhase, UI_TIMEOUT_MS, "搜索输入框真实呈现") {
                composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            File(resultsDir, "t94v9_search_presented.json").writeText(
                JSONObject()
                    .put("presented", searchPresented)
                    .put("editableNodes", composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                    .put("placeholderNodes",
                        composeRule.onAllNodesWithText("搜索标题、正文、标签或来源", substring = true)
                            .fetchSemanticsNodes().size)
                    .toString(2),
            )
            assertTrue("点击搜索后输入框必须在现有预算内真实呈现", searchPresented)
            val searchField = resolveEditableField("搜索标题、正文、标签或来源")
            assertTrue("搜索框必须唯一匹配：${searchField.diagnostics}", searchField.unique && searchField.node != null)
            val searchNode = searchField.node!!
            editableInteractionFor(searchNode).performTextClearance()
            composeRule.waitForIdle()
            editableInteractionFor(searchNode).performTextInput("T94B-$runId")
            composeRule.waitForIdle()
            // 关键词命中对照卡片、但分类条件不满足 → 两条卡片都不应出现（AND）。
            // 观察对象是精确ID卡片，不再看全屏文本（查询词自身就在输入框里）。
            assertTrue(
                "关键词命中对照项但分类不匹配时，两条卡片都必须被排除（AND 生效）",
                waitFor("t94v2_70_filter", UI_TIMEOUT_MS, "组合排除") {
                    !cardPresent(itemWithCategory) && !cardPresent(controlItem)
                },
            )
            // 关键词改回匹配项 + 分类 A → 匹配卡片重新出现，对照卡片仍不出现
            editableInteractionFor(searchNode).performTextClearance()
            composeRule.waitForIdle()
            editableInteractionFor(searchNode).performTextInput("T94A-$runId")
            composeRule.waitForIdle()
            assertTrue(
                "关键词与分类同时满足时匹配卡片必须重新出现",
                waitFor("t94v2_70_filter", UI_TIMEOUT_MS, "组合命中") { cardPresent(itemWithCategory) },
            )
            assertTrue(
                "组合命中时对照卡片仍不得出现",
                !cardPresent(controlItem),
            )
            writeEvidence(
                "t94v2_70_filter", true,
                mapOf("excludedControl" to true, "andCombination" to true, "category" to categoryA),
            )
            // 复位筛选
            composeRule.onNode(hasContentDescription("清空输入")).performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("全部分类")[0].performClick()
            composeRule.waitForIdle()

            // P8：真实精确回源（仅改分类后仍有效）
            markPhase("t94v2_80_source_jump")
            openInspirationDetail("T94A-$runId")
            assertTrue(
                "详情必须出现「查阅原文位置」",
                waitFor("t94v2_80_source_jump", UI_TIMEOUT_MS, "查阅原文位置") { textVisible("查阅原文位置") },
            )
            composeRule.onNodeWithText("查阅原文位置").performClick()
            composeRule.waitForIdle()
            assertTrue(
                "精确回源必须真实进入阅读器（出现「返回阅读处」）",
                waitFor("t94v2_80_source_jump", UI_TIMEOUT_MS, "临时查阅") {
                    composeRule.onAllNodesWithContentDescription("返回阅读处").fetchSemanticsNodes().isNotEmpty()
                },
            )
            assertTrue(
                "回源后的阅读器正文必须就绪",
                waitFor("t94v2_80_source_jump", UI_TIMEOUT_MS, "阅读器正文就绪") { readerReady() },
            )
            assertTrue(
                "回源后必须能读到本轮摘录正文",
                waitFor("t94v2_80_source_jump", UI_TIMEOUT_MS, "摘录可见") {
                    textVisible(capturedSelection.take(12))
                },
            )
            composeRule.onNode(hasContentDescription("返回阅读处")).performClick()
            composeRule.waitForIdle()
            writeEvidence("t94v2_80_source_jump", true, mapOf("excerptPrefix" to capturedSelection.take(12)))

            // P9：真实用户导出入口 → 系统文档选择器 → 取回实际字节
            markPhase("t94v2_90_export")
            assertTrue("导出前必须仍在阅读器", waitFor("t94v2_90_export", UI_TIMEOUT_MS, "阅读器") { readerReady() })
            ReaderExportReceipts.clear()
            composeRule.onNode(hasContentDescription("更多")).performClick()
            composeRule.waitForIdle()
            assertTrue("更多菜单必须出现「笔记与标注」", waitFor("t94v2_90_export", UI_TIMEOUT_MS, "笔记与标注") {
                textVisible("笔记与标注")
            })
            composeRule.onNodeWithText("笔记与标注").performClick()
            composeRule.waitForIdle()
            assertTrue("笔记面板必须出现「导出」", waitFor("t94v2_90_export", UI_TIMEOUT_MS, "导出入口") { textVisible("导出") })
            composeRule.onNodeWithText("导出").performClick()
            composeRule.waitForIdle()
            assertTrue("必须拉起系统文档选择器", waitFor("t94v2_90_export", PICKER_TIMEOUT_MS, "DocumentsUI") {
                documentsUiRoot() != null
            })
            val exportPreflight = prepareExportDestination()
            assertTrue("必须在已核目录确认保存", tapDocumentsUiSave())
            assertTrue("保存后必须离开系统选择器", waitFor("t94v2_90_export", EXPORT_TIMEOUT_MS, "选择器关闭") {
                documentsUiRoot() == null
            })
            assertTrue("必须取得系统返回的导出结果", waitFor("t94v2_90_export", EXPORT_TIMEOUT_MS, "导出回执") {
                ReaderExportReceipts.latest() != null || ReaderExportReceipts.latestFailure() != null
            })
            ReaderExportReceipts.latestFailure()?.let { failure ->
                throw AssertionError("系统导出失败：${failure.message}", failure.cause)
            }
            val exportedUri = requireNotNull(ReaderExportReceipts.latest()) { "导出必须返回真实 URI" }.uri
            // 在任何内容断言之前记录准确URI；即使身份/读回失败也给宿主保留恢复线索。
            val receiptFile = File(resultsDir, "t94v4_export_receipt.json")
            val exportRecord = JSONObject(exportPreflight.toString())
                .put("uri", exportedUri.toString()).put("runId", runId)
                .put("ownershipValidated", false)
            receiptFile.writeText(exportRecord.toString(2))
            val bytes = requireNotNull(context.contentResolver.openInputStream(exportedUri)) {
                "必须能按系统URI读回原始导出字节"
            }.use { it.readBytes() }
            val evidenceFile = File(resultsDir, "t94v2_90_export.md")
            evidenceFile.writeBytes(bytes)
            val sha = sha256(bytes)
            exportRecord.put("size", bytes.size).put("sha256", sha)
                .put("evidenceFile", evidenceFile.absolutePath)
            receiptFile.writeText(exportRecord.toString(2))
            val displayName = queryDisplayName(exportedUri)
            exportRecord.put("displayName", displayName)
            receiptFile.writeText(exportRecord.toString(2))
            assertEquals("返回provider必须与显式选择的本地存储一致", "com.android.externalstorage.documents", exportedUri.authority)
            assertEquals("返回URI必须是已核Download目录内的同名精确新资产",
                "primary:Download/${exportPreflight.getString("fileName")}",
                DocumentsContract.getDocumentId(exportedUri))
            assertEquals("返回显示名必须与保存前实际文件名一致", exportPreflight.getString("fileName"), displayName)
            exportRecord.put("ownershipValidated", true)
            receiptFile.writeText(exportRecord.toString(2))
            val exportedText = String(bytes, Charsets.UTF_8)
            // 94-v14：按精确条目标题定位标签行，与该精确行持久化 tags 做顺序无关的完整集合比较。
            // 不再要求目标标签在行首，也不用 contains(tagName) 掩盖缺失/重复/相似前缀。
            val persistedBeforeExport = payloadJson.decodeFromString(
                InspirationPayloadData.serializer(),
                requireNotNull(rowPayload(itemWithCategory)) { "导出前必须能读到该精确行 payload" },
            )
            val tagLine = ExportTagLine.parse(exportedText, "### T94A-$runId")
            ExportTagLine.assertMatchesPersisted(tagLine, persistedBeforeExport.tags)
            File(resultsDir, "t94v9_export_tag_check.json").writeText(
                JSONObject()
                    .put("inspirationId", itemWithCategory)
                    .put("heading", tagLine.heading)
                    .put("tagLine", tagLine.line)
                    .put("persistedTags", persistedBeforeExport.tags.joinToString("\u001f"))
                    .put("exportTags", tagLine.values.joinToString("\u001f"))
                    .put("orderIndependent", true)
                    .put("exactValues", true)
                    .toString(2),
            )
            // 个人想法/来源/摘录必须限定在该精确条目的同一小节内，避免全篇同名文本掩盖问题。
            val sectionText = ExportTagLine.sectionText(exportedText, "### T94A-$runId")
            assertTrue("导出必须在精确条目小节内包含个人想法", sectionText.contains("个人想法A：$runId"))
            assertTrue("导出必须在精确条目小节内包含来源行", sectionText.contains("来源："))
            assertTrue("导出必须在精确条目小节内包含摘录引用", sectionText.contains("> 摘录："))
            // 本仪器不删除导出；Root先保全E字节，再独立核初始快照与设备SHA后精确清理。
            writeEvidence(
                "t94v2_90_export", true,
                mapOf(
                    "uri" to exportedUri.toString(),
                    "displayName" to displayName,
                    "bytes" to bytes.size,
                    "sha256" to sha,
                    "evidenceFile" to evidenceFile.absolutePath,
                    "containsTagLine" to true,
                    "cleanup" to "deferred-to-host-after-E-preservation",
                ),
            )

            // P10：明确清空分类
            markPhase("t94v2_a0_clear_category")
            leaveReaderForInspiration("t94v2_a0_clear_category")
            composeRule.onNode(hasContentDescription("编辑灵感")).performClick()
            composeRule.waitForIdle()
            assertTrue("编辑器必须回显 A 与 B", waitFor("t94v2_a0_clear_category", UI_TIMEOUT_MS, "分类") {
                textVisible(categoryA) && textVisible(categoryB)
            })
            assertTrue("清空前分类 A 胶囊必须唯一", composeRule.onAllNodesWithText(categoryA).fetchSemanticsNodes().size == 1)
            composeRule.onNodeWithText(categoryA).performClick()
            composeRule.waitForIdle()
            assertTrue("清空前分类 B 胶囊必须唯一", composeRule.onAllNodesWithText(categoryB).fetchSemanticsNodes().size == 1)
            composeRule.onNodeWithText(categoryB).performClick()
            composeRule.waitForIdle()
            val clearBeforeUpdatedAt = rowUpdatedAt(itemWithCategory)
            val clearBeforePayload = requireNotNull(rowPayload(itemWithCategory))
            saveEditorAndWaitExit("t94v2_a0_clear_category", "保存修改")
            // 同样是异步持久化：等待同一精确行 payload 真实变化且分类为空，不重复点击保存。
            val clearedObserved = waitFor("t94v2_a0_clear_category", SAVE_TIMEOUT_MS, "同一精确行持久化空分类") {
                val raw = rowPayload(itemWithCategory)
                val parsed = raw?.let {
                    runCatching { payloadJson.decodeFromString(InspirationPayloadData.serializer(), it) }.getOrNull()
                }
                raw != null && raw != clearBeforePayload && parsed != null && parsed.categoryIds.isEmpty()
            }
            File(resultsDir, "t94v8_clear_persist_wait.json").writeText(
                JSONObject()
                    .put("inspirationId", itemWithCategory)
                    .put("beforeUpdatedAt", clearBeforeUpdatedAt ?: JSONObject.NULL)
                    .put("afterUpdatedAt", rowUpdatedAt(itemWithCategory) ?: JSONObject.NULL)
                    .put("updatedAtChanged", rowUpdatedAt(itemWithCategory) != clearBeforeUpdatedAt)
                    .put("payloadChanged", rowPayload(itemWithCategory) != clearBeforePayload)
                    .put("observedPersisted", clearedObserved)
                    .toString(2),
            )
            assertTrue("明确清空必须在现有预算内观察到真实持久化", clearedObserved)
            assertTrue(
                "明确清空必须更新该精确行的有效更新时间见证",
                rowUpdatedAt(itemWithCategory) != null && rowUpdatedAt(itemWithCategory) != clearBeforeUpdatedAt,
            )
            val clearedPayload = requireNotNull(rawOne("SELECT payload FROM inspirations WHERE id='$itemWithCategory'"))
            val cleared = payloadJson.decodeFromString(InspirationPayloadData.serializer(), clearedPayload)
            assertTrue("明确清空后分类必须为空", cleared.categoryIds.isEmpty())
            assertEquals("清空分类不得影响来源", before.source, cleared.source)
            writeEvidence("t94v2_a0_clear_category", true, mapOf("categoryIds" to cleared.categoryIds.size))

            // P11：取消编辑不写入
            markPhase("t94v2_b0_cancel")
            val cancelBeforePayload = requireNotNull(rowPayload(itemWithCategory))
            val cancelBeforeUpdatedAt = rowUpdatedAt(itemWithCategory)
            composeRule.onNode(hasContentDescription("编辑灵感")).performClick()
            composeRule.waitForIdle()
            assertTrue("编辑器必须可再次打开", waitFor("t94v2_b0_cancel", UI_TIMEOUT_MS, "分类") { textVisible(categoryA) })
            assertTrue("取消前分类 A 胶囊必须唯一", composeRule.onAllNodesWithText(categoryA).fetchSemanticsNodes().size == 1)
            composeRule.onNodeWithText(categoryA).performClick()
            composeRule.waitForIdle()
            composeRule.onNode(hasContentDescription("返回")).performClick()
            composeRule.waitForIdle()
            assertTrue("放弃未保存修改对话框必须出现", waitFor("t94v2_b0_cancel", UI_TIMEOUT_MS, "放弃对话框") {
                textVisible("放弃")
            })
            composeRule.onNodeWithText("放弃").performClick()
            composeRule.waitForIdle()
            val afterCancel = requireNotNull(rawOne("SELECT payload FROM inspirations WHERE id='$itemWithCategory'"))
            val cancelAfterUpdatedAt = rowUpdatedAt(itemWithCategory)
            val cancelled = payloadJson.decodeFromString(InspirationPayloadData.serializer(), afterCancel)
            assertTrue("取消编辑不得写入分类", cancelled.categoryIds.isEmpty())
            assertEquals("取消编辑不得改动来源", before.source, cancelled.source)
            assertEquals("取消编辑不得改动同一行payload任何字段", cancelBeforePayload, afterCancel)
            assertEquals("取消编辑不得改变该精确行的有效更新时间见证", cancelBeforeUpdatedAt, cancelAfterUpdatedAt)
            File(resultsDir, "t94v9_cancel_unchanged.json").writeText(
                JSONObject()
                    .put("inspirationId", itemWithCategory)
                    .put("payloadUnchanged", afterCancel == cancelBeforePayload)
                    .put("updatedAtUnchanged", cancelAfterUpdatedAt == cancelBeforeUpdatedAt)
                    .put("beforeUpdatedAt", cancelBeforeUpdatedAt ?: JSONObject.NULL)
                    .put("afterUpdatedAt", cancelAfterUpdatedAt ?: JSONObject.NULL)
                    .toString(2),
            )
            writeEvidence("t94v2_b0_cancel", true, mapOf("unchanged" to true))

            markPhase("t94v2_done")
        }
    }

    // ── 阅读器交互 ───────────────────────────────────────────────────────────

    /** 长按划词 → 更多 → 记为灵感，返回真实选区。 */
    private fun selectAndOpenSheet(): String {
        composeRule.onRoot().performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        assertTrue(
            "长按必须唤出划词工具条",
            waitFor(lastPhase, UI_TIMEOUT_MS, "工具条") {
                textVisible("更多") && (textVisible("高亮") || textVisible("复制"))
            },
        )
        val selection = selectionToolbarExcerpt()
        assertTrue("必须取得真实选区", selection.isNotBlank())
        capturedSelection = selection
        composeRule.onNodeWithText("更多").performClick()
        composeRule.waitForIdle()
        assertTrue("「更多」必须出现「记为灵感」", waitFor(lastPhase, UI_TIMEOUT_MS, "记为灵感") {
            textVisible("记为灵感")
        })
        composeRule.onNodeWithText("记为灵感").performClick()
        composeRule.waitForIdle()
        assertTrue("灵感面板必须出现标题输入", waitFor(lastPhase, UI_TIMEOUT_MS, "标题") {
            composeRule.onAllNodesWithText("标题").fetchSemanticsNodes().isNotEmpty()
        })
        return selection
    }

    /**
     * 填写灵感面板：标题 / 想法 / 可选标签 / 可选新建分类（新建后自动选中）。
     * [deselectCategories] 用于把已自动选中的分类再点掉，构造"只有部分分类被选中"的真实状态。
     */
    private fun fillSheet(
        title: String,
        ideas: String,
        tag: String?,
        createCategories: List<String>,
        deselectCategories: List<String> = emptyList(),
    ) {
        val titleField = resolveEditableField("标题")
        val ideaField = resolveEditableField("我的想法")
        assertTrue("标题字段必须唯一匹配：${titleField.diagnostics}", titleField.unique && titleField.node != null)
        assertTrue("想法字段必须唯一匹配：${ideaField.diagnostics}", ideaField.unique && ideaField.node != null)
        val titleNode = titleField.node!!
        val ideaNode = ideaField.node!!
        editableInteractionFor(titleNode).performTextClearance()
        composeRule.waitForIdle()
        editableInteractionFor(titleNode).performTextInput(title)
        composeRule.waitForIdle()
        editableInteractionFor(ideaNode).performTextClearance()
        composeRule.waitForIdle()
        editableInteractionFor(ideaNode).performTextInput(ideas)
        composeRule.waitForIdle()
        assertEquals("标题保存前读回必须等于本轮标题", title, readBack(titleNode))
        assertTrue("想法保存前读回必须含本轮标记", readBack(ideaNode)?.contains(runId) == true)
        if (tag != null) {
            assertTrue("标签输入入口必须存在", waitFor(lastPhase, UI_TIMEOUT_MS, "标签输入") {
                composeRule.onAllNodes(hasText("新标签，逗号或空格分隔…", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            })
            addChipViaUi("新标签，逗号或空格分隔…", tag)
        }
        createCategories.forEach { name ->
            assertTrue("分类输入入口必须存在（阅读面板）", waitFor(lastPhase, UI_TIMEOUT_MS, "分类输入") {
                composeRule.onAllNodes(hasText("新建分类名称…", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            })
            addChipViaUi("新建分类名称…", name)
            assertTrue("分类必须真实写入 categories 表", waitFor(lastPhase, SAVE_TIMEOUT_MS, "分类行") {
                (rawOne("SELECT COUNT(*) FROM categories WHERE name='$name' AND deleted_at IS NULL")
                    ?.toIntOrNull() ?: 0) == 1
            })
        }
        deselectCategories.forEach { name ->
            assertTrue("待取消的分类胶囊必须唯一可定位：$name", waitFor(lastPhase, UI_TIMEOUT_MS, "分类胶囊") {
                composeRule.onAllNodesWithText(name).fetchSemanticsNodes().size == 1
            })
            composeRule.onNodeWithText(name).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun saveSheet(phase: String) {
        assertTrue("保存按钮必须可用", waitFor(phase, UI_TIMEOUT_MS, "保存灵感") { textVisible("保存灵感") })
        composeRule.onNodeWithText("保存灵感").performClick()
        composeRule.waitForIdle()
    }

    private fun inspirationIdByTitle(title: String): String {
        assertTrue(
            "Room 必须出现标题为 $title 的灵感",
            waitFor(lastPhase, SAVE_TIMEOUT_MS, "灵感行") {
                (rawOne(
                    "SELECT COUNT(*) FROM inspirations WHERE deleted_at IS NULL AND title='$title'",
                )?.toIntOrNull() ?: 0) == 1
            },
        )
        return requireNotNull(
            rawOne(
                "SELECT id FROM inspirations WHERE deleted_at IS NULL " +
                    "AND title='$title' ORDER BY created_at DESC LIMIT 1",
            ),
        )
    }

    /** 经真实「新建灵感」编辑器创建对照条目（不带来源与分类）。 */
    private fun createControlViaEditor(title: String) {
        assertTrue("灵感列表必须出现新建入口", waitFor(lastPhase, UI_TIMEOUT_MS, "新建灵感") {
            composeRule.onAllNodesWithContentDescription("新建灵感").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNode(hasContentDescription("新建灵感")).performClick()
        composeRule.waitForIdle()
        val titleField = resolveEditableField("标题")
        assertTrue("新建编辑器标题字段必须唯一：${titleField.diagnostics}", titleField.unique && titleField.node != null)
        val node = titleField.node!!
        editableInteractionFor(node).performTextClearance()
        composeRule.waitForIdle()
        editableInteractionFor(node).performTextInput(title)
        composeRule.waitForIdle()
        assertEquals("标题保存前读回必须等于本轮标题", title, readBack(node))
        assertTrue("新建灵感保存按钮必须可用", waitFor(lastPhase, UI_TIMEOUT_MS, "保存灵感") { textVisible("保存灵感") })
        captureControlSaveBoundary("before_ui_precondition", title)
        // 仅关闭确证可见的输入法，不把键盘遮挡当成已证根因；不用Back改变编辑页状态。
        if (controlKeyboardVisible() == true) {
            scenario?.onActivity { activity ->
                val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                check(manager.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)) {
                    "Visible keyboard hide request was rejected"
                }
            }
            assertTrue("保存前已显示的键盘必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "输入法关闭") {
                controlKeyboardVisible() == false
            })
        }
        val saveButton = composeRule.onNode(hasText("保存灵感") and hasClickAction())
        saveButton.performScrollTo()
        composeRule.waitForIdle()
        captureControlSaveBoundary("before_click", title)
        assertTrue("真实保存按钮滚动后必须可见", saveButton.isDisplayed())
        check(!saveButton.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
            "真实保存按钮必须enabled"
        }
        saveButton.performClick()
        composeRule.waitForIdle()
        captureControlSaveBoundary("after_click", title)
    }

    /**
     * 编辑器保存（94-v7）：复用 v6 已证前置——若输入法可见先关闭、滚动到真实保存按钮、
     * 断言可见且 enabled 后再点击；点击后等待编辑器**真实退出**（回到详情）。
     * 未退出时保全语义树与截图，避免把"点了但没保存"直接当成产品缺陷。
     */
    private fun saveEditorAndWaitExit(phase: String, label: String) {
        if (controlKeyboardVisible() == true) {
            scenario?.onActivity { activity ->
                val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                check(manager.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)) {
                    "Visible keyboard hide request was rejected"
                }
            }
            assertTrue("保存前已显示的键盘必须关闭", waitFor(phase, UI_TIMEOUT_MS, "输入法关闭") {
                controlKeyboardVisible() == false
            })
        }
        val saveButton = composeRule.onNode(hasText(label) and hasClickAction())
        saveButton.performScrollTo()
        composeRule.waitForIdle()
        assertTrue("真实保存按钮滚动后必须可见（$label）", saveButton.isDisplayed())
        check(!saveButton.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
            "真实保存按钮必须 enabled"
        }
        saveButton.performClick()
        composeRule.waitForIdle()
        val exited = waitFor(phase, UI_TIMEOUT_MS, "保存后编辑器退出") {
            composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()
        }
        if (!exited) {
            File(resultsDir, "${phase}_save_not_exited.tree.txt")
                .writeText(composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20))
            saveScreenshot("${phase}_save_not_exited")
        }
        assertTrue("保存后编辑器必须真实退出（回到详情）", exited)
    }

    private fun controlKeyboardVisible(): Boolean? {
        var visible: Boolean? = null
        scenario?.onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime())
        }
        return visible
    }

    /** 只读本轮保存边界；行查询严格限runId，不收集正式书库数据。 */
    private fun captureControlSaveBoundary(stage: String, expectedTitle: String) {
        val diagnostic = JSONObject().put("runId", runId).put("stage", stage)
            .put("expectedTitle", expectedTitle).put("keyboardVisible", controlKeyboardVisible() ?: JSONObject.NULL)
        val titleField = resolveEditableField("标题")
        diagnostic.put("titleResolution", JSONObject(titleField.diagnostics))
            .put("titleFieldText", titleField.node?.let { editableTextOf(it) } ?: JSONObject.NULL)
        val buttons = composeRule.onAllNodes(hasText("保存灵感") and hasClickAction()).fetchSemanticsNodes()
        diagnostic.put("saveButtonCount", buttons.size)
        diagnostic.put("saveButtons", org.json.JSONArray(buttons.map { node ->
            JSONObject().put("boundsInRoot", node.boundsInRoot.toString())
                .put("boundsInWindow", node.boundsInWindow.toString())
                .put("enabled", !node.config.contains(SemanticsProperties.Disabled))
                .put("hasClickAction", node.config.getOrNull(SemanticsActions.OnClick)?.action != null)
                .put("semantics", node.config.toString())
        }))
        diagnostic.put("saveButtonDisplayed", if (buttons.size == 1)
            composeRule.onNode(hasText("保存灵感") and hasClickAction()).isDisplayed() else JSONObject.NULL)
        diagnostic.put("editorSaveVisible", buttons.isNotEmpty())
            .put("detailEditVisible", composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty())
        val rows = org.json.JSONArray()
        db.openHelper.readableDatabase.query(
            "SELECT id,title,source_book_id,payload,deleted_at FROM inspirations WHERE instr(title, ?) > 0 OR instr(payload, ?) > 0",
            arrayOf<Any>(runId, runId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    row.put(cursor.getColumnName(i), if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i))
                }
                rows.put(row)
            }
        }
        diagnostic.put("runScopedRows", rows)
        val tree = composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 20)
        diagnostic.put("visibleSaveErrors", org.json.JSONArray(tree.lineSequence().filter {
            it.contains("请至少填写") || it.contains("保存失败") || it.contains("错误")
        }.toList()))
        File(resultsDir, "t94v6_control_save_$stage.json").writeText(diagnostic.toString(2))
        File(resultsDir, "t94v6_control_save_$stage.tree.txt").writeText(tree)
    }

    /**
     * 重复取材边界诊断：复现 94-v2 的"同一会话第二次 长按→更多→记为灵感"，
     * 记录语义树、节点计数、选区微条与弹层状态、截图。**不参与业务判定**，
     * 异常只记录不外抛；成功后如菜单已展开则按返回收起。
     */
    private fun diagnoseRepeatSelection() {
        val diag = JSONObject()
        fun snapshot(tag: String) {
            diag.put("${tag}.moreNodes", composeRule.onAllNodesWithText("更多").fetchSemanticsNodes().size)
            diag.put("${tag}.highlightNodes", composeRule.onAllNodesWithText("高亮").fetchSemanticsNodes().size)
            diag.put("${tag}.copyNodes", composeRule.onAllNodesWithText("复制").fetchSemanticsNodes().size)
            diag.put("${tag}.inspirationNodes", composeRule.onAllNodesWithText("记为灵感").fetchSemanticsNodes().size)
            diag.put(
                "${tag}.selectionTagNodes",
                composeRule.onAllNodesWithTag(
                    ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size,
            )
            diag.put("${tag}.sheetIdeaFieldNodes", composeRule.onAllNodesWithText("我的想法").fetchSemanticsNodes().size)
            diag.put(
                "${tag}.tree",
                composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 12).take(20000),
            )
        }
        try {
            snapshot("beforeLongPress")
            composeRule.onRoot().performTouchInput { longClick(center) }
            composeRule.waitForIdle()
            snapshot("afterLongPress")
            val moreNodes = composeRule.onAllNodesWithText("更多").fetchSemanticsNodes()
            if (moreNodes.size == 1) {
                composeRule.onAllNodesWithText("更多")[0].performClick()
                composeRule.waitForIdle()
                snapshot("afterMoreClick")
            }
            diag.put("outcome", "diagnostic-complete")
        } catch (t: Throwable) {
            diag.put("outcome", "diagnostic-error")
            diag.put("error", "${t.javaClass.simpleName}: ${t.message}")
        } finally {
            runCatching { saveScreenshot("t94v3_25_repeat") }
            File(resultsDir, "t94v3_25_repeat_diagnostic.json").writeText(diag.toString(2))
            android.util.Log.i(TAG, "repeat-diagnostic outcome=${diag.optString("outcome")}")
            clearReaderSelection()
        }
    }

    private fun saveScreenshot(tag: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(resultsDir, "$tag.png").outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) return c.getString(i)
            }
        }
        return ""
    }

    // ── 文档选择器（复用 79-v2 探针口径） ────────────────────────────────────

    private fun documentsUiRoot(): AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo ?: AccessibilityServiceInfo()
        if ((info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) == 0) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
        }
        val candidates = automation.windows.mapNotNull { w ->
            val r = w.root ?: return@mapNotNull null
            Triple(r.packageName?.toString().orEmpty(), w.isFocused, r)
        }.filter { (pkg, _, _) -> pkg in DOCUMENTS_UI_PACKAGES }
        val focused = candidates.filter { it.second }
        if (focused.size == 1) return focused.first().third
        if (candidates.size == 1) return candidates.first().third
        val active = automation.rootInActiveWindow
        val activePkg = active?.packageName?.toString().orEmpty()
        return if (activePkg in DOCUMENTS_UI_PACKAGES) active else null
    }

    /**
     * 通过 DocumentsUI provider 查询 primary 根的真实标题与 documentId（不依赖 StorageVolume 描述）。
     * provider 需要 URI 授权，shell/app uid 直接查询会被 SecurityException 拒绝；
     * 本设备已有 root（恢复合同同样依赖 su），因此用 `su 0 content query` 读取。
     */
    private fun queryProviderPrimaryRoot(): Pair<String, String> {
        val output = runRootShellScript(
            "content query --uri content://com.android.externalstorage.documents/root " +
                "--projection root_id:title:document_id\n",
        )
        val match = Regex("""root_id=primary,\s*title=(.*?),\s*document_id=(primary:)""")
            .find(output)
            ?: throw IllegalStateException("primary provider root not found in: $output")
        return match.groupValues[1].trim() to match.groupValues[2]
    }

    /**
     * 通过 UiAutomation 的真实 stdin 通道执行 root shell 脚本（等价于宿主已核的
     * `adb shell -T su 0 sh -s`）。
     *
     * 注意：`executeShellCommand(String)` **不经过 shell**（只按空白拆 argv），
     * `;`、`$?` 会被当作普通参数，曾导致探测输出是命令行原文。
     * 这里使用 `executeShellCommandRw` 向 `su 0 sh -s` 写脚本，让 shell 真正解释多行脚本。
     */
    private fun runRootShellScript(script: String): String =
        UiAutomationShellChannel.run(script)

    /** 只认明确本地存储根→Download；未知目录不保存，也不创建不可恢复资产。 */
    private fun prepareExportDestination(): JSONObject {
        val args = InstrumentationRegistry.getArguments()
        val directory = requireNotNull(args.getString("craExportDirectory"))
        require(directory == "/storage/emulated/0/Download")
        val snapshotSha = requireNotNull(args.getString("craExportInitialSnapshotSha"))
        require(snapshotSha.matches(Regex("[a-fA-F0-9]{64}")))
        // 四个概念必须分开（94-v11）：
        // 1) provider 的 primary 根：root_id/document_id 与实际 UI 标题（DocumentsUI 抽屉里显示的名字）；
        // 2) StorageVolume.isPrimary 与它的描述（系统本地化名称，和 provider 标题是不同标签域）；
        // 3) canonical 保存目录；
        // 4) 系统返回 URI 的 documentId 与实际文件名。
        // 选根必须用 provider 标题；StorageVolume 描述只独立记录，不要求与 provider 标题相等。
        val (providerRootTitle, providerRootDocumentId) = queryProviderPrimaryRoot()
        require(providerRootDocumentId == "primary:") {
            "provider primary documentId must be exactly primary:，actual=$providerRootDocumentId"
        }
        val primaryVolume = (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager)
            .storageVolumes.single { it.isPrimary }
        val storageVolumeDescription = primaryVolume.getDescription(context)
        fun nodes(): List<AccessibilityNodeInfo> {
            val found = mutableListOf<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(requireNotNull(documentsUiRoot()))
            while (queue.isNotEmpty()) {
                check(found.size < MAX_A11Y_NODES) { "DocumentsUI tree exceeded bounded scan" }
                val n = queue.removeFirst()
                found += n
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            return found
        }
        fun clickUnique(matches: List<AccessibilityNodeInfo>, description: String) {
            check(matches.size == 1) { "$description must have exactly one real node, got ${matches.size}" }
            var target: AccessibilityNodeInfo? = matches.single()
            while (target != null && !target.isClickable) target = target.parent
            check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $description" }
        }
        File(resultsDir, "t94v4_picker_initial.json").writeText(JSONObject().put("nodes",
            org.json.JSONArray(nodes().map { n -> JSONObject().put("id", n.viewIdResourceName)
                .put("text", n.text?.toString()).put("description", n.contentDescription?.toString()) }),
        ).toString(2))
        // Open the system roots drawer, then choose the explicitly supplied local storage root.
        // 94-v15：面包屑可能已含同名标题，不能把全屏同名文本当根；只认抽屉 roots_list 子树内的标题。
        // 抽屉打开后先保全边界证据（祖先/资源ID/文本/可见性/可点击/前台/候选数），再做唯一性判断；
        // 候选不唯一立即拒绝保存，不猜首项、不加 sleep/重试。
        // 94-v15 设备证据：item_root 属于文件列表（dir_list），不是抽屉根行；
        // 抽屉根行在 roots_list 子树内（title 为 android:id/title，行容器可点击）。
        fun rootsList(): AccessibilityNodeInfo? =
            nodes().firstOrNull {
                it.viewIdResourceName?.endsWith("/roots_list") == true && it.isVisibleToUser
            }
        fun rootsListTitleNodes(): List<AccessibilityNodeInfo> {
            val list = rootsList() ?: return emptyList()
            val found = mutableListOf<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(list)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (n.text?.toString() == providerRootTitle) found += n
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            return found
        }
        fun nodePath(n: AccessibilityNodeInfo): String {
            val parts = mutableListOf<String>()
            var current: AccessibilityNodeInfo? = n
            var guard = 0
            while (current != null && guard < 64) {
                parts.add(current.viewIdResourceName ?: current.className?.toString() ?: "?")
                current = current.parent
                guard += 1
            }
            return parts.reversed().joinToString(" > ")
        }
        fun foregroundFocus(): String = runCatching {
            UiAutomationShellChannel.run(
                "echo CRA_FG_WINDOW\n" +
                    "dumpsys window | grep mCurrentFocus\n" +
                    "echo CRA_FG_ACTIVITY\n" +
                    "dumpsys activity activities | grep mResumedActivity\n",
            ).trim()
        }.getOrElse { "unavailable: ${it.message}" }
        fun boundaryJson(label: String): JSONObject {
            val all = nodes()
            val roots = all.filter { it.viewIdResourceName?.endsWith("/roots_list") == true }
            val candidates = rootsListTitleNodes()
            val nodesJson = org.json.JSONArray()
            all.forEach { n ->
                val rect = Rect()
                n.getBoundsInScreen(rect)
                nodesJson.put(
                    JSONObject()
                        .put("path", nodePath(n))
                        .put("id", n.viewIdResourceName)
                        .put("text", n.text?.toString())
                        .put("description", n.contentDescription?.toString())
                        .put("visible", n.isVisibleToUser)
                        .put("clickable", n.isClickable)
                        .put("bounds", rect.toShortString()),
                )
            }
            return JSONObject()
                .put("label", label)
                .put("at", Instant.now().toString())
                .put("foreground", foregroundFocus())
                .put("nodeCount", all.size)
                .put("rootsListCount", roots.size)
                .put("candidateCount", candidates.size)
                .put("providerRootTitle", providerRootTitle)
                .put("nodes", nodesJson)
        }
        fun persistBoundary(name: String, label: String) {
            val payload = runCatching { boundaryJson(label) }
                .getOrElse { JSONObject().put("label", label).put("captureError", it.toString()) }
            File(resultsDir, name).writeText(payload.toString(2))
        }
        persistBoundary("t94v15_picker_before_drawer.json", "before-drawer")
        clickUnique(nodes().filter {
            it.contentDescription?.toString() in setOf("显示根目录", "Show roots")
        }, "DocumentsUI roots drawer")
        try {
            waitFor(lastPhase, PICKER_TIMEOUT_MS, "抽屉根列表出现") { rootsList() != null }
        } finally {
            persistBoundary("t94v15_drawer_open.json", "drawer-open")
        }
        try {
            waitFor(lastPhase, PICKER_TIMEOUT_MS, "provider primary 根行") {
                rootsListTitleNodes().isNotEmpty()
            }
        } finally {
            persistBoundary("t94v15_drawer_candidates.json", "candidates")
        }
        val rootCandidates = rootsListTitleNodes()
        if (rootCandidates.size != 1) {
            persistBoundary("t94v15_drawer_ambiguous.json", "ambiguous")
            fail("provider primary 根行必须唯一，实得 ${rootCandidates.size}；已保全抽屉节点证据")
        }
        clickUnique(rootCandidates, "provider primary storage root")
        try {
            waitFor(lastPhase, PICKER_TIMEOUT_MS, "抽屉收起") { rootsList() == null }
        } finally {
            persistBoundary("t94v15_after_root.json", "after-root")
        }
        assertTrue("选择本地存储后必须出现Download入口", waitFor(lastPhase, PICKER_TIMEOUT_MS, "Download入口") {
            nodes().any { it.text?.toString() == "Download" }
        })
        assertTrue("本地存储必须出现Download目录", waitFor(lastPhase, PICKER_TIMEOUT_MS, "Download目录") {
            nodes().count { it.text?.toString() == "Download" && it.viewIdResourceName?.endsWith("/title") == true } == 1
        })
        clickUnique(nodes().filter { it.text?.toString() == "Download" && it.viewIdResourceName?.endsWith("/title") == true }, "Download directory")
        // 目录身份：进入后仍能从 DocumentsUI 节点读到 "Download"（面包屑/目录名）。
        // 不绑定具体 resource-id：不同系统版本的 toolbar/breadcrumb 资源名不同，
        // 而真正的保存目标绑定由后续的 provider 根、canonical 目录、系统返回 URI 与文件名共同证明。
        // 无论通过与否都落盘该边界节点，供失败时核对。
        val atDownload = waitFor(lastPhase, PICKER_TIMEOUT_MS, "当前Download目录") {
            nodes().any { it.text?.toString() == "Download" }
        }
        File(resultsDir, "t94v11_picker_after_download.json").writeText(
            JSONObject().put("atDownload", atDownload).put("nodes",
                org.json.JSONArray(nodes().map { n ->
                    JSONObject().put("id", n.viewIdResourceName)
                        .put("text", n.text?.toString())
                        .put("description", n.contentDescription?.toString())
                }),
            ).toString(2),
        )
        assertTrue("当前目录必须能证明为Download", atDownload)
        val nameNodes = nodes().filter { it.isEditable && it.viewIdResourceName?.endsWith("/title") == true }
        check(nameNodes.size == 1) { "Actual suggested filename must be uniquely readable" }
        val fileName = nameNodes.single().text?.toString().orEmpty()
        check(fileName.contains(runId) && fileName.endsWith(".md") && '/' !in fileName && '\\' !in fileName && fileName != "..") {
            "Actual suggested filename is not a single run-bound Markdown asset"
        }
        // 保存前缺席证明：脚本中**不含文件名**，从根上避免中文书名号/空格/引号转义问题。
        // 1) canonical 目录身份（readlink）；2) 目录列举必须带明确 rc；3) 文件名不得出现在列举中
        //    （文件与同名符号链接都会出现在 ls -A 里）。
        // 探测失败/输出缺标记 → 不许可保存；输出为空不等于成功。
        val probeRaw = runRootShellScript(UiAutomationShellChannel.exportProbeScript())
        val probe = UiAutomationShellChannel.parseExportProbe(probeRaw)
        val probeOk = probe.ok(directory)
        val targetAbsent = probeOk && fileName !in probe.entries
        val preflight = JSONObject().put("runId", runId).put("directoryIdentity", directory)
            .put("snapshotSha", snapshotSha).put("fileName", fileName)
            .put("providerRootTitle", providerRootTitle)
            .put("providerRootDocumentId", providerRootDocumentId)
            .put("storageVolumeDescription", storageVolumeDescription)
            .put("pickerDirectory", "Download")
            .put("canonicalDirectory", probe.canonical)
            .put("canonicalRc", probe.canonicalRc ?: JSONObject.NULL)
            .put("listingRc", probe.listingRc ?: JSONObject.NULL)
            .put("entryCount", probe.entries.size)
            .put("probeRaw", probeRaw.take(8000))
            .put("probeOk", probeOk)
            .put("initiallyAbsent", targetAbsent)
        File(resultsDir, "t94v4_export_before_save.json").writeText(preflight.toString(2))
        check(probeOk) {
            "Export absence probe failed (canonical=${probe.canonical} " +
                "canonRc=${probe.canonicalRc} lsRc=${probe.listingRc}); do not save"
        }
        check(targetAbsent) { "Exact export target already exists; do not overwrite: $fileName" }
        return preflight
    }

    private fun tapDocumentsUiSave(): Boolean {
        val root = documentsUiRoot() ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned < MAX_A11Y_NODES) {
            val node = queue.removeFirst()
            scanned++
            val label = (node.text?.toString() ?: "").ifBlank {
                node.contentDescription?.toString() ?: ""
            }
            if (label in SAVE_LABELS) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private fun fixtureFile(): File =
        File(context.getExternalFilesDir(null), "task94v2-fixtures/$FIXTURE_NAME-$runId.txt").apply {
            parentFile?.mkdirs()
        }

    private fun txtBody(): String = buildString {
        appendLine("第1章 取材真实接缝")
        appendLine()
        (1..18).forEach { p ->
            appendLine("第${p}段：这是任务94v2 使用的中性测试正文，用于真实长按摘录、分类编辑、筛选与导出，不包含任何正式书库内容。")
            appendLine()
        }
        appendLine("第2章 后续正文")
        appendLine()
        (1..18).forEach { p -> appendLine("第2章第${p}段：继续提供可滚动、可定位的中性文本。") }
    }

    private fun rawOne(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    /** 本轮分类名称→精确 id（只读本轮已落库行）。 */
    private fun categoryIdByName(name: String): String? =
        rawOne("SELECT id FROM categories WHERE name='$name' AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")

    private fun rowPayload(id: String): String? =
        rawOne("SELECT payload FROM inspirations WHERE id='$id'")

    private fun rowUpdatedAt(id: String): String? =
        rawOne("SELECT updated_at FROM inspirations WHERE id='$id'")

    private fun activeBookIds(): Set<String> {
        val ids = LinkedHashSet<String>()
        db.openHelper.readableDatabase
            .query("SELECT id FROM books WHERE deleted_at IS NULL")
            .use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        return ids
    }

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

    // ── UI 工具 ──────────────────────────────────────────────────────────────

    /**
     * 轮询谓词：谓词内部只用 `onAllNodes*` 等不抛异常的语义查询，
     * 因此这里**不捕获异常**——真实异常（Compose/系统）保留根因直接冒泡。
     */
    private fun waitFor(phase: String, timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return true
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$phase 等待超时（$timeoutMs ms）：$description")
        return false
    }

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun clickTopLevelTab(label: String) {
        composeRule.onNode(hasText(label) and isSelectable()).performClick()
        composeRule.waitForIdle()
    }

    private fun ensureOnShelf(phase: String): Boolean {
        val appeared = waitFor(phase, UI_TIMEOUT_MS, "首页或书架页面出现") { topLevelPage() != null }
        if (!appeared) return false
        if (topLevelPage() == "shelf") return true
        clickTopLevelTab("书架")
        return waitFor(phase, UI_TIMEOUT_MS, "书架页面出现") { topLevelPage() == "shelf" }
    }

    private fun readerReady(): Boolean =
        composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isNotEmpty()

    private fun textVisible(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * 仅统计**非输入控件**中的可见文本。
     * 搜索框会把查询词本身渲染成 EditableText，若用 textVisible 断言"结果中不包含某词"，
     * 输入框里的查询词会自我命中，导致 AND 组合被误判失败（94-v9 实际失败边界）。
     */
    private fun listTextVisible(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.EditableText) == null }

    /** 列表卡片的真实存在性：以生产 testTag 精确到本轮灵感 ID，避免全屏文本误命中。 */
    private fun cardPresent(id: String): Boolean =
        composeRule.onAllNodesWithTag("inspiration-card-$id").fetchSemanticsNodes().isNotEmpty()

    private fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun clearReaderSelection() {
        if (textVisible("记为灵感")) {
            pressBack()
            assertTrue("下拉菜单必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "菜单消失") { !textVisible("记为灵感") })
        }
        if (composeRule.onAllNodesWithTag(ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG,
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            pressBack()
            assertTrue("选区微条与工具条必须消失", waitFor(lastPhase, UI_TIMEOUT_MS, "清除选区") {
                composeRule.onAllNodesWithTag(ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG,
                    useUnmergedTree = true).fetchSemanticsNodes().isEmpty() && !textVisible("复制")
            })
        }
    }

    private fun backToShelf() {
        if (topLevelPage() == "shelf") return
        assertTrue("离开阅读器前必须确认正文状态", readerReady())
        if (textVisible("笔记与标注")) {
            pressBack()
            assertTrue("笔记面板必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "关闭笔记面板") { !textVisible("笔记与标注") })
        }
        clearReaderSelection()
        if (composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().isEmpty()) {
            composeRule.onRoot().performTouchInput { click(center) }
            composeRule.waitForIdle()
        }
        assertTrue("阅读器返回入口必须唯一", waitFor(lastPhase, UI_TIMEOUT_MS, "阅读器返回按钮") {
            composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size == 1
        })
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("回到书架必须成立", waitFor(lastPhase, UI_TIMEOUT_MS, "回到书架") { topLevelPage() == "shelf" })
    }

    /**
     * 94-v16：从阅读器退出到“本轮精确灵感”的详情。
     *
     * 导出发生在“查阅原文位置”临时进入阅读器之后，点击返回可能落在书架/首页或灵感详情，
     * 不能只断言书架。这里先记录真实落地页面（含语义树），再按实际页面导航：
     * 已到详情则直接用；到列表则打开本轮条目；到书架/首页则切到灵感页。
     */
    private fun leaveReaderForInspiration(phase: String) {
        assertTrue("离开阅读器前必须确认正文状态", readerReady())
        if (textVisible("笔记与标注")) {
            pressBack()
            assertTrue("笔记面板必须关闭", waitFor(phase, UI_TIMEOUT_MS, "关闭笔记面板") { !textVisible("笔记与标注") })
        }
        clearReaderSelection()
        if (composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().isEmpty()) {
            composeRule.onRoot().performTouchInput { click(center) }
            composeRule.waitForIdle()
        }
        assertTrue("阅读器返回入口必须唯一", waitFor(phase, UI_TIMEOUT_MS, "阅读器返回按钮") {
            composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size == 1
        })
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitForIdle()

        fun inspirationDetailVisible(): Boolean =
            composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()
        fun inspirationListVisible(): Boolean =
            composeRule.onAllNodesWithContentDescription("搜索灵感").fetchSemanticsNodes().isNotEmpty()
        fun landed(): Boolean = topLevelPage() != null || inspirationDetailVisible() || inspirationListVisible()
        val leftReader = waitFor(phase, UI_TIMEOUT_MS, "离开阅读器") { landed() }
        File(resultsDir, "t94v16_after_reader_back.json").writeText(
            JSONObject()
                .put("topLevelPage", topLevelPage() ?: JSONObject.NULL)
                .put("inspirationDetail", inspirationDetailVisible())
                .put("inspirationList", inspirationListVisible())
                .put("leftReader", leftReader)
                .put("tree", composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 12).take(12000))
                .toString(2),
        )
        assertTrue("离开阅读器后必须能识别当前页面", leftReader)
        if (!inspirationDetailVisible()) {
            if (topLevelPage() != null) {
                clickTopLevelTab("灵感")
            } else {
                assertTrue("灵感列表必须可识别", inspirationListVisible())
            }
            openInspirationDetail("T94A-$runId")
        }
    }

    private fun backToInspirationList() {
        if (composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()) {
            pressBack()
        }
        assertTrue("灵感列表搜索入口必须出现", waitFor(lastPhase, UI_TIMEOUT_MS, "灵感列表") {
            composeRule.onAllNodesWithContentDescription("搜索灵感").fetchSemanticsNodes().isNotEmpty()
        })
    }

    /** 打开指定标题的灵感详情（列表按更新时间倒序，本轮条目在顶部）。 */
    private fun openInspirationDetail(title: String) {
        assertTrue("列表必须可见条目 $title", waitFor(lastPhase, UI_TIMEOUT_MS, "条目") { textVisible(title) })
        val clickable = composeRule.onAllNodes(hasText(title, substring = true) and hasClickAction())
            .fetchSemanticsNodes()
        if (clickable.isNotEmpty()) {
            composeRule.onAllNodes(hasText(title, substring = true) and hasClickAction())[0].performClick()
        } else {
            composeRule.onAllNodesWithText(title, substring = true)[0].performClick()
        }
        composeRule.waitForIdle()
        assertTrue(
            "详情必须打开（出现编辑入口）",
            waitFor(lastPhase, UI_TIMEOUT_MS, "详情") {
                composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()
            },
        )
    }

    private fun selectionToolbarExcerpt(): String {
        val nodes = composeRule.onAllNodesWithTag(
            ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG,
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("选区工具条微条必须唯一命中", 1, nodes.size)
        return nodes.first().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()
    }

    private fun subtreeTexts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { out.add(it.text) }
        node.children.forEach { child -> out.addAll(subtreeTexts(child)) }
        return out
    }

    private fun editableTextOf(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            ?: ""

    private fun sameBounds(a: SemanticsNode, b: SemanticsNode): Boolean {
        val x = a.boundsInRoot
        val y = b.boundsInRoot
        return kotlin.math.abs(x.top - y.top) < 1f &&
            kotlin.math.abs(x.left - y.left) < 1f &&
            kotlin.math.abs(x.bottom - y.bottom) < 1f &&
            kotlin.math.abs(x.right - y.right) < 1f
    }

    private data class FieldResolution(
        val node: SemanticsNode?,
        val unique: Boolean,
        val diagnostics: Map<String, Any?>,
    )

    private fun resolveEditableField(label: String): FieldResolution {
        val editable = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val diag = linkedMapOf<String, Any?>("fieldLabel" to label, "editableNodeCount" to editable.size)
        if (editable.isEmpty()) {
            diag["matchStrategy"] = "no-editable-node"
            return FieldResolution(null, false, diag)
        }
        val labelled = editable.filter { node -> subtreeTexts(node).any { it.contains(label) } }
        diag["labelMatchCount"] = labelled.size
        if (labelled.size == 1) {
            diag["matchStrategy"] = "subtree-label"
            return FieldResolution(labelled[0], true, diag)
        }
        val labelNodes = composeRule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes()
        diag["labelNodeCount"] = labelNodes.size
        if (labelNodes.isEmpty()) {
            diag["matchStrategy"] = "no-label-node"
            return FieldResolution(null, false, diag)
        }
        val labelY = labelNodes[0].boundsInRoot.center.y
        val ranked = editable.mapIndexed { index, node ->
            index to kotlin.math.abs(node.boundsInRoot.center.y - labelY)
        }.sortedBy { it.second }
        diag["matchStrategy"] = "nearest-by-y"
        val unique = ranked.isNotEmpty() && (ranked.size == 1 || (ranked[1].second - ranked[0].second) > 1f)
        diag["uniqueMinimum"] = unique
        return FieldResolution(if (unique) editable[ranked[0].first] else null, unique, diag)
    }

    private fun editableInteractionFor(target: SemanticsNode) =
        composeRule.onAllNodes(hasSetTextAction())[
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                .indexOfFirst { sameBounds(it, target) }.also {
                    assertTrue("已解析字段必须仍在可编辑集合中", it >= 0)
                },
        ]

    private fun readBack(target: SemanticsNode): String? =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .firstOrNull { sameBounds(it, target) }
            ?.let { editableTextOf(it) }

    private fun addChipViaUi(placeholder: String, value: String) {
        val label = composeRule.onNode(hasText(placeholder, substring = true))
        val labelY = label.fetchSemanticsNode().boundsInRoot.center.y
        val fields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertTrue("「$placeholder」附近必须有输入框", fields.isNotEmpty())
        val fieldIndex = fields.indices.minByOrNull {
            kotlin.math.abs(fields[it].boundsInRoot.center.y - labelY)
        }!!
        val field = composeRule.onAllNodes(hasSetTextAction())[fieldIndex]
        field.performTextInput(value)
        composeRule.waitForIdle()
        val fieldY = field.fetchSemanticsNode().boundsInRoot.center.y
        val addNodes = composeRule.onAllNodesWithText("添加").fetchSemanticsNodes()
        assertTrue("「$placeholder」旁必须有「添加」按钮", addNodes.isNotEmpty())
        val addIndex = addNodes.indices.minByOrNull {
            kotlin.math.abs(addNodes[it].boundsInRoot.center.y - fieldY)
        }!!
        composeRule.onAllNodesWithText("添加")[addIndex].performClick()
        composeRule.waitForIdle()
    }

    private fun markPhase(phase: String) {
        lastPhase = phase
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeEvidence(phase: String, ok: Boolean, details: Map<String, Any?>) {
        lastPhase = phase
        val obj = JSONObject().put("phase", phase).put("pass", ok)
            .put("at", Instant.now().toString())
            .put("runId", runId)
            .put("pid", Process.myPid())
            .put("packageName", context.packageName)
        details.forEach { (k, v) -> obj.put(k, v?.toString() ?: "") }
        File(resultsDir, "$phase.json").writeText(obj.toString(2))
        android.util.Log.i(TAG, "$phase pass=$ok ${obj.toString(0)}")
    }
}
