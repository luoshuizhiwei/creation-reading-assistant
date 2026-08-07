package com.creationreadingassistant.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 纸墨风轻噪点纹理（中性灰度，跨亮/暗纸通用）。
 *
 * 一次性生成 96×96 平铺噪点 [Bitmap] 并缓存为单例，经 [Brush.image] 以 [TileMode.Repeated]
 * 平铺。噪点为中性中灰：在浅纸显暗纹、在深纸显亮纹，均克制可见；实际透明度由调用处 alpha
 * 控制，不破坏可读性对比度。
 *
 * 无资源文件、无逐帧绘制，滚动重绘零开销（纹理容器静止，文字在其上流动）。
 */
object PaperNoise {
    private var cached: ImageBitmap? = null

    private fun build(): ImageBitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(0x9E3779B9L)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = random.nextInt(256) // 0..255 灰度值
                bitmap.setPixel(x, y, android.graphics.Color.argb(255, n, n, n))
            }
        }
        return bitmap.asImageBitmap()
    }

    fun brush(): Brush {
        val bitmap = cached ?: build().also { cached = it }
        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                return ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)
            }
        }
    }
}
