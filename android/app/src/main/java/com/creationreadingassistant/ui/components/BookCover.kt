package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.SealMark

/**
 * 共享书籍封面组件。
 *
 * 统一三处（首页「继续阅读」/「已读完」、书架）重复的书脊渲染：
 * 图片加载、格式角标、斜向高光、藏书印，全部由此一处负责，
 * 消除各页面手写的 RoundedCornerShape / Color.Black 角标等散落实现。
 *
 * 无封面时由调用方通过 [fallback] 提供回退内容（各页面样式不同）。
 *
 * @param book            书籍实体
 * @param modifier       尺寸/位置修饰（调用方传 .size(...) 或 .fillMaxSize()）
 * @param shape          封面圆角，默认 [LocalComponentSpec.listItemShape]（10dp），与全站列表项一致
 * @param percent        阅读进度；非 null 且 >=99.5f 时盖藏书印（仅书架需要，首页传 null）
 * @param sealSize       藏书印尺寸
 * @param showBadge      是否显示格式角标
 * @param showSheen      是否显示斜向实体书高光
 * @param fallback       无封面时的回退内容
 */
@Composable
fun BookCover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    shape: Shape = LocalComponentSpec.current.listItemShape,
    percent: Float? = null,
    sealSize: Dp = 22.dp,
    showBadge: Boolean = true,
    showSheen: Boolean = true,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasImage = !book.cover_data_url.isNullOrBlank()
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            SizedAsyncImage(
                data = book.cover_data_url,
                cacheKey = "cover:${book.id}:${book.updated_at}",
                contentDescription = "《${book.title}》封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            fallback()
        }
        if (showBadge) {
            // 格式角标：随主题自适应。用 inverseSurface/inverseOnSurface 这对对比令牌，
            // 亮色模式=深色底+浅字（与旧 Color.Black 等价），暗色模式=浅色底+深字，
            // 彻底解决暗色下黑角标压在深色封面上"看不清"的问题。
            Surface(
                color = scheme.inverseSurface.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(if (hasImage) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Text(
                    book.format.uppercase(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.inverseOnSurface,
                )
            }
        }
        if (showSheen) {
            // 封面微高光：左上→右下极淡白色斜向光泽，强化实体书质感
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.16f),
                                0.42f to Color.White.copy(alpha = 0.0f),
                            ),
                        ),
                    ),
            )
        }
        if (percent != null && percent >= 99.5f) {
            // 藏书印：读完才盖。必须画在高光层之上，否则会被叠加层压住。
            SealMark(
                size = sealSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}
