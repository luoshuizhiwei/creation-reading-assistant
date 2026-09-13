package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.settings.SelectionActionGroup
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionToolbarModelTest {

    @Test
    fun `primary fallback constant holds the three frequent actions`() {
        // 「更多」不是可配置动作，而是工具条末尾固定追加的溢出入口——
        // 它不出现在常量里，也不能被用户隐藏。
        assertEquals(
            listOf("高亮", "浏览器", "复制"),
            selectionPrimaryActions.map { it.label },
        )
        assertFalse(selectionPrimaryActions.any { it.label == "添加批注" })
        assertFalse(selectionPrimaryActions.any { it.label == "更多" })
        assertFalse(selectionMoreActions.any { it.label == "更多" })
    }

    @Test
    fun `secondary actions live in more menu with explicit cancel wording`() {
        assertEquals(
            listOf("字典", "添加批注", "替换", "书内搜索", "AI 解读", "记为灵感", "取消选择"),
            selectionMoreActions.map { it.label },
        )
        assertFalse(selectionMoreActions.any { it.label == "清除" })
    }

    // ============================== R3-X1：配置驱动的工具条 ==============================

    @Test
    fun `every non fixed action can be promoted to the primary row`() {
        // 「搜索提供方切换」就是靠把「书内搜索」放进第一屏实现的，分组不该拦住它
        val settings = SelectionActionSettings(enabledPrimary = setOf("search"))

        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = true,
            aiConfigured = true,
        )

        assertEquals(listOf("search"), primary.map { it.id })
        assertFalse("已上第一屏的动作不该在溢出菜单里重复出现", more.any { it.id == "search" })
        assertTrue("取消选择永远在溢出菜单末尾", more.last().id == "cancel")
    }

    @Test
    fun `gating hides replace when the pager engine cannot apply rules`() {
        val settings = SelectionActionSettings(enabledMore = setOf("replace", "note"))

        val (_, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = false,
            aiConfigured = true,
        )

        assertFalse(more.any { it.id == "replace" })
        assertTrue(more.any { it.id == "note" })
    }

    @Test
    fun `gating hides ai when no key is configured`() {
        val settings = SelectionActionSettings(enabledMore = setOf("ai", "note"))

        val (_, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = true,
            aiConfigured = false,
        )

        assertFalse(more.any { it.id == "ai" })
        assertTrue(more.any { it.id == "note" })
    }

    @Test
    fun `primary row never renders empty even when every slot is gated out`() {
        // 用户只把「替换」+「AI 解读」放进第一屏，而两者此刻都不可用
        val settings = SelectionActionSettings(enabledPrimary = setOf("replace", "ai"))

        val (primary, _) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = false,
            aiConfigured = false,
        )

        assertEquals(listOf("highlight"), primary.map { it.id })
    }

    @Test
    fun `a broken config on disk still renders a usable toolbar`() {
        // 手改过的 DataStore：未知 id + 超出槽位上限
        val settings = SelectionActions.sanitize(
            SelectionActionSettings(enabledPrimary = setOf("highlight", "browser", "copy", "search", "ghost")),
        )

        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, SelectionActions.effectivePrimary(settings).size)

        val (primary, _) = selectionToolbarActions(settings, canCreateReplaceRule = true, aiConfigured = true)
        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, primary.size)
        assertFalse(primary.any { it.id == "ghost" })
    }

    @Test
    fun `pickable set excludes only the fixed group`() {
        assertEquals(
            SelectionActions.ALL.filter { it.group != SelectionActionGroup.FIXED }.map { it.id },
            SelectionActions.pickableDefs().map { it.id },
        )
        assertFalse(SelectionActions.pickableDefs().any { it.id == "cancel" })
    }
}
