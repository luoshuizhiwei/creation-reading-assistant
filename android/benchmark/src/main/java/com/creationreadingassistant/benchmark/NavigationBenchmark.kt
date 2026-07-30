package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun switchHomeToShelf() = measureTransition(from = "首页", to = "书架")

    @Test
    fun switchHomeToInspiration() = measureTransition(from = "首页", to = "灵感")

    @Test
    fun switchHomeToStats() = measureTransition(from = "首页", to = "统计")

    @Test
    fun switchHomeToProfile() = measureTransition(from = "首页", to = "我的")

    @Test
    fun switchShelfToHome() = measureTransition(from = "书架", to = "首页")

    private fun measureTransition(from: String, to: String) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                seedBenchmarkLibrary()
                openTopLevelStart(from)
            },
        ) {
            clickTopLevelNavigation(to)
        }
    }
}
