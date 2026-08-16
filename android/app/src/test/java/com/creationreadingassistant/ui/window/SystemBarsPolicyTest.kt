package com.creationreadingassistant.ui.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局系统栏策略（纯数据）验收：
 * - normal：状态栏可见、导航栏隐藏、transient swipe、挖孔 DEFAULT、图标跟随应用主题；
 * - reader(immersive=true)：全部系统栏隐藏、SHORT_EDGES、图标跟随纸张；
 * - reader(immersive=false)：与 normal(appDark) 完全一致（等同普通页，切纸色/主题不闪条）。
 */
class SystemBarsPolicyTest {

    @Test
    fun `normal 显示状态栏并隐藏导航栏且允许 transient swipe`() {
        val policy = SystemBarsPolicy.normal(appDark = false)
        assertTrue(policy.statusBarsVisible)
        assertFalse(policy.navigationBarsVisible)
        assertTrue(policy.swipeToReveal)
        assertEquals(SystemBarsCutoutMode.DEFAULT, policy.cutoutMode)
    }

    @Test
    fun `normal 图标明暗跟随应用主题`() {
        val light = SystemBarsPolicy.normal(appDark = false)
        assertTrue(light.lightStatusBars)
        assertTrue(light.lightNavigationBars)

        val dark = SystemBarsPolicy.normal(appDark = true)
        assertFalse(dark.lightStatusBars)
        assertFalse(dark.lightNavigationBars)
    }

    @Test
    fun `reader 沉浸模式隐藏全部系统栏`() {
        val policy = SystemBarsPolicy.reader(immersive = true, paperIsLight = true, appDark = false)
        assertFalse(policy.statusBarsVisible)
        assertFalse(policy.navigationBarsVisible)
        assertTrue(policy.swipeToReveal)
        assertEquals(SystemBarsCutoutMode.SHORT_EDGES, policy.cutoutMode)
    }

    @Test
    fun `reader 沉浸模式图标明暗跟随纸张`() {
        val lightPaper = SystemBarsPolicy.reader(immersive = true, paperIsLight = true, appDark = true)
        assertTrue(lightPaper.lightStatusBars)
        assertTrue(lightPaper.lightNavigationBars)

        val darkPaper = SystemBarsPolicy.reader(immersive = true, paperIsLight = false, appDark = false)
        assertFalse(darkPaper.lightStatusBars)
        assertFalse(darkPaper.lightNavigationBars)
    }

    @Test
    fun `reader 非沉浸完全等同 normal`() {
        assertEquals(
            SystemBarsPolicy.normal(appDark = true),
            SystemBarsPolicy.reader(immersive = false, paperIsLight = false, appDark = true),
        )
        assertEquals(
            SystemBarsPolicy.normal(appDark = false),
            SystemBarsPolicy.reader(immersive = false, paperIsLight = true, appDark = false),
        )
    }

    @Test
    fun `reader 沉浸与非沉浸是不同策略`() {
        val immersive = SystemBarsPolicy.reader(immersive = true, paperIsLight = false, appDark = true)
        val nonImmersive = SystemBarsPolicy.reader(immersive = false, paperIsLight = false, appDark = true)
        assertNotEquals(nonImmersive, immersive)
    }
}
