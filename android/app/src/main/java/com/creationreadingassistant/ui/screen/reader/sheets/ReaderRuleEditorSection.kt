package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.TocRule
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape

@Composable
internal fun PreviewHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun CorrectionTargetSelector(
    draft: RuleEditorDraft,
    allowCorrection: Boolean,
    onDraftChange: (RuleEditorDraft) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    Text(
        text = "替换目标",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = layout.relatedGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (allowCorrection) {
                SelectablePill(
                    selected = draft.saveAsCorrection,
                    text = "单处纠错",
                    onClick = { onDraftChange(draft.copy(saveAsCorrection = true)) },
                    modifier = Modifier.weight(1f).testTag("reader-rule-target-correction"),
                )
            }
            SelectablePill(
                selected = !draft.saveAsCorrection && draft.scope == RuleScope.PER_BOOK,
                text = "本书替换",
                onClick = { onDraftChange(draft.copy(saveAsCorrection = false, scope = RuleScope.PER_BOOK)) },
                modifier = Modifier.weight(1f),
            )
            SelectablePill(
                selected = !draft.saveAsCorrection && draft.scope == RuleScope.GLOBAL,
                text = "全局规则",
                onClick = { onDraftChange(draft.copy(saveAsCorrection = false, scope = RuleScope.GLOBAL)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (!allowCorrection) {
        Text(
            text = "单处纠错需要在正文中先选中文字；当前为手动新增，仅可保存为替换规则。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = layout.microGap),
        )
    } else if (draft.saveAsCorrection) {
        Text(
            text = "只替换本次选中的这一处文字，锚定其原文位置；撤销后正文立即还原。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = layout.microGap),
        )
    }
}

@Composable
internal fun EditorPreview(
    draft: RuleEditorDraft,
    previewText: String,
    check: RuleDraftCheck,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = layout.contentGap),
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "预览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (check is RuleDraftCheck.Valid) {
                    val count = when (val preview = check.preview) {
                        is RuleDraftPreview.Toc -> preview.result.chapterCount
                        is RuleDraftPreview.Replace -> preview.result.hitCount
                    }
                    val countUnit = if (draft.kind == RuleKind.TOC) "章" else "处"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "命中 $count $countUnit",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                draft.pattern.isBlank() ->
                    PreviewHint("请输入正则表达式后查看预览")
                check is RuleDraftCheck.Invalid ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        check.errors.forEach { error ->
                            Surface(
                                shape = RoundedCornerShape(spec.pedestalRadius),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = error.userMessage(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                previewText.isBlank() ->
                    PreviewHint("暂无预览文本，保存后可在阅读中生效。")
                else ->
                    when (val preview = (check as RuleDraftCheck.Valid).preview) {
                        is RuleDraftPreview.Toc -> {
                            if (preview.result.sampleTitles.isEmpty()) {
                                PreviewHint("未匹配到章节标题")
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    preview.result.sampleTitles.forEach { title ->
                                        Surface(
                                            shape = RoundedCornerShape(spec.pedestalRadius),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is RuleDraftPreview.Replace -> {
                            val excerpts = remember(draft, preview.result) {
                                replaceExcerpts(
                                    preview.result.before,
                                    preview.result.after,
                                    if (draft.saveAsCorrection) draft.simpleFind else draft.resolvedPattern(),
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(spec.pedestalRadius),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                    border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "前：",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                        Text(
                                            text = excerpts.before,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                            ),
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(spec.pedestalRadius),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "后：",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = excerpts.after,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                            ),
                                            modifier = Modifier.padding(top = 2.dp),
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

@Composable
internal fun RuleEditor(
    draft: RuleEditorDraft,
    onDraftChange: (RuleEditorDraft) -> Unit,
    previewText: String,
    effectiveToc: List<TocRule>,
    effectiveReplace: List<ReplaceRule>,
    mutationResult: RuleMutationResult?,
    replacementCapability: ReaderReplacementCapability,
    allowCorrection: Boolean,
    onSave: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    val check = remember(draft, previewText, effectiveToc, effectiveReplace) {
        evaluateDraft(draft, effectiveToc, effectiveReplace, previewText)
    }
    val saveEnabled = if (draft.kind == RuleKind.REPLACE && draft.saveAsCorrection) {
        draft.simpleFind.isNotBlank()
    } else {
        draft.resolvedPattern().isNotBlank() && check is RuleDraftCheck.Valid
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
    ) {
        if (draft.kind == RuleKind.REPLACE) {
            CorrectionTargetSelector(
                draft = draft,
                allowCorrection = allowCorrection,
                onDraftChange = onDraftChange,
            )
        }
        if (!draft.saveAsCorrection) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text("规则名称") },
                supportingText = { Text("留空时以正则表达式命名") },
                singleLine = true,
                shape = RoundedCornerShape(spec.islandRadius),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (draft.kind == RuleKind.REPLACE && !draft.saveAsCorrection) {
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = layout.relatedGap),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectablePill(
                        selected = !draft.advanced,
                        text = "普通模式（精确文字）",
                        onClick = {
                            onDraftChange(
                                draft.copy(
                                    advanced = false,
                                    simpleFind = unescapeRegexEscapeOrNull(draft.pattern).orEmpty(),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SelectablePill(
                        selected = draft.advanced,
                        text = "高级（正则）",
                        onClick = { onDraftChange(draft.copy(advanced = true)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (draft.kind == RuleKind.REPLACE && !draft.advanced) {
            OutlinedTextField(
                value = draft.simpleFind,
                onValueChange = { onDraftChange(draft.withSimpleFind(it)) },
                label = { Text("查找文本") },
                supportingText = { Text("按原文精确匹配（区分大小写），无需正则语法") },
                shape = RoundedCornerShape(spec.islandRadius),
                modifier = Modifier.fillMaxWidth().padding(top = layout.relatedGap),
            )
        } else {
            OutlinedTextField(
                value = draft.pattern,
                onValueChange = { onDraftChange(draft.copy(pattern = it)) },
                label = { Text(if (draft.kind == RuleKind.TOC) "章节模式正则" else "正则表达式") },
                supportingText = { Text("示例：第[0-9]+章") },
                shape = RoundedCornerShape(spec.islandRadius),
                modifier = Modifier.fillMaxWidth().padding(top = layout.relatedGap),
            )
        }
        if (draft.kind == RuleKind.REPLACE) {
            OutlinedTextField(
                value = draft.replacement,
                onValueChange = { onDraftChange(draft.copy(replacement = it)) },
                label = { Text(if (draft.saveAsCorrection) "替换为" else "替换文本") },
                supportingText = { Text("留空表示删除命中文本") },
                shape = RoundedCornerShape(spec.islandRadius),
                modifier = Modifier.fillMaxWidth().padding(top = layout.relatedGap),
            )
            if (!draft.saveAsCorrection && !draft.advanced) {
                Text(
                    text = "普通模式对全文所有相同文字生效；只改选中这一处请选择上方「单处纠错」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = layout.relatedGap),
                )
            }
            if (draft.advanced && !draft.saveAsCorrection) {
                Text(
                    text = "规则区分大小写（正则默认），英文请按原文大小写填写；" +
                        "全文搜索的命中词按小写归一，故搜索词面与规则写法不一致时可能显示「命中 0 处」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = layout.relatedGap),
                )
            }
        }

        if (draft.kind == RuleKind.REPLACE && !draft.saveAsCorrection) {
            Text(
                text = "作用域",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = layout.contentGap, bottom = layout.relatedGap),
            )
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectablePill(
                        selected = draft.scope == RuleScope.PER_BOOK,
                        text = "本书",
                        onClick = { onDraftChange(draft.copy(scope = RuleScope.PER_BOOK)) },
                        modifier = Modifier.weight(1f),
                    )
                    SelectablePill(
                        selected = draft.scope == RuleScope.GLOBAL,
                        text = "全局",
                        onClick = { onDraftChange(draft.copy(scope = RuleScope.GLOBAL)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        mutationResult?.let { MutationFeedbackRow(it, replacementCapability) }

        EditorPreview(draft = draft, previewText = previewText, check = check)

        Button(
            onClick = onSave,
            enabled = saveEnabled,
            shape = PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = layout.contentGap),
        ) {
            Text(
                when {
                    draft.saveAsCorrection -> "保存单处纠错"
                    draft.id == null -> "保存新增"
                    else -> "保存修改"
                }
            )
        }
    }
}
