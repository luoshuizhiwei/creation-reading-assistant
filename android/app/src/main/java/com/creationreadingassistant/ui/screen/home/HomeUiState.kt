package com.creationreadingassistant.ui.screen.home

import androidx.compose.runtime.Immutable
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Home 页"纯 UI 层"状态。
 *
 * 为什么不直接复用 [com.creationreadingassistant.ui.viewmodel.HomeUiState]？
 * —— 分层原则：纯 Screen 不应依赖 ViewModel 包，View 层（Route）负责把
 * ViewModel 的源状态 + 纯 UI 状态（骨架显示）合并到本类。
 *
 * 本类**只保留 [HomeScreen] 真正渲染的字段**。整库 `books` / `sessionsByBook` /
 * `removedContinueIds` 与 `isContinueSheetOpen` 曾在此搬运，但 Screen 从不读取——
 * 它们既是重复状态（sheet 开关的唯一来源是 Route 的本地状态），又会因为每次聚合
 * 都产生新的 Map 实例而放大重组面，故已移除。
 *
 * 注意：本类内的 entity 引用只是数据源搬运，纯 Screen 不会调用 DAO、
 * 不会调用 hiltViewModel、也不会 collect Flow。
 */
@Immutable
data class HomeUiState(
    /** 首屏骨架显示（= 数据未到且书籍为空，350ms 超时之前）。Route 生成，Screen 纯消费。 */
    val showSkeleton: Boolean = true,

    // ——— 以下字段直接来自 ViewModel.HomeUiState（纯搬运，无业务逻辑）———
    val progressById: Map<String, ReadingProgressEntity> = emptyMap(),
    val continueBooks: ImmutableList<BookEntity> = persistentListOf(),
    val completedBooks: ImmutableList<BookEntity> = persistentListOf(),
    val recentInspirations: ImmutableList<InspirationEntity> = persistentListOf(),
    val thisWeekNew: Int = 0,
    val readingCount: Int = 0,
    val completedCount: Int = 0,
    val totalReadBooksCount: Int = 0,
    val totalReadingMs: Long = 0L,
    val todayReadingMs: Long = 0L,
    val dailyGoalMinutes: Int = 0,
) {
    companion object {
        val Empty = HomeUiState(showSkeleton = false)
    }
}
