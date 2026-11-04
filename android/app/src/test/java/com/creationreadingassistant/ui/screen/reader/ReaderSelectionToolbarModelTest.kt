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

        // 兜底取「定义顺序里第一个可用动作」。声明顺序是产品决策（见 SelectionActions.ALL
        // 的 KDoc），改顺序时这里要跟着改——当前第一屏顺序为 复制/浏览器/替换/编辑/字典。
        assertEquals(listOf(SelectionActions.pickableDefs().first().id), primary.map { it.id })
        assertEquals("copy", primary.single().id)
        assertFalse(primary.any { it.id == "replace" || it.id == "ai" })
    }

    @Test
    fun `a broken config on disk still renders a usable toolbar`() {
        // 手改过的 DataStore：未知 id + 已知 id 数超过槽位上限（上限 5 → 需要 6 个以上才触发截断）
        val settings = SelectionActions.sanitize(
            SelectionActionSettings(
                enabledPrimary = setOf(
                    "copy", "browser", "replace", "dictionary", "highlight", "note", "ghost",
                ),
            ),
        )

        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, SelectionActions.effectivePrimary(settings).size)

        val (primary, _) = selectionToolbarActions(settings, canCreateReplaceRule = true, aiConfigured = true)
        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, primary.size)
        assertFalse(primary.any { it.id == "ghost" })
    }

    @Test
    fun `pickable set excludes fixed and dormant actions`() {
        // 固定组（取消选择）不可隐藏；dormant（编辑，等 S08 接线）不可配置。
        // 两者都从可配置清单里排除，但原因不同，断言分开写。
        val ids = SelectionActions.pickableDefs().map { it.id }

        assertEquals(
            "可配置清单 = 全部非固定动作减去未接线的 dormant 动作",
            listOf("copy", "browser", "replace", "dictionary", "highlight", "note", "search", "ai", "inspiration"),
            ids,
        )
        assertFalse(ids.any { it == "cancel" })
        assertFalse("编辑未接线前不得可配置", ids.any { it == "edit" })
    }

    @Test
    fun `toolbar never renders a dormant action even when it is configured`() {
        // 缺陷 A 的用户路径：配置里存在 edit（旧磁盘数据或手改）时，
        // 工具栏渲染清单必须把它摘掉 —— 否则点击落入 else -> Unit，成为死按钮。
        val settings = SelectionActionSettings(
            enabledPrimary = setOf("copy", "edit"),
            enabledMore = setOf("edit", "note"),
        )

        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = true,
            aiConfigured = true,
        )

        assertFalse("高频槽不得渲染 edit", primary.any { it.id == "edit" })
        assertFalse("溢出菜单不得渲染 edit", more.any { it.id == "edit" })
        assertTrue("其余已配置动作不受影响", primary.any { it.id == "copy" })
        assertTrue("其余已配置动作不受影响", more.any { it.id == "note" })
    }
}
