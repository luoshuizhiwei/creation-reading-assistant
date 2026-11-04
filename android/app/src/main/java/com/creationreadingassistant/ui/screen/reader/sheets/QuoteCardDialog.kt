package com.creationreadingassistant.ui.screen.reader.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.SealMark
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 古典书摘日历卡片质感风格定义。
 */
enum class QuoteCardTheme(
    val label: String,
    val bgColors: List<Color>,
    val borderColor: Color,
    val spineColor: Color,
    val textColor: Color,
    val mutedColor: Color,
    val spineShadowAlpha: Float,
) {
    XUANZHI(
        label = "宣纸",
        bgColors = listOf(Color(0xFFFCF9F2), Color(0xFFF4ECE0)),
        borderColor = Color(0xFFE5DAC8),
        spineColor = Color(0xFF8C5E3C),
        textColor = Color(0xFF231F1D),
        mutedColor = Color(0xFF7A6F68),
        spineShadowAlpha = 0.15f,
    ),
    SUJIAN(
        label = "素笺",
        bgColors = listOf(Color(0xFFF4F8F5), Color(0xFFE5EFE9)),
        borderColor = Color(0xFFD0E1D7),
        spineColor = Color(0xFF386B52),
        textColor = Color(0xFF1B2E24),
        mutedColor = Color(0xFF597568),
        spineShadowAlpha = 0.12f,
    ),
    MOHEI(
        label = "墨黑",
        bgColors = listOf(Color(0xFF28282A), Color(0xFF19191B)),
        borderColor = Color(0xFF3A3A3E),
        spineColor = Color(0xFFD4A373),
        textColor = Color(0xFFEDE9E3),
        mutedColor = Color(0xFF9E9B95),
        spineShadowAlpha = 0.35f,
    ),
}

/**
 * 唯美古典书摘日历卡片生成器与预览弹窗。
 *
 * 视觉语言与核心特性：
 * 1. 支持「宣纸 / 素笺 / 墨黑」三款东方古典风格无缝即时切换；
 * 2. 模拟真书微凹印痕与书脊阴影过渡；
 * 3. 墨黑典雅正文字体与微透明双引号开篇；
 * 4. 右下角朱红小篆「读毕/藏书印」印章钤印；
 * 5. 底部日期与《书名》· 作者题签；
 * 6. 支持一键系统分享（Intent.ACTION_SEND）与剪贴板复制。
 */
@Composable
fun QuoteCardDialog(
    quoteText: String,
    bookTitle: String,
    author: String? = null,
    chapterTitle: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic(rememberReducedMotion())
    val today = LocalDate.now()
    val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))

    var selectedTheme by remember { mutableStateOf(QuoteCardTheme.XUANZHI) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部：风格切换微芯片组
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QuoteCardTheme.entries.forEach { theme ->
                        val isSelected = selectedTheme == theme
                        val interaction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    selectedTheme = theme
                                }
                            },
                            shape = PillShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.bounceable(interaction),
                        ) {
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // 实体书摘卡片主体
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = selectedTheme.bgColors.first(),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, selectedTheme.borderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(selectedTheme.bgColors),
                        ),
                ) {
                    // 左侧书脊微阴影
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(width = 5.dp, height = 360.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = selectedTheme.spineShadowAlpha), Color.Transparent),
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 顶部题眉：书名与关闭按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    tint = selectedTheme.spineColor,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "《${bookTitle.ifBlank { "无题" }}》",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedTheme.textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = DisplayFontFamily,
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    tint = selectedTheme.mutedColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (!author.isNullOrBlank()) {
                            Text(
                                text = "著 · $author",
                                fontSize = 12.sp,
                                color = selectedTheme.mutedColor,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 书摘名句核心展示区：根据字数自适应字号与行距，长文本防溢出滚动
                        val trimmedText = quoteText.trim()
                        val textLength = trimmedText.length
                        val (fontSize, lineHeight, minCardHeight) = when {
                            textLength <= 30 -> Triple(19.sp, 31.sp, 80.dp)
                            textLength <= 80 -> Triple(16.5.sp, 27.sp, 95.dp)
                            textLength <= 150 -> Triple(14.5.sp, 24.sp, 110.dp)
                            else -> Triple(13.sp, 21.sp, 120.dp)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minCardHeight, max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = trimmedText,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = FontWeight.Normal,
                                color = selectedTheme.textColor,
                                textAlign = TextAlign.Justify,
                                fontFamily = DisplayFontFamily,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 底部日期、章节题签与古典藏书印
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (!chapterTitle.isNullOrBlank()) {
                                    Text(
                                        text = chapterTitle,
                                        fontSize = 11.5.sp,
                                        color = selectedTheme.mutedColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "$dateStr · 创作阅读助手",
                                    fontSize = 11.sp,
                                    color = selectedTheme.mutedColor.copy(alpha = 0.8f),
                                )
                            }

                            // 古典朱红藏书印
                            SealMark(
                                size = 32.dp,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            }

            // 底部操作功能按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = ClipData.newPlainText("书摘名句", "“${quoteText.trim()}” ——《$bookTitle》")
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, "书摘已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    shape = PillShape,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制文字", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val shareText = "“${quoteText.trim()}”\n\n——《$bookTitle》${if (!author.isNullOrBlank()) " ($author)" else ""}\n$dateStr · 创作阅读助手"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享精选书摘"))
                    },
                    shape = PillShape,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享书摘", fontSize = 13.sp)
                }
            }
        }
    }
}
