package com.creationreadingassistant.ui.screen.reader

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CRA-TASK48-v4：临时返回守卫的**生产调用序列**回放。
 *
 * 守卫用例（[ReaderTemporaryReturnSaveGuardTest]）验证单点语义，本类按
 * 真实生产接线的事件顺序重放整段序列，证明 Compose 生命周期接线在
 * 时序上成立（Compose 运行时时序本身由设备验收覆盖，见 REPORT.md）。
 *
 * 生产接线（时序即合同）：
 * 1. 旧临时页面组合进入时采纳 `TemporaryReturnSaveGuard.session(bid)`
 *    （此刻尚未武装 → null）；
 * 2. 用户点击返回：`resolveTemporaryReturnRoute` 先 `arm(...)` 武装新
 *    返回会话并发放凭证，然后导航到普通阅读 route；
 * 3. 新普通页面组合进入时采纳 `session(bid)` 领到该凭证；
 * 4. 旧临时页面随后（可能迟到）onDispose：`release(bid, 旧页面凭证=null)`
 *    —— 不得影响新会话；
 * 5. 旧临时页面 ON_PAUSE / 迟到离开回调：`persistOnLeave` **先**以实时权威
 *    位置经 `observe(..., owned = 本页面凭证)` 裁决，**之后**才用
 *    `!temporaryInspection` 决定是否保存 —— 裁决已携带所有权，所以它自己的
 *    查阅位置（≠ 返回基线）解除不了新普通会话（任务48-v4 修复的缺口）；
 * 6. 新普通页面退出裁决与最终 onDispose 都用同一凭证。
 */
class ReaderTemporaryReturnLifecycleSequenceTest {

    private val bookId = "seq_book_a2"

    @Before
    fun setUp() {
        TemporaryReturnSaveGuard.clear()
    }

    @After
    fun tearDown() {
        TemporaryReturnSaveGuard.clear()
    }

    // ── 生产形状回放工具 ───────────────────────────────────────────────
    //
    // 关键顺序（任务48-v4 的缺口正在这里）：persistOnLeave **先算位置、先裁决守卫，
    // 之后才**用 `!temporaryInspection` 决定是否真正保存。所以裁决必须携带会话所有权，
    // 否则旧同书临时页面（temporaryInspection=true、领到 null 凭证）用自己的**查阅**
    // 位置就能在「并未真正保存」的情况下解除新普通会话。
    //
    // 下面两个函数逐行复刻该顺序（裁决入口 = 生产的 observeOwned；保存只由门控决定），
    // 用生产接线形状回放，不是孤立守卫调用。

    /** 复刻 persistOnLeave：先裁决（携本页面凭证），后判断是否临时查阅，最后保存。 */
    private fun persistOnLeaveShape(
        temporaryInspection: Boolean,
        owned: TemporaryReturnSaveGuard.Session?,
        liveOffset: Int?,
        liveChapter: Int?,
        onPersist: () -> Unit,
    ) {
        val temporaryReturnFrozen = TemporaryReturnSaveGuard.observe(
            bookId = bookId,
            currentOffset = liveOffset,
            currentChapterIndex = liveChapter,
            owned = owned,
        )
        if (!temporaryInspection && !temporaryReturnFrozen) onPersist()
    }

    /** 复刻 DisposableEffect(bid).onDispose：按本页领到的凭证释放。 */
    private fun disposeShape(owned: TemporaryReturnSaveGuard.Session?) =
        TemporaryReturnSaveGuard.release(bookId, owned)

    // ── 1. 旧同书临时页面离开：不得触碰新普通会话（Codex 反例的 v4 形状）─────

    @Test
    fun oldTemporaryPageLeaveCallbackCannotMutateNewNormalSession() {
        // ① 旧临时页面在武装前进入组合：领到 null。
        val oldRouteAdoptedSession = TemporaryReturnSaveGuard.session(bookId)
        assertNull("武装前进入组合的页面领不到凭证", oldRouteAdoptedSession)

        // ② resolveTemporaryReturnRoute：先武装新返回会话（导航前）。
        val newSession = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNotNull(newSession)

        // ③ 新普通页面进入组合：领到新会话。
        val newRouteAdoptedSession = TemporaryReturnSaveGuard.session(bookId)
        assertEquals("新页面必须领到新返回会话", newSession, newRouteAdoptedSession)

        // ④ 旧临时页面 ON_PAUSE / 迟到离开：生产形状 = 先裁决、后判断临时模式。
        //    它持有自己的查阅位置 100（≠ 返回基线 244），但领到的是 null 凭证。
        var persistedByOldTemporary = false
        persistOnLeaveShape(
            temporaryInspection = true,
            owned = oldRouteAdoptedSession,
            liveOffset = 100,
            liveChapter = 1,
            onPersist = { persistedByOldTemporary = true },
        )
        assertEquals("旧临时页面不得清掉新普通会话", newSession, TemporaryReturnSaveGuard.session(bookId))
        assertFalse("旧临时页面不得保存（temporaryInspection 门控仍生效）", persistedByOldTemporary)

        // ⑤ 旧临时页面迟到 dispose：同样不得触碰。
        disposeShape(oldRouteAdoptedSession)
        assertEquals("迟到 dispose 后新会话仍在", newSession, TemporaryReturnSaveGuard.session(bookId))
    }

    @Test
    fun sameBookLateDisposeOfTemporaryRouteKeepsNewNormalSessionArmed() {
        val oldRouteAdoptedSession: TemporaryReturnSaveGuard.Session? = null
        val newSession = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNotNull("武装必须发放会话凭证", newSession)
        val newRouteAdoptedSession = TemporaryReturnSaveGuard.session(bookId)
        assertEquals("新页面必须领到新返回会话", newSession, newRouteAdoptedSession)

        // 旧临时页面随后（可能迟到）onDispose：只能释放自己领到的凭证。
        disposeShape(oldRouteAdoptedSession)
        assertTrue("旧临时页面的迟到 dispose 不得清掉新普通页面的保护", TemporaryReturnSaveGuard.isArmed(bookId))

        // 用户未移动直接退出：退出落库裁决维持冻结（12 列保持）。
        var persisted = false
        persistOnLeaveShape(
            temporaryInspection = false,
            owned = newRouteAdoptedSession,
            liveOffset = 244,
            liveChapter = 1,
            onPersist = { persisted = true },
        )
        assertFalse("未移动退出必须冻结（不保存）", persisted)
        assertTrue("冻结期间会话保持武装", TemporaryReturnSaveGuard.isArmed(bookId))

        // 新普通页面 dispose：持有者释放，会话正常解除。
        disposeShape(newRouteAdoptedSession)
        assertFalse("新页面 dispose 后应解除武装", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun sameBookQuickMoveBeforeDebounceStillSavesOnLeave() {
        // 用户返回后在 500ms 防抖发射前移动并退出（v3 的快退修复必须保留）。
        val oldRouteAdoptedSession: TemporaryReturnSaveGuard.Session? = null
        val newSession = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        val newRouteAdoptedSession = TemporaryReturnSaveGuard.session(bookId)
        assertEquals(newSession, newRouteAdoptedSession)

        // 旧页面迟到离开/ dispose 均不影响新会话。
        var oldPersisted = false
        persistOnLeaveShape(true, oldRouteAdoptedSession, 100, 1, { oldPersisted = true })
        disposeShape(oldRouteAdoptedSession)
        assertFalse(oldPersisted)
        assertTrue(TemporaryReturnSaveGuard.isArmed(bookId))

        // 退出裁决用实时位置（350）：已移动 → 放行保存（防抖尚未运行也成立）。
        var persisted = false
        persistOnLeaveShape(
            temporaryInspection = false,
            owned = newRouteAdoptedSession,
            liveOffset = 350,
            liveChapter = 1,
            onPersist = { persisted = true },
        )
        assertTrue("快速移动后退出必须保存", persisted)
        assertFalse("放行即解除", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun differentBookOldRouteCleanupDoesNotAffectNewRouteSession() {
        // 旧 route 是书 A，新返回会话也是书 A；书 B 的清理互不影响。
        val bookB = "seq_book_b2"
        assertNull(TemporaryReturnSaveGuard.session(bookId))
        val oldRouteAdoptedSession: TemporaryReturnSaveGuard.Session? = null
        val sessionA = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNotNull(sessionA)
        TemporaryReturnSaveGuard.arm(bookB, returnOffset = 88, chapterIndex = 0)

        // 书 B 的页面（旧 route）迟到 dispose：只按书 B 释放。
        TemporaryReturnSaveGuard.release(bookB, null)
        assertTrue("书 B 的清理不得影响书 A 的返回会话", TemporaryReturnSaveGuard.isArmed(bookId))

        // 书 A 的旧页面迟到 dispose：仍不影响（它领到的是 null）。
        disposeShape(oldRouteAdoptedSession)
        assertTrue("书 A 旧页面迟到 dispose 不得释放新会话", TemporaryReturnSaveGuard.isArmed(bookId))

        // 书 A 旧页面的离开裁决（带位移）也不得影响。
        var persisted = false
        persistOnLeaveShape(true, oldRouteAdoptedSession, 12, 0, { persisted = true })
        assertFalse(persisted)
        assertEquals("旧页面离开裁决不得触碰新会话", sessionA, TemporaryReturnSaveGuard.session(bookId))

        disposeShape(sessionA)
        assertFalse(TemporaryReturnSaveGuard.isArmed(bookId))
    }
}
