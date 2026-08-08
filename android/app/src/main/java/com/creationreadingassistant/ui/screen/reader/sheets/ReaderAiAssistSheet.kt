package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun selectedTextSafe(s: String): String = s

@Composable
internal fun AiAssistSheet(
    aiClient: AiClient,
    @Suppress("unused") bookTitle: String,
    chapterTitle: String,
    contextText: String,
) {
    var tab by remember { mutableStateOf("summary") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val hasContext = contextText.isNotBlank()

    fun callAi() {
        if (!hasContext) return
        loading = true; error = null; result = ""
        job = scope.launch(Dispatchers.IO) {
            try {
                val r = when (tab) {
                    "summary" -> aiClient.chatStreaming(
                        "你是一个阅读助手。请用简洁的中文总结下面的文本，控制在 200 字以内，突出核心观点。",
                        contextText.take(4000),
                    ) { delta -> result += delta }
                    "qa" -> aiClient.chatStreaming(
                        "你是一个阅读助手。请基于提供的上下文来回答问题。如果上下文不足以回答，请诚实说明。",
                        "上下文：${contextText.take(4000)}\n\n问题：$question",
                    ) { delta -> result += delta }
                    else -> aiClient.chatStreaming(
                        "你是一个阅读助手。请从下面的文本中提取 3-5 个关键要点，每条要点以「- 」开头，简明扼要。",
                        contextText.take(4000),
                    ) { delta -> result += delta }
                }
                r.onSuccess { loading = false }
                    .onFailure { error = it.message; loading = false }
            } catch (e: CancellationException) {
                loading = false
                throw e
            }
        }
    }

    ReaderSheetScaffold(title = "AI 阅读辅助") {
      Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OptionPill(selected = tab == "summary", label = "章节摘要", onClick = { tab = "summary" })
            OptionPill(selected = tab == "qa", label = "内容问答", onClick = { tab = "qa" })
            OptionPill(selected = tab == "keypoints", label = "要点提取", onClick = { tab = "keypoints" })
        }
        Text(
            if (selectedTextSafe(contextText).isNotBlank()) "已选中 ${contextText.length} 字作为上下文" else "当前章节：$chapterTitle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (tab == "qa") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("对选中文本或当前章节提问…") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Button(
            onClick = { if (loading) job?.cancel() else callAi() },
            enabled = hasContext,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("取消")
            } else {
                Text(
                    when (tab) {
                        "summary" -> "生成章节摘要"
                        "qa" -> if (question.isBlank()) "请先输入问题" else "提问"
                        else -> "提取关键要点"
                    },
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (result.isNotBlank()) {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(result)
            }
        }
        if (!hasContext) {
            Text("当前没有可分析的文本内容。", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
        }
      }
    }
}
