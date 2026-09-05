package com.creationreadingassistant.data.ai

import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.SettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * AiClient 直接单测（MockWebServer）：
 * - 成功响应解析与请求形态（路径 / Authorization）；
 * - 非 2xx 错误分支；
 * - 读超时分支（通过可注入的 OkHttpClient 使用极短超时）；
 * - 非加密 http:// 且非局域网目标的一次性警示状态；
 * - AI 未启用 / 未配置地址的前置校验。
 */
class AiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: SettingsStore
    private val aiState = MutableStateFlow(
        AISettings(enabled = true, baseUrl = "", model = "test-model", apiKey = "sk-test"),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = mockk {
            every { ai } returns aiState
        }
        aiState.value = AISettings(
            enabled = true,
            baseUrl = server.url("/").toString(),
            model = "test-model",
            apiKey = "sk-test",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** 测试专用客户端：极短超时，让超时分支可在毫秒级触发。 */
    private fun newClient(
        readTimeoutMs: Long = 500,
        connectTimeoutMs: Long = 500,
    ) = AiClient(
        settings = settings,
        ioDispatcher = Dispatchers.Unconfined,
        client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build(),
    )

    private fun chatJson(content: String = "你好，我是助手") = """
        {"choices":[{"index":0,"message":{"role":"assistant","content":"$content"}}]}
    """.trimIndent()

    @Test
    fun `chat returns content on successful response`() = runTest {
        server.enqueue(MockResponse().setBody(chatJson("总结结果")).setResponseCode(200))

        val result = newClient().chat("系统指令", "用户问题")

        assertTrue(result.isSuccess)
        assertEquals("总结结果", result.getOrThrow())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("系统指令"))
        assertTrue(body.contains("用户问题"))
    }

    @Test
    fun `chat sends non-stream request body`() = runTest {
        server.enqueue(MockResponse().setBody(chatJson()).setResponseCode(200))

        newClient().chat("s", "u")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `custom prompt is appended to system message for all calls`() = runTest {
        server.enqueue(MockResponse().setBody(chatJson()).setResponseCode(200))
        aiState.value = aiState.value.copy(prompt = "用轻松的语气说话")

        val result = newClient().chat("你是一个阅读助手。", "总结这段")

        assertTrue(result.isSuccess)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("个性化提示词应进入 system 消息", body.contains("用户的个性化要求（请遵守）：用轻松的语气说话"))
        assertTrue("原系统指令应保留", body.contains("你是一个阅读助手。"))
    }

    @Test
    fun `blank custom prompt does not alter system message`() = runTest {
        server.enqueue(MockResponse().setBody(chatJson()).setResponseCode(200))
        aiState.value = aiState.value.copy(prompt = "   ")

        newClient().chat("你是一个阅读助手。", "u")

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("个性化要求"))
    }

    @Test
    fun `chatStreaming parses sse deltas and returns full text`() = runTest {
        val sse = """
            data: {"choices":[{"delta":{"content":"你好"}}]}

            data: {"choices":[{"delta":{"content":"世界"}}]}

            data: {"choices":[{"delta":{"content":"。"}}]}

            data: [DONE]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(sse).setResponseCode(200))

        val deltas = mutableListOf<String>()
        val result = newClient().chatStreaming("s", "u") { deltas += it }

        assertTrue(result.isSuccess)
        assertEquals("你好世界。", result.getOrThrow())
        assertEquals(listOf("你好", "世界", "。"), deltas)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun `chatStreaming ignores non-delta chunks`() = runTest {
        val sse = """
            data: {"choices":[]}

            data: {"choices":[{"delta":{"role":"assistant"}}]}

            data: {"choices":[{"delta":{"content":"ok"}}]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(sse).setResponseCode(200))

        val deltas = mutableListOf<String>()
        val result = newClient().chatStreaming("s", "u") { deltas += it }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrThrow())
        assertEquals(listOf("ok"), deltas)
    }

    @Test
    fun `chat failure on 401 gives auth error classification`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":{"message":"bad key"}}""").setResponseCode(401))

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()!!.message!!
        assertTrue(msg.contains("鉴权失败"))
        // 响应体不进入错误消息（防敏感信息外泄）
        assertTrue(!msg.contains("bad key"))
    }

    @Test
    fun `chat failure on 429 gives rate limit classification`() = runTest {
        server.enqueue(MockResponse().setBody("rate limited").setResponseCode(429))

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("过于频繁"))
    }

    @Test
    fun `chat propagates cancellation when job cancelled`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(chatJson())
                .setResponseCode(200)
                .setBodyDelay(5, TimeUnit.SECONDS),
        )
        val client = newClient(readTimeoutMs = 5000)
        var thrown: Throwable? = null
        val job = launch {
            try {
                client.chat("s", "u")
            } catch (e: CancellationException) {
                thrown = e
            }
        }
        delay(50)
        job.cancelAndJoin()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `chat returns failure on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setBody("server exploded").setResponseCode(500))

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 500"))
    }

    @Test
    fun `chat returns failure on empty choices`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}""").setResponseCode(200))

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("未返回内容"))
    }

    @Test
    fun `chat fails fast when read timeout exceeded`() = runTest {
        // 服务端延迟 5s 才吐出响应体，客户端读超时只有 200ms
        server.enqueue(
            MockResponse()
                .setBody(chatJson())
                .setResponseCode(200)
                .setBodyDelay(5, TimeUnit.SECONDS),
        )

        val result = newClient(readTimeoutMs = 200).chat("s", "u")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `insecure http warning triggers for non-lan http host`() = runTest {
        // 非局域网 http 目标：警示状态先于请求结果生效（请求本身会失败，不影响断言）
        aiState.value = aiState.value.copy(baseUrl = "http://203.0.113.10:1")
        val client = newClient(connectTimeoutMs = 200, readTimeoutMs = 200)

        client.chat("s", "u")

        assertNotNull(client.insecureHttpWarning.value)
        assertTrue(client.insecureHttpWarning.value!!.contains("http"))
    }

    @Test
    fun `insecure http warning does not trigger for lan host`() = runTest {
        // 127.0.0.1 属局域网白名单：http 明文不警示
        server.enqueue(MockResponse().setBody(chatJson()).setResponseCode(200))
        val client = newClient()

        val result = client.chat("s", "u")

        assertTrue(result.isSuccess)
        assertNull(client.insecureHttpWarning.value)
    }

    @Test
    fun `https base url does not trigger warning`() = runTest {
        aiState.value = aiState.value.copy(baseUrl = "https://203.0.113.20:1")

        // 请求会失败（无真实服务），但足以走过 warnIfInsecureHttp
        val client = newClient(connectTimeoutMs = 200, readTimeoutMs = 200)
        client.chat("s", "u")

        assertNull(client.insecureHttpWarning.value)
    }

    @Test
    fun `chat fails when ai disabled`() = runTest {
        aiState.value = aiState.value.copy(enabled = false)

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("未启用"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `chat fails when base url blank`() = runTest {
        aiState.value = aiState.value.copy(baseUrl = "  ")

        val result = newClient().chat("s", "u")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("未配置"))
        assertEquals(0, server.requestCount)
    }
}
