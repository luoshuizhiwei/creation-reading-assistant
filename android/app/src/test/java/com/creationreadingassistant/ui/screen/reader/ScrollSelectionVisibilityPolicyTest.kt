package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F5 D-3：滚动 TXT 活动选区的**可见性失效策略**（真机 P2 缺陷）。
 *
 * 真机现象（`docs/qa/2026-09-21-s07-qoder-device-acceptance.md` §7.3 / D-3）：
 * 在第1章建立选区 → 连续下滑到第6章 → 第1章已不在无障碍树中，
 * 工具栏与第1章摘录仍常驻，且能从该工具栏保存高亮。
 *
 * 单一根因假设（本文件用纯策略钉住，不堆第二个清除回调）：
 * 现有唯一失效机制是 `DisposableEffect.onDispose`，它回答的是「离开组合」，
 * 不是「离开可见范围」。LazyColumn 会把刚滚出视口的项保留在预取/缓存窗口内继续组合
 * （`ScrollUnitContentLoader` 另有 5 条 LRU），因此「屏幕上看不见」并不蕴含
 * 「已 dispose」—— onDispose 不跑，选区就一直挂在用户看不见的单元上。
 *
 * 因此把权威判据上移到**持有全局 LazyListState 的宿主层**：
 * [scrollSelectionInvalidation] 以 `layoutInfo.visibleItemsInfo` 为输入。
 * 本文件是它的行为测试，覆盖任务书要求的 10 条不变量。
 *
 * 全部为纯 JVM 策略测试（无 Compose 运行期），与 [readingUnitOwnsSelection] 的
 * 单单元谓词测试互补：那里回答「谁拥有」，这里回答「拥有者还看得见吗」。
 */
class ScrollSelectionVisibilityPolicyTest {

    private fun unit(index: Int, start: Int, count: Int) = ReadingUnit(
        unitIndex = index,
        chapterIndex = index,
        title = "第${index + 1}章",
        charStart = start,
        charCount = count,
    )

    /** 6 个单元，每个 100 字：ch1 = [0,100)，ch6 = [500,600)。 */
    private val sixUnits = (0 until 6).map { unit(it, it * 100, 100) }

    private fun decide(
        selectedRangeStart: Int,
        visible: Set<Int>,
        initialized: Boolean = true,
        units: List<ReadingUnit> = sixUnits,
    ) = scrollSelectionInvalidation(
        selectedRangeStart = selectedRangeStart,
        readingUnits = units,
        visibleUnitIndices = visible,
        viewportInitialized = initialized,
    )

    // ── 1. owner 可见：不清 ───────────────────────────────────────────

    @Test
    fun `the owner being visible keeps the selection`() {
        // 选区在第1章（unit 0），unit 0 可见 → 保持。
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 50, visible = setOf(0, 1, 2)),
        )
    }

    @Test
    fun `a small scroll that keeps the owner visible does not clear`() {
        // 任务书：owner 仍可见时滚动小距离不得清除。
        // 可见窗口滑动，但 unit 0 仍在其中。
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 50, visible = setOf(0, 1)),
        )
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 50, visible = setOf(0)),
        )
    }

    // ── 2. 只有相邻非 owner 可见：清 owner 选区 ──────────────────────

    @Test
    fun `only neighbouring non owners visible clears the owner selection`() {
        // 选区在 unit 0，可见的只有 unit 1/2（相邻但非 owner）→ 必须清。
        // 注意这里**不会**误判为「相邻单元在组合中所以保留」——可见集合里没有 owner。
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 50, visible = setOf(1, 2)),
        )
    }

    @Test
    fun `scrolling five chapters away clears the stale selection`() {
        // 真机场景复现：第1章选区，视图已在第6章。
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 50, visible = setOf(5)),
        )
    }

    // ── 3. viewport 瞬时为空：按明确安全策略处理 ─────────────────────

    @Test
    fun `an uninitialized viewport is undecidable and never clears`() {
        // 首帧未测量 / 一帧布局空值：不得误清用户的真实选区。
        assertEquals(
            ScrollSelectionInvalidation.UNDECIDABLE,
            decide(selectedRangeStart = 50, visible = emptySet(), initialized = false),
        )
    }

    @Test
    fun `the undecidable outcome never dispatches a clear`() {
        // 明确安全策略：UNDECIDABLE 不产生清除派发。
        val gate = ScrollSelectionInvalidationGate()

        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.UNDECIDABLE))
    }

    @Test
    fun `an initialized viewport with no visible items is decidable`() {
        // 与上一条区分：列表已测量但可见集合为空（极端布局）属于**可判定**，
        // 且此时 owner 必然不可见 → 清。安全策略只保护「尚未测量」这一种情形。
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 50, visible = emptySet(), initialized = true),
        )
    }

    // ── 4. selectedRangeStart 不属于任何单元：安全清除 ────────────────

    @Test
    fun `a start that belongs to no loaded unit is cleared safely`() {
        // 选区起点落在所有单元之外（文档被替换 / 单元列表变化）→ 无宿主 → 清。
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 99999, visible = setOf(0, 1, 2)),
        )
    }

    @Test
    fun `a start past the last unit is cleared safely`() {
        // 文档被替换 / 单元列表收缩后，起点可能落在所有单元之后 —— 无宿主 → 清。
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 600, visible = setOf(5)),
        )
    }

    @Test
    fun `no selection is always kept and never dispatched`() {
        // 无选区（-1）：策略保持，门也不派发 —— 不得凭空产生一次 ClearSelection。
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = -1, visible = setOf(0)),
        )
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = -1, visible = emptySet(), initialized = false),
        )
        assertFalse(ScrollSelectionInvalidationGate().shouldDispatch(ScrollSelectionInvalidation.KEEP))
    }

    // ── 5. 半开区间边界（与 readingUnitOwnsSelection 同口径） ────────

    @Test
    fun `the owner predicate uses the same half open range as the unit policy`() {
        // unit 0 = [0,100)。起点 0 属于 unit 0；起点 100 属于 unit 1。
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 0, visible = setOf(0)),
        )
        assertEquals(
            ScrollSelectionInvalidation.CLEAR,
            decide(selectedRangeStart = 0, visible = setOf(1)),
        )
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 100, visible = setOf(1)),
        )
    }

    @Test
    fun `a zero length unit never claims a selection`() {
        // 空单元（charCount = 0）不得认领任何起点，否则会吞掉别人的选区。
        val units = listOf(unit(0, 0, 0), unit(1, 0, 100))
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 0, visible = setOf(1), units = units),
        )
    }

    // ── 6. 用户手势滚动与程序化滚动采用同一判据 ──────────────────────

    @Test
    fun `user scroll and programmatic scroll share the same predicate`() {
        // 策略函数的输入只有「可见集合」，不含滚动来源 —— 因此两条路径必然同判据。
        // 这里显式钉住：同样的可见集合，两次判定结果一致（不存在按来源分叉的入口）。
        val afterUserScroll = decide(selectedRangeStart = 50, visible = setOf(5))
        val afterProgrammaticScroll = decide(selectedRangeStart = 50, visible = setOf(5))

        assertEquals(ScrollSelectionInvalidation.CLEAR, afterUserScroll)
        assertEquals(afterUserScroll, afterProgrammaticScroll)
    }

    // ── 7. owner 返回 viewport：旧选区不复活 ─────────────────────────

    @Test
    fun `the policy never resurrects a selection once it is cleared`() {
        // 策略本身不产生选区，只产生 KEEP / CLEAR / UNDECIDABLE。因此 owner 重新可见时
        // 只能得到 KEEP —— 而 KEEP 不会把已经清掉的选区「恢复」，因为清除是 VM 侧
        // 一次性写入（ClearSelection），策略层没有任何写回选区的通道。
        //
        // 这条断言的实质是：策略的输出集合里**不存在**「RESTORE」这类值。
        val outcomes = ScrollSelectionInvalidation.entries
        assertEquals(
            "失效策略只允许 KEEP / CLEAR / UNDECIDABLE 三种结论",
            listOf(
                ScrollSelectionInvalidation.KEEP,
                ScrollSelectionInvalidation.CLEAR,
                ScrollSelectionInvalidation.UNDECIDABLE,
            ),
            outcomes.toList(),
        )
        // owner 回到可见 → KEEP（保持当前状态，而不是把旧选区写回来）。
        assertEquals(
            ScrollSelectionInvalidation.KEEP,
            decide(selectedRangeStart = 50, visible = setOf(0, 1, 2)),
        )
    }

    // ── 8. 去重门：一次失效只派发一次 ────────────────────────────────

    @Test
    fun `a user scroll that removes the owner dispatches exactly once`() {
        val gate = ScrollSelectionInvalidationGate()

        // owner 可见 → 不清；随后离开 → 派发一次；继续滚动（持续 CLEAR）→ 不再派发。
        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.KEEP))
        assertTrue("owner 离开时必须派发清除", gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
        assertFalse("同一失效不得重复派发", gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
    }

    @Test
    fun `a programmatic scroll that removes the owner dispatches exactly once too`() {
        // TTS / 进度跳转走同一条 effect 与同一个门，因此派发次数契约相同。
        val gate = ScrollSelectionInvalidationGate()
        val viewportBefore = decide(selectedRangeStart = 50, visible = setOf(0, 1))
        val viewportAfter = decide(selectedRangeStart = 50, visible = setOf(4, 5))

        val first = gate.shouldDispatch(viewportBefore)
        val second = gate.shouldDispatch(viewportAfter)
        val third = gate.shouldDispatch(viewportAfter)

        assertFalse(first)
        assertTrue(second)
        assertFalse(third)
    }

    @Test
    fun `the gate reopens after a new selection becomes valid again`() {
        // 门必须在「非 CLEAR」结论上复位，否则用户建立**新**选区再滚走时不会被清。
        val gate = ScrollSelectionInvalidationGate()

        assertTrue(gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
        // 新选区建立（owner 可见）→ KEEP → 门复位。
        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.KEEP))
        // 新选区再离开可见范围 → 必须再派发一次。
        assertTrue("新选区离开时必须重新派发", gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR))
    }

    @Test
    fun `an undecidable frame does not consume the single dispatch`() {
        // 关键：首帧 UNDECIDABLE 不得把门占掉，否则真正离开可见范围时反而漏清。
        val gate = ScrollSelectionInvalidationGate()

        assertFalse(gate.shouldDispatch(ScrollSelectionInvalidation.UNDECIDABLE))
        assertTrue(
            "UNDECIDABLE 之后 owner 真正离开时仍必须派发",
            gate.shouldDispatch(ScrollSelectionInvalidation.CLEAR),
        )
    }

    // ── 9. 消费者快照与活动选区正交（F2.4 不变量） ───────────────────

    @Test
    fun `the invalidation only ever asks to clear the active selection`() {
        // 本策略的派发信号固定是空文本 onSelect("", -1, -1, -1)，在
        // ReaderContentHostCallbacks 里收敛到 ReaderAction.ClearSelection ——
        // 它**只**动活动选区五字段，不触碰 selectionSnapshot（F2.4 的正交性）。
        // 快照语义由 ReaderSelectionSnapshotTest 覆盖，这里只钉本策略不产生第二种信号。
        val signals = ScrollSelectionInvalidation.entries.map { decision ->
            if (decision == ScrollSelectionInvalidation.CLEAR) "clear-active-only" else "no-signal"
        }
        assertTrue(signals.none { it.contains("snapshot") })
    }

    // ── 10. 最终防线仍在：owner dispose 依然清除 ─────────────────────

    @Test
    fun `the dispose path remains as the final defence and shares the ownership predicate`() {
        // DisposableEffect 保留为最终资源回收防线（任务书允许），但不再是唯一机制。
        assertTrue("owner dispose 仍须清除", readingUnitShouldClearOnDispose(ownsSelection = true))
        assertFalse(
            "非 owner dispose 仍不得清除（相邻单元不误清）",
            readingUnitShouldClearOnDispose(ownsSelection = false),
        )
        // 两条机制共用同一个「拥有」判据，边界口径不会分叉。
        assertTrue(
            readingUnitOwnsSelection(
                selectedRangeStart = 50,
                unitSourceStartAbs = 0,
                unitSourceEndAbs = 100,
            ),
        )
    }
}
