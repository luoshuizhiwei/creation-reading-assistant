package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSettingsSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allSettingsGroupsOpenAndReturnToRoot() {
        renderSettings()

        val groups = listOf(
            "排版与版式" to "间距与版面",
            "翻页与按键" to "自动翻页与按键",
            "显示与页眉页脚" to "屏幕显示",
            "护眼与阅读提醒" to "护眼滤镜",
            "排版引擎兼容" to "分页兼容模式",
        )
        groups.forEach { (entry, pageContent) ->
            scrollToText(entry)
            composeRule.onNodeWithText(entry).performClick()
            // 页面内容区块可能在折叠线下（排版页首屏是字号预览），先滚到位再断言
            scrollToText(pageContent)
            composeRule.onNodeWithText(pageContent).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("返回")
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            composeRule.onNodeWithText("快捷排版").assertIsDisplayed()
        }
    }

    @Test
    fun dependentOptionsOnlyAppearWhenTheirParentIsEnabled() {
        renderSettings(
            initial = ReaderSettings(
                volumeKeyPaging = false,
                showReaderInfo = false,
                eyeCareFilterEnabled = false,
                eyeCareScheduleEnabled = false,
                readingRhythmReminderEnabled = false,
            ),
        )

        scrollToText("翻页与按键")
        composeRule.onNodeWithText("翻页与按键").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("朗读时音量键仍翻页").assertCountEquals(0)
        scrollToText("音量键翻页")
        composeRule.onNodeWithText("音量键翻页").performClick()
        composeRule.waitForIdle()
        scrollToText("朗读时音量键仍翻页")
        composeRule.onNodeWithText("朗读时音量键仍翻页").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        scrollToText("显示与页眉页脚")
        composeRule.onNodeWithText("显示与页眉页脚").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("页眉左侧").assertCountEquals(0)
        scrollToText("显示安静阅读信息")
        composeRule.onNodeWithText("显示安静阅读信息").performClick()
        composeRule.waitForIdle()
        scrollToText("页眉左侧")
        composeRule.onNodeWithText("页眉左侧").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        scrollToText("护眼与阅读提醒")
        composeRule.onNodeWithText("护眼与阅读提醒").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("色温").assertCountEquals(0)
        scrollToText("开启护眼滤镜")
        composeRule.onNodeWithText("开启护眼滤镜").performClick()
        composeRule.waitForIdle()
        scrollToText("色温")
        composeRule.onNodeWithText("色温").assertIsDisplayed()
    }

    @Test
    fun closingAndReopeningSettingsReturnsToRoot() {
        val visible = mutableStateOf(true)
        renderSettings(visible = visible)

        scrollToText("排版引擎兼容")
        composeRule.onNodeWithText("排版引擎兼容").performClick()
        composeRule.onNodeWithText("分页兼容模式").assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.onAllNodesWithText("分页兼容模式").assertCountEquals(0)
        composeRule.runOnIdle { visible.value = true }

        composeRule.onNodeWithText("快捷排版").assertIsDisplayed()
        composeRule.onAllNodesWithText("分页兼容模式").assertCountEquals(0)
    }

    @Test
    fun autoHideShowsExactCurrentValueIncludingDefaultFour() {
        // 默认 4 秒必须明确显示且可选（用户反馈 2），0 = 不定时隐藏。
        renderSettings(initial = ReaderSettings(autoHideSeconds = 4))

        scrollToText("显示与页眉页脚")
        composeRule.onNodeWithText("显示与页眉页脚").performClick()

        listOf("不隐藏", "3 秒", "4 秒", "5 秒", "8 秒").forEach { option ->
            scrollToText(option)
            composeRule.onNodeWithText(option).assertIsDisplayed()
        }
        composeRule.onNodeWithText("4 秒").performClick()
    }

    @Test
    fun nightSettingsRemainUsableAt320DpWithLargeFont() {
        renderSettings(
            width = 320.dp,
            height = 640.dp,
            fontScale = 1.3f,
            darkTheme = true,
            initial = ReaderSettings(background = "night"),
        )

        val rootBounds = composeRule.onNodeWithTag("settings-root").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("阅读设置").assertIsDisplayed()
        // 大字号下快捷排版可能在折叠线下方，先惰性滚动到位再断言
        scrollToText("快捷排版")
        composeRule.onNodeWithText("快捷排版").assertIsDisplayed()
        scrollToText("排版引擎兼容")
        composeRule.onNodeWithText("排版引擎兼容").assertIsDisplayed()
        val advancedBounds = composeRule.onNodeWithText("排版引擎兼容").fetchSemanticsNode().boundsInRoot
        assertTrue(advancedBounds.left >= rootBounds.left)
        assertTrue(advancedBounds.right <= rootBounds.right)
    }

    @Test
    fun revealIsSelectableFromBothReaderSettingsEntryPoints() {
        var observed: ReaderSettings? = null
        renderSettings(onSettingsChangeObserved = { observed = it })

        scrollToText("揭示")
        composeRule.onNodeWithText("揭示").performClick()
        composeRule.runOnIdle { assertEquals("reveal", observed?.pageTurnEffect) }

        scrollToText("翻页与按键")
        composeRule.onNodeWithText("翻页与按键").performClick()
        scrollToText("揭示")
        composeRule.onNodeWithText("揭示").performClick()
        composeRule.runOnIdle { assertEquals("reveal", observed?.pageTurnEffect) }
    }

    /**
     * 惰性容器感知的滚动：LazyColumn 折叠屏外的 item 尚未组合，直接
     * onNodeWithText(x).performScrollTo() 会因节点不存在而失败；
     * 必须对容器执行 performScrollToNode 让其边滚边组合。
     */
    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun renderSettings(
        width: Dp = 393.dp,
        height: Dp = 780.dp,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        initial: ReaderSettings = ReaderSettings(),
        visible: MutableState<Boolean> = mutableStateOf(true),
        scope: ReaderSettingsScope = ReaderSettingsScope.BOOK,
        onSettingsChangeObserved: (ReaderSettings) -> Unit = {},
    ) {
        composeRule.setContent {
            var settings by remember { mutableStateOf(initial) }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                AppTheme(darkTheme = darkTheme) {
                    Box(
                        Modifier
                            .width(width)
                            .height(height)
                            .testTag("settings-root"),
                    ) {
                        if (visible.value) {
                            SettingsSheet(
                                paper = paperPalette(settings.background, darkTheme),
                                settings = settings,
                                onSettingsChange = {
                                    settings = it
                                    onSettingsChangeObserved(it)
                                },
                                onBookInfo = {},
                                scope = scope,
                            )
                        }
                    }
                }
            }
        }
    }

    // ===================== D-2-B：页级作用域说明 =====================

    /**
     * 等待 `AnimatedContent` 的页面切换动画走完（淡入 160ms / 淡出 120ms）。
     *
     * 为什么必须等：过渡窗口内**新旧两个 target 的内容同时存在**，
     * 此时单节点断言（`onNodeWithTag`）会报 "Expected exactly 1 node but found 2"，
     * 计数断言（`assertCountEquals(0)`）会读到尚未 dispose 的旧节点 —— 假红。
     *
     * 推进 Compose 主时钟而不是 `Thread.sleep`：时钟推进是确定性的，
     * 不受机器负载影响；250ms > 160ms，留出余量覆盖两个方向的过渡。
     * 等完之后断言仍是**严格**的唯一节点 / 零节点，不放宽任何产品断言。
     */
    private fun settlePageAnimation() {
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()
    }

    /** 从根页进入指定分组（分组标题即入口文案），并等待切换动画结束。 */
    private fun openGroup(entry: String) {
        scrollToText(entry)
        composeRule.onNodeWithText(entry).performClick()
        settlePageAnimation()
    }

    /** 点击返回并等待切回根页的动画结束。 */
    private fun goBackToRoot() {
        composeRule.onNodeWithContentDescription("返回").performClick()
        settlePageAnimation()
    }

    @Test
    fun rootPageShowsNoScopeNoticeButMarksPageTurnSpeedAsShared() {
        renderSettings()

        // 根页已有「只改本书 / 改全局默认」胶囊，不再叠一条页级说明。
        composeRule.onAllNodesWithTag(SCOPE_NOTICE_TAG).assertCountEquals(0)
        // 但翻页速度是全局字段，必须显式标注「所有书籍共用」（不能因根页有胶囊就省略）。
        scrollToText("翻页速度")
        composeRule.onNodeWithTag(PAGE_TURN_SPEED_NOTE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("全局设置，所有书籍共用").assertIsDisplayed()
    }

    @Test
    fun bookScopeNoticeAppearsOnEverySubPageWithTheExpectedCopy() {
        renderSettings()

        val expected = listOf(
            "翻页与按键" to "除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效",
            "显示与页眉页脚" to "除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效",
            "护眼与阅读提醒" to "本页均为全局设置，对所有书籍生效",
            "排版与版式" to "本页设置只对当前书籍生效",
            "排版引擎兼容" to "本页设置只对当前书籍生效",
        )

        expected.forEach { (entry, copy) ->
            openGroup(entry)
            // 进页第一屏即见，无需滚动。
            composeRule.onNodeWithTag(SCOPE_NOTICE_TAG).assertIsDisplayed()
            composeRule.onNodeWithText(copy).assertIsDisplayed()
            goBackToRoot()
            composeRule.onNodeWithText("快捷排版").assertIsDisplayed()
        }
    }

    @Test
    fun advancedCompatibilityNoticeNeverClaimsGlobalScope() {
        renderSettings()

        openGroup("排版引擎兼容")

        composeRule.onNodeWithTag(SCOPE_NOTICE_TAG).assertIsDisplayed()
        // D-2-A 的两个引擎键是真·本书覆盖：这一页绝不能出现「全局」方向的说法。
        composeRule.onNodeWithText("本页设置只对当前书籍生效").assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效",
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText("本页均为全局设置，对所有书籍生效").assertCountEquals(0)
    }

    @Test
    fun globalScopeNoticeUsesTheCorrectedCopyOnSubPages() {
        renderSettings(scope = ReaderSettingsScope.GLOBAL)

        // 根页在 GLOBAL 下同样不显示页级说明。
        composeRule.onAllNodesWithTag(SCOPE_NOTICE_TAG).assertCountEquals(0)

        openGroup("翻页与按键")

        composeRule.onNodeWithTag(SCOPE_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            "本页修改将保存为全局默认；其他书已有的单书设置不会被清除",
        ).assertIsDisplayed()
        // 修正版不得再出现旧的不准确承诺。
        composeRule.onAllNodesWithText("本页设置会写入全局默认，影响所有书").assertCountEquals(0)
    }

    @Test
    fun returningToRootRemovesTheScopeNotice() {
        renderSettings()

        openGroup("护眼与阅读提醒")
        composeRule.onNodeWithTag(SCOPE_NOTICE_TAG).assertIsDisplayed()

        goBackToRoot()

        composeRule.onNodeWithText("快捷排版").assertIsDisplayed()
        // 动画结束后旧 target 已 dispose：这里必须是**零节点**，不是"至少一个"。
        composeRule.onAllNodesWithTag(SCOPE_NOTICE_TAG).assertCountEquals(0)
    }

    @Test
    fun scopeNoticeRemainsVisibleAt320DpWithLargeFont() {
        // 沿用既有 nightSettingsRemainUsableAt320DpWithLargeFont 的密度压力配置：
        // 长句必须完整可读（允许换行），不得被单行省略截断。
        renderSettings(width = 320.dp, height = 640.dp, fontScale = 1.3f, darkTheme = true)

        openGroup("显示与页眉页脚")

        composeRule.onNodeWithTag(SCOPE_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            "除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效",
        ).assertIsDisplayed()
    }

    private companion object {
        const val SCOPE_NOTICE_TAG = "reader-settings-scope-notice"
        const val PAGE_TURN_SPEED_NOTE_TAG = "reader-page-turn-speed-global-note"
    }
}
