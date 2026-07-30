package com.creationreadingassistant.ui.theme

import android.os.Build

/**
 * Liquid Glass 能力分级守卫。
 *
 * 统一读取点，避免散落的 `Build.VERSION.SDK_INT` 判断。
 * 真 Window 模糊（[android.view.Window.setBlurBehindRadius]）需要 API 31+。
 */
object GlassCapabilities {
    /** 真 Window 模糊是否可用（API 31+）。 */
    val isRealBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 31
}
