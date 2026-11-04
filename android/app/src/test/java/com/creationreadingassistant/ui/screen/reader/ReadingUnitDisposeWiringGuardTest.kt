package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.4 / F2.3：滚动 ReadingUnit dispose 接线的**源码级结构守卫**。
 *
 * 为什么需要它：F2.2 的修复活在 `ReaderContentHostPlainTextBranch.kt` 的 dispose 块里，
 * 而纯函数用例只测 `readingUnitOwnsSelection` / `readingUnitShouldClearOnDispose` 两个
 * 谓词。以下改法都不会让任何**语义**用例变红，缺陷会静默复活：
 *
 * 1. 删掉整个 `DisposableEffect` → 单元回收后 VM 仍留着不可见的旧选区；
 * 2. 把 `rememberUpdatedState` 退回任何快照式 `remember`（`by remember(`、
 *    `= remember {`、`= remember(key) {` 都**编译得过**且看起来等价）→ `onDispose`
 *    读到陈旧的归属/旧回调 → 漏清或清错对象；
 * 3. 清除回调不再发送空文本 `onSelect`（漏参、走别的重载、或直接捕获 `callbacks.onSelect`）
 *    → 清不掉选区；
 * 4. 归属判定被挪出 `onDispose`（例如挪到 effect 体里只算一次）→ 归属不再参与 dispose 决策。
 *
 * 与 F2.3 版的差别（回应 Qoder ST-2 / ST-3）：
 * - 不再用「关键串出现在文件任何位置」判定。所有断言都先在源码里**定位出
 *   `DisposableEffect(content.sourceStartAbs)` 块**，再在其中定位 `onDispose { }` 块，
 *   最后只在该块内断言 —— 关键字挪到别处一律判失败。
 * - 删掉了原先那条形同虚设的 `assertFalse(content.contains("val ownsCurrentSelection by remember("))`：
 *   该写法根本编译不过（`remember` 不带 lambda 时不能 `by`），断言恒真，永远不可能抓到回归。
 *   改为解析 `ownsCurrentSelection` 的**声明**并校验其构造物只能是 `rememberUpdatedState`，
 *   这样 `val ownsCurrentSelection = remember { ... }` 这类**合法但危险**的写法也能被抓到。
 *
 * 本仓已有同类源码级结构断言的先例（`StatsFileStructureTest`、`InspirationFileStructureTest`、
 * `ProgrammaticJumpWiringGuardTest`）。JVM 单测工作目录 = app 模块根，因此这里的相对路径
 * 真实可执行；每条断言前先 fail-loud 检查文件存在，避免工作目录变化时静默通过。
 *
 * 注：本类是**结构守卫**，不是 Compose 运行时行为测试 —— 它证明的是接线形状，
 * 不证明运行时时序；运行时语义由 `ReadingUnitSelectionOwnershipTest` 等用例覆盖。
 */
class ReadingUnitDisposeWiringGuardTest {

    private val branchSource =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderContentHostPlainTextBranch.kt")

    private fun source(): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${branchSource.absolutePath}",
            branchSource.exists(),
        )
        return branchSource.readText()
    }

    // ── 块定位工具 ────────────────────────────────────────────────
    //
    // 花括号配平即可定位块：目标块内没有字符串 / 字符字面量 / 注释含花括号的情况，
    // 且配平失败会 fail-loud（返回 null 后断言报出明确原因），不会静默通过。

    /** 从 [openIndex] 处的 `{` 起配平到对应 `}`，返回含两端花括号的整块文本；不闭合返回 null。 */
    private fun matchBraces(text: String, openIndex: Int): String? {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 定位 `[header] { ... }` 块：先找 [header]（从 [fromIndex] 起），再取其后的首个 `{`
     * 开始配平。返回块内容（含花括号）；找不到 header 或不闭合时返回 null。
     */
    private fun blockAfter(text: String, header: String, fromIndex: Int = 0): String? {
        val headerIndex = text.indexOf(header, fromIndex)
        if (headerIndex < 0) return null
        val searchFrom = headerIndex + header.length - 1
        val openIndex = text.indexOf('{', searchFrom)
        if (openIndex < 0) return null
        return matchBraces(text, openIndex)
    }

    /** fail-loud 版 [blockAfter]：定位不到就带着原因直接失败，不让断言空跑。 */
    private fun requireBlock(text: String, header: String, fromIndex: Int = 0): String {
        return blockAfter(text, header, fromIndex)
            ?: throw AssertionError("源码中定位不到 `$header { ... }` 块（接线被改或删除？）")
    }

    /** `DisposableEffect(content.sourceStartAbs) { ... }` —— 滚动单元选区清理所在的 effect。 */
    private fun disposeEffectBlock(content: String): String =
        requireBlock(content, EFFECT_HEADER)

    /** effect 块内的 `onDispose { ... }`。 */
    private fun onDisposeBlock(content: String): String {
        val effect = disposeEffectBlock(content)
        return requireBlock(effect, "onDispose")
    }

    // ── 1. effect 仍在，且以单元身份为 key ─────────────────────────
    @Test
    fun `the dispose effect still exists and is keyed on the unit identity`() {
        // 回归 1：整块 DisposableEffect 被删除 → 单元回收不再清选区。
        // 这里不是查「串出现在文件里」：blockAfter 会配平出整块，块不存在即 fail-loud。
        val effect = disposeEffectBlock(source())

        assertTrue(
            "effect 块必须挂在单元身份 key 上（$EFFECT_HEADER）",
            source().contains(EFFECT_HEADER),
        )
        assertTrue(
            "effect 块内必须真的有 onDispose，否则这个 effect 什么也没清理",
            effect.contains("onDispose"),
        )
    }

    // ── 2. 归属决策参与 dispose 决策（断言落在 onDispose 块**内**） ───
    @Test
    fun `ownership participates in the dispose decision inside the onDispose block`() {
        // ST-3：过去只查「串出现在文件里」。把 `readingUnitShouldClearOnDispose(...)`
        // 挪到 effect 体（只算一次）也能让老断言通过 —— 现在必须在 onDispose 块内。
        val onDispose = onDisposeBlock(source())

        assertTrue(
            "onDispose 内必须经 readingUnitShouldClearOnDispose(...) 判定归属",
            onDispose.contains("readingUnitShouldClearOnDispose("),
        )
        assertTrue(
            "onDispose 内必须把归属结论 ownsCurrentSelection 交给该判定",
            onDispose.contains("ownsCurrentSelection"),
        )
        assertTrue(
            "归属不得在 onDispose 之外被提前取值（必须是 if 条件内参与决策）",
            onDispose.contains("if (readingUnitShouldClearOnDispose(ownsCurrentSelection))"),
        )
    }

    @Test
    fun `the clear call is gated by the ownership decision not emitted unconditionally`() {
        // 把清空调用挪到 if 之外（无条件清）会让相邻单元误清真实选区 —— 老断言抓不到。
        val onDispose = onDisposeBlock(source())
        val ifBlock = requireBlock(onDispose, "if (readingUnitShouldClearOnDispose(ownsCurrentSelection))")

        assertTrue(
            "清空信号必须在归属成立的分支里发出",
            ifBlock.contains("latestClearSelection("),
        )
        assertFalse(
            "onDispose 中不得在归属分支之外无条件发送清空信号",
            onDispose.replace(ifBlock, "").contains("latestClearSelection("),
        )
    }

    // ── 3. 归属经 rememberUpdatedState 读取（替换原先的空断言） ─────
    @Test
    fun `ownership is declared through rememberUpdatedState and no other remember family`() {
        // ST-2：原断言 `assertFalse(content.contains("val ownsCurrentSelection by remember("))`
        // 针对的是**编译不过**的写法，恒真、零价值。这里改为解析真实声明：
        // 抓 `val|var ownsCurrentSelection (by|=) <构造物>`，构造物只允许 rememberUpdatedState，
        // 于是 `val ownsCurrentSelection = remember { ... }` 这类合法但危险的写法也会失败。
        val declarations = Regex(
            """\b(?:val|var)\s+ownsCurrentSelection\s*(?:by|=)\s*([A-Za-z_][A-Za-z0-9_]*)""",
        ).findAll(source()).toList()

        assertTrue("必须至少有一处 ownsCurrentSelection 声明", declarations.isNotEmpty())
        declarations.forEach { m ->
            assertEquals(
                "归属必须经 rememberUpdatedState 读取最新值，实际为 `${m.groupValues[1]}`",
                "rememberUpdatedState",
                m.groupValues[1],
            )
        }
    }

    @Test
    fun `every remember family snapshot of ownership is rejected`() {
        // 显式列出「合法但危险」的几种写法，防止有人为了绕开上一条而换形式。
        val content = source()
        listOf(
            "val ownsCurrentSelection = remember {",
            "val ownsCurrentSelection = remember(",
            "var ownsCurrentSelection by remember(",
            "val ownsCurrentSelection by derivedStateOf(",
            "val ownsCurrentSelection = mutableStateOf(",
        ).forEach { dangerous ->
            assertFalse(
                "不得用快照式构造物持有归属：`$dangerous`",
                content.contains(dangerous),
            )
        }
    }

    @Test
    fun `the clear callback is read through rememberUpdatedState too`() {
        // 回调同样必须是最新值：捕获首次组合的 onSelect 会写到错的 book / 旧闭包。
        val declarations = Regex(
            """\b(?:val|var)\s+latestClearSelection\s*(?:by|=)\s*([A-Za-z_][A-Za-z0-9_]*)""",
        ).findAll(source()).toList()

        assertTrue("必须至少有一处 latestClearSelection 声明", declarations.isNotEmpty())
        declarations.forEach { m ->
            assertEquals(
                "清除回调必须经 rememberUpdatedState 读取最新值，实际为 `${m.groupValues[1]}`",
                "rememberUpdatedState",
                m.groupValues[1],
            )
        }
    }

    // ── 4. 清空信号仍是空文本 + 三个 -1 ────────────────────────────
    @Test
    fun `dispose sends a blank selection signal so it converges on ClearSelection`() {
        // 回归 3：清除回调不再发送空文本 onSelect → 沿 F2 接线收敛不到 ClearSelection。
        val onDispose = onDisposeBlock(source())

        assertTrue(
            "onDispose 必须发送空文本 onSelect(\"\", -1, -1, -1)",
            onDispose.contains("latestClearSelection(\"\", -1, -1, -1)"),
        )
        assertFalse(
            "onDispose 不得直接捕获首次组合的 onSelect",
            onDispose.contains("callbacks.onSelect("),
        )
    }

    // ── 5. 归属谓词单点定义（沿用 F2.3 C 项） ──────────────────────
    @Test
    fun `both ownership checks share the single tested predicate`() {
        // S07-F2.3 C 项：内联的 `in start until end` 必须改为复用同一个被断言过的纯函数，
        // 否则同一规则两份实现，边界口径一改就静默分叉。
        val content = source()

        assertFalse(
            "不得再保留手写的 `in content.sourceStartAbs until content.sourceEndAbs`",
            content.contains("in content.sourceStartAbs until content.sourceEndAbs"),
        )
        // 定义 1 处 + 调用至少 2 处（dispose 块与折叠分支）→ 共至少 3 次出现。
        val references = Regex("readingUnitOwnsSelection\\(").findAll(content).count()
        assertTrue(
            "readingUnitOwnsSelection( 应含 1 处定义 + 至少 2 处调用，实际 $references",
            references >= 3,
        )
    }

    private companion object {
        const val EFFECT_HEADER = "DisposableEffect(content.sourceStartAbs)"
    }
}
