package com.creationreadingassistant.feature.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * WebDavBackup 本体直接测试（P1-A7，此前只有编排层的 mock 间接覆盖）：
 * 用 MockWebServer 锁定真实 HTTP 行为（PUT/HEAD/GET/PROPFIND、Basic Auth）与
 * `requireSafeTarget` 安全约束（公网明文 http 拒绝、局域网 http 放行、坏协议拒绝）。
 */
class WebDavBackupTest {

    private lateinit var server: MockWebServer
    private val backup = WebDavBackup()
    private val user = "dav-user"
    private val pass = "dav-pass"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serverUrl(): String =
        // MockWebServer 监听 127.0.0.1，属局域网主机，明文 http 应被 requireSafeTarget 放行
        server.url("/dav/").toString()

    // ---- requireSafeTarget ----

    @Test
    fun `rejects public plain http with clear message`() = runTest {
        val result = backup.put(
            "http://dav.example.com/backup/", user, pass,
            "cra-backup-latest.json", "{}",
        )
        val e = result.exceptionOrNull()
        assertTrue("应因公网明文 http 被拒，实际：$e", e is IllegalArgumentException)
        assertTrue(e!!.message!!.contains("公网"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `rejects unsupported scheme`() = runTest {
        val result = backup.put("ftp://192.168.1.10/dav/", user, pass, "a.json", "{}")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("协议不受支持"))
    }

    @Test
    fun `rejects url without scheme`() = runTest {
        val result = backup.put("192.168.1.10/dav/", user, pass, "a.json", "{}")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("缺少协议"))
    }

    @Test
    fun `https passes safety gate and fails only on network`() = runTest {
        // 端口 1 的 https 必然连接失败；断言失败原因是网络错误而非安全拒绝，
        // 证明 https 不走 isLanHost 公网拦截。
        val result = backup.put("https://127.0.0.1:1/dav/", user, pass, "a.json", "{}")
        val e = result.exceptionOrNull()
        assertTrue("https 应通过安全门禁，实际异常：$e", e !is IllegalArgumentException)
    }

    @Test
    fun `lan plain http is allowed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        val result = backup.put(serverUrl(), user, pass, "a.json", "{}")
        assertTrue("局域网 http 应放行，实际：${result.exceptionOrNull()}", result.isSuccess)
    }

    // ---- put ----

    @Test
    fun `put sends json body with basic auth and reports server error`() = runTest {
        val body = """{"schemaVersion":1}"""
        server.enqueue(MockResponse().setResponseCode(201))
        val ok = backup.put(serverUrl(), user, pass, "cra-backup-latest.json", body)
        assertTrue(ok.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/dav/cra-backup-latest.json", recorded.path)
        assertEquals(okhttp3.Credentials.basic(user, pass), recorded.getHeader("Authorization"))
        assertEquals(body, recorded.body.readUtf8())

        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val failed = backup.put(serverUrl(), user, pass, "cra-backup-latest.json", body)
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("500"))
    }

    // ---- test（连接测试）----

    @Test
    fun `test distinguishes reachable auth failure and server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(backup.test(serverUrl(), user, pass).getOrDefault("").contains("连接成功"))

        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(backup.test(serverUrl(), user, pass).getOrDefault("").contains("凭据"))

        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(backup.test(serverUrl(), user, pass).isFailure)
    }

    // ---- get（下载恢复）----

    @Test
    fun `get downloads backup content with auth`() = runTest {
        val content = """{"schemaVersion":1,"books":[]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(content))
        val result = backup.get(serverUrl(), user, pass, "cra-backup-2026.json")
        assertEquals(content, result.getOrNull())

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/dav/cra-backup-2026.json", recorded.path)
        assertEquals(okhttp3.Credentials.basic(user, pass), recorded.getHeader("Authorization"))
    }

    @Test
    fun `get fails on missing file`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = backup.get(serverUrl(), user, pass, "missing.json")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("404"))
    }

    // ---- listBackups（PROPFIND）----

    @Test
    fun `listBackups parses propfind keeps json only and sorts newest first`() = runTest {
        // 生产解析器按本地名匹配（未启用 namespace-aware），测试语料用默认命名空间
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <multistatus xmlns="DAV:">
                  <response>
                    <href>/dav/cra-backup-latest.json</href>
                    <propstat><prop>
                      <displayname>cra-backup-latest.json</displayname>
                      <getcontentlength>1024</getcontentlength>
                      <getlastmodified>Mon, 10 Aug 2026 08:00:00 GMT</getlastmodified>
                    </prop></propstat>
                  </response>
                  <response>
                    <href>/dav/cra-backup-2026-08-09T100000Z.json</href>
                    <propstat><prop>
                      <displayname>cra-backup-2026-08-09T100000Z.json</displayname>
                      <getcontentlength>2048</getcontentlength>
                      <getlastmodified>Sat, 09 Aug 2026 10:00:00 GMT</getlastmodified>
                    </prop></propstat>
                  </response>
                  <response>
                    <href>/dav/notes.txt</href>
                    <propstat><prop>
                      <displayname>notes.txt</displayname>
                      <getcontentlength>10</getcontentlength>
                      <getlastmodified>Sun, 10 Aug 2026 09:00:00 GMT</getlastmodified>
                    </prop></propstat>
                  </response>
                </multistatus>
                """.trimIndent(),
            ),
        )
        val files = backup.listBackups(serverUrl(), user, pass).getOrThrow()

        assertEquals(listOf("cra-backup-latest.json", "cra-backup-2026-08-09T100000Z.json"), files.map { it.name })
        assertEquals(1024L, files[0].size)
        assertEquals("Mon, 10 Aug 2026 08:00:00 GMT", files[0].lastModified)

        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("1", recorded.getHeader("Depth"))
        assertEquals(okhttp3.Credentials.basic(user, pass), recorded.getHeader("Authorization"))
    }

    @Test
    fun `listBackups falls back to href name when displayname missing`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <multistatus xmlns="DAV:">
                  <response>
                    <href>/dav/no-name.json</href>
                    <propstat><prop>
                      <getcontentlength>1</getcontentlength>
                      <getlastmodified>Mon, 10 Aug 2026 08:00:00 GMT</getlastmodified>
                    </prop></propstat>
                  </response>
                </multistatus>
                """.trimIndent(),
            ),
        )
        val files = backup.listBackups(serverUrl(), user, pass).getOrThrow()
        assertEquals(listOf("no-name.json"), files.map { it.name })
    }

    @Test
    fun `listBackups returns empty on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(backup.listBackups(serverUrl(), user, pass).getOrThrow().isEmpty())
    }

    @Test
    fun `listBackups fails on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = backup.listBackups(serverUrl(), user, pass)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("PROPFIND"))
    }

    @Test
    fun `network outage surfaces as failure not crash`() = runTest {
        val port = server.port
        server.shutdown()
        val deadUrl = "http://127.0.0.1:$port/dav/"
        assertTrue(backup.put(deadUrl, user, pass, "a.json", "{}").isFailure)
        assertTrue(backup.test(deadUrl, user, pass).isFailure)
        assertTrue(backup.get(deadUrl, user, pass, "a.json").exceptionOrNull() is IOException)
    }
}
