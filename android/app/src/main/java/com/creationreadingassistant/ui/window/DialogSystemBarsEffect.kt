package com.creationreadingassistant.ui.window

import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 弹层窗口（Compose `Dialog` / `ModalBottomSheet`）应当使用的系统栏策略。
 *
 * 默认 `null` = 不干预：非沉浸场景（普通页面的弹层）保持系统默认，行为与引入本机制前一致。
 * 沉浸阅读器在根部提供策略，弹层内部用 [LocalDialogSystemBarsEffect] 消费。
 *
 * 注意 [staticCompositionLocalOf] 的代价：取值变化会让**整棵子树重组**。所以提供方必须
 * 用 `remember(...)` 固定实例，绝不能在每次重组时新建策略对象。
 */
internal val LocalDialogSystemBarsPolicy = staticCompositionLocalOf<SystemBarsPolicy?> { null }

/**
 * 把 [policy] 写入**当前弹层自己的窗口**。
 *
 * 为什么需要单独一条路径：`ModalBottomSheet` / `Dialog` 都是**独立窗口**，不继承 Activity
 * 窗口的系统栏状态；[SystemBarsEffect] 只写 Activity 窗口，所以沉浸阅读器里一打开面板，
 * 被隐藏的系统状态栏就会重新露出来（弹层窗口自身的默认值是「系统栏可见」）。
 *
 * 与 [SystemBarsEffect] 的分工：那条路径是 **Activity 窗口**的唯一写入点，本路径是
 * **弹层窗口**的唯一写入点；两者复用同一个 [SystemBarsPolicy]，不做二次策略推导。
 *
 * 窗口经 `DialogWindowProvider` 取得（先看承载组合的 View 本身，再沿视图树向上找，最后
 * 兜底看 `LocalContext` —— Compose 的 Dialog 宿主本身就是提供该接口的 Context）。
 * 都取不到（即不在弹层里）时静默不做事。
 *
 * 弹层窗口随弹层销毁，故 [DisposableEffect] 的 onDispose 不还原弹层窗口，而是让
 * [SystemBarsHost] 重申当前生效策略 —— 覆盖「面板关闭后系统栏没跟着收回去」。
 */
@Composable
internal fun DialogSystemBarsEffect(policy: SystemBarsPolicy) {
    val view = LocalView.current
    val context = LocalContext.current
    val host = LocalSystemBarsHost.current
    val window = remember(view, context) {
        view.dialogWindow() ?: (context as? DialogWindowProvider)?.window
    }
    if (window == null) return
    val currentPolicy by rememberUpdatedState(policy)
    DisposableEffect(window) {
        currentPolicy.applyTo(window, view)
        onDispose { host?.reassert() }
    }
    LaunchedEffect(window, policy) {
        policy.applyTo(window, view)
    }
}

/**
 * 消费 [LocalDialogSystemBarsPolicy] 的便捷入口：未提供策略（非沉浸阅读器）时完全不做事，
 * 因此可以安全地放在全应用共用的弹层组件里。
 */
@Composable
internal fun LocalDialogSystemBarsEffect() {
    val policy = LocalDialogSystemBarsPolicy.current
    if (policy != null) DialogSystemBarsEffect(policy)
}

/**
 * 沿视图树向上找承载本弹层的窗口。
 *
 * Compose 的弹层内容挂在自己的宿主下（`DialogLayout` / `ModalBottomSheetWindow`），
 * 它们都实现了 [DialogWindowProvider]；不保证是直接父节点，所以逐级向上找，直到 View 链尽头。
 */
private fun View.dialogWindow(): Window? {
    if (this is DialogWindowProvider) return window
    var parent: ViewParent? = this.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = (parent as? View)?.parent
    }
    return null
}

/**
 * 把弹层策略写入一个**由命令式创建**的弹层窗口（如 `android.app.TimePickerDialog`）。
 *
 * Compose 弹层有组合树可挂，走 [LocalDialogSystemBarsEffect]；平台对话框没有，只能在窗口
 * 真正可用之后（`Dialog.setOnShowListener`）直接调用本函数。
 * 接收者为 `null`（非沉浸场景）或窗口尚不可用时不做任何事，因此调用点无需分支。
 */
internal fun SystemBarsPolicy?.applyToDialogWindow(window: Window?) {
    if (this == null || window == null) return
    applyTo(window, window.decorView)
}
