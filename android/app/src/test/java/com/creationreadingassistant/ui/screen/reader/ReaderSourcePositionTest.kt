package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1.2 / R2-J1-I 回归：Reader 侧的「精确 source 位置采集」与「普通阅读 / 临时查阅隔离」纯函数语义。
 *
 * 这些测试只校验 [sourceNavigationTargetFor] / [canPersistNormalReadingProgress] /
 * [reportableSourceTarget] 三个生产纯函数，不依赖 Compose / Room / 真机。
 * [reportableSourceTarget] 是 R2-J1-I 接线后 ReaderProgressEffects 滚动与翻页两条位置路径
 * 实际调用的上报门槛；真正的持久化调用仍由源码中的
 * `if (!canPersistNormalReadingProgress(...)) return@collect` 负责短路，未在本测试伪造盘 I/O。
 */
class ReaderSourcePositionTest {

    // ── 回归 1：普通 TXT / EPUB / Markdown 位置产生正确 source target ──────────────

    @Test
    fun `txt source target carries global source offset only`() {
        val target = sourceNavigationTargetFor(
            bookId = "book-file",
            isEpub = false,
            chapterStartOffsets = listOf(0),
            absoluteOffset = 123,
        )

        assertNotNull(target)
        assertEquals("book-file", target!!.bookId)
        assertEquals(123, target.locator.legacyOffset)
        assertEquals(0, target.locator.chapterIndex)
        assertEquals(123, target.locator.charOffset)
    }

    @Test
    fun `epub source target maps absolute offset to verified chapter tuple`() {
        val target = sourceNavigationTargetFor(
            bookId = "book-file",
            isEpub = true,
            chapterStartOffsets = listOf(0, 100, 1_000),
            absoluteOffset = 450,
        )

        assertNotNull(target)
        assertEquals("book-file", target!!.bookId)
        assertEquals(450, target.locator.legacyOffset)
        assertEquals(1, target.locator.chapterIndex)
        assertEquals(350, target.locator.charOffset)
    }

    @Test
    fun `markdown source target follows canonical global offset rule`() {
        // Markdown 与 TXT 共享 canonical 路径：chapterIndex 恒 0，charOffset 与 legacyOffset
        // 都是全书 source 绝对偏移（R2-N1 的 ci=0 / co=全局 规则）。
        val target = sourceNavigationTargetFor(
            bookId = "book-file",
            isEpub = false,
            chapterStartOffsets = listOf(0, 100, 1_000),
            absoluteOffset = 250,
        )

        assertNotNull(target)
        assertEquals(250, target!!.locator.legacyOffset)
        assertEquals(0, target.locator.chapterIndex)
        assertEquals(250, target.locator.charOffset)
    }

    // ── 回归 2：temporary 模式不会触发普通 SaveProgress 覆盖 ────────────────────────

    @Test
    fun `temporary inspection suppresses normal progress persistence`() {
        // 临时查阅期间 (temporaryInspection=true)，即便初始定位已完成，也不得落库普通进度。
        assertFalse(canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = true))
        // 对照：普通阅读（非临时、非 pending）允许落库。
        assertTrue(canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = false))
    }

    // ── 回归 3：route 初始跳转 / 跨章程序化跳转不误记成用户普通阅读 ────────────────

    @Test
    fun `pending initial position suppresses normal progress persistence`() {
        // 初始定位尚未完成（route 初始跳转 / 跨章节程序化跳转期间）不算用户普通阅读。
        assertFalse(canPersistNormalReadingProgress(initialPositionPending = true, temporaryInspection = false))
    }

    @Test
    fun `pending and temporary together still suppress persistence`() {
        assertFalse(canPersistNormalReadingProgress(initialPositionPending = true, temporaryInspection = true))
    }

    // ── 回归 4：无效 locator 不写入、不崩溃 ─────────────────────────────────────────

    @Test
    fun `negative source offset produces no navigation target`() {
        // 负偏移属于无效坐标，必须显式不发送目标，绝不构造 offset=0 的假位置。
        assertNull(sourceNavigationTargetFor("book-file", isEpub = false, chapterStartOffsets = listOf(0), absoluteOffset = -1))
        assertNull(sourceNavigationTargetFor("book-file", isEpub = true, chapterStartOffsets = listOf(0, 100), absoluteOffset = -5))
    }

    @Test
    fun `blank book id produces no navigation target`() {
        assertNull(sourceNavigationTargetFor("", isEpub = false, chapterStartOffsets = listOf(0), absoluteOffset = 100))
        assertNull(sourceNavigationTargetFor("   ", isEpub = true, chapterStartOffsets = listOf(0, 100), absoluteOffset = 100))
    }

    // ── 回归 5：位置上报门槛 [reportableSourceTarget]（R2-J1-I 接线点）────────────
    // 这是 ReaderProgressEffects 滚动 / 翻页两条稳定 source 位置路径实际调用的生产 reducer。

    @Test
    fun `reportable target passes through normal reading valid coordinates`() {
        val target = reportableSourceTarget(
            bookId = "book-file",
            isEpub = false,
            chapterStartOffsets = listOf(0),
            absoluteOffset = 123,
            initialPositionPending = false,
            temporaryInspection = false,
        )

        assertNotNull(target)
        assertEquals("book-file", target!!.bookId)
        assertEquals(123, target.locator.legacyOffset)
        assertEquals(0, target.locator.chapterIndex)
        assertEquals(123, target.locator.charOffset)
    }

    @Test
    fun `temporary inspection never reports normal reading position`() {
        // temporary 模式：上报被抑制，返回 null（不覆盖普通阅读位置）。
        assertNull(
            reportableSourceTarget("book-file", false, listOf(0), 123, initialPositionPending = false, temporaryInspection = true),
        )
    }

    @Test
    fun `initial pending never reports normal reading position`() {
        // route 初始跳转 / 跨章程序化跳转尚未完成：不算用户阅读，不上报。
        assertNull(
            reportableSourceTarget("book-file", false, listOf(0), 123, initialPositionPending = true, temporaryInspection = false),
        )
    }

    @Test
    fun `pending and temporary together never report`() {
        assertNull(
            reportableSourceTarget("book-file", false, listOf(0), 123, initialPositionPending = true, temporaryInspection = true),
        )
    }

    @Test
    fun `invalid coordinates are never reported even in normal reading`() {
        assertNull(reportableSourceTarget("book-file", false, listOf(0), -1, false, false))
        assertNull(reportableSourceTarget("", false, listOf(0), 123, false, false))
        assertNull(reportableSourceTarget("   ", true, listOf(0, 100), 123, false, false))
    }

    @Test
    fun `epub reportable target maps to verified chapter tuple`() {
        val target = reportableSourceTarget(
            bookId = "book-b",
            isEpub = true,
            chapterStartOffsets = listOf(0, 100, 1_000),
            absoluteOffset = 450,
            initialPositionPending = false,
            temporaryInspection = false,
        )

        assertNotNull(target)
        assertEquals(1, target!!.locator.chapterIndex)
        assertEquals(350, target.locator.charOffset)
    }
}