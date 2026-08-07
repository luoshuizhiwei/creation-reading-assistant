package com.creationreadingassistant.feature.reader.layout.android

import android.graphics.Typeface
import android.text.TextPaint
import com.creationreadingassistant.feature.reader.layout.CharClass
import com.creationreadingassistant.feature.reader.layout.TextRuler
import android.util.SparseArray

/**
 * 基于 `TextPaint.getTextWidths()` 的测宽器。
 *
 * 为什么是它而不是 Compose 的 TextMeasurer：Compose 不存在「不做布局就拿到字符宽度」
 * 的 API（`getBoundingBox` 首条指令即 `getLineForOffset`，必须先有 StaticLayout）。
 * 平台的 `getTextWidths()` 是唯一主路。
 *
 * 三级缓存（思路参考 legado 的 TextMeasure，实现独立编写）：
 *
 * 1. **ASCII 直接查表**：0..127 一次性测好存进 FloatArray
 * 2. **CJK 等宽短路**：中文正文里汉字压倒性多数且等宽，测一个「一」即可复用。
 *    这是性能的大头 —— 三万字的章节里两万多个汉字全部零成本
 * 3. **其余走 SparseArray**：全角标点、日文假名、生僻符号等
 *
 * 线程安全：每个实例持有自己的 TextPaint 副本。排版跑在后台协程，
 * 而 UI 线程可能同时在用同一支 Paint 绘制，共享会踩踏。
 */
class PaintTextRuler(
    source: TextPaint,
    override val typefaceKey: String,
) : TextRuler {

    /** 副本，绝不与绘制共用 */
    private val paint = TextPaint(source)

    override val fontSizePx: Float = paint.textSize

    private val asciiWidths = FloatArray(128)
    private val cjkWidth: Float
    private val otherWidths = SparseArray<Float>()
    private val scratch = FloatArray(2)

    init {
        val buf = CharArray(1)
        for (c in 0..127) {
            buf[0] = c.toChar()
            paint.getTextWidths(buf, 0, 1, scratch)
            asciiWidths[c] = scratch[0]
        }
        buf[0] = '一'
        paint.getTextWidths(buf, 0, 1, scratch)
        cjkWidth = scratch[0]
    }

    override fun getTextWidths(text: String, out: FloatArray) {
        require(out.size >= text.length) { "out 长度不足：${out.size} < ${text.length}" }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val code = c.code

            // 代理对不能逐字符测，必须整体交给 Paint
            if (Character.isHighSurrogate(c) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1])
            ) {
                paint.getTextWidths(text, i, i + 2, scratch)
                out[i] = scratch[0]
                out[i + 1] = scratch[1]
                i += 2
                continue
            }

            out[i] = when {
                code < 128 -> asciiWidths[code]
                // 只有「统一表意文字」这一段能安全走等宽短路：
                // 全角标点虽然也占一格，但有些字体里并非严格等宽，仍走实测
                code in 0x4E00..0x9FFF -> cjkWidth
                else -> otherWidths[code] ?: measureOne(text, i).also { otherWidths.put(code, it) }
            }
            i++
        }
    }

    private fun measureOne(text: String, index: Int): Float {
        paint.getTextWidths(text, index, index + 1, scratch)
        return scratch[0]
    }

    companion object {
        /**
         * 生成字体标识，进排版指纹。
         *
         * 项目没有内置字体（assets/ 与 res/font 都不存在），默认全走系统 CJK 回退，
         * 所以 Android 12+ 用户换系统字体会在运行中改变排版。指纹必须能反映这一点，
         * 否则缓存的页边界会按旧字体算。
         *
         * [customFontPath] 为自定义正文字体路径（空 = 系统字体）。自定义字体必须把路径
         * 写进指纹：换字体后排版签名变化，页索引整体失效重排，页码/锚点才不会错位。
         */
        fun typefaceKeyOf(typeface: Typeface?, textSizePx: Float, letterSpacing: Float, customFontPath: String = ""): String {
            val tf = if (customFontPath.isNotBlank()) customFontPath else (typeface?.hashCode() ?: 0)
            return "tf=$tf|size=$textSizePx|ls=$letterSpacing"
        }
    }
}
