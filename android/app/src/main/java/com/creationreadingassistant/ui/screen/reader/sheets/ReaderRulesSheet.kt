package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.ReplacePreviewResult
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleEngine
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.feature.reader.rules.RuleValidationError
import com.creationreadingassistant.feature.reader.rules.TocPreviewResult
import com.creationreadingassistant.feature.reader.rules.TocRule
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability

// ─────────────────────────────────────────────────────────────────────────────
// 纯 JVM 可测的编辑/校验/预览逻辑（TDD seam）
// ─────────────────────────────────────────────────────────────────────────────

/** 编辑器草稿：新增/编辑自定义规则时的表单状态。编辑时 [id] 携带原规则 id。 */
internal data class RuleEditorDraft(
    val kind: RuleKind,
    val id: String? = null,
    val name: String = "",
    val pattern: String = "",
    val replacement: String = "",
    val scope: RuleScope = RuleScope.PER_BOOK,
    val enabled: Boolean = true,
)

internal fun tocDraft(rule: TocRule): RuleEditorDraft = RuleEditorDraft(
    kind = RuleKind.TOC,
    id = rule.id,
    name = rule.name,
    pattern = rule.pattern.orEmpty(),
    scope = rule.scope,
    enabled = rule.enabled,
)

internal fun replaceDraft(rule: ReplaceRule): RuleEditorDraft = RuleEditorDraft(
    kind = RuleKind.REPLACE,
    id = rule.id,
    name = rule.name,
    pattern = rule.pattern,
    replacement = rule.replacement,
    scope = rule.scope,
    enabled = rule.enabled,
)

/**
 * 草稿 → 保存命令：TOC 转 [RuleCommand.SaveCustomToc]，REPLACE 转 [RuleCommand.SaveCustomReplace]；
 * 编辑时携带原 [id]。名称或正则为空时返回 null（UI 应禁用保存）。
 */
internal fun RuleEditorDraft.toCommand(): RuleCommand? {
    if (name.isBlank() || pattern.isBlank()) return null
    return when (kind) {
        RuleKind.TOC -> RuleCommand.SaveCustomToc(
            id = id,
            name = name.trim(),
            pattern = pattern,
            scope = scope,
            enabled = enabled,
        )
        RuleKind.REPLACE -> RuleCommand.SaveCustomReplace(
            id = id,
            name = name.trim(),
            pattern = pattern,
            replacement = replacement,
            scope = scope,
            enabled = enabled,
        )
    }
}

/** 草稿校验/预览结果：校验不通过时只给错误枚举，绝不把未校验模式交给引擎。 */
internal sealed interface RuleDraftCheck {
    data class Valid(val preview: RuleDraftPreview) : RuleDraftCheck
    data class Invalid(val errors: List<RuleValidationError>) : RuleDraftCheck
}

/** 预览结果：TOC 命中数 + 抽样标题；REPLACE 命中数 + 前后全文。 */
internal sealed interface RuleDraftPreview {
    data class Toc(val result: TocPreviewResult) : RuleDraftPreview
    data class Replace(val result: ReplacePreviewResult) : RuleDraftPreview
}

/**
 * 对草稿做保存前校验并生成预览：TOC 用「当前有效 TOC + 草稿」，REPLACE 用「当前有效替换 + 草稿」。
 * 正则为空/无效/可空匹配/灾难性回溯时返回 [RuleDraftCheck.Invalid]，不进入引擎，避免崩溃。
 */
internal fun evaluateDraft(
    draft: RuleEditorDraft,
    effectiveToc: List<TocRule>,
    effectiveReplace: List<ReplaceRule>,
    previewText: String,
): RuleDraftCheck {
    if (draft.pattern.isBlank()) {
        return RuleDraftCheck.Invalid(listOf(RuleValidationError.INVALID_REGEX))
    }
    val validation = when (draft.kind) {
        RuleKind.TOC -> RuleEngine.validateTocRule(draft.pattern)
        RuleKind.REPLACE -> RuleEngine.validateReplaceRule(draft.pattern, draft.replacement)
    }
    if (!validation.valid) return RuleDraftCheck.Invalid(validation.errors)
    return when (draft.kind) {
        RuleKind.TOC -> RuleDraftCheck.Valid(
            RuleDraftPreview.Toc(RuleEngine.previewToc(previewText, effectiveToc + draft.toTocRule())),
        )
        RuleKind.REPLACE -> RuleDraftCheck.Valid(
            RuleDraftPreview.Replace(RuleEngine.previewReplace(previewText, effectiveReplace + draft.toReplaceRule())),
        )
    }
}

internal fun RuleEditorDraft.toTocRule(): TocRule = TocRule(
    id = id ?: DRAFT_TOC_ID,
    name = name.trim(),
    pattern = pattern,
    builtin = false,
    enabled = true,
    scope = scope,
    position = Int.MAX_VALUE,
)

internal fun RuleEditorDraft.toReplaceRule(): ReplaceRule = ReplaceRule(
    id = id ?: DRAFT_REPLACE_ID,
    name = name.trim(),
    pattern = pattern,
    replacement = replacement,
    enabled = true,
    position = Int.MAX_VALUE,
    scope = scope,
)

private const val DRAFT_TOC_ID = "__draft_toc__"
private const val DRAFT_REPLACE_ID = "__draft_replace__"

/** 把自定义规则 id 列表中的 [index] 项移动 [delta] 步；越界返回 null（UI 应禁用按钮）。 */
internal fun moveCustomRule(ids: List<String>, index: Int, delta: Int): List<String>? {
    val target = index + delta
    if (index !in ids.indices || target !in ids.indices) return null
    val result = ids.toMutableList()
    val moved = result.removeAt(index)
    result.add(target, moved)
    return result
}

/** 上移/下移后的 [RuleCommand.ReorderRules]：携带当前书可管理自定义规则全集的新顺序。 */
internal fun reorderCommand(
    kind: RuleKind,
    customIds: List<String>,
    index: Int,
    delta: Int,
): RuleCommand.ReorderRules? = moveCustomRule(customIds, index, delta)?.let {
    RuleCommand.ReorderRules(kind, it)
}

/** 校验错误的中文文案（编辑器预览区与保存反馈共用）。 */
internal fun RuleValidationError.userMessage(): String = when (this) {
    RuleValidationError.INVALID_REGEX -> "正则表达式无效，无法编译"
    RuleValidationError.EMPTY_MATCH -> "正则可能匹配空串，请调整模式"
    RuleValidationError.CATASTROPHIC_RISK -> "正则存在灾难性回溯风险，请简化嵌套量词"
    RuleValidationError.IMMUTABLE_BUILTIN -> "内置规则不可修改"
    RuleValidationError.ID_CONFLICT -> "规则 ID 冲突，无法保存"
}

/** 最近一次 mutation 的简短反馈文案。 */
internal fun RuleMutationResult.feedbackText(): String = when (this) {
    RuleMutationResult.Success -> "操作成功"
    is RuleMutationResult.Saved -> "已保存"
    RuleMutationResult.NotFound -> "规则不存在或不属于当前书"
    is RuleMutationResult.Rejected ->
        if (errors.isEmpty()) "保存失败：规则冲突"
        else "保存失败：" + errors.joinToString("；") { it.userMessage() }
    is RuleMutationResult.Migrated -> "已迁移目录规则"
}

/** 替换预览的前后文本节选：以首个命中位置为中心截取；无命中时取文本开头。 */
internal data class ReplaceExcerpt(val before: String, val after: String)

internal fun replaceExcerpts(
    before: String,
    after: String,
    pattern: String,
    radius: Int = 40,
): ReplaceExcerpt {
    val center = try {
        Regex(pattern).find(before)?.range?.first ?: 0
    } catch (e: Exception) {
        0
    }
    fun excerpt(text: String): String {
        if (text.isEmpty()) return ""
        val start = (center - radius).coerceIn(0, text.length)
        val end = (center + radius).coerceIn(start, text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end) + suffix
    }
    return ReplaceExcerpt(excerpt(before), excerpt(after))
}

internal fun RuleScope.ruleScopeLabel(): String = when (this) {
    RuleScope.PER_BOOK -> "本书"
    RuleScope.GLOBAL -> "全局"
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheet
// ─────────────────────────────────────────────────────────────────────────────

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
    onCommand: (RuleCommand) -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(RuleKind.TOC) }
    var editing by remember { mutableStateOf<RuleEditorDraft?>(null) }
    var saveEpoch by remember { mutableStateOf(0) }
    val replacementAvailable = replacementCapability is ReaderReplacementCapability.Available
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
                onSave = {
                    saveEpoch += 1
                    editing?.toCommand()?.let(onCommand)
                },
            )
        } else {
            RulesList(
                tab = visibleTab,
                onTabChange = { tab = it },
                snapshot = snapshot,
                mutationResult = mutationResult,
                replacementCapability = replacementCapability,
                onCommand = onCommand,
                onAdd = {
                    editing = RuleEditorDraft(kind = visibleTab)
                    saveEpoch = 0
                },
                onEditToc = {
                    editing = tocDraft(it)
                    saveEpoch = 0
                },
                onEditReplace = {
                    editing = replaceDraft(it)
                    saveEpoch = 0
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.RulesList(
    tab: RuleKind,
    onTabChange: (RuleKind) -> Unit,
    snapshot: RuleSnapshot,
    mutationResult: RuleMutationResult?,
    replacementCapability: ReaderReplacementCapability,
    onCommand: (RuleCommand) -> Unit,
    onAdd: () -> Unit,
    onEditToc: (TocRule) -> Unit,
    onEditReplace: (ReplaceRule) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val replacementAvailable = replacementCapability is ReaderReplacementCapability.Available
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
            horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            OptionPill(selected = tab == RuleKind.TOC, label = "目录规则", onClick = { onTabChange(RuleKind.TOC) })
            if (replacementAvailable) {
                OptionPill(selected = tab == RuleKind.REPLACE, label = "替换净化", onClick = { onTabChange(RuleKind.REPLACE) })
            }
        }
        if (replacementCapability is ReaderReplacementCapability.Unavailable) {
            ReplacementUnavailableNotice(replacementCapability.message)
        }
        mutationResult?.let { MutationFeedbackRow(it) }
        when (tab) {
            RuleKind.TOC -> TocRulesList(
                rules = snapshot.tocRules,
                onCommand = onCommand,
                onEdit = onEditToc,
            )
            RuleKind.REPLACE -> ReplaceRulesList(
                rules = snapshot.replaceRules,
                onCommand = onCommand,
                onEdit = onEditReplace,
            )
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增规则", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(layout.relatedGap))
            Text("新增规则")
        }
    }
}

@Composable
private fun ReplacementUnavailableNotice(message: String) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
    ) {
        Text(
            text = "替换净化当前不可用",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = layout.microGap),
        )
    }
}

@Composable
private fun ColumnScope.TocRulesList(
    rules: List<TocRule>,
    onCommand: (RuleCommand) -> Unit,
    onEdit: (TocRule) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    if (rules.isEmpty()) {
        RuleEmptyState(title = "暂无目录规则", body = "点击「新增规则」添加自定义目录识别规则。")
        return
    }
    val customIds = remember(rules) { rules.filter { !it.builtin }.map { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = layout.pageHorizontal, vertical = layout.microGap),
        verticalArrangement = Arrangement.spacedBy(layout.microGap),
    ) {
        if (customIds.isEmpty()) {
            item("custom_hint") {
                Text(
                    text = "暂无自定义目录规则。内置规则不可编辑，点击「新增规则」添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = layout.relatedGap),
                )
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
private fun ColumnScope.ReplaceRulesList(
    rules: List<ReplaceRule>,
    onCommand: (RuleCommand) -> Unit,
    onEdit: (ReplaceRule) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    if (rules.isEmpty()) {
        RuleEmptyState(
            title = "暂无替换净化规则",
            body = "点击「新增规则」创建替换净化规则，把正文中的干扰文本替换或删除。",
        )
        return
    }
    val customIds = remember(rules) { rules.map { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = layout.pageHorizontal, vertical = layout.microGap),
        verticalArrangement = Arrangement.spacedBy(layout.microGap),
    ) {
        items(rules, key = { it.id }) { rule ->
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
    }
}

@Composable
private fun ColumnScope.RuleEmptyState(title: String, body: String) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = layout.pageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(layout.relatedGap))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 自定义规则行：开关 + 底部操作区（上移/下移/编辑/删除）。 */
@Composable
private fun CustomRuleRow(
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
    val layout = LocalLayoutTokens.current
    Column(Modifier.fillMaxWidth()) {
        SettingRow(
            title = name,
            subtitle = "${scope.ruleScopeLabel()} · 自定义",
            trailing = { Switch(checked = enabled, onCheckedChange = onToggle) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = layout.cardPadding),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除")
            }
        }
    }
}

/** 内置规则行：标准内置开关禁用；宽松内置可启停（ToggleBuiltinToc）。 */
@Composable
private fun BuiltinRuleRow(rule: TocRule, onToggle: ((Boolean) -> Unit)?) {
    SettingRow(
        title = rule.name,
        subtitle = buildString {
            append(rule.scope.ruleScopeLabel())
            append(" · 内置")
            if (rule.id == BuiltinTocRules.STANDARD_ID) append(" · 标准")
        },
        trailing = { Switch(checked = rule.enabled, onCheckedChange = onToggle) },
    )
}

/** 最近 mutation 结果的简短反馈：错误用 error 色，其余用 primary 色。 */
@Composable
private fun MutationFeedbackRow(result: RuleMutationResult) {
    val isError = result is RuleMutationResult.Rejected || result is RuleMutationResult.NotFound
    Text(
        text = result.feedbackText(),
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 编辑器
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.RuleEditor(
    draft: RuleEditorDraft,
    onDraftChange: (RuleEditorDraft) -> Unit,
    previewText: String,
    effectiveToc: List<TocRule>,
    effectiveReplace: List<ReplaceRule>,
    mutationResult: RuleMutationResult?,
    onSave: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val check = remember(draft, previewText, effectiveToc, effectiveReplace) {
        evaluateDraft(draft, effectiveToc, effectiveReplace, previewText)
    }
    val saveEnabled = draft.name.isNotBlank() && draft.pattern.isNotBlank() && check is RuleDraftCheck.Valid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = { Text("规则名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.pattern,
            onValueChange = { onDraftChange(draft.copy(pattern = it)) },
            label = { Text("正则表达式") },
            supportingText = { Text("示例：第[0-9]+章") },
            modifier = Modifier.fillMaxWidth().padding(top = layout.relatedGap),
        )
        if (draft.kind == RuleKind.REPLACE) {
            OutlinedTextField(
                value = draft.replacement,
                onValueChange = { onDraftChange(draft.copy(replacement = it)) },
                label = { Text("替换文本") },
                supportingText = { Text("留空表示删除命中文本") },
                modifier = Modifier.fillMaxWidth().padding(top = layout.relatedGap),
            )
        }

        Text(
            text = "作用域",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = layout.contentGap, bottom = layout.relatedGap),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(layout.relatedGap)) {
            OptionPill(
                selected = draft.scope == RuleScope.PER_BOOK,
                label = "本书",
                onClick = { onDraftChange(draft.copy(scope = RuleScope.PER_BOOK)) },
            )
            OptionPill(
                selected = draft.scope == RuleScope.GLOBAL,
                label = "全局",
                onClick = { onDraftChange(draft.copy(scope = RuleScope.GLOBAL)) },
            )
        }

        mutationResult?.let { MutationFeedbackRow(it) }

        EditorPreview(draft = draft, previewText = previewText, check = check)

        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = layout.contentGap),
        ) {
            Text(if (draft.id == null) "保存新增" else "保存修改")
        }
    }
}

/** 编辑器预览区：命中数 + 抽样/节选；正则问题显示中文错误。 */
@Composable
private fun EditorPreview(
    draft: RuleEditorDraft,
    previewText: String,
    check: RuleDraftCheck,
) {
    val layout = LocalLayoutTokens.current
    Column(Modifier.fillMaxWidth().padding(top = layout.contentGap)) {
        Text("预览", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(layout.microGap))
        when {
            draft.pattern.isBlank() ->
                PreviewHint("请输入正则表达式后查看预览")
            check is RuleDraftCheck.Invalid ->
                check.errors.forEach { error ->
                    Text(
                        text = error.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = layout.microGap),
                    )
                }
            previewText.isBlank() ->
                PreviewHint("暂无预览文本，保存后可在阅读中生效。")
            else ->
                when (val preview = (check as RuleDraftCheck.Valid).preview) {
                    is RuleDraftPreview.Toc -> {
                        Text(
                            text = "命中 ${preview.result.chapterCount} 章",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        preview.result.sampleTitles.forEach { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = layout.microGap),
                            )
                        }
                    }
                    is RuleDraftPreview.Replace -> {
                        val excerpts = remember(draft, preview.result) {
                            replaceExcerpts(preview.result.before, preview.result.after, draft.pattern)
                        }
                        Text(
                            text = "命中 ${preview.result.hitCount} 处",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "前：${excerpts.before}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = layout.microGap),
                        )
                        Text(
                            text = "后：${excerpts.after}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
        }
    }
}

@Composable
private fun PreviewHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
