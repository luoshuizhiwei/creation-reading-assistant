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
    /**
     * CRA-READER-EXIT-SAVE-DRAIN-v1：lifecycle 防重复落库门由 ReaderScreen 持有，
     * 真实退出边界成功 drain 后会把"本次会话已在权威时机落库"标进去，
     * 因此导航后的 onDispose 不再派发一次可能被 scope 取消的重复保存。
     */
    persistGate: ReaderLifecyclePersistGate,
    /**
     * CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：阅读会话结束（本组合销毁）时的清理回调。
     * 在**退出落库之后**于同一 onDispose 块内顺序调用，保证守卫的退出裁决先于其状态释放
     *（不依赖 Compose 对多个 DisposableEffect 的释放顺序）。
     */
    onReaderSessionEnded: () -> Unit = {},
    // Auto-hide
    controlsVisibleForAutoHide: Boolean,
    autoHideSeconds: Int,
    sheetOpenGuard: Boolean,
    autoHideInteractionRevision: Long,
    onAutoHide: () -> Unit,        // called when auto-hide triggers
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnReaderResumed = rememberUpdatedState(onReaderResumed)
    val latestOnPersistProgress = rememberUpdatedState(onPersistProgress)
    val latestOnReaderSessionEnded = rememberUpdatedState(onReaderSessionEnded)

    // ── 1. Lifecycle observer ──────────────────────────────────────────────
    //
    // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v1（保存接缝：onDispose 用过渡布局重算并覆盖权威位置）：
    // 设备实测（j1_55s）显示，用户退出时 ON_PAUSE/ON_STOP 先各落一次**正确**位置（与滚动保存的
    // S2 同值）；随后组合被销毁触发的 onDispose 又落一次，但此刻阅读列表已按**过渡中的更窄视口**
    // （实测宽 1080→940）重新测量、同一像素位移映射到**不同字符偏移**，于是把正确的退出值覆盖成
    // 错误位置（实测 5269→4821）。滚动模式的位置真源是「用户停留在哪」，不是「销毁瞬间的过渡布局」。
    // 修复：本次组合已经因生命周期事件（ON_PAUSE/ON_STOP）落过权威位置时，onDispose 的重复落库
    // 视为冗余并跳过；只有**未经生命周期落库**的销毁（如同 Activity 内路由离开阅读器而只触发
    // composable dispose）才仍用 onDispose 落库——保持该路径的既有保存能力不丢失。
    // 不新增超时、不改判定标准、不改任何 marker/S/R/S2 合同。
    // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2：状态机抽成纯 Kotlin 类 [ReaderLifecyclePersistGate]，
    // 三条真实路径（生命周期已保存后 dispose 不覆盖 / 重新 ON_RESUME 后路由离开仍保存 /
    // 未经生命周期保存时 dispose 仍保存）可在 JVM 上按事件顺序驱动。
    DisposableEffect(lifecycleOwner) {
        // 组合（重新）开始：尚无生命周期落库。绝不把标记跨会话留存 —— 保存标记不得永久
        // 抑制后续保存。
        persistGate.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 回到前台：清掉「已由生命周期落库」标记，使后续同 Activity 内离开阅读器
                    // （只触发 composable dispose、不再有 ON_STOP）仍能经 onDispose 落库。
                    persistGate.onResume()
                    latestOnReaderResumed.value(true)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    latestOnReaderResumed.value(false)
                    val shouldPersist = persistGate.shouldPersistOnPauseOrStop()
                    persistGate.onPauseOrStop()
                    ReaderClampDiagnostics.log("reader_lifecycle_persist", "event" to event.name, "persist" to shouldPersist)
                    if (shouldPersist) {
                        latestOnPersistProgress.value()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            latestOnReaderResumed.value(false)
            // 已经由生命周期事件（权威时机）落过位置 → 不再用销毁瞬间的过渡布局覆盖。
            val persistOnDispose = persistGate.shouldPersistOnDispose()
            ReaderClampDiagnostics.log("reader_dispose_persist", "persist" to persistOnDispose)
            if (persistOnDispose) {
                latestOnPersistProgress.value()
            }
            // CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：落库之后再释放本会话守卫状态
            //（同一 onDispose 块内顺序执行，退出裁决一定先于释放）。
            latestOnReaderSessionEnded.value()
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
    LaunchedEffect(controlsVisibleForAutoHide, autoHideSeconds, sheetOpenGuard, autoHideInteractionRevision) {
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
