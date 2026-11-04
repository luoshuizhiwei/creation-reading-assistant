package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.InspirationAdoptionRecord
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

/**
 * 任务106-A-v1：轻量取材连续旅程的**真实 UI 窄验收**。
 *
 * 覆盖顺序与合同：
 * 1. 本批两条中性摘录（fixture 层：生产 DAO 写入 BookEntity + InspirationEntity，
 *    不冒充阅读器划词/「记为灵感」动作）→ 真实列表多选；
 * 2. 真实「合并为素材卡」→ 新卡 payload.excerpts 保留两个 source/locator，原条目归档；
 * 3. 真实详情「记录」对话框写入文字采用去向 → payload.adoptions 与新状态「已采用」；
 * 4. 真实列表「已采用」管线筛选找回该卡，归档原条目被筛除；
 * 5. 返回列表并关闭/重开 Activity → 详情仍显示两个来源与 adoption，DB 行逐字节不变。
 *
 * 边界：不新增素材工作台/专题/导出/删除撤销/链接类型矩阵；不修改产品 I1/I2；
 * 所有 ID 由本批 runId 生成并只操作这些 ID；外部产物仅本批结果目录（由外层 pull 后随包卸载清理）。
 */
@RunWith(AndroidJUnit4::class)
class MaterialAdoptionJourneyDeviceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private var lastPhase: String = "start"
    private var bookId: String = ""
    private var idA: String = ""
    private var idB: String = ""
    private var cardId: String = ""

    private lateinit var bookTitle: String
    private lateinit var titleA: String
    private lateinit var titleB: String
    private lateinit var excerptA: String
    private lateinit var excerptB: String
    private lateinit var chapterA: String
    private lateinit var chapterB: String
    private lateinit var locatorA: String
    private lateinit var locatorB: String
    private lateinit var adoptionValue: String

    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        const val TAG = "CRA106A"
        const val UI_TIMEOUT_MS = 20_000L
        const val DB_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 120L
        const val ADOPTION_TEXT_LABEL = "文字去向（用在哪一章/哪个作品）"
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = requireNotNull(InstrumentationRegistry.getArguments().getString("craRunId")) {
            "Root must bind craRunId"
        }
        require(runId.matches(Regex("T106A-[A-Za-z0-9-]+"))) { "unexpected runId: $runId" }
        val root = requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" }
        resultsDir = File(root, "task106a-results-$runId")
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
            File(resultsDir, "t106a_99_summary.json").writeText(
                JSONObject()
                    .put("runId", runId)
                    .put("lastPhase", lastPhase)
                    .put("bookId", bookId)
                    .put("itemA", idA)
                    .put("itemB", idB)
                    .put("materialCard", cardId)
                    .put("fixtureLevel", "production DAO seed (BookEntity + InspirationEntity); not a reading-excerpt action")
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

    // ── fixture（明确层级：生产 DAO 直接落库，不冒充阅读摘录动作） ───────────

    private fun seedFixture() {
        bookId = "t106a-book-$runId"
        idA = "t106a-item-a-$runId"
        idB = "t106a-item-b-$runId"
        bookTitle = "T106中性来源-$runId"
        titleA = "T106甲-$runId"
        titleB = "T106乙-$runId"
        excerptA = "摘录甲内容-$runId：用于合并素材卡的第一处来源。"
        excerptB = "摘录乙内容-$runId：用于合并素材卡的第二处来源。"
        chapterA = "第一章 甲-$runId"
        chapterB = "第二章 乙-$runId"
        adoptionValue = "写进正文第三章-$runId"
        locatorA = LocatorCodec.encode(legacyOffset = 120, chapterIndex = 0, charOffset = 120, excerpt = excerptA)
        locatorB = LocatorCodec.encode(legacyOffset = 300, chapterIndex = 0, charOffset = 300, excerpt = excerptB)

        runBlocking {
            assertNull("本轮书 ID 不得预先存在", rawOne("SELECT id FROM books WHERE id='$bookId'"))
            assertNull(
                "本轮灵感 ID 不得预先存在",
                rawOne("SELECT id FROM inspirations WHERE id IN ('$idA','$idB')"),
            )
            val now = Instant.now().toString()
            db.bookDao().upsert(
                BookEntity(
                    id = bookId,
                    title = bookTitle,
                    author = "中性测试作者",
                    format = "txt",
                    original_file_name = "t106a-neutral-$runId.txt",
                    imported_at = now,
                    updated_at = now,
                ),
            )
            db.inspirationDao().upsert(
                InspirationEntity(
                    id = idA,
                    title = titleA,
                    body = "正文甲-$runId",
                    type = "note",
                    status = "inbox",
                    source_book_id = bookId,
                    payload = encodePayload(sourceOf(chapterA, excerptA, locatorA, 12f)),
                    created_at = now,
                    updated_at = now,
                ),
            )
            db.inspirationDao().upsert(
                InspirationEntity(
                    id = idB,
                    title = titleB,
                    body = "正文乙-$runId",
                    type = "note",
                    status = "inbox",
                    source_book_id = bookId,
                    payload = encodePayload(sourceOf(chapterB, excerptB, locatorB, 30f)),
                    created_at = now,
                    updated_at = now,
                ),
            )
        }
        writeEvidence(
            "00_fixture_created",
            mapOf(
                "bookId" to bookId,
                "itemA" to idA,
                "itemB" to idB,
                "locatorA" to locatorA,
                "locatorB" to locatorB,
                "level" to "production DAO seed; no reading excerpt action",
            ),
        )
    }

    private fun sourceOf(chapter: String, excerpt: String, locator: String, percent: Float) =
        InspirationPayloadData(
            tags = listOf("t106a-$runId"),
            source = InspirationSourceInfo(
                bookId = bookId,
                bookTitle = bookTitle,
                bookAuthor = "中性测试作者",
                chapterTitle = chapter,
                locationLabel = "$percent%",
                progressPercent = percent,
                excerpt = excerpt,
                locatorJson = locator,
            ),
        )

    private fun encodePayload(payload: InspirationPayloadData): String =
        payloadJson.encodeToString(InspirationPayloadData.serializer(), payload)

    // ── 旅程 ─────────────────────────────────────────────────────────────────

    @Test
    fun mergeAdoptFilterAndReopenKeepSourcesAndAdoption() {
        markPhase("t106a_10_launch")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue("顶层页面必须出现", waitFor(UI_TIMEOUT_MS, "顶层页面") { topLevelPage() != null })
        clickTopLevelTab("灵感")
        assertTrue("灵感列表必须出现", waitFor(UI_TIMEOUT_MS, "灵感列表") { inspirationListVisible() })
        assertTrue(
            "本批两条素材卡片必须出现在列表",
            waitFor(UI_TIMEOUT_MS, "两条卡片") { cardPresent(idA) && cardPresent(idB) },
        )
        writeEvidence("10_list_ready", mapOf("itemA" to idA, "itemB" to idB))

        markPhase("t106a_20_merge")
        clickUnique("多选")
        selectCard(idA)
        selectCard(idB)
        assertTrue("多选计数必须为 2", waitFor(UI_TIMEOUT_MS, "已选 2 条") { textVisible("已选 2 条") })
        // 107-A：点击前采选择态真实观测（按钮节点数/bounds/是否布局、选中计数文案、本批行快照）。
        captureMergePreClick()
        // 110修复断言：合并按钮唯一且在窗口内有正的可见高度（109实测 198x0 触摸落空）。
        val preMergeNodes = composeRule.onAllNodes(hasText("合并为素材卡") and hasClickAction())
            .fetchSemanticsNodes()
        assertTrue("合并按钮必须唯一（实得 ${preMergeNodes.size}）", preMergeNodes.size == 1)
        val preVisible = visibleHeightPx(preMergeNodes.single())
        assertTrue("合并按钮可见高度必须 > 0（实得 $preVisible）", preVisible > 0f)
        clickUnique("合并为素材卡")
        // 108：点击后**立刻**采一次（选择条是否仍在 = onClick 是否执行），再采 2 秒后的状态；
        // 原 20 秒等待与断言保持不变。
        composeRule.waitForIdle()
        captureMergeImmediate()
        Thread.sleep(2_000)
        composeRule.waitForIdle()
        captureMergePostClick()
        val merged = awaitMergedCard()
        assertNotNull("合并必须产出素材卡并归档两条来源", merged)
        cardId = requireNotNull(merged)
        // 111导航修正：生产合并成功回调固定 state.page=Detail(cardId)（InspirationRoute.kt MergeToMaterialCard
        // 成功分支；110实测 detailOpen=true/listVisible=false）。先确证已在详情，再以真实返回动作回列表、
        // 等列表就绪，然后沿用原“新卡出现在列表”断言与后续全部 payload/归档断言。
        assertTrue(
            "合并成功后必须已进入素材卡详情",
            waitFor(UI_TIMEOUT_MS, "合并后详情") {
                composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()
            },
        )
        pressBack()
        assertTrue("返回后必须回到灵感列表", waitFor(UI_TIMEOUT_MS, "灵感列表") { inspirationListVisible() })
        assertTrue("新素材卡必须出现在列表", waitFor(UI_TIMEOUT_MS, "素材卡卡片") { cardPresent(cardId) })

        val payloadA = requirePayload(idA)
        val payloadB = requirePayload(idB)
        val cardPayload = requirePayload(cardId)
        assertEquals("素材卡必须保留两条来源摘录", 2, cardPayload.excerpts.size)
        assertEquals(
            "两条来源的 locator 必须逐条保留",
            setOf(locatorA, locatorB),
            cardPayload.excerpts.mapNotNull { it.locatorJson }.toSet(),
        )
        assertEquals(
            "两条来源必须指向本批同一中性书",
            setOf(bookId),
            cardPayload.excerpts.mapNotNull { it.bookId }.toSet(),
        )
        assertEquals(
            "两条来源的章节快照必须保留",
            setOf(chapterA, chapterB),
            cardPayload.excerpts.mapNotNull { it.chapterTitle }.toSet(),
        )
        assertEquals(
            "两条来源的摘录文字必须保留",
            setOf(excerptA, excerptB),
            cardPayload.excerpts.mapNotNull { it.excerpt }.toSet(),
        )
        assertEquals("原条目 A 必须归档并记录去向", "archived", statusOf(idA))
        assertEquals("原条目 B 必须归档并记录去向", "archived", statusOf(idB))
        assertEquals("原条目 A 的 mergedInto 必须指向新卡", cardId, payloadA.mergedInto)
        assertEquals("原条目 B 的 mergedInto 必须指向新卡", cardId, payloadB.mergedInto)
        assertEquals("新卡初始状态必须为待整理", "reviewing", statusOf(cardId))
        assertNull("新卡不得被删除", rawOne("SELECT deleted_at FROM inspirations WHERE id='$cardId'"))
        writeEvidence(
            "20_merged",
            mapOf(
                "materialCard" to cardId,
                "excerptCount" to cardPayload.excerpts.size,
                "locators" to cardPayload.excerpts.mapNotNull { it.locatorJson },
                "chapters" to cardPayload.excerpts.mapNotNull { it.chapterTitle },
                "statusA" to statusOf(idA),
                "statusB" to statusOf(idB),
                "statusCard" to statusOf(cardId),
            ),
        )

        markPhase("t106a_30_detail_adoption")
        openCard(cardId)
        assertTrue(
            "详情必须显示两条聚合来源",
            waitFor(UI_TIMEOUT_MS, "聚合摘录 2 条来源") { textVisible("聚合摘录 · 2 条来源") },
        )
        assertTrue(
            "详情必须显示两个来源章节",
            waitFor(UI_TIMEOUT_MS, "两个来源章节") { textVisible(chapterA) && textVisible(chapterB) },
        )
        assertTrue(
            "详情必须显示两段来源摘录",
            textVisible(excerptA) && textVisible(excerptB),
        )
        scrollDetailTo("采用去向")
        clickUnique("记录")
        assertTrue(
            "采用去向对话框必须出现",
            waitFor(UI_TIMEOUT_MS, "记录采用去向") { textVisible("记录采用去向") },
        )
        val editables = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("对话框必须只有文字去向与备注两个输入框", 2, editables.size)
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput(adoptionValue)
        composeRule.waitForIdle()
        clickUnique("保存")
        assertTrue(
            "保存后对话框必须关闭",
            waitFor(UI_TIMEOUT_MS, "对话框关闭") { !textVisible("记录采用去向") },
        )
        assertTrue("详情必须显示新采用去向", waitFor(UI_TIMEOUT_MS, "采用去向文字") { textVisible(adoptionValue) })

        val adopted = awaitPayload(cardId, "采用记录落库") { it.adoptions.size == 1 }
        assertNotNull("采用记录必须落库", adopted)
        val record = adopted!!.adoptions.single()
        assertEquals("去向类型必须为文字", InspirationAdoptionRecord.KIND_TEXT, record.kind)
        assertEquals("去向文字必须逐字保留", adoptionValue, record.value)
        assertNull("空备注不得写成空串", record.note)
        assertEquals("首次采用必须把状态推进为已采用", "used", statusOf(cardId))
        val payloadAfterAdoptionSha = rawPayloadSha(cardId)
        writeEvidence(
            "30_adoption",
            mapOf(
                "materialCard" to cardId,
                "kind" to record.kind,
                "value" to record.value,
                "note" to JSONObject.NULL,
                "status" to statusOf(cardId),
                "payloadSha256" to payloadAfterAdoptionSha,
            ),
        )

        markPhase("t106a_40_used_filter")
        pressBack()
        assertTrue("返回后必须回到灵感列表", waitFor(UI_TIMEOUT_MS, "灵感列表") { inspirationListVisible() })
        val usedPill = composeRule.onAllNodesWithText("已采用 1").fetchSemanticsNodes()
        assertTrue("已采用阶段胶囊必须显示计数 1", usedPill.isNotEmpty())
        composeRule.onAllNodesWithText("已采用 1")[0].performClick()
        composeRule.waitForIdle()
        assertTrue(
            "已采用筛选必须只找回新素材卡，归档原条目被筛除",
            waitFor(UI_TIMEOUT_MS, "已采用筛选结果") {
                cardPresent(cardId) && !cardPresent(idA) && !cardPresent(idB)
            },
        )
        writeEvidence(
            "40_used_filter",
            mapOf(
                "filterLabel" to "已采用 1",
                "cardPresent" to cardPresent(cardId),
                "itemAPresent" to cardPresent(idA),
                "itemBPresent" to cardPresent(idB),
            ),
        )

        openCard(cardId)
        assertTrue(
            "筛选后打开的详情仍显示采用去向",
            waitFor(UI_TIMEOUT_MS, "采用去向仍显示") { textVisible(adoptionValue) },
        )
        assertTrue(
            "筛选后打开的详情仍显示两个来源章节",
            textVisible(chapterA) && textVisible(chapterB),
        )
        assertEquals("返回前 payload 不得被读取改写", payloadAfterAdoptionSha, rawPayloadSha(cardId))
        pressBack()
        assertTrue("再次返回列表", waitFor(UI_TIMEOUT_MS, "灵感列表") { inspirationListVisible() })

        markPhase("t106a_50_reopen")
        scenario?.close()
        composeRule.waitForIdle()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue("重开后顶层页面必须出现", waitFor(UI_TIMEOUT_MS, "重开顶层页面") { topLevelPage() != null })
        clickTopLevelTab("灵感")
        assertTrue("重开后灵感列表必须出现", waitFor(UI_TIMEOUT_MS, "重开灵感列表") { inspirationListVisible() })
        assertTrue("重开后素材卡必须仍在列表", waitFor(UI_TIMEOUT_MS, "重开素材卡") { cardPresent(cardId) })
        openCard(cardId)
        assertTrue(
            "重开详情必须仍显示采用去向",
            waitFor(UI_TIMEOUT_MS, "重开采用去向") { textVisible(adoptionValue) },
        )
        assertTrue(
            "重开详情必须仍显示两个来源章节",
            textVisible(chapterA) && textVisible(chapterB),
        )
        assertTrue(
            "重开详情必须仍显示两段来源摘录",
            textVisible(excerptA) && textVisible(excerptB),
        )
        assertEquals("重开后素材卡 payload 必须逐字节不变", payloadAfterAdoptionSha, rawPayloadSha(cardId))
        val reopened = requirePayload(cardId)
        assertEquals("重开后采用记录必须仍为一条", 1, reopened.adoptions.size)
        assertEquals("重开后采用文字必须不变", adoptionValue, reopened.adoptions.single().value)
        assertEquals("重开后两个来源必须仍在", 2, reopened.excerpts.size)
        writeEvidence(
            "50_reopen",
            mapOf(
                "materialCard" to cardId,
                "payloadShaBefore" to payloadAfterAdoptionSha,
                "payloadShaAfter" to rawPayloadSha(cardId),
                "adoptions" to reopened.adoptions.size,
                "excerpts" to reopened.excerpts.size,
            ),
        )
    }

    // ── DB / 证据 ────────────────────────────────────────────────────────────

    private fun requirePayload(id: String): InspirationPayloadData {
        val raw = rawOne("SELECT payload FROM inspirations WHERE id='$id' AND deleted_at IS NULL")
        assertNotNull("$id 的 payload 必须存在", raw)
        return payloadJson.decodeFromString(InspirationPayloadData.serializer(), raw!!)
    }

    private fun awaitPayload(
        id: String,
        description: String,
        predicate: (InspirationPayloadData) -> Boolean,
    ): InspirationPayloadData? {
        val deadline = System.currentTimeMillis() + DB_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val raw = rawOne("SELECT payload FROM inspirations WHERE id='$id' AND deleted_at IS NULL")
            if (raw != null) {
                val payload = runCatching {
                    payloadJson.decodeFromString(InspirationPayloadData.serializer(), raw)
                }.getOrNull()
                if (payload != null && predicate(payload)) return payload
            }
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$lastPhase 等待超时：$description")
        return null
    }

    private fun awaitMergedCard(): String? {
        val deadline = System.currentTimeMillis() + DB_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val mergedA = rawOne("SELECT payload FROM inspirations WHERE id='$idA'")
                ?.let { runCatching { payloadJson.decodeFromString(InspirationPayloadData.serializer(), it) }.getOrNull() }
                ?.mergedInto
            val mergedB = rawOne("SELECT payload FROM inspirations WHERE id='$idB'")
                ?.let { runCatching { payloadJson.decodeFromString(InspirationPayloadData.serializer(), it) }.getOrNull() }
                ?.mergedInto
            if (mergedA != null && mergedA == mergedB &&
                rawOne("SELECT id FROM inspirations WHERE id='$mergedA' AND deleted_at IS NULL") != null
            ) {
                return mergedA
            }
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun statusOf(id: String): String? = rawOne("SELECT status FROM inspirations WHERE id='$id'")

    private fun rawPayloadSha(id: String): String? =
        rawOne("SELECT payload FROM inspirations WHERE id='$id'")?.let { sha256(it) }

    private fun rawOne(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun writeEvidence(name: String, data: Map<String, Any?>) {
        val json = JSONObject()
            .put("runId", runId)
            .put("phase", lastPhase)
            .put("fixtureLevel", "production DAO seed; real UI click/persist from list onward")
        data.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
        json.put("tree", runCatching {
            composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 12).take(12000)
        }.getOrDefault(""))
        File(resultsDir, "t106a_$name.json").writeText(json.toString(2))
    }

    private fun markPhase(phase: String) {
        lastPhase = phase
        android.util.Log.i(TAG, "PHASE=$phase")
    }

    // ── 107-A：合并链最小观测（只读，不改变原断言/流程） ─────────────────────

    /** 点击合并前的选择态观测：按钮节点数/bounds（root+window）/尺寸/是否布局/可见高度、选中计数文案、本批行快照。 */
    private fun captureMergePreClick() {
        val mergeNodes = composeRule.onAllNodes(hasText("合并为素材卡") and hasClickAction())
            .fetchSemanticsNodes()
        writeEvidence(
            "21_pre_merge",
            mapOf(
                "mergeNodeCount" to mergeNodes.size,
                "mergeBoundsInRoot" to mergeNodes.map { it.boundsInRoot.toString() },
                "mergeBoundsInWindow" to mergeNodes.map { it.boundsInWindow.toString() },
                "mergeSizePx" to mergeNodes.map { "${it.size.width}x${it.size.height}" },
                "mergePlaced" to mergeNodes.map { it.layoutInfo.isPlaced },
                "mergeVisibleHeightPx" to mergeNodes.map { visibleHeightOf(it) },
                "windowPx" to windowSizePx(),
                "selectedCountTextVisible" to textVisible("已选 2 条"),
                "selectedCountTexts" to selectedCountTexts(),
                "rowsBefore" to inspirationRowsSnapshot(),
            ),
        )
    }

    /**
     * 点击合并后的**即时**观测（睡眠前）：选择条是否已消失是本批的关键直接信号——
     * 生产 onClick 第一件事是 `onAction(...)`，随后把 `selectMode=false` 并清空选择；
     * 因此“点击后选择条仍在”直接说明 onClick 没有执行（触摸未送达），
     * 而不是“派发后被 VM 拒绝”。
     */
    private fun captureMergeImmediate() {
        writeEvidence(
            "23_post_click_immediate",
            mapOf(
                "selectionBarStillVisible" to textVisible("已选 2 条"),
                "mergeNodeCount" to composeRule
                    .onAllNodes(hasText("合并为素材卡") and hasClickAction())
                    .fetchSemanticsNodes().size,
                "detailOpen" to composeRule.onAllNodesWithContentDescription("编辑灵感")
                    .fetchSemanticsNodes().isNotEmpty(),
                "listVisible" to inspirationListVisible(),
                "snackbarRejected" to textVisible("至少选择两条素材才能合并。"),
                "snackbarMerged" to textVisible("已合并为素材卡，原条目已归档。"),
                "rowsImmediate" to inspirationRowsSnapshot(),
            ),
        )
    }

    /** 点击合并后的延时观测：页面落点、两种生产提示、本批全部行，用于区分被拒/已写入。 */
    private fun captureMergePostClick() {
        val detailOpen = composeRule.onAllNodesWithContentDescription("编辑灵感")
            .fetchSemanticsNodes().isNotEmpty()
        writeEvidence(
            "22_post_merge",
            mapOf(
                "detailOpen" to detailOpen,
                "listVisible" to inspirationListVisible(),
                "selectionBarStillVisible" to textVisible("已选 2 条"),
                "snackbarRejected" to textVisible("至少选择两条素材才能合并。"),
                "snackbarMerged" to textVisible("已合并为素材卡，原条目已归档。"),
                "rowsAfter" to inspirationRowsSnapshot(),
            ),
        )
    }

    /** 选中计数文案的原始文本（用于确认派发参数条数与 UI 一致）。 */
    private fun selectedCountTexts(): List<String> =
        composeRule.onAllNodesWithText("已选", substring = true).fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            }

    /** 窗口像素尺寸：只读当前 Activity 窗口，不改变任何状态。 */
    private fun windowSizePx(): String {
        var size = "unavailable"
        scenario?.onActivity { activity ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            size = "${metrics.widthPixels}x${metrics.heightPixels}"
        }
        return size
    }

    /** 110断言用：可见高度数值（root bounds 与窗口矩形求交）；窗口未知时返回 -1f 使断言失败。 */
    private fun visibleHeightPx(node: androidx.compose.ui.semantics.SemanticsNode): Float {
        val bounds = node.boundsInRoot
        var windowHeight = 0f
        scenario?.onActivity { activity ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            windowHeight = metrics.heightPixels.toFloat()
        }
        if (windowHeight <= 0f) return -1f
        val top = bounds.top.coerceAtLeast(0f)
        val bottom = bounds.bottom.coerceAtMost(windowHeight)
        return (bottom - top).coerceAtLeast(0f)
    }

    /** 节点在当前窗口内的可见高度（root bounds 与窗口矩形求交），用于直接判断是否被挤出屏幕。 */
    private fun visibleHeightOf(node: androidx.compose.ui.semantics.SemanticsNode): String {
        val bounds = node.boundsInRoot
        var windowHeight = 0f
        scenario?.onActivity { activity ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            windowHeight = metrics.heightPixels.toFloat()
        }
        if (windowHeight <= 0f) return "unknown"
        val top = bounds.top.coerceAtLeast(0f)
        val bottom = bounds.bottom.coerceAtMost(windowHeight)
        val visible = (bottom - top).coerceAtLeast(0f)
        return "${visible.toInt()} (root ${bounds.top.toInt()}..${bounds.bottom.toInt()})"
    }

    /** 本批中性行快照：只按本批 bookId/runId 归属，不读其他数据。 */
    private fun inspirationRowsSnapshot(): List<String> {
        val out = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "SELECT id,status,deleted_at,updated_at,payload FROM inspirations " +
                "WHERE source_book_id='$bookId' OR id LIKE '%$runId' ORDER BY id",
        ).use { c ->
            while (c.moveToNext()) {
                val payload = c.getString(4)
                out += listOf(
                    c.getString(0),
                    c.getString(1),
                    c.getString(2) ?: "null",
                    c.getString(3),
                    payload?.let { sha256(it).take(12) } ?: "null",
                ).joinToString("|")
            }
        }
        return out
    }

    // ── UI 工具 ──────────────────────────────────────────────────────────────

    private fun waitFor(timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return true
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$lastPhase 等待超时（$timeoutMs ms）：$description")
        return false
    }

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun clickTopLevelTab(label: String) {
        composeRule.onAllNodes(hasText(label) and isSelectable())[0].performClick()
        composeRule.waitForIdle()
    }

    private fun inspirationListVisible(): Boolean =
        composeRule.onAllNodesWithContentDescription("搜索灵感").fetchSemanticsNodes().isNotEmpty()

    private fun cardPresent(id: String): Boolean =
        composeRule.onAllNodesWithTag("inspiration-card-$id").fetchSemanticsNodes().isNotEmpty()

    private fun textVisible(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun clickUnique(text: String) {
        assertTrue(
            "文本「$text」必须唯一可点击",
            waitFor(UI_TIMEOUT_MS, "唯一可点 $text") {
                composeRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size == 1
            },
        )
        composeRule.onAllNodes(hasText(text) and hasClickAction())[0].performClick()
        composeRule.waitForIdle()
    }

    private fun selectCard(id: String) {
        assertTrue("卡片 $id 必须存在", waitFor(UI_TIMEOUT_MS, "选择卡片 $id") { cardPresent(id) })
        composeRule.onNodeWithTag("inspiration-card-$id").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openCard(id: String) {
        assertTrue("卡片 $id 必须存在", waitFor(UI_TIMEOUT_MS, "打开卡片 $id") { cardPresent(id) })
        composeRule.onNodeWithTag("inspiration-card-$id").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(
            "素材详情必须打开",
            waitFor(UI_TIMEOUT_MS, "详情编辑入口") {
                composeRule.onAllNodesWithContentDescription("编辑灵感").fetchSemanticsNodes().isNotEmpty()
            },
        )
    }

    private fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** 详情 LazyColumn 滚到目标区块；多个滚动容器时取高度最大的一个。 */
    private fun scrollDetailTo(text: String) {
        val nodes = composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        assertTrue("详情必须有可滚动容器", nodes.isNotEmpty())
        val index = requireNotNull(nodes.indices.maxByOrNull { nodes[it].boundsInRoot.height })
        composeRule.onAllNodes(hasScrollAction())[index]
            .performScrollToNode(hasText(text, substring = true))
        composeRule.waitForIdle()
    }
}
