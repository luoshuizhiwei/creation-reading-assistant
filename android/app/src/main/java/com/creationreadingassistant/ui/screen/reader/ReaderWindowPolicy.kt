package com.creationreadingassistant.ui.screen.reader

/**
 * 阅读器窗口策略（纯决策，JVM 可测）。
 *
 * - 系统栏显隐/图标明暗/挖孔已统一迁移到 ui/window/SystemBarsPolicy（唯一权威），
 *   由 [ReaderPlatformEffects] 经 SystemBarsEffect 注册执行，本文件不再承载系统栏决策；
 * - 窗口亮度：readerBrightness < 0 跟随系统（-1.0f，即 WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE），
 *   固定值收敛到 5..100 后映射 0..1；
 * - 退出阅读器：系统栏由 SystemBarsHost.pop 回退 normal，screenBrightness 由
 *   [ReaderPlatformEffects] 的 onDispose 恢复。
 */
internal object ReaderWindowPolicy {
    /** 阅读器窗口亮度：-1 跟随系统（BRIGHTNESS_OVERRIDE_NONE），否则 5..100 → 0..1。 */
    fun screenBrightness(readerBrightness: Int): Float =
        if (readerBrightness < 0) -1f else readerBrightness.coerceIn(5, 100) / 100f
}
