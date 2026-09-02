package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
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
    }

    fun stateFor(sourceKey: Any, unitIndex: Int): State<ScrollUnitContentState> {
        val key = sourceKey to unitIndex
        val cached = if (sourceKey === cacheSourceKey) cache.get(unitIndex) else null
        return states.getOrPut(key) {
            mutableStateOf(cached?.let(ScrollUnitContentState::Loaded) ?: ScrollUnitContentState.Loading)
        }
    }

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
            return
        }
        val state = states.getOrPut(key) { mutableStateOf(ScrollUnitContentState.Loading) }
        state.value = ScrollUnitContentState.Loading
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
        }
    }
}
