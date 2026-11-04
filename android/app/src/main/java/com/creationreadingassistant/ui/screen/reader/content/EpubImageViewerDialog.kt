package com.creationreadingassistant.ui.screen.reader.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

/**
 * EPUB 原生全屏图文手势查看器：
 * 1. 双击切换缩放（1.0x <-> 2.5x 弹簧动画）；
 * 2. 双指捏合缩放（Pinch-to-zoom，1.0f ~ 4.0f 钳制）；
 * 3. 放大后随手拖动平移（Pan），动态计算图片尺寸与视口边界并钳制，防止图片被拖出屏幕外；
 * 4. 单击空白或关闭图标退出预览。
 */
@Composable
fun EpubImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val file = remember(imagePath) { File(imagePath) }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var imageSize by remember { mutableStateOf(IntSize.Zero) }

        val animatedScale = remember { Animatable(1f) }
        LaunchedEffect(scale) {
            animatedScale.animateTo(
                targetValue = scale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.2f) {
                                offset = Offset.Zero
                                1f
                            } else {
                                2.5f
                            }
                        },
                        onTap = {
                            if (scale <= 1.05f) {
                                onDismiss()
                            }
                        },
                    )
                }
                .pointerInput(containerSize, imageSize) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale

                        if (newScale > 1f && containerSize.width > 0 && containerSize.height > 0) {
                            val maxOffsetX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)

                            val newOffsetX = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val newOffsetY = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            offset = Offset(newOffsetX, newOffsetY)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "EPUB插图大图查看",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val currentScale = animatedScale.value
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .onSizeChanged { imageSize = it },
                contentScale = ContentScale.Fit,
            )

            // 顶部关闭微悬浮胶囊
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.size(38.dp),
                    onClick = onDismiss,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭大图",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 底部缩放读数指示器（仅在缩放大于 1.05 时淡现）
            AnimatedVisibility(
                visible = scale > 1.05f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = "%.1fx".format(scale),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
