package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S07-F2：正文宿主「选区清除」回调的接线契约。
 *
 * 分页正文里所有「撤掉选区」的入口（单击撤选、双击撤选、滚动单元回收，
 * 以及提交翻页时的 onPrevious / onNext / requestManualReveal）都只发一个
 * **空文本** 的 `onSelect`。因此空文本必须映射到唯一的完整清除动作
 * [ReaderAction.ClearSelection]，而不是只复位四个字段、把 `showColorRow`
 * 留在打开状态的 `SetSelectedText`。
 *
 * 这里只测接线（纯 lambda，无 Android 依赖），不挂载 Compose 树。
 */
class ReaderContentHostSelectionCallbacksTest {

    private fun callbacks(recorded: MutableList<ReaderAction>): ReaderContentHostCallbacks =
        buildReaderContentHostCallbacks(
            onAction = { recorded += it },
            onPagedAbsOffsetChange = {},
            onPagedPercentChange = {},
            onPendingInitialPositionChange = {},
            onAutoPagingActiveChange = {},
            goToChapter = {},
            syncPagedChapter = {},
            showNotice = {},
            onSearchScrollFocusRequestConsumed = {},
            onPersistProgress = {},
        )

    @Test
    fun `an empty selection signal maps to the single full clear action`() {
        val recorded = mutableListOf<ReaderAction>()

        callbacks(recorded).onSelect("", -1, -1, -1)

        assertEquals(
            "空选区必须收敛到 ClearSelection（五字段一起复位）",
            listOf(ReaderAction.ClearSelection),
            recorded,
        )
    }

    @Test
    fun `a blank but non empty string still counts as a clear`() {
        // BasicTextField 上报的空选区可能是空白串而非 ""；两者都必须走同一条清除语义，
        // 否则「选了空白字符再撤选」会留下打开的色板行。
        val recorded = mutableListOf<ReaderAction>()

        callbacks(recorded).onSelect("   ", -1, -1, -1)

        assertEquals(listOf(ReaderAction.ClearSelection), recorded)
    }

    @Test
    fun `a real selection still maps to SetSelectedText with all four coordinates`() {
        val recorded = mutableListOf<ReaderAction>()

        // onSelect 的入参顺序是 (text, globalOffset, rangeStart, sourceEnd)；
        // SetSelectedText 的顺序是 (text, rangeStart, globalOffset, sourceEnd)。
        // 这条用例同时钉住这次换位，避免修复清除分支时把选择分支一起改错。
        callbacks(recorded).onSelect("一期一会", -1, 120, 124)

        assertEquals(
            listOf(ReaderAction.SetSelectedText("一期一会", 120, -1, 124)),
            recorded,
        )
    }

    @Test
    fun `an epub style selection keeps its global offset`() {
        val recorded = mutableListOf<ReaderAction>()

        callbacks(recorded).onSelect("一期一会", 500, -1, 504)

        assertEquals(
            listOf(ReaderAction.SetSelectedText("一期一会", -1, 500, 504)),
            recorded,
        )
    }
}
