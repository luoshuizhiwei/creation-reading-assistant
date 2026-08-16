package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

/** 灵感列表加载状态：由 repository flow 第一次真实 emission 驱动，Route 不再自行猜测 firstLoad。 */
sealed interface InspirationItemsState {
    data object Loading : InspirationItemsState
    data class Loaded(val items: List<InspirationEntity>) : InspirationItemsState
}

@HiltViewModel
class InspirationViewModel @Inject constructor(
    private val inspirationRepository: InspirationRepository,
    private val bookRepository: BookRepository,
    private val aiClient: AiClient,
    private val settings: SettingsStore,
) : ViewModel() {
    private val payloadCache = object : LinkedHashMap<String, InspirationPayloadData>(128, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, InspirationPayloadData>?,
        ): Boolean = size > 256
    }

    private val _pendingSavedIds = MutableStateFlow<Set<String>>(emptySet())

    /** 已保存但尚未被列表 flow 观察到的草稿 id：详情解析据此保持 Loading，避免误判为不存在。 */
    val pendingSavedIds: StateFlow<Set<String>> = _pendingSavedIds.asStateFlow()

    val itemsState: StateFlow<InspirationItemsState> = inspirationRepository.observeAllActive()
        .map { list ->
            if (_pendingSavedIds.value.isNotEmpty()) {
                _pendingSavedIds.update { pending -> pending - list.mapTo(HashSet()) { it.id } }
            }
            InspirationItemsState.Loaded(list)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InspirationItemsState.Loading)

    val books: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 灵感列表排序方式（持久化到 DataStore，对照网页 INSPIRATION_SORT_KEY）。 */
    val inspirationSort: StateFlow<String> = settings.inspirationSort

    fun setInspirationSort(value: String) {
        viewModelScope.launch { settings.setInspirationSort(value) }
    }

    /** 观察某条灵感的 AI 候选版本。 */
    fun observeVariants(inspirationId: String): Flow<List<InspirationVariantEntity>> =
        inspirationRepository.observeVariants(inspirationId)

    /** 采用某个候选版本为正文。 */
    fun applyVariant(inspirationId: String, variant: InspirationVariantEntity) {
        if (variant.content == null) return
        viewModelScope.launch { inspirationRepository.applyVariant(inspirationId, variant) }
    }

    /** 删除某个候选版本。 */
    fun deleteVariant(variantId: String) {
        viewModelScope.launch { inspirationRepository.deleteVariant(variantId) }
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
                inspirationRepository.saveVariant(
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

    fun tagsOf(entity: InspirationEntity): List<String> = parsePayload(entity).tags

    fun sourceOf(entity: InspirationEntity): InspirationSourceInfo? = parsePayload(entity).source

    fun saveInspiration(draft: InspirationDraft) {
        val now = java.time.Instant.now().toString()
        val loadedItems = (itemsState.value as? InspirationItemsState.Loaded)?.items ?: emptyList()
        val existing = draft.id?.let { id -> loadedItems.firstOrNull { it.id == id } }
        val id = existing?.id ?: draft.id ?: java.util.UUID.randomUUID().toString()
        if (existing == null) {
            _pendingSavedIds.update { it + id }
        }
        val payload = InspirationPayloadData(tags = draft.tags, source = draft.source)
        val entity = InspirationEntity(
            id = id,
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
        viewModelScope.launch { inspirationRepository.upsert(entity) }
    }

    fun deleteInspiration(id: String) {
        viewModelScope.launch { inspirationRepository.deleteInspiration(id) }
    }

    private fun parsePayload(json: String?): InspirationPayloadData {
        if (json.isNullOrBlank()) return InspirationPayloadData()
        return runCatching { JSON.decodeFromString<InspirationPayloadData>(json) }
            .getOrDefault(InspirationPayloadData())
    }

    private fun parsePayload(entity: InspirationEntity): InspirationPayloadData {
        val cacheKey = "${entity.id}:${entity.updated_at}:${entity.payload?.hashCode() ?: 0}"
        synchronized(payloadCache) {
            payloadCache[cacheKey]?.let { return it }
        }
        val parsed = parsePayload(entity.payload)
        synchronized(payloadCache) { payloadCache[cacheKey] = parsed }
        return parsed
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
