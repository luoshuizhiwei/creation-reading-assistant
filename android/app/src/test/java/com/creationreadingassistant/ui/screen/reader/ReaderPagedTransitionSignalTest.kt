package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderExitBoundary
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 197-r3：临时入口转换信号与分页无位移退出判定的定向集成回归。
 *
 * 覆盖：
 * 1. ReaderExitBoundary.requestExitWithAction(skipExitSave=true) → dispatchExitSave 不调用
 * 2. ReaderExitBoundary.requestExitWithAction(skipExitSave=false) → dispatchExitSave 调用
 * 3. 两种模式下 awaitProgressSaves/action 均执行（排空后导航）
 * 4. skipPagedExitPersistOnUnmovedSession 纯判定（未移动=skip，移动/无效=no skip）
 * 5. 真实 persistCurrentProgress(paged) 派发 SaveProgress 携带引擎页首
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPagedTransitionSignalTest {

    private val bookId = "b_paged_transition_r3"
    private val totalChars = 40 * 68
    private val content = "x".repeat(totalChars)
    private val anchorOffset = 2000

    private val pagedSource: PagedChapterSource = mockk {
        every { chapterIndexFor(any()) } returns 0
        every { chapterStartAbs(any()) } returns 0
    }

    // 生产 boundary 的失败反馈/action 在 Dispatchers.Main.immediate 上执行；
    // 本仓库既有约定（见 ReaderTemporaryEntryBoundaryTest）：
    // 共享同一 StandardTestDispatcher 同时作为 runTest 调度器与 Main，
    // 使 withContext(Main.immediate) 的任务落入 advanceUntilIdle 可推进的同一队列。
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── ReaderExitBoundary 定向（skipExitSave 参数行为） ──

    @Test
    fun `skipExitSave true - dispatchExitSave NOT called but action still runs`() = runTest(testDispatcher) {
        var saveDispatched = false
        var actionRun = false
        var drained = false
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { saveDispatched = true },
            awaitProgressSaves = { },
            navigate = {},
            onDrained = { drained = true },
        )
        boundary.requestExitWithAction(
            action = { actionRun = true },
            skipExitSave = true,
        )
        runCurrent()
        advanceUntilIdle()
        assertFalse("skipExitSave=true → dispatchExitSave 不调用", saveDispatched)
        assertTrue("action 仍执行", actionRun)
        assertTrue("onDrained 仍调用", drained)
    }

    @Test
    fun `skipExitSave false - dispatchExitSave called and action runs`() = runTest(testDispatcher) {
        var saveDispatched = false
        var actionRun = false
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { saveDispatched = true },
            awaitProgressSaves = { },
            navigate = {},
            onDrained = {},
        )
        boundary.requestExitWithAction(
            action = { actionRun = true },
            skipExitSave = false,
        )
        runCurrent()
        advanceUntilIdle()
        assertTrue("skipExitSave=false → dispatchExitSave 调用", saveDispatched)
        assertTrue("action 执行", actionRun)
    }

    @Test
    fun `skipExitSave true - awaitProgressSaves still runs before action`() = runTest(testDispatcher) {
        var awaited = false
        var actionRun = false
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { },
            awaitProgressSaves = { awaited = true },
            navigate = {},
            onDrained = {},
        )
        boundary.requestExitWithAction(
            action = { actionRun = true },
            skipExitSave = true,
        )
        runCurrent()
        advanceUntilIdle()
        assertTrue("awaitProgressSaves 仍在 action 前运行（排空在途保存）", awaited)
        assertTrue("action 排空后执行", actionRun)
    }

    @Test
    fun `skipExitSave true - save failure prevents action and calls onFailure`() = runTest(testDispatcher) {
        var actionRun = false
        var failureReceived = false
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = { },
            awaitProgressSaves = { throw IllegalStateException("save failed") },
            navigate = {},
            onDrained = {},
        )
        boundary.requestExitWithAction(
            action = { actionRun = true },
            skipExitSave = true,
            onFailure = { failureReceived = true },
        )
        runCurrent()
        advanceUntilIdle()
        assertFalse("保存失败 → action 不执行", actionRun)
        assertTrue("onFailure 接收异常", failureReceived)
    }

    // ── skipPagedExitPersistOnUnmovedSession 纯判定 ──

    @Test
    fun `paged unmoved skip=true`() {
        assertTrue(skipPagedExitPersistOnUnmovedSession(2000, 2000))
    }

    @Test
    fun `paged moved skip=false`() {
        assertFalse(skipPagedExitPersistOnUnmovedSession(2000, 2600))
    }

    @Test
    fun `paged invalid anchor skip=false`() {
        assertFalse(skipPagedExitPersistOnUnmovedSession(Int.MIN_VALUE, 2000))
        assertFalse(skipPagedExitPersistOnUnmovedSession(-1, 2000))
    }

    // ── 真实 persistCurrentProgress paged 落库 ──

    @Test
    fun `paged exit persist dispatches engine page-start offset`() = runTest(testDispatcher) {
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = mockk<ReaderLoadedBook>(relaxed = true),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = true,
            pagedAbsOffset = anchorOffset,
            pagedSource = pagedSource,
            chapterIndex = 0,
            epubListState = LazyListState(0, 0),
            blockGlobalOffsets = listOf(0),
            chapterBase = 0,
            chapterBlocks = emptyList(),
            bookIndex = null,
            chapterStartOffsets = listOf(0),
            visiblePlainOffset = -1,
            markdownDocument = null,
            txtStreamingDocument = null,
            plainContent = content,
            sessionPositionGuard = null,
            onAction = { saved = it },
        )
        val entity = requireNotNull((saved as? ReaderAction.SaveProgress)?.progress)
        val offset = LocatorCodec.locatorFromProgress(entity.current_location_json)?.legacyOffset
        assertEquals(anchorOffset, offset)
    }
}
