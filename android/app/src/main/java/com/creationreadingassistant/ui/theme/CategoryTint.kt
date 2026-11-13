package com.creationreadingassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * 分类 / 状态语义色令牌（CategoryTint）。
 *
 * 背景：ProfileHome / Search / Inspiration / stats / home 的分类图标色此前为**仅浅色硬编码**
 * `Color(0xFF…)`，无深色变体。在深色表面（清爽蓝深 #161A20 / 纸墨深 #1E221F / AMOLED #000000）
 * 上，20 个常用色中 13 个对比度 < 3:1（WCAG 非文本最低线），图标不可辨。
 *
 * 本令牌提供成对浅 / 深值：
 * - **浅色沿用现网硬编码值** → 浅色模式像素级零回归；
 * - **深色取同色相提亮版**，对全部深色表面实算 ≥ 6.9:1（远超图标 3:1，亦 ≥ 4.5 可作文本）。
 *
 * 深浅判定复用 [com.creationreadingassistant.ui.components.SectionCard] 既有法：读
 * `MaterialTheme.colorScheme.surface` 的感知亮度（luma），**不读 isSystemInDarkTheme()**——
 * 后者只看系统夜间模式，与 App 自己的浅 / 深 / 跟随枚举并不保证一致，会导致 AMOLED 纯黑 /
 * 动态色 / 应用内深浅与系统不一致时误判。阅读器子树内 [ReaderPaperTheme] 已把 surface 重映射
 * 为 paper.panel，故本令牌在阅读器内自动随纸明暗。
 *
 * 文本用途专用变体（[categoryTintText]）：浅色表面上 amber / emerald / sky / teal 作图标 ≥ 3:1
 * 合格、作文本 < 4.5 不合格，故另给加深版（≥ 4.5）；深色直接用提亮版（均 ≥ 4.5）。
 * **仅当某色实际用于文本时**才取 text 变体。
 *
 * 色值与对比度证据见规格 SPEC §2.3 与 contrast_evidence.py（可复现）。本令牌为纯新增，
 * 不改 [Theme.kt] 现有配色映射真源，不引入新依赖。
 */
data class CategoryTintPair(
    /** 浅色值（沿用现网硬编码，浅色零回归）。 */
    val light: Color,
    /** 深色值（同色相提亮，深色表面实算 ≥ 6.9:1）。 */
    val dark: Color,
    /** 浅色表面文本专用加深值（≥ 4.5:1）；为 null 时文本直接取 [light]。 */
    val lightText: Color? = null,
)

/**
 * 20 个分类 / 状态语义色的浅深对。键名与规格 SPEC §2.3 表一一对应。
 * 浅色 = 现网硬编码原值；深色 = 实算达标提亮候选（owner 已批准 D1/D2）。
 */
val CategoryTints: Map<String, CategoryTintPair> = mapOf(
    "violet" to CategoryTintPair(Color(0xFF6750A4), Color(0xFFCBB8F5)),
    "teal" to CategoryTintPair(Color(0xFF00897B), Color(0xFF7FD1C0), lightText = Color(0xFF0F766E)),
    "blue" to CategoryTintPair(Color(0xFF00639B), Color(0xFFA8C6FF)),
    "cyan" to CategoryTintPair(Color(0xFF006874), Color(0xFF8FD0DA)),
    "rust" to CategoryTintPair(Color(0xFF984715), Color(0xFFE8B48A)),
    "slate" to CategoryTintPair(Color(0xFF5B5B7E), Color(0xFFB9B9D6)),
    "pine" to CategoryTintPair(Color(0xFF386568), Color(0xFF9CC5C8)),
    "plum" to CategoryTintPair(Color(0xFF6B5778), Color(0xFFC9B3D6)),
    "steel" to CategoryTintPair(Color(0xFF4C626B), Color(0xFFAEC4CD)),
    "link" to CategoryTintPair(Color(0xFF1967D2), Color(0xFFA8C6FF)),
    "sky" to CategoryTintPair(Color(0xFF0284C7), Color(0xFF7CC7F0), lightText = Color(0xFF0369A1)),
    "purple" to CategoryTintPair(Color(0xFF7B1FA2), Color(0xFFD9A8E8)),
    "diag" to CategoryTintPair(Color(0xFF4A6572), Color(0xFFA9C2CD)),
    "green" to CategoryTintPair(Color(0xFF2E7D32), Color(0xFF9CCC9C)),
    "gray" to CategoryTintPair(Color(0xFF5E6266), Color(0xFFB9BDC1)),
    "amber" to CategoryTintPair(Color(0xFFD97706), Color(0xFFF0B35C), lightText = Color(0xFFB45309)),
    "emerald" to CategoryTintPair(Color(0xFF059669), Color(0xFF6FCF9F), lightText = Color(0xFF047857)),
    "indigo" to CategoryTintPair(Color(0xFF7C3AED), Color(0xFFB9A3F5)),
    "blue2" to CategoryTintPair(Color(0xFF2563EB), Color(0xFF93B4F5)),
    "rose" to CategoryTintPair(Color(0xFFBE185D), Color(0xFFF08CB0)),
)

/**
 * 当前表面是否为深色：基于实际 ColorScheme.surface 的感知亮度（luma），
 * 与 [com.creationreadingassistant.ui.components.SectionCard] 同一判据（阈值 0.5）。
 * 不读 isSystemInDarkTheme，兼容 AMOLED / 动态色 / 应用内深浅与系统不一致。
 */
@Composable
fun rememberCategoryTintDark(): Boolean {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) {
        0.299f * surface.red + 0.587f * surface.green + 0.114f * surface.blue < 0.5f
    }
}

/**
 * 取分类色（图标 / 非文本用途，≥ 3:1）。按当前表面明暗返回浅 / 深值。
 * 未知键回退到当前主题 primary，避免崩溃（调用方均使用已登记键）。
 */
@Composable
fun categoryTint(name: String): Color {
    val dark = rememberCategoryTintDark()
    val pair = CategoryTints[name] ?: return MaterialTheme.colorScheme.primary
    return if (dark) pair.dark else pair.light
}

/**
 * 取分类色的**文本专用**变体（≥ 4.5:1）。浅色表面取加深版（无加深版则取浅值），
 * 深色表面取提亮版（均 ≥ 4.5）。仅当某色实际用于文本时调用。
 */
@Composable
fun categoryTintText(name: String): Color {
    val dark = rememberCategoryTintDark()
    val pair = CategoryTints[name] ?: return MaterialTheme.colorScheme.onSurface
    return if (dark) pair.dark else (pair.lightText ?: pair.light)
}