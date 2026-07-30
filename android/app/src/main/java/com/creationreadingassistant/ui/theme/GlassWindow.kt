package com.creationreadingassistant.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 将当前 Dialog 宿主窗口的背景做**真模糊**（仅 API31+ 生效）。
 *
 * 通过 [DialogWindowProvider] 拿到 Dialog 的 [android.view.Window]，
 * 调用 `setBlurBehindRadius` 模糊窗口背后的内容。
 * 用于 ModalBottomSheet / AlertDialog 这类 Dialog 容器；**卡片绝不能调用**。
 *
 * @param enabled 是否启用（通常传 `LocalGlassPalette.current != null`，即仅 APPLE）
 */
@Composable
fun Modifier.glassWindowBlur(enabled: Boolean): Modifier {
    if (enabled && GlassCapabilities.isRealBlurSupported) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val palette = LocalGlassPalette.current
        val blurRadiusPx = with(density) {
            (
                if (isSystemInDarkTheme()) {
                    palette?.dark?.blurRadius
                } else {
                    palette?.light?.blurRadius
                } ?: 24.dp
                ).toPx()
        }.toInt()
        DisposableEffect(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val provider = context as? DialogWindowProvider
                provider?.window?.let { win ->
                    try {
                        win.javaClass
                            .getMethod("setBlurBehindRadius", Int::class.javaPrimitiveType)
                            .invoke(win, blurRadiusPx)
                    } catch (_: Throwable) {
                        // 个别 ROM / 低版本无此方法，静默降级为不模糊
                    }
                }
                onDispose {
                    provider?.window?.let { win ->
                        try {
                            win.javaClass
                                .getMethod("setBlurBehindRadius", Int::class.javaPrimitiveType)
                                .invoke(win, 0)
                        } catch (_: Throwable) {
                        }
                    }
                }
            } else {
                onDispose {}
            }
        }
    }
    // 模糊作用于窗口，修饰符本身不绘制任何内容
    return this
}
