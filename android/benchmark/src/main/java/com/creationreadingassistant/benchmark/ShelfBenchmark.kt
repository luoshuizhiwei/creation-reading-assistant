package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ShelfBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollShelf() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                seedBenchmarkLibrary()
                openShelfForBenchmark()
            },
        ) {
            val width = device.displayWidth
            val height = device.displayHeight
            repeat(4) {
                device.swipe(width / 2, height * 4 / 5, width / 2, height / 4, 12)
            }
            repeat(2) {
                device.swipe(width / 2, height / 4, width / 2, height * 4 / 5, 12)
            }
            device.waitForIdle()
        }
    }

    @Test
    fun searchShelf() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                seedBenchmarkLibrary()
                openShelfForBenchmark()
            },
        ) {
            val existingField = device.findObject(By.clazz("android.widget.EditText"))
            if (existingField == null) {
                val search = device.wait(Until.findObject(By.desc("搜索")), 3_000)
                checkNotNull(search) { "找不到书架搜索入口" }
                search.click()
            }
            val field = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")),
                3_000,
            )
            checkNotNull(field) { "找不到书架搜索输入框" }
            field.text = "测试"
            device.waitForIdle()
            val refreshedField = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")),
                3_000,
            )
            checkNotNull(refreshedField) { "搜索结果刷新后输入框消失" }
            refreshedField.clear()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * 书架排序切换基准（新交互链等效版）。
     *
     * 旧口径：书架页排序 chip（“最近阅读”）→ 排序弹层 → 选“书名”。
     * 新交互已改版为全屏导航：书架页标题区“打开书架整理”→ 书架整理页“排序”行 →
     * 排序选择页（ShelfSelectionRoute）选“书名”，随后逐层返回书架页。
     * 测量指标口径不变（FrameTimingMetric → frameOverrunMs / frameDurationCpuMs）。
     */
    @Test
    fun sortLargeShelfByTitle() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                seedBenchmarkLibrary()
                openShelfForBenchmark()
                // 与旧基准 setup 对齐：确保起点排序为“最近阅读”，使每个迭代都是真实重排。
                applyShelfSortMode("最近阅读")
            },
        ) {
            applyShelfSortMode("书名")
        }
    }

    @Test
    fun scrollLargeShelfWithWarmCoverCache() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                seedBenchmarkLibrary()
                openShelfForBenchmark()
                val width = device.displayWidth
                val height = device.displayHeight
                repeat(3) {
                    device.swipe(width / 2, height * 4 / 5, width / 2, height / 4, 10)
                }
                repeat(3) {
                    device.swipe(width / 2, height / 4, width / 2, height * 4 / 5, 10)
                }
                device.waitForIdle()
            },
        ) {
            val width = device.displayWidth
            val height = device.displayHeight
            repeat(5) {
                device.swipe(width / 2, height * 4 / 5, width / 2, height / 4, 10)
            }
            device.waitForIdle()
        }
    }

}
