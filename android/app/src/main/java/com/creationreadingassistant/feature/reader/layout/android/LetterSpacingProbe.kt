package com.creationreadingassistant.feature.reader.layout.android

import android.graphics.Rect
import android.text.TextPaint

/**
 * 探测 `letterSpacing` 的墨水偏移语义。
 *
 * 背景：设了 `letterSpacing = d` 之后，minikin 会在每个字形左右各加 d/2，
 * 于是整行首字的墨水会比绘制起点右移 d/2、末字会比推进终点左移 d/2。
 * 要让行首行末精确贴边，绘制时需要整体左移 d/2 —— 这就是 legado 里那个
 * `drawDx = -d/2` 的来历。
 *
 * **但这个语义在 Android 15 变了**，而且由 targetSdkVersion 门控。
 * legado 源码里有五处 `VANILLA_ICE_CREAM`(API 35) 兼容分支即是明证。
 * 本项目当前 targetSdk = 34，但 Google Play 逐年强制推进，升到 35 是排期内的确定事件。
 * 届时若还硬编码 0.5，全书右边缘会整体偏移 d/2，且 d 逐行不同 → 参差。
 *
 * 与其猜，不如**运行期实测一次**：把 letterSpacing 设成一个已知值，
 * 看同一个字的墨水左边界移动了多少个 d。API<35 返回约 0.5，新语义返回约 0。
 * 顺带还覆盖了厂商 ROM 的差异。
 *
 * 结果进排版指纹 —— 它会影响 clusterX，缓存必须跟着失效。
 */
object LetterSpacingProbe {

    /**
     * @return 首字墨水相对绘制起点右移了几个 d。典型值 0.5f（旧语义）或 0f（新语义）。
     */
    fun probe(base: TextPaint): Float {
        return try {
            val p = TextPaint(base).apply {
                textSize = 100f
                letterSpacing = 0f
            }
            val r0 = Rect()
            p.getTextBounds("中", 0, 1, r0)

            p.letterSpacing = 1.0f // d = textSize * 1.0 = 100px
            val r1 = Rect()
            p.getTextBounds("中", 0, 1, r1)

            ((r1.left - r0.left) / 100f).coerceIn(0f, 0.5f)
        } catch (_: Throwable) {
            // 探测失败时取旧语义：这是 API 34 及以下的行为，也是当前 targetSdk 的行为
            0.5f
        }
    }
}
