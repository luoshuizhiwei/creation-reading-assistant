package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspirationSheet(
    bookTitle: String,
    chapterTitle: String,
    excerpt: String,
    progressPercent: Float,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onCreateCategory: (String) -> String,
    onCreateTag: (String) -> String,
    onSave: (String, String, List<String>, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("阅读灵感：$bookTitle") }
    var body by remember { mutableStateOf("") }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTagNames by remember { mutableStateOf<List<String>>(listOf(bookTitle, "阅读灵感")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }
    val canSave = title.isNotBlank() || body.isNotBlank() || excerpt.isNotBlank()
    val inspirationTags = tags.filter { it.type == "inspiration" || it.type == null }
    // G 档：分类/标签 chips 离散选择加轻触感
    val haptic = rememberHaptic(rememberReducedMotion())

    ReaderSheetScaffold(title = "记录灵感") {
      Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("我的想法") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(top = 8.dp),
        )
        if (excerpt.isNotBlank()) {
            SectionCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("来源摘录：$excerpt")
            }
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

        Text(
            "来源：$bookTitle · ${chapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canSave,
                onClick = { haptic(HapticFeedbackType.LongPress); onSave(title.trim(), body.trim(), selectedTagNames, selectedCategoryIds) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存灵感") }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
      }
    }
}
