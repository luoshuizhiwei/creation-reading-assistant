package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TXT 重进恢复链定向回归（CRA-TXT-REENTRY-FIX-v1）。
 *
 * 设备缺陷（unified-mumu-accept-v1 J1）：退出保存 2553 正确落库，重进后恢复 effect
 * 在 LazyColumn 完成首次布局前执行 scrollToItem（被静默忽略）并置 pending=false，
 * 退出保存随后读到 (0,0) 把非零进度覆盖为 0。
 *
 * 本类用真实生产纯函数（[plainRestoreTarget] / [unitIndexForOffset] /
 * [canPersistNormalReadingProgress] / [currentPlainListOffset]）钉住整条裁决链；
 * Compose 布局时序本身无法在 JVM 复现，由 [layoutReady] 参数表达
 * `layoutInfo.totalItemsCount > 0 && index in units.indices` 的生产判据。
 */
class TxtReentryRestoreTest {

    /** 40 段 × 每段 68 字符 ≈ 设备夹具同构（约 2720 字符总量）。 */
    private fun units(total: Int, perUnit: Int): List<ReadingUnit> =
        (0 until total).map { i ->
            ReadingUnit(
                unitIndex = i, chapterIndex = 0, title = "",
                charStart = i * perUnit, charCount = perUnit,
            )
        }

    private val units = units(40, 68)
    private val totalChars = units.sumOf { it.charCount }

    @Test
    fun `nonzero saved offset resolves to the unit that contains it when layout is ready`() {
        val restore = plainRestoreTarget(
            savedPlainOffset = 2553, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = true,
        )
        assertTrue(restore != null)
        assertEquals(unitIndexForOffset(units, 2553), restore!!.itemIndex)
        assertEquals(2553, restore.targetOffset)
        // 目标单元确实包含 2553：恢复后 firstVisibleItemIndex 指向它，
        // currentPlainListOffset 读出的起点不得是 0。
        val u = units[restore.itemIndex]
        assertTrue(2553 in u.charStart until u.charStart + u.charCount)
    }

    @Test
    fun `layout not ready keeps pending and defers instead of silently scrolling to top`() {
        val restore = plainRestoreTarget(
            savedPlainOffset = 2553, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = false,
        )
        assertNull("布局未就绪必须返回 null（保持 pending、下一帧重试）", restore)
        // 契约联动：pending 未释放时退出/暂停保存被生产门控拦截——
        // 这就是"重进不再把 DB 覆盖为 0"的保障。
        assertFalse(canPersistNormalReadingProgress(initialPositionPending = true, temporaryInspection = false))
    }

    @Test
    fun `restored position then user exit persists the restored offset not zero`() {
        // 恢复完成后（pending=false），退出保存从真实列表状态读位置：
        // 列表停在目标单元起点 → currentPlainListOffset 必须读出该单元 charStart，
        // 而不是旧实现 (0,0) 时的 0。
        val restore = plainRestoreTarget(
            savedPlainOffset = 2553, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = true,
        )!!
        val stateAfterRestore = LazyListState(
            firstVisibleItemIndex = restore.itemIndex,
            firstVisibleItemScrollOffset = 0,
        )
        val savedOffset = currentPlainListOffset(stateAfterRestore, units, fallbackOffset = 0)
        assertEquals(units[restore.itemIndex].charStart, savedOffset)
        assertTrue(savedOffset > 0)
        assertTrue(canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = false))
    }

    @Test
    fun `fresh book with zero progress opens at unit zero and saves without being blocked`() {
        val restore = plainRestoreTarget(
            savedPlainOffset = 0, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = false, // 新书布局未就绪也要放行
        )
        assertEquals(0, restore!!.itemIndex)
        assertEquals(0, restore.targetOffset)
        assertTrue(canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = false))
    }

    @Test
    fun `oversized and percent-only locators clamp safely`() {
        // 越界 offset：clamp 到 totalChars 所在末单元
        val over = plainRestoreTarget(
            savedPlainOffset = totalChars + 5000, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = true,
        )!!
        assertEquals(unitIndexForOffset(units, totalChars), over.itemIndex)
        assertEquals(totalChars, over.targetOffset)
        // 仅 percent：换算成偏移
        val pct = plainRestoreTarget(
            savedPlainOffset = 0, savedPlainPercent = 50f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = -1, layoutReady = true,
        )!!
        assertEquals(totalChars / 2, pct.targetOffset)
        // 仅 percent 但 totalChars 未知（0）→ 0 目标，放行到书首，不卡 pending
        val pctZero = plainRestoreTarget(
            savedPlainOffset = 0, savedPlainPercent = 50f,
            totalChars = 0, units = units,
            pagedAbsOffset = -1, layoutReady = false,
        )!!
        assertEquals(0, pctZero.targetOffset)
    }

    @Test
    fun `paged in-session offset takes priority over saved plain values`() {
        val restore = plainRestoreTarget(
            savedPlainOffset = 2553, savedPlainPercent = 0f,
            totalChars = totalChars, units = units,
            pagedAbsOffset = 1024, layoutReady = true,
        )!!
        assertEquals(1024, restore.targetOffset)
        assertEquals(unitIndexForOffset(units, 1024), restore.itemIndex)
    }

    @Test
    fun `empty units never resolves and never crashes`() {
        assertNull(
            plainRestoreTarget(
                savedPlainOffset = 2553, savedPlainPercent = 0f,
                totalChars = totalChars, units = emptyList(),
                pagedAbsOffset = -1, layoutReady = true,
            ),
        )
    }
}
