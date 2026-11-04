package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.AppTheme
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 真实 [CraSlider] 在 RTL 下的渲染行为测试（方向由测试自己注入，不依赖设备 force_rtl 或系统语言）。
 *
 * ## 为什么这两组断言是「可观察」的，而不是自证
 *
 * 本文件**一次都没有调用** `computeCraSliderTrackGeometry`。断言对象是渲染像素与语义回调，
 * 两者只能由真实组件在真实方向下产出。
 *
 * 1. **数值 / 拇指方向（M3 那一层）**：`CompositionLocalProvider(LocalLayoutDirection provides …)`
 *    注入方向后，同一个物理点击位置（节点宽度的 20%）在两个方向下必须给出**互补**数值：
 *    LTR 偏小、RTL 偏大。`onValueChange` 是组件对外的数值出口，捕获它即可观察，无需新增钩子。
 *    用 `valueRange = 0f..1f` + `steps = 0`，回调值就是 fraction 本身（含 M3 的拇指内缩量纲，
 *    360dp 宽下 20% 处约 0.18 / 0.82，与 0.40 / 0.60 阈值之间留有安全边距）。
 *    这一条同时充当「注入确实生效」的见证：若 provider 没抵达 Slider，两个方向会给出同一个
 *    数值，于是这两条断言必有一条变红。
 * 2. **自绘轨道填充方向（本组件自己那一层）**：M3 只镜像拇指，不镜像我们自绘的 `Canvas`
 *    （已对 material3 1.3.1 产物取证：`SliderState.onPress` 用 `totalWidth - x` 做算术镜像，
 *    `SliderKt` 无 graphicsLayer / scaleX / reverseLayout）。所以「已走段画在哪一侧」只能由
 *    像素判定。沿用仓库既有先例 `captureToImage().toPixelMap()`（见
 *    `feature/reader/pager/PageTurnerStaticSurfaceTest`）。tint 由测试显式传入 [SliderTint]
 *    （主题里不存在的饱和蓝，不会与未走段的 onSurface 10% 灰混合撞色），在 25% / 75% 两列上
 *    纵向扫描判定「这一列有没有 tint 像素」。`value` 固定 0.5 使拇指停在中轴，20dp 直径的拇指
 *    不会污染 25% / 75% 采样列。
 *
 * 两个方向各成独立用例：若组件忽略方向，两条 RTL 断言会同时变红 —— 这就是「能区分 RTL 与 LTR」
 * 的操作性定义。
 *
 * ## 尚未断言的（诚实边界）
 *
 * 拇指圆心与已走段边缘在 RTL 下是否严格贴合（取决于 M3 对 `track` 槽左右留白是否对称）不在本
 * 文件断言范围内：那需要采样点落在两者交界处，容差要按真机密度定，留待真机目视。
 * 本文件在真机跑绿之前**不声称 RTL PASS**，只声称「断言读的是可观察量，且方向被忽略时会变红」。
 */
@OptIn(ExperimentalMaterial3Api::class)
class CraSliderRtlRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 1. 数值语义：同一物理位置，两个方向给出互补数值 ──────────────────

    @Test
    fun tapNearLeftEdgeYieldsLowValueInLtr() {
        val reported = tapAtLeftEdge(LayoutDirection.Ltr)
        val value = reported.last()
        assertTrue(
            "LTR 下点击节点宽度 20% 处应得到偏小的值（约 0.18），实际=$value；全部回调=$reported",
            value < 0.40f,
        )
    }

    @Test
    fun tapNearLeftEdgeYieldsHighValueInRtl() {
        val reported = tapAtLeftEdge(LayoutDirection.Rtl)
        val value = reported.last()
        assertTrue(
            "RTL 下点击同一物理位置应得到偏大的值（与 LTR 互补，约 0.82），实际=$value；全部回调=$reported",
            value > 0.60f,
        )
    }

    // ── 2. 自绘轨道像素：已走段所在的一侧随方向翻转 ─────────────────────

    @Test
    fun activeTrackIsPaintedOnLeftInLtr() {
        val pixels = renderSliderAndCapture(LayoutDirection.Ltr)
        assertTrue("LTR：轨道 25% 处应看到已走段（tint）像素", pixels.hasTintColumn(0.25f))
        assertFalse("LTR：轨道 75% 处属未走段，不该出现 tint", pixels.hasTintColumn(0.75f))
    }

    @Test
    fun activeTrackIsPaintedOnRightInRtl() {
        val pixels = renderSliderAndCapture(LayoutDirection.Rtl)
        assertTrue("RTL：已走段必须翻到右侧，轨道 75% 处应看到 tint 像素", pixels.hasTintColumn(0.75f))
        assertFalse(
            "RTL：25% 处属未走段却出现 tint —— 自绘轨道没有跟随布局方向",
            pixels.hasTintColumn(0.25f),
        )
    }

    // ── 夹具 ────────────────────────────────────────────────────────────

    /**
     * 在指定方向下渲染 slider，点击节点宽度 20% 处，返回 [CraSlider.onValueChange] 收到的全部值。
     * 若一次回调都没有则直接失败：那说明「点轨道不产生数值」，上面的方向断言将失去意义，必须显式暴露。
     */
    private fun tapAtLeftEdge(direction: LayoutDirection): List<Float> {
        val reported = mutableListOf<Float>()
        composeRule.setContent {
            SliderHarness(direction) { value ->
                CraSlider(
                    value = value,
                    onValueChange = { reported += it },
                    valueRange = 0f..1f,
                    steps = 0,
                    tint = SliderTint,
                    modifier = Modifier.fillMaxWidth().testTag(SliderTag),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SliderTag).performTouchInput {
            click(Offset(width * TapXFrac, height * 0.5f))
        }
        composeRule.waitForIdle()
        assertTrue("点击轨道后 onValueChange 一次都没被调用，数值方向无从观察：$reported", reported.isNotEmpty())
        return reported
    }

    /** 在指定方向下渲染 value=[MidValue] 的 slider，抓取其节点的像素。 */
    private fun renderSliderAndCapture(direction: LayoutDirection): PixelMap {
        composeRule.setContent {
            SliderHarness(direction) { value ->
                CraSlider(
                    value = value,
                    onValueChange = { },
                    valueRange = 0f..1f,
                    steps = 0,
                    tint = SliderTint,
                    modifier = Modifier.fillMaxWidth().testTag(SliderTag),
                )
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(SliderTag).captureToImage().toPixelMap()
    }

    /** 固定尺寸外壳 + 方向注入：宽度固定，x 分位数才成为可复现坐标。 */
    @Composable
    private fun SliderHarness(
        direction: LayoutDirection,
        content: @Composable (Float) -> Unit,
    ) {
        AppTheme {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.requiredSize(HarnessWidth, HarnessHeight)) {
                    content(MidValue)
                }
            }
        }
    }

    /**
     * 在 [yCenterFrac] 附近纵向扫描若干像素，判断该列是否出现 [SliderTint]。
     * 纵向取「邻域命中」而非单点，避免依赖轨道在 28dp 控件里的精确居中；横向位置才是被断言的量。
     */
    private fun PixelMap.hasTintColumn(
        xFrac: Float,
        yCenterFrac: Float = 0.5f,
        scanRadiusPx: Int = 12,
        tolerance: Float = 0.02f,
    ): Boolean {
        val x = (width * xFrac).toInt().coerceIn(0, width - 1)
        val yCenter = (height * yCenterFrac).toInt()
        for (dy in -scanRadiusPx..scanRadiusPx) {
            val y = yCenter + dy
            if (y < 0 || y >= height) continue
            val p = get(x, y)
            if (abs(p.red - SliderTint.red) < tolerance &&
                abs(p.green - SliderTint.green) < tolerance &&
                abs(p.blue - SliderTint.blue) < tolerance
            ) {
                return true
            }
        }
        return false
    }

    private companion object {
        const val SliderTag = "cra-slider-under-test"
        const val MidValue = 0.5f
        const val TapXFrac = 0.20f

        /** 主题里不存在的饱和蓝：只可能来自测试显式传入的 tint。 */
        val SliderTint = Color(0xFF11AAEE)
        val HarnessWidth = 360.dp
        val HarnessHeight = 60.dp
    }
}
