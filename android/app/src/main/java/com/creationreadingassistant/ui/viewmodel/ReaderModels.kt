package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.ui.screen.reader.ReaderSheet

/**
 * 阅读器状态 / 动作 / 结果模型。
 *
 * 从 [ReaderDocumentLoader] 文件拆出：文档加载器只保留 IO 与资源所有权职责，
 * 这些与加载器无关的 UI 状态模型集中在此文件。
 */

data class ReaderUiState(
    val requestedBookId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadedBook: ReaderLoadedBook? = null,
) {
    val isReady: Boolean
        get() = loadedBook != null && !isLoading && errorMessage == null
}

// ── 章节加载结果 ─────────────────────────────────────────────
sealed interface ChapterLoadResult {
    data class Loading(val bookId: String, val chapterIndex: Int) : ChapterLoadResult
    data class Loaded(val bookId: String, val chapterIndex: Int, val blocks: List<DocBlock>) : ChapterLoadResult
    data class Error(val bookId: String, val chapterIndex: Int, val message: String) : ChapterLoadResult
}

// ── TXT 规则扫描结果 ─────────────────────────────────────────
data class TxtRuleScanResult(
    val ruleId: String,
    val fileIndex: TxtFileIndex? = null,
    val document: PlainTextDocument? = null,
    val error: String? = null,
)

sealed interface ReaderAction {
    // ── 文档协调 ──
    data class OpenBook(val bookId: String) : ReaderAction
    data object Retry : ReaderAction
    data class LoadChapter(val bookId: String, val chapterIndex: Int) : ReaderAction
    data class ScanTxtTocRule(val filePath: String, val ruleId: String) : ReaderAction

    // ── UI 状态变更 ──
    data class ToggleControls(val visible: Boolean? = null) : ReaderAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderAction
    data object CloseSheet : ReaderAction
    data class SetSelectedText(val text: String, val rangeStart: Int, val globalOffset: Int) : ReaderAction
    data object ClearSelection : ReaderAction
    data class SetShowTts(val show: Boolean) : ReaderAction
    data class SetSearchQuery(val query: String) : ReaderAction
    data class SetShowOverflow(val show: Boolean) : ReaderAction
    data class SetNoteOpen(val open: Boolean) : ReaderAction
    data class SetNoteBody(val body: String) : ReaderAction
    data object ToggleColorRow : ReaderAction

    // ── 数据写入 ──
    data class SaveHighlight(val highlight: HighlightEntity) : ReaderAction
    data class DeleteHighlight(val highlightId: String) : ReaderAction
    data class UpdateHighlightColor(val highlightId: String, val color: String) : ReaderAction
    data class UpdateHighlightNote(val highlightId: String, val note: String) : ReaderAction
    data class SaveNote(val note: NoteEntity) : ReaderAction
    data class DeleteNote(val noteId: String) : ReaderAction
    /**
     * 新增书签。统一携带 v2 locator（[locatorJson]）与绝对偏移 / 章节内偏移，
     * 使书签与高亮、笔记、搜索跳转共用同一套 locator 解析（SE4）。
     * [offset] 保留为 [progress_percent] 兜底值（兼容性 / 无 locator 旧书签）。
     */
    data class AddBookmark(
        val bookId: String,
        val offset: Int,
        val title: String,
        val absOffset: Int = -1,
        val chapterIndex: Int = -1,
        val charOffset: Int = -1,
        val locatorJson: String? = null,
    ) : ReaderAction
    data class ConvertHighlightToNote(val highlightId: String) : ReaderAction
    data class ConvertHighlightToInspiration(val highlightId: String) : ReaderAction
    data class SaveInspiration(val inspiration: InspirationEntity) : ReaderAction
    data class CreateCategory(val name: String) : ReaderAction
    data class CreateTag(val name: String) : ReaderAction
    data class SaveProgress(val progress: ReadingProgressEntity) : ReaderAction
    data class SaveEpubProgress(
        val bookId: String,
        val chapterIndex: Int,
        val percent: Float,
        val offsetInChapter: Int = 0,
        val absoluteOffset: Int = -1,
    ) : ReaderAction
    data class DeleteBook(val bookId: String) : ReaderAction

    // ── 设置 ──
    data class LoadTxtTocRule(val bookId: String) : ReaderAction
    data class SaveTxtTocRule(val bookId: String, val ruleId: String) : ReaderAction
    data class SaveTtsResume(val bookId: String, val chapterIndex: Int, val offset: Int) : ReaderAction
}
