package com.creationreadingassistant.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.GlassCard
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.LocalVisualStyle
import com.creationreadingassistant.ui.theme.VisualStyle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

private const val PREFS = "app_prefs"
private const val KEY_ONBOARDED = "onboarded_v1"

fun isOnboardingCompleted(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

private fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
}

private data class OnboardingStep(val title: String, val desc: String, val icon: ImageVector)

private val STEPS = listOf(
    OnboardingStep(
        "导入本地书籍",
        "支持 TXT、Markdown、EPUB 三种格式，自动识别编码，导入后离线阅读。",
        Icons.Filled.Book,
    ),
    OnboardingStep(
        "边读边记灵感",
        "阅读时选中文字即可转为灵感、高亮或笔记，灵感支持状态流转和批量整理。",
        Icons.Filled.AutoAwesome,
    ),
    OnboardingStep(
        "与电脑端同步",
        "在「我的」页面连接电脑端，可以同步书籍、进度、灵感和笔记，WebDAV 也支持。",
        Icons.Filled.Sync,
    ),
)

/**
 * 首次引导浮层（对照 web OnboardingOverlay.tsx）。
 * 仅在未完成任务（SharedPreferences 标记）时展示，3 步后写入完成标记。
 */
@Composable
fun OnboardingOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val style = LocalVisualStyle.current
    var step by remember { mutableStateOf(0) }
    val current = STEPS[step]
    val isLast = step >= STEPS.lastIndex
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 底部抽屉面板：消费 LocalComponentSpec。
            // - Apple 下 GlassCard 自动变为毛玻璃近似（半透明表面 + 极细边 + 柔和阴影）
            // - Web 下退化为轻投影 SectionCard（1dp key + 4dp ambient）
            // - 墨韵下退化为扁平 + 1px 发丝线 SectionCard（无投影）
            // 仅上方两角大圆角（抽屉式），用 clip 把 GlassCard 的全圆角收敛为顶部圆角。
            // 内边距补偿 GlassCard 内部 contentPadding（三套主题均为 16dp），保持原 28/24/24/24 的外边距。
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(spec.sheetShape),
            ) {
                Column(
                    Modifier.padding(
                        top = 28.dp - layout.cardPadding,
                        start = 24.dp - layout.cardPadding,
                        end = 24.dp - layout.cardPadding,
                        bottom = 24.dp - layout.cardPadding,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 跳过（右上，透明胶囊，对齐 web .onboarding-skip）
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { markOnboardingComplete(context); onClose() },
                            shape = spec.listItemShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("跳过", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // 视觉区：图标盒（72×72 r20 primary 12%）+ 圆点
                    Box(
                        Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), spec.cardShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(current.icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STEPS.forEachIndexed { i, _ ->
                            Box(
                                Modifier
                                    .size(width = if (i == step) 18.dp else 6.dp, height = 6.dp)
                                    .background(
                                        if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = spec.pillShape,
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        current.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = {
                                if (isLast) { markOnboardingComplete(context); onClose() }
                                else step++
                            },
                            shape = spec.listItemShape,
                            colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isLast) "开始使用" else "下一步", style = MaterialTheme.typography.labelLarge)
                        if (!isLast) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
