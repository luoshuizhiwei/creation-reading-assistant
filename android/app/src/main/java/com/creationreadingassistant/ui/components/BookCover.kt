package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.creationreadingassistant.ui.theme.AppShapes
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.SealMark

/**
 * 低饱和多色占位封面色板（冻结规范 §8 书架：灰绿/陶土/石板蓝/藕灰/暖灰等互异低彩度色）。
 * 按书籍 id 稳定分配，每本书不同色，消灭「全部同一个灰蓝矩形」的单调观感。
 */
private val CoverPalette = listOf(
    Color(0xFF7E93AE), // 石板蓝
    Color(0xFF9CAEA0), // 灰绿
    Color(0xFFC08466), // 陶土
    Color(0xFFA89AA2), // 藕灰
    Color(0xFFB7A98F), // 暖灰
    Color(0xFF8FA8C8), // 雾蓝
    Color(0xFFAD8FA8), // 灰紫
)

private fun coverLuma(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

/**
 * 无封面时的共享占位封面：多色低饱和渐变 + 衬线书名 + 格式标注。
 * 书架网格与首页「继续阅读 / 已读完」卡片统一消费，全站占位封面同一语言。
 * 深态整体降明度；文字色随封面明度取反保证可读。
 */
@Composable
fun MutedCoverFallback(
    book: BookEntity,
    modifier: Modifier = Modifier,
    maxTitleChars: Int = 12,
    showFormat: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = coverLuma(scheme.surface) < 0.5f
    // 高位混合后再取模：UUID 风格 id 低位相似时也能散到不同色，减少相邻书撞色
    val h = book.id.hashCode()
    val spread = ((h xor (h ushr 16)).toLong() and 0x7fffffffL).toInt()
    val base = CoverPalette[spread % CoverPalette.size]
    val top = if (dark) androidx.compose.ui.graphics.lerp(base, Color.Black, 0.42f) else androidx.compose.ui.graphics.lerp(base, Color.White, 0.14f)
    val bottom = if (dark) androidx.compose.ui.graphics.lerp(base, Color.Black, 0.58f) else androidx.compose.ui.graphics.lerp(base, Color.Black, 0.14f)
    val ink = if (coverLuma(if (dark) bottom else base) > 0.55f) Color(0xFF2A2E35) else Color(0xFFF5F3EE)
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 书脊纹理：左缘 4dp，按书籍 id 稳定分配（布纹/皮纹/毛边/烫金/麻面）；
        // 墨色按封面底色明度取反，保证可读。
        SpineTexture(
            kind = spineKindOf(book.id),
            baseInk = if (coverLuma(base) > 0.5f) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.30f),
            modifier = Modifier
                .align(Alignment.Start)
                .fillMaxHeight()
                .width(4.dp),
        )
        Text(
            book.title.take(maxTitleChars),
            color = ink,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.creationreadingassistant.ui.theme.DisplayFontFamily),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (showFormat) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            Text(
                book.format.uppercase(),
                color = ink.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

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
 * @param overlay        覆盖在最上层的内容槽（如「已收藏」状态印章），由调用方自行定位
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
    overlay: (@Composable BoxScope.() -> Unit)? = null,
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
                shape = AppShapes.extraSmall,
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
            // 封面微高光：左上→右下极淡白色斜向光泽，强化实体书质感。
            // Color.White 在此是「光」的视觉语言（物理高光），不是 UI 主题色：
            // 深色模式下封面底色变深，白色光泽仍呈现为高光，故故意脱离主题令牌。
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
        // 调用方叠加层（如「已收藏」状态印章），画在所有内置层之上。
        overlay?.invoke(this)
    }
}
