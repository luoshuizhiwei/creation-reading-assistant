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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    /**
     * R5-I2：保存时的阅读器 source locator JSON（[LocatorCodec] 口径，与高亮/笔记同源）。
     * 携带全局 source 坐标的摘录可在详情里「临时查阅原文」精确回到来源位置；
     * null = 历史数据或无定位上下文，详情降级为「打开书籍」。
     */
    val locatorJson: String? = null,
)

/** 灵感 payload（tags + categoryIds + source），与原生 inspirations.payload 列对应。 */
@Serializable
 data class InspirationPayloadData(
    val tags: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val source: InspirationSourceInfo? = null,
    /** R5-I1：采用去向记录（文字/链接），与正文、AI 候选分开存放。 */
    val adoptions: List<InspirationAdoptionRecord> = emptyList(),
    /** R5-I2：多摘录素材卡聚合的各来源摘录（首来源另见 [source]）。 */
    val excerpts: List<InspirationSourceInfo> = emptyList(),
    /** 该条已被合并进的目标素材卡 id；非 null 表示原条目已归档待查。 */
    val mergedInto: String? = null,
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
    data class Loaded(val items: ImmutableList<InspirationEntity>) : InspirationItemsState {
        constructor(items: List<InspirationEntity> = emptyList()) : this(items.toImmutableList())
    }
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
            InspirationItemsState.Loaded(list.toImmutableList())
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

    /** R5-I1/I2：详情页消费的完整 payload（采用去向 + 聚合摘录 + 来源定位）。 */
    fun payloadFor(entity: InspirationEntity): InspirationPayloadData = parsePayload(entity)

    fun saveInspiration(draft: InspirationDraft) {
        val now = java.time.Instant.now().toString()
        val loadedItems = (itemsState.value as? InspirationItemsState.Loaded)?.items ?: emptyList()
        val existing = draft.id?.let { id -> loadedItems.firstOrNull { it.id == id } }
        val id = existing?.id ?: draft.id ?: java.util.UUID.randomUUID().toString()
        if (existing == null) {
            _pendingSavedIds.update { it + id }
        }
        // 编辑器只拥有 tags / source 两个字段；采用去向（R5-I1）、聚合摘录（R5-I2）与
        // 归档去向由详情页与合并流程维护。编辑保存必须从既有 payload 继承这些字段，
        // 否则「只改一个标题」会静默清空素材卡的摘录/采用记录。
        val previous = existing?.let { parsePayload(it) }
        val payload = InspirationPayloadData(
            tags = draft.tags,
            categoryIds = previous?.categoryIds ?: emptyList(),
            source = draft.source,
            adoptions = previous?.adoptions ?: emptyList(),
            excerpts = previous?.excerpts ?: emptyList(),
            mergedInto = previous?.mergedInto,
        )
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

    /** 当前列表条目的 payload 快照（不在列表中返回空 payload，操作幂等失败）。 */
    private fun payloadOf(id: String): InspirationPayloadData? {
        val loaded = itemsState.value as? InspirationItemsState.Loaded ?: return null
        val entity = loaded.items.firstOrNull { it.id == id } ?: return null
        return parsePayload(entity)
    }

    private fun upsertPayload(id: String, payload: InspirationPayloadData, status: String? = null) {
        val loaded = itemsState.value as? InspirationItemsState.Loaded ?: return
        val entity = loaded.items.firstOrNull { it.id == id } ?: return
        val now = java.time.Instant.now().toString()
        viewModelScope.launch {
            inspirationRepository.upsert(
                entity.copy(
                    payload = JSON.encodeToString(InspirationPayloadData.serializer(), payload),
                    status = status ?: entity.status,
                    revision = entity.revision + 1,
                    updated_at = now,
                ),
            )
        }
    }

    /**
     * R5-I1：记录一条采用去向（文字/链接）。首次采用会把状态推进为「已采用」；
     * 校验失败（value 空白 / kind 非法）回调 false，不落库。
     */
    fun addAdoption(
        inspirationId: String,
        kind: String,
        value: String,
        note: String?,
        onDone: (Boolean) -> Unit = {},
    ) {
        val payload = payloadOf(inspirationId) ?: run { onDone(false); return }
        val entity = (itemsState.value as? InspirationItemsState.Loaded)?.items
            ?.firstOrNull { it.id == inspirationId }
        val record = InspirationAdoptionRecord(
            kind = kind,
            value = value.trim(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = java.time.Instant.now().toString(),
        )
        val (next, nextStatus) = payloadWithAdoption(payload, record, entity?.status ?: "inbox")
            ?: run { onDone(false); return }
        upsertPayload(inspirationId, next, nextStatus)
        onDone(true)
    }

    /** 移除一条采用去向；状态不自动回退。 */
    fun removeAdoption(inspirationId: String, record: InspirationAdoptionRecord) {
        val payload = payloadOf(inspirationId) ?: return
        upsertPayload(inspirationId, payloadWithoutAdoption(payload, record))
    }

    /**
     * R5-I2：把多条素材聚合成一张**多摘录素材卡**。
     *
     * - 新卡状态「待整理」，正文按来源快照拼接，各条摘录的来源/locator 原样进入
     *   `payload.excerpts`（来源定位逐条可用）；
     * - 被聚合的原始条目**归档**（不删除）并在 payload 记 `mergedInto`，可从归档恢复；
     * - 少于 2 条或列表未就绪时回调 false，不产生任何写入。
     */
    fun mergeIntoMaterialCard(ids: List<String>, onDone: (String?) -> Unit = {}) {
        if (ids.size < 2) { onDone(null); return }
        val loaded = itemsState.value as? InspirationItemsState.Loaded ?: run { onDone(null); return }
        val entities = ids.mapNotNull { id -> loaded.items.firstOrNull { it.id == id } }
        if (entities.size < 2) { onDone(null); return }

        val now = java.time.Instant.now().toString()
        val cardId = java.util.UUID.randomUUID().toString()
        val payloads = entities.map { parsePayload(it) }
        val cardPayload = mergedMaterialPayload(payloads, tags = emptyList(), categoryIds = emptyList())

        val body = entities.joinToString("\n\n") { entity ->
            val src = parsePayload(entity).source
            val header = listOfNotNull(
                src?.bookTitle?.let { "《$it》" },
                src?.chapterTitle,
            ).joinToString(" · ")
            (if (header.isBlank()) "" else "$header\n") + entity.body.ifBlank { src?.excerpt.orEmpty() }
        }
        val firstTitle = entities.first().title.takeIf { it.isNotBlank() && it != "未命名灵感" }
        val cardTitle = firstTitle ?: "素材卡 · ${entities.size} 条来源"

        viewModelScope.launch {
            inspirationRepository.upsert(
                InspirationEntity(
                    id = cardId,
                    title = cardTitle,
                    body = body,
                    type = "note",
                    status = "reviewing",
                    source_book_id = entities.firstNotNullOfOrNull { it.source_book_id },
                    payload = JSON.encodeToString(InspirationPayloadData.serializer(), cardPayload),
                    created_at = now,
                    device_id = null,
                    revision = 1,
                    updated_at = now,
                    deleted_at = null,
                ),
            )
            // 原始条目归档并记录去向，非破坏、可恢复
            entities.forEach { entity ->
                val p = parsePayload(entity)
                inspirationRepository.upsert(
                    entity.copy(
                        status = "archived",
                        payload = JSON.encodeToString(
                            InspirationPayloadData.serializer(),
                            p.copy(mergedInto = cardId),
                        ),
                        revision = entity.revision + 1,
                        updated_at = now,
                    ),
                )
            }
            _pendingSavedIds.update { it + cardId }
            onDone(cardId)
        }
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
