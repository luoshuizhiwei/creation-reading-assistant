package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AiExplainSheet(
    aiClient: AiClient,
    selectedText: String,
    bookTitle: String,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSaveInspiration: (String, List<String>, List<String>) -> Unit,
) {
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }
    // G 档：分类/标签 chips 离散选择加轻触感
    val haptic = rememberHaptic(rememberReducedMotion())

    fun callExplain() {
        if (selectedText.isBlank()) return
        loading = true; error = null; result = ""
        job = scope.launch(Dispatchers.IO) {
            try {
                aiClient.chatStreaming(
                    "你是一个阅读助手。请对选中的文本进行深入解读：分析其含义、背景、写作手法或潜在寓意。保持专业但易懂。",
                    "请解读这段文字：${selectedText.take(4000)}",
                ) { delta -> result += delta }
                    .onSuccess { loading = false }
                    .onFailure { error = it.message; loading = false }
            } catch (e: CancellationException) {
                loading = false
                throw e
            }
        }
    }

    ReaderSheetScaffold(title = "AI 解读") {
      Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        if (selectedText.isNotBlank()) {
            SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("来源摘录：$selectedText")
            }
        } else {
            Text("请先选中一段正文，再使用 AI 解读。", color = MaterialTheme.colorScheme.outline)
        }
        Button(
            onClick = { if (loading) job?.cancel() else callExplain() },
            enabled = selectedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (loading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text("取消") }
            else Text("解读选中文本")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (result.isNotBlank()) {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(result)
            }
            Text("分类", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { c ->
                    val active = selectedCategoryIds.contains(c.id)
                    FilterChip(
                        selected = active,
                        onClick = { haptic(HapticFeedbackType.TextHandleMove); selectedCategoryIds = if (active) selectedCategoryIds - c.id else selectedCategoryIds + c.id },
                        label = { Text(c.name) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(value = newCategoryInput, onValueChange = { newCategoryInput = it }, label = { Text("新建分类") }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val n = newCategoryInput.trim()
                    if (n.isNotBlank()) { val id = onCreateCategory(n); selectedCategoryIds = selectedCategoryIds + id; newCategoryInput = "" }
                }) { Text("添加") }
            }
            Text("标签（默认带书名）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                inspirationTags.forEach { t ->
                    val active = selectedTagNames.contains(t.name)
                    FilterChip(
                        selected = active,
                        onClick = { haptic(HapticFeedbackType.TextHandleMove); selectedTagNames = if (active) selectedTagNames - t.name else selectedTagNames + t.name },
                        label = { Text(t.name) },
                    )
                }
                selectedTagNames.filter { name -> inspirationTags.none { it.name == name } }.forEach { name ->
                    FilterChip(selected = true, onClick = { haptic(HapticFeedbackType.TextHandleMove); selectedTagNames = selectedTagNames - name }, label = { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(value = newTagInput, onValueChange = { newTagInput = it }, label = { Text("新标签，逗号分隔") }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                    if (names.isNotEmpty()) {
                        names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                        selectedTagNames = (selectedTagNames + names).distinct()
                        newTagInput = ""
                    }
                }) { Text("添加") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { haptic(HapticFeedbackType.LongPress); onSaveInspiration(result, selectedTagNames, selectedCategoryIds) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("存入灵感") }
            }
        }
      }
    }
}
