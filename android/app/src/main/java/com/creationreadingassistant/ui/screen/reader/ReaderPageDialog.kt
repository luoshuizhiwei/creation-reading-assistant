package com.creationreadingassistant.ui.screen.reader

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.window.LocalDialogSystemBarsEffect

/** 全屏附页入场时长：右侧24dp位移 + 淡入（READER-PAGES-v1 合同 §4）。 */
private const val PAGE_ENTER_MS = 180

/** 全屏附页退场时长：入场反向。 */
private const val PAGE_EXIT_MS = 140

/** 入场水平位移（向右滑入）。 */
private val PAGE_ENTER_OFFSET = 24.dp

/**
 * 阅读附页全屏容器（READER-PAGES-v1）。
 *
 * 承载 TOC / SETTINGS / THEME / INSPIRATION 四个从底部弹层升级出的完整页面：
 * - Compose [Dialog] + `usePlatformDefaultWidth=false` 满窗口；**禁止**外部点击/系统Back直接
 *   关闭——关闭只能由页面内返回链发起（走既有 CloseSheet 业务入口），键盘/子页/草稿检查
 *   全部在页面层完成；
 * - 独立 Window 不继承 Activity 系统栏策略，沿用 M-BUG-11 的 [LocalDialogSystemBarsEffect]
 *   把阅读器策略写进本窗口；`decorFitsSystemWindows=false` 后页面根一次性消费
 *   safeDrawing（除 IME）+ [imePadding]，子层不再重复抬升；
 * - 只包裹现有 reader owner：不新建 Activity/NavHost/ViewModel/阅读引擎，不 dispose 正文。
 *
 * 进出过渡（合同 §4）：入场右侧 [PAGE_ENTER_OFFSET] 位移+淡入 [PAGE_ENTER_MS]ms，退场反向
 * [PAGE_EXIT_MS]ms；减少动态效果时直接到终态。退场期间容器保活一拍（[rendered] 滞后于
 * [target]），动画走完才真正移除 Dialog——单个 Animatable 同时驱动两侧，不使用动画回调
 * 执行任何业务。
 *
 * @param target 当前应展示的附页（[ReaderSheet] 的四个页面分支之一）；null = 请求关闭。
 * @param onDismiss 业务关闭入口（= 既有 CloseSheet）。
 * @param content 按 [ReaderSheet] 渲染页面内容；页面自持唯一的 BackHandler。
 */
@Composable
internal fun ReaderPageDialogHost(
    target: ReaderSheet?,
    paper: ReaderPaperPalette,
    onDismiss: () -> Unit,
    content: @Composable (ReaderSheet) -> Unit,
) {
    var rendered by remember { mutableStateOf(target) }
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target, reducedMotion) {
        when {
            target != null -> {
                rendered = target
                if (reducedMotion) {
                    progress.snapTo(1f)
                } else {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(PAGE_ENTER_MS, easing = MotionTokens.StandardEasing))
                }
            }

            rendered != null -> {
                if (reducedMotion) {
                    progress.snapTo(0f)
                } else {
                    progress.animateTo(0f, tween(PAGE_EXIT_MS, easing = MotionTokens.StandardEasing))
                }
                rendered = null
            }
        }
    }
    rendered?.let { page ->
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            // M-BUG-11：弹层是独立 Window，把阅读器的系统栏策略写进本窗口（沉浸态延续隐藏）。
            LocalDialogSystemBarsEffect()
            val view = LocalView.current
            // API<30 的窗口不会自动派发 IME insets，靠 adjustResize 换取 insets；
            // API30+ 上 decorFitsSystemWindows=false 时该标志被忽略（纯 insets 派发），无害。
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                )
            }
            val density = LocalDensity.current
            val enterOffsetPx = with(density) { PAGE_ENTER_OFFSET.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Dialog 窗口默认透明：不铺纸面底色的话，底层阅读正文/底部弹层会透出来。
                    .background(paper.bg)
                    // 页面根一次性消费安全区（状态栏/导航手势/挖孔），IME 留给 imePadding，
                    // 全页只有这一层消费，避免双重抬高。
                    .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                    .imePadding()
                    .graphicsLayer {
                        translationX = (1f - progress.value) * enterOffsetPx
                        alpha = progress.value
                    },
            ) {
                content(page)
            }
        }
    }
}

/**
 * 附页统一的系统 Back 入口（合同 §3.4 优先级的第一层）。
 *
 * 有键盘时先收键盘（部分 OEM 会把「收起 IME」与「返回」同时派发，这里显式守卫，
 * 保证键盘收起不连带触发页面返回）；键盘收起后才执行页面各自的返回链
 * （子页→根→关闭，或草稿确认）。每个附页内容内只装一个本处理器，与子组件
 * 自己的 BackHandler（如设置子页）构成 Compose 回调栈——后注册者优先，
 * 不存在两个处理器同时触发。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderPageBackHandler(onBack: () -> Unit) {
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val latestOnBack by rememberUpdatedState(onBack)
    BackHandler(enabled = true) {
        if (imeVisible) {
            keyboard?.hide()
        } else {
            latestOnBack()
        }
    }
}
