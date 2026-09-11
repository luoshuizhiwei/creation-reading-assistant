package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1-I 重写：临时查阅「可返回目标」的实时派生回归。
 *
 * 原测试（`assertTrue(true && true)`、复述生产 `enabled` / `when` 表达式、`assertFalse(!true)`）
 * 是伪测试 —— 断言的是测试自己写下的布尔字面量，生产代码改坏也不会失败。这里改为直接调用
 * 生产函数 [hasReturnableTemporaryTarget]，并用真实 [TemporaryReadingNavigationViewModel]
 * 驱动状态，验证「普通位置不算待返回目标」「临时入栈后为真」「LIFO 返回后为假」。
 */
class ReaderTemporaryInspectionUiTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    @Test
    fun `returnable target tracks real coordinator stack depth`() {
        val vm = TemporaryReadingNavigationViewModel()

        assertFalse(hasReturnableTemporaryTarget(vm.state.value))

        vm.recordNormalReading(target("book-a", 100))
        // 普通阅读位置本身不构成「待返回的临时目标」。
        assertFalse(hasReturnableTemporaryTarget(vm.state.value))

        vm.beginTemporaryInspection(target("book-b", 200))
        assertTrue(hasReturnableTemporaryTarget(vm.state.value))

        vm.returnFromTemporaryInspection()
        // 逐层返回后按钮应立即消失。
        assertFalse(hasReturnableTemporaryTarget(vm.state.value))
    }

    @Test
    fun `nested temporary inspection keeps returnable target until last level`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        vm.beginTemporaryInspection(target("book-b", 200))
        vm.beginTemporaryInspection(target("book-c", 300))

        assertTrue(hasReturnableTemporaryTarget(vm.state.value))

        vm.returnFromTemporaryInspection()
        assertTrue(hasReturnableTemporaryTarget(vm.state.value))

        vm.returnFromTemporaryInspection()
        assertFalse(hasReturnableTemporaryTarget(vm.state.value))
    }

    @Test
    fun `missing coordinator has no returnable target`() {
        assertFalse(hasReturnableTemporaryTarget(null))
    }

    // ── UI 状态袋字段透传（构造生产 data class，非伪断言）────────────────────

    @Test
    fun `ReaderInteractionLayerState carries temporary inspection fields`() {
        val state = ReaderInteractionLayerState(
            controlsVisible = true,
            selectedText = "",
            showColorRow = false,
            canCreateReplaceRule = false,
            showTts = false,
            showReaderOverflow = false,
            autoPagingActive = false,
            autoPageSpeed = 1,
            progressPercent = 50f,
            bookTitle = "Test",
            currentChapterTitle = "Ch1",
            chapterProgress = 50f,
            isFirstChapter = false,
            isLastChapter = false,
            isEpub = false,
            isMarkdown = false,
            isLoading = false,
            error = null,
            showProgressBar = true,
            paper = createMinimalPaperPalette(),
            temporaryInspection = true,
            hasReturnableTarget = true,
        )

        assertTrue(state.temporaryInspection)
        assertTrue(state.hasReturnableTarget)
    }

    @Test
    fun `ReaderInteractionLayerState defaults temporary fields to false`() {
        val state = ReaderInteractionLayerState(
            controlsVisible = true,
            selectedText = "",
            showColorRow = false,
            canCreateReplaceRule = false,
            showTts = false,
            showReaderOverflow = false,
            autoPagingActive = false,
            autoPageSpeed = 1,
            progressPercent = 50f,
            bookTitle = "Test",
            currentChapterTitle = "Ch1",
            chapterProgress = 50f,
            isFirstChapter = false,
            isLastChapter = false,
            isEpub = false,
            isMarkdown = false,
            isLoading = false,
            error = null,
            showProgressBar = true,
            paper = createMinimalPaperPalette(),
        )

        assertFalse(state.temporaryInspection)
        assertFalse(state.hasReturnableTarget)
    }
}

// ── 测试辅助：最小化 PaperPalette ──────────────────────────────────────────

private fun createMinimalPaperPalette() = com.creationreadingassistant.ui.theme.ReaderPaperPalette(
    key = "white",
    bg = androidx.compose.ui.graphics.Color.White,
    fg = androidx.compose.ui.graphics.Color.Black,
    fgMuted = androidx.compose.ui.graphics.Color.Gray,
    accent = androidx.compose.ui.graphics.Color.Blue,
    outlineVariant = androidx.compose.ui.graphics.Color.LightGray,
    outline = androidx.compose.ui.graphics.Color.DarkGray,
    panel = androidx.compose.ui.graphics.Color.LightGray,
    panelStrong = androidx.compose.ui.graphics.Color.DarkGray,
    onAccent = androidx.compose.ui.graphics.Color.White,
    isLight = true,
    highlightColors = listOf(
        androidx.compose.ui.graphics.Color.Yellow,
        androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.Green,
        androidx.compose.ui.graphics.Color.Blue,
        androidx.compose.ui.graphics.Color.Magenta
    )
)
