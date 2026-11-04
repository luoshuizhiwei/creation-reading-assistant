package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReaderCorrectionDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `put automatically creates collection with MKCOL on 409 and retries`() = runTest {
        val body = """{"test":1}"""
        server.enqueue(MockResponse().setResponseCode(409).setBody("Conflict"))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))

        val result = backup.put(serverUrl(), user, pass, "cra-backup-latest.json", body)
        assertTrue(result.isSuccess)

        val r1 = server.takeRequest()
        assertEquals("PUT", r1.method)
        val r2 = server.takeRequest()
        assertEquals("MKCOL", r2.method)
        val r3 = server.takeRequest()
        assertEquals("PUT", r3.method)
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

    @Test
    fun `test fallback to OPTIONS on 405 Method Not Allowed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(200))
        val result = backup.test(serverUrl(), user, pass)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.contains("200"))

        val req1 = server.takeRequest()
        assertEquals("HEAD", req1.method)

        val req2 = server.takeRequest()
        assertEquals("PROPFIND", req2.method)
        assertEquals("0", req2.getHeader("Depth"))

        val req3 = server.takeRequest()
        assertEquals("OPTIONS", req3.method)
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

    @Test
    fun `getBytes encodes unicode backup name and enforces declared size limit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("zip-bytes"))
        val bytes = backup.getBytes(serverUrl(), user, pass, "backup2026-09-14-手机.zip", 1024).getOrThrow()
        assertEquals("zip-bytes", bytes.toString(Charsets.UTF_8))
        assertEquals("/dav/backup2026-09-14-%E6%89%8B%E6%9C%BA.zip", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "2048"))
        val tooLarge = backup.getBytes(serverUrl(), user, pass, "backup.zip", 1024)
        assertTrue(tooLarge.isFailure)
        assertTrue(tooLarge.exceptionOrNull()!!.message!!.contains("超过"))
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
    fun `listBackups parses D prefixed namespace and decodes urlencoded href and ignores collection`() = runTest {
        // 坚果云等真实服务端格式：命名空间带 D: 前缀，根集合自身和子目录带有 collection 标记，非 ASCII 文件名在 href 中被 urlencode
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/</D:href>
                    <D:propstat><D:prop>
                      <D:resourcetype><D:collection/></D:resourcetype>
                    </D:prop></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/%E6%B5%8B%E8%AF%95-backup.json</D:href>
                    <D:propstat><D:prop>
                      <D:resourcetype/>
                      <D:getcontentlength>512</D:getcontentlength>
                      <D:getlastmodified>Tue, 11 Aug 2026 10:00:00 GMT</D:getlastmodified>
                    </D:prop></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/subdir/</D:href>
                    <D:propstat><D:prop>
                      <D:displayname>subdir</D:displayname>
                      <D:resourcetype><D:collection/></D:resourcetype>
                    </D:prop></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            ),
        )
        val files = backup.listBackups(serverUrl(), user, pass).getOrThrow()
        assertEquals(1, files.size)
        assertEquals("测试-backup.json", files[0].name)
        assertEquals(512L, files[0].size)
        assertEquals("Tue, 11 Aug 2026 10:00:00 GMT", files[0].lastModified)
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
    fun `listLegadoBackups keeps only root backup zip archives`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <multistatus xmlns="DAV:">
                  <response><href>/dav/backup2026-09-13.zip</href><propstat><prop>
                    <displayname>backup2026-09-13.zip</displayname><getcontentlength>11</getcontentlength>
                    <getlastmodified>Sun, 13 Sep 2026 08:00:00 GMT</getlastmodified>
                  </prop></propstat></response>
                  <response><href>/dav/bookshelf.json</href><propstat><prop>
                    <displayname>bookshelf.json</displayname><getcontentlength>12</getcontentlength>
                    <getlastmodified>Sun, 13 Sep 2026 09:00:00 GMT</getlastmodified>
                  </prop></propstat></response>
                  <response><href>/dav/not-backup.zip</href><propstat><prop>
                    <displayname>not-backup.zip</displayname><getcontentlength>13</getcontentlength>
                    <getlastmodified>Sun, 13 Sep 2026 10:00:00 GMT</getlastmodified>
                  </prop></propstat></response>
                </multistatus>
                """.trimIndent(),
            ),
        )
        val files = backup.listLegadoBackups(serverUrl(), user, pass).getOrThrow()
        assertEquals(listOf("backup2026-09-13.zip"), files.map { it.name })
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

    // =========================================================================
    // S14 / S04-5：备份覆盖面扩展（来源引用与规则数据）与安全门禁
    // =========================================================================

    private val testJson = Json { ignoreUnknownKeys = true }

    private fun createMockJsonBridge(
        bookDao: BookDao = mockk(relaxed = true) {
            coEvery { observeAllActive() } returns flowOf(emptyList())
        },
        sourceRefDao: LibrarySourceRefDao = mockk(relaxed = true),
        textRuleDao: ReaderTextRuleDao = mockk(relaxed = true) {
            coEvery { observeAll() } returns flowOf(emptyList())
        },
        correctionDao: ReaderCorrectionDao = mockk(relaxed = true),
    ): Pair<JsonBridge, BridgeMocks> {
        val inspirationDao = mockk<InspirationDao>(relaxed = true) {
            coEvery { observeAllActive() } returns flowOf(emptyList())
        }
        val progressDao = mockk<ReadingProgressDao>(relaxed = true) {
            coEvery { observeAllActive() } returns flowOf(emptyList())
        }
        val sessionDao = mockk<ReadingSessionDao>(relaxed = true) {
            coEvery { observeAllActive() } returns flowOf(emptyList())
        }
        val noteDao = mockk<NoteDao>(relaxed = true) { coEvery { observeAllActive() } returns flowOf(emptyList()) }
        val highlightDao = mockk<HighlightDao>(relaxed = true) { coEvery { observeAllActive() } returns flowOf(emptyList()) }
        val tagDao = mockk<TagDao>(relaxed = true)
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val shelfDao = mockk<ShelfDao>(relaxed = true)
        val bookTagDao = mockk<BookTagDao>(relaxed = true)
        val bookCategoryDao = mockk<BookCategoryDao>(relaxed = true)
        val shelfBookDao = mockk<ShelfBookDao>(relaxed = true)
        val chapterReadDao = mockk<ChapterReadDao>(relaxed = true)

        coEvery { bookDao.runInTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }

        val bridge = JsonBridge(
            bookDao = bookDao,
            inspirationDao = inspirationDao,
            progressDao = progressDao,
            sessionDao = sessionDao,
            noteDao = noteDao,
            highlightDao = highlightDao,
            tagDao = tagDao,
            categoryDao = categoryDao,
            shelfDao = shelfDao,
            bookTagDao = bookTagDao,
            bookCategoryDao = bookCategoryDao,
            shelfBookDao = shelfBookDao,
            chapterReadDao = chapterReadDao,
            ioDispatcher = Dispatchers.Unconfined,
            sourceRefDao = sourceRefDao,
            textRuleDao = textRuleDao,
            correctionDao = correctionDao,
        )
        return bridge to BridgeMocks(bookDao, sourceRefDao, textRuleDao, correctionDao)
    }

    private class BridgeMocks(
        val bookDao: BookDao,
        val sourceRefDao: LibrarySourceRefDao,
        val textRuleDao: ReaderTextRuleDao,
        val correctionDao: ReaderCorrectionDao,
    )

    @Test
    fun `export JSON contains library_source_refs and rule groups and deserializes successfully`() = runTest {
        val bookDao = mockk<BookDao>(relaxed = true) {
            coEvery { observeAllActive() } returns flowOf(
                listOf(BookEntity(id = "b1", title = "测试书籍", format = "txt", updated_at = "2026-01-01T00:00:00Z"))
            )
        }
        val sourceRefDao = mockk<LibrarySourceRefDao>(relaxed = true) {
            coEvery { getAll() } returns listOf(
                LibrarySourceRefEntity(
                    book_id = "b1",
                    root_id = "root-1",
                    provider_authority = "com.android.providers.media.documents",
                    document_id = "doc-1",
                    display_name = "test.txt",
                    format = "txt",
                    size = 1024L,
                    last_modified = 1700000000L,
                    content_hash = "md5-hash-1",
                    candidate_fingerprint = null,
                    last_seen_at = 1700000100L,
                    availability = "available",
                )
            )
        }
        val textRuleDao = mockk<ReaderTextRuleDao>(relaxed = true) {
            coEvery { observeAll() } returns flowOf(
                listOf(
                    ReaderTextRuleEntity(
                        id = "rule-1",
                        kind = "REPLACE",
                        name = "去广告",
                        pattern = "ad_pattern",
                        replacement = "",
                        builtin = false,
                        enabled = true,
                        scope = "GLOBAL",
                        book_id = null,
                        position = 0,
                        created_at = 1000L,
                        updated_at = 2000L,
                    )
                )
            )
        }
        val correctionDao = mockk<ReaderCorrectionDao>(relaxed = true) {
            coEvery { listForBook("b1") } returns listOf(
                ReaderCorrectionEntity(
                    id = "corr-1",
                    book_id = "b1",
                    source_start = 10,
                    source_end = 15,
                    find_text = "错字",
                    replace_text = "正字",
                    status = ReaderCorrectionEntity.STATUS_ACTIVE,
                    created_at = 3000L,
                    updated_at = 4000L,
                )
            )
        }

        val (bridge, _) = createMockJsonBridge(bookDao, sourceRefDao, textRuleDao, correctionDao)
        val context = mockk<Context>(relaxed = true)

        val jsonString = bridge.exportToString(context)

        // 关键断言：导出的 JSON 必须包含 library_source_refs, reader_text_rules, reader_text_corrections
        assertTrue("导出的 JSON 必须包含 library_source_refs", jsonString.contains("library_source_refs"))
        assertTrue("导出的 JSON 必须包含 reader_text_rules", jsonString.contains("reader_text_rules"))
        assertTrue("导出的 JSON 必须包含 reader_text_corrections", jsonString.contains("reader_text_corrections"))

        // 关键断言：能成功反序列化为 LocalExport 且数据完整
        val export = testJson.decodeFromString<LocalExport>(jsonString)
        assertEquals(1, export.librarySourceRefs.size)
        assertEquals("b1", export.librarySourceRefs[0].book_id)
        assertEquals("test.txt", export.librarySourceRefs[0].display_name)

        assertEquals(1, export.textRules.size)
        assertEquals("rule-1", export.textRules[0].id)
        assertEquals("去广告", export.textRules[0].name)

        assertEquals(1, export.textCorrections.size)
        assertEquals("corr-1", export.textCorrections[0].id)
        assertEquals("错字", export.textCorrections[0].find_text)
    }

    @Test
    fun `importFromString merges library_source_refs and rules into database with safe revision policy`() = runTest {
        val existingSourceRef = LibrarySourceRefEntity(
            book_id = "b1",
            root_id = null,
            provider_authority = "auth",
            document_id = "doc-1",
            display_name = "old.txt",
            format = "txt",
            size = 500L,
            last_modified = 1000L,
            content_hash = "h1",
            candidate_fingerprint = null,
            last_seen_at = 1000L,
            availability = "available",
        )
        val sourceRefDao = mockk<LibrarySourceRefDao>(relaxed = true) {
            coEvery { getAll() } returns listOf(existingSourceRef)
        }

        val existingRule = ReaderTextRuleEntity(
            id = "rule-1",
            kind = "REPLACE",
            name = "本地较新规则",
            pattern = "p",
            replacement = "r",
            builtin = false,
            enabled = true,
            scope = "GLOBAL",
            book_id = null,
            position = 0,
            created_at = 1000L,
            updated_at = 5000L, // 本地较新
        )
        val textRuleDao = mockk<ReaderTextRuleDao>(relaxed = true) {
            coEvery { getById("rule-1") } returns existingRule
            coEvery { getById("rule-new") } returns null
        }

        val correctionDao = mockk<ReaderCorrectionDao>(relaxed = true) {
            coEvery { getById("corr-new") } returns null
        }

        val (bridge, mocks) = createMockJsonBridge(
            sourceRefDao = sourceRefDao,
            textRuleDao = textRuleDao,
            correctionDao = correctionDao,
        )
        val context = mockk<Context>(relaxed = true)

        val jsonString = """
        {
          "schemaVersion": 1,
          "exportedAt": "2026-09-23T00:00:00Z",
          "deviceId": "dev-remote",
          "books": [],
          "inspirations": [],
          "progress": [],
          "sessions": [],
          "library_source_refs": [
            {
              "book_id": "b1",
              "provider_authority": "auth",
              "display_name": "new.txt",
              "format": "txt",
              "size": 600,
              "last_seen_at": 2000,
              "availability": "available"
            },
            {
              "book_id": "b2",
              "provider_authority": "auth",
              "display_name": "b2.txt",
              "format": "txt",
              "size": 800,
              "last_seen_at": 500,
              "availability": "available"
            }
          ],
          "reader_text_rules": [
            {
              "id": "rule-1",
              "kind": "REPLACE",
              "name": "远端陈旧规则",
              "replacement": "",
              "builtin": false,
              "enabled": false,
              "scope": "GLOBAL",
              "position": 0,
              "created_at": 1000,
              "updated_at": 3000
            },
            {
              "id": "rule-new",
              "kind": "REPLACE",
              "name": "远端新增规则",
              "replacement": "",
              "builtin": false,
              "enabled": true,
              "scope": "GLOBAL",
              "position": 1,
              "created_at": 2000,
              "updated_at": 6000
            }
          ],
          "reader_text_corrections": [
            {
              "id": "corr-new",
              "book_id": "b2",
              "source_start": 0,
              "source_end": 2,
              "find_text": "错",
              "replace_text": "对",
              "status": "ACTIVE",
              "created_at": 1000,
              "updated_at": 2000
            }
          ]
        }
        """.trimIndent()

        bridge.importFromString(context, jsonString)

        // 验证来源引用：b1 的 last_seen_at (2000) > 本地 (1000)，覆盖更新；b2 为新增，写入
        coVerify(exactly = 1) { mocks.sourceRefDao.upsert(match { it.book_id == "b1" && it.display_name == "new.txt" }) }
        coVerify(exactly = 1) { mocks.sourceRefDao.upsert(match { it.book_id == "b2" && it.display_name == "b2.txt" }) }

        // 验证文本规则：rule-1 远端 updated_at (3000) < 本地 (5000)，旧不覆新（0 次 upsert）；rule-new 为新增，写入 1 次
        coVerify(exactly = 0) { mocks.textRuleDao.upsert(match { it.id == "rule-1" }) }
        coVerify(exactly = 1) { mocks.textRuleDao.upsert(match { it.id == "rule-new" && it.name == "远端新增规则" }) }

        // 验证纠错记录：corr-new 为新增，写入 1 次
        coVerify(exactly = 1) { mocks.correctionDao.upsert(match { it.id == "corr-new" && it.find_text == "错" }) }
    }

    @Test
    fun `importFromString safely rejects unsupported schemaVersion`() = runTest {
        val (bridge, mocks) = createMockJsonBridge()
        val context = mockk<Context>(relaxed = true)

        val invalidSchemaJson = """
        {
          "schemaVersion": 999,
          "exportedAt": "2026-09-23T00:00:00Z",
          "deviceId": "dev-future",
          "books": [],
          "inspirations": [],
          "progress": [],
          "sessions": []
        }
        """.trimIndent()

        var caught: Throwable? = null
        try {
            bridge.importFromString(context, invalidSchemaJson)
        } catch (t: Throwable) {
            caught = t
        }

        assertTrue("不认识的 schemaVersion 必须被拒绝", caught is IllegalStateException)
        assertTrue("异常提示必须包含版本信息", caught!!.message!!.contains("schemaVersion=999"))
        // 关键断言：版本不符时事务绝不能执行，任何 DAO 都不能被 upsert
        coVerify(exactly = 0) { mocks.bookDao.upsert(any()) }
        coVerify(exactly = 0) { mocks.sourceRefDao.upsert(any()) }
    }

    @Test
    fun `webdav full roundtrip with source refs and rules`() = runTest {
        // 验证 WebDAV 管道传输包含 source_refs 与 rules 的真实 JSON 时的连通性
        val jsonPayload = """{"schemaVersion":1,"exportedAt":"now","deviceId":"d","books":[],"inspirations":[],"progress":[],"sessions":[],"library_source_refs":[],"reader_text_rules":[]}"""
        server.enqueue(MockResponse().setResponseCode(201))
        val putResult = backup.put(serverUrl(), user, pass, "cra-backup-latest.json", jsonPayload)
        assertTrue("WebDAV PUT 应该成功", putResult.isSuccess)

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonPayload))
        val getResult = backup.get(serverUrl(), user, pass, "cra-backup-latest.json")
        assertEquals(jsonPayload, getResult.getOrNull())
    }
}
