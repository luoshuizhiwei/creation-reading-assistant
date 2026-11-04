package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 197-r1：分页会话「无位移退出不重复落库」判定（[skipPagedExitPersistOnUnmovedSession]）
 * 与真实分页退出落库 [persistCurrentProgress]（pagerEngineOn=true）的定向回归。
 *
 * 覆盖 197-v1 缺口：默认 paged+on 下 ReaderScreen.persistOnLeave 的分页分支此前恒
 * skip=false → 普通 S0 → 临时 T1 转换时 S0 dispose 落库照常派发，S0 采样在临时转换
 * 边界被覆盖。修复后：
 *
 * 1) 未移动（current == 会话锚）→ 跳过退出落库（S0 采样在临时转换边界冻结）；
 * 2) 真实移动（current != 会话锚）→ 不跳过，退出落库携带引擎当前页首（最新合法进度不丢失）；
 * 3) 锚缺失/无效（Int.MIN_VALUE / 负值）→ fail-closed 照常落库；
 * 4) 分页落库真实派发 SaveProgress 且 absoluteOffset == 引擎页首（既有合同不因本修复改变）。
 *
 * 判定函数为生产 ReaderScreen.persistOnLeave 分页分支实际调用的同一实现（非测试镜像）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPagedSessionAnchorDecisionTest {

    private val bookId = "b_paged_session_anchor"

    /** TXT 分页正文：40 单元 × 68 字符；会话锚 = 引擎恢复页首 2000。 */
    private val totalChars = 40 * 68
    private val content = "x".repeat(totalChars)
    private val anchorAbsOffset = 2000

    private val pagedSource: PagedChapterSource = mockk {
        every { chapterIndexFor(any()) } returns 0
        every { chapterStartAbs(any()) } returns 0
    }

    private fun savedLegacyOffset(action: ReaderAction?): Int? {
        val entity = (action as? ReaderAction.SaveProgress)?.progress ?: return null
        return LocatorCodec.locatorFromProgress(entity.current_location_json ?: "")?.legacyOffset
    }

    /** 走真实生产入口 [persistCurrentProgress] 的分页退出落库，返回捕获到的动作。 */
    private fun persistPagedExit(pagedAbsOffset: Int): ReaderAction? {
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = mockk<ReaderLoadedBook>(relaxed = true),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = true,
            pagedAbsOffset = pagedAbsOffset,
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
        return saved
    }

    private fun unitsFor(offset: Int): List<ReadingUnit> =
        (0 until 40).map { i -> ReadingUnit(i, 0, "", i * 68, 68) }.also { units ->
            check(offset < units.sumOf { it.charCount })
        }

    @Test
    fun `unmoved paged session skips the exit persist so S0 stays frozen across the temporary transition`() {
        // 会话锚 = 恢复落位后首个有效页首 2000；用户未翻页 → current == anchor → 跳过。
        assertTrue(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = anchorAbsOffset,
                currentAbsOffset = anchorAbsOffset,
            ),
        )
        // 同一判定在锚等于恢复位置的其他取值下也成立（对称面：滚动 TXT 走
        // ReaderSessionProgressGuardTest，分页走本判定）。
        assertTrue(skipPagedExitPersistOnUnmovedSession(0, 0))
    }

    @Test
    fun `moved paged session does not skip so the latest legal progress is persisted`() {
        // 用户从锚 2000 真实翻页到 2600 → 不跳过 → S0 dispose 落库派发 2600。
        assertFalse(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = anchorAbsOffset,
                currentAbsOffset = 2600,
            ),
        )
        // 真实分页退出落库：absoluteOffset == 引擎当前页首 2600（最新合法进度不丢失）。
        val units = unitsFor(2600)
        check(2600 < units.sumOf { it.charCount })
        val saved = persistPagedExit(2600)
        assertEquals(2600, savedLegacyOffset(saved))
    }

    @Test
    fun `missing or invalid anchor never skips (fail-closed)`() {
        // 锚尚未建立（Int.MIN_VALUE）→ 不跳过，照常落库。
        assertFalse(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = Int.MIN_VALUE,
                currentAbsOffset = 2000,
            ),
        )
        // 负值页首（引擎未上报）→ 不跳过。
        assertFalse(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = -1,
                currentAbsOffset = -1,
            ),
        )
        // 当前页首无效 → 不跳过（fail-closed）。
        assertFalse(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = anchorAbsOffset,
                currentAbsOffset = -1,
            ),
        )
    }

    @Test
    fun `paged exit persist dispatches the engine page-start offset (existing contract unchanged)`() {
        // 普通退出（非临时）既有合同：paged+on 落库 absoluteOffset == 引擎页首。
        // 本修复只在该落库「无新进度可写」时跳过，不改变派发内容的构造。
        val saved = persistPagedExit(anchorAbsOffset)
        assertEquals(anchorAbsOffset, savedLegacyOffset(saved))
    }

    @Test
    fun `chapter jump keeps the exit persist (anchor stays at the old chapter by design)`() {
        // 锚建立后不随跳章更新：跳章后 current != anchor → 照常落库（保守：新章位置必落库）。
        assertFalse(
            skipPagedExitPersistOnUnmovedSession(
                anchorAbsOffset = anchorAbsOffset,
                currentAbsOffset = 2600,
            ),
        )
    }
}
