package com.creationreadingassistant.feature.reader.navigation

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1.5 端到端旅程回归：验证普通阅读 → 临时查阅 → 返回的完整 LIFO 链路。
 *
 * 覆盖：
 * 1. 普通阅读 A@100 → 临时 B@200 → 临时 C@300 → 返回 B@200 → 返回 A@100，严格 LIFO
 * 2. 同书跨章和跨书跳转都仅使用 source locator
 * 3. 普通滚动/翻页不制造导航历史
 * 4. temporary 期间不会把普通阅读进度改到临时位置
 * 5. 进程重建语义：临时栈不存在，不能伪造返回位置
 * 6. 无 locator、负 offset、半截章节坐标安全降级
 */
class TemporaryReadingJourneyTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ── 1. 严格 LIFO 返回链路 ─────────────────────────────────────────────

    @Test
    fun `full journey A to B to C returns in strict LIFO order`() {
        val state0 = SourceNavigationState()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        val c = target("book-c", 300)

        // 普通阅读 A@100
        val state1 = SourceNavigationContract.recordNormalReading(state0, a)
        assertEquals(a, state1.active)
        assertEquals(a, state1.normalReading)
        assertTrue(state1.temporaryReturnStack.isEmpty())

        // 临时 B@200
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, b)
        assertEquals(b, state2.active)
        assertEquals(a, state2.normalReading) // 普通阅读位置不变
        assertEquals(listOf(a), state2.temporaryReturnStack)

        // 临时 C@300
        val state3 = SourceNavigationContract.beginTemporaryInspection(state2, c)
        assertEquals(c, state3.active)
        assertEquals(a, state3.normalReading) // 普通阅读位置仍不变
        assertEquals(listOf(a, b), state3.temporaryReturnStack)

        // 返回 B@200
        val state4 = SourceNavigationContract.returnFromTemporaryInspection(state3)
        assertEquals(b, state4.active)
        assertEquals(a, state4.normalReading)
        assertEquals(listOf(a), state4.temporaryReturnStack)

        // 返回 A@100
        val state5 = SourceNavigationContract.returnFromTemporaryInspection(state4)
        assertEquals(a, state5.active)
        assertEquals(a, state5.normalReading)
        assertTrue(state5.temporaryReturnStack.isEmpty())
    }

    // ── 2. 同书跨章和跨书跳转都仅使用 source locator ─────────────────────

    @Test
    fun `same-book cross-chapter uses only source locator`() {
        val chapter1 = target("book-a", 100)
        val chapter2 = target("book-a", 500)

        val state1 = SourceNavigationContract.recordNormalReading(SourceNavigationState(), chapter1)
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, chapter2)

        assertEquals(chapter2, state2.active)
        assertEquals(chapter1, state2.normalReading)
        assertEquals(listOf(chapter1), state2.temporaryReturnStack)

        // 返回时仍使用 source locator
        val state3 = SourceNavigationContract.returnFromTemporaryInspection(state2)
        assertEquals(chapter1, state3.active)
        assertTrue(state3.temporaryReturnStack.isEmpty())
    }

    @Test
    fun `cross-book journey uses only source locator`() {
        val bookA = target("book-a", 100)
        val bookB = target("book-b", 200)
        val bookC = target("book-c", 300)

        val state1 = SourceNavigationContract.recordNormalReading(SourceNavigationState(), bookA)
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, bookB)
        val state3 = SourceNavigationContract.beginTemporaryInspection(state2, bookC)

        // 验证所有位置都携带正确的 bookId
        assertEquals("book-c", state3.active?.bookId)
        assertEquals("book-a", state3.normalReading?.bookId)
        assertEquals("book-a", state3.temporaryReturnStack[0].bookId)
        assertEquals("book-b", state3.temporaryReturnStack[1].bookId)
    }

    // ── 3. 普通滚动/翻页不制造导航历史 ───────────────────────────────────

    @Test
    fun `normal reading progress does not create temporary history`() {
        val state0 = SourceNavigationState()
        val pos1 = target("book-a", 100)
        val pos2 = target("book-a", 150)
        val pos3 = target("book-a", 200)

        // 模拟普通阅读过程中的多次位置更新
        val state1 = SourceNavigationContract.recordNormalReading(state0, pos1)
        val state2 = SourceNavigationContract.recordNormalReading(state1, pos2)
        val state3 = SourceNavigationContract.recordNormalReading(state2, pos3)

        assertEquals(pos3, state3.active)
        assertEquals(pos3, state3.normalReading)
        assertTrue(state3.temporaryReturnStack.isEmpty()) // 无临时历史
    }

    // ── 4. temporary 期间不会把普通阅读进度改到临时位置 ──────────────────

    @Test
    fun `temporary inspection does not overwrite normal reading progress`() {
        val normal = target("book-a", 100)
        val temporary1 = target("book-b", 200)
        val temporary2 = target("book-c", 300)

        val state1 = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, temporary1)
        val state3 = SourceNavigationContract.beginTemporaryInspection(state2, temporary2)

        // 普通阅读位置始终保持为最初的位置
        assertEquals(normal, state3.normalReading)
        assertEquals(100, state3.normalReading?.locator?.legacyOffset)

        // active 是当前临时位置
        assertEquals(temporary2, state3.active)
    }

    // ── 5. 进程重建语义：临时栈不存在，不能伪造返回位置 ──────────────────

    @Test
    fun `restart recovery discards temporary stack and does not fabricate positions`() {
        val normal = target("book-a", 100)
        val temporary = target("book-b", 200)

        val state1 = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, temporary)

        // 模拟进程重启
        val restored = SourceNavigationContract.restoreAfterRestart(state2)

        assertEquals(normal, restored.active)
        assertEquals(normal, restored.normalReading)
        assertTrue(restored.temporaryReturnStack.isEmpty()) // 临时栈消失
    }

    @Test
    fun `fresh coordinator has no fabricated positions`() {
        val state = SourceNavigationState()
        assertNull(state.active)
        assertNull(state.normalReading)
        assertTrue(state.temporaryReturnStack.isEmpty())
    }

    // ── 6. 无 locator、负 offset、半截章节坐标安全降级 ───────────────────

    @Test
    fun `invalid coordinates degrade safely without fabricating offsets`() {
        // 空白书籍 ID
        assertNull(SourceNavigationContract.target("", ReaderLocator(100, 0, 100, null)))
        assertNull(SourceNavigationContract.target("   ", ReaderLocator(100, 0, 100, null)))

        // 负 offset
        assertNull(SourceNavigationContract.target("book-a", ReaderLocator(-1, null, null, null)))
        assertNull(SourceNavigationContract.target("book-a", ReaderLocator(-100, 0, -100, null)))

        // 半截章节坐标（有 chapterIndex 无 charOffset）
        assertNull(SourceNavigationContract.target("book-a", ReaderLocator(null, 1, null, null)))

        // null locator
        assertNull(SourceNavigationContract.target("book-a", null))
    }

    @Test
    fun `empty return stack does not fabricate return target`() {
        val state = SourceNavigationState()
        val result = SourceNavigationContract.returnFromTemporaryInspection(state)

        // 栈空时返回原状态，不伪造位置
        assertEquals(state, result)
        assertNull(result.active)
        assertNull(result.normalReading)
    }

    // ── 7. 8 层上限保护 ──────────────────────────────────────────────────

    @Test
    fun `temporary history bounded to 8 levels`() {
        val normal = target("book-a", 100)
        var state = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)

        // 连续 10 次临时跳转
        for (i in 1..10) {
            state = SourceNavigationContract.beginTemporaryInspection(
                state,
                target("book-$i", i * 100)
            )
        }

        // 栈大小不超过 8
        assertTrue(state.temporaryReturnStack.size <= 8)

        // 最旧的普通位置被挤出
        assertFalse(state.temporaryReturnStack.contains(normal))
    }

    // ── 8. recordNormalReading 清空临时链 ────────────────────────────────

    @Test
    fun `recording normal reading clears temporary chain`() {
        val normal1 = target("book-a", 100)
        val temporary1 = target("book-b", 200)
        val temporary2 = target("book-c", 300)
        val normal2 = target("book-a", 150)

        val state1 = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal1)
        val state2 = SourceNavigationContract.beginTemporaryInspection(state1, temporary1)
        val state3 = SourceNavigationContract.beginTemporaryInspection(state2, temporary2)

        // 此时有临时链
        assertEquals(2, state3.temporaryReturnStack.size)

        // 记录新的普通阅读位置
        val state4 = SourceNavigationContract.recordNormalReading(state3, normal2)

        // 临时链被清空
        assertTrue(state4.temporaryReturnStack.isEmpty())
        assertEquals(normal2, state4.active)
        assertEquals(normal2, state4.normalReading)
    }
}
