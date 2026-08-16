package com.creationreadingassistant.ui.screen.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeys
import com.creationreadingassistant.ui.window.SystemBarsEffect
import com.creationreadingassistant.ui.window.SystemBarsPolicy
import kotlinx.coroutines.delay

/**
 * 阅读器平台级 Effects 聚合：生命周期、常亮、系统栏策略、亮度、窗口底色、音量键、自动隐藏。
 *
 * 从 ReaderScreen.kt 提取，目的是将平台副作用与 UI 渲染解耦，降低主 Composable 行数。
 *
 * 系统栏显隐/图标明暗/挖孔已统一委托给 [SystemBarsEffect]（唯一 WindowInsetsControllerCompat
 * 写入点）：进入阅读器 push reader 策略，退出/重组由宿主 pop 回退 normal，绝不 show all。
 * [controlsVisible] 参数保留仅因调用链兼容，系统栏不再依赖控件显隐。
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
    // Screen orientation lock: system | portrait | landscape
    screenOrientation: String,
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

    // ── 3. 全局系统栏策略（唯一写入点 SystemBarsEffect）────────────────────────
    // immersive=true → 全隐 + SHORT_EDGES + 图标跟随纸张；immersive=false → 等同 normal；
    // 退出/重组由宿主 pop 回退 normal；ON_RESUME 由宿主重申当前策略。
    SystemBarsEffect(
        policy = SystemBarsPolicy.reader(
            immersive = immersiveMode,
            paperIsLight = paperIsLight,
            appDark = appDark,
        ),
    )

    // ── 4. Window brightness ───────────────────────────────────────────────
    DisposableEffect(readerBrightness) {
        val window = (context as? Activity)?.window
        val original = window?.attributes?.screenBrightness ?: -1f
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = ReaderWindowPolicy.screenBrightness(readerBrightness)
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

    // ── 8. 屏幕方向锁定（USER_*：尊重系统旋转锁定，允许 180° 翻转）────────
    DisposableEffect(screenOrientation) {
        val activity = context as? Activity
        val original = activity?.requestedOrientation
            ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = when (screenOrientation) {
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = original }
    }
}
