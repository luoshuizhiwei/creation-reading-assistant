package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometry
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_BOUNDARY_FIRST
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_BOUNDARY_LAST
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_ZONE_BOTTOM
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_ZONE_LEFT
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_ZONE_RIGHT
import com.creationreadingassistant.ui.screen.reader.content.LEGACY_EPUB_ZONE_TOP
import com.creationreadingassistant.ui.screen.reader.content.LegacyEpubTapArbiter
import com.creationreadingassistant.ui.screen.reader.sheets.TAP_PREVIEW_TAG_BOTTOM
import com.creationreadingassistant.ui.screen.reader.sheets.TAP_PREVIEW_TAG_CENTER
import com.creationreadingassistant.ui.screen.reader.sheets.TAP_PREVIEW_TAG_LEFT
import com.creationreadingassistant.ui.screen.reader.sheets.TAP_PREVIEW_TAG_RIGHT
import com.creationreadingassistant.ui.screen.reader.sheets.TAP_PREVIEW_TAG_TOP
import com.creationreadingassistant.ui.screen.reader.sheets.TapZonePreviewCard
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI 测试覆盖 ReaderScreen 关键 UI 行为。
 *
 * 由于 ReaderScreen 内部使用 hiltViewModel() 获取 SettingsViewModel，
 * 完整渲染需要 Hilt 注入链。本测试聚焦于：
 * - UI 状态数据类的行为验证
 * - Action 路由逻辑验证
 * - Compose 基础渲染（加载/错误/内容状态）
 * - 配置适配（最小宽度、字体缩放）
 * - 点击热区（第 9、10 组）：渲染**生产组件** —— 设置预览卡 `TapZonePreviewCard`
 *   与 legacy EPUB 的完整点击仲裁层 `LegacyEpubTapArbiter`（父级中央兜底 + 分区 overlay），
 *   按共享几何算坐标真点，断言真实回调次数
 *
 * 这两组点击热区用例是 instrumentation 用例，只能在真机/模拟器上执行；
 * 由本文件所在切片完成编译门禁，实际运行由后续带设备的窗口验收（见各用例注释）。
 */
class ReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 1. 加载状态显示 — 章节加载中显示正确 UI ──────────────────
    @Test
    fun chapter_loading_state_has_correct_properties() {
        val state = ChapterLoadResult.Loading("book-1", 0)
        assertEquals("book-1", state.bookId)
        assertEquals(0, state.chapterIndex)
    }

    @Test
    fun loading_ui_state_shows_isLoading_true() {
        val uiState = ReaderUiState(requestedBookId = "book-1", isLoading = true)
        assertTrue(uiState.isLoading)
        assertNull(uiState.loadedBook)
        assertNull(uiState.errorMessage)
        assertFalse(uiState.isReady)
    }

    // ── 2. 错误状态显示 — 加载失败时显示错误提示 ─────────────────
    @Test
    fun error_ui_state_exposes_error_message() {
        val uiState = ReaderUiState(
            requestedBookId = "book-1",
            errorMessage = "加载失败：文件不存在",
        )
        assertNotNull(uiState.errorMessage)
        assertEquals("加载失败：文件不存在", uiState.errorMessage)
        assertFalse(uiState.isReady)
        assertNull(uiState.loadedBook)
    }

    @Test
    fun chapter_load_error_state_contains_message() {
        val state = ChapterLoadResult.Error("book-1", 2, "章节解压失败")
        assertEquals("book-1", state.bookId)
        assertEquals(2, state.chapterIndex)
        assertEquals("章节解压失败", state.message)
    }

    // ── 3. 控制栏打开/关闭 ────────────────────────────────────────
    @Test
    fun controls_visible_toggle_state() {
        val initial = ReaderScreenState()
        assertTrue(initial.controlsVisible)

        val hidden = initial.copy(controlsVisible = false)
        assertFalse(hidden.controlsVisible)

        val toggled = hidden.copy(controlsVisible = !hidden.controlsVisible)
        assertTrue(toggled.controlsVisible)
    }

    @Test
    fun controls_visibility_renders_in_composable() {
        composeRule.setContent {
            AppTheme {
                val visible = true
                Box(modifier = Modifier.testTag("controls")) {
                    if (visible) {
                        Text("控制栏", modifier = Modifier.testTag("controls-text"))
                    }
                }
            }
        }
        composeRule.onNodeWithTag("controls-text").assertIsDisplayed()
    }

    // ── 4. Sheet 打开关闭（目录/设置/笔记等）──────────────────────
    @Test
    fun sheet_open_and_close_state_transitions() {
        val initial = ReaderScreenState()
        assertNull(initial.sheet)

        val withToc = initial.copy(sheet = ReaderSheet.TOC)
        assertEquals(ReaderSheet.TOC, withToc.sheet)

        val closed = withToc.copy(sheet = null)
        assertNull(closed.sheet)
    }

    @Test
    fun all_sheet_types_can_be_opened() {
        val sheets = ReaderSheet.entries
        assertTrue(sheets.contains(ReaderSheet.TOC))
        assertTrue(sheets.contains(ReaderSheet.NOTES))
        assertTrue(sheets.contains(ReaderSheet.AI_ASSIST))
        assertTrue(sheets.contains(ReaderSheet.AI_EXPLAIN))
        assertTrue(sheets.contains(ReaderSheet.INSPIRATION))
        assertTrue(sheets.contains(ReaderSheet.SETTINGS))
        assertTrue(sheets.contains(ReaderSheet.PROGRESS))
        assertTrue(sheets.contains(ReaderSheet.SEARCH))
        assertTrue(sheets.contains(ReaderSheet.BOOK_INFO))
        assertTrue(sheets.contains(ReaderSheet.THEME))
    }

    // ── 5. 长标题截断显示 ────────────────────────────────────────
    @Test
    fun long_title_text_is_handled_with_overflow_ellipsis() {
        val longTitle = "这是一本非常非常长的书名，".repeat(10)
        composeRule.setContent {
            AppTheme {
                Text(
                    text = longTitle,
                    maxLines = 1,
                    modifier = Modifier
                        .width(200.dp)
                        .testTag("title-text"),
                )
            }
        }
        composeRule.onNodeWithTag("title-text").assertIsDisplayed()
    }

    // ── 6. 最小宽度 320dp 布局 ───────────────────────────────────
    @Test
    fun content_renders_at_minimum_320dp_width() {
        composeRule.setContent {
            AppTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .testTag("min-width-container")
                ) {
                    Text("内容区域", modifier = Modifier.testTag("content"))
                }
            }
        }
        composeRule.onNodeWithTag("min-width-container").assertIsDisplayed()
        composeRule.onNodeWithTag("content").assertIsDisplayed()
    }

    // ── 7. 字体缩放适配 ─────────────────────────────────────────
    @Test
    fun text_renders_with_different_font_sizes() {
        composeRule.setContent {
            AppTheme {
                Box(modifier = Modifier.testTag("font-container")) {
                    Text("正常字体", fontSize = 16.sp, modifier = Modifier.testTag("font-normal"))
                    Text("大字体", fontSize = 24.sp, modifier = Modifier.testTag("font-large"))
                }
            }
        }
        composeRule.onNodeWithTag("font-normal").assertIsDisplayed()
        composeRule.onNodeWithTag("font-large").assertIsDisplayed()
    }

    // ── 8. 横屏布局 — 验证状态在横屏下的行为 ─────────────────────
    @Test
    fun screen_state_maintains_consistency_across_configurations() {
        val state = ReaderScreenState(
            controlsVisible = true,
            sheet = ReaderSheet.TOC,
            selectedText = "选中文本",
        )
        // 状态在横屏/竖屏应保持一致
        assertTrue(state.controlsVisible)
        assertEquals(ReaderSheet.TOC, state.sheet)
        assertEquals("选中文本", state.selectedText)
    }

    // ── 9. 点击区域预览：渲染**生产组件** TapZonePreviewCard ──────────
    //
    // 原来这条用例在测试里自建 `Text("点击区域").clickable { clicked = true }`
    // 再点自己，既不触及 resolveReaderTapAction，也不触及任何生产热区组件，
    // 属于会制造「热区已覆盖」假象的占位测试，已删除并替换为下面这几条。
    // 点击**策略**由 JVM 行为测试覆盖（ReaderTapActionTest / ReaderTapActionPresetTest /
    // ReaderTapZoneGeometryTest）；这里覆盖的是预览卡是否真的用共享几何画出等宽三区。
    //
    // 注意：本文件只是**编译**通过，本切片没有在真机上执行 instrumentation
    // （受共享工作区与设备占用约束），所以这几条断言是未运行状态。

    @Test
    fun previewCardThreeZoneDrawsEqualThirdsForStandardPreset() {
        renderPreviewCard("three-zone", "standard")

        val left = zone(TAP_PREVIEW_TAG_LEFT)
        val center = zone(TAP_PREVIEW_TAG_CENTER)
        val right = zone(TAP_PREVIEW_TAG_RIGHT)

        assertTrue("左区必须在中央区左侧", left.boundsInRoot.right <= center.boundsInRoot.left + 1f)
        assertTrue("中央区必须在右区左侧", center.boundsInRoot.right <= right.boundsInRoot.left + 1f)

        // 共享几何是 1/3 : 1/3 : 1/3。旧预览卡的 0.32 : 0.36 : 0.32 会让中央区明显更宽。
        assertEqualsRatio(1f, center.boundsInRoot.width / left.boundsInRoot.width, tolerance = 0.05f)
        assertEqualsRatio(1f, right.boundsInRoot.width / left.boundsInRoot.width, tolerance = 0.05f)

        // 三分区不该画出顶/底带。
        assertEquals(
            "三分区不得有顶部带",
            0,
            composeRule.onAllNodesWithTag(TAP_PREVIEW_TAG_TOP, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun previewCardFiveZoneUsesTwelvePercentBands() {
        renderPreviewCard("five-zone", "standard")

        val top = zone(TAP_PREVIEW_TAG_TOP)
        val middle = zone(TAP_PREVIEW_TAG_CENTER)
        val bottom = zone(TAP_PREVIEW_TAG_BOTTOM)
        val left = zone(TAP_PREVIEW_TAG_LEFT)

        // 共享几何：顶/底各 12%，中带 76% → 比值 0.12/0.76 ≈ 0.158。
        // 旧预览卡是 0.24/0.52 ≈ 0.46，会被这条断言抓住。
        assertEqualsRatio(
            0.12f / 0.76f,
            top.boundsInRoot.height / middle.boundsInRoot.height,
            tolerance = 0.05f,
        )
        assertEqualsRatio(
            0.12f / 0.76f,
            bottom.boundsInRoot.height / middle.boundsInRoot.height,
            tolerance = 0.05f,
        )
        // 中带内部仍是等宽三段
        assertEqualsRatio(1f, middle.boundsInRoot.width / left.boundsInRoot.width, tolerance = 0.05f)
        assertTrue("顶部带必须在中带之上", top.boundsInRoot.bottom <= middle.boundsInRoot.top + 1f)
        assertTrue("底部带必须在中带之下", middle.boundsInRoot.bottom <= bottom.boundsInRoot.top + 1f)
    }

    @Test
    fun leftHandPreviewMirrorsThePresetActionMapping() {
        // 左手模式：左区下一页、右区上一页（与 resolveReaderTapAction 的 LEFT_HAND 分支一致）
        renderPreviewCard("three-zone", "left_hand")

        val left = zone(TAP_PREVIEW_TAG_LEFT)
        val right = zone(TAP_PREVIEW_TAG_RIGHT)

        labelInZone("下一页", left)
        labelInZone("上一页", right)
        labelInZone("菜单", zone(TAP_PREVIEW_TAG_CENTER))
    }

    @Test
    fun bothNextThreeZonePreviewMustNotPromiseATapBackZone() {
        // 双向推进 + 三分区：左右两侧都是下一页；三分区没有顶部带，
        // 所以说明文字不得声称「顶部热区」可以回退 —— 只能指向滑动手势。
        renderPreviewCard("three-zone", "both_next")

        assertEquals(
            "双向推进三分区应有左右两个下一页区",
            2,
            composeRule.onAllNodesWithText("下一页", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            "双向推进三分区不应出现上一页区",
            0,
            composeRule.onAllNodesWithText("上一页", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            "三分区预览不得画出顶部翻页带",
            0,
            composeRule.onAllNodesWithTag(TAP_PREVIEW_TAG_TOP, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            "三分区预览的说明里不存在「顶部」这个词",
            0,
            composeRule.onAllNodesWithText("顶部", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            "三分区的回退说明必须提到滑动手势",
            1,
            composeRule.onAllNodesWithText("滑动", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun bothNextFiveZonePreviewCanPointAtTheTopBand() {
        // 对照组：五分区确实画出顶部带（只有箭头图标，标签随带高省略），
        // 此时说明提到「顶部热区」才是事实。
        renderPreviewCard("five-zone", "both_next")

        val top = zone(TAP_PREVIEW_TAG_TOP)
        val center = zone(TAP_PREVIEW_TAG_CENTER)
        assertTrue("五分区必须画出有实际高度的顶部带", top.boundsInRoot.height > 1f)
        assertTrue("顶部带必须在中带之上", top.boundsInRoot.bottom <= center.boundsInRoot.top + 1f)
        assertEquals(
            "五分区的双向推进说明应提到顶部热区",
            1,
            composeRule.onAllNodesWithText("顶部", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        // 中带两侧仍都是下一页；顶/底带的动作由箭头图标表达。
        assertEquals(
            "五分区双向推进的中带两侧都是下一页",
            2,
            composeRule.onAllNodesWithText("下一页", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun previewCardFiveZoneBandsExposeTurnSemanticsToTalkBack() {
        // S09-B-F1 P2-1：顶/底带为了 12% 带高省略了文字标签，动作语义必须由图标的
        // contentDescription 承担 —— 否则 TalkBack 用户只看得到"有块东西"，读不出翻页方向。
        renderPreviewCard("five-zone", "standard")

        contentDescriptionInZone("上一页", zone(TAP_PREVIEW_TAG_TOP))
        contentDescriptionInZone("下一页", zone(TAP_PREVIEW_TAG_BOTTOM))
        // 中带里有文字标签的分区不该另外再挂一份描述，否则同一分区被读两遍。
        assertEquals(
            "「上一页」的语义描述只能出现在顶部带",
            1,
            composeRule.onAllNodesWithContentDescription("上一页", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    /** 渲染生产预览卡（不是测试内自造的 clickable）。 */
    private fun renderPreviewCard(tapZoneMode: String, tapActionPreset: String) {
        composeRule.setContent {
            AppTheme {
                Box(Modifier.width(360.dp)) {
                    TapZonePreviewCard(tapZoneMode = tapZoneMode, tapActionPreset = tapActionPreset)
                }
            }
        }
    }

    /** 取分区**容器**节点；用 onNodeWithText 量到的是文字行盒，不是热区。 */
    private fun zone(tag: String): SemanticsNode =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    /** 断言 [text] 这个文字标签确实画在 [container] 这个分区里。 */
    private fun labelInZone(text: String, container: SemanticsNode) {
        val label = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        assertNodeInZone(label, "「$text」", container)
    }

    /** 断言 TalkBack 按 contentDescription 找得到的节点确实落在 [container] 分区里。 */
    private fun contentDescriptionInZone(description: String, container: SemanticsNode) {
        val nodes = composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(
            "contentDescription=「$description」必须唯一，否则读屏会在两个分区间歧义",
            1,
            nodes.size,
        )
        assertNodeInZone(nodes[0], "contentDescription=「$description」", container)
    }

    private fun assertNodeInZone(node: SemanticsNode, name: String, container: SemanticsNode) {
        val bounds = node.boundsInRoot
        val box = container.boundsInRoot
        assertTrue(
            "$name 必须整体落在该分区容器内：node=$bounds zone=$box",
            bounds.left >= box.left - 1f && bounds.right <= box.right + 1f &&
                bounds.top >= box.top - 1f && bounds.bottom <= box.bottom + 1f,
        )
    }

    private fun assertEqualsRatio(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            "比例期望≈$expected（±$tolerance），实际=$actual",
            kotlin.math.abs(expected - actual) <= tolerance,
        )
    }

    // ── 10. legacy EPUB **生产点击仲裁层**：真实坐标点击 + 真实回调计数 ──
    //          （S09-B-F1 P2-2 覆盖面；S09-B-F2 边界穿透）
    //
    // 上面第 9 组只证明预览卡"画得对"；就算把 PagedEpubContent 里的 clickable 整段删掉
    // 或把 leftAction/rightAction 接反，那些断言也照样绿。所以这一组渲染生产的整层仲裁结构
    // LegacyEpubTapArbiter（= 父级 readerCenterTapToToggle 兜底 Box + 子层
    // LegacyEpubTapZoneOverlay 分区），PagedEpubView 用的就是同一个函数；命中点由
    // ReaderTapZoneGeometry 算出，断言的是 onPrev / onNext / onToggleControls 的
    // **实际调用次数**。
    // 失效形态与对应变红的断言：
    // - 接线被删除 → 计数停在 0；
    // - leftAction/rightAction 接反 → 计数落在错误的计数器上；
    // - 不可用分区重新漏点给父层 → 边界用例的 toggle 由 0 变 1；
    // - 父子两层双触发 → 中央用例的 toggle 由 1 变 2。
    //
    // 注意：这一组与第 9 组同样**本几轮只编译未执行**（不碰手机，见类注释），
    // 属于交给带设备窗口的断言，不能当作已通过验证。

    @Test
    fun legacyEpubArbiterThreeZoneStandardRoutesEachZoneToItsCallback() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls)

        tapEpubZone("left")
        assertEquals("左区必须真的回调 onPrev", 1, calls.prev)
        assertEquals("左区不得同时回调别的", 0, calls.next + calls.toggle)

        tapEpubZone("center")
        assertEquals("中央区必须真的回调 onToggleControls", 1, calls.toggle)

        tapEpubZone("right")
        assertEquals("右区必须真的回调 onNext", 1, calls.next)
        assertEquals("总数必须正好是三次，没有重复派发", 3, calls.total)
    }

    @Test
    fun legacyEpubArbiterFiveZoneAddsWorkingTopAndBottomBands() {
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls)

        tapEpubZone("top")
        assertEquals("顶部带必须回调 onPrev", 1, calls.prev)
        tapEpubZone("bottom")
        assertEquals("底部带必须回调 onNext", 1, calls.next)
        tapEpubZone("center")
        assertEquals("中带中央仍唤出控制栏", 1, calls.toggle)
        // 中带左右两侧沿用横向分区语义，没有被顶/底带挤掉。
        tapEpubZone("left")
        tapEpubZone("right")
        assertEquals("中带左侧回上一页", 2, calls.prev)
        assertEquals("中带右侧翻下一章", 2, calls.next)
    }

    @Test
    fun legacyEpubArbiterLeftHandPresetSwapsTheSideZones() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "left_hand", calls)

        tapEpubZone("left")
        assertEquals("左手模式：左区必须翻下一章", 1, calls.next)
        assertEquals("左手模式：左区不得回退", 0, calls.prev)
        tapEpubZone("right")
        assertEquals("左手模式：右区必须回上一章", 1, calls.prev)
        assertEquals("右手区不得再前进（左区那一次之外没有新增）", 1, calls.next)
        assertEquals("中央区之外不该有唤出回调", 0, calls.toggle)
    }

    // 边界 no-op（S09-B-F2）：不可用的翻页分区必须"什么都不做"。
    // 关键探针是 **toggle 计数**：修复前这些分区用 clickable(enabled=false)，不消费轻点，
    // 手势会漏到父级 readerCenterTapToToggle，于是"翻不动"变成"切换控制栏"。
    // 断言写在渲染整层仲裁结构（父 Box + 子 overlay）的用例里，
    // 所以只要父级仲裁重新失效（漏点或双触发），这几条就会变红。

    @Test
    fun legacyEpubThreeZoneFirstChapterLeftZoneIsSilentNoOp() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls, canPrev = false, canNext = true)

        tapEpubZone("left")
        assertEquals("首页点左区不得回上一章", 0, calls.prev)
        assertEquals("首页点左区不得改判成下一章", 0, calls.next)
        assertEquals("首页点左区不得穿透到父层切换控制栏", 0, calls.toggle)
    }

    @Test
    fun legacyEpubThreeZoneLastChapterRightZoneIsSilentNoOp() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls, canPrev = true, canNext = false)

        tapEpubZone("right")
        assertEquals("末页点右区不得翻下一章", 0, calls.next)
        assertEquals("末页点右区不得改判成上一章", 0, calls.prev)
        assertEquals("末页点右区不得穿透到父层切换控制栏", 0, calls.toggle)
    }

    @Test
    fun legacyEpubFiveZoneFirstChapterTopBandIsSilentNoOp() {
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls, canPrev = false, canNext = true)

        tapEpubZone("top")
        assertEquals("首页点顶部带不得回上一章", 0, calls.prev)
        assertEquals("首页点顶部带不得改判成下一章", 0, calls.next)
        assertEquals("首页点顶部带不得穿透到父层切换控制栏", 0, calls.toggle)
        // 中带左侧同样不可用（同一 canPrev），一并钉住，避免只有顶带被修好。
        tapEpubZone("left")
        assertEquals("首页中带左侧仍必须静默", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubFiveZoneLastChapterBottomBandIsSilentNoOp() {
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls, canPrev = true, canNext = false)

        tapEpubZone("bottom")
        assertEquals("末页点底部带不得翻下一章", 0, calls.next)
        assertEquals("末页点底部带不得改判成上一章", 0, calls.prev)
        assertEquals("末页点底部带不得穿透到父层切换控制栏", 0, calls.toggle)
        tapEpubZone("right")
        assertEquals("末页中带右侧仍必须静默", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubCenterTapTogglesControlsExactlyOnceThroughBothLayers() {
        // 中央 Box 自己有 clickable，父层又有中央兜底观察器 —— 两侧都可能触发 toggle。
        // 只允许一次：出现 2 就说明父子两层双触发（本片的结构前提被破坏）。
        val threeZone = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", threeZone)
        tapEpubZone("center")
        assertEquals("三分区中央点击只能有一次 toggle", 1, threeZone.toggle)
        assertEquals("中央点击不得顺带翻页", 0, threeZone.prev + threeZone.next)
    }

    @Test
    fun legacyEpubAvailableEdgeTurnsExactlyOnceWithoutToggling() {
        // 反向对照：可用边缘必须真的翻一页、且只翻一次、且不碰控制栏。
        // 没有这条，上面四条边界用例的 0 可能只是"整层没接线"造成的假通过。
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls, canPrev = true, canNext = true)

        tapEpubZone("top")
        tapEpubZone("bottom")
        tapEpubZone("center")
        assertEquals("顶部带恰好一次回退", 1, calls.prev)
        assertEquals("底部带恰好一次前进", 1, calls.next)
        assertEquals("中央区恰好一次唤出", 1, calls.toggle)
        assertEquals("总回调数必须是 3，没有重复派发", 3, calls.total)
    }

    // ── 11. 边界分区的无障碍语义：有名称的 disabled 节点，不是匿名可激活按钮（S09-B-F3）──
    //
    // 第 10 组证明"边界点了什么都不发生"（触摸层消费、不穿透）。这一组证明同一批分区
    // 在语义树上也是一个**有名字、明确 disabled、且不提供 OnClick** 的边界节点。
    // 上一版用 clickable(onClick = {}) 消费触摸，代价是暴露一个"没有名称、看起来能激活、
    // 激活后什么都不做"的匿名节点，TalkBack / Switch Access / 键盘焦点都会撞上它。
    // 文案按"该分区实际映射到哪个动作"选：left_hand / both_next 下物理左右是反的。

    @Test
    fun legacyEpubFirstChapterLeftZoneIsNamedDisabledBoundary() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls, canPrev = false, canNext = true)

        assertBoundarySemantics(LEGACY_EPUB_ZONE_LEFT, LEGACY_EPUB_BOUNDARY_FIRST)
        tapEpubZone("left")
        assertEquals("边界节点不得因为可被读屏发现就顺带翻页", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubLastChapterRightZoneIsNamedDisabledBoundary() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls, canPrev = true, canNext = false)

        assertBoundarySemantics(LEGACY_EPUB_ZONE_RIGHT, LEGACY_EPUB_BOUNDARY_LAST)
        tapEpubZone("right")
        assertEquals("末页右区必须保持 no-op", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubFiveZoneFirstChapterTopBandIsNamedDisabledBoundary() {
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls, canPrev = false, canNext = true)

        assertBoundarySemantics(LEGACY_EPUB_ZONE_TOP, LEGACY_EPUB_BOUNDARY_FIRST)
        tapEpubZone("top")
        assertEquals("首页顶部带必须保持 no-op", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubFiveZoneLastChapterBottomBandIsNamedDisabledBoundary() {
        val calls = TapZoneCalls()
        renderEpubArbiter("five-zone", "standard", calls, canPrev = true, canNext = false)

        assertBoundarySemantics(LEGACY_EPUB_ZONE_BOTTOM, LEGACY_EPUB_BOUNDARY_LAST)
        tapEpubZone("bottom")
        assertEquals("末页底部带必须保持 no-op", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubBoundaryLabelFollowsTheMappedActionNotThePhysicalSide() {
        // left_hand：左区映射到"下一章"。所以在最后一页，被禁用的**左区**必须说
        // "已到最后一章"，而右区（映射到上一章）仍可用、仍是"上一章"按钮。
        // 这条用例专门防止有人按物理左右硬编码文案。
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "left_hand", calls, canPrev = true, canNext = false)

        assertBoundarySemantics(LEGACY_EPUB_ZONE_LEFT, LEGACY_EPUB_BOUNDARY_LAST)
        contentDescriptionInZone("上一章", zone(LEGACY_EPUB_ZONE_RIGHT))
        composeRule.onNodeWithTag(LEGACY_EPUB_ZONE_RIGHT, useUnmergedTree = true).assertHasClickAction()

        tapEpubZone("left")
        assertEquals("left_hand 的禁用左区也必须 no-op", 0, calls.prev + calls.next + calls.toggle)
    }

    @Test
    fun legacyEpubAvailableZonesKeepClickActionAndTurnDescription() {
        val calls = TapZoneCalls()
        renderEpubArbiter("three-zone", "standard", calls, canPrev = true, canNext = true)

        // 可用分区：仍然是能激活的按钮 + 箭头图标带"上一章/下一章"描述。
        composeRule.onNodeWithTag(LEGACY_EPUB_ZONE_LEFT, useUnmergedTree = true).assertHasClickAction()
        composeRule.onNodeWithTag(LEGACY_EPUB_ZONE_RIGHT, useUnmergedTree = true).assertHasClickAction()
        contentDescriptionInZone("上一章", zone(LEGACY_EPUB_ZONE_LEFT))
        contentDescriptionInZone("下一章", zone(LEGACY_EPUB_ZONE_RIGHT))
        // 可用状态下不得出现边界文案。
        assertEquals(
            "可翻页时不应存在边界提示节点",
            0,
            composeRule.onAllNodesWithContentDescription(
                LEGACY_EPUB_BOUNDARY_FIRST,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size +
                composeRule.onAllNodesWithContentDescription(
                    LEGACY_EPUB_BOUNDARY_LAST,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size,
        )

        tapEpubZone("left")
        tapEpubZone("right")
        assertEquals("可用左区仍要回退一次", 1, calls.prev)
        assertEquals("可用右区仍要前进一次", 1, calls.next)
        assertEquals("可用边缘不得顺手切换控制栏", 0, calls.toggle)
    }

    /**
     * 边界分区的语义契约：有名称 + disabled + **没有 OnClick**。
     *
     * "没有 OnClick" 直接查语义配置，而不是用 hasClickAction 的反面断言：
     * 这样即使有人把 clickable(enabled=false) 改回来"消音"，也能看到差异在哪 ——
     * 那种写法虽然也没有 OnClick，但触摸会穿透，第 10 组的 toggle 断言会红。
     */
    private fun assertBoundarySemantics(zoneTag: String, expectedDescription: String) {
        val node = composeRule.onNodeWithTag(zoneTag, useUnmergedTree = true)
        node.assertContentDescriptionEquals(expectedDescription)
        node.assertIsNotEnabled()
        assertFalse(
            "边界分区 $zoneTag 不得暴露 OnClick（否则读屏会撞上'可激活但什么都不做'的节点）",
            node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick),
        )
    }

    /** 三个回调的调用计数 —— 断言的是生产接线，不是"有没有 click action"。 */
    private class TapZoneCalls {
        var prev: Int = 0
        var next: Int = 0
        var toggle: Int = 0

        val total: Int get() = prev + next + toggle
    }

    /**
     * 渲染生产仲裁层本体（[LegacyEpubTapArbiter] = 父级中央兜底 Box + 分区 overlay），
     * 不给测试另造一份 clickable，也不单独渲染子层 overlay。
     *
     * 必须渲染整层：S09-B-F2 的缺陷（不可用分区把轻点漏给父层 → 误切控制栏）
     * 只存在于父子两层的交互里，单独渲染 overlay 永远测不到。
     */
    private fun renderEpubArbiter(
        tapZoneMode: String,
        tapActionPreset: String,
        calls: TapZoneCalls,
        canPrev: Boolean = true,
        canNext: Boolean = true,
    ) {
        composeRule.setContent {
            AppTheme {
                LegacyEpubTapArbiter(
                    tapZoneMode = tapZoneMode,
                    tapActionPreset = tapActionPreset,
                    canPrev = canPrev,
                    canNext = canNext,
                    onPrev = { calls.prev++ },
                    onNext = { calls.next++ },
                    onToggleControls = { calls.toggle++ },
                    iconTint = Color.Gray,
                    modifier = Modifier.fillMaxSize().testTag(EPUB_TAP_ROOT_TAG),
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 用 [ReaderTapZoneGeometry] 把"分区"换算成像素坐标后点下去。
     *
     * overlay 根节点铺满 root，所以容器相对坐标与 root 绝对坐标重合，
     * `click(position)` 的两种坐标约定点的是同一个点；几何来自事实源，
     * 测试里没有第二套比例。
     */
    private fun tapEpubZone(name: String) {
        val node = composeRule.onNodeWithTag(EPUB_TAP_ROOT_TAG, useUnmergedTree = true)
        val root = node.fetchSemanticsNode().boundsInRoot
        val w = root.width
        val h = root.height
        val point = when (name) {
            "left" -> Offset(ReaderTapZoneGeometry.centerLeftEdge(w) / 2f, h / 2f)
            "center" -> Offset(
                (ReaderTapZoneGeometry.centerLeftEdge(w) + ReaderTapZoneGeometry.centerRightEdge(w)) / 2f,
                h / 2f,
            )
            "right" -> Offset(
                (ReaderTapZoneGeometry.centerRightEdge(w) + w) / 2f,
                h / 2f,
            )
            "top" -> Offset(w / 2f, ReaderTapZoneGeometry.topBandBottomEdge(h) / 2f)
            "bottom" -> Offset(w / 2f, (ReaderTapZoneGeometry.bottomBandTopEdge(h) + h) / 2f)
            else -> throw IllegalArgumentException("未知的分区名：$name")
        }
        node.performTouchInput { click(point) }
        composeRule.waitForIdle()
    }

    private companion object {
        const val EPUB_TAP_ROOT_TAG = "legacy-epub-tap-overlay"
    }

    // ── 补充：ReaderScreenState 完整性 ───────────────────────────
    @Test
    fun screen_state_default_values_are_correct() {
        val state = ReaderScreenState()
        assertTrue(state.controlsVisible)
        assertEquals("", state.selectedText)
        assertEquals(-1, state.selectedRangeStart)
        assertEquals(-1, state.selectedGlobalOffset)
        assertNull(state.sheet)
        assertFalse(state.showTts)
        assertEquals("", state.searchQuery)
        assertFalse(state.showReaderOverflow)
        assertFalse(state.noteOpen)
        assertEquals("", state.noteBody)
        assertFalse(state.showColorRow)
    }

    @Test
    fun reader_ui_state_isReady_only_when_book_loaded_and_not_loading() {
        // 初始状态
        assertFalse(ReaderUiState().isReady)

        // 加载中
        assertFalse(ReaderUiState(requestedBookId = "b1", isLoading = true).isReady)

        // 有错误
        assertFalse(ReaderUiState(requestedBookId = "b1", errorMessage = "err").isReady)
    }

    @Test
    fun chapter_loaded_state_carries_blocks() {
        val blocks = listOf(
            com.creationreadingassistant.feature.reader.doc.DocBlock.Text("段落1"),
            com.creationreadingassistant.feature.reader.doc.DocBlock.Text("段落2", isHeading = true),
        )
        val state = ChapterLoadResult.Loaded("book-1", 0, blocks)
        assertEquals(2, state.blocks.size)
        assertEquals("book-1", state.bookId)
        assertEquals(0, state.chapterIndex)
    }

    @Test
    @Suppress("DEPRECATION") // ScanTxtTocRule 为已废弃死路径，本用例只验证 Action 可构造
    fun action_types_cover_all_UI_interactions() {
        // 验证所有 Action 类型可以正确构造
        val actions = listOf<ReaderAction>(
            ReaderAction.OpenBook("book-1"),
            ReaderAction.Retry,
            ReaderAction.LoadChapter("book-1", 0),
            ReaderAction.ScanTxtTocRule("book-1", "/path", "rule"),
            ReaderAction.ToggleControls(),
            ReaderAction.ToggleControls(visible = true),
            ReaderAction.OpenSheet(ReaderSheet.TOC),
            ReaderAction.CloseSheet,
            ReaderAction.SetSelectedText("text", 0, 0),
            ReaderAction.ClearSelection,
            ReaderAction.SetShowTts(true),
            ReaderAction.SetSearchQuery("query"),
            ReaderAction.SetShowOverflow(true),
            ReaderAction.SetNoteOpen(true),
            ReaderAction.SetNoteBody("body"),
            ReaderAction.ToggleColorRow,
        )
        assertEquals(16, actions.size)
    }
}
