package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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

    val panelHeight by animateDpAsState(
        targetValue = if (zoomed) 110.dp else 0.dp,
        animationSpec = tween(MotionTokens.Base, easing = MotionTokens.StandardEasing),
        label = "zoom_panel",
    )
    Box(
        modifier = modifier.padding(horizontal = layout.relatedGap)
            .onGloballyPositioned { sliderRootY = it.positionInRoot().y },
    ) {
        AnimatedVisibility(
            visible = zoomed,
            enter = fadeIn(tween(MotionTokens.Fast)) + scaleIn(tween(MotionTokens.Fast)),
            exit = fadeOut(tween(MotionTokens.Fast)) + scaleOut(tween(MotionTokens.Fast)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .offset { IntOffset(0, -panelHeight.roundToPx()) },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val progress = sliderValue.coerceIn(0f, 100f) / 100f
                Surface(
                    color = accentColor.copy(alpha = 0.10f), contentColor = accentColor,
                    shape = RoundedCornerShape(10.dp), tonalElevation = 0.dp,
                ) {
                    Text(
                        text = chapterProgressPreviewLabel(sliderValue),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
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
                        color = accentColor.copy(alpha = 0.72f),
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                    )
                    val lineWidth = with(density) { 3.dp.toPx() }
                    val lineX = progress * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(lineX, top - trackHeight),
                        end = Offset(lineX, top + trackHeight * 2f),
                        strokeWidth = lineWidth,
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
                .run { if (zoomed) padding(top = panelHeight) else this },
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
