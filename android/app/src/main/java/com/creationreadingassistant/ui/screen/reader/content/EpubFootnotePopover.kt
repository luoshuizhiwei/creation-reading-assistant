package com.creationreadingassistant.ui.screen.reader.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

/**
 * EPUB 内联注脚气泡卡片：
 * 读者轻触正文中的注脚引用（如 [1]、[注1]、① 等）时唤起微卡片，
 * 在原地阅览注释内容，无需跳转到章末再翻回，极大保护阅读沉浸感。
 */
@Composable
fun EpubFootnoteDialog(
    footnoteLabel: String,
    footnoteContent: String,
    paperPalette: ReaderPaperPalette? = null,
    onDismiss: () -> Unit,
) {
    val bgColor = paperPalette?.panel ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val fgColor = paperPalette?.fg ?: MaterialTheme.colorScheme.onSurface
    val accentColor = paperPalette?.accent ?: MaterialTheme.colorScheme.primary
    val mutedColor = paperPalette?.fgMuted ?: MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bgColor,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = "注脚说明 $footnoteLabel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = fgColor,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = mutedColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // 注释正文内容
                Text(
                    text = footnoteContent.ifBlank { "该注脚引文标注于当前章节。" },
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp,
                    color = fgColor,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                Spacer(Modifier.height(4.dp))

                // 底部确认关闭微按钮
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = paperPalette?.onAccent ?: MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("知道了", fontSize = 13.sp)
                }
            }
        }
    }
}
