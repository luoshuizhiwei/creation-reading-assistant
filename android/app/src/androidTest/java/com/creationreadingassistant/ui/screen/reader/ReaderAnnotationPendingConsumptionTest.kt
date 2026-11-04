package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * CRA-JOINT-ACCEPTANCE-TESTS-v1：[ReaderProgressEffects] SE4「精确跳转」effect 的
 * **key 与 pending 时序**最小仪器补验（M-BUG-10 合同）。
 *
 * ## 被验合同（sync/md 合流版 SE4 的嫁接语义）
 *
 * 1. **及时消费**：`pendingHighlightId` 在文档未就绪（isLoading=true）时到达，
 *    不得滞留到某个无关的 Room 流发射时刻 —— 一旦文档就绪（isLoading 变 false），
 *    effect 因 key 含 `isLoading` 而立即重启并消费请求（跳转 + 清空 pending）。
 * 2. **不再误跳**：请求消费完成后（pendingHighlightId == null），highlights/notes
 *    等无关更新的重组不得再次触发跳转。
 *
 * ## 证明方式与边界（如实声明）
 *
 * - 本类在 Compose 仪器环境**真实组合** [ReaderProgressEffects]（生产函数本体，
 *   非 mock、非源码检查），通过注入的记录 lambda 与可变 state 驱动时序，
 *   断言的是 effect 的实际执行结果（跳转 lambda 是否被调用、pending 是否被清空）。
 * - 本类证明的是 **SE4 effect 的组合时序行为**；它不证明完整 ReaderScreen 的接线
 *   （pendingHighlightId 从哪里来、goToChapter 之后 UI 是否真的滚动到目标）。
 *   端到端回源行为由已验 MD 批次（MdTrueChapterAcceptV1Test 10/11/20/30 +
 *   设备侧 uiautomator 驱动）覆盖，本类不重复。
 * - Compose 仪器环境的时钟是测试驱动的：本类断言「文档就绪后**有限次重组内**被消费」
 *   （waitUntil 默认超时），不断言真实毫秒数。
 *
 * ## 数据边界
 *
 * 高亮/笔记实体只在**内存 state** 中构造（不落 Room），DB 仅用于 AnchorCacheStore
 * 的真实 DAO（隔离宿主私有库，jse4- 前缀 key，tearDown 清理）。
 */
@RunWith(AndroidJUnit4::class)
class ReaderAnnotationPendingConsumptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    private val highlights = mutableStateListOf<HighlightEntity>()
    private val pendingHighlightId = mutableStateOf<String?>(null)
    private val isLoading = mutableStateOf(true)
    private val pendingSourceLocatorJson = mutableStateOf<String?>(null)

    /** 跳转记录：PlainBranch 消费的唯一可观察出口。 */
    private val plainJumps = mutableListOf<Int>()

    private companion object {
        const val BOOK_ID = "jse4-annotation-pending"
        const val HIGHLIGHT_ID = "jse4-hl-1"

        /** 正文与高亮摘录（摘录必须真实出现在正文中，AnchorResolver 才能精确锚定）。 */
        const val PLAIN_CONTENT =
            "中性正文开头甲甲甲。\n中性中段用于锚定乙乙乙。\n中性正文结尾丙丙丙。"
        const val EXCERPT = "中性中段用于锚定乙乙乙"
    }

    private fun highlight(
        id: String,
        excerpt: String,
        charOffset: Int,
    ) = HighlightEntity(
        id = id,
        book_id = BOOK_ID,
        text = excerpt,
        color = "yellow",
        chapter_title = "第 1 章",
        progress_percent = 50f,
        locator_json = "{\"offset\":$charOffset,\"locator_v2\":{\"v\":2,\"offset\":$charOffset,\"ci\":0,\"co\":$charOffset}}",
        created_at = Instant.now().toString(),
        updated_at = Instant.now().toString(),
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries()
            .build()
        highlights.clear()
        plainJumps.clear()
        pendingHighlightId.value = null
        isLoading.value = true
        pendingSourceLocatorJson.value = null
    }

    @After
    fun tearDown() = runBlocking {
        // AnchorCacheStore 真实 DAO 的本批 key 清理（隔离宿主私有库，jse4- 前缀）
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM reader_anchor_cache WHERE entity_id LIKE 'jse4-%' OR content_key LIKE 'jse4-%'"
        )
        db.close()
    }

    /** 组合生产 ReaderProgressEffects；除被测状态外全部取最小中性值。 */
    private fun composeEffects() {
        composeRule.setContent {
            ReaderProgressEffects(
                bid = BOOK_ID,
                isLoading = isLoading.value,
                error = null,
                readingActive = false,
                progressPercent = 0f,
                loadedBook = null,
                chapterIndex = 0,
                epubBook = null,
                readingUnits = emptyList(),
                pagerEngineOn = false,
                plainListState = LazyListState(),
                epubListState = LazyListState(),
                txtStreamingDocument = null,
                plainContent = PLAIN_CONTENT,
                markdownDocument = null,
                pagedSource = null,
                savedPlainOffset = 0,
                savedPlainPercent = 0f,
                chapterBlocks = emptyList(),
                savedEpubOffsetInChapter = 0,
                chapterBase = 0,
                blockGlobalOffsets = emptyList(),
                highlights = highlights,
                notes = emptyList(),
                bookIndex = null,
                chapterStartOffsets = listOf(0),
                anchorCacheStore = com.creationreadingassistant.feature.reader.locator.AnchorCacheStore(
                    db.readerAnchorCacheDao(),
                ),
                recentChapters = mutableStateListOf(),
                activeReadingMsState = mutableLongStateOf(0L),
                pagedAbsOffsetState = mutableIntStateOf(-1),
                pagedPercentState = mutableFloatStateOf(0f),
                pendingInitialPositionState = mutableStateOf(false),
                pendingHighlightIdState = pendingHighlightId,
                pendingSourceLocatorJsonState = pendingSourceLocatorJson,
                requestedSourceNavigationChapterState = mutableStateOf<Int?>(null),
                navFocusBlockIndexState = mutableStateOf<Int?>(null),
                scrollFocusRequestState = mutableStateOf<SearchScrollFocusRequest?>(null),
                chapterIndexState = mutableIntStateOf(0),
                settingsRef = mutableStateOf(ReaderSettings()),
                pagedJumpRequest = mutableStateOf<Int?>(null),
                onAction = {},
                showNotice = {},
                goToChapter = {},
                jumpToPlainOffset = { offset -> plainJumps.add(offset) },
                jumpToMarkdownOffset = {},
                onLoadChapterBlocks = { _, _ -> emptyList() },
                onExtractChapterText = { _, _ -> "" },
            )
        }
    }

    /**
     * **se4_10**：pendingHighlightId 在 isLoading=true 时已就位 —— 文档就绪
     * （isLoading → false）后，请求必须在**无需任何无关 Room 流发射**的情况下被消费：
     * jumpToPlainOffset 获得锚定偏移、pendingHighlightId 被清空。
     */
    @Test
    fun se4_10_annotationRequestConsumedWhenDocumentBecomesReady() {
        val targetOffset = PLAIN_CONTENT.indexOf(EXCERPT)
        assertTrue("夹具前置：摘录必须真实出现在正文中", targetOffset > 0)
        highlights.add(highlight(HIGHLIGHT_ID, EXCERPT, targetOffset))
        pendingHighlightId.value = "highlight:$HIGHLIGHT_ID"

        composeEffects()

        // isLoading=true：SE4 不得消费（即使高亮已在列表里）
        composeRule.waitForIdle()
        assertEquals("文档未就绪时不得消费", emptyList<Int>(), plainJumps.toList())
        assertEquals("未消费前 pending 必须保留", "highlight:$HIGHLIGHT_ID", pendingHighlightId.value)

        // 文档就绪：SE4 因 key(isLoading) 变化重启 → 立即消费
        isLoading.value = false
        composeRule.waitUntil(timeoutMillis = 10_000) {
            plainJumps.isNotEmpty() && pendingHighlightId.value == null
        }

        assertEquals("消费必须恰好跳转一次", 1, plainJumps.size)
        assertEquals("跳转偏移必须等于摘录在正文中的锚定位置", targetOffset, plainJumps.first())
        assertEquals("消费后 pendingHighlightId 必须清空", null, pendingHighlightId.value)
    }

    /**
     * **se4_20**：消费完成后，highlights 的**无关更新**（追加另一条高亮、列表重组）
     * 不得再次触发跳转 —— 旧缺陷正是「阅读中途毫无征兆地跳到某条批注位置」。
     */
    @Test
    fun se4_20_noSecondJumpOnUnrelatedHighlightsUpdate() {
        val targetOffset = PLAIN_CONTENT.indexOf(EXCERPT)
        highlights.add(highlight(HIGHLIGHT_ID, EXCERPT, targetOffset))
        pendingHighlightId.value = "highlight:$HIGHLIGHT_ID"

        composeEffects()
        isLoading.value = false
        composeRule.waitUntil(timeoutMillis = 10_000) {
            plainJumps.isNotEmpty() && pendingHighlightId.value == null
        }
        assertEquals(1, plainJumps.size)

        // 无关更新：另一条高亮进入列表（SE4 key 含 highlights，effect 会重启）
        highlights.add(
            highlight("jse4-hl-2-unrelated", "中性正文开头甲甲甲", PLAIN_CONTENT.indexOf("中性正文开头甲甲甲")),
        )
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        assertEquals(
            "无关更新不得再次触发跳转（pending 已清空，effect 必须空转）",
            1,
            plainJumps.size,
        )
        assertEquals("pending 必须保持为空", null, pendingHighlightId.value)
    }

    /**
     * **se4_30**：源定位请求在场（pendingSourceLocatorJson != null）时，SE4 必须
     * 让位（不消费批注跳转）—— 两通道互斥是 SE4 key 嫁接的另一半合同。
     */
    @Test
    fun se4_30_defersToSourceNavigationRequest() {
        val targetOffset = PLAIN_CONTENT.indexOf(EXCERPT)
        highlights.add(highlight(HIGHLIGHT_ID, EXCERPT, targetOffset))
        pendingHighlightId.value = "highlight:$HIGHLIGHT_ID"
        pendingSourceLocatorJson.value = "{\"offset\":1}"

        composeEffects()
        isLoading.value = false
        composeRule.waitForIdle()

        assertTrue(
            "源定位请求在场时 SE4 必须让位（不得消费批注跳转）",
            plainJumps.isEmpty(),
        )
        assertEquals("SE4 让位时不得清空 pendingHighlightId", "highlight:$HIGHLIGHT_ID", pendingHighlightId.value)
        assertTrue("前置：源定位请求必须仍在场", pendingSourceLocatorJson.value != null)
    }
}
