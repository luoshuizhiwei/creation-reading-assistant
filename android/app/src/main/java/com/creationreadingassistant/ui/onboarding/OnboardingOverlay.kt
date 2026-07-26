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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var step by remember { mutableStateOf(0) }
    val current = STEPS[step]
    val isLast = step >= STEPS.lastIndex
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 对齐 web .onboarding-panel：底部抽屉，仅上方两角 20dp 圆角，无投影
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(top = 28.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 跳过（右上，透明胶囊，对齐 web .onboarding-skip）
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { markOnboardingComplete(context); onClose() },
                            shape = RoundedCornerShape(999.dp),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
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
                                        shape = RoundedCornerShape(999.dp),
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
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isLast) "开始使用" else "下一步", style = MaterialTheme.typography.labelLarge)
                        if (!isLast) Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
