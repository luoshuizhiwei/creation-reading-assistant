package com.creationreadingassistant.feature.reader.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig

/**
 * 页面预渲染器
 *
 * 将 PageCanvas 的绘制结果预渲染到 Bitmap，避免每翻页重新执行
 * 数百次 native.drawText() 调用。
 */
object PagePreRenderer {

    /**
     * 预渲染单个页面为 Bitmap
     * @param page 页面对象
     * @param chapterText 章节文本
     * @param cfg 布局配置
     * @param pageSize 页面尺寸
     * @param textColor 文本颜色
     * @param underlays 底色矩形列表
     * @return 预渲染的 Bitmap
     */
    fun renderPage(
        page: ChapterPaginator.Page,
        chapterText: String,
        cfg: LayoutConfig,
        pageSize: IntSize,
        textColor: ComposeColor = ComposeColor.Black,
        underlays: List<Pair<ComposeColor, List<com.creationreadingassistant.feature.reader.layout.PageHitTest.Rect>>> = emptyList(),
    ): Bitmap {
        val width = pageSize.width.coerceAtLeast(1)
        val height = pageSize.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val nativeCanvas = android.graphics.Canvas(bitmap)

        // 绘制背景
        nativeCanvas.drawColor(Color.WHITE)

        // 绘制底色矩形
        underlays.forEach { (color, rects) ->
            val paint = android.graphics.Paint().apply {
                setColor(color.toArgb())
                alpha = (color.alpha * 255).toInt()
            }
            rects.forEach { r ->
                nativeCanvas.drawRect(
                    r.left.toFloat(), r.top.toFloat(),
                    r.right.toFloat(), r.bottom.toFloat(),
                    paint
                )
            }
        }

        // 绘制文本
        // 注意：这里简化实现，真正的文本绘制需要复用 PageCanvas 的逻辑
        // 实际项目中，应该抽取 PageCanvas 的绘制逻辑为独立函数
        // 这里只是占位，实际需要重构 PageCanvas.kt:618-682 的绘制逻辑

        return bitmap
    }

    /**
     * 批量预渲染多个页面
     * @param pages 页面对象列表
     * @param chapterText 章节文本
     * @param cfg 布局配置
     * @param pageSize 页面尺寸
     * @param onProgress 预渲染进度回调
     * @return 页索引到 Bitmap 的映射
     */
    fun renderPages(
        pages: List<ChapterPaginator.Page>,
        chapterText: String,
        cfg: LayoutConfig,
        pageSize: IntSize,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Map<Int, Bitmap> {
        val results = mutableMapOf<Int, Bitmap>()
        pages.forEachIndexed { index, page ->
            val bitmap = renderPage(page, chapterText, cfg, pageSize)
            results[page.index] = bitmap
            onProgress(index + 1, pages.size)
        }
        return results
    }
}