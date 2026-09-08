package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 低饱和多色占位封面色板（冻结规范 §8 书架：灰绿/陶土/石板蓝/藕灰/暖灰等互异低彩度色）。
 * 按书籍 id 稳定分配，每本书不同色，消灭「全部同一个灰蓝矩形」的单调观感。
 */
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * 现代高质感纯色书卷色板：雾蓝、鼠尾草绿、烟褐、灰青、暖灰棕、墨青、雾紫、铅灰。
 * 自然雅致、低彩度、不古董沉闷，按书籍 id 稳定哈希分配。
 */
private val ModernCoverPalette = listOf(
    Color(0xFF3F4F5E), // 雾蓝
    Color(0xFF4A5B52), // 鼠尾草灰绿
    Color(0xFF5E4E52), // 烟褐
    Color(0xFF484A59), // 灰青
    Color(0xFF5A5245), // 暖灰棕
    Color(0xFF3E5459), // 墨青
    Color(0xFF544A5E), // 雾紫
    Color(0xFF424A52), // 铅灰
)

/**
 * 左侧克制自然的实体书微阴影画笔（顶级常量，零 GC 分配）。
 */
private val CoverSpineShadowBrush = Brush.horizontalGradient(
    listOf(
        Color.Black.copy(alpha = 0.25f),
        Color.Black.copy(alpha = 0.08f),
        Color.Transparent,
    ),
)

/**
 * 封面微高光：左上→右下极淡白色斜向光泽，强化实体书质感（顶级常量，零 GC 分配）。
 */
private val CoverSheenBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.0f to Color.White.copy(alpha = 0.16f),
        0.42f to Color.White.copy(alpha = 0.0f),
    ),
)

/**
 * 现代阅读器风格的默认书籍封面：
 * 1. 自然低彩度书卷底版 + 柔和垂直微光；
 * 2. 左侧细腻克制的实体书微阴影；
 * 3. 干净大气的居中书名排版 + 真实作者名（若有）；
 * 4. 彻底去除“典藏版/精排/经典巨献”等夸张文案与浮夸烫金装饰，清爽现代耐看；
 * 5. 支持大封面（网格）与小封面（列表）自适应，小尺寸下自动精简与缩放，杜绝挤压截断。
 */
@Composable
fun MutedCoverFallback(
    book: BookEntity,
    modifier: Modifier = Modifier,
    maxTitleChars: Int = 20,
    @Suppress("UNUSED_PARAMETER") showFormat: Boolean = false,
    isCompact: Boolean = false,
) {
    val h = book.id.hashCode()
    val spread = ((h xor (h ushr 16)).toLong() and 0x7fffffffL).toInt()
    val base = ModernCoverPalette[spread % ModernCoverPalette.size]

    val backgroundBrush = remember(book.id, base) {
        val top = androidx.compose.ui.graphics.lerp(base, Color.White, 0.06f)
        val bottom = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.12f)
        Brush.verticalGradient(listOf(top, bottom))
    }

    val spineWidth = if (isCompact) 4.dp else 6.dp
    val horizontalStartPadding = if (isCompact) 6.dp else 12.dp
    val horizontalEndPadding = if (isCompact) 4.dp else 10.dp
    val verticalPadding = if (isCompact) 5.dp else 14.dp
    val titleSize = if (isCompact) 9.5.sp else 13.sp
    val titleLineHeight = if (isCompact) 12.sp else 17.sp
    val maxLines = if (isCompact) 3 else 4

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        // 1. 左侧克制自然的实体书微阴影
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(spineWidth)
                .background(CoverSpineShadowBrush),
        )

        // 2. 居中书名与作者。文件格式属于详情元数据，不在封面上重复标注。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalStartPadding,
                    end = horizontalEndPadding,
                    top = verticalPadding,
                    bottom = verticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = book.title.take(maxTitleChars),
                color = Color.White.copy(alpha = 0.95f),
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = titleLineHeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = if (isCompact) 0.sp else 0.3.sp,
            )

            val author = book.author?.trim()?.takeIf { it.isNotBlank() }
            if (author != null && !isCompact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = author.take(12),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    showSheen: Boolean = true,
    fallback: @Composable BoxScope.() -> Unit,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var imageLoadFailed by remember(book.id, book.updated_at, book.cover_data_url) { mutableStateOf(false) }
    val hasImage = !book.cover_data_url.isNullOrBlank() && !imageLoadFailed
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
                onError = { imageLoadFailed = true },
            )
        } else {
            fallback()
        }
        // 格式角标（TXT/EPUB）已移除：真机反馈角标压在小说封面上碍眼。
        // 无封面回退态仍由 MutedCoverFallback 居中标注格式，那里不遮画。
        if (showSheen) {
            // 封面微高光：左上→右下极淡白色斜向光泽，强化实体书质感。
            // Color.White 在此是「光」的视觉语言（物理高光），不是 UI 主题色：
            // 深色模式下封面底色变深，白色光泽仍呈现为高光，故故意脱离主题令牌。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CoverSheenBrush),
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
