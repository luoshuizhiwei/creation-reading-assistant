package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S09-B：点击热区几何的**唯一生产事实源**。
 *
 * 修复前三套几何并存：
 * - 自研分页引擎 `resolveReaderTapAction`：内联 `width / 3f`、`width * 2f / 3f`、`height * 0.12f`；
 * - legacy 分页 EPUB `PagedEpubContent`：`weight(0.16f) / 0.68f / 0.16f`；
 * - 设置预览卡 `TapZonePreviewCard`：`0.24/0.52/0.24`、`0.30/0.40/0.30`、`0.32/0.36/0.32`。
 * 同一个设置在三个地方表达成不同的区域比例，用户改设置时无法预期真实热区。
 *
 * 本片把比例收敛到 [ReaderTapZoneGeometry]。本文件用**行为**证明收敛成立：
 * 边界探针直接取共享常量的值，若 `resolveReaderTapAction` 仍偷偷使用另一套数字，
 * 这些探针就会翻到错误的分区上。
 */
class ReaderTapZoneGeometryTest {

    private val w = 1200f
    private val h = 1000f

    private fun action(
        x: Float,
        y: Float,
        mode: String = "three-zone",
        canPrevious: Boolean = true,
        canNext: Boolean = true,
        preset: String = TapActionPreset.STANDARD,
        edgeDeadzonePx: Float = 0f,
    ) = resolveReaderTapAction(
        x = x, y = y, width = w, height = h, tapZoneMode = mode,
        canPrevious = canPrevious, canNext = canNext,
        preset = preset, edgeDeadzonePx = edgeDeadzonePx,
    )

    // ── 1. 共享几何自身的契约 ───────────────────────────────────────

    @Test
    fun horizontalWeightsSumToOneWithinTolerance() {
        val (left, center, right) = ReaderTapZoneGeometry.horizontalWeights
        assertEquals(1f, left + center + right, TOLERANCE)
    }

    @Test
    fun verticalWeightsSumToOneWithinTolerance() {
        val (top, middle, bottom) = ReaderTapZoneGeometry.verticalWeights
        assertEquals(1f, top + middle + bottom, TOLERANCE)
    }

    @Test
    fun everyWeightIsPositiveAndBelowOne() {
        val all = listOf(
            ReaderTapZoneGeometry.SIDE_WIDTH,
            ReaderTapZoneGeometry.CENTER_WIDTH,
            ReaderTapZoneGeometry.TOP_HEIGHT,
            ReaderTapZoneGeometry.BOTTOM_HEIGHT,
            ReaderTapZoneGeometry.MIDDLE_HEIGHT,
        )
        all.forEach { value ->
            assertTrue("weight=$value 必须为正", value > 0f)
            assertTrue("weight=$value 必须小于 1", value < 1f)
        }
    }

    @Test
    fun horizontalZonesAreEqualThirds() {
        assertEquals(1f / 3f, ReaderTapZoneGeometry.SIDE_WIDTH, TOLERANCE)
        assertEquals(1f / 3f, ReaderTapZoneGeometry.CENTER_WIDTH, TOLERANCE)
        // 侧区与中央区等宽 —— 这正是 legacy EPUB 的 0.16/0.68 与预览卡的 0.32/0.36 违反的点。
        assertEquals(ReaderTapZoneGeometry.SIDE_WIDTH, ReaderTapZoneGeometry.CENTER_WIDTH, TOLERANCE)
    }

    @Test
    fun fiveZoneBandsAreTwelvePercentTopAndBottom() {
        assertEquals(0.12f, ReaderTapZoneGeometry.TOP_HEIGHT, TOLERANCE)
        assertEquals(0.12f, ReaderTapZoneGeometry.BOTTOM_HEIGHT, TOLERANCE)
        assertEquals(0.76f, ReaderTapZoneGeometry.MIDDLE_HEIGHT, TOLERANCE)
    }

    // ── 2. 行为证明：resolveReaderTapAction 真的按共享几何分区 ────────

    @Test
    fun horizontalBoundaryMatchesSharedGeometry() {
        val leftEdge = ReaderTapZoneGeometry.centerLeftEdge(w)
        val rightEdge = ReaderTapZoneGeometry.centerRightEdge(w)

        // 边界两侧必须落到不同分区，且中央就是唤出控制栏。
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(leftEdge - 1f, 500f))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(leftEdge + 1f, 500f))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(rightEdge - 1f, 500f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(rightEdge + 1f, 500f))
    }

    @Test
    fun centerZoneIsExactlyOneThirdOfTheWidth() {
        val centerWidth = ReaderTapZoneGeometry.centerRightEdge(w) - ReaderTapZoneGeometry.centerLeftEdge(w)
        assertEquals(w / 3f, centerWidth, TOLERANCE * w)
    }

    @Test
    fun fiveZoneVerticalBoundaryMatchesSharedGeometry() {
        val topEdge = ReaderTapZoneGeometry.topBandBottomEdge(h)
        val bottomEdge = ReaderTapZoneGeometry.bottomBandTopEdge(h)

        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(600f, topEdge - 1f, "five-zone"))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(600f, topEdge + 1f, "five-zone"))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(600f, bottomEdge - 1f, "five-zone"))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(600f, bottomEdge + 1f, "five-zone"))
    }

    @Test
    fun fiveZoneMiddleBandStillUsesTheHorizontalZones() {
        val midY = (ReaderTapZoneGeometry.topBandBottomEdge(h) + ReaderTapZoneGeometry.bottomBandTopEdge(h)) / 2f
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(ReaderTapZoneGeometry.centerLeftEdge(w) - 1f, midY, "five-zone"))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(ReaderTapZoneGeometry.centerRightEdge(w) + 1f, midY, "five-zone"))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(w / 2f, midY, "five-zone"))
    }

    // ── 3. 消费端不得再持有旧比例（结构守卫，作为上面行为断言的补充） ──

    @Test
    fun legacyEpubAndPreviewCardReadTheSharedGeometry() {
        legacyEpubSource().let { src ->
            assertTrue("legacy EPUB 必须引用 ReaderTapZoneGeometry", src.contains("ReaderTapZoneGeometry"))
        }
        previewCardSource().let { src ->
            assertTrue("预览卡必须引用 ReaderTapZoneGeometry", src.contains("ReaderTapZoneGeometry"))
        }
    }

    @Test
    fun retiredMagicRatiosAreGoneFromTheTwoConsumers() {
        // 这些是修复前各自私有的比例；收敛后不得再出现在任何消费端。
        val retired = listOf("0.16f", "0.68f", "0.24f", "0.52f", "0.30f", "0.40f", "0.32f", "0.36f")
        listOf(legacyEpubSource(), previewCardSource()).forEachIndexed { index, src ->
            val who = if (index == 0) "PagedEpubContent" else "TapZonePreviewCard"
            retired.forEach { literal ->
                assertFalse("$who 不得再出现旧比例 $literal", src.contains(literal))
            }
        }
    }

    @Test
    fun engineDoesNotInlineItsOwnCopyOfTheRatios() {
        val src = engineSource()
        // 以函数签名收尾为分界，只取函数体。注意：不要在生产代码的注释里原样复述
        // 这个分界串 —— 首轮实现时 KDoc 里抄了一遍，substringAfter 从注释处就切了，
        // 于是共享常量自己的 0.12f 被算进"函数体"，守卫把干净的文件判成失败。
        val body = src.substringAfter("): ReaderTapAction {")
        assertTrue("分界串必须存在，否则守卫是在对空串断言", body.isNotEmpty() && body != src)
        // 变异验证（MUT-1）证明：只禁 `width / 3f` 与 `0.12f` 时，把引擎改成
        // `x < width * 0.16f` 能绕过这个守卫，所以这里直接禁掉函数体内对
        // 视口尺寸做任何乘法 —— 换算比例是几何事实源的职责，不是分发函数的。
        assertFalse("resolveReaderTapAction 必须读共享几何", body.contains("width / 3f"))
        assertFalse("resolveReaderTapAction 必须读共享几何", body.contains("0.12f"))
        assertFalse("函数体内不得对 width 乘比例", body.contains("width *"))
        assertFalse("函数体内不得对 height 乘比例", body.contains("height *"))
        assertTrue("resolveReaderTapAction 必须读共享几何", body.contains("ReaderTapZoneGeometry"))
    }

    // ── 4. 旧比例确实与新几何不同（防止上面守卫变成空断言） ──────────

    @Test
    fun sharedGeometryIsNotTheLegacySixtyEighteenSplit() {
        // 若有人把共享常量改回 0.16/0.68，第 2 节的边界探针会失败；
        // 这条断言则保证「守卫用的旧比例」本身不是随手抄来的当前值。
        assertFalse(kotlin.math.abs(ReaderTapZoneGeometry.SIDE_WIDTH - 0.16f) < TOLERANCE)
        assertFalse(kotlin.math.abs(ReaderTapZoneGeometry.TOP_HEIGHT - 0.24f) < TOLERANCE)
    }

    private fun legacyEpubSource(): String = readSource(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt",
    )

    private fun previewCardSource(): String = readSource(
        "src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt",
    )

    private fun engineSource(): String = readSource(
        "src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt",
    )

    private fun readSource(relative: String): String {
        val file = File(relative)
        assertTrue("源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}", file.exists())
        return file.readText()
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
