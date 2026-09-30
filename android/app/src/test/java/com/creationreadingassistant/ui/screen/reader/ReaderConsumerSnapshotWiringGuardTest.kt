package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.4：**消费者接线的源码级结构守卫**。
 *
 * 为什么需要它：笔记 / 规则 / AI 解读 / 灵感 / AI 助读这些弹层活在 Compose 里，
 * JVM 单测**执行不到**它们的重组。把某一处改回「读实时活动选区」
 * （如 `selectedText = state.ui.selectedText`）在编译和所有行为用例下都是绿的，
 * 缺陷会静默复活 —— 只有源码级断言能拦住。
 *
 * 本守卫锁两件事：
 * 1. `ReaderSheetHost` 的每个消费者分支都读 `consumerSnapshot`，
 *    不得回读 `state.ui.selectedText` / 实时选区字段；
 * 2. 保存路径（`ReaderLayerBuilders` 的灵感保存、`ReaderScreen` 的笔记保存）
 *    的文字与 locator 都来自快照，不得出现「文字取快照、坐标取实时」的混搭。
 *
 * 与 [ReadingUnitDisposeWiringGuardTest] 同属**结构守卫**：证明接线形状，
 * 不证明运行时时序；运行时语义由 `ReaderSelectionSnapshotTest` 等用例覆盖。
 * 统计时两类必须分开计。
 */
class ReaderConsumerSnapshotWiringGuardTest {

    private val sheetHost =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt")
    private val layerBuilders =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderLayerBuilders.kt")
    private val screen =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt")

    private fun read(file: File): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}",
            file.exists(),
        )
        return file.readText()
    }

    /**
     * 定位 `when` 中的一个 sheet 分支：从 [header] 起，到**下一个** `ReaderSheet.` 分支
     * 头（或 `else ->`）为止。
     *
     * 不能简单配平花括号：分支体是 `ReaderSheet.X -> YyySheet(...)`，第一个 `{`
     * 往往是某个参数 lambda（`onBack = {`、`takeIf {`），配平会只截到那个 lambda。
     */
    private fun branch(text: String, header: String): String {
        // 锚定到**分支头**（`ReaderSheet.X ->`），否则会先命中分支体里嵌套的
        // `OpenSheet(ReaderSheet.X)` 之类调用，截到错的区间。
        val branchHeader = "$header ->"
        val start = text.indexOf(branchHeader)
        if (start < 0) throw AssertionError("定位不到 `$branchHeader` 分支（消费者被删除？）")
        val rest = text.substring(start + branchHeader.length)
        val nextBranch = Regex("""\n\s+(?:ReaderSheet\.[A-Z_]+|else)\s*->""").find(rest)
        return branchHeader + if (nextBranch != null) rest.substring(0, nextBranch.range.first) else rest
    }

    /** 去掉行注释，避免注释里提到的标识符被当成真实引用。 */
    private fun withoutComments(text: String): String =
        text.lines().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }

    // ── 1. 每个消费者 sheet 分支都读快照 ──────────────────────────
    @Test
    fun `every consumer sheet branch reads the snapshot`() {
        val host = read(sheetHost)
        mapOf(
            "ReaderSheet.RULES" to listOf("consumerSnapshot?.text"),
            "ReaderSheet.AI_ASSIST" to listOf("consumerSnapshot?.text"),
            "ReaderSheet.AI_EXPLAIN" to listOf("consumerSnapshot?.text"),
            "ReaderSheet.INSPIRATION" to listOf("consumerSnapshot?.text"),
        ).forEach { (branch, required) ->
            val block = withoutComments(branch(host, branch))
            required.forEach { token ->
                assertTrue(
                    "$branch 分支必须读 `$token`，不得回读实时活动选区",
                    block.contains(token),
                )
            }
        }
    }

    @Test
    fun `no consumer sheet branch reads the live selection fields`() {
        // 反向锁定：`state.ui.selectedText` 是**实时**活动选区，弹层一旦读它，
        // ReadingUnit dispose 后摘录/草稿/定位就全丢了 —— 这正是 F2.3 残留缺陷。
        val host = read(sheetHost)
        listOf(
            "ReaderSheet.RULES",
            "ReaderSheet.AI_ASSIST",
            "ReaderSheet.AI_EXPLAIN",
            "ReaderSheet.INSPIRATION",
        ).forEach { branch ->
            val block = withoutComments(branch(host, branch))
            assertFalse(
                "$branch 分支不得读实时选区 state.ui.selectedText",
                block.contains("state.ui.selectedText"),
            )
        }
    }

    @Test
    fun `AI assist never degrades to whole chapter text while a snapshot exists`() {
        // 要求 6：从选区打开 AI 助读时，正文选区被清后**不得**退化为整章正文。
        // 实现必须是「快照优先，无快照才回落整章」，且回落写在同一个表达式里。
        val block = withoutComments(branch(read(sheetHost), "ReaderSheet.AI_ASSIST"))
        assertTrue(
            "AI 助读必须以快照为首选上下文",
            block.contains("consumerSnapshot?.text"),
        )
        assertTrue(
            "整章正文只能作为无快照时的回落",
            block.contains("?: state.document.contentText"),
        )
    }

    @Test
    fun `the snapshot is read from the VM screen state not from a second source`() {
        // 唯一真源：快照存在 VM 的 screenState 里，弹层只读转发，不新增第二份状态。
        val host = read(sheetHost)
        assertTrue(
            "consumerSnapshot 必须由 inputs.screenState.selectionSnapshot 提供",
            host.contains("val consumerSnapshot = inputs.screenState.selectionSnapshot"),
        )
        assertTrue(
            "ReaderSheetUiState 不得再塞一份快照字段（避免两份状态分叉）",
            !host.contains("val selectionSnapshot:"),
        )
    }

    // ── 2. 保存路径：文字与 locator 同源 ──────────────────────────
    @Test
    fun `inspiration saves use the snapshot text and snapshot locator together`() {
        // 要求 7：禁止「文字取快照、坐标取已被清空的实时状态」。
        val builders = read(layerBuilders)
        listOf("onSaveAiExplainInspiration", "onSaveInspiration").forEach { save ->
            val block = withoutComments(builders.substring(builders.indexOf("$save = {")))
            assertFalse(
                "$save 不得再回读实时 selectedText",
                block.contains("val snapshotText = selectedText"),
            )
            assertFalse(
                "$save 不得再回读实时 computeLocatorJson()",
                block.contains("val locatorJson = computeLocatorJson()"),
            )
            assertTrue(
                "$save 必须使用随快照同源传入的 locatorJson",
                block.contains("locatorJson = locatorJson"),
            )
        }
    }

    @Test
    fun `note save uses the snapshot locator helper not the live one`() {
        val screenSrc = read(screen)
        assertTrue(
            "笔记保存必须走 computeLocatorJsonForSnapshot（快照口径）",
            screenSrc.contains("nav.computeLocatorJsonForSnapshot(noteSelection)"),
        )
        assertTrue(
            "笔记摘录取自打开时快照",
            screenSrc.contains("val noteSelection = screenState.selectionSnapshot"),
        )
        assertFalse(
            "笔记保存不得回读实时选区文本",
            screenSrc.contains(
                "saveReaderNote(\n                    onAction, bid, selectedText, noteBody",
            ),
        )
    }

    // ── 3. 捕获/释放只能在 VM 侧发生 ─────────────────────────────
    @Test
    fun `the rules sheet pins its prefill so a cleared selection cannot rebuild tab or draft`() {
        // 要求 4（Qoder ST-1）：`initialReplaceText` 曾是 tab / editing / initialReplaceDraft
        // 三个 remember 的 key。它来自活动选区，被清后 key 变化 ⇒ 三个 remember 全重建：
        // tab 从 REPLACE 跳回 TOC、editing 重置、用户草稿整份丢失。
        // 修法是把它钉成宿主生命周期常量，key 不再随正文选区变化。
        val rulesSheet = File(
            "src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderRulesSheet.kt",
        )
        val src = withoutComments(read(rulesSheet))

        assertTrue(
            "必须把打开时的预填文本钉成常量",
            src.contains("val pinnedReplaceText = remember { initialReplaceText }"),
        )
        assertFalse(
            "tab 的 remember key 不得再直接用 initialReplaceText",
            src.contains("var tab by remember(initialReplaceText"),
        )
        assertFalse(
            "editing 的 remember key 不得再直接用 initialReplaceText",
            src.contains("var editing by remember(initialReplaceText"),
        )
        assertFalse(
            "initialReplaceDraft 的 remember key 不得再直接用 initialReplaceText",
            src.contains("remember(initialReplaceText, replacementAvailable)"),
        )
        // 三处 key 都必须换成钉住值。
        val pinnedKeys = Regex("""remember\(pinnedReplaceText, replacementAvailable\)""")
            .findAll(src).count()
        assertEquals("tab / editing / initialReplaceDraft 三处 key 都应改为钉住值", 3, pinnedKeys)
    }
    @Test
    fun `capture and release happen in the view model reducers`() {
        val vm = File(
            "src/main/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModel.kt",
        )
        val src = read(vm)

        assertTrue(
            "OpenSheet 必须按消费者分类捕获/释放快照",
            src.contains("if (action.sheet.consumesSelection()) opened.withSelectionCaptured()"),
        )
        assertTrue(
            "CloseSheet 必须释放快照",
            src.contains("withSelectionSnapshotReleased()"),
        )
        assertFalse(
            "SetSelectedText 不得改写消费者快照",
            withoutComments(src.substring(src.indexOf("is ReaderAction.SetSelectedText"), src.indexOf("is ReaderAction.ClearSelection"))).contains("selectionSnapshot"),
        )
        assertTrue(
            "SetNoteOpen 必须在打开时捕获、关闭时释放",
            withoutComments(src.substring(src.indexOf("is ReaderAction.SetNoteOpen"), src.indexOf("is ReaderAction.SetNoteBody")))
                .contains("if (action.open) it.withSelectionCaptured() else it.withSelectionSnapshotReleased()"),
        )
    }

    @Test
    fun `the active clear helper never writes the snapshot field`() {
        // 两条语义必须正交：withSelectionCleared 只管活动选区。
        val vm = File(
            "src/main/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModel.kt",
        )
        val src = read(vm)
        val decl = "internal fun ReaderScreenState.withSelectionCleared(): ReaderScreenState = copy("
        val start = src.indexOf(decl)
        assertTrue("必须保留 withSelectionCleared 这一唯一清除入口", start >= 0)
        val block = withoutComments(src.substring(start, src.indexOf("\n)", start)))

        assertFalse(
            "withSelectionCleared 不得顺手清掉消费者快照",
            block.contains("selectionSnapshot"),
        )
        // 反向确认：活动选区五个字段仍在这里一起复位。
        listOf("selectedText", "selectedRangeStart", "selectedGlobalOffset", "selectedSourceEnd", "showColorRow")
            .forEach { field ->
                assertTrue(
                    "withSelectionCleared 必须继续复位 $field",
                    block.contains(field),
                )
            }
    }
}
