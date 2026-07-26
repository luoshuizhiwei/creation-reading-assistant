package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 灵感来源信息（原生无独立 source 表，随灵感存入 payload）。 */
@Serializable
data class InspirationSourceInfo(
    val bookId: String? = null,
    val bookTitle: String? = null,
    val bookAuthor: String? = null,
    val chapterTitle: String? = null,
    val locationLabel: String? = null,
    val progressPercent: Float? = null,
    val excerpt: String? = null,
)

/** 灵感 payload（tags + categoryIds + source），与原生 inspirations.payload 列对应。 */
@Serializable
 data class InspirationPayloadData(
    val tags: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val source: InspirationSourceInfo? = null,
)

/** 编辑器产出的灵感草稿（id 为 null 表示新建）。 */
data class InspirationDraft(
    val id: String? = null,
    val title: String,
    val body: String,
    val type: String,
    val status: String,
    val tags: List<String>,
    val source: InspirationSourceInfo?,
)

@HiltViewModel
class InspirationViewModel @Inject constructor(
    private val inspirationDao: InspirationDao,
    private val variantDao: InspirationVariantDao,
    private val bookRepository: BookRepository,
    private val aiClient: AiClient,
    private val settings: SettingsStore,
) : ViewModel() {

    val items: StateFlow<List<InspirationEntity>> = inspirationDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 灵感列表排序方式（持久化到 DataStore，对照网页 INSPIRATION_SORT_KEY）。 */
    val inspirationSort: StateFlow<String> = settings.inspirationSort

    fun setInspirationSort(value: String) {
        viewModelScope.launch { settings.setInspirationSort(value) }
    }

    /** 观察某条灵感的 AI 候选版本。 */
    fun observeVariants(inspirationId: String): Flow<List<InspirationVariantEntity>> =
        variantDao.observeByInspiration(inspirationId)

    /** 采用某个候选版本为正文。 */
    fun applyVariant(inspirationId: String, variant: InspirationVariantEntity) {
        val content = variant.content ?: return
        viewModelScope.launch {
            val existing = inspirationDao.getById(inspirationId) ?: return@launch
            inspirationDao.upsert(existing.copy(body = content, updated_at = java.time.Instant.now().toString()))
        }
    }

    /** 删除某个候选版本。 */
    fun deleteVariant(variantId: String) {
        viewModelScope.launch { variantDao.delete(variantId) }
    }

    /** 灵感 AI 动作（对照网页 aiActions + buildPrompt）。 */
    val AI_ACTIONS: List<Pair<String, String>> = listOf(
        "polish" to "润色",
        "expand" to "扩写",
        "platform-style" to "平台风格化",
        "conflict" to "生成冲突",
        "humanize" to "去 AI 味",
    )

    private val AI_INSTRUCTIONS: Map<String, String> = mapOf(
        "polish" to "把这条小说灵感润色成更清楚、更有画面感、更适合后续写作的素材。保留原意，不要替作者决定完整剧情。",
        "expand" to "基于这条小说灵感扩展 3-5 个可写方向，包括人物动机、场景推进和可用细节。",
        "platform-style" to "把这条灵感改写成更适合中文网文平台使用的素材，节奏直接、冲突清楚、表达自然。",
        "conflict" to "从这条灵感中提炼或生成 5 个可推动剧情的冲突点，每个冲突点给一句可写切入。",
        "humanize" to "去掉机械总结感和 AI 腔，把这条灵感改成更自然、更像作者自己随手记录但清楚可用的素材。",
    )

    private fun buildInspirationPrompt(action: String, title: String, content: String): String {
        val instruction = AI_INSTRUCTIONS[action] ?: return content
        val titleLine = title.trim().takeIf { it.isNotBlank() }?.let { "标题：$it\n" } ?: ""
        return "${instruction}\n\n${titleLine}原始灵感：\n${content}\n\n要求：只输出候选内容，不要解释你做了什么。"
    }

    /**
     * 对灵感执行某一个具名 AI 动作（润色/扩写/平台风格化/生成冲突/去 AI 味），
     * 结果作为一条候选版本存入 inspiration_variants，不覆盖正文。对照网页 runInspirationAI。
     */
    fun runInspirationAction(
        inspirationId: String,
        action: String,
        title: String,
        baseBody: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        if (action !in AI_INSTRUCTIONS) { onDone(false); return }
        if (baseBody.isBlank()) { onDone(false); return }
        viewModelScope.launch {
            val model = settings.ai.value.model.ifBlank { "gpt-3.5-turbo" }
            val userPrompt = buildInspirationPrompt(action, title, baseBody)
            aiClient.chat(
                systemPrompt = "你是中文小说作者的灵感打磨助手。输出要自然、具体、可继续写，不要有 AI 腔。",
                userPrompt = userPrompt,
            ).onSuccess { result ->
                variantDao.upsert(
                    InspirationVariantEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        inspiration_id = inspirationId,
                        kind = action,
                        content = result,
                        prompt = userPrompt,
                        model = model,
                        created_at = java.time.Instant.now().toString(),
                    ),
                )
                onDone(true)
            }.onFailure { onDone(false) }
        }
    }

    fun tagsOf(entity: InspirationEntity): List<String> = parsePayload(entity.payload).tags

    fun sourceOf(entity: InspirationEntity): InspirationSourceInfo? = parsePayload(entity.payload).source

    fun saveInspiration(draft: InspirationDraft) {
        val now = java.time.Instant.now().toString()
        val existing = draft.id?.let { id -> items.value.firstOrNull { it.id == id } }
        val payload = InspirationPayloadData(tags = draft.tags, source = draft.source)
        val entity = InspirationEntity(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            title = draft.title.ifBlank { "未命名灵感" },
            body = draft.body,
            type = draft.type,
            status = draft.status,
            source_book_id = draft.source?.bookId,
            payload = JSON.encodeToString(InspirationPayloadData.serializer(), payload),
            created_at = existing?.created_at ?: now,
            updated_at = now,
            deleted_at = null,
        )
        viewModelScope.launch { inspirationDao.upsert(entity) }
    }

    fun deleteInspiration(id: String) {
        viewModelScope.launch {
            val existing = inspirationDao.getById(id) ?: return@launch
            inspirationDao.upsert(existing.copy(deleted_at = java.time.Instant.now().toString()))
        }
    }

    private fun parsePayload(json: String?): InspirationPayloadData {
        if (json.isNullOrBlank()) return InspirationPayloadData()
        return runCatching { JSON.decodeFromString<InspirationPayloadData>(json) }
            .getOrDefault(InspirationPayloadData())
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
