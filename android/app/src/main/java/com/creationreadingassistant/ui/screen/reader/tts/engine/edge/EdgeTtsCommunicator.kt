package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Headers
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Edge TTS HTTP 流式合成器（非官方协议骨架）。
 *
 * ⚠️ 注意：微软未公开 Edge 浏览器读 aloud 的稳定服务端点，这里实现的是社区逆向的
 *   常用端点 + SSML 拼接 + MP3 流式写入。服务端随时可能变更（鉴权、路径、频率限制），
 *   因此 **任何一步失败都必须抛出异常**，由 [EdgeTtsEngine] 捕获并自动回退到系统 TTS，
 *   绝不让用户卡在"神经语音加载中"。
 *
 * 协议参考（rany2/edge-tts 开源项目社区总结）：
 * - Endpoint: https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1
 * - Query: ?TrustedClientToken={token}&ConnectionId={uuid}
 * - Header: Content-Type: application/ssml+xml
 * - Body: <speak version="1.0" ...> 标准 SSML
 * - 响应 content-type: audio/mpeg （chunked streaming）
 *
 * 当前实现**不**内置固定 TrustedClientToken（硬编码会过期）。首次请求若被 401/403 拒绝，
 * 即视为失败 → 立即回退 System 引擎。后续可通过升级 App 跟进新鉴权方式。
 */
internal class EdgeTtsCommunicator(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
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
        // 0) 网络可用性预检：无网直接抛，不走 HTTP 浪费 10s 超时
        assertNetworkAvailable()

        // 1) 拼 SSML
        val ssml = buildSsml(req)

        // 2) 准备请求
        val connId = UUID.randomUUID().toString().replace("-", "")
        val url = buildString {
            append(ENDPOINT)
            append("?Retry-After=200")
            append("&ConnectionId=").append(connId)
        }
        val request = Request.Builder()
            .url(url)
            .headers(
                Headers.headersOf(
                    "User-Agent", UA,
                    "Accept", "*/*",
                    "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                    "Content-Type", "application/ssml+xml",
                    "X-Microsoft-OutputFormat", "audio-24khz-48kbit-rate-mono-mp3",
                    "X-ConnectionId", connId,
                )
            )
            .post(ssml.toRequestBody(CT_SSML))
            .build()

        // 3) 执行 + 流式落盘
        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string()?.take(200) }.getOrDefault("")
                throw RuntimeException("EdgeTTS HTTP ${resp.code}: $body")
            }
            val src = resp.body?.byteStream()
                ?: throw RuntimeException("EdgeTTS: empty response body")
            FileOutputStream(dstFile).use { out ->
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.flush()
                if (total < 64) throw RuntimeException("EdgeTts: audio too short, likely bad response")
                return@use total
            }
        }
    }

    // ── 内部辅助 ─────────────────────────────────────────────

    private fun assertNetworkAvailable() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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
        val pitchHz = ((req.pitch - 1f) * 50).toInt().let {
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
                .append("\" pitch=\"").append(pitchHz)
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
        private const val ENDPOINT =
            "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
        private val CT_SSML = "application/ssml+xml; charset=utf-8".toMediaType()

        private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)  // 长句合成耗时
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
