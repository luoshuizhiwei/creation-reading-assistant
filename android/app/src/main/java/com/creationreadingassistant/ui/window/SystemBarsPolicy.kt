package com.creationreadingassistant.ui.window

/**
 * 全局系统栏策略（纯数据，JVM 可测）。
 *
 * 职责边界：
 * - 只描述「想要什么」，不接触任何 Android API；写入窗口由 [SystemBarsEffect] 唯一执行；
 * - [normal]：普通页面——显示状态栏、隐藏导航栏、transient swipe 临时查看；
 *   图标明暗跟随应用主题（appDark → 浅色图标），挖孔 DEFAULT；
 * - [reader]：阅读器——immersive=true 隐藏全部系统栏、SHORT_EDGES、图标跟随纸张；
 *   immersive=false 与 [normal] 完全一致（等同普通页），避免切纸色/主题时闪条。
 */
data class SystemBarsPolicy(
    val statusBarsVisible: Boolean,
    val navigationBarsVisible: Boolean,
    /** true = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE：隐藏的栏可被 transient swipe 临时唤出。 */
    val swipeToReveal: Boolean,
    val cutoutMode: SystemBarsCutoutMode,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean,
) {
    companion object {
        fun normal(appDark: Boolean): SystemBarsPolicy = SystemBarsPolicy(
            statusBarsVisible = true,
            navigationBarsVisible = false,
            swipeToReveal = true,
            cutoutMode = SystemBarsCutoutMode.DEFAULT,
            lightStatusBars = !appDark,
            lightNavigationBars = !appDark,
        )

        /**
         * 阅读器策略：immersive=true 全隐 + SHORT_EDGES + 图标跟随纸张；
         * immersive=false 等同 normal(appDark)（状态栏显示、导航栏隐藏、DEFAULT 挖孔）。
         * appDark 只用于非沉浸时的回退外观，保证两种模式严格一致。
         */
        fun reader(immersive: Boolean, paperIsLight: Boolean, appDark: Boolean): SystemBarsPolicy =
            if (immersive) {
                SystemBarsPolicy(
                    statusBarsVisible = false,
                    navigationBarsVisible = false,
                    swipeToReveal = true,
                    cutoutMode = SystemBarsCutoutMode.SHORT_EDGES,
                    lightStatusBars = paperIsLight,
                    lightNavigationBars = paperIsLight,
                )
            } else {
                normal(appDark)
            }
    }
}

enum class SystemBarsCutoutMode { DEFAULT, SHORT_EDGES }
