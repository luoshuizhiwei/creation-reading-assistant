package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

/**
 * 现代化墨青微岛卡片：目录与净化规则管理入口。
 */
@Composable
internal fun TocRulesEntryRow(
    onClick: () -> Unit,
    title: String = "目录与净化规则",
    subtitle: String = "管理目录智能识别与正则净化",
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .bounceable(interaction)
            .semantics { contentDescription = "管理目录与净化规则" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 墨青微图标底座（微岛收敛：统一走 IconPedestal / pedestalRadius）
                IconPedestal(
                    icon = Icons.Outlined.Tune,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 28.dp,
                    iconSize = 16.dp,
                    contentDescription = "目录与净化规则",
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "进入规则管理",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 目录识别快捷区。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TocRecognitionSection(
    txtRules: List<TxtChapterDetector.Rule>,
    selectedTxtRule: String,
    txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    txtRuleScanStatus: TxtRuleScanStatus?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTxtRule: (String) -> Unit,
    onCancelTxtScan: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(spec.hintRadius))
                .clickable {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onToggleExpanded()
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("目录识别", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("仅在目录不准确时调整", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "收起目录识别" else "展开目录识别",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (txtRuleScanStatus as? TxtRuleScanStatus.Running)?.let { running ->
            TxtScanProgressRow(status = running, onCancel = onCancelTxtScan)
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                txtRules.forEach { rule ->
                    val preview = txtRulePreviews[rule.id].orEmpty()
                    val suffix = txtRulePillSuffix(txtRuleScanStatus, rule.id, preview.size)
                    val label = if (suffix == null) rule.label else "${rule.label} · $suffix"
                    OptionPill(
                        selected = selectedTxtRule == rule.id,
                        label = label,
                        onClick = { onTxtRule(rule.id) },
                    )
                }
            }
        }
    }
}

/** 扫描中的进度 + 取消行。 */
@Composable
internal fun TxtScanProgressRow(
    status: TxtRuleScanStatus.Running,
    onCancel: () -> Unit,
) {
    val percent = status.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "正在扫描目录，$percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val progress = status.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.weight(1f))
        }
        Text(
            text = "扫描中…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) {
            Text("取消")
        }
    }
}

/**
 * 目录章节阅读清单：章节顺序、标题和字数构成稳定三列；当前章以书签色竖线和浅底强调。
 * 未读行保持透明，避免“每章一张卡片”的视觉噪音。
 */
@Composable
internal fun TocRow(entry: ReaderTocEntry, onPick: () -> Unit) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    val spec = LocalComponentSpec.current

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onPick()
        },
        shape = RoundedCornerShape(spec.listItemRadius),
        color = when {
            entry.isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            else -> Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 当前章书签线：全表只在一个位置使用强调色，形成纸质目录的定位锚点。
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(
                        if (entry.isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                        PillShape,
                    ),
            )

            Spacer(Modifier.width(9.dp))

            Text(
                text = (entry.index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (entry.isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                },
                modifier = Modifier.width(34.dp),
            )

            // 章节标题
            Text(
                text = entry.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    entry.isCurrent -> MaterialTheme.colorScheme.primary
                    entry.isRead -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(72.dp),
            ) {
                entry.wordCountLabel?.takeIf { it.isNotBlank() }?.let { wordCount ->
                    Text(
                        text = wordCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                when {
                    entry.isCurrent -> Text(
                        text = "阅读中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { contentDescription = "当前章节" },
                    )
                    entry.isRead -> Text(
                        text = "已读",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics { contentDescription = "已读章节" },
                    )
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
        thickness = spec.hairlineBorderWidth,
    )
}
