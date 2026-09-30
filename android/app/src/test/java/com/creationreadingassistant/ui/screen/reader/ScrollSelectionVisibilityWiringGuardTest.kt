package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F5 D-3：宿主层可见性失效接线的**源码级结构守卫**。
 *
 * 为什么需要它：D-3 的行为语义由 [ScrollSelectionVisibilityPolicyTest] 的纯策略用例
 * 覆盖（20 项），但那些用例**不会**因为下面这些改法变红 —— 缺陷会静默复活：
 *
 * 1. 删掉 `snapshotFlow { ... visibleItemsInfo ... }` 观察块 → 又回到「只靠
 *    `DisposableEffect` 判断离开组合」，真机上 owner 滚出视口但仍在预取窗口时不再清；
 * 2. 观察对象从 `visibleItemsInfo` 换成别的（例如只看 `firstVisibleItemIndex`）→
 *    判定粒度退化成单点，相邻可见单元会被误判为不可见；
 * 3. 清空信号不再走空文本 `onSelect("", -1, -1, -1)` → 收敛不到 `ClearSelection`；
 * 4. 去掉 `distinctUntilChanged` / 去重门 → 每帧派发一次清除；
 * 5. `DisposableEffect` 被删掉 → 失去最终资源回收防线。
 *
 * 本类是**结构守卫**，不是 Compose 运行期测试：它证明接线形状，不证明运行时序。
 * 运行时时序（`visibleItemsInfo` 何时更新）只能由真机复验确认。
 */
class ScrollSelectionVisibilityWiringGuardTest {

    private val branchSource =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderContentHostPlainTextBranch.kt")

    private fun source(): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${branchSource.absolutePath}",
            branchSource.exists(),
        )
        return branchSource.readText()
    }

    /** 从 [openIndex] 处的 `{` 起配平到对应 `}`；不闭合返回 null。 */
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

    private fun blockAfter(text: String, header: String, fromIndex: Int = 0): String? {
        val headerIndex = text.indexOf(header, fromIndex)
        if (headerIndex < 0) return null
        val openIndex = text.indexOf('{', headerIndex + header.length - 1)
        if (openIndex < 0) return null
        return matchBraces(text, openIndex)
    }

    private fun requireBlock(text: String, header: String): String =
        blockAfter(text, header)
            ?: throw AssertionError("源码中定位不到 `$header { ... }` 块（接线被改或删除？）")

    // ── 1. 宿主层必须观察可见项集合 ──────────────────────────────────

    @Test
    fun `the host observes the visible item set through snapshot flow`() {
        val content = source()
        val effect = requireBlock(content, HOST_EFFECT_HEADER)

        assertTrue(
            "宿主 effect 必须经 snapshotFlow 观察列表布局",
            effect.contains("snapshotFlow"),
        )
        assertTrue(
            "必须读 visibleItemsInfo（可见项集合），不能只看 firstVisibleItemIndex",
            effect.contains("visibleItemsInfo"),
        )
        assertFalse(
            "不得用固定 sleep/delay 猜测滚动停止（任务书禁止）",
            effect.contains("delay("),
        )
    }

    @Test
    fun `the observed visible set is mapped through the loaded units`() {
        // 可见项索引是 LazyColumn 的下标，必须经 readingUnits 映射成 unitIndex，
        // 否则策略拿到的集合与单元身份不是同一空间，判定会整体错位。
        val effect = requireBlock(source(), HOST_EFFECT_HEADER)

        assertTrue(
            "可见项下标必须映射为单元身份（getOrNull(...).unitIndex）",
            effect.contains("getOrNull(info.index)") && effect.contains(".unitIndex"),
        )
    }

    @Test
    fun `the observed viewport is de bounced with distinctUntilChanged`() {
        val effect = requireBlock(source(), HOST_EFFECT_HEADER)

        assertTrue(
            "滚动中可见集合会高频变化，必须 distinctUntilChanged 去抖",
            effect.contains("distinctUntilChanged()"),
        )
    }

    // ── 2. 判定走纯策略函数，并经去重门派发 ──────────────────────────

    @Test
    fun `the effect delegates the decision to the tested policy function`() {
        val effect = requireBlock(source(), HOST_EFFECT_HEADER)

        assertTrue(
            "必须调用被断言的纯策略函数 scrollSelectionInvalidation(...)",
            effect.contains("scrollSelectionInvalidation("),
        )
        assertTrue(
            "必须把可见集合交给策略（visibleUnitIndices = ...）",
            effect.contains("visibleUnitIndices ="),
        )
        assertTrue(
            "必须把 viewport 初始化状态交给策略（viewportInitialized = ...）",
            effect.contains("viewportInitialized ="),
        )
    }

    @Test
    fun `the clear signal is gated by the de duplicating gate`() {
        val effect = requireBlock(source(), HOST_EFFECT_HEADER)

        assertTrue(
            "派发必须经 ScrollSelectionInvalidationGate 去重",
            effect.contains("shouldDispatch("),
        )
        assertTrue(
            "清除信号必须是空文本四字段 onSelect(\"\", -1, -1, -1)",
            effect.contains("(\"\", -1, -1, -1)"),
        )
    }

    @Test
    fun `the selection input is read through rememberUpdatedState`() {
        // effect 的 key 是列表身份，选区变化不会重启它；若直接捕获组合期快照，
        // 判据会永远用第一次组合时的起点（F2.3 登记过的同一类缺陷）。
        val content = source()
        val declarations = Regex(
            """\b(?:val|var)\s+latestSelectedRangeStart\s*(?:by|=)\s*([A-Za-z_][A-Za-z0-9_]*)""",
        ).findAll(content).toList()

        assertTrue("必须声明 latestSelectedRangeStart", declarations.isNotEmpty())
        declarations.forEach { m ->
            assertTrue(
                "选区起点必须经 rememberUpdatedState 读最新值，实际为 `${m.groupValues[1]}`",
                m.groupValues[1] == "rememberUpdatedState",
            )
        }
    }

    // ── 3. DisposableEffect 保留为最终防线（不得删除） ───────────────

    @Test
    fun `the dispose effect survives as the final defence`() {
        // 任务书：保留 DisposableEffect 作为最终资源回收防线可以接受，
        // 但不得继续把它作为唯一的可见性失效机制 —— 上面已证明宿主策略存在。
        val content = source()

        assertTrue(
            "单元 dispose 的最终防线必须保留",
            content.contains("DisposableEffect(content.sourceStartAbs)"),
        )
        assertTrue(
            "宿主层可见性策略必须与 dispose 防线同时存在",
            content.contains(HOST_EFFECT_HEADER),
        )
    }

    private companion object {
        /** 宿主层可见性失效 effect 的头（LazyListState 为 key）。 */
        const val HOST_EFFECT_HEADER = "LaunchedEffect(s.plainListState)"
    }
}
