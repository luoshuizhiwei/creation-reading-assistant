package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Check
import androidx.compose.animation.core.tween
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.sync.legado.LegadoImportPipeline
import com.creationreadingassistant.ui.theme.PillShape

/**
 * 统一「导入阅读记录」预览-提交页（LEGADO-IMPORT-PLAN-v2 §1/§6）。
 *
 * 本地 ZIP 与 WebDAV 备份都进入本页；预览展示 §4 六类结果与三层历史冲突，
 * 用户逐书确认后提交；提交结果支持一次性撤销（§9）。应用内交互（预览手选、
 * 来源库确认）属产品功能；开发/验收流程无用户动手依赖（契约 §8）。
 */
@Composable
internal fun LegadoImportPreview(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val ui = state.legadoImport
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(ProfileAction.LegadoDismiss) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text(
                    "导入阅读记录",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "开源阅读（Legado/MD3）记录的只读预览与确认提交",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (ui.phase) {
            LegadoImportPhase.IDLE -> IdleContent(onAction)
            LegadoImportPhase.PARSING -> ParsingContent()
            LegadoImportPhase.FAILED -> FailedContent(ui, onAction)
            LegadoImportPhase.READY -> ReadyContent(state, ui, onAction)
            LegadoImportPhase.COMMITTING -> ParsingContent(committing = true)
            LegadoImportPhase.COMMITTED -> CommittedContent(state, ui, onAction)
        }

        BatchesSection(state, onAction)
        PendingSection(state, onAction)
    }
}

@Composable
private fun IdleContent(onAction: (ProfileAction) -> Unit) {
    SectionCardHost {
        Text(
            "从「备份与同步」页选择 WebDAV 备份，或用「从本机选择备份 ZIP」导入本地归档；" +
                "本页完成解析、预览与提交。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.WEBDAV)) },
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            Text("去备份与同步选择备份")
        }
    }
}

@Composable
private fun ParsingContent(committing: Boolean = false) {
    SectionCardHost {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(if (committing) "正在提交…" else "正在解析备份…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FailedContent(ui: LegadoImportUi, onAction: (ProfileAction) -> Unit) {
    SectionCardHost {
        Text("解析失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(
            ui.error ?: "未知原因",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onAction(ProfileAction.LegadoDismiss) },
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) { Text("返回") }
    }
}

@Composable
private fun ReadyContent(state: ProfileUiState, ui: LegadoImportUi, onAction: (ProfileAction) -> Unit) {
    val preview = ui.preview ?: return

    // ---- 来源库 ----
    SectionCardHost(title = "来源库") {
        Text(
            "同一来源库内的重复导入按内容指纹幂等；不同来源库互不合并。跨设备/副本默认分列。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(
                onClick = { onAction(ProfileAction.LegadoSelectSource(null)) },
                label = { Text(if (ui.selectedSourceId == null) "● 新建来源库" else "新建来源库") },
            )
            preview.sources.take(6).forEach { source ->
                AssistChip(
                    onClick = { onAction(ProfileAction.LegadoSelectSource(source.source_id)) },
                    label = {
                        Text(
                            if (ui.selectedSourceId == source.source_id) "● ${source.label}" else source.label,
                        )
                    },
                )
            }
        }
        if (ui.selectedSourceId == null) {
            OutlinedTextField(
                value = ui.newSourceLabel,
                onValueChange = { onAction(ProfileAction.LegadoSetNewSourceLabel(it)) },
                label = { Text("新来源库名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(),
            )
        }
    }

    // ---- 行级异常 ----
    if (preview.rowErrors.any) {
        SectionCardHost(title = "无法识别的行") {
            Text(
                "bookshelf ${preview.rowErrors.bookshelf} / 会话 ${preview.rowErrors.sessions} / " +
                    "按日 ${preview.rowErrors.days} / 书级 ${preview.rowErrors.books} 条缺必需字段或类型/范围无效，" +
                    "已如实跳过（不默填、不伪造）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // ---- 位置预览 ----
    SectionCardHost(title = "续读位置（${preview.positions.size} 条）") {
        Text(
            "本地已有有效位置默认保留；替换需逐书确认。FINISHED 与已删除进度受保护。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        preview.positions.forEach { row ->
            val canSelect = row.importable || ui.candidateOverrides.containsKey(row.ordinal)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = canSelect && row.ordinal in ui.selectedOrdinals,
                        onCheckedChange = { onAction(ProfileAction.LegadoTogglePosition(row.ordinal)) },
                        enabled = canSelect,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${row.subjectKey.name}／${row.subjectKey.author.ifBlank { "佚名" }}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            "${row.category.label}｜${row.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.protection != null) {
                            Text(
                                row.protection,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                // 同名候选预览手选（契约 §3：不隐式消歧）。
                val multiCandidates = row.candidates.filter { it.contentPresent && !it.unsupportedPositionFormat }
                if (multiCandidates.size > 1) {
                    multiCandidates.forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 28.dp),
                        ) {
                            RadioButton(
                                selected = ui.candidateOverrides[row.ordinal] == candidate.bookId,
                                onClick = { onAction(ProfileAction.LegadoSelectCandidate(row.ordinal, candidate.bookId)) },
                            )
                            Text(
                                "${candidate.title}（${candidate.format.uppercase()}，${candidate.chapters.size} 章）",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 三层历史 ----
    SectionCardHost(title = "外部阅读历史") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "会话 ${preview.history.sessionCount} / 按日 ${preview.history.dayCount} / 书级 ${preview.history.bookCount}" +
                    "（设备 ${preview.history.devices} 台）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Switch(checked = ui.importHistory, onCheckedChange = { onAction(ProfileAction.LegadoSetHistory(it)) })
        }
        Text(
            "历史只进独立来源账本，不增加本地阅读时长/天数/连续阅读（契约 §5，无合并开关）。" +
                "readWords 保留上游原字段（上游将其记为章节序号累加，非真实字数）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val conflicts = preview.history.dayConflicts + preview.history.bookConflicts
        if (conflicts.isNotEmpty()) {
            Text("同键异值快照 ${conflicts.size} 条：默认保留现有快照，勾选后以本次值替换。", style = MaterialTheme.typography.bodySmall)
            conflicts.forEach { conflict ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = conflict.rowKey in ui.replaceSnapshotKeys,
                        onCheckedChange = { onAction(ProfileAction.LegadoToggleReplaceSnapshot(conflict.rowKey)) },
                    )
                    Text(
                        "${conflict.layer}｜${conflict.existingSummary} → ${conflict.incomingSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ---- 提交/取消 ----
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // 提交按钮三连变（林逸-02 Submit Morph）：点击文字淡出、宽度收缩成圆形转圈；
        // 完成后变绿画对勾并停留（导入结果页承载终态，不做 1s 恢复）。
        val committing = ui.phase == LegadoImportPhase.COMMITTING
        val committed = ui.phase == LegadoImportPhase.COMMITTED
        val shrink = remember { Animatable(0f) }
        LaunchedEffect(committing, committed) {
            shrink.animateTo(
                if (committing || committed) 1f else 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            // 半行全宽（Row 间距 12.dp 的一半由 weight 分摊，取父约束即可）收缩到 44.dp 圆形
            val expandedW = this.maxWidth
            val w = expandedW - ((expandedW - 44.dp) * shrink.value)
            Button(
                onClick = { onAction(ProfileAction.LegadoCommit) },
                enabled = !committing && !committed,
                modifier = Modifier
                    .width(w.coerceAtLeast(44.dp))
                    .height(44.dp)
                    .align(Alignment.CenterStart),
                shape = RoundedCornerShape(percent = 50),
                colors = if (committed) ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White,
                ) else ButtonDefaults.buttonColors(),
            ) {
                if (committed) {
                    Icon(Icons.Outlined.Check, contentDescription = "导入完成", modifier = Modifier.size(22.dp))
                } else if (committing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("提交所选")
                }
            }
        }
        OutlinedButton(
            onClick = { onAction(ProfileAction.LegadoDismiss) },
            enabled = !committing,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = PillShape,
        ) { Text("取消") }
    }
}

@Composable
private fun CommittedContent(state: ProfileUiState, ui: LegadoImportUi, onAction: (ProfileAction) -> Unit) {
    val result = ui.lastResult ?: return
    SectionCardHost(title = "导入完成") {
        Text(result.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            "批次 ${result.batchId?.take(8) ?: "?"}；可撤销本批（不影响之后的本地阅读）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { result.batchId?.let { onAction(ProfileAction.LegadoUndo(it)) } },
                shape = PillShape,
            ) {
                Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("撤销本批")
            }
            Button(onClick = { onAction(ProfileAction.LegadoDismiss) }, shape = PillShape) { Text("完成") }
        }
        ui.lastUndo?.let { undo ->
            Text(
                "撤销结果：${undo.status}（撤销 ${undo.reverted}/保留 ${undo.kept}）${undo.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatchesSection(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    if (state.legadoBatches.isEmpty()) return
    SectionCardHost(title = "导入批次") {
        state.legadoBatches.take(8).forEach { batch ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${batch.archive_label.take(18)} · ${batch.created_at.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when (batch.status) {
                            "committed" -> "已提交（可撤销）"
                            "undone_full" -> "已全部撤销"
                            else -> "已部分撤销"
                        } + (batch.undo_note?.let { "｜$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (batch.status == "committed") {
                    OutlinedButton(
                        onClick = { onAction(ProfileAction.LegadoUndo(batch.batch_id)) },
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("撤销", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun PendingSection(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    if (state.legadoPending.isEmpty()) return
    SectionCardHost(title = "待关联（${state.legadoPending.size}）") {
        Text(
            "未绑定/不可定位的位置记录：可导入对应 TXT/EPUB 后再关联，或直接删除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pendingJson = androidx.compose.runtime.remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
        state.legadoPending.take(20).forEach { item ->
            val payload = androidx.compose.runtime.remember(item.item_id) {
                runCatching {
                    pendingJson.decodeFromString(
                        LegadoImportPipeline.PendingPositionPayload.serializer(),
                        item.payload_json,
                    )
                }.getOrNull()
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        payload?.let { "${it.bookName}／${it.bookAuthor.ifBlank { "佚名" }}" } ?: "（载荷不可读）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        payload?.reason ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAction(ProfileAction.LegadoDeletePending(item.item_id)) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除待关联项")
                }
            }
        }
    }
}

@Composable
private fun SectionCardHost(title: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            content()
        }
    }
}
