package com.creationreadingassistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.pager.CustomTipPlaceholder

/**
 * 自定义页眉/页脚模板编辑弹窗（对标 Legado MD3 的 CustomTipDialog）。
 *
 * 用户在输入框中编辑模板，点击下方 Chip 可在当前光标位置插入预定义占位符。
 * 实时校验 `{Xxx}` 语法；未知占位符时禁用确定按钮并标红提示。
 */
@Composable
fun CustomTipDialog(
    show: Boolean,
    title: String,
    initialTemplate: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    var template by remember(show, initialTemplate) {
        mutableStateOf(TextFieldValue(initialTemplate, TextRange(initialTemplate.length)))
    }

    val placeholders = remember { CustomTipPlaceholder.entries.toList() }
    val isValid by remember(template) {
        derivedStateOf { CustomTipPlaceholder.isValid(template.text) }
    }

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    placeholder = {
                        Text(
                            "如：{ChapterTitle} 或 {PageIndex}/{PageSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    isError = !isValid && template.text.isNotEmpty(),
                    supportingText = {
                        if (!isValid && template.text.isNotEmpty()) {
                            Text(
                                text = "存在无法识别的占位符，请检查拼写",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "点击下方标签可在光标位置快速插入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                val previewText by remember(template.text) {
                    derivedStateOf {
                        if (template.text.isBlank()) "（留空不显示）"
                        else CustomTipPlaceholder.renderSample(template.text)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "效果预览：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "可用变量",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(placeholders, key = { it.token }) { ph ->
                        AssistChip(
                            label = {
                                Text(
                                    ph.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                )
                            },
                            onClick = {
                                template = template.insertAtCursor(ph.token)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onConfirm(template.text)
                        onDismissRequest()
                    }
                },
                enabled = isValid,
            ) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        },
    )
}

/**
 * 在 TextFieldValue 当前光标位置插入 token，并把光标移到插入文本末尾。
 */
private fun TextFieldValue.insertAtCursor(token: String): TextFieldValue {
    val cursor = selection.end.coerceIn(0, text.length)
    val newText = text.substring(0, cursor) + token + text.substring(cursor)
    val newCursor = cursor + token.length
    return TextFieldValue(newText, TextRange(newCursor))
}

/**
 * 紧凑型页眉/页脚自定义提示配置行。
 */
@Composable
fun HeaderFooterPicker(
    label: String,
    template: String,
    onTemplateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    // 折叠行给人看的是中文（章节名 · 当前时间），不是 {ChapterTitle} 这种
                    // 原始 token；token 只在点开后的输入框里出现，那是用户要编辑的东西。
                    text = CustomTipPlaceholder.describeTemplate(template).ifBlank { "无" },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }

    if (showDialog) {
        CustomTipDialog(
            show = showDialog,
            title = "自定义$label",
            initialTemplate = template,
            onConfirm = { newTemplate ->
                onTemplateChange(newTemplate)
            },
            onDismissRequest = { showDialog = false },
        )
    }
}

