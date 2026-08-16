package com.creationreadingassistant.ui.window

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 组合树共享的策略宿主；由首个 [SystemBarsEffect] 创建并提供，保证全应用只有一处写入。 */
internal val LocalSystemBarsHost = staticCompositionLocalOf<SystemBarsHost?> { null }

/**
 * 全局系统栏唯一宿主（Compose 侧），也是全应用唯一的 WindowInsetsControllerCompat 写入点。
 *
 * - [base] = true：注册全局 normal 策略（MainActivity）；首个宿主还会创建 [SystemBarsHost]
 *   并通过 [LocalSystemBarsHost] 提供给整棵组合树；
 * - [base] = false：注册覆盖策略（阅读器）：进入 push、策略变化原位 updateTop、退出 pop；
 * - ON_RESUME 重申当前生效策略：OEM 临时显示系统栏、无障碍拉起系统栏后自动恢复；
 * - 恢复只回到 normal（status 显示 / nav 隐藏），绝不调用 show(systemBars())。
 */
@Composable
fun SystemBarsEffect(
    policy: SystemBarsPolicy,
    base: Boolean = false,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(context) { (context as? Activity)?.window }
    val existingHost = LocalSystemBarsHost.current
    if (existingHost == null) {
        val host = remember(window, view) {
            SystemBarsHost { candidate -> candidate.applyTo(window, view) }
        }
        CompositionLocalProvider(LocalSystemBarsHost provides host) {
            SystemBarsRegistration(policy, base, host, lifecycleOwner)
        }
    } else {
        SystemBarsRegistration(policy, base, existingHost, lifecycleOwner)
    }
}

@Composable
private fun SystemBarsRegistration(
    policy: SystemBarsPolicy,
    base: Boolean,
    host: SystemBarsHost,
    lifecycleOwner: LifecycleOwner,
) {
    val currentPolicy by rememberUpdatedState(policy)
    DisposableEffect(host, base) {
        if (base) host.setBase(currentPolicy) else host.push(currentPolicy)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) host.reassert()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!base) host.pop()
        }
    }
    // 策略变化原位替换：绝不 pop→push（会先闪回 base）；base 变化在覆盖激活时也只保存。
    LaunchedEffect(policy, base) {
        if (base) host.setBase(policy) else host.updateTop(policy)
    }
}

/**
 * 把纯策略写入窗口（唯一 WindowInsetsControllerCompat 写入点）。
 * 写入项：show/hide statusBars、show/hide navigationBars、transient swipe behavior、
 * 图标明暗、挖孔模式（API 28+）。
 */
internal fun SystemBarsPolicy.applyTo(window: Window?, view: View?) {
    if (window == null || view == null) return
    val controller = WindowCompat.getInsetsController(window, view)
    controller.systemBarsBehavior = if (swipeToReveal) {
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
    controller.isAppearanceLightStatusBars = lightStatusBars
    controller.isAppearanceLightNavigationBars = lightNavigationBars
    if (statusBarsVisible) {
        controller.show(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
    if (navigationBarsVisible) {
        controller.show(WindowInsetsCompat.Type.navigationBars())
    } else {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = when (cutoutMode) {
                SystemBarsCutoutMode.DEFAULT -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                SystemBarsCutoutMode.SHORT_EDGES -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
