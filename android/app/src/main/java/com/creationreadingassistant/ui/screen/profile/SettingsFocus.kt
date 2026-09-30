package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos

/**
 * 设置子页整页 LazyColumn 的状态（Route 层创建并下发）。
 *
 * 子页侧统一用 [rememberSettingsPageListState] 取：拿不到就自建，
 * 因此子页不需要知道聚焦链路是否存在（预览 / 单测也照常工作）。
 */
internal val LocalSettingsListState = staticCompositionLocalOf<LazyListState?> { null }

/** 聚焦控制器（Route 层创建并下发）。 */
internal val LocalSettingsFocusController = staticCompositionLocalOf<SettingsFocusController?> { null }

/**
 * 取本页列表状态，并把它登记给 [SettingsFocusController]。
 *
 * 登记这一步是**必需**的：没有它控制器手里就没有 listState，`reveal` 会直接返回，
 * 表现为「跳到了子页但停在页首」——首轮真机验证正是这个症状。
 */
@Composable
internal fun rememberSettingsPageListState(): LazyListState {
    val state = LocalSettingsListState.current ?: rememberLazyListState()
    val controller = LocalSettingsFocusController.current
    if (controller != null) {
        DisposableEffect(controller, state) {
            controller.attach(state)
            onDispose { controller.detach(state) }
        }
    }
    return state
}

/**
 * 聚焦控制器：把 [SettingsSearchIndex] 命中的锚点滚进视口。
 *
 * ## 为什么不能只靠 bringIntoViewRequester（真机验证发现的缺陷）
 *
 * `bringIntoViewRequester` 只能作用于**已经组合**的节点。LazyColumn 只组合可见项
 * （及少量预取），目标设置项在几屏之外时，它的 `SettingsAnchor` 根本还没存在，
 * requester 无从谈起——真机上表现为「跳到了子页，但停在页首，目标在下面」。
 *
 * 所以这里按屏推进 [LazyListState]，直到目标锚点被组合出来：
 * 终止条件是「锚点是否已组合」，而不是「第几个 item」——
 * item 索引会随条件渲染变化（开一个开关，后面全移位），锚点是稳定的。
 * 锚点一旦组合，它自己的 `bringIntoViewRequester` 负责把该行对齐进视口。
 *
 * 全程不维护 `锚点 → item 索引` 映射，因此没有第二份会漂移的清单。
 *
 * ## 触发方必须是 Route
 *
 * 不能由目标 [SettingsAnchor] 自己发起滚动：目标在屏幕外时它还没被组合，
 * 等它来启动就是死锁。Route 持有路由参数，一开始就知道目标是哪个锚点，
 * 所以由它在子页首帧后调用 [reveal]。
 */
internal class SettingsFocusController {

    private var listState: LazyListState? = null

    /** 当前已组合的锚点，由 [SettingsAnchor] 在进入/离开组合时维护。 */
    private val composedAnchors = mutableSetOf<String>()

    /** 兜底上限：最多推进这么多跳，避免异常状态下无限滚动。 */
    private val maxHops = 40

    /** 等列表挂载的帧数上限（Route 与控制器在同一帧内建立连接，这里留足余量）。 */
    private val maxAttachFrames = 60

    internal fun attach(state: LazyListState) {
        listState = state
    }

    internal fun detach(state: LazyListState) {
        if (listState === state) listState = null
    }

    internal fun onAnchorComposed(anchor: String) {
        composedAnchors += anchor
    }

    internal fun onAnchorDisposed(anchor: String) {
        composedAnchors -= anchor
    }

    /**
     * 把 [anchor] 滚进视口。锚点始终不出现（例如条件渲染项当前没渲染）时，
     * 推进到列表底部后静默结束——索引侧已保证条件项挂的是必然存在的父卡片锚点。
     */
    suspend fun reveal(anchor: String) {
        var waited = 0
        while (listState == null && waited < maxAttachFrames) {
            withFrameNanos { }
            waited++
        }
        val state = listState ?: return

        // 首屏宽限：目标本来就在首屏时，别白白滚一屏再回来
        repeat(3) { withFrameNanos { } }
        if (anchor in composedAnchors) return

        repeat(maxHops) {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val total = info.totalItemsCount
                val lastVisible = visible.last().index
                if (lastVisible >= total - 1) return

                // 半屏步进：跳得够快，又不容易整个跨过目标（跨过也能被 requester 拉回）
                val step = (visible.size / 2).coerceAtLeast(1)
                state.scrollToItem((lastVisible + step).coerceAtMost(total - 1))
            }
            repeat(2) { withFrameNanos { } }
            if (anchor in composedAnchors) return
        }
    }
}
