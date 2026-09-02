package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 排版进行中的骨架屏占位。
 *
 * **关键不变式**：占位行的上下/左右坐标、行高、段距、首行缩进必须与
 * [LayoutConfig] 派生的实际排版签名严格一致，保证「骨架 → 真实第一页」
 * 切换时没有高度跳变（用户感知为内容"从灰变实"，而不是页面上下抖一下）。
 *
 * 设计策略（与起点/番茄一致）：
 * - 不做逐帧 shimmer 动画（干扰阅读意图，Compose repeat + infiniteTransition 也耗电）。
 * - 静态 8~12 行灰色条，行宽随机 72%~96% 模拟真实段落长度。
 * - 第 1 行强制首行缩进 2em，后续行全宽。
 * - 每 6±1 行留一段 paragraphSpacingPx 的空行，模拟自然段断节奏。
 * - 颜色取 MaterialTheme.colorScheme.onSurface 的 14% alpha，亮/暗模式都不突兀。
 * - 角落放一个迷你 spinner，让用户知道"不是死机，是加载中"（骨架屏是背景前景）。
 *
 * @param cfg 同一 LayoutConfig 传给真实正文和骨架，保证尺寸签名一致。
 * @param density 从 Compose LocalDensity 传入，用于 roundToInt 换算。
 * @param textColor 真实正文色值，仅用于计算骨架条的低饱和近似色（不直接用，避免深色模式纯黑）。
 */
@Composable
internal fun ReaderLoadingSkeleton(
    cfg: LayoutConfig,
    density: Density,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val lineH = cfg.lineHeightPx
    val paraGap = cfg.paragraphSpacingPx
    val indent = cfg.firstLineIndentPx
    val topPad = cfg.contentTopPaddingPx
    val contentW = cfg.contentWidthPx
    val contentH = cfg.contentHeightPx

    // 骨架条底色：取 onSurfaceVariant（14% alpha），亮/暗模式都不突兀。
    // MaterialTheme.colorScheme 在 App 根 Composable 已包 Theme.kt，无需 try/catch。
    val scheme = MaterialTheme.colorScheme
    val baseColor = scheme.onSurfaceVariant
    val barColor = remember(baseColor.value.toLong()) {
        baseColor.copy(alpha = (baseColor.alpha * 0.14f).coerceIn(0.05f, 0.2f))
    }
    val cornerRadiusPx = with(density) { 4.dp.toPx() }

    // 行布局规划：计算每页放多少行，避免骨架高度与真实正文差太多。
    val (lines, paragraphBreaks) = remember(contentH.toDouble(), lineH.toDouble(), paraGap.toDouble()) {
        planSkeletonLines(
            contentHeightPx = contentH,
            lineHeightPx = lineH,
            paragraphSpacingPx = paraGap,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "正在加载正文" },
    ) {
        // 骨架条：直接 Canvas 画圆角矩形，不比 8×Row 性能差。
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
        ) {
            val contentLeft = (size.width - contentW) / 2f
            val baseTop = topPad
            lines.forEachIndexed { i, lineSpec ->
                val top = baseTop + lineSpec.topOffsetPx
                val left = contentLeft + if (lineSpec.hasFirstLineIndent) indent else 0f
                val width = lineSpec.widthRatio * contentW
                if (width <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top),
                    size = Size(width, lineH * 0.58f), // 字形高度≈行高58%
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        cornerRadiusPx,
                        cornerRadiusPx,
                    ),
                )
            }
        }
    }
}

/**
 * 行布局规划结果。
 *
 * @property lines 每行的起始偏移（相对 cfg.contentTopPaddingPx=0）与宽度比例。
 * @property paragraphBreaks 第 N 行之后插入的段距索引（用于调试，暂时仅记录）。
 */
private data class SkeletonPlan(
    val lines: List<SkeletonLine>,
    val paragraphBreaks: Set<Int>,
)

private data class SkeletonLine(
    val topOffsetPx: Float,
    val widthRatio: Float,
    val hasFirstLineIndent: Boolean,
)

/**
 * 纯函数骨架行规划，便于单测。
 *
 * 规则：
 * 1. 每页 8~12 行（8 行保底，12 行上限避免小号字满屏灰条）。
 * 2. 第 1 行强制首行缩进 + 宽度 82%（短行似标题）。
 * 3. 从第 2 行开始，每 6 行插入 1 个 paragraphSpacingPx 的段距空行，下一行强制首行缩进。
 * 4. 普通行宽度随机 72%~96%，段尾行偏短（55%~78%）以模拟自然段末换行。
 * 5. 整体高度不超过 contentHeightPx - paragraphSpacingPx（留底部呼吸感）。
 * 6. 算法确定性：用 contentH/lineH 做 seed，同一排版签名下骨架逐帧相同（无闪烁）。
 */
private fun planSkeletonLines(
    contentHeightPx: Float,
    lineHeightPx: Float,
    paragraphSpacingPx: Float,
): SkeletonPlan {
    if (contentHeightPx <= 0f || lineHeightPx <= 0f) {
        return SkeletonPlan(emptyList(), emptySet())
    }
    val maxLinesRaw = ((contentHeightPx - paragraphSpacingPx) / (lineHeightPx + paragraphSpacingPx * 0.08f)).roundToInt()
    val targetLines = max(8, min(12, maxLinesRaw))

    // 确定性伪随机：避免每次 recompose 行宽都跳（用目标行数×倍数做种子，保持同签名恒定）。
    var state = (targetLines * 1_103_515_245).ushr(1) or 1
    fun next01(): Float {
        // xorshift32
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        return (state.toLong().and(0xFFFFFFL)).toFloat() / 0xFFFFFFL.toFloat()
    }

    val out = ArrayList<SkeletonLine>(targetLines)
    var yAccum = 0f
    var isParaStart = true
    var count = 0
    val paraBreaks = LinkedHashSet<Int>()
    var lineSeqInPara = 0

    while (count < targetLines) {
        val needed = yAccum + lineHeightPx
        if (needed > contentHeightPx - paragraphSpacingPx * 0.5f) break

        val (widthRatio, isParaEnd) = when {
            count == 0 -> 0.82f to false // 第 1 行（标题感）
            else -> {
                val r = next01()
                // 第 4~6 行概率作为段尾
                val paraEndProb = when {
                    lineSeqInPara < 3 -> 0.03f
                    lineSeqInPara == 3 -> 0.20f
                    lineSeqInPara == 4 -> 0.42f
                    else -> 0.75f
                }
                val end = r < paraEndProb
                if (end) {
                    // 段尾行：55%~78%
                    val w = 0.55f + next01() * 0.23f
                    w to true
                } else {
                    // 普通行：72%~96%
                    val w = 0.72f + next01() * 0.24f
                    w to false
                }
            }
        }

        out.add(
            SkeletonLine(
                topOffsetPx = yAccum,
                widthRatio = widthRatio.coerceIn(0.4f, 1f),
                hasFirstLineIndent = isParaStart,
            ),
        )
        count++
        lineSeqInPara++
        yAccum += lineHeightPx

        if (isParaEnd && count < targetLines) {
            paraBreaks.add(count - 1)
            val gap = paragraphSpacingPx.coerceAtMost(lineHeightPx * 0.6f)
            if (yAccum + gap + lineHeightPx <= contentHeightPx) {
                yAccum += gap
            }
            isParaStart = true
            lineSeqInPara = 0
        } else if (isParaStart) {
            isParaStart = false
        }
    }

    return SkeletonPlan(out, paraBreaks)
}
