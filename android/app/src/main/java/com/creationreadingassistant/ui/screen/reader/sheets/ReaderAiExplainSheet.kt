package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
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

    // 思考解析中波纹微动画
    val infiniteTransition = rememberInfiniteTransition(label = "explainThinkingWave")
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveAlpha",
    )

    fun callExplain() {
        if (selectedText.isBlank()) return
        loading = true
        error = null
        result = ""
        val contextLimit = readerAiSentCharacterCount(selectedText)
        job = scope.launch(Dispatchers.IO) {
            try {
                aiClient.chatStreaming(
                    "你是一个阅读助手。请对选中的文本进行深入解读：分析其含义、背景、写作手法或潜在寓意。保持专业但易懂。",
                    "请解读这段文字：${selectedText.take(contextLimit)}",
                ) { delta -> result += delta }
                    .onSuccess {
                        loading = false
                        result = readerAiResultForRequestOutcome(result, successful = true)
                    }
                    .onFailure {
                        error = it.message
                        loading = false
                        result = readerAiResultForRequestOutcome(result, successful = false)
                    }
            } catch (e: CancellationException) {
                loading = false
                result = readerAiResultForRequestOutcome(result, successful = false)
                throw e
            }
        }
    }

    ReaderSheetScaffold(
        title = "AI 词句解读",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 选中文本卡片
            AiExplainSelectedCard(selectedText = selectedText)

            // 解读操作主按钮
            val canExplain = selectedText.isNotBlank()
            AiExplainActionButton(
                loading = loading,
                canExplain = canExplain,
                onCancel = { job?.cancel() },
                onExplain = { callExplain() },
            )

            // 错误提示微卡片
            error?.let {
                AiExplainErrorCard(error = it)
            }

            // AI 解析结果卡片
            if (result.isNotBlank()) {
                AiExplainResultCard(
                    result = result,
                    loading = loading,
                    error = error,
                    selectedTagNames = selectedTagNames,
                    selectedCategoryIds = selectedCategoryIds,
                    waveAlpha = waveAlpha,
                    onSaveInspiration = onSaveInspiration,
                )

                // 灵感沉淀组件微岛
                AiExplainInspirationSection(
                    categories = categories,
                    selectedCategoryIds = selectedCategoryIds,
                    onCategoryToggle = { id ->
                        selectedCategoryIds = if (selectedCategoryIds.contains(id)) {
                            selectedCategoryIds - id
                        } else {
                            selectedCategoryIds + id
                        }
                    },
                    newCategoryInput = newCategoryInput,
                    onNewCategoryInputChange = { newCategoryInput = it },
                    onCreateCategory = {
                        val n = newCategoryInput.trim()
                        if (n.isNotBlank()) {
                            val id = onCreateCategory(n)
                            selectedCategoryIds = selectedCategoryIds + id
                            newCategoryInput = ""
                        }
                    },
                    inspirationTags = inspirationTags,
                    selectedTagNames = selectedTagNames,
                    onTagToggle = { name ->
                        selectedTagNames = if (selectedTagNames.contains(name)) {
                            selectedTagNames - name
                        } else {
                            selectedTagNames + name
                        }
                    },
                    onCustomTagRemove = { name ->
                        selectedTagNames = selectedTagNames - name
                    },
                    newTagInput = newTagInput,
                    onNewTagInputChange = { newTagInput = it },
                    onCreateTag = {
                        val names = newTagInput.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
                        if (names.isNotEmpty()) {
                            names.forEach { n -> if (inspirationTags.none { it.name == n }) onCreateTag(n) }
                            selectedTagNames = (selectedTagNames + names).distinct()
                            newTagInput = ""
                        }
                    },
                    canDeposit = canSaveReaderAiResult(result, loading, error),
                    onDeposit = {
                        onSaveInspiration(result, selectedTagNames, selectedCategoryIds)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
