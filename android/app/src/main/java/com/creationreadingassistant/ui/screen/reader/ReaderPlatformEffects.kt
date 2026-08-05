package com.creationreadingassistant.ui.screen.reader

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeys
import kotlinx.coroutines.delay

/**
 * 阅读器平台级 Effects 聚合：生命周期、常亮、沉浸模式、亮度、窗口底色、音量键、自动隐藏。
 *
 * 从 ReaderScreen.kt 提取，目的是将平台副作用与 UI 渲染解耦，降低主 Composable 行数。
 */
@Composable
internal fun ReaderPlatformEffects(
    // Keep awake
    keepAwake: Boolean,
    // Immersive mode
    immersiveMode: Boolean,
    controlsVisible: Boolean,
    paperIsLight: Boolean,
    appDark: Boolean,
    // Brightness
    readerBrightness: Int,         // 0-100, -1 for system default
    // Window background
    paperBgColor: Color,
    // Volume keys
    volumeKeyPaging: Boolean,
    onVolumeUp: () -> Boolean,     // returns true if consumed
    onVolumeDown: () -> Boolean,   // returns true if consumed
    // Lifecycle
    onReaderResumed: (Boolean) -> Unit,
    onPersistProgress: () -> Unit,
    // Auto-hide
    controlsVisibleForAutoHide: Boolean,
    autoHideSeconds: Int,
    sheetOpenGuard: Boolean,
    onAutoHide: () -> Unit,        // called when auto-hide triggers
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnReaderResumed = rememberUpdatedState(onReaderResumed)
    val latestOnPersistProgress = rememberUpdatedState(onPersistProgress)

    // ── 1. Lifecycle observer ──────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> latestOnReaderResumed.value(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    latestOnReaderResumed.value(false)
                    latestOnPersistProgress.value()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            latestOnPersistProgress.value()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── 2. Keep screen on ──────────────────────────────────────────────────
    DisposableEffect(keepAwake) {
        val window = (context as? Activity)?.window
        if (keepAwake) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── 3. Immersive mode + system bar hide/show + cutout handling ─────────
    DisposableEffect(immersiveMode, controlsVisible, paperIsLight, appDark) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        val hide = immersiveMode && !controlsVisible
        // 始终允许内容延伸到挖孔区域（SHORT_EDGES），由 Compose 侧 windowInsetsPadding(displayCutout) 负责避让
        if (window != null && Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 系统栏图标明暗跟随纸张：亮纸→深色图标，夜读→浅色图标（禁止明暗断层）。
            controller.isAppearanceLightStatusBars = paperIsLight
            controller.isAppearanceLightNavigationBars = paperIsLight
            if (hide) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // 离开阅读器：恢复应用浅/深外观对应的系统栏图标明暗（!appDark）。
            controller?.let {
                it.isAppearanceLightStatusBars = !appDark
                it.isAppearanceLightNavigationBars = !appDark
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null && Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    // ── 4. Window brightness ───────────────────────────────────────────────
    DisposableEffect(readerBrightness) {
        val window = (context as? Activity)?.window
        val original = window?.attributes?.screenBrightness ?: -1f
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (readerBrightness < 0) {
                    // 跟随系统亮度（不覆盖）
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    readerBrightness / 100f
                }
            }
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
        }
    }

    // ── 5. Window background color ─────────────────────────────────────────
    val originalWindowBg = remember { (context as? Activity)?.window?.decorView?.background }
    DisposableEffect(paperBgColor) {
        val window = (context as? Activity)?.window
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(paperBgColor.toArgb()))
        onDispose { window?.setBackgroundDrawable(originalWindowBg) }
    }

    // ── 6. Volume key handler registration/release ─────────────────────────
    DisposableEffect(volumeKeyPaging, onVolumeUp, onVolumeDown) {
        ReaderHardwareKeys.handler = handler@{ direction ->
            when (direction) {
                -1 -> if (onVolumeUp()) return@handler true else false
                1 -> if (onVolumeDown()) return@handler true else false
                else -> false
            }
        }
        onDispose {
            ReaderHardwareKeys.handler = null
        }
    }

    // ── 7. Menu auto-hide timer ────────────────────────────────────────────
    LaunchedEffect(controlsVisibleForAutoHide, autoHideSeconds, sheetOpenGuard) {
        val secs = autoHideSeconds
        if (controlsVisibleForAutoHide && secs > 0 && !sheetOpenGuard) {
            delay(secs * 1000L)
            onAutoHide()
        }
    }
}
