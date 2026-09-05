package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.MotionTokens
import kotlin.math.abs

internal data class ChapterHeat(val title: String, val weight: Float)

private const val DisabledAlpha = 0.38f

/** 底部滑块按当前章节定位，预览文案也必须使用当前章节坐标。 */
internal fun chapterProgressPreviewLabel(progress: Float): String =
    "本章 · ${progress.coerceIn(0f, 100f).toInt()}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZoomableProgressScrubber(
    chapterProgress: Float,
    onSeekProgress: (Float) -> Unit,
    accentColor: Color,
    chapterHeats: List<ChapterHeat> = emptyList(),
    onPreviewProgress: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    val density = LocalDensity.current
    var sliderValue by remember { mutableFloatStateOf(chapterProgress) }
    var isDragging by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var pinchAccum by remember { mutableFloatStateOf(1f) }
    var sliderRootY by remember { mutableFloatStateOf(0f) }
    val latestPreview by rememberUpdatedState(onPreviewProgress)

    DisposableEffect(Unit) { onDispose { latestPreview(null) } }
    LaunchedEffect(chapterProgress, isDragging) {
        if (!isDragging && abs(sliderValue - chapterProgress) > 0.5f) sliderValue = chapterProgress
    }

    val showPreview = zoomed || isDragging
    val panelHeight by animateDpAsState(
        targetValue = if (showPreview) 110.dp else 0.dp,
        animationSpec = tween(MotionTokens.Base, easing = MotionTokens.StandardEasing),
        label = "zoom_panel",
    )
    Box(
        modifier = modifier.padding(horizontal = layout.relatedGap)
            .onGloballyPositioned { sliderRootY = it.positionInRoot().y },
    ) {
        AnimatedVisibility(
            visible = showPreview,
            enter = fadeIn(tween(MotionTokens.Fast)) + scaleIn(tween(MotionTokens.Fast)),
            exit = fadeOut(tween(MotionTokens.Fast)) + scaleOut(tween(MotionTokens.Fast)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .offset { IntOffset(0, -panelHeight.roundToPx()) },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val progress = sliderValue.coerceIn(0f, 100f) / 100f
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    contentColor = accentColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accentColor, shape = CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = chapterProgressPreviewLabel(sliderValue),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                    val trackHeight = with(density) { 8.dp.toPx() }
                    val top = (size.height - trackHeight) / 2f
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.16f),
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                    )
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.78f),
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                    )
                    // Micro-ticks (微刻度) along the track
                    val tickCount = 10
                    val tickHeight = with(density) { 4.dp.toPx() }
                    val tickTop = top + (trackHeight - tickHeight) / 2f
                    val tickStroke = with(density) { 1.5.dp.toPx() }
                    for (i in 1 until tickCount) {
                        val tickFraction = i / tickCount.toFloat()
                        val tickX = tickFraction * size.width
                        val isPassed = tickFraction <= progress
                        drawLine(
                            color = if (isPassed) Color.White.copy(alpha = 0.7f) else accentColor.copy(alpha = 0.35f),
                            start = Offset(tickX, tickTop),
                            end = Offset(tickX, tickTop + tickHeight),
                            strokeWidth = tickStroke,
                            cap = StrokeCap.Round,
                        )
                    }
                    val lineWidth = with(density) { 3.5.dp.toPx() }
                    val lineX = progress * size.width
                    drawLine(
                        color = accentColor.copy(alpha = 0.40f),
                        start = Offset(lineX, top - trackHeight * 0.8f),
                        end = Offset(lineX, top + trackHeight * 1.8f),
                        strokeWidth = lineWidth * 2f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(lineX, top - trackHeight * 0.7f),
                        end = Offset(lineX, top + trackHeight * 1.7f),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = { isDragging = true; sliderValue = it; latestPreview(it) },
            onValueChangeFinished = { isDragging = false; latestPreview(null); onSeekProgress(sliderValue) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth().testTag("reader-progress-scrubber")
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        pinchAccum *= zoomChange
                        when {
                            !zoomed && pinchAccum >= 1.4f -> { zoomed = true; pinchAccum = 1f }
                            zoomed && pinchAccum <= 0.7f -> { zoomed = false; pinchAccum = 1f }
                        }
                    }
                }
                .run { if (showPreview) padding(top = panelHeight) else this },
            colors = SliderDefaults.colors(
                thumbColor = accentColor, activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = if (zoomed) 0.12f else 0.20f),
                disabledThumbColor = accentColor.copy(alpha = DisabledAlpha),
                disabledActiveTrackColor = accentColor.copy(alpha = DisabledAlpha),
                disabledInactiveTrackColor = accentColor.copy(alpha = DisabledAlpha * 0.5f),
            ),
        )
    }
}
