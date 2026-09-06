package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * EdgeTtsCommunicator WebSocket 协议契约：
 * - 请求 query 必须携带 TrustedClientToken / Sec-MS-GEC / Sec-MS-GEC-Version / ConnectionId；
 * - 连接后先发 Path:speech.config 再发 Path:ssml；
 * - 二进制帧（2 字节大端头长 + 头串 + 载荷）中仅 Path:audio 载荷写入音频；
 * - Path:turn.end 结束；无音频/未 turn.end 视为失败。
 */
class EdgeTtsCommunicatorTest {

    private lateinit var server: MockWebServer
    private lateinit var communicator: EdgeTtsCommunicator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        communicator = EdgeTtsCommunicator(
            context = null,
            okHttpClient = OkHttpClient(),
            wsEndpointBase = server.url("/consumer/speech/synthesize/readaloud/edge/v1").toString()
                .trimEnd('/'),
            networkProbe = {},
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun audioFrame(path: String, payload: ByteArray): ByteArray {
        val header = "X-RequestId:test\r\nPath:$path\r\n\r\n".toByteArray(Charsets.UTF_8)
        val out = ByteArray(2 + header.size + payload.size)
        out[0] = ((header.size shr 8) and 0xFF).toByte()
        out[1] = (header.size and 0xFF).toByte()
        header.copyInto(out, 2)
        payload.copyInto(out, 2 + header.size)
        return out
    }

    @Test
    fun `协议握手帧序与音频解析`() {
        val fakeMp3 = ByteArray(128) { it.toByte() }
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                private var configSeen = false

                override fun onMessage(webSocket: WebSocket, text: String) {
                    when {
                        text.contains("Path:speech.config") -> configSeen = true
                        text.contains("Path:ssml") -> {
                            assertTrue("必须先发 speech.config 再发 ssml", configSeen)
                            assertTrue("ssml 帧必须携带正文", text.contains("你好"))
                            // 回发音频帧（含一个应被忽略的 metadata 帧）+ turn.end
                            webSocket.send(
                                okio.ByteString.of(*audioFrame("audio.metadata", ByteArray(8))),
                            )
                            webSocket.send(okio.ByteString.of(*audioFrame("audio", fakeMp3)))
                            webSocket.send("X-RequestId:t\r\nPath:turn.end\r\n\r\n")
                        }
                    }
                }
            }),
        )

        val dst = File.createTempFile("edge-tts-test", ".mp3")
        val written = runBlocking {
            communicator.synthesizeToFile(
                EdgeTtsCommunicator.SynthesisRequest(
                    voiceId = "zh-CN-XiaoxiaoNeural",
                    text = "你好",
                    rate = 1f,
                    pitch = 1f,
                    volume = 1f,
                ),
                dst,
            )
        }

        assertEquals(fakeMp3.size.toLong(), written)
        assertTrue(dst.readBytes().contentEquals(fakeMp3))

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        val path = recorded!!.path.orEmpty()
        assertTrue("query 必须带 TrustedClientToken", path.contains("TrustedClientToken=6A5AA1D4"))
        assertTrue("query 必须带 Sec-MS-GEC", path.contains("Sec-MS-GEC="))
        assertTrue("query 必须带 Sec-MS-GEC-Version", path.contains("Sec-MS-GEC-Version="))
        assertTrue("query 必须带 ConnectionId", path.contains("ConnectionId="))
        assertEquals(
            "Edge 端点要求扩展 Origin",
            "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
            recorded.getHeader("Origin"),
        )
    }

    @Test
    fun `服务端异常关闭视为合成失败`() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(1001, "boom")
                }
            }),
        )
        val dst = File.createTempFile("edge-tts-test", ".mp3")
        val result = runCatching {
            runBlocking {
                communicator.synthesizeToFile(
                    EdgeTtsCommunicator.SynthesisRequest("v", "text", 1f, 1f, 1f),
                    dst,
                )
            }
        }
        assertTrue("无 turn.end 无音频必须抛异常走回退", result.isFailure)
    }
}
