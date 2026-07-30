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
                val search = device.wait(Until.findObject(By.desc("搜索书架")), 3_000)
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
                if (device.findObject(By.text("最近阅读")) == null) {
                    device.findObject(By.text("书名"))?.click()
                    device.wait(Until.findObject(By.text("最近阅读")), 3_000)?.click()
                    device.waitForIdle()
                }
            },
        ) {
            val sort = device.wait(Until.findObject(By.text("最近阅读")), 3_000)
            checkNotNull(sort) { "找不到书架排序入口" }
            sort.click()
            val titleSort = device.wait(Until.findObject(By.text("书名")), 3_000)
            checkNotNull(titleSort) { "找不到书名排序选项" }
            titleSort.click()
            device.waitForIdle()
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
