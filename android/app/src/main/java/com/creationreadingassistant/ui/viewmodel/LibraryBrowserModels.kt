package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.feature.library.LibraryEntry
import com.creationreadingassistant.feature.library.LibrarySortMode
import com.creationreadingassistant.feature.library.RecognitionSummary
import com.creationreadingassistant.feature.library.RecognizedBookCandidate

/** One segment of the breadcrumb. The root segment carries a `null` document ID. */
data class LibraryCrumb(
    val documentId: String?,
    val displayName: String,
)

/**
 * A selectable file row. [shelfMatch] carries the layered duplicate/update verdict from
 * [LibraryBrowserPolicy.shelfMatch]; `null` means there is no evidence the file is on the shelf.
 */
data class LibraryFileRow(
    val entry: LibraryEntry,
    val shelfMatch: LibraryShelfMatch? = null,
) {
    /** 确认已入架（精确来源/同内容/内容有更新）时才有可打开的书籍；弱判定不得猜 bookId。 */
    val openableBookId: String?
        get() = shelfMatch?.bookId
}

data class LibraryBrowserUiState(
    val hasRoot: Boolean = false,
    val rootName: String = "",
    val crumbs: List<LibraryCrumb> = emptyList(),
    val directories: List<LibraryEntry> = emptyList(),
    val files: List<LibraryFileRow> = emptyList(),
    val selection: Set<String> = emptySet(),
    val query: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.NAME,
    val isLoading: Boolean = false,
    val truncated: Boolean = false,
    val unreadable: Boolean = false,
) {
    val selectedFiles: List<LibraryEntry>
        get() = files.filter { it.entry.documentId in selection }.map { it.entry }

    val allFilesSelected: Boolean
        get() = files.isNotEmpty() && files.all { it.entry.documentId in selection }

    val inSelectionMode: Boolean
        get() = selection.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && directories.isEmpty() && files.isEmpty()
}

/** A recognition row plus the same layered shelf match used by the browser list. */
data class LibraryRecognitionRow(
    val candidate: RecognizedBookCandidate,
    val shelfMatch: LibraryShelfMatch? = null,
) {
    val key: String get() = candidate.uri.toString()
}

/** Tabs on the recognition result page (roadmap 6.3). */
enum class RecognitionFilter(val label: String) {
    RECOMMENDED("推荐"),
    ALL("全部"),
    IN_SHELF("已入架"),
    REJECTED("未识别"),
}

/**
 * 候选文件与书架的匹配结论。判据决定文案（方案 §5.6）：
 *
 * - [LibraryShelfMatchKind.EXACT_SOURCE] / [LibraryShelfMatchKind.SAME_CONTENT]：
 *   有精确来源位置或内容指纹证据，UI 呈现为「已在书架」（确认口径）；
 * - [LibraryShelfMatchKind.CONTENT_UPDATED]：来源位置一致但大小/时间相对导入基线变化，
 *   呈现为「内容有更新」；
 * - [LibraryShelfMatchKind.POSSIBLE] / [LibraryShelfMatchKind.NAME_ONLY]：只是弱证据，
 *   UI 必须保留「疑似」措辞，不得写成事实。
 *
 * [bookId] 只在判据足以锚定到具体书籍时非空（①②④），此时目录页允许单击打开该书；
 * 弱判定一律为 null，绝不猜 bookId。
 */
enum class LibraryShelfMatchKind(val priority: Int) {
    EXACT_SOURCE(0),
    CONTENT_UPDATED(1),
    SAME_CONTENT(2),
    POSSIBLE(3),
    NAME_ONLY(4),
}

data class LibraryShelfMatch(
    val kind: LibraryShelfMatchKind,
    val bookId: String?,
)

data class LibraryRecognitionUiState(
    val isRunning: Boolean = false,
    val finished: Boolean = false,
    val summary: RecognitionSummary? = null,
    val rows: List<LibraryRecognitionRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val warnings: List<String> = emptyList(),
) {
    /** `null` progress means discovery has not reported a candidate total yet. */
    val processedRatio: Float?
        get() {
            val current = summary ?: return null
            return if (current.discovered > 0) current.processed.toFloat() / current.discovered else null
        }
}

/** One-shot messages for the snackbar. */
enum class LibraryMessage(val text: String) {
    ROOT_SAVED("书籍目录已设置"),
    ROOT_CLEARED("已移除书籍目录配置，书架内已导入书籍不受影响"),
    ROOT_UNREADABLE("无法访问原书籍目录，请重新授权"),
    NOTHING_SELECTED("请先选择要加入书架的文件"),
    IMPORT_STARTED("已提交加入书架"),
}
