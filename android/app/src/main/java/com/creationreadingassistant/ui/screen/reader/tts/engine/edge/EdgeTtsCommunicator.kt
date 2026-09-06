package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Edge TTS 合成器（官方 WebSocket 协议，社区逆向：rany2/edge-tts）。
 *
 * ## 协议（2026-06 现状）
 *
 * - Endpoint: `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1`
 *   （旧的 HTTP POST 直返音频形态已被微软退役，一律 404）
 * - Query: `TrustedClientToken` + `Sec-MS-GEC`（2024-06 起强制 DRM 令牌，见 [EdgeTtsDrm]，
 *   缺失/过期一律 403）+ `Sec-MS-GEC-Version` + `ConnectionId`
 * - 连接后先发 `Path:speech.config` 文本帧，再发 `Path:ssml` 文本帧；
 *   二进制帧 = 2 字节大端头长 + 头串（含 `Path:audio` 等）+ 载荷；
 *   `Path:turn.end` 文本帧表示本次合成结束。
 *
 * ## 失败纪律
 *
 * 任何一步失败（连接/握手/超时/空音频）都抛异常，由 [EdgeTtsEngine] 捕获并自动
 * 回退到系统 TTS，绝不让用户卡在「神经语音加载中」。
 *
 * @param wsEndpointBase 可注入的 WS 端点（scheme+host+path，不含 query）；
 *   仅测试注入 MockWebServer 使用。
 */
internal class EdgeTtsCommunicator(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val wsEndpointBase: String = WS_ENDPOINT_BASE,
    /** 网络可用性探针；测试注入空实现（JVM 无 ConnectivityManager），生产走真实检查。 */
    internal var networkProbe: (() -> Unit)? = null,
) {

    data class SynthesisRequest(
        val voiceId: String,
        val text: String,
        val rate: Float,       // 0.5f..2.0f
        val pitch: Float,      // 0.5f..2.0f
        val volume: Float,     // 0f..1f
    )

    /**
     * 合成单句音频并写入 [dstFile]。
     * @return 写入字节数；失败一律抛异常（由外层决定回退策略）。
     */
    suspend fun synthesizeToFile(
        req: SynthesisRequest,
        dstFile: File,
    ): Long = withContext(Dispatchers.IO) {
        // 0) 网络可用性预检：无网直接抛，不做无效等待
        probeNetwork()

        val connId = UUID.randomUUID().toString().replace("-", "")
        val gec = EdgeTtsDrm.secMsGecToken(EdgeTtsDrm.nowUnixSeconds())
        val url = "$wsEndpointBase" +
            "?TrustedClientToken=${EdgeTtsDrm.TRUSTED_CLIENT_TOKEN}" +
            "&Sec-MS-GEC=$gec" +
            "&Sec-MS-GEC-Version=${EdgeTtsDrm.SEC_MS_GEC_VERSION}" +
            "&ConnectionId=$connId"

        val audioBuffer = java.io.ByteArrayOutputStream()
        val completion = CompletableDeferred<Unit>()
        var gotTurnEnd = false

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Origin", ORIGIN)
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .build()

        val ws = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 帧序固定：先 speech.config，再 ssml
                    val ts = "%1\$tFT%1\$tT.%1\$tL".format(java.util.Date())
                    val tsZ = "${ts}Z"
                    webSocket.send(
                        "X-Timestamp:$ts\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                            "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}",
                    )
                    val reqId = UUID.randomUUID().toString().replace("-", "")
                    webSocket.send(
                        "X-RequestId:$reqId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:$tsZ\r\n" +
                            "Path:ssml\r\n\r\n" +
                            buildSsml(req),
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        gotTurnEnd = true
                        completion.complete(Unit)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size < 2) return
                    val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    if (data.size < 2 + headerLen) return
                    val header = String(data, 2, headerLen, Charsets.UTF_8)
                    // 仅收音频帧；audio.metadata（词边界等元数据）跳过
                    if (header.contains("Path:audio\r\n")) {
                        audioBuffer.write(data, 2 + headerLen, data.size - 2 - headerLen)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!completion.isCompleted) {
                        completion.completeExceptionally(
                            RuntimeException(
                                "EdgeTts ws failure: ${t.message ?: t::class.java.simpleName}",
                                t,
                            ),
                        )
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!completion.isCompleted) completion.complete(Unit)
                }
            },
        )

        try {
            withTimeout(WS_TIMEOUT_MS) { completion.await() }
        } catch (e: TimeoutCancellationException) {
            AppLog.debug("EdgeTts", "ws synthesis timeout after ${WS_TIMEOUT_MS}ms")
            throw RuntimeException("EdgeTts: synthesis timeout (${WS_TIMEOUT_MS}ms)")
        } finally {
            ws.cancel()
        }

        val audio = audioBuffer.toByteArray()
        if (!gotTurnEnd || audio.size < 64) {
            throw RuntimeException(
                "EdgeTts: audio too short (${audio.size}B, turnEnd=$gotTurnEnd), likely rejected",
            )
        }
        FileOutputStream(dstFile).use { out ->
            out.write(audio)
            out.flush()
        }
        audio.size.toLong()
    }

    // ── 内部辅助 ─────────────────────────────────────────────

    private fun probeNetwork() {
        val probe = networkProbe
        if (probe != null) {
            probe()
            return
        }
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: throw RuntimeException("无网络连接（ConnectivityManager 不可用）")
        val act = cm.activeNetwork ?: throw RuntimeException("当前未连接网络，请开启 Wi-Fi 或移动数据后重试。")
        val caps = cm.getNetworkCapabilities(act)
            ?: throw RuntimeException("当前未连接网络，请开启 Wi-Fi 或移动数据后重试。")
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            throw RuntimeException("当前网络无法访问互联网。")
        }
        // minSdk = 24 > M (23)，NET_CAPABILITY_VALIDATED 恒可用；
        // 允许 NOT_VALIDATED（VPN/内网代理场景），只要求 NET_CAPABILITY_INTERNET。
        // 空 if 保留以明确表达「允许未校验网络」的策略意图。
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) { }
    }

    private fun buildSsml(req: SynthesisRequest): String {
        // rate/pitch/volume 映射为百分比字符串（0% = 基准）
        val ratePct = ((req.rate - 1f) * 100).toInt().let { "${if (it >= 0) "+" else ""}$it%" }
        val pitchPct = ((req.pitch - 1f) * 50).toInt().let {
            // Edge 神经语音用 Hz 偏移 + 百分比都接受；选百分比兼容性最好
            "${if (it >= 0) "+" else ""}$it%"
        }
        val volPct = (req.volume * 100).toInt().let { "${it}%" }
        val safeText = xmlEscape(req.text)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"https://www.w3.org/2001/mstts\" xml:lang=\"en-US\">")
            append("<voice name=\"").append(xmlEscape(req.voiceId)).append("\">")
            append("<prosody rate=\"").append(ratePct)
                .append("\" pitch=\"").append(pitchPct)
                .append("\" volume=\"").append(volPct).append("\">")
            append(safeText)
            append("</prosody>")
            append("</voice>")
            append("</speak>")
        }
    }

    private fun xmlEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private companion object {
        /** 官方 WebSocket 端点（旧 HTTP POST 形态已退役，一律 404）。 */
        private const val WS_ENDPOINT_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"

        /** 单句合成的整体上限：覆盖握手 + 合成 + 收流；超时抛异常走回退。 */
        private const val WS_TIMEOUT_MS = 30_000L

        /** Edge 浏览器读 aloud 的扩展 Origin（官方扩展 ID，来自 edge-tts 上游）。 */
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"

        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/${EdgeTtsDrm.CHROMIUM_MAJOR_VERSION}.0.0.0 " +
                "Safari/537.36 Edg/${EdgeTtsDrm.CHROMIUM_MAJOR_VERSION}.0.0.0"

        private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)  // 长句合成耗时
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
