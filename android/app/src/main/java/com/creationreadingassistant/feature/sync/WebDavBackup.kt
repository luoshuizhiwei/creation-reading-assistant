package com.creationreadingassistant.feature.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.nio.charset.StandardCharsets

/**
 * WebDAV 远程备份（P4，最简实现）。
 *
 * 通过 HTTP PUT 把整库导出 JSON 上传到 `{url}/{filename}`（Basic Auth）。
 * 多数 WebDAV 服务器（Nextcloud / 群晖 / nginx-webdav）接受直接 PUT 创建文件。
 * 后续若需列目录/增量，可在此叠加 PROPFIND/GET，本版先保证「一键备份」可用。
 */
@Singleton
class WebDavBackup @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
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
        val target = if (url.endsWith("/")) "$url$filename" else "$url/$filename"
        val body = content.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(target)
            .put(body)
            .header("Authorization", Credentials.basic(user, pass))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("WebDAV 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
            }
        }
    }

    /** 连接测试：对根目录做 HEAD 探测，区分「可达」「鉴权失败」「网络错误」。 */
    suspend fun test(url: String, user: String, pass: String): Result<String> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        val request = Request.Builder()
            .url(target)
            .head()
            .header("Authorization", Credentials.basic(user, pass))
            .build()
        client.newCall(request).execute().use { resp ->
            when (resp.code) {
                in 200..299 -> "连接成功（HTTP ${resp.code}）"
                401, 403 -> "服务器可达，但凭据可能不正确（HTTP ${resp.code}）"
                else -> throw IllegalStateException("连接测试返回 HTTP ${resp.code}")
            }
        }
    }

    /** 下载恢复：GET 指定备份文件，返回其 JSON 文本。 */
    suspend fun get(url: String, user: String, pass: String, filename: String): Result<String> = runCatching {
        requireSafeTarget(url)
        requireSafeFilename(filename)
        val target = resolveTarget(url, filename)
        val request = Request.Builder()
            .url(target)
            .get()
            .header("Authorization", Credentials.basic(user, pass))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("WebDAV 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
            }
            resp.body?.string() ?: throw IllegalStateException("空响应")
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
        val body = """<?xml version="1.0" encoding="utf-8"?>
            |<propfind xmlns="DAV:">
            |  <prop>
            |    <displayname/>
            |    <getcontentlength/>
            |    <getlastmodified/>
            |  </prop>
            |</propfind>
        """.trimMargin().toRequestBody("text/xml".toMediaType())
        val request = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .header("Authorization", Credentials.basic(user, pass))
            .header("Depth", "1")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@runCatching emptyList()
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

    /** 解析 `getlastmodified` 的 RFC1123 时间为 epoch millis；无法解析返回 0（视为最旧）。 */
    private fun parseHttpDateMillis(value: String): Long = runCatching {
        java.time.Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)).toEpochMilli()
    }.getOrDefault(0L)

    private fun resolveTarget(url: String, filename: String): String {
        val base = if (url.endsWith("/")) url else "$url/"
        return "$base$filename"
    }

    /**
     * 拒绝经明文 HTTP 把 WebDAV 账号密码和整库导出发往公网。
     *
     * Basic Auth 只是 Base64，等同明文；配合应用全局放行的 cleartext，
     * 一个 `http://` 的公网 WebDAV 地址会让路径上任何人拿到密码和全部书库内容。
     * 公网一律要求 https；局域网自建服务（群晖/nginx-webdav）仍允许 http，
     * 与配对使用同一套内网判定。
     */
    private fun requireSafeTarget(url: String) {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址无法解析：$url")
        when (val scheme = uri.scheme?.lowercase()) {
            "https" -> return
            "http" -> {
                val host = uri.host
                    ?: throw IllegalArgumentException("WebDAV 地址缺少主机名：$url")
                if (!PairingManager.isLanHost(host)) {
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
     * 文件名白名单：只允许普通文件名，拒绝含路径分隔符 / 或 .. 的注入，
     * 避免服务器返回的恶意 displayname 把请求导向错误路径。
     */
    private fun requireSafeFilename(filename: String) {
        if (!FILENAME_RE.matches(filename)) {
            throw IllegalArgumentException("非法备份文件名：$filename")
        }
    }

    private val FILENAME_RE = Regex("[A-Za-z0-9._-]+")

    private fun parsePropFind(xml: String, baseUrl: String, extension: String = ".json"): List<BackupFile> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        val files = mutableListOf<BackupFile>()
        var inResponse = false
        var inProp = false
        var href = ""
        var displayName = ""
        var size = 0L
        var lastModified = ""
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "response" -> { inResponse = true; href = ""; displayName = ""; size = 0L; lastModified = "" }
                        "prop" -> inProp = true
                        "href" -> if (inResponse) href = parser.nextText()
                        "displayname" -> if (inProp) displayName = parser.nextText()
                        "getcontentlength" -> if (inProp) size = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> if (inProp) lastModified = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "response" -> {
                            inResponse = false
                            val fileName = displayName.ifBlank { href.substringAfterLast("/") }
                            if (fileName.isNotBlank() && fileName.endsWith(extension, ignoreCase = true)) {
                                files.add(BackupFile(fileName, size, lastModified))
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
     * 开源阅读（Legado）备份是 ZIP 二进制，不能借用 [get] 的 UTF-8 文本路径。
     * 按 Content-Length 与实际流式读取做上限校验，避免恶意或损坏条目撑爆内存。
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
        val request = Request.Builder()
            .url(target)
            .get()
            .header("Authorization", basicAuth(user, pass))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("WebDAV 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
            }
            val declared = resp.header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > maxBytes) {
                throw IllegalStateException("备份体积 ${declared}B 超过上限 ${maxBytes}B")
            }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (bytes.size > maxBytes) {
                throw IllegalStateException("备份体积 ${bytes.size}B 超过上限 ${maxBytes}B")
            }
            bytes
        }
    }

    /** 列出根目录的开源阅读自动备份归档（backup*.zip），不混入本应用 JSON 快照。 */
    suspend fun listLegadoBackups(url: String, user: String, pass: String): Result<List<BackupFile>> = runCatching {
        requireSafeTarget(url)
        val target = if (url.endsWith("/")) url else "$url/"
        val body = """<?xml version="1.0" encoding="utf-8"?>
            |<D:propfind xmlns:D="DAV:">
            |  <D:prop>
            |    <D:displayname/>
            |    <D:resourcetype/>
            |    <D:getcontentlength/>
            |    <D:getlastmodified/>
            |  </D:prop>
            |</D:propfind>
        """.toRequestBody("text/xml".toMediaType())
        val request = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .header("Authorization", basicAuth(user, pass))
            .header("Depth", "1")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@runCatching emptyList()
            if (!resp.isSuccessful) {
                throw IllegalStateException("PROPFIND 返回 ${resp.code}: ${resp.body?.string()?.take(200)}")
            }
            val xml = resp.body?.string() ?: throw IllegalStateException("空响应")
            parsePropFind(xml, baseUrl = target, extension = ".zip")
                .filter { it.name.startsWith("backup", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<BackupFile> { parseHttpDateMillis(it.lastModified) }
                        .thenByDescending { it.lastModified },
                )
        }
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
}

private fun Instant.formatBackupDate(): String =
    DateTimeFormatter.ISO_INSTANT.format(this)
