package com.creationreadingassistant.feature.reader.doc

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 滚动 TXT 读取单元的加载状态（loading / 成功 / 失败均可观察）。 */
sealed interface UnitTextState {
    /** 尚未加载完成。 */
    data object Loading : UnitTextState

    /** 加载成功。 */
    data class Loaded(val text: String) : UnitTextState

    /** 读取失败。 */
    data class Failed(val cause: Throwable) : UnitTextState
}

/**
 * 滚动模式 TXT 读取单元的异步加载器（组合/纯渲染路径与阻塞文件 I/O 之间的 seam）。
 *
 * - 组合阶段只允许读取 [stateFor]（非阻塞，缓存命中立即返回 Loaded），
 *   不得直接调用阻塞的 [PlainTextDocument.readUnit]；
 * - 阻塞读取由 [load] 在 [ioDispatcher] 上执行；
 * - 文档变化时组合层调用 [switchDocument]：清空缓存并作废旧文档在途结果，
 *   旧请求完成时不会覆盖新文档状态（stale result 保护）；
 * - item 离开组合时调用 [release] 释放状态槽，内存随可见项回收；读取失败保持 sticky
 *   （滚出/滚回视口不自动重试），只有显式 [retry] / [invalidate] / [switchDocument] 清除。
 */
class UnitTextLoader(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cache: ReadingUnitCache = ReadingUnitCache(),
) {
    private val states = mutableMapOf<Pair<Any, Int>, MutableState<UnitTextState>>()
    /** 每个 (doc, unit) 的读取代次：快速连续 retry / load 只允许最新代次写回。 */
    private val attempts = mutableMapOf<Pair<Any, Int>, Int>()
    /** 失败 sticky 标记：release 后仍保留失败槽，重进视口不自动重试；按文档归属，切文档释放。 */
    private val stickyFailures = mutableSetOf<Pair<Any, Int>>()
    private var currentDocKey: Any? = null
    /** 缓存内容归属的文档；仅该文档可读写缓存（文档身份绑定）。 */
    private var cacheDocKey: Any? = null

    /**
     * 文档实例变化时调用：作废旧文档在途结果并清空跨文档缓存。
     *
     * Compose 首帧时序是 `stateFor` 先于 `LaunchedEffect { switchDocument }`：
     * 新文档的状态槽在组合期就已创建并被 UI 订阅，因此这里只清旧文档的
     * identity（旧状态槽 / 代次 / 失败 sticky），保留新文档已创建的状态槽，
     * 否则后续 [load] 写入的是另一个新槽，UI 订阅的旧槽会永久停在 Loading。
     */
    fun switchDocument(docKey: Any) {
        if (docKey === currentDocKey) return
        currentDocKey = docKey
        cache.clear()
        cacheDocKey = docKey
        // 只清旧文档 identity：新文档首帧组合已创建的状态槽必须原样保留
        val newDocStates = states.filterKeys { (stateDoc, _) -> stateDoc === docKey }
        states.clear()
        states.putAll(newDocStates)
        attempts.clear()
        stickyFailures.clear()
    }

    /**
     * 缓存是否可为 [docKey] 服务：缓存内容只属于当前文档。
     *
     * 组合首帧的时序是 `stateFor` 先于 `LaunchedEffect { switchDocument }`，
     * 若直接按 unitIndex 读缓存，新文档首帧会拿到旧文档的已解码文本；
     * 故缓存必须与文档身份绑定（switchDocument 确立，或首次写缓存时确立）。
     */
    private fun cacheUsableFor(docKey: Any): Boolean = docKey === cacheDocKey

    /** 组合阶段只读：当前文档 [unitIndex] 的可观察状态。 */
    fun stateFor(docKey: Any, unitIndex: Int): State<UnitTextState> {
        val key = docKey to unitIndex
        val cached = if (cacheUsableFor(docKey)) cache.get(unitIndex) else null
        return states.getOrPut(key) {
            mutableStateOf(cached?.let { UnitTextState.Loaded(it) } ?: UnitTextState.Loading)
        }
    }

    /** item 离开组合时调用：释放该 (docKey, unitIndex) 的状态槽。 */
    fun release(docKey: Any, unitIndex: Int) {
        val key = docKey to unitIndex
        val current = states[key]?.value
        attempts.remove(key)
        // 未 switchDocument（首帧前）时失败同样 sticky；切过文档后只保留当前文档的失败
        if (current is UnitTextState.Failed && (currentDocKey == null || docKey === currentDocKey)) {
            // 失败保持 sticky：滚出视口后重进仍显示 Failed，不自动重试
            stickyFailures += key
        } else {
            states.remove(key)
        }
    }

    /** 加载 [unitIndex] 并写入状态；文档已切换（[switchDocument]）则丢弃结果。 */
    suspend fun load(docKey: Any, unitIndex: Int, read: suspend () -> String) {
        val key = docKey to unitIndex
        // sticky 失败：重进视口的组合自动 load 不得自动重试，保持 Failed
        if (key in stickyFailures) return
        val cached = if (cacheUsableFor(docKey)) cache.get(unitIndex) else null
        if (cached != null) {
            states.getOrPut(key) { mutableStateOf(UnitTextState.Loading) }.value = UnitTextState.Loaded(cached)
            return
        }
        val state = states.getOrPut(key) { mutableStateOf(UnitTextState.Loading) }
        state.value = UnitTextState.Loading
        val attempt = (attempts[key] ?: 0) + 1
        attempts[key] = attempt
        publishAfterRead(key, docKey, unitIndex, state, attempt, read)
    }

    /**
     * 使 (docKey, unitIndex) 的在途结果作废并回到 [UnitTextState.Loading]；
     * 不触发重新读取。旧代次完成时因代次不匹配不会写回。
     */
    fun invalidate(docKey: Any, unitIndex: Int) {
        val key = docKey to unitIndex
        stickyFailures -= key
        attempts[key] = (attempts[key] ?: 0) + 1
        states.getOrPut(key) { mutableStateOf(UnitTextState.Loading) }.value = UnitTextState.Loading
    }

    /**
     * 失败后的显式重试：作废在途结果 → [UnitTextState.Loading] → 重新读取 →
     * [UnitTextState.Loaded] / [UnitTextState.Failed]。不读旧失败缓存；
     * 快速连续重试只接受最新代次；切文档后旧重试不得写回。
     */
    suspend fun retry(docKey: Any, unitIndex: Int, read: suspend () -> String) {
        invalidate(docKey, unitIndex)
        load(docKey, unitIndex, read)
    }

    /** 读取完成后按「文档身份 + 代次」双守卫决定是否写回。 */
    private suspend fun publishAfterRead(
        key: Pair<Any, Int>,
        docKey: Any,
        unitIndex: Int,
        state: MutableState<UnitTextState>,
        attempt: Int,
        read: suspend () -> String,
    ) {
        val result = try {
            UnitTextState.Loaded(withContext(ioDispatcher) { read() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            UnitTextState.Failed(e)
        }
        // stale result 保护：文档身份 + 代次都必须仍是最新，否则丢弃
        val docStillCurrent = currentDocKey == null || docKey === currentDocKey
        if (docStillCurrent && attempt == attempts[key]) {
            if (result is UnitTextState.Loaded) {
                // loader 直用（从未 switchDocument）时以首次写入确立缓存归属
                if (cacheDocKey == null) cacheDocKey = docKey
                cache.put(unitIndex, result.text)
            }
            state.value = result
        }
    }
}
