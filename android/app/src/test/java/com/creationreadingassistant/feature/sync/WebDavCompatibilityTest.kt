package com.creationreadingassistant.feature.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R2 WebDAV 兼容性专项回归测试套件：
 * 覆盖：
 * 1. HEAD 探测成功（无回退，单次请求）；
 * 2. HEAD 405 平滑回退到 PROPFIND (Depth: 0) 成功；
 * 3. 认证失败（401/403）不得当成方法不支持，不发 PROPFIND / MKCOL；
 * 4. PUT 遇到 404/409 目标目录不存在时，仅对目标集合执行必要 MKCOL 并有限重试；
 * 5. MKCOL 遇到 403 权限不足、409 父目录缺失时立即终止，不盲目重试，不递归建目录；
 * 6. 重试达到上限（最多 1 次重试）时立即终止，杜绝无限循环；
 * 7. 中文与空格文件名正确编码，杜绝双重编码（无 %25）；
 * 8. 文件名安全检查防止目录穿越注入；
 * 9. 已交付的 getBytes 与 listLegadoBackups 接口回归保持。
 */
class WebDavCompatibilityTest {

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

    private fun serverUrl(path: String = "/dav/"): String =
        server.url(path).toString()

    // ---- 1. HEAD 探测与 405 回退测试 ----

    @Test
    fun testConnection_head200_success_singleRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("200"))

        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("HEAD", req.method)
    }

    @Test
    fun testConnection_head405_fallsBackToPropfindDepth0_success() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody("""<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:"/>""")
        )

        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        val msg = result.getOrThrow()
        assertTrue(msg.contains("207") || msg.contains("成功"))

        assertEquals(2, server.requestCount)
        val req1 = server.takeRequest()
        assertEquals("HEAD", req1.method)
        val req2 = server.takeRequest()
        assertEquals("PROPFIND", req2.method)
        assertEquals("0", req2.getHeader("Depth"))
    }

    @Test
    fun testConnection_headAuthFailure401_noFallbackToPropfind_returnsAuthWarning() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        val msg = result.getOrThrow()
        assertTrue("401 认证失败应提示凭据错误，实际：$msg", msg.contains("凭据可能不正确") && msg.contains("401"))

        // 严格断言：认证失败绝对不能发起 PROPFIND
        assertEquals(1, server.requestCount)
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun testConnection_headForbidden403_noFallbackToPropfind_returnsAuthWarning() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        val msg = result.getOrThrow()
        assertTrue("403 权限不足应提示凭据错误，实际：$msg", msg.contains("凭据可能不正确") && msg.contains("403"))

        assertEquals(1, server.requestCount)
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun testConnection_propfindAuthFailure401_whenHead405() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(401))

        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        val msg = result.getOrThrow()
        assertTrue("PROPFIND 401 应正确识别凭据错误，实际：$msg", msg.contains("凭据可能不正确") && msg.contains("401"))

        assertEquals(2, server.requestCount)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    // ---- 2. PUT 与 404/409 MKCOL 自动建连测试 ----

    @Test
    fun put_success_singleRequest_noRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = backup.put(serverUrl(), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isSuccess)

        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertTrue(req.path!!.endsWith("cra-backup.json"))
    }

    @Test
    fun put_authFailure401_noMkcol_noRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = backup.put(serverUrl(), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue("应返回 401 错误，实际：${e?.message}", e?.message?.contains("401") == true)

        // 严格断言：认证失败绝对不尝试 MKCOL，绝对不发起重试
        assertEquals(1, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun put_authForbidden403_noMkcol_noRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = backup.put(serverUrl(), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue("应返回 403 错误，实际：${e?.message}", e?.message?.contains("403") == true)

        assertEquals(1, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun put_missingDirectory409_createsCollectionWithMkcol_andRetriesPutSuccess() = runTest {
        // 1. 初次 PUT 目录不存在，返回 409
        server.enqueue(MockResponse().setResponseCode(409).setBody("Parent collection does not exist"))
        // 2. 客户端向配置目录发起 MKCOL，创建成功返回 201
        server.enqueue(MockResponse().setResponseCode(201))
        // 3. 客户端重试 PUT，成功返回 201
        server.enqueue(MockResponse().setResponseCode(201))

        val result = backup.put(serverUrl("/dav/mybackup/"), user, pass, "cra-backup.json", "{}")
        assertTrue("MKCOL 成功后 PUT 重试应成功，实际：$result", result.isSuccess)

        assertEquals(3, server.requestCount)
        val req1 = server.takeRequest()
        assertEquals("PUT", req1.method)
        assertEquals("/dav/mybackup/cra-backup.json", req1.path)

        val req2 = server.takeRequest()
        assertEquals("MKCOL", req2.method)
        assertEquals("/dav/mybackup/", req2.path)

        val req3 = server.takeRequest()
        assertEquals("PUT", req3.method)
        assertEquals("/dav/mybackup/cra-backup.json", req3.path)
    }

    @Test
    fun put_missingDirectory404_collectionAlreadyExistsOnMkcol405_andRetriesPutSuccess() = runTest {
        // 1. 初次 PUT 返回 404
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        // 2. MKCOL 返回 405 Method Not Allowed（RFC 4918: 集合已存在）
        server.enqueue(MockResponse().setResponseCode(405))
        // 3. 重试 PUT 成功
        server.enqueue(MockResponse().setResponseCode(200))

        val result = backup.put(serverUrl("/dav/mybackup/"), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isSuccess)

        assertEquals(3, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
    }

    // ---- 3. MKCOL 失败与重试上限保护（杜绝死循环） ----

    @Test
    fun put_mkcolForbidden403_abortsImmediately_noRetryPut() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("Conflict"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("Permission Denied"))

        val result = backup.put(serverUrl("/dav/folder/"), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue("MKCOL 403 应终止并抛出异常，实际：${e?.message}", e?.message?.contains("权限不足") == true || e?.message?.contains("403") == true)

        // 严格断言：MKCOL 失败后立即中止，不得发起第二次 PUT
        assertEquals(2, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
    }

    @Test
    fun put_mkcolParentMissing409_abortsImmediately_noRecursiveMkcol() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("Conflict"))
        // MKCOL 返回 409 表示多级父目录缺失，不得递归建目录
        server.enqueue(MockResponse().setResponseCode(409).setBody("Parent collection missing"))

        val result = backup.put(serverUrl("/dav/a/b/c/"), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue("上级目录缺失应终止，实际：${e?.message}", e?.message?.contains("上级目录不存在") == true || e?.message?.contains("409") == true)

        // 仅尝试对配置目标集合发一次 MKCOL，绝不递归创建未知路径
        assertEquals(2, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
        val mkcolReq = server.takeRequest()
        assertEquals("MKCOL", mkcolReq.method)
        assertEquals("/dav/a/b/c/", mkcolReq.path)
    }

    @Test
    fun put_retryPutStillFails_reachesMaxAttemptLimit_noInfiniteLoop() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("Conflict"))
        server.enqueue(MockResponse().setResponseCode(201)) // MKCOL 成功
        server.enqueue(MockResponse().setResponseCode(409).setBody("Still Conflict")) // 第二次 PUT 仍然失败

        val result = backup.put(serverUrl("/dav/folder/"), user, pass, "cra-backup.json", "{}")
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue(e?.message?.contains("重试返回 409") == true || e?.message?.contains("409") == true)

        // 严格断言总请求次数为 3（最多重试一次），绝不无限循环
        assertEquals(3, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
    }

    // ---- 4. 中文、空格文件名与 URL 编码（杜绝双重编码） ----

    @Test
    fun put_chineseAndSpaceFilename_properlyEncoded_noDoubleEncoding() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = backup.put(serverUrl(), user, pass, "备份 2026-10-01.json", "{\"test\":1}")
        assertTrue("含中文和空格的文件名应被正确支持，实际结果：$result", result.isSuccess)

        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        val path = req.path!!
        // 验证路径包含正确的百分号编码
        assertTrue("路径应包含 UTF-8 百分号编码，实际：$path", path.contains("%E5%A4%87%E4%BB%BD") && path.contains("%20"))
        // 严格断言不含双重编码（绝无 %25）
        assertFalse("路径绝不能包含二次编码 %25，实际：$path", path.contains("%25"))
    }

    @Test
    fun put_preEncodedUrl_doesNotDoubleEncode() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        // 传入的 URL 路径自身已经包含编码 %E6%96%B0%E5%BB%BA
        val preEncodedBase = serverUrl("/dav/%E6%96%B0%E5%BB%BA/")
        val result = backup.put(preEncodedBase, user, pass, "test.json", "{}")
        assertTrue(result.isSuccess)

        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        val path = req.path!!
        assertTrue("已有编码应原样保持，实际：$path", path.contains("/dav/%E6%96%B0%E5%BB%BA/test.json"))
        assertFalse("绝不能将 % 再次编码为 %25，实际：$path", path.contains("%25"))
    }

    @Test
    fun filenameValidation_rejectsDirectoryTraversal() = runTest {
        val dangerousNames = listOf(
            "../hack.json",
            "..\\hack.json",
            "foo/bar.json",
            "foo\\bar.json",
            "..",
            ".",
            " "
        )
        for (name in dangerousNames) {
            val result = backup.put(serverUrl(), user, pass, name, "{}")
            assertTrue("应拒绝危险文件名: $name", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("应抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }

    // ---- 红测反例验证（复核回归） ----

    @Test
    fun urlSecurity_rejectsUserInfoInUrl_toPreventHostConfusionAttack() = runTest {
        // 反例：URL 中包含 userinfo（如 127.0.0.1:80@public.example），必须在网络请求前被坚决拒绝
        val confusedUrls = listOf(
            "http://127.0.0.1:80@public.example/backups/",
            "http://user:pass@127.0.0.1:8080/dav/",
            "https://admin:secret@dav.example.com/dav/",
        )
        for (url in confusedUrls) {
            val result = backup.test(url, user, pass)
            assertTrue("应在网络请求前拒绝带 userinfo 的 URL: $url", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
            assertTrue("错误信息必须明确提示不允许 userinfo，实际：${e?.message}", e?.message?.contains("userinfo") == true || e?.message?.contains("用户信息") == true)
        }
        // 严格断言：受控本地 server 没有收到任何请求（网络前置阻断）
        assertEquals(0, server.requestCount)
    }

    @Test
    fun urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract() = runTest {
        // 既有 PairingManager 合同不放宽无点单段主机名或 .lan/.home 等私有顶级域
        val disallowedHttpUrls = listOf(
            "http://nas/dav/",
            "http://openwrt/dav/",
            "http://router.lan/dav/",
            "http://server.home/dav/",
            "http://mybox.internal/dav/",
        )
        for (url in disallowedHttpUrls) {
            val result = backup.test(url, user, pass)
            assertTrue("根据 PairingManager 合同，应拒绝非合法 LAN 主机的明文 HTTP: $url", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
            assertTrue("错误信息必须提示公网或改用 https，实际：${e?.message}", e?.message?.contains("公网地址") == true || e?.message?.contains("https") == true)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun filenameValidation_rejectsControlCharacters() = runTest {
        // 控制字符（\t, \n, \r 等）必须拒绝，严禁 trim 后放过，严禁正则 \\s 匹配
        val controlCharNames = listOf(
            "\tbackup.json",
            "backup.json\t",
            "\nbackup.json",
            "backup.json\n",
            "\rbackup.json",
            "backup.json\r",
            "back\tup.json",
            "back\nup.json",
            "back\rup.json",
        )
        for (name in controlCharNames) {
            val result = backup.put(serverUrl(), user, pass, name, "{}")
            assertTrue("应拒绝含控制字符的文件名: '$name'", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun urlSecurity_rejectsPlaintextHttpForPublicDomainAndPublicIp() = runTest {
        val publicUrls = listOf(
            "http://public.example/dav/",
            "http://dav.jianguoyun.com/dav/",
            "http://8.8.8.8/dav/",
            "http://1.1.1.1:8080/dav/",
        )
        for (url in publicUrls) {
            val result = backup.test(url, user, pass)
            assertTrue("应在网络请求前拒绝公网明文 HTTP: $url", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
            assertTrue("错误信息必须提示公网地址与 https，实际：${e?.message}", e?.message?.contains("公网地址") == true)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun urlSecurity_allowsLegitimateLanAndHttpsWithCustomPort() = runTest {
        // 1. 合法内网 IPv4 与 localhost 走通受控网络请求
        server.enqueue(MockResponse().setResponseCode(200))
        val lanResult = backup.test("http://127.0.0.1:${server.port}/dav/", user, pass)
        assertTrue("合法 127.0.0.1 明文 HTTP 应被放行并通过测试，实际：$lanResult", lanResult.isSuccess)

        server.enqueue(MockResponse().setResponseCode(200))
        val localhostResult = backup.test("http://localhost:${server.port}/dav/", user, pass)
        assertTrue("合法 localhost 明文 HTTP 应被放行并通过测试，实际：$localhostResult", localhostResult.isSuccess)

        assertEquals(2, server.requestCount)
    }

    @Test
    fun filenameValidation_acceptsValidChineseAndSpaces_rejectsWhitespaceEdgeCases() = runTest {
        // 1. 合法中文、空格与符号文件名
        server.enqueue(MockResponse().setResponseCode(201))
        val validResult = backup.put(serverUrl(), user, pass, "2026-10-01 创作阅读助手 (备份) [全量].json", "{}")
        assertTrue("合法中文及空格文件名应成功，实际：$validResult", validResult.isSuccess)

        // 2. 首尾空格文件名应被严格拒绝，且不发起网络请求
        val spaceEdgeCases = listOf(
            " backup.json",
            "backup.json ",
            " ",
            "   ",
        )
        for (name in spaceEdgeCases) {
            val result = backup.put(serverUrl(), user, pass, name, "{}")
            assertTrue("首尾空格文件名应被拒绝: '$name'", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际：$e", e is IllegalArgumentException)
        }
        // 仅有第 1 次合法 PUT 产生了网络请求
        assertEquals(1, server.requestCount)
    }

    @Test
    fun filenameValidation_enforcesCurrentAllowedSetAndRejectsDisallowedPunctuation() = runTest {
        // 1. 当前合同允许集边界：中文、数字、允许符号（点、下划线、短横线、中间半角空格、英文括号、中文括号、英文方括号）
        val allowedCases = listOf(
            "中文备份.json",
            "20261012.json",
            "backup.1.2.3.json",
            "my_backup_file.json",
            "cra-backup-file.json",
            "cra backup file.json",
            "backup(1).json",
            "备份（全量）.json",
            "backup[0].json",
            "2026-10-12 创作阅读助手 (备份) [全量] v1.0_final.json",
        )
        for (name in allowedCases) {
            server.enqueue(MockResponse().setResponseCode(201))
            val result = backup.put(serverUrl(), user, pass, name, "{}")
            assertTrue("当前允许字符集边界应通过校验: '$name'，实际结果: $result", result.isSuccess)
        }

        // 2. 当前合同明确拒绝的特殊字符边界：+ @ # % , ~ !
        val disallowedPunctuationCases = listOf(
            "backup+1.json",
            "backup@home.json",
            "backup#1.json",
            "backup%20.json",
            "backup,v1.json",
            "backup~temp.json",
            "backup!important.json",
        )
        for (name in disallowedPunctuationCases) {
            val result = backup.put(serverUrl(), user, pass, name, "{}")
            assertTrue("当前合同拒绝特殊字符应被拦截: '$name'", result.isFailure)
            val e = result.exceptionOrNull()
            assertTrue("必须抛出 IllegalArgumentException，实际: $e", e is IllegalArgumentException)
            assertTrue("异常信息应提示非法备份文件名，实际: ${e?.message}", e?.message?.contains("非法备份文件名") == true)
        }

        // 仅允许集用例产生了网络请求，拒绝字符在前置安全检查即被拦截
        assertEquals(allowedCases.size, server.requestCount)
    }

    // ---- 5. 已交付接口回归保持 ----



    @Test
    fun getBytes_success_and_oversizeFailure() = runTest {
        // 成功下载
        val bytesData = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // ZIP 魔数
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytesData)))

        val successResult = backup.getBytes(serverUrl(), user, pass, "backup.zip", maxBytes = 1024)
        assertTrue(successResult.isSuccess)
        assertEquals(4, successResult.getOrThrow().size)

        // 超限下载
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "2048")
                .setBody(okio.Buffer().write(ByteArray(2048)))
        )
        val failResult = backup.getBytes(serverUrl(), user, pass, "large.zip", maxBytes = 100)
        assertTrue(failResult.isFailure)
    }

    @Test
    fun listLegadoBackups_filtersOnlyZipAndSorts() = runTest {
        val propfindXml = """<?xml version="1.0" encoding="utf-8"?>
            |<D:multistatus xmlns:D="DAV:">
            |  <D:response>
            |    <D:href>/dav/backup_old.zip</D:href>
            |    <D:propstat><D:prop>
            |      <D:displayname>backup_old.zip</D:displayname>
            |      <D:getcontentlength>100</D:getcontentlength>
            |      <D:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</D:getlastmodified>
            |    </D:prop></D:propstat>
            |  </D:response>
            |  <D:response>
            |    <D:href>/dav/backup_new.zip</D:href>
            |    <D:propstat><D:prop>
            |      <D:displayname>backup_new.zip</D:displayname>
            |      <D:getcontentlength>200</D:getcontentlength>
            |      <D:getlastmodified>Tue, 02 Jan 2024 00:00:00 GMT</D:getlastmodified>
            |    </D:prop></D:propstat>
            |  </D:response>
            |  <D:response>
            |    <D:href>/dav/cra-backup.json</D:href>
            |    <D:propstat><D:prop>
            |      <D:displayname>cra-backup.json</D:displayname>
            |      <D:getcontentlength>300</D:getcontentlength>
            |      <D:getlastmodified>Wed, 03 Jan 2024 00:00:00 GMT</D:getlastmodified>
            |    </D:prop></D:propstat>
            |  </D:response>
            |</D:multistatus>
        """.trimMargin()
        server.enqueue(MockResponse().setResponseCode(207).setBody(propfindXml))

        val result = backup.listLegadoBackups(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        // 严格仅保留 backup*.zip，不包含 .json
        assertEquals(2, list.size)
        // 且按时间降序排列
        assertEquals("backup_new.zip", list[0].name)
        assertEquals("backup_old.zip", list[1].name)
    }

    @Test fun mergedUnsupportedHeadAndPropfindUsesOptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(501))
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(backup.test(serverUrl(), user, pass).isSuccess)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
        assertEquals("OPTIONS", server.takeRequest().method)
        assertEquals(3, server.requestCount)
    }
    @Test fun mergedMissingDirectoryIsReachableWithoutCreatingIt() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess); assertTrue(result.getOrThrow().contains("目录尚未建立"))
        assertEquals(1, server.requestCount)
    }
    @Test fun mergedUnicodeCredentialsUseUtf8() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(backup.get(serverUrl(), "测试用户", "test-password", "backup.json").isSuccess)
        assertEquals(okhttp3.Credentials.basic("测试用户", "test-password", Charsets.UTF_8), server.takeRequest().getHeader("Authorization"))
    }
}
