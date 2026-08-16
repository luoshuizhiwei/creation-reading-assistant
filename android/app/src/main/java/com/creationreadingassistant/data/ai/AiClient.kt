package com.creationreadingassistant.data.ai

import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    /** 无网预检用；测试直连构造时为 null（预检跳过）。 */
    private val appContext: android.content.Context? = null,
) {
    /** Hilt 注入入口：使用默认超时配置的 OkHttpClient，行为与历史版本一致。 */
    @Inject
    constructor(
        settings: SettingsStore,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context,
    ) : this(settings, ioDispatcher, defaultClient(), appContext)

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

    /** SSE 流式响应的单条 chunk（choices[0].delta.content 为增量）。 */
    @Serializable
    data class ChatChunk(val choices: List<ChunkChoice> = emptyList())

    @Serializable
    data class ChunkChoice(val delta: ChunkDelta = ChunkDelta())

    @Serializable
    data class ChunkDelta(val content: String? = null)

    /**
     * 通用对话（非流式）：发送提示词 + 系统指令，返回助手回答。
     * 底层为可取消调用：协程取消时底层 HTTP 请求立即中止。
     */
    suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> =
        chatInternal(systemPrompt, userPrompt, stream = false, onDelta = null)

    /**
     * 流式对话：增量内容经 [onDelta] 回调（可能在 IO 线程），返回完整回答。
     * 协程取消时底层 HTTP 请求立即中止；[CancellationException] 向上传播，不吞掉。
     */
    suspend fun chatStreaming(
        systemPrompt: String,
        userPrompt: String,
        onDelta: (String) -> Unit,
    ): Result<String> = chatInternal(systemPrompt, userPrompt, stream = true, onDelta = onDelta)

    private suspend fun chatInternal(
        systemPrompt: String,
        userPrompt: String,
        stream: Boolean,
        onDelta: ((String) -> Unit)?,
    ): Result<String> = try {
        Result.success(withContext(ioDispatcher) {
            val ai = settings.ai.value
            if (!ai.enabled) error("AI 未启用，请在「我的 → AI 设置」中开启。")
            if (ai.baseUrl.isBlank()) error("AI 接口地址未配置。")
            if (!isNetworkAvailable()) error("当前无网络连接，请检查网络后重试。")
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
                stream = stream,
            )
            val req = Request.Builder()
                .url(url)
                .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
                .apply {
                    if (ai.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${ai.apiKey}")
                }
                .build()

            executeCancellable(req, stream, onDelta)
        })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 发起可取消请求：协程取消时调用 [Call.cancel] 立即中断底层连接。 */
    private suspend fun executeCancellable(
        request: Request,
        stream: Boolean,
        onDelta: ((String) -> Unit)?,
    ): String {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(classifyNetworkError(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isCancelled) {
                        response.close()
                        return
                    }
                    if (!response.isSuccessful) {
                        // 不把响应体带进错误消息（可能回显 API Key / 敏感内容），只分类。
                        response.close()
                        cont.resumeWithException(IllegalStateException(classifyHttpError(response.code)))
                        return
                    }
                    try {
                        val text = if (stream) {
                            parseSse(response, onDelta ?: { })
                        } else {
                            response.body?.string() ?: error("空响应")
                        }
                        if (!stream) {
                            val chatResp = json.decodeFromString<ChatResponse>(text)
                            cont.resume(chatResp.choices.firstOrNull()?.message?.content ?: error("AI 未返回内容"))
                        } else {
                            cont.resume(text)
                        }
                    } catch (e: Exception) {
                        if (cont.isCancelled) return
                        cont.resumeWithException(e)
                    } finally {
                        response.close()
                    }
                }
            })
        }
    }

    /** 解析 SSE 流：逐行读 `data: {...}`，增量经 [onDelta] 回调，返回拼好的完整文本。 */
    private fun parseSse(response: Response, onDelta: (String) -> Unit): String {
        val full = StringBuilder()
        response.body?.charStream()?.buffered()?.use { reader ->
            reader.forEachLine { raw ->
                val line = raw.trim()
                if (!line.startsWith("data:")) return@forEachLine
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") return@forEachLine
                val chunk = json.decodeFromString<ChatChunk>(data)
                val delta = chunk.choices.firstOrNull()?.delta?.content
                if (!delta.isNullOrEmpty()) {
                    full.append(delta)
                    onDelta(delta)
                }
            }
        }
        return full.toString()
    }

    /** HTTP 状态码 → 用户可读的错误分类（不携带响应体，避免敏感信息外泄）。 */
    private fun classifyHttpError(code: Int): String = when (code) {
        401, 403 -> "AI 接口鉴权失败（HTTP $code），请检查 API Key 与接口地址。"
        429 -> "AI 请求过于频繁（HTTP 429），请稍后重试或降低使用频率。"
        in 500..599 -> "AI 服务暂时不可用（HTTP $code），请稍后重试。"
        else -> "AI 服务返回错误（HTTP $code）。"
    }

    /** 网络异常 → 用户可读分类。 */
    private fun classifyNetworkError(e: IOException): Exception = when (e) {
        is SocketTimeoutException -> IllegalStateException("AI 请求超时，请检查接口地址与网络连接。", e)
        is UnknownHostException -> IllegalStateException("无法连接 AI 接口（域名解析失败），请检查网络。", e)
        is ConnectException -> IllegalStateException("无法连接 AI 接口，请检查接口地址与网络。", e)
        else -> IllegalStateException("AI 网络请求失败：${e.message ?: "未知错误"}", e)
    }

    /**
     * 无网预检：完全断网时立即返回可读错误，不必等连接超时。
     * 只判「有没有可用网络」而不判「是否真正联外网」（NET_CAPABILITY_INTERNET），
     * 局域网自建接口（无外网）不会被误拦。
     */
    private fun isNetworkAvailable(): Boolean {
        val ctx = appContext ?: return true
        return runCatching {
            val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != false
        }.getOrDefault(true)
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
