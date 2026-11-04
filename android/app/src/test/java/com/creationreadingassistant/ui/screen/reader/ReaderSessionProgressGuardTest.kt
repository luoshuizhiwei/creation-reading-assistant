package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1 —— 读端会话位置保护守卫的定向回归（红→绿）。
 *
 * 覆盖任务14引入 [ReaderSessionPositionGuard] 后暴露的四类风险：
 *
 * 1) **移动后防抖尚未发射即退出**：原守卫只在 500ms 防抖 collector 里 observe，
 *    用户快速滑动后立即退出时守卫仍以为「未移动」，退出落库被压回旧初始位置。
 * 2) **合法小幅移动被像素阈值压住**：原判定 `abs(offsetPx - anchorPx) > 10`，
 *    小于 10px 的真实位移被当作未移动，退出落库被压回旧初始位置。
 * 3) **恢复自动滚动 / 加载重测量被误判为用户移动**：锚定之前的程序化滚动与占位帧不得算移动。
 * 4) **会话隔离与清理**：同书临时查阅不得污染普通阅读；离开后的新会话独立；
 *    旧会话取消不得清掉新会话；不再依赖进程级 bookId→Map 全局缓存。
 *
 * 断言全部通过**真实生产入口** [persistCurrentProgress] 与守卫状态机验证保存决策，
 * 不检查源码关键词、不镜像实现内部字段。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionProgressGuardTest {

    private val bookId = "b_session_guard"

    // 40 个阅读单元 × 68 字符 = 2720 字符（与设备实测的 S2=1775 同一量级）。
    private val units = (0 until 40).map { i -> ReadingUnit(i, 0, "", i * 68, 68) }
    private val totalChars = 40 * 68
    private val content = "x".repeat(totalChars)

    /** 权威初始进度：进入阅读器时已持久化的合法位置（设备实测 S2=1775 / 46.223957%）。 */
    private val initialOffset = 1775
    private val initialPercent = 46.223957f

    /** 恢复落位后的列表锚点：offset 1775 落在第 26 单元（26*68=1768 ≤ 1775 < 1836）。 */
    private val anchorIndex = unitIndexForOffset(units, initialOffset)
    private val anchorOffsetPx = 0

    private fun armedGuard(): ReaderSessionPositionGuard = ReaderSessionPositionGuard().apply {
        arm(initialOffset, initialPercent)
    }

    /** 走真实生产入口 [persistCurrentProgress] 的 TXT 滚动退出落库，返回捕获到的动作。 */
    private fun persistTxtExit(
        guard: ReaderSessionPositionGuard?,
        state: LazyListState,
    ): ReaderAction? {
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = mockk<ReaderLoadedBook>(relaxed = true),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = false,
            pagedAbsOffset = -1,
            pagedSource = null,
            chapterIndex = 0,
            epubListState = LazyListState(0, 0),
            blockGlobalOffsets = emptyList(),
            chapterBase = 0,
            chapterBlocks = emptyList(),
            bookIndex = null,
            chapterStartOffsets = listOf(0),
            visiblePlainOffset = -1,
            markdownDocument = null,
            txtStreamingDocument = null,
            plainContent = content,
            plainListState = state,
            readingUnits = units,
            sessionPositionGuard = guard,
        ) { saved = it }
        return saved
    }

    private fun savedOffset(action: ReaderAction?): Int? {
        val save = action as? ReaderAction.SaveProgress ?: return null
        return LocatorCodec.locatorFromProgress(save.progress.current_location_json)?.charOffset
    }

    private fun savedPercent(action: ReaderAction?): Float? =
        (action as? ReaderAction.SaveProgress)?.progress?.progress_percent

    // ── 合同保持：无操作退出必须保住权威初始位置（clamp35/40 合同）────────────────

    @Test
    fun `no-op exit keeps the authoritative initial position and percent`() = runTest {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx) // 恢复落位完成

        val saved = persistTxtExit(guard, LazyListState(anchorIndex, anchorOffsetPx))

        assertEquals("无操作退出必须落库权威初始偏移", initialOffset, savedOffset(saved))
        assertEquals(
            "无操作退出必须落库权威初始百分比（而非按实时列表重算）",
            initialPercent,
            savedPercent(saved)!!,
            1e-4f,
        )
    }

    @Test
    fun `restore auto-scroll and loading re-measure before anchoring are not user movement`() = runTest {
        val guard = armedGuard()
        // 恢复阶段的程序化滚动 / 占位帧重测量：锚定之前观测到大幅位移与多帧变化。
        guard.observe(0, 0)
        guard.observe(5, 400)
        guard.observe(12, 33)
        // 恢复落位完成 → 锚定
        guard.anchor(anchorIndex, anchorOffsetPx)
        // 锚定后加载/重测量：同一 (index, offsetPx) 的重复观测不是用户移动。
        guard.observe(anchorIndex, anchorOffsetPx)

        assertFalse("恢复程序化滚动与重测量不得被误判为用户移动", guard.hasUserMoved())
        val saved = persistTxtExit(guard, LazyListState(anchorIndex, anchorOffsetPx))
        assertEquals("无操作退出仍须保住权威初始位置", initialOffset, savedOffset(saved))
    }

    // ── 风险 1：移动后防抖尚未发射即退出 ─────────────────────────────────────────

    @Test
    fun `movement then exit before debounce emits saves the live position not the stale initial`() = runTest {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx)
        guard.beginUserScroll(anchorIndex, anchorOffsetPx) // Real input precedes coordinate change.

        // 用户滚动到第 30 单元；500ms 防抖尚未发射（没有任何 observe 采样），随即退出。
        val liveIndex = 30
        val saved = persistTxtExit(guard, LazyListState(liveIndex, 0))

        assertEquals(
            "退出必须使用本会话有效的实时位置，而不是防抖未发射时的旧初始位置",
            units[liveIndex].charStart,
            savedOffset(saved),
        )
        assertNotEquals("不得压回旧初始位置", initialOffset, savedOffset(saved))
    }

    @Test
    fun `immediate observation marks movement without waiting for the 500ms debounce`() {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx)
        guard.beginUserScroll(anchorIndex, anchorOffsetPx) // Real input precedes coordinate change.

        assertFalse("锚定后未观测到变化时不得判为移动", guard.hasUserMoved())

        guard.observe(anchorIndex, anchorOffsetPx + 7) // 即时（非防抖）观测
        assertTrue("即时观测到的位移必须立即判为用户移动，不得等待防抖", guard.hasUserMoved())
    }

    // ── 风险 2：合法小幅移动不得被像素阈值压住 ────────────────────────────────────

    @Test
    fun `small legal movement below any pixel threshold is not suppressed`() = runTest {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx)
        guard.beginUserScroll(anchorIndex, anchorOffsetPx) // Real input precedes coordinate change.


        // 同一单元内 7px 位移（远小于旧实现的 10px 阈值）——这是已经发生的合法阅读位置变化。
        val saved = persistTxtExit(guard, LazyListState(anchorIndex, anchorOffsetPx + 7))

        assertNotEquals("小于阈值的真实位移不得被压回旧初始位置", initialOffset, savedOffset(saved))
        assertEquals(
            "必须使用实时位置（该单元起点）落库",
            units[anchorIndex].charStart,
            savedOffset(saved),
        )
    }

    @Test
    fun `one pixel movement is treated as movement`() {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx)
        guard.beginUserScroll(anchorIndex, anchorOffsetPx) // Real input precedes coordinate change.

        guard.observe(anchorIndex, anchorOffsetPx + 1)
        assertTrue("任何已发生的位移都算移动，不使用任意像素阈值", guard.hasUserMoved())
        assertNull(
            "已移动时不得再回退权威初始位置",
            guard.resolveNoMovePosition(anchorIndex, anchorOffsetPx + 1),
        )
    }

    // ── 风险 4：会话隔离与清理 ───────────────────────────────────────────────────

    @Test
    fun `temporary inspection never participates in the session guard`() = runTest {
        assertTrue("普通阅读会话参与守卫", sessionPositionGuardParticipates(temporaryInspection = false))
        assertFalse("临时查阅会话不参与守卫", sessionPositionGuardParticipates(temporaryInspection = true))
        assertFalse(
            "临时模式不得绕过禁止保存门控",
            canPersistNormalReadingProgress(initialPositionPending = false, temporaryInspection = true),
        )

        // 普通阅读会话已武装并锚定。
        val normal = armedGuard()
        normal.anchor(anchorIndex, anchorOffsetPx)
        // 同书临时查阅使用**独立**守卫且不参与（未武装）；其观测不会触碰普通会话。
        val temporary = ReaderSessionPositionGuard()
        temporary.observe(5, 900)
        assertFalse("未武装的临时守卫不产生任何会话裁决", temporary.isArmed())

        val saved = persistTxtExit(normal, LazyListState(anchorIndex, anchorOffsetPx))
        assertEquals("临时查阅不得污染普通阅读的会话状态", initialOffset, savedOffset(saved))
    }

    @Test
    fun `a new session after leaving is independent of the previous one`() = runTest {
        val first = armedGuard()
        first.anchor(anchorIndex, anchorOffsetPx)
        first.beginUserScroll(anchorIndex, anchorOffsetPx)
        first.observe(30, 0) // 上一会话中用户移动过
        first.disarm() // 离开阅读器 → 释放本会话状态

        val second = ReaderSessionPositionGuard().apply {
            arm(2000, 70f)
            anchor(unitIndexForOffset(units, 2000), 0)
        }
        val secondIndex = unitIndexForOffset(units, 2000)
        val saved = persistTxtExit(second, LazyListState(secondIndex, 0))
        assertEquals("新会话使用自己的权威初始位置", 2000, savedOffset(saved))
        assertEquals(70f, savedPercent(saved)!!, 1e-4f)
    }

    @Test
    fun `disarming an old session never clears a newer session`() = runTest {
        val old = armedGuard()
        old.anchor(anchorIndex, anchorOffsetPx)

        val newer = ReaderSessionPositionGuard().apply {
            arm(2000, 70f)
            anchor(unitIndexForOffset(units, 2000), 0)
        }
        old.disarm() // 旧会话取消 / 释放

        assertEquals(
            "旧会话释放不得清掉新会话的裁决能力",
            2000,
            newer.resolveNoMovePosition(unitIndexForOffset(units, 2000), 0)?.first,
        )
        val saved = persistTxtExit(newer, LazyListState(unitIndexForOffset(units, 2000), 0))
        assertEquals(2000, savedOffset(saved))
    }

    @Test
    fun `unarmed guard never overrides the live position`() = runTest {
        // 未武装（既有单测 / Markdown 路径 / 未接线）时行为与接线前一致：直接用实时位置。
        val guard = ReaderSessionPositionGuard()
        assertNull("未武装不得产生会话裁决", guard.resolveNoMovePosition(0, 0))
        val saved = persistTxtExit(guard, LazyListState(5, 0))
        assertEquals("未武装时按实时位置落库", units[5].charStart, savedOffset(saved))
    }

    @Test
    fun `null guard preserves the pre-wiring behaviour`() = runTest {
        val saved = persistTxtExit(null, LazyListState(5, 0))
        assertEquals("未接线时按实时位置落库（既有调用点零变化）", units[5].charStart, savedOffset(saved))
    }

    // ── 场景 D（JVM）：防重测量合同与列表静止稳定性 ──────────────────────────────

    @Test
    fun `settled reader remeasure without coordinate displacement preserves authoritative initial position`() = runTest {
        val guard = armedGuard()
        guard.anchor(anchorIndex, anchorOffsetPx)

        // 模拟布局完成后的重测量：触发多次观测，但实际坐标与锚点完全一致
        guard.observe(anchorIndex, anchorOffsetPx)
        guard.observe(anchorIndex, anchorOffsetPx)

        assertFalse("重测量未发生坐标漂移时，守卫判定未移动", guard.hasUserMoved())
        val resolved = guard.resolveNoMovePosition(anchorIndex, anchorOffsetPx)
        assertEquals("未移动时稳定返回权威初始偏移", initialOffset, resolved?.first)
        assertEquals("未移动时稳定返回权威初始百分比", initialPercent, resolved?.second ?: 0f, 1e-4f)

        val saved = persistTxtExit(guard, LazyListState(anchorIndex, anchorOffsetPx))
        assertEquals("无位移重测量后退出，坚决保留权威初始偏移", initialOffset, savedOffset(saved))
    }

    // ── 场景 F（JVM）：旧会话迟到调用与新会话完全物理隔离 ────────────────────────

    @Test
    fun `stale session cancellation and late calls cannot corrupt active session`() = runTest {
        val oldGuard = armedGuard()
        oldGuard.anchor(anchorIndex, anchorOffsetPx)

        // 新会话独立实例化
        val newInitialOffset = 3850
        val newInitialPercent = 47.61f
        val newUnitIndex = unitIndexForOffset(units, newInitialOffset)
        val newGuard = ReaderSessionPositionGuard().apply {
            arm(newInitialOffset, newInitialPercent)
            anchor(newUnitIndex, 0)
        }

        // 模拟旧会话迟到返回的清理与调用
        oldGuard.disarm()
        oldGuard.observe(0, 0)
        assertNull("旧实例在 disarm 后调用 resolve 返回 null", oldGuard.resolveNoMovePosition(0, 0))

        // 验证新会话不受任何迟到调用的影响
        assertFalse("新会话状态不受旧会话迟到调用影响", newGuard.hasUserMoved())
        val newResolved = newGuard.resolveNoMovePosition(newUnitIndex, 0)
        assertEquals("新会话权威偏移不受破坏", newInitialOffset, newResolved?.first)
        assertEquals("新会话权威百分比不受破坏", newInitialPercent, newResolved?.second ?: 0f, 1e-4f)

        val saved = persistTxtExit(newGuard, LazyListState(newUnitIndex, 0))
        assertEquals("新会话退出落库新权威偏移", newInitialOffset, savedOffset(saved))
    }

    // ── 场景 E（JVM）：TXT 初始恢复对齐 locator 目标解析 ──────────────────────────

    @Test
    fun `source locator target resolution aligns plain restore offset`() {
        val locatorJson = "{\"v\":2,\"offset\":8023}"
        val target = SourceNavigationContract.targetFromStoredLocation(bookId, locatorJson)
        val position = target?.let { SourceNavigationContract.resolvePlainPosition(it) }

        assertEquals("有效 sourceLocator 正确解析为绝对字符偏移", 8023, position?.absoluteOffset)

        // 验证损坏 locator 安全降级为 null
        val invalidTarget = SourceNavigationContract.targetFromStoredLocation(bookId, "invalid-json")
        assertNull("无效 locatorJson 降级为 null", invalidTarget)
    }
}
