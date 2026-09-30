// bringIntoViewRequester 仍是 foundation 实验性 API；项目里已有同类用法（ReaderEyeCareFocusState），
// 这里按文件级 opt-in，不外溢到调用方。
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 当前要高亮的设置项锚点（搜索结果导航带过来）。
 *
 * 挂在 Route 层的 CompositionLocal 上，各子页的 [SettingsAnchor] 自行比对，
 * 不必给 17 个子页都加一个参数。默认 null = 正常浏览，无待聚焦项。
 */
internal val LocalFocusedSettingsAnchor = staticCompositionLocalOf<String?> { null }

private const val HIGHLIGHT_HOLD_MS = 1800L

/**
 * 设置项锚点容器：被 [LocalFocusedSettingsAnchor] 命中时自动滚入视口并短暂高亮。
 *
 * ## 为什么是容器而不是「给统一组件加 modifier 参数」
 *
 * [com.creationreadingassistant.ui.components.SettingSwitchRow] 这类统一组件
 * **没有 modifier 参数**（除 slider 外），为加锚点给 8 个共享组件各开一个洞，
 * 等于让搜索功能反向改写统一组件库的契约，影响面远超本任务。
 * 包一层 Column 容器即可，代价只是多一层可组合节点。
 *
 * ## 滚动分两段（真机验证后修正）
 *
 * 只挂 `bringIntoViewRequester` 不够：它只能作用于**已组合**的节点，而 LazyColumn
 * 只组合可见项。目标在几屏之外时锚点压根不存在，真机上表现为「跳到了子页却停在页首」。
 * 因此这里在组合时把锚点登记进 [SettingsFocusController]，由控制器先按屏推进列表
 * 把锚点「扫」出来，再交给 requester 精调对齐——全程不需要 `锚点 → item 索引` 映射。
 *
 * ## 减少动态效果（reducedMotion）
 *
 * **仍然滚动**——不滚动等于没跳转。扫描推进用 `scrollToItem`（瞬时，无动画），
 * 高亮则在 reducedMotion 下取消颜色渐变，避免闪烁。
 *
 * ## 折叠区内的目标
 *
 * 条件渲染项（如「滤镜色温」只在护眼滤镜开启后出现）不挂自身锚点，
 * 而是统一挂到**必然存在的父卡片锚点**上（见 [SettingsSearchIndex] 文件头），
 * 因此不存在「目标折叠着、滚过去什么都没有」的情况。
 */
@Composable
internal fun SettingsAnchor(
    anchor: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focused = LocalFocusedSettingsAnchor.current
    val controller = LocalSettingsFocusController.current

    // 无论是否命中都登记：控制器需要知道目标何时被组合出来
    if (controller != null) {
        DisposableEffect(controller, anchor) {
            controller.onAnchorComposed(anchor)
            onDispose { controller.onAnchorDisposed(anchor) }
        }
    }

    if (focused != anchor) {
        // 未命中：零装饰，直接渲染，不给常规浏览路径加额外节点
        Column(modifier = modifier) { content() }
        return
    }

    val reducedMotion = rememberReducedMotion()
    val requester = remember { BringIntoViewRequester() }
    var highlighted by remember { mutableStateOf(true) }

    LaunchedEffect(anchor) {
        // 滚动由 Route 层的 controller 驱动（它在有路由参数时就知道目标是谁，
        // 不依赖本节点是否已组合）。本节点只负责：一旦被扫进组合，把自己对齐进视口。
        requester.bringIntoView()
        delay(HIGHLIGHT_HOLD_MS)
        highlighted = false
    }

    val overlay by animateColorAsState(
        targetValue = if (highlighted) Color(0xFFF59E0B).copy(alpha = 0.26f) else Color.Transparent,
        animationSpec = tween(if (reducedMotion) 0 else MotionTokens.Base),
        label = "settingsAnchorHighlight",
    )

    Column(
        modifier = modifier
            .bringIntoViewRequester(requester)
            .background(overlay),
    ) {
        content()
    }
}
