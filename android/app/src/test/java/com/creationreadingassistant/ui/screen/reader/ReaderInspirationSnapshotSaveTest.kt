package com.creationreadingassistant.ui.screen.reader

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.withSelectionCleared
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderInspirationSnapshotSaveTest {
    private fun callbacks(actions: MutableList<ReaderAction>, targets: MutableList<String?>) =
        buildReaderSheetHostCallbacks(
            epubBook = null, markdownDocument = null, txtChapters = emptyList(),
            visiblePlainOffset = 0, textContent = null, txtStreamingDocument = null,
            bid = "controlled-book", bookTitle = "测试 TXT", highlights = emptyList(),
            context = mockk<Context>(), selectedText = "",
            currentChapterTitle = "章节", progressPercent = 10f, bookAuthor = null,
            recentChapters = mutableStateListOf(), pagedJumpRequest = mutableStateOf<Int?>(null),
            onTxtTocRuleIdChange = {}, onPendingTxtRuleAnchorOffsetChange = {},
            onPendingHighlightIdChange = { targets.add(it) }, goToChapter = {}, seekToPercent = {},
            jumpToPlainOffset = {}, showNotice = {}, onAction = { actions.add(it) },
            onCancelTxtScan = {}, onPersistProgress = {}, onSettingsScopeChange = {},
            computeLocatorJson = { error("must never read the cleared live selection locator") },
        )

    private fun assertSavedSnapshot(aiExplain: Boolean) {
        val actions = mutableListOf<ReaderAction>()
        val captured = ReaderScreenState(
            selectedText = "受控摘录", selectedRangeStart = 42,
            selectedGlobalOffset = 42, selectedSourceEnd = 46,
        ).withSelectionCaptured().withSelectionCleared()
        assertEquals("", captured.selectedText)
        val snapshot = captured.selectionSnapshot!!
        val locator = computeLocatorJsonForSnapshot(null, emptyList(), snapshot)
        val cb = callbacks(actions, mutableListOf())
        if (aiExplain) {
            cb.onSaveAiExplainInspiration("解读正文", snapshot.text, locator, emptyList(), emptyList())
        } else {
            cb.onSaveInspiration("灵感", "灵感正文", snapshot.text, locator, emptyList(), emptyList())
        }
        val saved = actions.filterIsInstance<ReaderAction.SaveInspiration>().single().inspiration
        val source = Json.decodeFromString(InspirationPayloadData.serializer(), saved.payload!!).source!!
        assertEquals(snapshot.text, source.excerpt)
        assertEquals(locator, source.locatorJson)
        assertEquals(ReaderAction.CloseSheet, actions.last())
    }

    @Test fun `AI explanation saves the opening excerpt and locator after live selection clears`() {
        assertSavedSnapshot(aiExplain = true)
    }

    @Test fun `inspiration saves the opening excerpt and locator after live selection clears`() {
        assertSavedSnapshot(aiExplain = false)
    }

    @Test fun `typed note navigation closes the sheet without converting to bookmark`() {
        val actions = mutableListOf<ReaderAction>()
        val targets = mutableListOf<String?>()
        callbacks(actions, targets).onJumpToTypedAnnotation("note:controlled-id")
        assertEquals(listOf("note:controlled-id"), targets)
        assertEquals(listOf(ReaderAction.CloseSheet), actions)
    }

    @Test fun `reopening without a selection cannot inherit a released snapshot`() {
        val state = ReaderScreenState(selectedText = "受控摘录", selectedRangeStart = 42)
            .withSelectionCaptured().withSelectionSnapshotReleased().withSelectionCleared()
            .withSelectionCaptured()
        assertNull(state.selectionSnapshot)
        assertFalse(state.selectedText.isNotEmpty())
    }
}
