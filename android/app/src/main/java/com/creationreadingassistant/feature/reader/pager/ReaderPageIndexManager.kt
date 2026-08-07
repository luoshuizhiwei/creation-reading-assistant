package com.creationreadingassistant.feature.reader.pager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局当前阅读位置管理（要求：PageIndex 的全局管理）。
 *
 * 翻页引擎每次翻页都会把当前页的 (书籍 / 章节 / 页号 / 全书偏移区间 / 进度)
 * 写入此单例，作为全 App 唯一的「当前页索引」真源。书签创建、进度持久化、
 * 底部进度条等都从这里读当前位置，避免把位置在 Compose 各层反复透传。
 *
 * 与 [PageIndexStore] 的区别：后者是**每章 pageStarts 的写通缓存**（排版产物），
 * 本对象是**当前阅读到的那一页的轻量快照**（运行期状态）。
 */
data class ReaderPagePosition(
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val absStart: Int,
    val absEnd: Int,
    val percent: Float,
)

@Singleton
class ReaderPageIndexManager @Inject constructor() {
    private val _position = MutableStateFlow<ReaderPagePosition?>(null)
    val position: StateFlow<ReaderPagePosition?> = _position.asStateFlow()

    fun update(position: ReaderPagePosition) {
        _position.value = position
    }

    /** 离开本书 / 关闭阅读器时调用，避免残留旧书位置。 */
    fun clear() {
        _position.value = null
    }
}
