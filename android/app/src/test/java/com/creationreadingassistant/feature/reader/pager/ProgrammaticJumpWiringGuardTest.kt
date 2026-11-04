package com.creationreadingassistant.feature.reader.pager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.2：程序化跳转失效通道的**接线级**回归门（源码级）。
 *
 * 为什么需要它：`ProgrammaticJumpInvalidationTest` 只钉住纯函数
 * [programmaticJumpInvalidation] 的产出，若有人把宿主的接线改回
 * `onSelectionInvalidated = onGesturePageTurn`（正是 F2.1 复核的 ST-1 缺陷），
 * 纯函数测试**仍然全绿** —— 缺陷会从门禁下溜过。
 *
 * 本仓已有同类源码级结构断言的先例（如 `StatsFileStructureTest`、
 * `InspirationFileStructureTest`），JVM 单测工作目录 = app 模块根，
 * 因此这里的相对路径是真实可执行的。
 */
class ProgrammaticJumpWiringGuardTest {

    private val hostSource =
        File("src/main/java/com/creationreadingassistant/feature/reader/pager/PagedTxtReaderHost.kt")

    @Test
    fun `the host wires programmatic invalidation to the selection channel`() {
        assertTrue("宿主源码存在", hostSource.exists())
        val content = hostSource.readText()

        assertTrue(
            "程序化跳转的失效通知必须接到 programmaticJumpInvalidation(onSelect)",
            content.contains("onSelectionInvalidated = programmaticJumpInvalidation(onSelect)"),
        )
    }

    @Test
    fun `the host never routes programmatic invalidation through the gesture channel`() {
        val content = hostSource.readText()

        assertFalse(
            "程序化跳转不得接到 onGesturePageTurn —— 那会顺带隐藏顶栏/底栏（F2.1 ST-1）",
            content.contains("onSelectionInvalidated = onGesturePageTurn"),
        )
    }

    @Test
    fun `the gesture channel is still wired for real page turns`() {
        // 反向保护：修复 A 不能把真正的手势翻页也一起摘掉。
        val content = hostSource.readText()

        assertTrue(
            "手势翻页仍必须接到 onGesturePageTurn",
            content.contains("onGesturePageTurn = onGesturePageTurn"),
        )
    }
}
