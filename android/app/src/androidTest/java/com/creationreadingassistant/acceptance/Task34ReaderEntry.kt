package com.creationreadingassistant.acceptance

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.creationreadingassistant.MainActivity

/**
 * CRA-ABC-DEVICE-CONTRACT-PREP-v1 / 任务34-v1 —— 任务专属「进入真实阅读入口」辅助。
 *
 * 只做导航，不伪造任何阅读状态：等真实书架/首页出现该书**可见标题** → 真实点击 → 等待进入 reader。
 * 与既有 `ReaderRegressionAcceptTest.openBookAAndRequireReaderIdentity` 同源思路，
 * 供 Task34 测试类共用，**不改动既有公共 fixture**。
 */
object Task34ReaderEntry {

    /** 点击书架/首页上的书籍可见标题，进入生产阅读器。 */
    fun openReader(
        composeRule: ComposeTestRule,
        visibleTitle: String,
        timeoutMillis: Long = 20_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(visibleTitle, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(visibleTitle, substring = true)[0].performClick()
        composeRule.waitForIdle()
    }

    /** 打开生产阅读器底栏「目录」，点击指定章节标签。 */
    fun openTocAndSelectChapter(
        composeRule: ComposeTestRule,
        chapterLabel: String,
        timeoutMillis: Long = 20_000,
    ) {
        // 阅读器底栏动作标签为「目录」（ReaderChrome.ReaderFooterAction）
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText("目录", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("目录", substring = true)[0].performClick()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(chapterLabel, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(chapterLabel, substring = true)[0].performClick()
        composeRule.waitForIdle()
    }

    /**
     * 经**真实生产入口**进入临时查阅模式：底部「我的」→「阅读笔记」→ 点击带 source 坐标的条目。
     *
     * 该条目由测试预置（NoteEntity.locator_json 携带 source 坐标），点击后由生产
     * `readerTemporaryRouteForSource` 构造 `navigationMode=temporary` 路由进入阅读器。
     */
    fun leaveReaderToTopLevel(
        composeRule: ComposeTestRule,
        scenario: ActivityScenario<MainActivity>,
        visibleTopLevelTitle: String,
        timeoutMillis: Long = 20_000,
    ) {
        val directory = composeRule.onAllNodesWithText("目录", substring = true)
        composeRule.waitUntil(timeoutMillis) {
            directory.fetchSemanticsNodes().isNotEmpty()
        }
        directory[0].assertIsDisplayed()

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(visibleTopLevelTitle, substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("目录", substring = true)
                    .fetchSemanticsNodes().isEmpty()
        }
        if (composeRule.onAllNodesWithText(visibleTopLevelTitle, substring = true)
                .fetchSemanticsNodes().isEmpty()
        ) {
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(visibleTopLevelTitle, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(visibleTopLevelTitle, substring = false)[0].assertIsDisplayed()
    }

    /** Enter through the dedicated action pill, whose production label is「临时查看来源」. */
    fun openTemporaryInspection(
        composeRule: ComposeTestRule,
        noteTitle: String,
        temporaryInspectAction: String = "临时查看来源",
        timeoutMillis: Long = 20_000,
    ) {
        // 底部导航「我的」
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText("我的", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("我的", substring = true)[0].performClick()
        composeRule.waitForIdle()
        // 「阅读笔记」入口
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText("阅读笔记", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("阅读笔记", substring = true)[0].performClick()
        composeRule.waitForIdle()
        // Search by this random task note title so the exact card and its action are isolated.
        composeRule.onNodeWithText("搜索标题 / 摘录 / 批注").performClick()
        composeRule.onNodeWithText("搜索标题 / 摘录 / 批注").performTextInput(noteTitle)
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(noteTitle).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(noteTitle).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(temporaryInspectAction).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(temporaryInspectAction).assertIsDisplayed()
        // The exact task book and note each matched once; click the sole dedicated temporary action.
        composeRule.onNodeWithText(temporaryInspectAction).performClick()
        composeRule.waitForIdle()
    }

    fun returnFromTemporaryInspection(composeRule: ComposeTestRule, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription("返回阅读处", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("返回阅读处", substring = true)[0].performClick()
        composeRule.waitForIdle()
    }
}
