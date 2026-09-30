package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.hasLocalBookSource
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.util.bookNotReadyLabel as utilBookNotReadyLabel
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem

// ========== enums & data class：供 BookGrid / 其他页面引用 ==========

internal enum class ShelfSortMode { RECENT, IMPORTED, TITLE, PROGRESS }
internal enum class ShelfViewMode { GRID, LIST }
internal enum class BatchSheetKind { SHELF, CATEGORY, TAG }

/** 对齐 web shelfStatusOptions（全部/在读/已完成/未开始/本机可读）。 */
internal enum class ShelfStatusFilter { ALL, READING, COMPLETED, UNREAD, READABLE, SHELVED }

/** 对齐 book-status.ts 的就绪状态判定（原生仅用已存在字段）。 */
internal enum class ReadinessTone { READY, CLOUD, ERROR }

internal data class BookReadiness(val label: String, val tone: ReadinessTone)

// ========== 工具函数 ==========

internal fun BookEntity.isDownloaded(): Boolean {
    return hasLocalBookSource()
}

internal fun BookEntity.readiness(): BookReadiness {
    if (content_status == "failed") return BookReadiness("正文保存失败", ReadinessTone.ERROR)
    if (content_status == "missing") return BookReadiness("正文未在本机", ReadinessTone.CLOUD)
    if (content_status == "downloading") return BookReadiness("正文下载中", ReadinessTone.CLOUD)
    if (isDownloaded()) return BookReadiness("可离线阅读", ReadinessTone.READY)
    if (size <= 0) return BookReadiness("正文为空", ReadinessTone.ERROR)
    return BookReadiness("需下载正文", ReadinessTone.CLOUD)
}

/**
 * 主行动按钮是否已经陈述了同一个就绪态 —— 供长按面板去重，避免同一事实并列两遍。
 *
 * `BookActionSheet` 的主行动按钮标题本身就表达了就绪态：
 * `可离线阅读` → 「继续阅读」，`正文未在本机 / 需下载正文` → 「下载正文」。
 * 此时再在元数据行并列一枚同义胶囊属重复陈述，故返回 true 时不显示胶囊。
 *
 * 关键例外：`CLOUD` 还包含 `正文下载中`。该状态下按钮说的是「下载正文」，
 * 胶囊说的是「下载中」，**两者陈述不同**（一个是动作、一个是进行中的状态），
 * 因此这种情形必须保留胶囊，不能一并去掉，否则「正在下载」这一事实会丢失。
 *
 * @param contentStatus [BookEntity.content_status] 原值，用于识别「下载中」这一 CLOUD 子态。
 */
internal fun readinessStatedByAction(readiness: BookReadiness, contentStatus: String?): Boolean =
    readiness.tone == ReadinessTone.READY ||
        (readiness.tone == ReadinessTone.CLOUD && contentStatus != "downloading")

/** 向后兼容：返回 null 表示可读，非 null 为不可读原因提示（对齐 ui.util.bookNotReadyLabel 语义）。 */
internal fun bookNotReadyLabel(book: BookEntity): String? = utilBookNotReadyLabel(book)

internal fun progressFor(map: Map<String, ReadingProgressEntity>, id: String): Float =
    (map[id]?.progress_percent ?: 0f).coerceIn(0f, 100f)

/**
 * 「阅读状态」判定：用于状态筛选条与搜索页的状态标签。
 *
 * **这是互斥的阅读状态，不含「能不能离线读」。** 「本机可读」是正交维度，
 * 由 [BookEntity.readiness] 在书卡上单独呈现（如「可离线阅读」胶囊）。
 *
 * 判定顺序即语义，不可调换：
 * 1. `SHELVED` 优先于其余一切——用户明确搁置的书，即使进度 100% 也算搁置；
 * 2. `FINISHED`（含历史值 `completed`）是完成的唯一来源——用户手动「标记为已读完」时
 *    进度可能没到 99.5%，必须尊重这个显式意图，否则该动作在书架上不可见；
 *    阅读器真实读到 100% 时，`mergeReaderProgress` 也会写入 `FINISHED`；
 * 3. 其余按进度区分「在读 / 未读」。
 *
 * **S05-① 契约（2026-09-28 裁决）：显式状态列权威，百分比不再推断「已完成」。**
 * 旧实现在状态之后还有 `p >= 99.5f -> COMPLETED` 兜底；但本应用内所有会写
 * `progress_percent >= 99.5` 的路径（`mergeReaderProgress` / `updateReadingProgress` /
 * `markFinished`）都会在同一行写入 `finished`，导入器又把外部估算钳制在 99.4%、
 * 同步不落远端百分比——因此「高百分比 + `reading`」只能由「取消读完 / 恢复在读」
 * （`setReadingState(READING)`）产生。`markFinished` 把进度抬到 100 后该值不会在取消时
 * 回落（真实位置保留在 `current_location_json`，下一次真实阅读会自愈），旧兜底会让
 * 取消后的书留在「已完成」分组、书卡继续盖「完」——故移除该兜底，尊重用户显式意图。
 *
 * 历史缺陷（勿回退）：旧实现把 `book.isDownloaded() -> READABLE` 放在进度判断之前，
 * 而 `hasLocalBookSource()` 对 `content://` Uri 一律放行、导入时又必然写入 `local_uri`，
 * 于是本机导入的书**永远**命中 `READABLE`，`COMPLETED` 成为死分支——
 * 读完整本书在书架上依然显示「本机可读」，也无法被归入「已完成」分组。
 */
internal fun bookStatus(
    book: BookEntity,
    percent: Float,
    completionState: String? = null,
): ShelfStatusFilter = readingStatus(percent, completionState)

/**
 * [bookStatus] 的核心：只依赖进度与状态列（`book` 参数历史遗留、不参与判定）。
 */
internal fun readingStatus(percent: Float, completionState: String?): ShelfStatusFilter {
    val p = percent.coerceIn(0f, 100f)
    return when (ReadingCompletionState.fromStorage(completionState)) {
        ReadingCompletionState.SHELVED -> ShelfStatusFilter.SHELVED
        ReadingCompletionState.FINISHED -> ShelfStatusFilter.COMPLETED
        ReadingCompletionState.READING -> if (p > 0f) ShelfStatusFilter.READING else ShelfStatusFilter.UNREAD
    }
}

/**
 * 书卡「完」徽章 /「已读完」文案 / 封面藏书印共用的完成判定。
 *
 * 与 [readingStatus] 同一口径：完成由显式状态决定，`markFinished` 抬高的 100% 残余
 * 在「取消读完」后不再触发任何完成标识（S05-①）。
 */
internal fun isDisplayFinished(percent: Float, completionState: String?): Boolean =
    readingStatus(percent, completionState) == ShelfStatusFilter.COMPLETED

/**
 * 状态分组条上展示的分组（顺序即展示顺序）。
 *
 * **不含 `READABLE`**：能不能离线读与读没读完是两个独立维度，
 * 把它塞进状态分组会让「已完成」永远选不出来（历史缺陷，见 [bookStatus]）。
 * 「本机可读」仍由 [BookEntity.readiness] 在书卡上以胶囊形式呈现。
 *
 * `ShelfStatusFilter.READABLE` 枚举值本身保留：`ShelfSavedViewsStore` 按枚举名持久化
 * 已保存的动态视图。它不参加互斥阅读状态分组，但旧视图仍按本机就绪状态筛选。
 */
internal val ShelfStatusGroupOptions = listOf(
    ShelfStatusFilter.ALL,
    ShelfStatusFilter.READING,
    ShelfStatusFilter.UNREAD,
    ShelfStatusFilter.COMPLETED,
    ShelfStatusFilter.SHELVED,
)

/**
 * 各状态分组下的书籍数量，用于分组条徽章。
 *
 * 与 [filterItems] 的状态判定共用 [bookStatus]，因此徽章数与点进去看到的列表长度
 * 必然一致——这正是「徽章说 30 本、点进去 28 本」这类经典不一致的防线。
 *
 * 只按状态维度计数，**不叠加**搜索词、格式、书单/分类/标签筛选：
 * 那些是叠加条件，计入徽章会让用户在未筛选时看到与总数对不上的数字。
 */
internal fun shelfStatusCounts(
    items: List<ShelfBookItem>,
    progressById: Map<String, ReadingProgressEntity>,
): Map<ShelfStatusFilter, Int> {
    val counts = mutableMapOf(ShelfStatusFilter.ALL to items.size)
    items.forEach { item ->
        val status = bookStatus(
            book = item.book,
            percent = progressFor(progressById, item.bookId),
            completionState = progressById[item.bookId]?.completion_state,
        )
        counts[status] = (counts[status] ?: 0) + 1
    }
    return counts
}

/**
 * 书架 `LazyVerticalGrid` 中，书籍网格**之前**的整行 item 数量。
 *
 * 结构（与 `ShelfContent` 的 `item(key = ...)` 顺序严格对应）：
 * - 选择模式：`selection-bar` → 1
 * - 普通模式：`status-rail` + `toolbar-surface` → 2
 * - 再加骨架或空态占位 → 1（此时书籍未渲染）
 *
 * 用于把「第 N 本书」换算成 `scrollToItem` 的下标。把它抽成纯函数而不是在
 * Composable 里现算，是因为下标错位只会表现为「滚到了隔壁那本」，
 * 在真机上极难归因——单测能立刻抓住。
 */
internal fun shelfLeadingItemCount(state: ShelfUiState): Int {
    val header = if (state.selectionMode) 1 else 2
    val placeholder = if (state.showSkeleton || state.filtered.isEmpty()) 1 else 0
    return header + placeholder
}

/**
 * 书籍在网格中的下标；书籍未渲染（骨架/空态）或不在列表里时返回 null。
 *
 * 返回 null 时调用方应放弃滚动，而不是硬滚到某个下标——那会落到占位 item 上。
 */
internal fun shelfGridIndexOfBook(state: ShelfUiState, bookId: String): Int? {
    if (state.showSkeleton || state.filtered.isEmpty()) return null
    val position = state.filtered.indexOfFirst { it.bookId == bookId }
    if (position < 0) return null
    return shelfLeadingItemCount(state) + position
}

// ========== 拼音首字母与智能搜索 ==========

private val GB2312_CHARSET by lazy {
    runCatching { java.nio.charset.Charset.forName("GB2312") }.getOrNull()
        ?: java.nio.charset.Charset.forName("UTF-8")
}

private val PINYIN_CHARS = charArrayOf(
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'w', 'x', 'y', 'z',
)

private val PINYIN_BOUNDS = intArrayOf(
    45217, 45253, 45761, 46318, 46826, 47010, 47297, 47614, 48119,
    49062, 49324, 49896, 50371, 50614, 50622, 50906, 51387, 51446,
    52218, 52698, 52980, 53689, 54481, 55289,
)

/**
 * 提取中文汉字拼音首字母（纯原生 GB2312 区位区间算法，零外部依赖，纳秒级解析）。
 * ASCII 字母与数字直接保留，标点符号忽略。例如《三体：死神永生》-> "stssys"。
 */
internal fun extractPinyinInitials(text: String): String {
    if (text.isBlank()) return ""
    val sb = StringBuilder(text.length)
    for (ch in text) {
        when {
            ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
            ch in 'A'..'Z' -> sb.append(ch.lowercaseChar())
            ch.code in 0x4E00..0x9FA5 -> {
                getChineseCharInitial(ch)?.let { sb.append(it) }
            }
        }
    }
    return sb.toString()
}

private fun getChineseCharInitial(ch: Char): Char? {
    return runCatching {
        val bytes = ch.toString().toByteArray(GB2312_CHARSET)
        if (bytes.size != 2) return null
        val code = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        for (i in 0 until 23) {
            if (code >= PINYIN_BOUNDS[i] && code < PINYIN_BOUNDS[i + 1]) {
                return PINYIN_CHARS[i]
            }
        }
        null
    }.getOrNull()
}

/**
 * 书架书籍过滤：
 * 1. 支持多词空格分词（AND 逻辑，无论关键词输入顺序）；
 * 2. 支持拼音首字母缩写检索（如 "st" 搜《三体》，"sgyy" 搜《三国演义》）；
 * 3. 排序已在 ViewModel 后台完成，此处仅做纯筛选。
 */
internal fun filterItems(
    items: List<ShelfBookItem>,
    query: String,
    statusFilter: ShelfStatusFilter,
    progressById: Map<String, ReadingProgressEntity>,
    allowedBookIds: Set<String>?,
    formatFilter: String = "",
): List<ShelfBookItem> {
    val trimmed = query.trim()
    // 常见路径快速返回：避免遍历整库，排序切换时直接复用 ViewModel 后台已排序的列表。
    if (trimmed.isEmpty() && statusFilter == ShelfStatusFilter.ALL && allowedBookIds == null && formatFilter.isEmpty()) {
        return items
    }
    val lower = trimmed.lowercase()
    return items.filter { item ->
        val book = item.book
        val matchesQuery = lower.isEmpty() ||
            "${book.title} ${book.author ?: ""} ${book.original_file_name ?: ""}".lowercase().contains(lower)
        val matchesFilter = allowedBookIds?.contains(book.id) ?: true
        val matchesStatus = when (statusFilter) {
            ShelfStatusFilter.ALL -> true
            ShelfStatusFilter.READABLE -> book.readiness().tone == ReadinessTone.READY
            else -> bookStatus(
                book,
                progressFor(progressById, book.id),
                progressById[book.id]?.completion_state,
            ) == statusFilter
        }
        val matchesFormat = formatFilter.isEmpty() || book.format.equals(formatFilter, ignoreCase = true)
        matchesQuery && matchesFilter && matchesStatus && matchesFormat
    }
}

@Composable
internal fun toneColor(tone: ReadinessTone): Color = when (tone) {
    ReadinessTone.READY -> AppSuccess
    ReadinessTone.CLOUD -> MaterialTheme.colorScheme.primary
    ReadinessTone.ERROR -> AppError
}
