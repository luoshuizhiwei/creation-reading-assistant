package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ScrollUnitContentState {
    data object Loading : ScrollUnitContentState
    data class Loaded(val content: ScrollUnitProjection) : ScrollUnitContentState
    data class Failed(val cause: Throwable) : ScrollUnitContentState
}

/** Async seam between scrolling Compose items and blocking chapter projection/file IO. */
class ScrollUnitContentLoader(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cache = BoundedLruCache<Int, ScrollUnitProjection>(maxSize = 5)
    private val states = mutableMapOf<Pair<Any, Int>, MutableState<ScrollUnitContentState>>()
    private val attempts = mutableMapOf<Pair<Any, Int>, Int>()
    private val stickyFailures = mutableSetOf<Pair<Any, Int>>()
    private var currentSourceKey: Any? = null
    private var cacheSourceKey: Any? = null

    /**
     * 任意单元加载状态变化的单调修订号（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）。
     *
     * 恢复落位判定必须在**真实正文布局**上做出：Loading 占位（ShimmerBlock）同样参与
     * LazyColumn 测量，其高度与正文无关，据此判出的「末项已在屏内」是假落位。恢复协程用
     * `snapshotFlow` 观察本值，从而在任何单元状态真正变化（开始加载 / Loaded / Failed /
     * 释放 / 切源）时**重新判定**，而不是依赖「布局三元组恰好也变了」这种偶然。
     */
    private val stateRevisionState = mutableIntStateOf(0)

    /** 只读观察入口：值本身无意义，变化即代表有单元状态发生变更。 */
    val stateRevision: State<Int> get() = stateRevisionState

    private fun bumpRevision() {
        stateRevisionState.intValue += 1
    }

    fun switchSource(sourceKey: Any) {
        if (sourceKey === currentSourceKey) return
        currentSourceKey = sourceKey
        cache.clear()
        cacheSourceKey = sourceKey
        val newSourceStates = states.filterKeys { (stateSource, _) -> stateSource === sourceKey }
        states.clear()
        states.putAll(newSourceStates)
        attempts.clear()
        stickyFailures.clear()
        bumpRevision()
    }

    fun stateFor(sourceKey: Any, unitIndex: Int): State<ScrollUnitContentState> {
        val key = sourceKey to unitIndex
        val cached = if (sourceKey === cacheSourceKey) cache.get(unitIndex) else null
        return states.getOrPut(key) {
            mutableStateOf(cached?.let(ScrollUnitContentState::Loaded) ?: ScrollUnitContentState.Loading)
        }
    }

    /**
     * 当前 source 下该单元的加载状态（只读观察；不创建条目、不触发加载）。
     *
     * 条目不存在（尚未组合 / 已释放 / 切源后未重建）一律按 [ScrollUnitContentState.Loading]
     * 处理 ——「未开始加载」不是「正文已就绪」，恢复落位不得据此放行。
     */
    fun currentStateOf(unitIndex: Int): ScrollUnitContentState =
        states[currentSourceKey to unitIndex]?.value ?: ScrollUnitContentState.Loading

    fun release(sourceKey: Any, unitIndex: Int) {
        val key = sourceKey to unitIndex
        val current = states[key]?.value
        attempts.remove(key)
        if (current is ScrollUnitContentState.Failed &&
            (currentSourceKey == null || sourceKey === currentSourceKey)
        ) {
            stickyFailures += key
        } else {
            states.remove(key)
        }
        bumpRevision()
    }

    suspend fun load(
        sourceKey: Any,
        unitIndex: Int,
        read: suspend () -> ScrollUnitProjection,
    ) {
        val key = sourceKey to unitIndex
        if (key in stickyFailures) return
        val cached = if (sourceKey === cacheSourceKey) cache.get(unitIndex) else null
        if (cached != null) {
            states.getOrPut(key) { mutableStateOf(ScrollUnitContentState.Loading) }.value =
                ScrollUnitContentState.Loaded(cached)
            bumpRevision()
            return
        }
        val state = states.getOrPut(key) { mutableStateOf(ScrollUnitContentState.Loading) }
        state.value = ScrollUnitContentState.Loading
        bumpRevision()
        val attempt = (attempts[key] ?: 0) + 1
        attempts[key] = attempt
        publishAfterRead(key, sourceKey, unitIndex, state, attempt, read)
    }

    suspend fun retry(
        sourceKey: Any,
        unitIndex: Int,
        read: suspend () -> ScrollUnitProjection,
    ) {
        val key = sourceKey to unitIndex
        stickyFailures -= key
        attempts[key] = (attempts[key] ?: 0) + 1
        states.getOrPut(key) { mutableStateOf(ScrollUnitContentState.Loading) }.value =
            ScrollUnitContentState.Loading
        bumpRevision()
        load(sourceKey, unitIndex, read)
    }

    private suspend fun publishAfterRead(
        key: Pair<Any, Int>,
        sourceKey: Any,
        unitIndex: Int,
        state: MutableState<ScrollUnitContentState>,
        attempt: Int,
        read: suspend () -> ScrollUnitProjection,
    ) {
        val result = try {
            ScrollUnitContentState.Loaded(withContext(ioDispatcher) { read() })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ScrollUnitContentState.Failed(error)
        }
        val sourceStillCurrent = currentSourceKey == null || sourceKey === currentSourceKey
        if (sourceStillCurrent && attempt == attempts[key]) {
            if (result is ScrollUnitContentState.Loaded) {
                if (cacheSourceKey == null) cacheSourceKey = sourceKey
                if (sourceKey === cacheSourceKey) cache.put(unitIndex, result.content)
            }
            state.value = result
            bumpRevision()
        }
    }
}
