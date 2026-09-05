package com.creationreadingassistant.ui.screen.profile

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingSwitchRow
import com.creationreadingassistant.ui.theme.AppPalette
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 外观偏好设置微岛 ==============================

@Composable
internal fun AppearanceSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val dynamicColorActive = state.appearance.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val currentPalette = AppPalette.fromStored(state.appearance.colorPalette)
    val isDark = when (state.appearance.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 提示微岛：独立外壳配色与阅读器纸张的作用域说明
        item(key = "appearance_scope_note") {
            DegradedNote(
                text = "应用外壳配色仅影响首页、书架、灵感、统计和设置；正文阅读纸张与夜读遮罩在阅读器内独立控制。",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            )
        }

        // 2. 微岛分节：外观模式与材质
        item(key = "appearance_mode_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubPageSectionTitle(
                        title = "外观模式",
                        icon = Icons.Outlined.DarkMode,
                        iconTint = Color(0xFF6366F1),
                    )

                    // 现代化平滑导轨三态分段选择微岛
                    ThemeModeRailSegment(
                        currentMode = state.appearance.themeMode,
                        onModeChange = { newMode ->
                            onAction(ProfileAction.UpdateAppearance { copy(themeMode = newMode) })
                        },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "AMOLED 纯黑",
                        subtitle = "深色模式下将背景底色收敛为纯黑，OLED 屏更省电且对比度更纯粹；不影响阅读正文纸张",
                        checked = state.appearance.amoledPureBlack,
                        onCheckedChange = { checked ->
                            onAction(ProfileAction.UpdateAppearance { copy(amoledPureBlack = checked) })
                        },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "纸张质感底纹",
                        subtitle = "在界面卡片与底层叠加极淡素笺纸纹与柔和漫反射，提升温润雅致质感",
                        checked = state.appearance.paperTexture,
                        onCheckedChange = { checked ->
                            onAction(ProfileAction.UpdateAppearance { copy(paperTexture = checked) })
                        },
                    )
                }
            }
        }

        // 3. 微岛分节：外壳配色调色板
        item(key = "appearance_palette_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubPageSectionTitle(
                        title = "配色主题",
                        icon = Icons.Outlined.Palette,
                        iconTint = Color(0xFFEC4899),
                        trailing = {
                            if (dynamicColorActive) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                ) {
                                    Text(
                                        text = "动态壁纸取色中",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        },
                    )

                    // 动态壁纸取色（Material You）微岛
                    SettingSwitchRow(
                        title = "跟随系统壁纸取色",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Material You 动态色彩：自动从当前系统壁纸提取柔和主色调"
                        } else {
                            "需要 Android 12 (API 31) 及以上系统，当前系统版本不支持"
                        },
                        checked = dynamicColorActive,
                        onCheckedChange = { checked ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                onAction(ProfileAction.UpdateAppearance { copy(useDynamicColor = checked) })
                            }
                        },
                    )

                    SectionDivider()

                    Text(
                        text = "预置微胶囊色板",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 圆润微胶囊色板（纸墨、清爽蓝、雾青、暖杏、黛檀、松玉）
                    PaletteGrid(
                        currentPalette = currentPalette,
                        isDark = isDark,
                        disabled = dynamicColorActive,
                        onSelect = { palette ->
                            onAction(ProfileAction.UpdateAppearance { copy(colorPalette = palette.storageId) })
                        },
                    )
                }
            }
        }
    }
}

/**
 * 现代化平滑导轨主题模式选择微岛（三态：跟随系统 / 浅色 / 深色）。
 */
@Composable
private fun ThemeModeRailSegment(
    currentMode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        Triple("system", "跟随系统", Icons.Outlined.BrightnessAuto),
        Triple("light", "浅色", Icons.Outlined.LightMode),
        Triple("dark", "深色", Icons.Outlined.DarkMode),
    )
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEach { (modeKey, title, icon) ->
                val isSelected = currentMode == modeKey
                val interactionSource = remember { MutableInteractionSource() }

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "theme_mode_container",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "theme_mode_content",
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = containerColor,
                    shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                    border = if (isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .bounceable(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            if (!isSelected) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onModeChange(modeKey)
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 预置微胶囊色板网格布局（双列平铺）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteGrid(
    currentPalette: AppPalette,
    isDark: Boolean,
    disabled: Boolean,
    onSelect: (AppPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitleMap = mapOf(
        AppPalette.PAPER_INK to "宣纸质感 · 沉着墨韵",
        AppPalette.CLEAR_BLUE to "晴空明净 · 澄澈现代",
        AppPalette.SOFT_MIST to "云水苍茫 · 烟青素雅",
        AppPalette.WARM_APRICOT to "暖杏温润 · 柔和护目",
        AppPalette.DUSK_PLUM to "黛紫檀香 · 静谧深沉",
        AppPalette.PINE_MIST to "松针凝露 · 碧玉清心",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val palettes = AppPalette.entries
        for (i in palettes.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val left = palettes[i]
                PaletteCapsuleCard(
                    palette = left,
                    subtitle = subtitleMap[left] ?: "",
                    isSelected = currentPalette == left,
                    isDark = isDark,
                    disabled = disabled,
                    onClick = { onSelect(left) },
                    modifier = Modifier.weight(1f),
                )
                if (i + 1 < palettes.size) {
                    val right = palettes[i + 1]
                    PaletteCapsuleCard(
                        palette = right,
                        subtitle = subtitleMap[right] ?: "",
                        isSelected = currentPalette == right,
                        isDark = isDark,
                        disabled = disabled,
                        onClick = { onSelect(right) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 单个圆润微胶囊色板。
 * 选中态带有双层高光微光环与发丝描边。
 */
@Composable
private fun PaletteCapsuleCard(
    palette: AppPalette,
    subtitle: String,
    isSelected: Boolean,
    isDark: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val interactionSource = remember { MutableInteractionSource() }
    val capsuleShape = RoundedCornerShape(14.dp)
    val accentColor = if (isDark) palette.accentDark else palette.accentLight

    val containerColor = if (isSelected) {
        accentColor.copy(alpha = if (isDark) 0.16f else 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .then(if (disabled) Modifier.alpha(0.42f) else Modifier)
            // 外层柔光微光环（双层描边外层）
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.55f),
                                accentColor.copy(alpha = 0.15f),
                            ),
                        ),
                        shape = capsuleShape,
                    )
                } else {
                    Modifier
                },
            )
            .padding(if (isSelected) 1.5.dp else 0.dp),
    ) {
        Surface(
            shape = capsuleShape,
            color = containerColor,
            // 内层发丝描边（双层描边内层）
            border = BorderStroke(
                width = if (isSelected) 1.dp else 0.6.dp,
                color = if (isSelected) {
                    accentColor.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .bounceable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !disabled,
                ) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 双色微彩同心微底座
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(palette.accentLight, palette.accentDark),
                            ),
                        )
                        .border(
                            0.8.dp,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = palette.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
