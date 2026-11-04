package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3：**选区 / 持久高亮接线的源码级结构守卫**。
 *
 * 为什么需要它（与 [ReaderConsumerSnapshotWiringGuardTest] 同一理由）：Markdown 与
 * 非分页 EPUB 的整块选择、持久高亮投影都活在 Compose 重组里，JVM 单测**执行不到**。
 * 把某一处改回 `callbacks.onSelect(text, gOff, -1, -1)`（丢掉 sourceEnd）或把
 * `blockGlobalBase` 写死成 0，编译和全部行为用例都是绿的，缺陷会静默复活 ——
 * 只有源码级断言能拦住。
 *
 * 本守卫锁四件事：
 * 1. 两条分支提交选区时**同时**给出起点与源终点（同一映射结果），不得再硬编码 -1；
 * 2. Markdown 分支把 `blockGlobalBase` 真的传进 `RenderMarkdownUnit`（流式分章会错位）；
 * 3. 非分页 EPUB 分支坐标不可靠时回落 `onToggleControls`，不得发出可保存伪坐标；
 * 4. 表格不再产生字面量 `"[Table]"` 伪选区（历史缺陷）。
 *
 * 属于**结构守卫**：证明接线形状，不证明运行时语义；运行时语义由
 * [MarkdownUnitSourceRangeTest] / [EpubBlockSourceRangeTest] /
 * [MarkdownPersistentHighlightProjectionTest] 覆盖。统计时两类必须分开计。
 */
class ReaderSelectionRangeWiringGuardTest {

    private val markdownBranch = File(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderContentHostMarkdownBranch.kt",
    )
    private val epubBranch = File(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderContentHostEpubBranch.kt",
    )
    private val markdownRender = File(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderMarkdownRender.kt",
    )
    private val pagedEpub = File(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt",
    )

    private fun read(file: File): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}",
            file.exists(),
        )
        return file.readText()
    }

    /**
     * F3.1 任务 A 第 7 条：必须检查 `onSelect` 的**四个参数位置**，不能只检查
     * 字符串里是否出现了 `range.first` / `range.last`。
     *
     * F3 的 P1 缺陷正是「只检查了出现与否」漏掉的：调用写作
     * `onSelect(text, range.first, -1, range.last + 1)` —— 两个坐标都出现了，
     * 但起点被放进 `globalOffset` 槽，`rangeStart` 槽是 -1；而
     * `computeLocatorJson` 在 `epubBook == null` 时**只读 rangeStart**，
     * locator 恒为 null，持久高亮端到端不可达。这里按位置断言。
     */
    @Test
    fun `markdown branch submits start and source end from one mapping result`() {
        val text = read(markdownBranch)
        val call = Regex("""callbacks\.onSelect\([^)]*\)""").find(text)?.value
            ?: throw AssertionError("Markdown 分支找不到 onSelect 调用（整块选择被移除？）")
        val args = call.removePrefix("callbacks.onSelect(").removeSuffix(")").split(",")
            .map { it.trim() }
        assertTrue(
            "onSelect 必须是四参数调用：$call",
            args.size == 4,
        )
        val (textArg, globalOffset, rangeStart, sourceEnd) = args
        assertEqualsMsg("第 1 槽必须是可见文本：$call", "args.text", textArg)
        // 第 2 槽必须取 globalOffset 字段（其值恒为 -1 —— 「恒为 -1」由
        // [MarkdownBlockSelectionLocatorTest] 的行为断言证明，源码层只能证明取的是哪个字段）。
        assertEqualsMsg("第 2 槽必须是 globalOffset：$call", "args.globalOffset", globalOffset)
        assertEqualsMsg(
            "第 3 槽 rangeStart 必须承载 canonical 起点（写错槽会让 locator 恒为 null）：$call",
            "args.rangeStart",
            rangeStart,
        )
        assertEqualsMsg("第 4 槽 sourceEnd 必须承载半开终点：$call", "args.sourceEnd", sourceEnd)
        // 反向钉子：起点**不得**出现在 globalOffset 槽（F3 的 P1 形态）
        assertFalse(
            "起点不得写进 globalOffset 槽：$call",
            globalOffset == "args.rangeStart" || globalOffset == "args.sourceEnd",
        )
        assertFalse(
            "rangeStart 槽不得是常量 -1（否则 locator 恒 null）：$call",
            rangeStart == "-1",
        )
    }

    /**
     * 槽位含义本身也必须由**生产唯一入口**产生，而不是调用方手写顺序：
     * `markdownBlockSelectionArgs` 是那个入口，接线处不得再出现裸 `range.first`。
     */
    @Test
    fun `markdown branch derives the four slots from the dedicated mapping entry`() {
        val text = read(markdownBranch)
        assertTrue(
            "必须由 markdownBlockSelectionArgs 产出四槽，不得就地手写坐标",
            text.contains("markdownBlockSelectionArgs("),
        )
        val call = Regex("""callbacks\.onSelect\([^)]*\)""").find(text)?.value ?: ""
        assertFalse(
            "接线处不得再出现裸 range.first / range.last（顺序由类型固定）：$call",
            Regex("""\brange\.(first|last)""").containsMatchIn(call),
        )
    }

    private fun assertEqualsMsg(message: String, expected: String, actual: String) =
        assertTrue(message, expected == actual)

    @Test
    fun `markdown branch passes blockGlobalBase into the unit renderer`() {
        val text = read(markdownBranch)
        assertTrue(
            "必须把 blockGlobalBase 传给 RenderMarkdownUnit（流式分章解析会整体错位）",
            Regex("""blockGlobalBase\s*=\s*blockGlobalBase""").containsMatchIn(text),
        )
    }

    @Test
    fun `markdown branch feeds projected persistent highlights into the unit`() {
        val text = read(markdownBranch)
        assertTrue(
            "必须把持久高亮投影结果传给单元渲染",
            Regex("""persistentHighlights\s*=\s*unitHighlights""").containsMatchIn(text),
        )
        assertTrue(
            "投影必须复用 MarkdownPersistentHighlights（不得另起一套）",
            text.contains("MarkdownPersistentHighlights.projectPersistentHighlights"),
        )
    }

    @Test
    fun `epub scroll branch submits start and source end from one mapping result`() {
        val text = read(epubBranch)
        val call = Regex("""callbacks\.onSelect\(\s*block\.text[^)]*\)""").find(text)?.value
            ?: throw AssertionError("非分页 EPUB 分支找不到整块选择调用")
        assertTrue(
            "非分页 EPUB 必须传源终点（半开），此前恒为 -1：$call",
            Regex("""range\.last \+ 1""").containsMatchIn(call),
        )
        assertTrue(
            "起点必须来自 epubTextBlockSourceRange：$call",
            Regex("""range\.first""").containsMatchIn(call),
        )
    }

    @Test
    fun `epub scroll branch degrades to toggling controls when coordinates are unreliable`() {
        val text = read(epubBranch)
        // 锚定到**调用点**（`val range = ...`），否则会命中 epubBlockSourceRange 里的
        // 同名实现行，截到无关的区间。
        val idx = text.indexOf("val range = epubTextBlockSourceRange(block, gOff)")
        assertTrue("找不到整块选择调用点（被删除？）", idx >= 0)
        val body = text.substring(idx, (idx + 900).coerceAtMost(text.length))
        assertTrue(
            "坐标不可靠时必须回落 onToggleControls，不得发出可保存的伪坐标",
            body.contains("callbacks.onToggleControls()"),
        )
        assertTrue("不可靠分支必须先判 null", body.contains("range == null"))
    }

    @Test
    fun `markdown renderer never emits the literal table pseudo selection`() {
        val text = read(markdownRender)
        assertFalse(
            "表格不得再产生字面量 \"[Table]\" 伪选区（历史缺陷）",
            text.contains("[Table]"),
        )
    }

    @Test
    fun `markdown renderer gates selection on block selectability`() {
        val text = read(markdownRender)
        assertTrue(
            "整块选择必须由 markdownUnitIsBlockSelectable 门控",
            text.contains("markdownUnitIsBlockSelectable(unit)"),
        )
        assertTrue(
            "坐标必须由 markdownUnitCanonicalRange 给出，不得另算",
            text.contains("markdownUnitCanonicalRange(unit, blockGlobalBase)"),
        )
        assertTrue(
            "不可选择 / 无区间时不得挂 clickable",
            text.contains("blockCanonicalRange == null -> Modifier"),
        )
        assertTrue(
            "blockGlobalBase 不得再带默认值 0（省略会造成流式分章静默错位）",
            Regex("""blockGlobalBase:\s*Int,""").containsMatchIn(text),
        )
    }

    @Test
    fun `paged epub selection path is untouched by F3`() {
        // F3 明确不得影响分页 EPUB 选区逻辑：遗留 (String, Int) 契约必须仍在。
        val text = read(pagedEpub)
        assertTrue(
            "分页 EPUB 仍须保留遗留 onSelectBlock(String, Int) 契约",
            Regex("""onSelectBlock\s*=\s*\{\s*text,\s*gOff\s*->""").containsMatchIn(text),
        )
        assertTrue(
            "分页 EPUB 文本块仍按 block.text + gOff 提交",
            text.contains("onSelectBlock(block.text, gOff)"),
        )
    }
}
