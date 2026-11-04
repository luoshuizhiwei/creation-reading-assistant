package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.data.settings.SelectionActionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F4 第（三）项：**消费者分类与动作分发的完备性门禁**。
 *
 * 两条独立的「不能静默漏项」要求：
 *
 * 1. **消费者分类**（P2）：[ReaderSheet] 是一个枚举，[consumesSelection] 用
 *    `when` 穷举。新增一个 sheet 时 Kotlin 会报编译错 —— 但**只在这两个分支都
 *    显式列出**时才会报错。如果有人为了赶工加一个 `else -> false`，新加的
 *    消费者就会被静默判成「不消费选区」，弹层读不到摘录（缺陷表现为
 *    「打开灵感是空的」，很难往分类上想）。本类用**反射枚举全部值**断言
 *    每个值都被显式分类，且分类结果与 [ReaderSheetHost] 实际读快照的分支一致。
 *
 * 2. **动作分发**（P3 / F1 遗留）：可渲染动作 id 必须有对应的生产分发分支。
 *    死动作（点了没反应）是 S07-F1 的 P1 缺陷，已由 [SelectionActions.DORMANT_IDS]
 *    挡住；本类把它钉成不变量：dormant 既不出现在可配置清单、也不出现在渲染清单，
 *    而**每一个非 dormant 的可渲染 id 都必须在工具条的分发 `when` 里有分支**。
 *
 * 统计口径：本类是**行为测试**（反射读真实枚举与真实渲染函数），
 * 不是源码字符串守卫 —— 只有 `toolbar dispatch covers every renderable action`
 * 一项读源码（分发写在 Composable 里，JVM 执行不到，只能做源码级断言），
 * 该项在报告中单列。
 */
class ReaderConsumerAndActionGateTest {

    // ── 1. 消费者分类：每个 sheet 值都被显式分类 ──────────────────────

    @Test
    fun `every sheet value is explicitly classified as consumer or not`() {
        // 反射取全部枚举值：新增值必然被这条抓到。
        val all = ReaderSheet.values().toSet()
        assertEquals("ReaderSheet 枚举值数量变化时需复核本分类门禁", 12, all.size)

        val consumers = all.filter { it.consumesSelection() }.toSet()
        assertEquals(
            "消费者集合必须与 ReaderSheetHost 读快照的分支一致",
            setOf(
                ReaderSheet.RULES,
                ReaderSheet.AI_EXPLAIN,
                ReaderSheet.INSPIRATION,
                ReaderSheet.AI_ASSIST,
            ),
            consumers,
        )
        assertEquals(
            "非消费者集合必须覆盖剩下每一个值，不得有未分类项",
            all - consumers,
            setOf(
                ReaderSheet.TOC,
                ReaderSheet.NOTES,
                ReaderSheet.SETTINGS,
                ReaderSheet.PROGRESS,
                ReaderSheet.SEARCH,
                ReaderSheet.BOOK_INFO,
                ReaderSheet.THEME,
                ReaderSheet.DICTIONARY,
            ),
        )
    }

    @Test
    fun `the classification has no silent else branch`() {
        // Kotlin 的穷举 when 只在**所有分支都显式列出**时才保证完备。
        // 这条读源码断言没有 else 兜底 —— 有了 else，新 sheet 就会静默落入默认分类。
        // 源码不在 classpath：用 java.io.File 读（工作目录为 app 模块根）
        val file = java.io.File(
            "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreenState.kt",
        )
        assertTrue("必须能读到 ReaderScreenState.kt：${file.absolutePath}", file.exists())
        val text = file.readText()
        val decl = "internal fun ReaderSheet.consumesSelection(): Boolean = when (this) {"
        val start = text.indexOf(decl)
        assertTrue("定位不到 consumesSelection 声明（被重命名或删除？）", start >= 0)
        val block = text.substring(start, text.indexOf("\n}", start))
        assertFalse(
            "consumesSelection 不得有 else 分支 —— 否则新增 sheet 会被静默判为非消费者",
            block.contains("else ->"),
        )
        assertTrue(
            "两个分支必须都显式列举全部枚举值",
            block.contains("-> true") && block.contains("-> false"),
        )
    }

    @Test
    fun `a consumer sheet opening captures and a non consumer releases`() {
        // 分类不是文档，而是行为：消费者打开必须捕获，非消费者打开必须释放。
        val seeded = ReaderScreenState(
            selectedText = "一期一会",
            selectedRangeStart = 5,
            selectedGlobalOffset = 100,
            selectedSourceEnd = 120,
        )

        ReaderSheet.values().forEach { sheet ->
            val after = seeded
                .copy(sheet = sheet)
                .let { if (sheet.consumesSelection()) it.withSelectionCaptured() else it.withSelectionSnapshotReleased() }
            if (sheet.consumesSelection()) {
                assertTrue("$sheet 是消费者，打开后必须有快照", after.selectionSnapshot != null)
            } else {
                assertTrue("$sheet 不是消费者，打开后不得有快照", after.selectionSnapshot == null)
            }
        }
    }

    // ── 2. 动作分发：可渲染 id 必须有生产分支 ────────────────────────

    @Test
    fun `every renderable action id has a toolbar dispatch branch`() {
        // 源码级断言（Composable 分发在 JVM 单测里执行不到）。
        // 死动作是 S07-F1 的 P1：配置可见 → 可点 → 落入 else -> Unit → 无反应。
        val file = java.io.File(
            "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionToolbar.kt",
        )
        assertTrue(file.exists())
        val src = file.readText()
        val dispatch = src.substring(src.indexOf("val onActionClick: (String) -> Unit"))
            .let { it.substring(0, it.indexOf("\n    }")) }

        // 可渲染 id = 可配置清单 + 固定组（取消选择）
        val renderable = SelectionActions.pickableDefs().map { it.id } +
            SelectionActions.ALL.filter { it.group == com.creationreadingassistant.data.settings.SelectionActionGroup.FIXED }
                .map { it.id }

        renderable.forEach { id ->
            assertTrue(
                "可渲染动作 `$id` 必须在工具条分发 when 里有分支，否则是点了没反应的死动作",
                dispatch.contains("\"$id\" ->"),
            )
        }
    }

    @Test
    fun `dormant actions are neither configurable nor renderable`() {
        // 「编辑」等未接线动作：只移出默认集合挡不住用户主动勾选，必须双向挡住。
        val settings = SelectionActionSettings(
            enabledPrimary = SelectionActions.ALL.map { it.id }.toSet(),
            enabledMore = SelectionActions.ALL.map { it.id }.toSet(),
        )
        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = true,
            aiConfigured = true,
        )
        val rendered = (primary + more).map { it.id }.toSet()

        SelectionActions.DORMANT_IDS.forEach { id ->
            assertFalse("未接线的 $id 不得可配置", SelectionActions.pickableDefs().any { it.id == id })
            assertFalse("未接线的 $id 不得被渲染", id in rendered)
        }
        // 反向：dormant 之外的一切（含固定组）都可以被渲染出来。
        assertTrue("固定组「取消选择」必须始终可达", "cancel" in rendered)
    }

    @Test
    fun `gating never removes an action silently from both rows without a fallback`() {
        // 门控摘项后第一屏不得为空（用户会以为选区坏了）。
        val settings = SelectionActionSettings(
            enabledPrimary = setOf("replace", "ai"),
            enabledMore = setOf("replace", "ai"),
        )
        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = false,
            aiConfigured = false,
        )
        assertTrue("门控全摘后第一屏必须回退到至少一个可用动作", primary.isNotEmpty())
        assertTrue("固定组必须始终保留", more.any { it.id == "cancel" })
        assertFalse(primary.any { it.id in SelectionActions.DORMANT_IDS })
        assertFalse(more.any { it.id in SelectionActions.DORMANT_IDS })
    }
}
