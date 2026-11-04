package com.creationreadingassistant.feature.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * WebDAV 远程备份（P4）。
 *
 * 支持标准 WebDAV（坚果云 / Nextcloud / 群晖 NAS / Nginx-webdav / Alist）。
 * - 认证支持 Basic Auth（显式 UTF-8 字符集）。
 * - 支持 PROPFIND（带命名空间感知与前缀剥离，兼容坚果云 D: 前缀）。
 * - 连接测试优先采用 WebDAV 规范的 PROPFIND (Depth: 0) 探针，平滑兼容 OPTIONS。
 * - 支持 MKCOL 目录自动创建，解决 409 Conflict 与 404 Not Found。
 * - 路径自动进行标准 URL 编解码，杜绝中文文件名二次编码。
 */
@Singleton
class WebDavBackup @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun put(
        url: String,
        user: String,
        pass: String,
        filename: String,
        content: String,
    ): Result<Unit> = runCatching {
        requireSafeTarget(url)
        requireSafeFilename(filename)
        val target = resolveTarget(url, filename)
        withRetry {
            val body = content.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(target)
                .put(body)
                .header("Authorization", basicAuth(user, pass))
                .build()
            val firstResp = client.newCall(request).execute()
            var resp = firstResp
            // 若远程目录不存在，坚果云、Nextcloud 等服务器通常返回 409 Conflict 或 404 Not Found
            if (resp.code == 409 || resp.code == 404) {
                firstResp.close()
                // 尝试自动创建远程父目录集合（MKCOL）后重试一次
                ensureCollection(url, user, pass)
                resp = client.newCall(request).execute()
            }
            resp.use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException("WebDAV 返回 ${r.code}: ${r.body?.string()?.take(200)}")
                }
            }
        }
    }

    /**
     * 确保 WebDAV 目录集合已就绪。若远程尚未创建该目录，发送 MKCOL 请求创建。
     */
    suspend fun ensureCollection(url: String, user: String, pass: String): Result<Unit> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        val request = Request.Builder()
            .url(target)
            .method("MKCOL", null)
            .header("Authorization", basicAuth(user, pass))
            .build()
        client.newCall(request).execute().use { resp ->
            // 201 Created: 创建成功；405: 目录已存在，均视为集合就绪
            if (resp.isSuccessful || resp.code == 405 || resp.code == 200 || resp.code == 204) {
                return@runCatching
            }
        }
    }

    /**
     * 连接测试：使用 WebDAV 规范的 PROPFIND (Depth: 0) 探针，平滑回退 OPTIONS。
     * 避免因使用 HEAD 方法而被坚果云/Nextcloud 等服务器报 HTTP 405 拦截。
     */
    suspend fun test(url: String, user: String, pass: String): Result<String> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        withRetry(maxAttempts = 2) {
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop><D:resourcetype/></D:prop>
                |</D:propfind>
            """.trimMargin().toRequestBody("text/xml".toMediaType())
            val propfindRequest = Request.Builder()
                .url(target)
                .method("PROPFIND", propfindBody)
                .header("Authorization", basicAuth(user, pass))
                .header("Depth", "0")
                .build()
            var resp = client.newCall(propfindRequest).execute()
            // 若服务器对根目录禁用了 PROPFIND（返回 405/501），回退使用 OPTIONS 探测
            if (resp.code == 405 || resp.code == 501) {
                resp.close()
                val optionsRequest = Request.Builder()
                    .url(target)
                    .method("OPTIONS", null)
                    .header("Authorization", basicAuth(user, pass))
                    .build()
                resp = client.newCall(optionsRequest).execute()
            }
            resp.use { r ->
                when (r.code) {
                    in 200..299 -> "连接成功（HTTP ${r.code}）"
                    401, 403 -> "服务器可达，但凭据可能不正确（HTTP ${r.code}）"
                    404 -> "服务器可达，但目录尚未建立（HTTP 404），保存备份时将自动创建"
                    else -> throw IllegalStateException("连接测试返回 HTTP ${r.code}: ${r.body?.string()?.take(200)}")
                }
            }
        }
    }

    /** 下载恢复：GET 指定备份文件，返回其 JSON 文本。 */
    suspend fun get(url: String, user: String, pass: String, filename: String): Result<String> = runCatching {
        requireSafeTarget(url)
        requireSafeFilename(filename)
        val target = resolveTarget(url, filename)
        withRetry {
            val request = Request.Builder()
                .url(target)
                .get()
                .header("Authorization", basicAuth(user, pass))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("WebDAV 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                resp.body?.string() ?: throw IllegalStateException("空响应")
            }
        }
    }

    /**
     * 下载受限大小的二进制备份。
     *
     * 开源阅读的备份是 ZIP，不能借用 [get] 的 UTF-8 文本路径；这里同时对
     * Content-Length 和实际流式读取做上限校验，避免恶意或损坏 WebDAV 条目撑爆内存。
     */
    suspend fun getBytes(
        url: String,
        user: String,
        pass: String,
        filename: String,
        maxBytes: Long,
    ): Result<ByteArray> = runCatching {
        require(maxBytes > 0L) { "下载大小上限必须大于 0" }
        requireSafeTarget(url)
        requireSafeFilename(filename)
        val target = resolveTarget(url, filename)
        withRetry {
            val request = Request.Builder()
                .url(target)
                .get()
                .header("Authorization", basicAuth(user, pass))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("WebDAV 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val body = resp.body ?: throw IllegalStateException("空响应")
                if (body.contentLength() > maxBytes) {
                    throw IllegalArgumentException("备份文件超过 ${(maxBytes / 1024 / 1024)} MiB，已拒绝下载")
                }
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size().toLong() + count > maxBytes) {
                            throw IllegalArgumentException("备份文件超过 ${(maxBytes / 1024 / 1024)} MiB，已拒绝下载")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        }
    }

    data class BackupFile(
        val name: String,
        val size: Long,
        val lastModified: String,
    )

    /** 通过 PROPFIND 列出目录下所有 .json 备份文件。 */
    suspend fun listBackups(url: String, user: String, pass: String): Result<List<BackupFile>> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        withRetry {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop>
                |    <D:displayname/>
                |    <D:resourcetype/>
                |    <D:getcontentlength/>
                |    <D:getlastmodified/>
                |  </D:prop>
                |</D:propfind>
            """.trimMargin().toRequestBody("text/xml".toMediaType())
            val request = Request.Builder()
                .url(target)
                .method("PROPFIND", body)
                .header("Authorization", basicAuth(user, pass))
                .header("Depth", "1")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@withRetry emptyList()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("PROPFIND 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val xml = resp.body?.string() ?: throw IllegalStateException("空响应")
                parsePropFind(xml, baseUrl = target)
                    .filter { it.name.endsWith(".json", ignoreCase = true) }
                    // RFC1123 时间以英文星期缩写开头，字典序 ≠ 时间序；解析成 epoch 再排，失败按 0 兜底
                    .sortedWith(
                        compareByDescending<BackupFile> { parseHttpDateMillis(it.lastModified) }
                            .thenByDescending { it.lastModified },
                    )
            }
        }
    }

    /** 列出根目录的开源阅读自动备份归档（backup*.zip），不混入本应用 JSON 快照。 */
    suspend fun listLegadoBackups(url: String, user: String, pass: String): Result<List<BackupFile>> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        withRetry {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop>
                |    <D:displayname/>
                |    <D:resourcetype/>
                |    <D:getcontentlength/>
                |    <D:getlastmodified/>
                |  </D:prop>
                |</D:propfind>
            """.trimMargin().toRequestBody("text/xml".toMediaType())
            val request = Request.Builder()
                .url(target)
                .method("PROPFIND", body)
                .header("Authorization", basicAuth(user, pass))
                .header("Depth", "1")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@withRetry emptyList()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("PROPFIND 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val xml = resp.body?.string() ?: throw IllegalStateException("空响应")
                parsePropFind(xml, baseUrl = target)
                    .filter { it.name.startsWith("backup", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
                    .sortedWith(
                        compareByDescending<BackupFile> { parseHttpDateMillis(it.lastModified) }
                            .thenByDescending { it.lastModified },
                    )
            }
        }
    }

    /** 解析 `getlastmodified` 的 RFC1123 时间为 epoch millis；无法解析返回 0（视为最旧）。 */
    private fun parseHttpDateMillis(value: String): Long = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)).toEpochMilli()
    }.getOrDefault(0L)

    private fun resolveTarget(url: String, filename: String): HttpUrl {
        val base = if (url.endsWith("/")) url else "$url/"
        // addPathSegment 会正确编码中文/空格等合法文件名，且不会把文件名中的字符
        // 解释成 query 或额外路径；requireSafeFilename 已拒绝分隔符和 ..。
        return base.toHttpUrl().newBuilder().addPathSegment(filename).build()
    }

    private fun basicAuth(user: String, pass: String): String =
        Credentials.basic(user, pass, StandardCharsets.UTF_8)

    /**
     * 允许的 HTTP 主机判定：
     * 1. 私有 IPv4/IPv6、localhost、*.local
     * 2. 局域网常见无点单段主机名（如 nas, openwrt, router, raspberrypi）
     * 3. 局域网私有顶级域名（.lan, .home, .internal, .home.arpa）
     */
    private fun isPermittedHttpHost(host: String): Boolean {
        if (PairingManager.isLanHost(host)) return true
        if (!host.contains('.')) return true
        val lower = host.lowercase()
        return lower.endsWith(".lan") || lower.endsWith(".home") ||
            lower.endsWith(".internal") || lower.endsWith(".home.arpa")
    }

    /**
     * 拒绝经明文 HTTP 把 WebDAV 账号密码和整库导出发往公网。
     *
     * Basic Auth 只是 Base64，等同明文；配合应用全局放行的 cleartext，
     * 一个 `http://` 的公网 WebDAV 地址会让路径上任何人拿到密码和全部书库内容。
     * 公网一律要求 https；局域网自建服务（群晖/nginx-webdav/NAS）仍允许 http。
     */
    private fun requireSafeTarget(url: String) {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址无法解析：$url")
        when (val scheme = uri.scheme?.lowercase()) {
            "https" -> return
            "http" -> {
                val host = uri.host
                    ?: throw IllegalArgumentException("WebDAV 地址缺少主机名：$url")
                if (!isPermittedHttpHost(host)) {
                    throw IllegalArgumentException(
                        "已拒绝请求：$host 是公网地址，明文 HTTP 会泄露 WebDAV 账号密码与整库内容，请改用 https://。",
                    )
                }
            }
            null -> throw IllegalArgumentException("WebDAV 地址缺少协议，请以 https:// 开头")
            else -> throw IllegalArgumentException("WebDAV 地址协议不受支持：$scheme")
        }
    }

    /**
     * 文件名白名单：允许 Unicode（开源阅读可把设备名拼进 backup*.zip），但拒绝
     * 路径分隔符、控制字符和 .. 注入，避免服务器返回的恶意 displayname 把请求
     * 导向错误路径。
     */
    private fun requireSafeFilename(filename: String) {
        if (!FILENAME_RE.matches(filename) || filename.contains("..")) {
            throw IllegalArgumentException("非法备份文件名：$filename")
        }
    }

    private val FILENAME_RE = Regex("[^/\\\\\\u0000-\\u001F]{1,255}")

    /**
     * 解析 WebDAV PROPFIND XML 响应：
     * 1. 开启命名空间感知，并对标签名剥离前缀（彻底兼容坚果云 D:、Nextcloud d:、无前缀等所有实现）。
     * 2. 识别并过滤 collection 目录集合项，仅返回具体文件。
     * 3. 对 href 进行 URL 解码，防止中文文件名在列表展示乱码和下载时二次编码（%25）。
     */
    private fun parsePropFind(xml: String, baseUrl: String): List<BackupFile> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        val files = mutableListOf<BackupFile>()
        var inResponse = false
        var inProp = false
        var isCollection = false
        var href = ""
        var displayName = ""
        var size = 0L
        var lastModified = ""
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val rawName = parser.name ?: ""
            // 剥离可能存在的命名空间前缀并转为小写
            val name = rawName.substringAfterLast(":").lowercase()
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "response" -> {
                            inResponse = true
                            isCollection = false
                            href = ""
                            displayName = ""
                            size = 0L
                            lastModified = ""
                        }
                        "prop" -> inProp = true
                        "collection" -> isCollection = true
                        "href" -> if (inResponse && !inProp) href = parser.nextText()
                        "displayname" -> if (inProp) displayName = parser.nextText()
                        "getcontentlength" -> if (inProp) size = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> if (inProp) lastModified = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "response" -> {
                            inResponse = false
                            // 排除自身目录集合或子目录
                            if (!isCollection) {
                                val rawFileName = displayName.ifBlank {
                                    href.removeSuffix("/").substringAfterLast("/")
                                }
                                val fileName = runCatching {
                                    URLDecoder.decode(rawFileName, StandardCharsets.UTF_8.name())
                                }.getOrDefault(rawFileName)

                                if (fileName.isNotBlank()) {
                                    files.add(BackupFile(fileName, size, lastModified))
                                }
                            }
                        }
                        "prop" -> inProp = false
                    }
                }
            }
            eventType = parser.next()
        }
        return files
    }

    /**
     * 带全随机抖动（Full Jitter）的指数退避重试执行器。
     *
     * 专为弱网移动端环境设计：针对 TCP 闪断、DNS 瞬时解析失败、SocketTimeout 等 [IOException]
     * 网络层瞬态异常执行平滑退避重试，消除电梯/基站切换/瞬时弱网导致的备份悬空；
     * 对业务明确响应或非法参数（[IllegalArgumentException]）快速抛出，绝不浪费重试配额。
     */
    internal suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 300L,
        maxDelayMs: Long = 1500L,
        factor: Double = 2.0,
        block: suspend (attempt: Int) -> T,
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (e: Throwable) {
                lastException = e
                if (!isTransientNetworkError(e) || attempt >= maxAttempts) {
                    throw e
                }
                // Full Jitter: 50ms 到 currentDelay 均匀随机，避免并发雪崩
                val jitteredDelay = Random.nextLong(50L, currentDelay.coerceAtLeast(51L))
                delay(jitteredDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        throw lastException ?: IllegalStateException("WebDAV 重试耗尽")
    }

    private fun isTransientNetworkError(e: Throwable): Boolean {
        if (e is IllegalArgumentException || e is CancellationException) return false
        return when (e) {
            is SocketTimeoutException -> true
            is java.io.InterruptedIOException -> true
            is java.net.ConnectException, is java.net.NoRouteToHostException -> true
            is java.net.UnknownHostException -> true
            is java.io.EOFException -> true
            is IOException -> true
            else -> false
        }
    }
}
