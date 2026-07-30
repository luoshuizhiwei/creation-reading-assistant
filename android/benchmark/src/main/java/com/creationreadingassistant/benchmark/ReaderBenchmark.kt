package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReaderBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @OptIn(ExperimentalMetricApi::class)
    fun openTestEpubToFirstContent() = measureReaderOpen(openOldest = false)

    @Test
    @OptIn(ExperimentalMetricApi::class)
    fun openTestTxtToFirstContent() = measureReaderOpen(openOldest = true)

    @Test
    fun openReaderMenu() = measureReaderInteraction(openOldest = false, hideControls = true) {
        val width = device.displayWidth
        val height = device.displayHeight
        device.click(width / 2, height / 2)
        check(device.wait(Until.hasObject(By.desc("下一章")), 3_000)) {
            "阅读器菜单未出现"
        }
        device.waitForIdle()
        device.click(width / 2, height / 2)
        check(device.wait(Until.gone(By.desc("下一章")), 3_000)) {
            "阅读器菜单未关闭"
        }
        device.waitForIdle()
    }

    @Test
    fun turnTestEpubPage() = measureReaderInteraction(openOldest = false, hideControls = true) {
        device.click(device.displayWidth * 4 / 5, device.displayHeight / 2)
        device.waitForIdle()
    }

    @Test
    fun turnTestTxtPage() = measureReaderInteraction(openOldest = true, hideControls = true) {
        device.click(device.displayWidth * 4 / 5, device.displayHeight / 2)
        device.waitForIdle()
    }

    @Test
    fun jumpTestEpubChapter() = measureReaderInteraction(openOldest = false) {
        val target = listOf("下一章", "上一章")
            .asSequence()
            .flatMap { label -> device.findObjects(By.desc(label)).asSequence() }
            .firstOrNull { it.isEnabled }
        checkNotNull(target) { "测试 EPUB 没有可跳转章节" }
        target.click()
        device.wait(Until.gone(By.desc("正在加载章节")), 10_000)
        check(device.wait(Until.hasObject(By.desc("阅读正文已就绪")), 10_000)) {
            "章节跳转后正文未恢复"
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun measureReaderOpen(openOldest: Boolean) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric(
                    sectionName = "ReaderOpen",
                    mode = TraceSectionMetric.Mode.First,
                    label = "readerDocumentLoad",
                ),
            ),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = { prepareShelfBook(openOldest) },
        ) {
            clickBenchmarkBook(openOldest)
            check(device.wait(Until.hasObject(By.desc("阅读正文已就绪")), 15_000)) {
                "阅读器文档未就绪"
            }
            check(device.wait(Until.hasObject(By.desc("分页正文已就绪")), 15_000)) {
                "阅读器首屏正文未就绪"
            }
        }
    }

    private fun measureReaderInteraction(
        openOldest: Boolean,
        hideControls: Boolean = false,
        action: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            setupBlock = {
                prepareShelfBook(openOldest)
                clickBenchmarkBook(openOldest)
                check(device.wait(Until.hasObject(By.desc("阅读正文已就绪")), 15_000)) {
                    "阅读器首屏正文未就绪"
                }
                check(device.wait(Until.hasObject(By.desc("分页正文已就绪")), 15_000)) {
                    "分页正文未就绪"
                }
                if (hideControls) {
                    if (device.hasObject(By.desc("下一章"))) {
                        device.click(device.displayWidth / 2, device.displayHeight / 2)
                    }
                    check(device.wait(Until.gone(By.desc("下一章")), 3_000)) {
                        "测量前无法隐藏阅读器菜单"
                    }
                }
            },
            measureBlock = action,
        )
    }

    private fun MacrobenchmarkScope.prepareShelfBook(openOldest: Boolean) {
        seedBenchmarkLibrary()
        openShelfForBenchmark()
        filterShelfForBenchmark(if (openOldest) "测试 TXT" else "测试 EPUB")
    }

    private fun MacrobenchmarkScope.clickBenchmarkBook(openOldest: Boolean) {
        repeat(3) {
            val book = device.wait(Until.findObject(By.desc("打开书籍")), 5_000)
            checkNotNull(book) {
                "书架没有可用于性能测试的${if (openOldest) "测试 TXT" else "测试 EPUB"}"
            }
            try {
                book.click()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        error("书架搜索结果持续刷新，无法稳定打开测试书籍")
    }
}
