package com.creationreadingassistant.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S06-⑦ CraSlider RTL 轨道与拇指方向一致性定向测试。
 *
 * 验证：
 * 1. LTR 模式：已走段从左侧起点 (x=0) 开始向右延伸，末端停点位于最右侧；
 * 2. RTL 模式：已走段从右侧起点 (x=W) 开始向左延伸，末端停点位于最左侧，与 M3 Slider 拇指镜像运动严格一致；
 * 3. 边界条件：0% 进度、100% 进度、极小值短条退化保护、超出区间夹紧保护。
 */
class CraSliderTest {

    private val trackWidth = 1000f
    private val trackHeight = 20f
    private val strokeInset = 1.5f

    @Test
    fun `ltr track geometry starts from left and places stop dot at right`() {
        val fraction = 0.4f
        val geom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = fraction,
            isRtl = false,
            strokeInsetPx = strokeInset,
        )

        // 已走段从左侧 (x=0) 向右延展 400px
        assertTrue("LTR 下必须有已走段", geom.hasActiveTrack)
        assertEquals(0f, geom.activeTrackTopLeft.x, 0.001f)
        assertEquals(0f, geom.activeTrackTopLeft.y, 0.001f)
        assertEquals(400f, geom.activeTrackSize.width, 0.001f)
        assertEquals(20f, geom.activeTrackSize.height, 0.001f)

        // 停点指示小圆位于最右端 (未走段终点)
        assertTrue("未达到 100% 时必须有停点指示", geom.hasStopDot)
        val expectedStopDotX = trackWidth - trackHeight * 0.55f // 1000 - 11 = 989
        assertEquals(expectedStopDotX, geom.stopDotCenter.x, 0.001f)
        assertEquals(trackHeight / 2f, geom.stopDotCenter.y, 0.001f)

        // 外侧细描边贴合已走段
        assertTrue("已走段大于高度时必须有外侧描边", geom.hasOutline)
        assertEquals(-strokeInset, geom.outlineTopLeft.x, 0.001f)
        assertEquals(400f + 2f * strokeInset, geom.outlineSize.width, 0.001f)
    }

    @Test
    fun `rtl track geometry starts from right and places stop dot at left`() {
        val fraction = 0.4f
        val geom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = fraction,
            isRtl = true,
            strokeInsetPx = strokeInset,
        )

        // 已走段从右侧 (x=1000) 向左延展 400px，起点位于 x = 1000 - 400 = 600
        assertTrue("RTL 下必须有已走段", geom.hasActiveTrack)
        assertEquals(600f, geom.activeTrackTopLeft.x, 0.001f)
        assertEquals(0f, geom.activeTrackTopLeft.y, 0.001f)
        assertEquals(400f, geom.activeTrackSize.width, 0.001f)
        assertEquals(20f, geom.activeTrackSize.height, 0.001f)

        // 核心校验：已走段覆盖区间为 [600, 1000]，其左边界 (600) 正好与 M3 Slider 拇指位置重合
        val activeRightEdge = geom.activeTrackTopLeft.x + geom.activeTrackSize.width
        assertEquals("RTL 下已走段右边缘必须紧贴轨道最右端", trackWidth, activeRightEdge, 0.001f)

        // 停点指示小圆位于最左端 (RTL 下未走段终点/最大值方向)
        assertTrue("未达到 100% 时必须有停点指示", geom.hasStopDot)
        val expectedStopDotX = trackHeight * 0.55f // 11
        assertEquals(expectedStopDotX, geom.stopDotCenter.x, 0.001f)
        assertEquals(trackHeight / 2f, geom.stopDotCenter.y, 0.001f)

        // 外侧细描边贴合 RTL 已走段
        assertTrue("已走段大于高度时必须有外侧描边", geom.hasOutline)
        assertEquals(600f - strokeInset, geom.outlineTopLeft.x, 0.001f)
        assertEquals(400f + 2f * strokeInset, geom.outlineSize.width, 0.001f)
    }

    @Test
    fun `zero fraction produces no active track and preserves directional stop dot`() {
        val ltrGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = 0f,
            isRtl = false,
        )
        assertFalse("fraction 为 0 时不应绘制已走段", ltrGeom.hasActiveTrack)
        assertFalse("fraction 为 0 时不应绘制描边", ltrGeom.hasOutline)
        assertTrue("fraction 为 0 时应有末端停点", ltrGeom.hasStopDot)
        assertEquals(trackWidth - trackHeight * 0.55f, ltrGeom.stopDotCenter.x, 0.001f)

        val rtlGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = 0f,
            isRtl = true,
        )
        assertFalse("RTL 下 fraction 为 0 时不应绘制已走段", rtlGeom.hasActiveTrack)
        assertFalse("RTL 下 fraction 为 0 时不应绘制描边", rtlGeom.hasOutline)
        assertTrue("RTL 下 fraction 为 0 时应有末端停点", rtlGeom.hasStopDot)
        assertEquals(trackHeight * 0.55f, rtlGeom.stopDotCenter.x, 0.001f)
    }

    @Test
    fun `full fraction covers entire track and removes stop dot`() {
        val ltrGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = 1f,
            isRtl = false,
        )
        assertTrue(ltrGeom.hasActiveTrack)
        assertFalse("100% 进度停点应消失", ltrGeom.hasStopDot)
        assertEquals(0f, ltrGeom.activeTrackTopLeft.x, 0.001f)
        assertEquals(trackWidth, ltrGeom.activeTrackSize.width, 0.001f)

        val rtlGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = 1f,
            isRtl = true,
        )
        assertTrue(rtlGeom.hasActiveTrack)
        assertFalse("RTL 下 100% 进度停点应消失", rtlGeom.hasStopDot)
        assertEquals(0f, rtlGeom.activeTrackTopLeft.x, 0.001f)
        assertEquals(trackWidth, rtlGeom.activeTrackSize.width, 0.001f)
    }

    @Test
    fun `tiny non zero fraction degenerates to minimum pill without negative coordinates`() {
        // 当 activeW (5px) < height (20px) 时，退化为圆形胶囊最小宽度 (20px)
        val tinyFraction = 0.005f // 5px
        val ltrGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = tinyFraction,
            isRtl = false,
        )
        assertTrue(ltrGeom.hasActiveTrack)
        assertEquals(0f, ltrGeom.activeTrackTopLeft.x, 0.001f)
        assertEquals(trackHeight, ltrGeom.activeTrackSize.width, 0.001f)

        val rtlGeom = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = tinyFraction,
            isRtl = true,
        )
        assertTrue(rtlGeom.hasActiveTrack)
        // RTL 下胶囊应靠在最右侧: 1000 - 20 = 980
        assertEquals(trackWidth - trackHeight, rtlGeom.activeTrackTopLeft.x, 0.001f)
        assertEquals(trackHeight, rtlGeom.activeTrackSize.width, 0.001f)
        assertTrue("RTL 胶囊左上角 x 不得为负", rtlGeom.activeTrackTopLeft.x >= 0f)
    }

    @Test
    fun `fraction clamp protects against out of range values`() {
        val underflow = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = -0.5f,
            isRtl = true,
        )
        assertFalse(underflow.hasActiveTrack)

        val overflow = computeCraSliderTrackGeometry(
            totalWidth = trackWidth,
            height = trackHeight,
            fraction = 1.5f,
            isRtl = true,
        )
        assertEquals(0f, overflow.activeTrackTopLeft.x, 0.001f)
        assertEquals(trackWidth, overflow.activeTrackSize.width, 0.001f)
        assertFalse(overflow.hasStopDot)
    }

    /**
     * 镜像不变式：对同一组输入，RTL 几何必须是 LTR 几何关于轨道中轴的**精确**镜像。
     *
     * 为什么单独写这一条，而不是再多列几个 RTL 断言：逐点断言只覆盖我想到的那几个
     * 取值，而「镜像」是对**整条 fraction 区间和全部四个输出量**的性质约束。任何
     * 「只在一路方向上改了偏移」的编辑都会在这里变红 —— 包括绘制标志本身
     * （hasStopDot 若只看 fraction 而漏掉方向，也是撕裂）。
     *
     * 记 LTR 下某段占据 [x0, x1]，则 RTL 下必须占据 [W-x1, W-x0]。
     */
    @Test
    fun `rtl geometry is the exact mirror of ltr across the whole fraction range`() {
        repeat(101) { percent ->
            val fraction = percent / 100f
            val ltr = computeCraSliderTrackGeometry(
                totalWidth = trackWidth,
                height = trackHeight,
                fraction = fraction,
                isRtl = false,
                strokeInsetPx = strokeInset,
            )
            val rtl = computeCraSliderTrackGeometry(
                totalWidth = trackWidth,
                height = trackHeight,
                fraction = fraction,
                isRtl = true,
                strokeInsetPx = strokeInset,
            )

            withAssertContext(fraction) {
                // 绘制与否必须同向同条件，否则一侧多出/缺失元素
                assertTrue("hasActiveTrack 方向不一致", ltr.hasActiveTrack == rtl.hasActiveTrack)
                assertTrue("hasStopDot 方向不一致", ltr.hasStopDot == rtl.hasStopDot)
                assertTrue("hasOutline 方向不一致", ltr.hasOutline == rtl.hasOutline)
                // 尺寸与方向无关
                assertEquals(ltr.activeTrackSize.width, rtl.activeTrackSize.width, 0.001f)
                assertEquals(ltr.activeTrackSize.height, rtl.activeTrackSize.height, 0.001f)
                assertEquals(ltr.outlineSize.width, rtl.outlineSize.width, 0.001f)
                assertEquals(ltr.outlineSize.height, rtl.outlineSize.height, 0.001f)
                assertEquals(ltr.stopDotCenter.y, rtl.stopDotCenter.y, 0.001f)
                // 三段水平区间逐一镜像
                assertEquals(mirrorRight(ltr.activeTrackTopLeft.x, ltr.activeTrackSize.width), rtl.activeTrackTopLeft.x, 0.001f)
                assertEquals(mirrorRight(ltr.outlineTopLeft.x, ltr.outlineSize.width), rtl.outlineTopLeft.x, 0.001f)
                assertEquals(trackWidth - ltr.stopDotCenter.x, rtl.stopDotCenter.x, 0.001f)
            }
        }
    }

    /**
     * RTL 几何不得越出画布：Canvas 不裁剪越界绘制，画出边界即成为视觉毛刺。
     *
     * `hasStopDot` 只按 fraction 判定，与「已走段是否已经把那一端吃掉」无关，所以
     * 100% 之外的一切取值都必须把停点留在轨道内。描边按设计**允许**外扩 strokeInset
     * （它本来就是套在已走段外侧的一圈），因此只约束已走段与停点。
     */
    @Test
    fun `rtl drawing stays inside the canvas for every fraction`() {
        val stopDotRadius = trackHeight * 0.45f
        repeat(101) { percent ->
            val fraction = percent / 100f
            val geom = computeCraSliderTrackGeometry(
                totalWidth = trackWidth,
                height = trackHeight,
                fraction = fraction,
                isRtl = true,
                strokeInsetPx = strokeInset,
            )

            withAssertContext(fraction) {
                if (geom.hasActiveTrack) {
                    assertTrue("RTL 已走段左边界不得为负: $fraction", geom.activeTrackTopLeft.x >= 0f)
                    assertTrue(
                        "RTL 已走段右边界不得超过轨道宽度: $fraction",
                        geom.activeTrackTopLeft.x + geom.activeTrackSize.width <= trackWidth + 0.001f,
                    )
                }
                if (geom.hasStopDot) {
                    assertTrue(
                        "RTL 停点圆不得画出轨道左边界: $fraction",
                        geom.stopDotCenter.x - stopDotRadius >= -0.001f,
                    )
                    assertTrue(
                        "RTL 停点圆不得画出轨道右边界: $fraction",
                        geom.stopDotCenter.x + stopDotRadius <= trackWidth + 0.001f,
                    )
                }
            }
        }
    }

    /** LTR 区间 [x, x+width] 镜像后该区间的左边界。 */
    private fun mirrorRight(x: Float, width: Float): Float = trackWidth - (x + width)

    private inline fun withAssertContext(fraction: Float, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("fraction=$fraction 处镜像/边界断言失败：${e.message}", e)
        }
    }
}
