package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem

/**
 * Shelf 纯 Screen 层状态。
 *
 * 将搜索、筛选、排序、选择模式、弹层开关 **集中声明**——Pure Screen 渲染纯数据，
 * 不做 remember mutableStateOf。
 *
 * 数据源说明：
 *  - `library` / `activity` / `auxiliary` 来自 ViewModel 的源状态（纯搬运）
 *  - `shelfBooks`：ViewModel 已经排序完成的 ShelfBookItem（避免 Screen 自己做排序）
 *  - `filteredBookIds`：Route 层通过 TaxonomyViewModel 计算的交集集合（Screen 不持有）
 *  - `isRefreshing`：下拉刷新状态
 *  - 其他字段：搜索/筛选/排序/选择/弹层状态
 */
internal data class ShelfUiState(
    // ========= 源数据：来自 ShelfViewModel =========
    val library: com.creationreadingassistant.ui.viewmodel.ShelfLibraryState = com.creationreadingassistant.ui.viewmodel.ShelfLibraryState(),
    val activity: com.creationreadingassistant.ui.viewmodel.ShelfActivityState = com.creationreadingassistant.ui.viewmodel.ShelfActivityState(),
    val auxiliary: com.creationreadingassistant.ui.viewmodel.ShelfAuxiliaryState = com.creationreadingassistant.ui.viewmodel.ShelfAuxiliaryState(),
    val shelfBooks: List<ShelfBookItem> = emptyList(),
    val isRefreshing: Boolean = false,

    // ========= 视图 / 偏好（由 Route 初始化 + 持久化回写） =========
    val query: String = "",
    val debouncedQuery: String = "",
    val viewMode: ShelfViewMode = ShelfViewMode.GRID,
    val sortMode: ShelfSortMode = ShelfSortMode.RECENT,
    val statusFilter: ShelfStatusFilter = ShelfStatusFilter.ALL,
    /** 格式筛选："" = 全部；epub / txt / md。 */
    val formatFilter: String = "",

    // ========= 顶栏：搜索 / 多选 / 菜单 =========
    val searchActive: Boolean = false,
    val showPageMenu: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),

    // ========= 分类学筛选：书单 / 分类 / 标签 =========
    val selectedShelfId: String = "",
    val selectedCategoryId: String = "",
    val selectedTagIds: Set<String> = emptySet(),
    val filteredBookIds: Set<String>? = null,

    // ========= 首屏加载骨架 =========
    val showSkeleton: Boolean = true,

    // ========= 弹层：BottomSheet / 对话框 =========
    val showSortSheet: Boolean = false,
    val showFilterPanel: Boolean = false,
    val showImportHistory: Boolean = false,
    val showImportSource: Boolean = false,
    val showDesktopBooks: Boolean = false,
    val batchSheet: BatchSheetKind? = null,
    val detailBookId: String? = null,
    val actionBookId: String? = null,
    val confirmDeleteIds: List<String>? = null,
) {
    /** 筛选后的书（纯函数，放在 data class 里便于 Screen + 测试复用）。 */
    val filtered: List<ShelfBookItem> by lazy(LazyThreadSafetyMode.NONE) {
        filterItems(
            items = shelfBooks,
            query = debouncedQuery,
            statusFilter = statusFilter,
            formatFilter = formatFilter,
            progressById = library.progressById,
            allowedBookIds = filteredBookIds,
        )
    }

    /** 是否正在处理导入（顶部队列卡片开关）。 */
    val hasActiveImports: Boolean
        get() = auxiliary.importBatch.isRunning || auxiliary.importTasks.any { it.status == "processing" }

    /** 文件可用性始终可筛选，因此高级筛选入口始终显示。 */
    val showFilterButton: Boolean
        get() = true

    /** 紧凑工具栏摘要：单一条件显示名称，多条件只显示维度数，避免遗漏组合筛选。 */
    val filterSummary: String
        get() {
            val labels = buildList {
                if (statusFilter != ShelfStatusFilter.ALL) add(statusFilter.label())
                if (formatFilter.isNotEmpty()) add(formatLabel(formatFilter))
                selectedShelfId.takeIf(String::isNotEmpty)?.let { id ->
                    add(library.shelves.firstOrNull { it.id == id }?.name ?: "书单")
                }
                selectedCategoryId.takeIf(String::isNotEmpty)?.let { id ->
                    add(library.categories.firstOrNull { it.id == id }?.name ?: "分类")
                }
                if (selectedTagIds.isNotEmpty()) {
                    add(
                        if (selectedTagIds.size == 1) {
                            library.tags.firstOrNull { it.id in selectedTagIds }?.name ?: "标签"
                        } else {
                            "${selectedTagIds.size} 个标签"
                        },
                    )
                }
            }
            return when (labels.size) {
                0 -> "全部"
                1 -> labels.single()
                else -> "${labels.size} 项筛选"
            }
        }
}

/** 同一标签维度取并集，不同筛选维度取交集。 */
internal fun formatLabel(format: String): String = when (format) {
    "epub" -> "EPUB"
    "txt" -> "TXT"
    "md" -> "Markdown"
    else -> format
}

internal fun combineTaxonomyFilterIds(
    shelfIds: Set<String>?,
    categoryIds: Set<String>?,
    tagIdGroups: List<Set<String>>,
): Set<String>? {
    val tagIds = tagIdGroups
        .takeIf { it.isNotEmpty() }
        ?.fold(emptySet<String>()) { result, ids -> result + ids }
    val dimensions = listOfNotNull(shelfIds, categoryIds, tagIds)
    return dimensions.takeIf { it.isNotEmpty() }?.reduce { result, ids -> result.intersect(ids) }
}

private fun ShelfStatusFilter.label(): String = when (this) {
    ShelfStatusFilter.ALL -> "全部"
    ShelfStatusFilter.READING -> "在读"
    ShelfStatusFilter.READABLE -> "本机可读"
    ShelfStatusFilter.COMPLETED -> "已完成"
    ShelfStatusFilter.UNREAD -> "未开始"
}
