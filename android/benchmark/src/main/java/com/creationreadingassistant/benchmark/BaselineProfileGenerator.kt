package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartup() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            dismissOnboardingIfPresent()
        }
    }

    @Test
    fun generateCriticalUserJourneys() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
        ) {
            seedBenchmarkLibrary()
            pressHome()
            startActivityAndWait()
            dismissOnboardingIfPresent()
            listOf("书架", "灵感", "统计", "我的", "首页").forEach { label ->
                clickTopLevelNavigation(label)
            }
        }
    }

    /**
     * 阅读热路径：打开书籍 → 首屏排版 → 连续翻页采样相邻章排版。
     *
     * **为什么必须单独有此 journey**：Baseline Profile 只采样 journey 真正走过的路径。若不开书，
     * Compose 组合、文档加载、分页排版、首屏绘制这条最重的热路径不会进入 profile，
     * release 首次开书只能主线程解释/JIT，产生明显加载卡顿。
     * `ReaderBenchmark.kt` 的 Macrobenchmark 只做「测量」，不会让 release 包变快，两者不可互替。
     *
     * 分两个测试分别覆盖 EPUB 与 TXT：两者的文档加载链路不同
     * （EPUB 走章节解析 + 分页，TXT 走 TextStreamLoader 有界分段 + 索引），
     * 合并到一个 journey 会让第二次开书命中已缓存的路径而采样不全。
     */
    @Test
    fun generateReaderJourneyForEpub() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
        ) {
            seedBenchmarkLibrary()
            openBenchmarkBookForProfile(openOldest = false)
            turnBenchmarkPages()
        }
    }

    @Test
    fun generateReaderJourneyForTxt() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
        ) {
            seedBenchmarkLibrary()
            openBenchmarkBookForProfile(openOldest = true)
            turnBenchmarkPages()
        }
    }
}
