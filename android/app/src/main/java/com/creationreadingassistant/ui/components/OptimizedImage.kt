package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision

/**
 * 按最终布局尺寸请求图片，避免书架封面以原图尺寸解码。
 * 首次测量前保留父容器背景，测得尺寸后才开始请求。
 */
@Composable
fun SizedAsyncImage(
    data: Any?,
    cacheKey: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier.onSizeChanged {
            if (it.width > 0 && it.height > 0 && it != targetSize) targetSize = it
        },
    ) {
        if (data != null && targetSize != IntSize.Zero) {
            val context = LocalContext.current
            val request = remember(data, cacheKey, targetSize) {
                ImageRequest.Builder(context)
                    .data(data)
                    .size(targetSize.width, targetSize.height)
                    .precision(Precision.INEXACT)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

/**
 * 阅读器图片没有固定高度，按当前视口建立解码上限；Compose 仍按图片宽高比布局。
 */
@Composable
fun rememberViewportImageRequest(
    data: Any,
    cacheKey: String,
): ImageRequest {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
    return remember(data, cacheKey, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(data)
            .size(widthPx, heightPx * 2)
            .precision(Precision.INEXACT)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(false)
            .build()
    }
}
