package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import com.creationreadingassistant.ui.viewmodel.InspirationSaveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * READER-PAGES-v1 集中修正 P1：灵感页**保存期间全页编辑锁定**的组件行为验收
 * （androidTest 真实运行，非源码守卫）。
 *
 * 「可控延迟」实现：测试持有 `saveOutcome` 状态——保存点击后保持 null 即为在途
 * 窗口（不依赖任何 sleep/延迟注入），测试在任意时机经 runOnIdle 发布
 * Saved/Failed 驱动两条结果路径；保存回调记录 (entityId, body) 供断言。
 * 必须观察**实际组件状态与回调**：锁定=输入不改文本+控件 Disabled 语义；
 * 返回=确认弹窗不出现且页面保留；成功=三回调按序发生+清稿；失败=错误可见+
 * 草稿原样+重试复用同一 entityId。
 */
@OptIn(ExperimentalTestApi::class)
class ReaderInspirationPageSaveLockTest {
    @get:Rule val composeRule = createComposeRule()

    /** 测试驱动页面的 outcome 状态（null = 落库在途）。 */
    private val outcome = mutableStateOf<InspirationSaveOutcome?>(null)

    private val saveCalls = mutableListOf<Pair<String, String>>() // entityId to body
    private val notices = mutableListOf<String>()
    private var closeCalls = 0
    private var clearSelectionCalls = 0

    private fun setContent(bookTitle: String = "测试书名") {
        composeRule.setContent {
            AppTheme {
                ReaderPaperTheme(paperPalette("white", false)) {
                    InspirationPage(
                        bookId = "bid-1",
                        bookTitle = bookTitle,
                        chapterTitle = "第一章",
                        excerpt = "",
                        progressPercent = 42f,
                        categories = listOf(
                            com.creationreadingassistant.data.local.entity.CategoryEntity(
                                id = "c1", name = "示例分类", created_at = "", updated_at = "",
                            ),
                        ),
                        tags = emptyList(),
                        saveOutcome = outcome.value,
                        onCreateCategory = { it },
                        onCreateTag = { it },
                        onSave = { entityId, _, body, _, _ ->
                            saveCalls.add(entityId to body)
                        },
                        onClose = { closeCalls++ },
                        onClearSelection = { clearSelectionCalls++ },
                        onSaveNotice = { notices.add(it) },
                    )
                }
            }
        }
    }

    private fun tapSave() {
        composeRule.onNodeWithText("保存").performClick()
    }

    private fun publish(o: InspirationSaveOutcome) {
        composeRule.runOnIdle { outcome.value = o }
    }

    @Test
    fun saveInFlightLocksEditingBlocksBackAndUnlocksOnFailureWithSameId() {
        setContent()
        // 准备：输入正文（正常可编辑），展开分类/标签区。
        val bodyTag = "insp-lock-body"
        composeRule.onNodeWithTag(bodyTag).performTextInput("初稿内容")
        composeRule.onNodeWithText("分类 0 · 标签 2").performClick()
        composeRule.onNodeWithText("示例分类").assertIsDisplayed()

        // 提交：进入在途窗口（outcome 保持 null = 可控延迟）。
        tapSave()
        composeRule.onNodeWithText("保存中…").assertIsDisplayed()
        composeRule.onNodeWithText("保存中…").assertIsNotEnabled()

        // 1) 保存期间尝试继续编辑：注入按键不改变文本（readOnly 字段丢弃键入；
        // performTextInput 自带可编辑前置断言、在锁定字段上会直接抛
        // IsEditable=false，故这里用按键注入观察「输入无效」的行为本身，
        // 与第4步「失败解锁后可编辑」构成差分证明）。
        composeRule.onNodeWithTag(bodyTag).performClick()
        composeRule.onNodeWithTag(bodyTag).performKeyInput {
            keyDown(Key.Z)
            keyUp(Key.Z)
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("初稿内容").assertCountEquals(1)
        composeRule.onAllNodesWithText("初稿内容Z").assertCountEquals(0)

        // 2) 保存期间分类胶囊 Disabled 语义在位（不可改选）。
        composeRule.onNodeWithText("示例分类").assertIsNotEnabled()

        // 3) 保存期间可见返回：不开「放弃修改」、页面保留并出现「正在保存」状态。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onAllNodesWithText("有未保存的修改").assertCountEquals(0)
        composeRule.onNodeWithText("正在保存，请稍候…").assertIsDisplayed()
        composeRule.onNodeWithText("记灵感").assertIsDisplayed()

        // 4) 失败结果：解锁编辑、保留原稿、可见错误。
        val firstId = saveCalls.single().first
        publish(InspirationSaveOutcome.Failed(seq = 1, requestId = firstId, message = "boom"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("保存失败：boom").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(bodyTag).performTextInput("失败后可编辑")
        composeRule.onAllNodesWithText("初稿内容失败后可编辑").assertCountEquals(1)

        // 5) 重试：复用同一 entityId（防重复写入），且携带锁定期未被污染的原稿+失败后修改。
        tapSave()
        composeRule.waitUntil(timeoutMillis = 5_000) { saveCalls.size == 2 }
        assertEquals(saveCalls[0].first, saveCalls[1].first)
        assertEquals("初稿内容失败后可编辑", saveCalls[1].second)
        assertEquals(0, closeCalls)
    }

    @Test
    fun saveInFlightBlocksDoubleSubmit() {
        setContent()
        tapSave()
        composeRule.waitUntil(timeoutMillis = 5_000) { saveCalls.size == 1 }
        // 在途窗口内再次点击（按钮已禁用 + onClick 内部再查在途状态）。
        composeRule.onNodeWithText("保存中…").assertIsNotEnabled()
        composeRule.onNodeWithText("保存中…").performClick()
        composeRule.waitForIdle()
        assertEquals(1, saveCalls.size)
    }

    @Test
    fun savedOutcomeClearsDraftThenClosesWithCallbacks() {
        setContent()
        val bodyTag = "insp-lock-body"
        composeRule.onNodeWithTag(bodyTag).performTextInput("会话A内容")
        tapSave()
        composeRule.waitUntil(timeoutMillis = 5_000) { saveCalls.size == 1 }
        val id = saveCalls.single().first
        publish(InspirationSaveOutcome.Saved(seq = 1, requestId = id))
        composeRule.waitUntil(timeoutMillis = 5_000) { closeCalls == 1 }
        // 成功顺序：清草稿 → 清选区 → 提示 → 关页。
        assertEquals(1, clearSelectionCalls)
        assertEquals(listOf("已保存灵感，并记录来源阅读位置"), notices)
        // 清稿可观察：正文回到空（关闭只由本测试回调计数表达，页面未卸载）。
        composeRule.onAllNodesWithText("会话A内容").assertCountEquals(0)
    }
}
