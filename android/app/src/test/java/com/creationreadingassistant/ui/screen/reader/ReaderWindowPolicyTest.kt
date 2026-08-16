package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读器窗口策略验收（保留部分）：
 * - 系统栏显隐/图标明暗/挖孔已迁移到 ui/window/SystemBarsPolicy（唯一权威），
 *   本文件不再承载系统栏决策；
 * - 亮度映射保留：-1 跟随系统（BRIGHTNESS_OVERRIDE_NONE=-1.0f），固定值 5..100 → 0..1；
 * - 退出恢复 bars 与 screenBrightness 分别由 SystemBarsHost.pop（回退 normal）与
 *   ReaderPlatformEffects 的 onDispose 执行。
 */
class ReaderWindowPolicyTest {

    @Test
    fun `负亮度映射为跟随系统`() {
        assertEquals(-1f, ReaderWindowPolicy.screenBrightness(-1), 0f)
    }

    @Test
    fun `固定亮度映射到 0 到 1`() {
        assertEquals(0.5f, ReaderWindowPolicy.screenBrightness(50), 0f)
        assertEquals(1f, ReaderWindowPolicy.screenBrightness(100), 0f)
        assertEquals(0.05f, ReaderWindowPolicy.screenBrightness(5), 0f)
    }

    @Test
    fun `越界固定亮度收敛到合法区间`() {
        assertEquals(0.05f, ReaderWindowPolicy.screenBrightness(0), 0f)
        assertEquals(1f, ReaderWindowPolicy.screenBrightness(120), 0f)
    }
}
