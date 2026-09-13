package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability

/**
 * 目录与净化规则管理 Sheet：双 tab 列表 + 同 Sheet 内编辑器 + 实时预览。
 * 宿主只需提供规则快照、预览文本、最近写入结果与命令回调。
 */
@Composable
internal fun RulesSheet(
    snapshot: RuleSnapshot,
    previewText: String,
    mutationResult: RuleMutationResult?,
    replacementCapability: ReaderReplacementCapability,
    initialReplaceText: String? = null,
    onCommand: (RuleCommand) -> Unit,
    onBack: () -> Unit,
) {
    val replacementAvailable = replacementCapability is ReaderReplacementCapability.Available
    val initialReplaceDraft = remember(initialReplaceText, replacementAvailable) {
        initialReplaceText
            ?.takeIf { replacementAvailable }
            ?.let(::selectionReplaceDraft)
    }
    var tab by remember(initialReplaceText, replacementAvailable) {
        mutableStateOf(if (initialReplaceDraft != null) RuleKind.REPLACE else RuleKind.TOC)
    }
    var editing by remember(initialReplaceText, replacementAvailable) {
        mutableStateOf(initialReplaceDraft)
    }
    var saveEpoch by remember { mutableStateOf(0) }
    val visibleTab = if (replacementAvailable) tab else RuleKind.TOC

    LaunchedEffect(replacementCapability) {
        if (!replacementAvailable) {
            tab = RuleKind.TOC
            if (editing?.kind == RuleKind.REPLACE) {
                editing = null
                saveEpoch = 0
            }
        }
    }

    // 保存成功（Saved）后关闭编辑器回到列表；epoch 防止陈旧 Saved 误关新打开的编辑器。
    LaunchedEffect(mutationResult) {
        if (mutationResult is RuleMutationResult.Saved && saveEpoch > 0) {
            editing = null
            saveEpoch = 0
        }
    }

    val currentDraft = editing
    ReaderSheetScaffold(
        title = when {
            currentDraft != null && currentDraft.saveAsCorrection -> "新增单处纠错"
            currentDraft != null && currentDraft.id == null -> "新增规则"
            currentDraft != null -> "编辑规则"
            else -> "规则管理"
        },
        onBack = {
            if (currentDraft != null) {
                editing = null
                saveEpoch = 0
            } else {
                onBack()
            }
        },
    ) {
        if (currentDraft != null) {
            RuleEditor(
                draft = currentDraft,
                onDraftChange = { editing = it },
                previewText = previewText,
                effectiveToc = snapshot.effectiveToc,
                effectiveReplace = snapshot.effectiveReplace,
                mutationResult = mutationResult,
                replacementCapability = replacementCapability,
                allowCorrection = initialReplaceText != null,
                onSave = {
                    val command = currentDraft.toCommand()
                    if (command != null) {
                        saveEpoch += 1
                        onCommand(command)
                    }
                },
            )
        } else {
            RulesListContent(
                snapshot = snapshot,
                tab = visibleTab,
                replacementAvailable = replacementAvailable,
                replacementCapability = replacementCapability,
                mutationResult = mutationResult,
                onTabChange = { tab = it },
                onCommand = onCommand,
                onEditToc = { editing = tocDraft(it) },
                onEditReplace = { editing = replaceDraft(it) },
                onAdd = {
                    editing = RuleEditorDraft(
                        kind = visibleTab,
                        scope = RuleScope.PER_BOOK,
                    )
                },
            )
        }
    }
}
