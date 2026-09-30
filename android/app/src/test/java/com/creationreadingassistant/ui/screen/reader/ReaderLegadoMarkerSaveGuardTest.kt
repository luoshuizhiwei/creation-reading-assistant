package com.creationreadingassistant.ui.screen.reader

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S12 流式 TXT marker 保存保护——判定状态机定向测试。
 *
 * 生产接线：ReaderProgressEffects 的两处 500ms 防抖 collect 以
 * `LegadoMarkerSaveGuard.observe(bid, position)` 判定是否放行保存；
 * ReaderScreen.persistOnLeave 以 `LegadoMarkerSaveGuard.isArmed(bid)` 抑制
 * 退出/暂停/离开落库。本文件钉住判定语义：
 * - 恢复锚点上的自动保存持续被抑制（回归：把未换算 marker 覆盖为 offset 0）；
 * - 用户改变阅读位置后解除，正常原生进度保存恢复；
 * - 程序化恢复、临时查阅导航、滚动↔分页切换都不得被误判为用户移动；
 * - 换算成功 / 原生进度的书永不武装，既有路径零变化。
 *
 * Compose 效果体的接线本身不可在 JVM 运行（见报告「未运行的验证」）；
 * 本文件覆盖的是接线所消费的全部判定语义。
 */
class ReaderLegadoMarkerSaveGuardTest {

    @Before
    fun cleanUp() {
        disarmAll()
    }

    @After
    fun tearDown() {
        disarmAll()
    }

    private fun disarmAll() {
        LegadoMarkerSaveGuard.disarm("b")
        LegadoMarkerSaveGuard.disarm("p")
        LegadoMarkerSaveGuard.disarm("m")
        LegadoMarkerSaveGuard.disarm("t")
        LegadoMarkerSaveGuard.disarm("native")
        LegadoMarkerSaveGuard.disarm("a")
        LegadoMarkerSaveGuard.disarm("r")
    }

    @Test
    fun `auto save at restored anchor stays suppressed until user moves`() {
        LegadoMarkerSaveGuard.arm("b")
        // 初始定位未完成（pending）：不可定位 → 抑制，且不得捕获锚点。
        assertTrue(LegadoMarkerSaveGuard.observe("b", null) == true)
        // 程序化恢复落点（滚动 index+偏移）：第一帧锚定，继续抑制。
        assertTrue(LegadoMarkerSaveGuard.observe("b", 0 to 0) == true)
        // 恢复后 500ms 的自动保存（正是「把 marker 覆盖为 offset 0」的回归场景）：
        // 仍在锚点 → 持续抑制。
        assertTrue(LegadoMarkerSaveGuard.observe("b", 0 to 0) == true)
        assertTrue(LegadoMarkerSaveGuard.observe("b", 0 to 0) == true)
        // 用户未移动：退出/暂停落库同样被抑制。
        assertTrue(LegadoMarkerSaveGuard.isArmed("b"))
    }

    @Test
    fun `user movement disarms and lets normal saves proceed`() {
        LegadoMarkerSaveGuard.arm("b")
        LegadoMarkerSaveGuard.observe("b", null)
        LegadoMarkerSaveGuard.observe("b", 0 to 0)

        // 用户滚动：位置偏离锚点 → 解除保护，本次按正常流程保存。
        assertEquals(false, LegadoMarkerSaveGuard.observe("b", 5 to 240))
        // 此后该书完全解除：防抖与退出保存都走既有路径。
        assertNull(LegadoMarkerSaveGuard.observe("b", 5 to 240))
        assertFalse(LegadoMarkerSaveGuard.isArmed("b"))
    }

    @Test
    fun `paged mode anchors on the restored whole-book offset`() {
        LegadoMarkerSaveGuard.arm("p")
        assertTrue(LegadoMarkerSaveGuard.observe("p", null) == true)
        assertTrue(LegadoMarkerSaveGuard.observe("p", 8_321) == true)
        assertTrue(LegadoMarkerSaveGuard.observe("p", 8_321) == true)
        assertEquals(false, LegadoMarkerSaveGuard.observe("p", 9_000))
        assertNull(LegadoMarkerSaveGuard.observe("p", 9_100))
    }

    @Test
    fun `mode switch re-anchors instead of disarming`() {
        LegadoMarkerSaveGuard.arm("m")
        assertTrue(LegadoMarkerSaveGuard.observe("m", 0 to 0) == true)

        // 滚动↔分页切换：位置身份量纲变化，重锚（保持武装），不得误判为用户移动。
        LegadoMarkerSaveGuard.resetAnchor("m")
        assertTrue(LegadoMarkerSaveGuard.observe("m", 5_000) == true)
        assertTrue(LegadoMarkerSaveGuard.observe("m", 5_000) == true)
        assertTrue("新模式下未移动仍抑制", LegadoMarkerSaveGuard.isArmed("m"))

        // 新模式下用户真正移动才解除。
        assertEquals(false, LegadoMarkerSaveGuard.observe("m", 5_100))
    }

    @Test
    fun `temporary inspection navigation never disarms the marker guard`() {
        LegadoMarkerSaveGuard.arm("t")
        assertTrue(LegadoMarkerSaveGuard.observe("t", 0 to 0) == true)
        // 临时查阅期间位置不可作为普通阅读锚点：传 null，继续抑制、不破坏锚点。
        assertTrue(LegadoMarkerSaveGuard.observe("t", null) == true)
        // 返回后仍在原锚点：继续抑制，marker 保留。
        assertTrue(LegadoMarkerSaveGuard.observe("t", 0 to 0) == true)
        assertTrue(LegadoMarkerSaveGuard.isArmed("t"))
    }

    @Test
    fun `converted or native progress books are never armed`() {
        // 未武装的书：observe 恒返回 null（既有门控零变化），isArmed 恒 false。
        assertNull(LegadoMarkerSaveGuard.observe("native", 777_777))
        assertNull(LegadoMarkerSaveGuard.observe("native", null))
        assertFalse(LegadoMarkerSaveGuard.isArmed("native"))
        // disarm 未武装书 = 无操作。
        LegadoMarkerSaveGuard.disarm("native")
        assertNull(LegadoMarkerSaveGuard.observe("native", 1))
    }

    @Test
    fun `guard sessions are isolated per book`() {
        LegadoMarkerSaveGuard.arm("a")
        LegadoMarkerSaveGuard.arm("b")
        assertTrue(LegadoMarkerSaveGuard.observe("a", 1) == true)
        assertTrue(LegadoMarkerSaveGuard.observe("b", 2) == true)
        // a 解除不影响 b。
        LegadoMarkerSaveGuard.disarm("a")
        assertFalse(LegadoMarkerSaveGuard.isArmed("a"))
        assertTrue(LegadoMarkerSaveGuard.isArmed("b"))
        assertTrue(LegadoMarkerSaveGuard.observe("b", 2) == true)
    }

    @Test
    fun `re-arming on reopen starts a fresh anchor session`() {
        LegadoMarkerSaveGuard.arm("r")
        assertTrue(LegadoMarkerSaveGuard.observe("r", 1) == true)
        // 离开后重开同一本未换算书：重新武装 = 全新会话，重新锚定。
        LegadoMarkerSaveGuard.arm("r")
        assertTrue(LegadoMarkerSaveGuard.observe("r", 1) == true)
        assertEquals(false, LegadoMarkerSaveGuard.observe("r", 9))
    }

    @Test
    fun `blank book id never arms`() {
        LegadoMarkerSaveGuard.arm("")
        assertFalse(LegadoMarkerSaveGuard.isArmed(""))
        assertNull(LegadoMarkerSaveGuard.observe("", null))
    }

    // ── S12 集成复核修正（2026-09-28）：两处接线风险的判别性用例 ──────────────────
    // 修复前生产接线不存在 legadoGuardObservation / anchorAt（编译红灯即「当前接线失败」）：
    // ①临时查阅期间 collect 直接把查阅位置喂给守卫 → 提前解除、返回后 marker 被覆盖；
    // ②锚点只靠 500ms 防抖后的第一次 observe 建立 → 首采样前用户移动被误锚为锚点，
    //   首次主动滚动/翻页不保存，直到第二次移动。

    @Test
    fun `temporary inspection positions are never fed to the guard and return keeps protection`() {
        LegadoMarkerSaveGuard.arm("b")
        // 恢复落点即时锚定（修复 ②）：不依赖防抖后的首采样。
        LegadoMarkerSaveGuard.anchorAt("b", 0 to 0)
        assertTrue(LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, false, 0 to 0)) == true)

        // 修复 ①：临时查阅期间的位置变化必须被接线函数置为 null——不把查阅位置
        // 当作普通阅读移动交给守卫。
        assertNull(
            "临时查阅位置不得进入守卫判定",
            legadoGuardObservation(initialPositionPending = false, temporaryInspection = true, position = 40 to 120),
        )
        assertTrue(LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, true, 40 to 120)) == true)

        // 返回原锚点：marker 仍受保护（守卫未解除）。
        assertTrue(LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, false, 0 to 0)) == true)
        assertTrue(LegadoMarkerSaveGuard.isArmed("b"))

        // 返回后用户真正移动才解除并放行保存。
        assertEquals(false, LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, false, 3 to 9)))
    }

    @Test
    fun `user movement before first debounce sample disarms and saves`() {
        LegadoMarkerSaveGuard.arm("b")
        // 恢复完成即时锚定。
        LegadoMarkerSaveGuard.anchorAt("b", 0 to 0)
        assertTrue(LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, false, 0 to 0)) == true)

        // 首次防抖采样之前用户已移动：第一次 observe 就必须判为移动并放行保存，
        // 而不是把已移动的位置误锚为锚点。
        assertEquals(false, LegadoMarkerSaveGuard.observe("b", legadoGuardObservation(false, false, 4 to 77)))
        assertNull(LegadoMarkerSaveGuard.observe("b", 4 to 77))
        assertFalse(LegadoMarkerSaveGuard.isArmed("b"))
    }

    @Test
    fun `eager anchor is ignored for unarmed books and cannot overwrite an existing anchor`() {
        // 未武装（换算成功 / 原生进度）：anchorAt 无操作，既有路径零变化。
        LegadoMarkerSaveGuard.anchorAt("native", 1 to 1)
        assertNull(LegadoMarkerSaveGuard.observe("native", 1 to 1))

        // 已锚定后迟到的锚点请求不得覆盖既有锚点。
        LegadoMarkerSaveGuard.arm("e")
        LegadoMarkerSaveGuard.anchorAt("e", 0 to 0)
        LegadoMarkerSaveGuard.anchorAt("e", 9 to 9)
        assertTrue(LegadoMarkerSaveGuard.observe("e", 0 to 0) == true)
        assertEquals(false, LegadoMarkerSaveGuard.observe("e", 9 to 9))
    }

    @Test
    fun `pending snapshots never anchor even with eager anchoring available`() {
        LegadoMarkerSaveGuard.arm("pd")
        // anchorAt(null) = 无操作；pending 阶段的观察也保持 null（不锚定、不解除）。
        LegadoMarkerSaveGuard.anchorAt("pd", null)
        assertTrue(LegadoMarkerSaveGuard.observe("pd", legadoGuardObservation(true, false, 0 to 0)) == true)
        // 初始定位完成后的真实锚定照常生效。
        LegadoMarkerSaveGuard.anchorAt("pd", 2 to 0)
        assertTrue(LegadoMarkerSaveGuard.observe("pd", 2 to 0) == true)
    }
}
