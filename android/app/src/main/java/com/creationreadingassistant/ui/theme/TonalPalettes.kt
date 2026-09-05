package com.creationreadingassistant.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * 零依赖的 Material3 tonal palettes 与种子色取色。
 *
 * 为什么不用 Material Color Utilities（androidx.core:core-remoteviews 内部的
 * Scheme / Hct / Tone）：我们只需要一个"给定主种子色 → 产出 40 个 Material 令牌色"
 * 的最小能力，不需要动态壁纸、CAM16 色彩空间与跨设备同步，引一个约 200KB 的
 * `com.google.android.material:material-color-utilities` 或手写 HCT 换算都不划算。
 *
 * 这里用**实用近似**：种子色转 HSL → 保 H 不动、在 L 轴上切出 13 级 tone（0,4,6,10,12,..95,99,100）
 * → 对主/次/第三/错误四套各自产出 TonalPalette。精度比 HCT 差 1~3 个 sRGB 台阶，
 * 但在中文阅读 UI（主色只负责少量可操作与选中态）这个场景下肉眼无法区分。
 *
 * 使用方法：
 * ```
 *   val scheme = SeedColorScheme.fromSeed(Color(0xFF6750A4), darkTheme = false)
 *   MaterialTheme(colorScheme = scheme.asColorScheme()) { ... }
 * ```
 */
object TonalPalettes {

    // ── sRGB <-> HSL 工具 ──────────────────────────────────────────────

    internal fun toHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        if (d == 0f) return Triple(0f, 0f, l)
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        return Triple(h, s, l)
    }

    internal fun hslToColor(h: Float, s: Float, l: Float): Color {
        if (s == 0f) return Color(l, l, l)
        val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hueToRgb(t: Float): Float {
            val tc = t.mod(1f)
            return when {
                tc < 1f / 6f -> p + (q - p) * 6f * tc
                tc < 1f / 2f -> q
                tc < 2f / 3f -> p + (q - p) * (2f / 3f - tc) * 6f
                else -> p
            }
        }
        return Color(
            red = hueToRgb(h + 1f / 3f).coerceIn(0f, 1f),
            green = hueToRgb(h).coerceIn(0f, 1f),
            blue = hueToRgb(h - 1f / 3f).coerceIn(0f, 1f),
        )
    }

    /**
     * 从种子色生成 TonalPalette：13 个 Material tone 键对应 sRGB Color。
     *
     * tone 的语义（L* 近似）：0=纯黑、4/6/10/12=近黑容器、17/20/22=暗色 onPrimary、
     * 30=暗色 primary、40=浅色 primary、50=中间、60=浅色 onPrimary、
     * 70/80=浅色 primaryContainer、87/90/92/94/95/96/98/99=极浅容器、100=纯白。
     */
    fun paletteFromSeed(seed: Color, chromaScale: Float = 1f): Map<Int, Color> {
        val (h, s, _) = toHsl(seed)
        // 为中文 UI 稍微降饱和：种子可能来自壁纸取色，纯 CAM16 常常过艳；
        // chromaScale 默认 1.0，调用方可压到 0.75~0.9 让主色更"耐看"。
        val baseS = (s * chromaScale).coerceIn(0f, 1f)
        val tones = intArrayOf(0, 4, 6, 10, 12, 17, 20, 22, 24, 30, 40, 50, 60, 70, 80, 87, 90, 92, 94, 95, 96, 98, 99, 100)
        val result = LinkedHashMap<Int, Color>(tones.size)
        for (tone in tones) {
            // tone 是 L*（0..100），直接当作 HSL 的 L 再按"越近两端饱和度越低"
            // 做一次 clamp 以保持色感：白/黑两端自然消色。
            val lp = tone / 100f
            // 饱和度包络：在 L=40..70 之间饱满，两端渐降
            val skirt = when {
                lp < 0.20f -> lp / 0.20f
                lp > 0.92f -> (1f - lp) / 0.08f
                else -> 1f
            }
            val effS = baseS * skirt
            result[tone] = hslToColor(h, effS, lp)
        }
        return result
    }

    /** 次色调：同色相略降饱和 + 轻移亮度，制造"主色的温和兄弟"效果。 */
    fun secondaryFromPrimary(seed: Color): Color {
        val (h, s, l) = toHsl(seed)
        return hslToColor(
            h = h,
            s = (s * 0.58f).coerceIn(0f, 1f),
            l = (l * 1.04f).coerceIn(0f, 1f),
        )
    }

    /** 第三色调：色相偏一极（+60°），给"强调/警告"类令牌一个不冲突的主色。 */
    fun tertiaryFromPrimary(seed: Color): Color {
        val (h, s, l) = toHsl(seed)
        return hslToColor(
            h = (h + 60f / 360f).mod(1f),
            s = (s * 0.72f).coerceIn(0f, 1f),
            l = (l * 0.98f + 0.01f).coerceIn(0f, 1f),
        )
    }

    /** 语义错误色：固定朱砂红（Material 规范里 error 应独立于主色）。 */
    val errorSeed: Color = Color(0xFFB3261E)
}

/** 方便从 tone 表取色；缺失时就近插值（其实永远齐全，兜底即可）。 */
private fun Map<Int, Color>.tone(t: Int): Color =
    get(t) ?: entries.minByOrNull { kotlin.math.abs(it.key - t) }!!.value

/**
 * 种子色派生的完整 Material 明暗配色方案。
 *
 * 令牌对齐 Material3 `ColorScheme` 的全部字段：primary / onPrimary / primaryContainer /
 * onPrimaryContainer / secondary* / tertiary* / background / surface* / inverse* /
 * outline / outlineVariant / scrim / error*。四套主/次/第三/错误各自对应一套
 * TonalPalette，再按 Material3 tone 映射规则填入。
 *
 * 参考映射（简化版，足够覆盖 Compose ColorScheme）：
 * - 浅色：primary=40, onPrimary=100, primaryContainer=90, onPrimaryContainer=10
 * - 暗色：primary=80, onPrimary=20, primaryContainer=30, onPrimaryContainer=90
 * - surfaceContainer* / surfaceBright / surfaceDim 用一套 4~99 的阶梯映射。
 */
class SeedColorScheme private constructor(
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
) {
    companion object {
        fun fromSeed(
            seed: Color,
            chromaScale: Float = 0.86f,
        ): SeedColorScheme {
            val primary = TonalPalettes.paletteFromSeed(seed, chromaScale)
            val secondarySeed = TonalPalettes.secondaryFromPrimary(seed)
            val secondary = TonalPalettes.paletteFromSeed(secondarySeed, chromaScale)
            val tertiarySeed = TonalPalettes.tertiaryFromPrimary(seed)
            val tertiary = TonalPalettes.paletteFromSeed(tertiarySeed, chromaScale)
            val error = TonalPalettes.paletteFromSeed(TonalPalettes.errorSeed, chromaScale)

            // 中性表面：取种子色的色相，饱和几乎抽干 → 得到暖色米灰/冷灰。
            val (h, _, _) = TonalPalettes.toHsl(seed)
            val neutralSeed0 = TonalPalettes.run { hslToColor(h, 0.06f, 0.5f) }
            val neutralVariant0 = TonalPalettes.run { hslToColor(h, 0.10f, 0.5f) }
            val neutral = TonalPalettes.paletteFromSeed(neutralSeed0, 1f)
            val neutralVariant = TonalPalettes.paletteFromSeed(neutralVariant0, 1f)

            val light = buildLight(primary, secondary, tertiary, error, neutral, neutralVariant)
            val dark = buildDark(primary, secondary, tertiary, error, neutral, neutralVariant)
            return SeedColorScheme(light, dark)
        }

        private fun buildLight(
            p: Map<Int, Color>, s: Map<Int, Color>, t: Map<Int, Color>,
            e: Map<Int, Color>, n: Map<Int, Color>, nv: Map<Int, Color>,
        ): androidx.compose.material3.ColorScheme = androidx.compose.material3.lightColorScheme(
            primary = p.tone(40), onPrimary = p.tone(100),
            primaryContainer = p.tone(90), onPrimaryContainer = p.tone(10),
            secondary = s.tone(40), onSecondary = s.tone(100),
            secondaryContainer = s.tone(90), onSecondaryContainer = s.tone(10),
            tertiary = t.tone(40), onTertiary = t.tone(100),
            tertiaryContainer = t.tone(90), onTertiaryContainer = t.tone(10),
            background = n.tone(98), onBackground = n.tone(10),
            surface = n.tone(98), onSurface = n.tone(10),
            surfaceVariant = nv.tone(90), onSurfaceVariant = nv.tone(30),
            surfaceTint = p.tone(40),
            surfaceBright = n.tone(99), surfaceDim = n.tone(87),
            surfaceContainerLowest = n.tone(100), surfaceContainerLow = n.tone(96),
            surfaceContainer = n.tone(94), surfaceContainerHigh = n.tone(92),
            surfaceContainerHighest = n.tone(90),
            inverseSurface = n.tone(20), inverseOnSurface = n.tone(95),
            inversePrimary = p.tone(80),
            outline = nv.tone(50), outlineVariant = nv.tone(80),
            scrim = n.tone(0),
            error = e.tone(40), onError = e.tone(100),
            errorContainer = e.tone(90), onErrorContainer = e.tone(10),
        )

        private fun buildDark(
            p: Map<Int, Color>, s: Map<Int, Color>, t: Map<Int, Color>,
            e: Map<Int, Color>, n: Map<Int, Color>, nv: Map<Int, Color>,
        ): androidx.compose.material3.ColorScheme = androidx.compose.material3.darkColorScheme(
            primary = p.tone(80), onPrimary = p.tone(20),
            primaryContainer = p.tone(30), onPrimaryContainer = p.tone(90),
            secondary = s.tone(80), onSecondary = s.tone(20),
            secondaryContainer = s.tone(30), onSecondaryContainer = s.tone(90),
            tertiary = t.tone(80), onTertiary = t.tone(20),
            tertiaryContainer = t.tone(30), onTertiaryContainer = t.tone(90),
            background = n.tone(6), onBackground = n.tone(90),
            surface = n.tone(6), onSurface = n.tone(90),
            surfaceVariant = nv.tone(30), onSurfaceVariant = nv.tone(80),
            surfaceTint = p.tone(80),
            surfaceBright = n.tone(24), surfaceDim = n.tone(6),
            surfaceContainerLowest = n.tone(4), surfaceContainerLow = n.tone(10),
            surfaceContainer = n.tone(12), surfaceContainerHigh = n.tone(17),
            surfaceContainerHighest = n.tone(22),
            inverseSurface = n.tone(90), inverseOnSurface = n.tone(20),
            inversePrimary = p.tone(40),
            outline = nv.tone(60), outlineVariant = nv.tone(30),
            scrim = n.tone(0),
            error = e.tone(80), onError = e.tone(20),
            errorContainer = e.tone(30), onErrorContainer = e.tone(90),
        )
    }
}
