package com.creationreadingassistant.ui.screen.reader

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.screen.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiAssistSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiExplainSheet
import com.creationreadingassistant.ui.screen.reader.sheets.BookInfoSheet
import com.creationreadingassistant.ui.screen.reader.sheets.InspirationSheet
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ProgressSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SearchSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SettingsSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ThemeSheet
import com.creationreadingassistant.ui.screen.reader.sheets.TocSheet
import com.creationreadingassistant.ui.screen.reader.sheets.readerTocEntries
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel

/**
 * 阅读器底部弹层分发所需的只读展示数据。
 *
 * 从 [ReaderScreen] 主函数局部状态中提取，供 [ReaderSheetHost] 在各 sheet 分支内转发给
 * 具体 Sheet 组件。仅承载「读」数据；任何对主函数可变状态的写入都通过
 * [ReaderSheetHostCallbacks] 回调上抛，避免跨文件扩散状态耦合。
 */
internal data class ReaderSheetHostState(
    val epubBook: EpubBook?,
    val epubDocument: ReaderDocument?,
    val txtStreamingDocument: PlainTextDocument?,
    val plainContent: String,
    val chapterIndex: Int,
    val txtChapterIndex: Int,
    val txtChapterTitles: List<String>,
    val currentChapterTitle: String,
    val progressPercent: Float,
    val bookTitle: String,
    val bookAuthor: String?,
    val bookOriginalFile: String?,
    val selectedText: String,
    val contentText: String,
    val readerSettings: ReaderSettings,
    val activeReadingMs: Long,
    val savedBookReadingMs: Long,
    val estimatedRemainingMs: Long,
    val readerSpeed: Int,
    val inspirationsCount: Int,
    val bookmarksCount: Int,
    val documentWordCount: Int,
    val isTxt: Boolean,
    val searchQuery: String,
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val bookIndex: BookIndex?,
    val txtTocRuleId: String,
    val txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    val recentChapters: List<Int>,
    val appDark: Boolean,
)

/**
 * 阅读器底部弹层分发所需的回调集合。
 *
 * 每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」。复杂分支
 * （目录跳章、TXT 规则切换、搜索跳转、导出书摘、保存灵感等）的完整逻辑体仍留在
 * [ReaderScreen] 主函数中定义，本接口只暴露其触发入口，从而保持 [ReaderSheetHost]
 * 为纯分发层。
 */
internal data class ReaderSheetHostCallbacks(
    val onDismiss: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenBookInfo: () -> Unit,
    val goToChapter: (Int) -> Unit,
    val seekToPercent: (Float) -> Unit,
    val jumpToPlainOffset: (Int) -> Unit,
    val showNotice: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSelectedText: () -> Unit,
    val onPickChapter: (Int) -> Unit,
    val onTxtRule: (String) -> Unit,
    val onSearchJump: (BookSearchResult) -> Unit,
    val onJumpToHighlight: (String) -> Unit,
    val onExportHighlights: () -> Unit,
    val onSaveAiExplainInspiration: (body: String, tags: List<String>, categoryIds: List<String>) -> Unit,
    val onSaveInspiration: (title: String, body: String, tags: List<String>, categoryIds: List<String>) -> Unit,
    val onCreateCategory: (String) -> String,
    val onCreateTag: (String) -> String,
)

/**
 * 阅读器底部弹层宿主：按 [sheet] 当前值分发到对应 Sheet 组件。
 *
 * 仅做路由分发与参数转发，不持有任何可变状态；所有展示数据来自 [state]，
 * 所有副作用通过 [sheetCallbacks] 上抛给 [ReaderScreen] 主函数。原逻辑 1:1 搬运自
 * ReaderScreen 主函数的 `sheet?.let { type -> GlassModalBottomSheet(...) { when (type) {...} } }` 块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSheetHost(
    sheet: ReaderSheet?,
    sheetState: SheetState,
    paper: ReaderPaperPalette,
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
    settingsVm: SettingsViewModel,
    state: ReaderSheetHostState,
    sheetCallbacks: ReaderSheetHostCallbacks,
) {
    val bid = inputs.bookId ?: ""
    sheet?.let { type ->
        GlassModalBottomSheet(
            onDismissRequest = sheetCallbacks.onDismiss,
            sheetState = sheetState,
            containerColor = paper.bg,
            shape = LocalComponentSpec.current.sheetShape,
            dragHandle = { SheetHandle() },
        ) {
            ReaderPaperTheme(paper) {
            when (type) {
                ReaderSheet.TOC -> TocSheet(
                    entries = readerTocEntries(
                        titles = state.epubBook?.chapters?.map { it.title } ?: state.txtChapterTitles,
                        current = if (state.epubBook != null) state.chapterIndex else state.txtChapterIndex,
                        recent = state.recentChapters,
                    ),
                    current = if (state.epubBook != null) state.chapterIndex else state.txtChapterIndex,
                    totalChapters = state.epubBook?.chapters?.size ?: state.txtChapterTitles.size,
                    onPick = sheetCallbacks.onPickChapter,
                    txtRules = if (state.isTxt) TxtChapterDetector.rules else emptyList(),
                    selectedTxtRule = state.txtTocRuleId,
                    txtRulePreviews = state.txtRulePreviews,
                    onTxtRule = sheetCallbacks.onTxtRule,
                )

                ReaderSheet.NOTES -> NotesSheet(
                    paper = paper,
                    highlights = inputs.highlights,
                    notes = inputs.notes,
                    inspirations = inputs.inspirations,
                    onAddBookmark = {
                        callbacks.onAction(
                            ReaderAction.AddBookmark(
                                bid,
                                state.progressPercent.toInt(),
                                state.currentChapterTitle.ifBlank { "正文" },
                            ),
                        )
                        sheetCallbacks.showNotice("已添加书签")
                    },
                    onDeleteHighlight = { h ->
                        callbacks.onAction(ReaderAction.DeleteHighlight(h.id))
                    },
                    onDeleteNote = { n ->
                        callbacks.onAction(ReaderAction.DeleteNote(n.id))
                    },
                    onChangeHighlightColor = { h, c ->
                        callbacks.onAction(ReaderAction.UpdateHighlightColor(h.id, c))
                    },
                    onEditHighlightNote = { h, note ->
                        callbacks.onAction(ReaderAction.UpdateHighlightNote(h.id, note))
                    },
                    onHighlightToNote = { h ->
                        callbacks.onAction(ReaderAction.ConvertHighlightToNote(h.id))
                        sheetCallbacks.showNotice("已转为笔记")
                    },
                    onHighlightToInspiration = { h ->
                        callbacks.onAction(ReaderAction.ConvertHighlightToInspiration(h.id))
                        sheetCallbacks.showNotice("已转为灵感")
                    },
                    onJumpToHighlight = { h -> sheetCallbacks.onJumpToHighlight(h.id) },
                    onExportHighlights = sheetCallbacks.onExportHighlights,
                )

                ReaderSheet.AI_ASSIST -> AiAssistSheet(
                    aiClient = callbacks.aiClient,
                    bookTitle = state.bookTitle,
                    chapterTitle = state.currentChapterTitle,
                    contextText = state.selectedText.ifBlank { state.contentText },
                )

                ReaderSheet.AI_EXPLAIN -> AiExplainSheet(
                    aiClient = callbacks.aiClient,
                    selectedText = state.selectedText,
                    bookTitle = state.bookTitle,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSaveInspiration = { body, tags, categoryIds ->
                        sheetCallbacks.onSaveAiExplainInspiration(body, tags, categoryIds)
                    },
                )

                ReaderSheet.INSPIRATION -> InspirationSheet(
                    bookTitle = state.bookTitle,
                    chapterTitle = state.currentChapterTitle,
                    excerpt = state.selectedText,
                    progressPercent = state.progressPercent,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSave = { title, body, tags, categoryIds ->
                        sheetCallbacks.onSaveInspiration(title, body, tags, categoryIds)
                    },
                )

                ReaderSheet.SETTINGS -> SettingsSheet(
                    paper = paper,
                    settings = state.readerSettings,
                    onSettingsChange = { updated -> settingsVm.updateReader { updated } },
                    onBookInfo = sheetCallbacks.onOpenBookInfo,
                )

                ReaderSheet.THEME -> ThemeSheet(
                    background = state.readerSettings.background,
                    appDark = state.appDark,
                    onBackground = { settingsVm.updateReader { copy(background = it) } },
                )

                ReaderSheet.PROGRESS -> ProgressSheet(
                    epubBook = state.epubBook,
                    chapterIndex = state.chapterIndex,
                    currentChapterTitle = state.currentChapterTitle,
                    progressPercent = state.progressPercent,
                    activeReadingMs = state.activeReadingMs,
                    readerSpeed = state.readerSpeed,
                    estimatedRemainingMs = state.estimatedRemainingMs,
                    savedBookReadingMs = state.savedBookReadingMs,
                    inspirationsCount = state.inspirationsCount,
                    bookmarksCount = state.bookmarksCount,
                    onChapter = { sheetCallbacks.goToChapter(it) },
                    onSeekPercent = { sheetCallbacks.seekToPercent(it) },
                    isTxt = state.isTxt,
                )

                ReaderSheet.SEARCH -> SearchSheet(
                    document = state.epubDocument,
                    txtDocument = state.txtStreamingDocument,
                    plainContent = state.plainContent,
                    chapterStartOffsets = state.chapterStartOffsets,
                    chapterTitles = state.chapterTitles,
                    totalChars = state.bookIndex?.totalChars
                        ?: state.txtStreamingDocument?.totalChars ?: 0,
                    isTxt = state.isTxt,
                    query = state.searchQuery,
                    onQueryChange = sheetCallbacks.onSearchQueryChange,
                    onJump = sheetCallbacks.onSearchJump,
                )

                ReaderSheet.BOOK_INFO -> BookInfoSheet(
                    bookTitle = state.bookTitle,
                    bookAuthor = state.bookAuthor,
                    bookFormat = if (state.epubBook != null) "EPUB" else "TXT",
                    chapterCount = state.epubBook?.chapters?.size ?: 0,
                    wordCount = state.documentWordCount,
                    currentChapterTitle = state.currentChapterTitle,
                    progressPercent = state.progressPercent,
                    activeReadingMs = state.activeReadingMs,
                    savedReadingMs = state.savedBookReadingMs,
                    sessionsCount = inputs.sessions.size,
                    sourceFile = state.bookOriginalFile,
                    onOpenSettings = sheetCallbacks.onOpenSettings,
                    onDelete = {
                        callbacks.onAction(ReaderAction.DeleteBook(bid))
                        callbacks.onBack()
                    },
                )
            }
            }
        }
    }
}
