package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.Clusterizer
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM 对照基准，打印时间和线程分配量，不把桌面测量当设备帧时。 */
class ReaderKernelPerformanceBenchmark {
    private val ruler = FakeTextRuler(20f)

    @Test
    fun `clusterize a 100k character chapter`() {
        val text = "这是测试正文，包含中文与 English 123。".repeat(4000)
        measure("clusterize-${text.length}") {
            val clusters = Clusterizer.of(text, ruler)
            assertEquals(text.length, clusters.count)
            assertEquals(text.length, clusters.startInText.last())
        }
    }

    @Test
    fun `read progress 2000 times in a 1000 chapter book`() {
        val source = object : PagedChapterSource {
            override val chapterCount = 1000
            override val totalChars = 1_000_000
            override val chapterLengthsAreEstimated = true
            override fun chapterTitle(index: Int) = "测试 EPUB"
            override fun chapterStartAbs(index: Int) = index * 1000
            override fun loadChapter(index: Int) = error("Progress must not load chapter text")
        }
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = PagedReaderController(
            source, LayoutConfig(200f, 200f, fontSizePx = 20f), ruler, AnywhereBreakOracle(), scope,
        )
        try {
            measure("progress-1000-chapters-2000-reads") {
                repeat(2000) { assertEquals(0f, controller.progressPercent, 0f) }
            }
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private fun measure(label: String, action: () -> Unit) {
        // Android 编译桩没有 management 包，JVM test worker 运行时提供该计数器。
        val bean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)
        val beanType = Class.forName("com.sun.management.ThreadMXBean")
        beanType.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        val allocatedBytes = beanType.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val threadId = Thread.currentThread().id
        repeat(3) { action() }
        val elapsed = LongArray(7)
        val allocated = LongArray(7)
        repeat(7) { index ->
            val bytes = allocatedBytes.invoke(bean, threadId) as Long
            val start = System.nanoTime()
            action()
            elapsed[index] = System.nanoTime() - start
            allocated[index] = (allocatedBytes.invoke(bean, threadId) as Long) - bytes
        }
        elapsed.sort()
        allocated.sort()
        println("$label medianNs=${elapsed[3]} medianAllocatedBytes=${allocated[3]}")
    }
}
