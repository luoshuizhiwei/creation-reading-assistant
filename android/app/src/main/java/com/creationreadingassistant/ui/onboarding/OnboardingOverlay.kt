package com.creationreadingassistant.ui.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private const val PREFS = "app_prefs"
private const val KEY_ONBOARDED = "onboarded_v1"

fun isOnboardingCompleted(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

private fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
}

/**
 * 首次向导步骤数据载体。
 *
 * 对齐东方纸墨设计美学，承载精雕细琢的主副标题、分色微彩底座、特性标签与图标。
 */
private data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val desc: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badge: String,
    val features: List<String>,
)

private val STEPS = listOf(
    // Step 0：本地优先与全格式阅读
    OnboardingStep(
        title = "本地优先 · 全格式阅读",
        subtitle = "TXT · EPUB · Markdown 离线自由解析",
        desc = "深度支持主流电子书格式，智能编码识别与逐页精准排版。数据与阅读痕迹永驻本地沙箱，私密安全无拘束。",
        icon = Icons.Outlined.Book,
        accentColor = Color(0xFF0284C7), // 淡蓝 / 天青微彩
        badge = "自由阅读",
        features = listOf("纯本地存储", "EPUB/TXT/MD", "编码自适应"),
    ),
    // Step 1：极致东方排版与护眼
    OnboardingStep(
        title = "极致东方排版 · 温润护眼",
        subtitle = "自研逐页引擎 · 精调字偶距 · 墨水屏质感",
        desc = "专为深度阅读研制东方纸墨排版算法，字间距与呼吸感自如随心。配备纸墨温润底色与墨水屏高对比模式，久读目不眩。",
        icon = Icons.Outlined.AutoAwesome,
        accentColor = Color(0xFFD97706), // 暖金 / 琥珀微彩
        badge = "纸墨排版",
        features = listOf("精细字偶距", "纸墨多档色温", "墨水屏质感"),
    ),
    // Step 2：AI 伴读与灵感沉淀
    OnboardingStep(
        title = "AI 伴读 · 灵感沉淀",
        subtitle = "划线秒级摘录 · 上下文深度探问 · 云同步",
        desc = "阅读中划线秒级沉淀为灵感卡片，随书唤起上下文深度问答探析。支持多端无缝互联与 WebDAV 自动备份，思考随心绽放。",
        icon = Icons.Outlined.Lightbulb,
        accentColor = Color(0xFF059669), // 翡翠绿微彩
        badge = "智能伴读",
        features = listOf("秒级灵感摘录", "上下文深度问答", "跨端云同步"),
    ),
)

/**
 * 首次引导浮层（东方纸墨悬浮微岛版 Floating Glass Micro-Island）。
 *
 * 核心升级点：
 * 1. 整体背景：东方纸墨柔和微渐变与漫反射发丝遮罩，既通透沉浸又防止误触底层。
 * 2. 居中引导主卡片：东方纸墨悬浮微岛，具备 24dp 典雅圆角、双层高光边框（0.6dp 发丝描边 + 顶部微高光反光层）、柔和多层立体阴影、surface 95% 半透背景。
 * 3. 3 步向导视觉重塑：44dp 独立分色微彩圆角底座（天青 / 暖金 / 翡翠绿）、排版精致的主副标题、正文与特性微胶囊。
 * 4. 步进指示器：现代化 Spring 物理弹性微指示导轨，当前项弹性拉伸为 24dp 圆润微胶囊，非激活项为 6dp 微点。
 * 5. 底部控制操作栏：左侧「跳过」轻量微胶囊按钮（带触感反馈），右侧「下一步」/「立即开启」全宽高质感微岛主按钮（高光微发丝与 Spring 按压微缩放动效）。
 */
@Composable
fun OnboardingOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val spec = LocalComponentSpec.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val isDark = isSystemInDarkTheme()

    var step by remember { mutableIntStateOf(0) }
    val current = STEPS[step]
    val isLast = step >= STEPS.lastIndex

    // 整体背景：东方纸墨微渐变与发丝遮罩，通透沉浸，拦截底层手势穿透
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 消费外部点击，防止穿透 */ }
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f),
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 纸墨微光漫反射发丝遮罩层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        radius = 850f,
                    ),
                ),
        )

        // 居中引导主卡片：东方纸墨悬浮微岛（Floating Glass Micro-Island）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .animateEnter(reducedMotion = reducedMotion)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                ),
        ) {
            // 纸墨半透主表面
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                tonalElevation = 2.dp,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    // 双层高光边框之二：顶部微高光反光层（垂直渐变细光带）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = if (isDark) 0.18f else 0.45f),
                                        0.25f to Color.White.copy(alpha = if (isDark) 0.05f else 0.10f),
                                        1.0f to Color.Transparent,
                                    ),
                                ),
                            ),
                    )

                    // 卡片核心内容容器
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 顶部行：步骤指示徽章 + 右上轻微关闭图标
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 东方纸墨微章
                            Surface(
                                shape = spec.pillShape,
                                color = current.accentColor.copy(alpha = 0.10f),
                                border = BorderStroke(0.6.dp, current.accentColor.copy(alpha = 0.28f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(spec.pillShape)
                                            .background(current.accentColor),
                                    )
                                    Text(
                                        text = "${step + 1} / ${STEPS.size} · ${current.badge}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.4.sp,
                                        ),
                                        color = current.accentColor,
                                    )
                                }
                            }

                            // 右上微关闭按钮
                            val closeInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    markOnboardingComplete(context)
                                    onClose()
                                },
                                shape = spec.pillShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(30.dp)
                                    .bounceable(closeInteraction),
                                interactionSource = closeInteraction,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "跳过并开启",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // 视觉内容展示区：带淡入淡出动效
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    fadeIn(tween(MotionTokens.Fast)) togetherWith fadeOut(tween(MotionTokens.Fast / 2))
                                }
                            },
                            label = "onboarding_step_content",
                            modifier = Modifier.fillMaxWidth(),
                        ) { targetStepIndex ->
                            val stepData = STEPS[targetStepIndex]
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 44dp 独立微彩圆角底座（天青 / 暖金 / 翡翠绿）
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(stepData.accentColor.copy(alpha = 0.12f))
                                        .border(
                                            width = 0.8.dp,
                                            color = stepData.accentColor.copy(alpha = 0.32f),
                                            shape = RoundedCornerShape(14.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = stepData.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = stepData.accentColor,
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // 主标题
                                Text(
                                    text = stepData.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp,
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Spacer(Modifier.height(4.dp))

                                // 副标题
                                Text(
                                    text = stepData.subtitle,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.2.sp,
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = stepData.accentColor,
                                )

                                Spacer(Modifier.height(12.dp))

                                // 正文描述
                                Text(
                                    text = stepData.desc,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 21.sp,
                                        letterSpacing = 0.2.sp,
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )

                                Spacer(Modifier.height(14.dp))

                                // 特性标签微胶囊
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    stepData.features.forEach { feat ->
                                        Surface(
                                            shape = spec.pillShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                        ) {
                                            Text(
                                                text = feat,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // 底部步进指示器：现代化 Spring 物理弹性微指示导轨
                        Surface(
                            shape = spec.pillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                STEPS.indices.forEach { i ->
                                    val isActive = (i == step)
                                    val targetWidth = if (isActive) 24.dp else 6.dp
                                    val animatedWidth by animateDpAsState(
                                        targetValue = targetWidth,
                                        animationSpec = if (reducedMotion) {
                                            snap()
                                        } else {
                                            spring(
                                                dampingRatio = 0.72f,
                                                stiffness = 380f,
                                            )
                                        },
                                        label = "step_indicator_$i",
                                    )

                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .width(animatedWidth)
                                            .clip(spec.pillShape)
                                            .then(
                                                if (isActive) {
                                                    Modifier.background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                                                            ),
                                                        ),
                                                    )
                                                } else {
                                                    Modifier.background(
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                                    )
                                                },
                                            ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        // 底部控制操作栏：左侧「跳过」轻量微胶囊 + 右侧「下一步」/「立即开启」微岛主按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 左侧「跳过」轻量微胶囊按钮（带轻微触感）
                            val skipInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    markOnboardingComplete(context)
                                    onClose()
                                },
                                shape = spec.pillShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                modifier = Modifier.bounceable(skipInteraction),
                                interactionSource = skipInteraction,
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "跳过",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.3.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 右侧「下一步」/「立即开启」全宽高质感微岛主按钮（高光微发丝、Spring 按压微缩放动效）
                            val primaryInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic(if (isLast) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                                    if (isLast) {
                                        markOnboardingComplete(context)
                                        onClose()
                                    } else {
                                        step++
                                    }
                                },
                                shape = spec.pillShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp,
                                border = BorderStroke(
                                    0.6.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.35f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceable(primaryInteraction),
                                interactionSource = primaryInteraction,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 顶部微高光反光发丝
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(spec.pillShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0.0f to Color.White.copy(alpha = 0.22f),
                                                        0.4f to Color.Transparent,
                                                    ),
                                                ),
                                            ),
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = if (isLast) "立即开启" else "下一步",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.5.sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Icon(
                                            imageVector = if (isLast) Icons.Outlined.AutoStories else Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(AppIconSize.Small),
                                            tint = MaterialTheme.colorScheme.onPrimary,
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
}
