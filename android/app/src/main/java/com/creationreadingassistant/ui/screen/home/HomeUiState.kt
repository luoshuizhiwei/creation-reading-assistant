package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity

/**
 * Home 页"纯 UI 层"状态。
 *
 * 为什么不直接复用 [com.creationreadingassistant.ui.viewmodel.HomeUiState]？
 * —— 分层原则：纯 Screen 不应依赖 ViewModel 包，View 层（Route）负责把
 * ViewModel 的源状态 + 纯 UI 状态（骨架显示、sheet 开关）合并到本类。
 *
 * 注意：本类内的 entity 引用只是数据源搬运，纯 Screen 不会调用 DAO、
 * 不会调用 hiltViewModel、也不会 collect Flow。
 */
data class HomeUiState(
    /** 首屏骨架显示（= 数据未到且书籍为空，350ms 超时之前）。Route 生成，Screen 纯消费。 */
    val showSkeleton: Boolean = true,
    /** 继续阅读 sheet 是否展开。Route 维护开关状态，Screen 只展示。 */
    val isContinueSheetOpen: Boolean = false,

    // ——— 以下字段直接来自 ViewModel.HomeUiState（纯搬运，无业务逻辑）———
    val books: List<BookEntity> = emptyList(),
    val progressById: Map<String, com.creationreadingassistant.data.local.entity.ReadingProgressEntity> = emptyMap(),
    val sessionsByBook: Map<String, List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>> = emptyMap(),
    val removedContinueIds: Map<String, String> = emptyMap(),
    val continueBooks: List<BookEntity> = emptyList(),
    val completedBooks: List<BookEntity> = emptyList(),
    val recentInspirations: List<InspirationEntity> = emptyList(),
    val thisWeekNew: Int = 0,
    val readingCount: Int = 0,
    val completedCount: Int = 0,
    val totalReadBooksCount: Int = 0,
    val totalReadingMs: Long = 0L,
    val todayReadingMs: Long = 0L,
    val dailyGoalMinutes: Int = 0,
) {
    companion object {
        val Empty = HomeUiState(
            showSkeleton = false,
            isContinueSheetOpen = false,
            books = emptyList(),
            progressById = emptyMap(),
            sessionsByBook = emptyMap(),
            removedContinueIds = emptyMap(),
            continueBooks = emptyList(),
            completedBooks = emptyList(),
            recentInspirations = emptyList(),
            thisWeekNew = 0,
            readingCount = 0,
            completedCount = 0,
            totalReadBooksCount = 0,
            totalReadingMs = 0L,
            todayReadingMs = 0L,
        )
    }
}
