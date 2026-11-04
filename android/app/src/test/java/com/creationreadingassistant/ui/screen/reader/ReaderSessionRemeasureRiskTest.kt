package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
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

/** Regression of actual guard and production exit save: geometry versus explicit input. Device timing is verified separately. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionRemeasureRiskTest {

    private val bookId = "b_remeasure_risk"

    // 40 个阅读单元 × 68 字符 = 2720 字符（与设备实测 S2=1775 同一量级）。
    private val units = (0 until 40).map { i -> ReadingUnit(i, 0, "", i * 68, 68) }
    private val content = "x".repeat(40 * 68)

    /** 进入阅读器时已持久化的合法位置（设备实测 S2=1775 / 46.223957%）。 */
    private val initialOffset = 1775
    private val initialPercent = 46.223957f

    /** 恢复落位后的列表锚点：1775 落在第 26 单元（26*68=1768 ≤ 1775 < 1836）。 */
    private val anchorIndex = unitIndexForOffset(units, initialOffset)
    private val anchorOffsetPx = 0

    /** 重测量后真实变化的坐标（index 与 offsetPx 都不同，不是同坐标重复）。 */
    private val remeasuredIndex = anchorIndex + 1
    private val remeasuredOffsetPx = 40

    private fun armedGuard(): ReaderSessionPositionGuard = ReaderSessionPositionGuard().apply {
        arm(initialOffset, initialPercent)
        anchor(anchorIndex, anchorOffsetPx)
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

    @Test fun passiveGeometryCannotOpenDebounceOrChangeExitLocation() = runTest {
        val guard = armedGuard()
        guard.observe(remeasuredIndex, remeasuredOffsetPx)
        assertFalse(guard.hasUserMoved())
        val saved = persistTxtExit(guard, LazyListState(remeasuredIndex, remeasuredOffsetPx))
        assertEquals(initialOffset, savedOffset(saved))
    }

    @Test fun actualDragToIdenticalGeometryStillSavesNewLocation() = runTest {
        val guard = armedGuard()
        guard.beginUserScroll(anchorIndex, anchorOffsetPx)
        guard.observe(remeasuredIndex, remeasuredOffsetPx)
        guard.endUserScroll(remeasuredIndex, remeasuredOffsetPx)
        assertTrue(guard.hasUserMoved())
        val saved = persistTxtExit(guard, LazyListState(remeasuredIndex, remeasuredOffsetPx))
        assertEquals(units[remeasuredIndex].charStart, savedOffset(saved))
    }

    @Test fun explicitNavigationStillSavesNewLocation() = runTest {
        val guard = armedGuard()
        guard.recordNavigation()
        val saved = persistTxtExit(guard, LazyListState(remeasuredIndex, remeasuredOffsetPx))
        assertEquals(units[remeasuredIndex].charStart, savedOffset(saved))
    }

    @Test fun noOpDragFollowedByGeometryRemainsFrozen() = runTest {
        val guard = armedGuard()
        guard.beginUserScroll(anchorIndex, anchorOffsetPx)
        guard.endUserScroll(anchorIndex, anchorOffsetPx)
        guard.observe(remeasuredIndex, remeasuredOffsetPx)
        assertFalse(guard.hasUserMoved())
        assertEquals(initialOffset, savedOffset(persistTxtExit(guard, LazyListState(remeasuredIndex, remeasuredOffsetPx))))
    }

    @Test fun restoreBeforeAnchorDoesNotAuthorizeMovement() {
        val guard = ReaderSessionPositionGuard().apply { arm(initialOffset, initialPercent) }
        guard.beginUserScroll(anchorIndex, anchorOffsetPx)
        guard.observe(5,400)
        guard.recordNavigation()
        guard.anchor(anchorIndex,anchorOffsetPx)
        guard.observe(remeasuredIndex,remeasuredOffsetPx)
        assertFalse(guard.hasUserMoved())
    }
}
