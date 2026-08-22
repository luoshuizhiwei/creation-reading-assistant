package com.creationreadingassistant.ui.screen.stats

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stats 拆分结构的源码级验收（自 androidTest 迁入 JVM）。
 * 原 androidTest 版本用仓库根相对路径，在设备上永远 ENOENT；
 * JVM 单测工作目录 = app 模块根，结构断言在此才真实可执行。
 */
class StatsFileStructureTest {

    @Test
    fun oldEntryFileIsTinyForwarder() {
        val f = File("src/main/java/com/creationreadingassistant/ui/screen/StatsScreen.kt")
        assertTrue("旧入口文件存在", f.exists())
        val lines = f.readLines().size
        assertTrue("旧入口 $lines 行 ≤ 90（含 typealias 转发）", lines <= 90)
    }

    @Test
    fun screenFileDoesNotContainBucketingOrRangeCalculations() {
        val f = File("src/main/java/com/creationreadingassistant/ui/screen/stats/StatsScreen.kt")
        assertTrue("stats/StatsScreen 文件存在", f.exists())
        val content = f.readText()
        listOf(
            "computeStats", "buildTrend", "buildRange", "inRange", "bucketByDate",
            "fillDaily", "fillWeekly", "fillMonthly", "fillTotal", "computeStreak",
            ".filter {", ".map { session", ".groupBy",
        ).forEach { token ->
            assertTrue(
                "stats/StatsScreen 不得包含 '$token'（计算应在 VM/StatsPage）",
                !content.contains(token),
            )
        }
    }

    @Test
    fun computeStatsIsNowDefinedInStatsPageNotOldScreen() {
        val old = File("src/main/java/com/creationreadingassistant/ui/screen/StatsScreen.kt")
        val new = File("src/main/java/com/creationreadingassistant/ui/screen/stats/StatsPage.kt")
        assertTrue("旧入口存在", old.exists())
        assertTrue("StatsPage 存在", new.exists())
        assertTrue("旧 StatsScreen 只是转发，不再内联 computeStats 实现", !old.readText().contains("bucketByDate"))
        assertTrue("computeStats 实现在 StatsPage.kt（趋势分桶/连续天数均在 VM 层）", new.readText().contains("bucketByDate"))
    }
}
