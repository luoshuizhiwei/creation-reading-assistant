package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.2：滚动大 TXT 的 ReadingUnit 选区归属（缺陷 B）。
 *
 * 背景：每个 ReadingUnit 的本地 `selection` 以 `content.sourceStartAbs` 为 remember key。
 * 用户在单元 A 选中文字后滚动到单元 B，A 被 LazyColumn 回收时本地选区消失，
 * 但 VM 的 `selectedText` / source 坐标仍在，工具栏继续引用已经不可见的文字。
 *
 * 关键约束：**不能**用「任一当前渲染单元不包含选区就清空」——LazyColumn 会同时组合
 * 多个单元，那会让相邻单元误清真实选区。所以只允许「**拥有**当前 VM 选区的那个单元
 * 被回收/身份替换时」才请求清除。
 */
class ReadingUnitSelectionOwnershipTest {

    // ── 1. 归属判定 ─────────────────────────────────────────────────

    @Test
    fun `a unit owns the selection when its start falls inside the unit source range`() {
        // VM 存的是选区 source 起点；它落在本单元半开区间 [1000, 2000) 内 → 本单元拥有。
        assertTrue(
            readingUnitOwnsSelection(
                selectedRangeStart = 1500,
                unitSourceStartAbs = 1000,
                unitSourceEndAbs = 2000,
            ),
        )
    }

    @Test
    fun `a unit owns the selection when the start equals its first character`() {
        // 半开区间左端属于本单元（选区第一字符就在本单元第一个字符上）。
        assertTrue(
            readingUnitOwnsSelection(
                selectedRangeStart = 1000,
                unitSourceStartAbs = 1000,
                unitSourceEndAbs = 2000,
            ),
        )
    }

    @Test
    fun `a unit does not own a selection that starts at its exclusive end`() {
        // 半开区间右端**不**属于本单元 —— 这正是「相邻单元起点」的情形，
        // 必须判 false，否则下一个单元会误认领别人的选区。
        assertFalse(
            readingUnitOwnsSelection(
                selectedRangeStart = 2000,
                unitSourceStartAbs = 1000,
                unitSourceEndAbs = 2000,
            ),
        )
    }

    @Test
    fun `a unit does not own a selection from a neighbouring unit`() {
        // 选区在单元 B（3000..），对单元 A（1000..2000）必须为 false。
        assertFalse(
            readingUnitOwnsSelection(
                selectedRangeStart = 3200,
                unitSourceStartAbs = 1000,
                unitSourceEndAbs = 2000,
            ),
        )
        // 反之亦然。
        assertFalse(
            readingUnitOwnsSelection(
                selectedRangeStart = 1200,
                unitSourceStartAbs = 3000,
                unitSourceEndAbs = 4000,
            ),
        )
    }

    @Test
    fun `no selection means no unit owns it`() {
        // 无选区时 VM 的起点是 -1（ClearSelection 的复位值），任何单元都不得认领。
        assertFalse(
            readingUnitOwnsSelection(
                selectedRangeStart = -1,
                unitSourceStartAbs = 1000,
                unitSourceEndAbs = 2000,
            ),
        )
    }

    @Test
    fun `an empty or inverted unit range owns nothing`() {
        assertFalse(readingUnitOwnsSelection(1500, 1000, 1000))
        assertFalse(readingUnitOwnsSelection(1500, 2000, 1000))
    }

    // ── 2. dispose 时是否请求清除 ────────────────────────────────────

    @Test
    fun `disposing the owning unit requests a clear`() {
        assertTrue(readingUnitShouldClearOnDispose(ownsSelection = true))
    }

    @Test
    fun `disposing a non owning unit must not clear`() {
        // 相邻单元被回收时绝不能清掉真实选区。
        assertFalse(readingUnitShouldClearOnDispose(ownsSelection = false))
    }

    // ── 3. 多个单元同时组合时的整组判定 ──────────────────────────────

    @Test
    fun `only the owning unit is picked out of a set of composed units`() {
        // LazyColumn 同时组合多个单元：模拟 A、B、C 三个，选区在 B。
        val units = listOf(
            Triple(1000, 2000, "A"),
            Triple(2000, 3000, "B"),
            Triple(3000, 4000, "C"),
        )
        val owners = units.filter { (start, end, _) ->
            readingUnitOwnsSelection(selectedRangeStart = 2500, unitSourceStartAbs = start, unitSourceEndAbs = end)
        }.map { it.third }

        assertEquals("只有 B 拥有选区", listOf("B"), owners)
    }

    @Test
    fun `an adjacent unit never claims the selection across the whole set`() {
        // 同一组单元下，A 与 C 都必须为 false —— 这是「相邻单元不误清」的根。
        val units = listOf(1000 to 2000, 2000 to 3000, 3000 to 4000)
        val ownership = units.map { (start, end) ->
            readingUnitOwnsSelection(selectedRangeStart = 2500, unitSourceStartAbs = start, unitSourceEndAbs = end)
        }

        assertEquals(listOf(false, true, false), ownership)
    }
}
