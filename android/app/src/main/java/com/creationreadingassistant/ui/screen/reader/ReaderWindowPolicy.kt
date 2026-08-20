package com.creationreadingassistant.ui.screen.reader

/**
 * 阅读器窗口策略（纯决策，JVM 可测）。
 *
 * - 系统栏显隐/图标明暗/挖孔已统一迁移到 ui/window/SystemBarsPolicy（唯一权威），
 *   由 [ReaderPlatformEffects] 经 SystemBarsEffect 注册执行，本文件不再承载系统栏决策；
 * - 窗口亮度：readerBrightness < 0 跟随系统（-1.0f，即 BRIGHTNESS_OVERRIDE_NONE），
 *   readerBrightness in 0..100 分两段：
 *     5..100 → 直接走 [screenBrightness]（线性 0.05..1.0），[overlayDimmingAlpha]=0；
 *     0..4   → [screenBrightness] 保持最低 0.05 系统 floor，再叠一层黑色压暗遮罩
 *              [overlayDimmingAlpha] 补缺口（0.86 → 0），模拟 0–4% 的进一步压暗。
 *   这样设置滑条拉到 0% 时夜间真的能非常暗（0.05 × (1-0.86) ≈ 0.007 ≈ 0.7%）。
 * - 退出阅读器：系统栏由 SystemBarsHost.pop 回退 normal，screenBrightness 由
 *   [ReaderPlatformEffects] 的 onDispose 恢复。
 */
internal object ReaderWindowPolicy {

    /** 系统窗口亮度：-1 跟随系统；否则 5..100 → 0.05..1.0；0..4 夹住到 0.05（系统 floor）。 */
    fun screenBrightness(readerBrightness: Int): Float = when {
        readerBrightness < 0 -> -1f
        readerBrightness <= 4 -> 0.05f
        else -> readerBrightness.coerceIn(5, 100) / 100f
    }

    /**
     * 额外压暗遮罩的黑色 alpha（0..1，SrcOver 叠加）。
     * - 0..4：线性从 0.86 → 0（补足 screenBrightness 到不了的 0–5% 范围）；
     * - 其他值：0（完全透明，不压暗）。
     */
    fun overlayDimmingAlpha(readerBrightness: Int): Float = when {
        readerBrightness < 0 -> 0f
        readerBrightness >= 5 -> 0f
        else -> {
            // 0 → 0.86，4 → 0，线性衰减
            (4 - readerBrightness.coerceIn(0, 4)) * 0.215f
        }
    }

    /**
     * 把 readerBrightness 显示为用户可读的百分比（跟随系统 → "跟随系统"，否则 "N%"）。
     * 仅 UI 文案用，显示逻辑集中一处避免多处散写。
     */
    fun displayPercent(readerBrightness: Int): String =
        if (readerBrightness < 0) "跟随系统" else "${readerBrightness.coerceIn(0, 100)}%"
}
