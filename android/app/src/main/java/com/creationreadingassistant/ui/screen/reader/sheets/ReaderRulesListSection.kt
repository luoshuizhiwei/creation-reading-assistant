package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.CorrectionRecord
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.feature.reader.rules.TocRule
import com.creationreadingassistant.ui.components.AppAlertDialog
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
import com.creationreadingassistant.ui.screen.reader.replacementRulesTabBodyNotice
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape

@Composable
internal fun RuleScopeBadge(scope: RuleScope) {
    val (bg, fg, label) = when (scope) {
        RuleScope.PER_BOOK -> Triple(
            Color(0xFF2E7D32).copy(alpha = 0.12f),
            Color(0xFF2E7D32),
            "本书",
        )
        RuleScope.GLOBAL -> Triple(
            Color(0xFF6A1B9A).copy(alpha = 0.12f),
            Color(0xFF6A1B9A),
            "全局",
        )
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun BuiltinBadge(isStandard: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
    ) {
        Text(
            text = if (isStandard) "内置·标准" else "内置",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun CustomBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Text(
            text = "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun RuleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val spec = LocalComponentSpec.current
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bg = when {
        !enabled -> Color.Transparent
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    }
    Surface(
        shape = RoundedCornerShape(spec.pedestalRadius),
        color = bg,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(32.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun ReplacementCapabilityNotice(
    title: String,
    message: String,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = layout.microGap),
            )
        }
    }
}

@Composable
internal fun ColumnScope.RuleEmptyState(title: String, body: String) {
    FullEmptyState(
        modifier = Modifier.fillMaxWidth().weight(1f),
        icon = { LineArtBook(sizeDp = 64.dp) },
        title = title,
        body = body,
    )
}

@Composable
internal fun MutationFeedbackRow(
    result: RuleMutationResult,
    capability: ReaderReplacementCapability? = null,
) {
    val isError = result is RuleMutationResult.Rejected ||
        result is RuleMutationResult.NotFound ||
        result is RuleMutationResult.NotAnchorable
    Text(
        text = result.feedbackText(capability),
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun CustomRuleRow(
    name: String,
    scope: RuleScope,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val spec = LocalComponentSpec.current

    // R3-P1：删除规则不可逆（规则里往往写着长正则与替换文本，重新敲一遍代价极高），
    // 所以先把「删除」变成一次请求，用户确认后才真正下发 [RuleCommand.DeleteCustom]。
    // 只加一道确认，命令通道与回调签名保持不变。
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete) {
        DeleteRuleConfirmDialog(
            name = name,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(spec.pedestalRadius))
                        .background(Color(0xFF6750A4).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF6750A4),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CustomBadge()
                        RuleScopeBadge(scope = scope)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleActionButton(
                    icon = Icons.Outlined.ArrowUpward,
                    contentDescription = "上移",
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                )
                RuleActionButton(
                    icon = Icons.Outlined.ArrowDownward,
                    contentDescription = "下移",
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                )
                RuleActionButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    onClick = onEdit,
                )
                RuleActionButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    onClick = { confirmingDelete = true },
                    isDestructive = true,
                )
            }
        }
    }
}

/**
 * 删除自定义规则的二次确认。
 *
 * 措辞必须说清两件事：删的是哪一条、删了不能撤销。只写「确定删除吗？」会让用户
 * 在列表里滚动过好几条规则之后分不清自己点的是哪一条。
 */
@Composable
private fun DeleteRuleConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "删除规则？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "「${name.ifBlank { "未命名规则" }}」将被删除，且无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun BuiltinRuleRow(rule: TocRule, onToggle: ((Boolean) -> Unit)?) {
    val isStandard = rule.id == BuiltinTocRules.STANDARD_ID
    val spec = LocalComponentSpec.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(spec.pedestalRadius))
                    .background(Color(0xFF00796B).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF00796B),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BuiltinBadge(isStandard = isStandard)
                    RuleScopeBadge(scope = rule.scope)
                }
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                enabled = onToggle != null,
            )
        }
    }
}

@Composable
internal fun CorrectionRow(
    record: CorrectionRecord,
    onToggle: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader-correction-row-${record.id}"),
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(spec.pedestalRadius))
                    .background(Color(0xFF2E7D32).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF2E7D32),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = record.replaceText.ifEmpty { "（删除）" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "原文：" + record.findText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (record.undone) "已撤销 · 正文保留原文" else "生效中",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.undone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color(0xFF2E7D32)
                    },
                )
            }
            RuleActionButton(
                icon = if (record.undone) {
                    Icons.AutoMirrored.Outlined.Redo
                } else {
                    Icons.AutoMirrored.Outlined.Undo
                },
                contentDescription = if (record.undone) "恢复纠错" else "撤销纠错",
                onClick = onToggle,
                isDestructive = false,
            )
        }
    }
}

@Composable
internal fun ColumnScope.TocRulesList(
    rules: List<TocRule>,
    onCommand: (RuleCommand) -> Unit,
    onEdit: (TocRule) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    if (rules.isEmpty()) {
        RuleEmptyState(title = "暂无目录规则", body = "点击「新增规则」添加自定义目录识别规则。")
        return
    }
    val customIds = remember(rules) { rules.filter { !it.builtin }.map { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = layout.pageHorizontal, vertical = layout.microGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (customIds.isEmpty()) {
            item("custom_hint") {
                Surface(
                    shape = RoundedCornerShape(spec.hintRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = layout.microGap),
                ) {
                    Text(
                        text = "暂无自定义目录规则。内置规则不可编辑，点击「新增规则」添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        items(rules, key = { it.id }) { rule ->
            val index = customIds.indexOf(rule.id)
            when {
                rule.builtin && rule.id == BuiltinTocRules.STANDARD_ID ->
                    BuiltinRuleRow(rule = rule, onToggle = null)
                rule.builtin ->
                    BuiltinRuleRow(
                        rule = rule,
                        onToggle = { enabled -> onCommand(RuleCommand.ToggleBuiltinToc(rule.id, enabled)) },
                    )
                else ->
                    CustomRuleRow(
                        name = rule.name,
                        scope = rule.scope,
                        enabled = rule.enabled,
                        canMoveUp = index > 0,
                        canMoveDown = index >= 0 && index < customIds.lastIndex,
                        onToggle = { enabled -> onCommand(RuleCommand.ToggleCustom(rule.id, enabled)) },
                        onEdit = { onEdit(rule) },
                        onDelete = { onCommand(RuleCommand.DeleteCustom(rule.id)) },
                        onMoveUp = { reorderCommand(RuleKind.TOC, customIds, index, -1)?.let(onCommand) },
                        onMoveDown = { reorderCommand(RuleKind.TOC, customIds, index, 1)?.let(onCommand) },
                    )
            }
        }
    }
}

@Composable
internal fun ColumnScope.ReplaceRulesList(
    rules: List<ReplaceRule>,
    corrections: List<CorrectionRecord>,
    onCommand: (RuleCommand) -> Unit,
    onEdit: (ReplaceRule) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    if (rules.isEmpty() && corrections.isEmpty()) {
        RuleEmptyState(
            title = "暂无替换净化规则",
            body = "点击「新增规则」创建替换净化规则，或在正文中选中文字保存单处纠错。",
        )
        return
    }
    val customIds = remember(rules) { rules.map { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = layout.pageHorizontal, vertical = layout.microGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rules, key = { "rule:" + it.id }) { rule ->
            val index = customIds.indexOf(rule.id)
            CustomRuleRow(
                name = rule.name,
                scope = rule.scope,
                enabled = rule.enabled,
                canMoveUp = index > 0,
                canMoveDown = index < customIds.lastIndex,
                onToggle = { enabled -> onCommand(RuleCommand.ToggleCustom(rule.id, enabled)) },
                onEdit = { onEdit(rule) },
                onDelete = { onCommand(RuleCommand.DeleteCustom(rule.id)) },
                onMoveUp = { reorderCommand(RuleKind.REPLACE, customIds, index, -1)?.let(onCommand) },
                onMoveDown = { reorderCommand(RuleKind.REPLACE, customIds, index, 1)?.let(onCommand) },
            )
        }
        if (corrections.isNotEmpty()) {
            item("corrections_header") {
                Text(
                    text = "单处纠错（只作用于选中位置）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(corrections, key = { "correction:" + it.id }) { record ->
                CorrectionRow(
                    record = record,
                    onToggle = {
                        if (record.undone) {
                            onCommand(RuleCommand.RestoreCorrection(record.id))
                        } else {
                            onCommand(RuleCommand.UndoCorrection(record.id))
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun RulesListContent(
    snapshot: RuleSnapshot,
    tab: RuleKind,
    replacementAvailable: Boolean,
    replacementCapability: ReaderReplacementCapability,
    mutationResult: RuleMutationResult?,
    onTabChange: (RuleKind) -> Unit,
    onCommand: (RuleCommand) -> Unit,
    onEditToc: (TocRule) -> Unit,
    onEditReplace: (ReplaceRule) -> Unit,
    onAdd: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    Column(Modifier.fillMaxWidth()) {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectablePill(
                    selected = tab == RuleKind.TOC,
                    text = "目录规则",
                    onClick = { onTabChange(RuleKind.TOC) },
                    modifier = Modifier.weight(1f),
                )
                if (replacementAvailable) {
                    SelectablePill(
                        selected = tab == RuleKind.REPLACE,
                        text = "替换净化",
                        onClick = { onTabChange(RuleKind.REPLACE) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (replacementCapability is ReaderReplacementCapability.Unavailable) {
            ReplacementCapabilityNotice(
                title = "替换净化当前不可用",
                message = replacementCapability.message,
            )
        }
        mutationResult?.let { MutationFeedbackRow(it, replacementCapability) }
        when (tab) {
            RuleKind.TOC -> TocRulesList(
                rules = snapshot.tocRules,
                onCommand = onCommand,
                onEdit = onEditToc,
            )
            RuleKind.REPLACE -> {
                replacementRulesTabBodyNotice(replacementCapability)
                    ?.let { notice ->
                        ReplacementCapabilityNotice(
                            title = "替换净化状态说明",
                            message = notice,
                        )
                    }
                ReplaceRulesList(
                    rules = snapshot.replaceRules,
                    corrections = snapshot.corrections,
                    onCommand = onCommand,
                    onEdit = onEditReplace,
                )
            }
        }
        Button(
            onClick = onAdd,
            shape = PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增规则", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("新增规则")
        }
    }
}
