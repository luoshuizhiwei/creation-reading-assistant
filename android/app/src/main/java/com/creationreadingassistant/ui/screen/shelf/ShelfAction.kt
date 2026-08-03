package com.creationreadingassistant.ui.screen.shelf

import android.net.Uri
import com.creationreadingassistant.data.local.entity.BookEntity

/**
 * Shelf 纯 Screen 到 Route 的交互协议（单向数据流）。
 *
 * Screen 只发 action，不调用 ViewModel / 不持有 navController / 不触发 Activity launcher。
 * 文件选择器 / 文件夹选择器 / 重选正文 / 换封面 四个 launcher 的权限请求由 Route
 * 使用自己的 requestXxx(id: String) + 自身 Launcher 实现，Screen 只发语义 action。
 */
internal sealed interface ShelfAction {
    // ========= 顶栏：搜索 / 导入 / 菜单 =========
    data object OpenOrganizer : ShelfAction
    data object OpenSearch : ShelfAction
    data object ExitSearch : ShelfAction
    data class UpdateQuery(val query: String) : ShelfAction
    data object TogglePageMenu : ShelfAction
    data object ClosePageMenu : ShelfAction
    data object EnterSelection : ShelfAction
    data object ExitSelection : ShelfAction
    data object OpenImportSource : ShelfAction
    data object OpenImportHistory : ShelfAction
    data object OpenDesktopBooks : ShelfAction

    // ========= 工具栏：排序 / 筛选 / 视图切换 =========
    data object OpenSortSheet : ShelfAction
    data class SelectSort(val sort: ShelfSortMode) : ShelfAction
    data object DismissSortSheet : ShelfAction
    data object OpenFilterPanel : ShelfAction
    data class SelectShelf(val id: String) : ShelfAction
    data class SelectCategory(val id: String) : ShelfAction
    data class SelectTag(val id: String) : ShelfAction
    data object DismissFilterPanel : ShelfAction
    data object ToggleViewMode : ShelfAction
    data class UpdateStatusFilter(val status: ShelfStatusFilter) : ShelfAction
    data object ResetAllFilters : ShelfAction

    // ========= 选择模式：全选 / 清空 / 切换单本 =========
    data object SelectAllVisible : ShelfAction
    data object ClearSelection : ShelfAction
    data class ToggleSelected(val bookId: String) : ShelfAction

    // ========= 单本书：打开 / 操作弹层 / 详情 =========
    data class OpenBook(val book: BookEntity) : ShelfAction
    data class ToggleActions(val bookId: String) : ShelfAction
    data class DismissActionSheet(val bookId: String) : ShelfAction
    data class ContinueFromActionSheet(val book: BookEntity) : ShelfAction
    data class DownloadBook(val bookId: String) : ShelfAction
    data class ReselectFile(val bookId: String) : ShelfAction
    data class OpenDetail(val bookId: String) : ShelfAction
    data object DismissDetailSheet : ShelfAction
    data class DeleteSingle(val bookId: String) : ShelfAction

    // ========= 批量操作栏 =========
    data class BatchAddToShelf(val ids: Set<String>) : ShelfAction
    data class BatchSetCategory(val ids: Set<String>) : ShelfAction
    data class BatchAddTag(val ids: Set<String>) : ShelfAction
    data class BatchDownload(val ids: Set<String>) : ShelfAction
    data class BatchClearCache(val ids: Set<String>) : ShelfAction
    data class BatchRequestDelete(val ids: Set<String>) : ShelfAction
    data class SelectBatchSheetKind(val kind: BatchSheetKind) : ShelfAction
    data object DismissBatchSheet : ShelfAction

    // ========= 分类学：批量分配 / 创建 / 移除标签 =========
    data class BatchApplyTaxonomy(val kind: BatchSheetKind, val id: String) : ShelfAction
    data class BatchRemoveTag(val bookIds: Set<String>, val tagId: String) : ShelfAction
    data class BatchCreateTaxonomy(val kind: BatchSheetKind, val name: String) : ShelfAction

    // ========= 详情弹层：分类学 / 元数据 / 封面 =========
    data class DetailRemoveShelf(val bookId: String, val shelfId: String) : ShelfAction
    data class DetailRemoveCategory(val bookId: String, val categoryId: String) : ShelfAction
    data class DetailRemoveTag(val bookId: String, val tagId: String) : ShelfAction
    data class DetailAddTag(val bookId: String, val tagId: String) : ShelfAction
    data class DetailUpdateBook(val bookId: String, val title: String, val author: String?, val description: String?) : ShelfAction
    data class DetailChangeCover(val bookId: String) : ShelfAction
    data class DetailChangeTextCover(val bookId: String) : ShelfAction
    data class DetailResetCover(val bookId: String) : ShelfAction
    data class DetailContinue(val book: BookEntity) : ShelfAction
    data class DetailDownload(val bookId: String) : ShelfAction
    data class DetailDelete(val bookId: String) : ShelfAction

    // ========= 导入 / 历史 =========
    data class ImportFiles(val uris: List<Uri>) : ShelfAction
    data class ImportFolder(val uri: Uri) : ShelfAction
    data object RetryFailedImports : ShelfAction
    data object DismissImportBatchSummary : ShelfAction
    data object ClearImportHistory : ShelfAction
    data object DismissImportSource : ShelfAction
    data object DismissImportHistory : ShelfAction
    data object DismissDesktopBooks : ShelfAction
    data class DesktopDownloadBook(val bookId: String) : ShelfAction

    // ========= 删除确认弹层 =========
    data object CancelDeleteConfirm : ShelfAction
    data class ConfirmDelete(val ids: List<String>) : ShelfAction
    data object UndoLastDelete : ShelfAction

    // ========= launcher 回传：Route 自己处理 activity result =========
    /** 文件选择器回传；Screen 不持有 launcher，仅作为 action 预留接口（Route 内部直连 ActivityResultLauncher）。 */
    data class ReselectFileResult(val bookId: String, val uri: Uri) : ShelfAction
    data class CoverChangeResult(val bookId: String, val uri: Uri) : ShelfAction

    // ========= 下拉刷新 =========
    data object TriggerRefresh : ShelfAction
}
