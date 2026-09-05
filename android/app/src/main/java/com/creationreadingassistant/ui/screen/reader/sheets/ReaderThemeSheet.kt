package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperOptions
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.paperPalette
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private fun paperToneSubtitle(key: String): String = when (key) {
    "white" -> "清冷墨香 · 纯净白纸"
    "warm" -> "温润米黄 · 羊皮纸感"
    "green" -> "草木柔和 · 护眼青绿"
    "night" -> "暗夜纯粹 · 极黑沉浸"
    "sepia_dark" -> "沉静暮色 · 暖棕护眼"
    "follow" -> "跟随系统 · 智能明暗"
    else -> "经典阅读纸面"
}

private fun paperTonePoeticSample(key: String): Pair<String, String> = when (key) {
    "white" -> "窗前竹影摇阶砌" to "墨染霜绡展素心"
    "warm" -> "灯下清芬翻贝叶" to "旧墨微黄泛古香"
    "green" -> "林泉入座清肌骨" to "山翠迎眸解目疲"
    "night" -> "静夜寒星沉万籁" to "墨玉凝光照字明"
    "sepia_dark" -> "暮色闲披棕椟卷" to "炉香初泛静幽思"
    "follow" -> "天光向晚随时换" to "云影随风任卷舒"
    else -> "天地玄黄生浩气" to "纸墨相传见古今"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSheet(
    background: String,
    appDark: Boolean,
    onBackground: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val currentPalette = paperPalette(background, appDark)
    val isNight = !currentPalette.isLight
    val isFollow = background == "follow"

    ReaderSheetScaffold(
        title = "主题外观",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 动态取色 / 深色跟随精致微胶囊导轨
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 跟随系统微胶囊芯片
                    val followInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (!isFollow) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onBackground("follow")
                            }
                        },
                        shape = PillShape,
                        color = if (isFollow) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isFollow) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .bounceable(followInteraction),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.BrightnessAuto,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isFollow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "跟随系统",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isFollow) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFollow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 日间模式微胶囊芯片
                    val isDayActive = !isNight && !isFollow
                    val dayInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (!isDayActive) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onBackground("white")
                            }
                        },
                        shape = PillShape,
                        color = if (isDayActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isDayActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .bounceable(dayInteraction),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isDayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "日间明朗",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isDayActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 夜读模式微胶囊芯片
                    val isNightActive = isNight && !isFollow
                    val nightInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (!isNightActive) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onBackground("night")
                            }
                        },
                        shape = PillShape,
                        color = if (isNightActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isNightActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .bounceable(nightInteraction),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isNightActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "夜读沉浸",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isNightActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isNightActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 2. 说明信息微岛指示行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "纸色选择作用于阅读正文与底栏，沉浸守护护眼专注度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 3. 色盘圆润微岛卡片网格
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReaderPaperOptions.forEach { option ->
                    val palette = paperPalette(option.key, appDark)
                    val selected = option.key == background
                    val cardInteraction = remember { MutableInteractionSource() }

                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onBackground(option.key)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .bounceable(cardInteraction),
                        shape = RoundedCornerShape(16.dp),
                        color = palette.bg,
                        contentColor = palette.fg,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) palette.accent else palette.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 顶行：标题与双层高光外光环
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFontFamily),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = palette.fg,
                                )

                                // 双层高光外光环与微徽章
                                if (selected) {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // 外层呼吸微光晕
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(palette.accent.copy(alpha = 0.22f), shape = CircleShape),
                                        )
                                        // 内层聚焦圆环与对勾微徽章
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(palette.bg, shape = CircleShape)
                                                .border(1.5.dp, palette.accent, shape = CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = "已选择",
                                                tint = palette.accent,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(palette.panelStrong, shape = CircleShape)
                                            .border(1.dp, palette.outlineVariant.copy(alpha = 0.65f), shape = CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Aa",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.fgMuted,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // 副标题：意境纸墨描述
                            Text(
                                text = paperToneSubtitle(option.key),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.fgMuted,
                                maxLines = 1,
                            )

                            // 样张文字微岛展示
                            val sample = paperTonePoeticSample(option.key)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panel.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = sample.first,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = palette.fg,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = sample.second,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = palette.fgMuted,
                                        maxLines = 1,
                                    )
                                }
                            }

                            // 底行：随纸 5 色微小批注色点预览
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                palette.highlightColors.take(4).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 3.dp)
                                            .size(7.dp)
                                            .background(color, CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
