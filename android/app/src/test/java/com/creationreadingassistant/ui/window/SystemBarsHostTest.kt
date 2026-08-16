package com.creationreadingassistant.ui.window

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 系统栏策略宿主（单一写入点）验收：
 * - 覆盖入栈/出栈 LIFO，pop 回退到 base（即 normal），绝不 show all；
 * - 覆盖激活时 setBase 只保存不写入（阅读器内切主题不闪条）；
 * - updateTop 原位替换不经过 base（切纸色不闪条）；
 * - reassert 重申当前生效策略（onResume / OEM 临时显示恢复）。
 */
class SystemBarsHostTest {

    private val normalLight = SystemBarsPolicy.normal(appDark = false)
    private val normalDark = SystemBarsPolicy.normal(appDark = true)
    private val readerA = SystemBarsPolicy.reader(immersive = true, paperIsLight = true, appDark = false)
    private val readerB = SystemBarsPolicy.reader(immersive = true, paperIsLight = false, appDark = false)

    private fun host(record: MutableList<SystemBarsPolicy>): SystemBarsHost =
        SystemBarsHost { applied -> record += applied }

    @Test
    fun `push 后生效策略为覆盖策略`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        host.push(readerA)

        assertEquals(readerA, host.effective())
        assertEquals(listOf(normalLight, readerA), applied)
    }

    @Test
    fun `pop 回退到 base`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        host.push(readerA)
        host.pop()

        assertEquals(normalLight, host.effective())
        assertEquals(listOf(normalLight, readerA, normalLight), applied)
    }

    @Test
    fun `嵌套覆盖按 LIFO 回退`() {
        val host = host(mutableListOf())
        host.setBase(normalLight)
        host.push(readerA)
        host.push(readerB)
        assertEquals(readerB, host.effective())
        host.pop()
        assertEquals(readerA, host.effective())
        host.pop()
        assertEquals(normalLight, host.effective())
    }

    @Test
    fun `覆盖激活时 setBase 只保存不写入 防主题切换闪条`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        host.push(readerA)
        applied.clear()

        host.setBase(normalDark)

        assertEquals(readerA, host.effective())
        assertEquals(emptyList<SystemBarsPolicy>(), applied)
        host.pop()
        assertEquals(normalDark, host.effective())
        assertEquals(listOf(normalDark), applied)
    }

    @Test
    fun `updateTop 原位替换不经过 base 防切纸色闪条`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        host.push(readerA)
        applied.clear()

        host.updateTop(readerB)

        assertEquals(readerB, host.effective())
        // 只允许出现一次 readerB，不允许出现 base/中间态
        assertEquals(listOf(readerB), applied)
        host.pop()
        assertEquals(normalLight, host.effective())
        assertEquals(listOf(readerB, normalLight), applied)
    }

    @Test
    fun `reassert 重申当前生效策略`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        host.push(readerA)
        applied.clear()

        host.reassert()

        assertEquals(listOf(readerA), applied)
        host.pop()
        applied.clear()
        host.reassert()
        assertEquals(listOf(normalLight), applied)
    }

    @Test
    fun `空栈 pop 幂等回退到 base`() {
        val applied = mutableListOf<SystemBarsPolicy>()
        val host = host(applied)
        host.setBase(normalLight)
        applied.clear()

        host.pop()
        host.pop()

        assertEquals(normalLight, host.effective())
        assertEquals(listOf(normalLight, normalLight), applied)
    }
}
