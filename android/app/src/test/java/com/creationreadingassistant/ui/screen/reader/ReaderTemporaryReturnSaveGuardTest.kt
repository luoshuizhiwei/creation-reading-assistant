package com.creationreadingassistant.ui.screen.reader

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CRA-TASK48-v2：从临时查阅返回普通阅读时的保存保护守卫定向测试。
 *
 * 验证核心合同：
 * 1. 临时往返无真实移动保持抑制（保证 Room 12 列完全冻结，不改写 last_read_at / updated_at）；
 * 2. 返回后真实移动正常放行并解除武装；
 * 3. 首个 500ms 内的快速移动不被当作恢复锚点丢弃，直接识别为位移并放行保存；
 * 4. 普通重新阅读（从书架正常打开）恒不武装，正常保存和时间更新保持；
 * 5. 跨章导航位移同样解除武装并放行。
 */
class ReaderTemporaryReturnSaveGuardTest {

    private val bookId = "test_book_a2"

    @Before
    fun setUp() {
        TemporaryReturnSaveGuard.clear()
    }

    @After
    fun tearDown() {
        TemporaryReturnSaveGuard.clear()
    }

    @Test
    fun temporaryReturnWithoutMovementKeepsSavesSuppressed() {
        // 模拟从临时查阅返回：返回目标为 第2章（ci=1），offset=244
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertTrue("返回后守卫必须处于武装状态", TemporaryReturnSaveGuard.isArmed(bookId))

        // 初始定位未完成阶段（null 样本）：维持抑制
        assertTrue("位置尚未解析时必须抑制", TemporaryReturnSaveGuard.observe(bookId, null, 1, owner))

        // 阅读器恢复落位于 baseline (offset=244, ci=1)：持续抑制
        assertTrue("恢复到基线位置时必须抑制自动保存", TemporaryReturnSaveGuard.observe(bookId, 244, 1, owner))
        assertTrue("再次触发防抖采样时仍必须抑制", TemporaryReturnSaveGuard.observe(bookId, 244, 1, owner))

        // 未移动前用户退出：persistOnLeave 同样读取 isArmed 保持抑制，保证 12 列冻结
        assertTrue("未移动时退出必须受守卫保护", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun userMovementAfterReturnDisarmsGuardAndAllowsSave() {
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertTrue(TemporaryReturnSaveGuard.observe(bookId, 244, 1, owner))

        // 用户在第 2 章内发生翻页位移（offset 从 244 变为 300）
        val suppressed = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 300, currentChapterIndex = 1, owner)
        assertFalse("真实位移发生后不得抑制保存", suppressed)
        assertFalse("真实位移后守卫必须解除武装", TemporaryReturnSaveGuard.isArmed(bookId))

        // 解除后后续防抖采样直接放行
        assertFalse("已解除武装的书后续采样必须放行", TemporaryReturnSaveGuard.observe(bookId, 300, 1, owner))
    }

    @Test
    fun fastMovementWithin500msIsNotDiscardedAsRestorationAnchor() {
        // 关键反例场景（Codex Spec 2）：
        // 用户返回后，在 500ms 首个防抖输出发射前已经快速翻页到了 offset=350。
        // 首个采样就是新位置，守卫必须直接识别为真实位移，绝不能把它误当成“恢复锚点”直接丢弃！
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)

        // 首个到达防抖采集的样本已经是快速移动后的新位置 (350, 1)
        val suppressed = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 350, currentChapterIndex = 1, owner)
        assertFalse("首个防抖样本若已包含真实位移，必须直接放行保存，不得丢弃", suppressed)
        assertFalse("守卫必须立即解除", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun ordinaryReopenDoesNotArmGuardAndAllowsSave() {
        // 普通从书架/首页打开书籍（未经过临时查阅返回流程）
        assertFalse("普通打开书籍恒不武装", TemporaryReturnSaveGuard.isArmed(bookId))

        // 在相同位置触发保存
        val suppressed = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 244, currentChapterIndex = 1, null)
        assertFalse("未武装的书恒不抑制保存，允许正常更新 last_read_at", suppressed)
    }

    @Test
    fun chapterNavigationAfterReturnDisarmsGuardAndAllowsSave() {
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)

        // 用户通过目录或跳章跳到了第 1 章（ci=0, offset=0）
        val suppressed = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 0, currentChapterIndex = 0, owner)
        assertFalse("章节变化必须识别为位移并放行保存", suppressed)
        assertFalse("章节变化后守卫必须解除", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun owningSessionReleaseAndClearResetGuard() {
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertTrue(TemporaryReturnSaveGuard.isArmed(bookId))

        // 只有领到该会话凭证的持有者才能释放（任务48-v3 会话所有权）。
        val session = TemporaryReturnSaveGuard.session(bookId)
        TemporaryReturnSaveGuard.release(bookId, session)
        assertFalse(TemporaryReturnSaveGuard.isArmed(bookId))
        assertFalse(TemporaryReturnSaveGuard.observe(bookId, 244, 1, owner))
    }

    // ── 任务48-v3：Codex 复核反例的守卫级语义 ───────────────────────────

    @Test
    fun unarmedBookHasNoSession() {
        assertNull("未武装书籍没有会话凭证（普通打开不武装）", TemporaryReturnSaveGuard.session(bookId))
    }

    @Test
    fun lateDisposeOfOldSameBookRouteCannotReleaseNewReturnGuard() {
        // Codex 反例 1：resolveTemporaryReturnRoute 先按 book 武装新返回保护，
        // 旧临时 ReaderScreen 随后 onDispose。旧页面在武装**前**进入组合、
        // 领到 null 凭证，其迟到 dispose（release(bid, null)）不得清掉新会话。
        TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        val newSession = TemporaryReturnSaveGuard.session(bookId)
        assertNotNull("武装后必须发放会话凭证", newSession)

        // 旧临时页面的迟到 onDispose：释放它进入组合时领到的凭证（null）。
        TemporaryReturnSaveGuard.release(bookId, null)
        assertTrue("旧同书 route 的迟到清理不得释放新返回会话保护", TemporaryReturnSaveGuard.isArmed(bookId))
        assertEquals("当前武装会话仍须是新返回会话", newSession, TemporaryReturnSaveGuard.session(bookId))

        // 新普通页面自己 dispose 才释放。
        TemporaryReturnSaveGuard.release(bookId, newSession)
        assertFalse("新会话持有者 dispose 后应解除", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun staleGenerationSessionCannotReleaseNewerReturnSession() {
        // 同书第二次返回：第一次会话的凭证（旧代际）不得释放第二次武装的新会话。
        val firstSession = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 100, chapterIndex = 0)
        val secondSession = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNotNull(firstSession)
        assertNotNull(secondSession)
        assertNotEquals("每次武装必须发放独立的会话凭证", firstSession, secondSession)

        TemporaryReturnSaveGuard.release(bookId, firstSession)
        assertTrue("旧代际凭证不得释放新会话", TemporaryReturnSaveGuard.isArmed(bookId))
        assertEquals("当前武装会话必须是最新会话", secondSession, TemporaryReturnSaveGuard.session(bookId))

        TemporaryReturnSaveGuard.release(bookId, secondSession)
        assertFalse(TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun otherBookReleaseDoesNotTouchArmedSession() {
        // 不同书 route 的清理（不同 key）不影响本会话。
        val otherBookId = "test_book_other"
        val session = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        TemporaryReturnSaveGuard.release(otherBookId, session)
        TemporaryReturnSaveGuard.release(otherBookId, null)
        assertTrue("其他书的释放不得影响本会话", TemporaryReturnSaveGuard.isArmed(bookId))

        TemporaryReturnSaveGuard.release(bookId, session)
        assertFalse(TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun leaveAdjudicationWithMovedLivePositionBeforeDebounceRuns() {
        // Codex 反例 2：用户返回后移动到 350，并在 500ms 防抖发射前退出。
        // 退出落库以实时权威位置（observe 直接比对基线）裁决：已移动 → 放行保存。
        // 不依赖防抖消费者已经运行。
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)

        val frozen = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 350, currentChapterIndex = 1, owner)
        assertFalse("移动后（防抖未跑）退出必须放行保存", frozen)
        assertFalse("放行保存即解除武装", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun leaveWithoutMovementKeepsFullColumnFreeze() {
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)

        val frozen = TemporaryReturnSaveGuard.observe(bookId, currentOffset = 244, currentChapterIndex = 1, owner)
        assertTrue("未移动退出必须维持冻结", frozen)
        assertTrue("冻结期间会话保持武装", TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun leaveWithUnreadyPositionConservativelyKeepsFreeze() {
        // 位置尚未就绪（如返回后恢复落位仍在进行）：保守冻结，不得误判位移。
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertTrue("位置未就绪退出必须维持冻结", TemporaryReturnSaveGuard.observe(bookId, null, 1, owner))
        assertTrue(TemporaryReturnSaveGuard.isArmed(bookId))
    }

    // ── 任务48-v4：所有权覆盖**所有**修改入口（不只 release）────────────────

    @Test
    fun oldTemporaryLeaveCallbackCannotMutateNewNormalReturnSession() {
        // Codex 反例（v3 实证缺口）：会话所有权只落实到 release，没有落实到
        // observe 这类**会修改守卫状态**的入口。
        // 旧同书临时页面持有自己的**查阅**位置；新普通返回会话已经 arm；
        // 旧临时页面的 ON_PAUSE / 迟到离开回调仍调用 observe —— 它的位置与
        // 新返回基线不等，于是新会话被解除（即使该页面随后并未真正保存）。
        val oldOwner = TemporaryReturnSaveGuard.session(bookId)
        val newOwner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNull("旧临时页面在武装前进入组合，领不到凭证", oldOwner)

        // 旧临时页面离开：用本页面领到的凭证（null）裁决自己的查阅位置 100。
        val frozen = TemporaryReturnSaveGuard.observe(
            bookId = bookId,
            currentOffset = 100,
            currentChapterIndex = 1,
            owned = oldOwner,
        )
        assertEquals("旧临时回调不得清掉新普通会话", newOwner, TemporaryReturnSaveGuard.session(bookId))
        assertTrue("新会话必须仍处武装", TemporaryReturnSaveGuard.isArmed(bookId))
        assertFalse("无所有权回调不得冻结（既不冻结也不解除）", frozen)
    }

    @Test
    fun owningPageObserveOwnedAdjudicatesAndDisarmsOnMovement() {
        // 新普通页面（持有凭证）的裁决保持 v3 的退出语义：
        // 未移动 → 冻结；移动（防抖未跑）→ 解除并放行。
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)

        assertTrue("持有凭证的页面在基线位置必须冻结", TemporaryReturnSaveGuard.observe(bookId, 244, 1, owner))
        assertTrue("冻结期间仍武装", TemporaryReturnSaveGuard.isArmed(bookId))

        assertFalse("持有凭证的页面发生位移必须放行（500ms 内移动也成立）", TemporaryReturnSaveGuard.observe(bookId, 350, 1, owner))
        assertFalse("放行即解除", TemporaryReturnSaveGuard.isArmed(bookId))
        assertFalse("已解除后继续采样必须放行", TemporaryReturnSaveGuard.observe(bookId, 350, 1, owner))
    }

    @Test
    fun owningPageObserveOwnedKeepsFreezeWhilePositionUnready() {
        // 持有凭证 + 位置未就绪 → 保守冻结，不误判位移。
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertTrue("位置未就绪必须维持冻结", TemporaryReturnSaveGuard.observe(bookId, null, 1, owner))
        assertTrue(TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun staleOwnerObserveOwnedCannotMutateNewerReturnSession() {
        // 旧代际凭证（上一代返回会话）不得解除或冻结新一代会话。
        val firstOwner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 100, chapterIndex = 0)
        val secondOwner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertNotEquals("每次武装必须发放独立凭证", firstOwner, secondOwner)

        val frozen = TemporaryReturnSaveGuard.observe(bookId, 999, 3, firstOwner)
        assertEquals("旧代际凭证不得触碰新会话", secondOwner, TemporaryReturnSaveGuard.session(bookId))
        assertFalse("旧代际凭证不得冻结新会话", frozen)

        TemporaryReturnSaveGuard.release(bookId, secondOwner)
        assertFalse(TemporaryReturnSaveGuard.isArmed(bookId))
    }

    @Test
    fun crossBookObserveOwnedCannotTouchForeignSession() {
        // 其他书的裁决（即使偏移不等）不得影响本会话。
        val otherBookId = "test_book_other"
        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        TemporaryReturnSaveGuard.arm(otherBookId, returnOffset = 88, chapterIndex = 0)

        TemporaryReturnSaveGuard.observe(otherBookId, 999, 0, TemporaryReturnSaveGuard.session(otherBookId))

        assertEquals("他书裁决不得影响本会话", owner, TemporaryReturnSaveGuard.session(bookId))
        assertTrue(TemporaryReturnSaveGuard.isArmed(bookId))

        TemporaryReturnSaveGuard.release(bookId, owner)
        TemporaryReturnSaveGuard.release(otherBookId, TemporaryReturnSaveGuard.session(otherBookId))
    }

    @Test
    fun observeIsReadOnlyWhenUnarmedOrWithoutOwnership() {
        // 未武装 / 无所有权时 observe 恒放行且**不改状态**：
        // 不冻结（不误抑制普通保存），也不解除（不误清别人会话）。
        assertFalse("未武装书籍恒放行", TemporaryReturnSaveGuard.observe(bookId, 244, 1, SessionForTest()))
        assertFalse("未武装书籍无所有权调用也放行", TemporaryReturnSaveGuard.observe(bookId, 244, 1, null))

        val owner = TemporaryReturnSaveGuard.arm(bookId, returnOffset = 244, chapterIndex = 1)
        assertFalse("凭证不匹配（含 null）不得冻结", TemporaryReturnSaveGuard.observe(bookId, 999, 9, SessionForTest()))
        assertEquals("凭证不匹配也不得解除", owner, TemporaryReturnSaveGuard.session(bookId))
    }

    /** 与当前武装会话必然不同的凭证（测试辅助）。 */
    private fun SessionForTest(): TemporaryReturnSaveGuard.Session =
        TemporaryReturnSaveGuard.arm("nonexistent_book_for_test", returnOffset = 0, chapterIndex = null)
            ?: error("测试辅助凭证无法创建")
}
