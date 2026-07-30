package com.creationreadingassistant.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 视觉样式枚举。支持在默认「墨韵·素笺」、Apple 风格、Web 版风格之间切换。
 */
enum class VisualStyle {
    /** 默认「墨韵·素笺」主题 —— 现有样式不变 */
    DEFAULT,

    /** Apple/iOS 风格（T1 已收敛：仅保留枚举常量用于持久化兼容回退，实现统一回退 DEFAULT） */
    APPLE,

    /** Web 版移动端风格（T1 已收敛：同上） */
    WEB;

    companion object {
        /**
         * 从持久化字符串解析视觉样式。Apple / Web / 未知 / 空 一律回退 DEFAULT，
         * 保证旧版存储值不失效（见设计实施稿 §7）。
         */
        fun fromStored(name: String?): VisualStyle = when (name) {
            "DEFAULT" -> DEFAULT
            else -> DEFAULT
        }
    }
}

/** CompositionLocal：当前视觉样式，默认 DEFAULT */
val LocalVisualStyle = staticCompositionLocalOf { VisualStyle.DEFAULT }

/**
 * CompositionLocal：视觉样式状态持有者（MutableState）。
 * 由 [AppNavigation] 在全局创建并提供，任意子页面（如首页切换按钮）均可读写，
 * 实现全局统一切换。值本身是 MutableState 引用（不变），.value 变化由快照系统驱动重组。
 */
val LocalVisualStyleState = staticCompositionLocalOf<MutableState<VisualStyle>> {
    error("LocalVisualStyleState 必须由 AppNavigation 提供")
}
