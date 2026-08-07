package com.creationreadingassistant.data.ai

import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容 API 客户端，用于阅读器 AI 助手 / AI 解读。
 * 调用配置来自 SettingsStore（AI 启用、接口地址、模型、Key、温度、提示词）。
 */
@Singleton
class AiClient(
    private val settings: SettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val client: OkHttpClient,
) {
    /** Hilt 注入入口：使用默认超时配置的 OkHttpClient，行为与历史版本一致。 */
    @Inject
    constructor(
        settings: SettingsStore,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(settings, ioDispatcher, defaultClient())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 非加密 http 接口警示（不硬拦截，尊重用户自配接口语义）：
     * baseUrl 是 http:// 且目标不是局域网地址时，API Key 可能明文传输。
     * 进程内只记录一次警示日志，并通过该 StateFlow 暴露给 UI 展示。
     */
    private val _insecureHttpWarning = MutableStateFlow<String?>(null)
    val insecureHttpWarning: StateFlow<String?> = _insecureHttpWarning.asStateFlow()
    private val httpWarned = AtomicBoolean(false)

    @Serializable
    data class ChatMessage(val role: String, val content: String)

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float = 0.7f,
        val max_tokens: Int = 2048,
        val stream: Boolean = false,
    )

    @Serializable
    data class Choice(val index: Int, val message: ChatMessage)

    @Serializable
    data class ChatResponse(val choices: List<Choice>)

    /** 通用对话：发送提示词 + 系统指令，返回助手回答。 */
    suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val ai = settings.ai.value
            if (!ai.enabled) error("AI 未启用，请在「我的 → AI 设置」中开启。")
            if (ai.baseUrl.isBlank()) error("AI 接口地址未配置。")
            warnIfInsecureHttp(ai.baseUrl)
            val url = ai.baseUrl.trimEnd('/') + "/v1/chat/completions"

            val messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt),
            )
            val body = ChatRequest(
                model = ai.model.ifBlank { "gpt-3.5-turbo" },
                messages = messages,
                temperature = ai.temperature,
            )
            val req = Request.Builder()
                .url(url)
                .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
                .apply {
                    if (ai.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${ai.apiKey}")
                }
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("AI 服务返回 HTTP ${resp.code}: ${resp.body?.string()?.take(200) ?: ""}")
                val chatResp = json.decodeFromString<ChatResponse>(resp.body?.string() ?: error("空响应"))
                chatResp.choices.firstOrNull()?.message?.content ?: error("AI 未返回内容")
            }
        }
    }

    /**
     * baseUrl 为 http:// 且主机不是局域网地址时，记录一次性警示日志并暴露 UI 可读状态。
     * 不拦截请求：用户可能故意用自建明文接口，仅提示风险。
     */
    private fun warnIfInsecureHttp(baseUrl: String) {
        if (!baseUrl.startsWith("http://")) return
        val host = runCatching { URI(baseUrl).host }.getOrNull()?.lowercase()
        if (host == null || isLanHost(host)) return
        if (httpWarned.compareAndSet(false, true)) {
            AppLog.w("AiClient", "AI 接口为非加密 http:// 且目标不是局域网地址，API Key 可能明文传输：$baseUrl")
            _insecureHttpWarning.value =
                "当前 AI 接口为非加密 http:// 且目标不是局域网地址，API Key 可能明文传输，建议改用 https:// 或局域网地址。"
        }
    }

    /** 局域网/回环地址白名单：这些目标走明文 http 属常见自建部署场景，不警示。 */
    private fun isLanHost(host: String): Boolean =
        host == "localhost" || host == "0.0.0.0" ||
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            LAN_172_REGEX.matches(host)

    private val LAN_172_REGEX = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.")

    /** 阅读辅助快捷入口。 */
    suspend fun summarize(text: String): Result<String> = chat(
        systemPrompt = "你是一个阅读助手。请用简洁的中文总结下面的文本，控制在 200 字以内，突出核心观点。",
        userPrompt = text,
    )

    /** 连接测试：用最小 prompt 验证接口地址 / Key 是否可用。 */
    suspend fun testConnection(): Result<String> = chat(
        systemPrompt = "你是连接测试助手，只回复「OK」两个字。",
        userPrompt = "ping",
    )

    suspend fun askQuestion(contextText: String, question: String): Result<String> = chat(
        systemPrompt = "你是一个阅读助手。请基于提供的上下文来回答问题。如果上下文不足以回答，请诚实说明。",
        userPrompt = "上下文：$contextText\n\n问题：$question",
    )

    suspend fun extractKeyPoints(text: String): Result<String> = chat(
        systemPrompt = "你是一个阅读助手。请从下面的文本中提取 3-5 个关键要点，每条要点以「- 」开头，简明扼要。",
        userPrompt = text,
    )

    suspend fun explain(text: String): Result<String> = chat(
        systemPrompt = "你是一个阅读助手。请对选中的文本进行深入解读：分析其含义、背景、写作手法或潜在寓意。保持专业但易懂。",
        userPrompt = "请解读这段文字：$text",
    )
}
