package com.creationreadingassistant.feature.sync.legado

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM 门禁（契约 §8 合成夹具）：解析器防护语义、行级严格 schema、身份指纹。
 */
class LegadoBackupParserTest {

    private fun parse(bytes: ByteArray): LegadoBackupParser.ParsedArchive = LegadoBackupParser.parse(bytes)

    // ---- 1) 正常四文件解析 ----
    @org.junit.Test
    fun `four whitelist files parse and unknown fields ignored`() {
        val zip = LegadoFixtures.zip(
            bookshelf = listOf(LegadoFixtures.bookshelfEntry()),
            sessions = listOf(LegadoFixtures.session()),
            days = listOf(LegadoFixtures.day()),
            books = listOf(LegadoFixtures.book()),
        )
        val parsed = parse(zip)
        assertEquals(1, parsed.bookshelf.size)
        assertEquals(1, parsed.sessions.size)
        assertEquals(1, parsed.days.size)
        assertEquals(1, parsed.books.size)
        assertEquals(0, parsed.rowErrors.total)
        assertEquals(setOf(
            LegadoBackupParser.FILE_BOOKSHELF,
            LegadoBackupParser.FILE_SESSION,
            LegadoBackupParser.FILE_DAY,
            LegadoBackupParser.FILE_BOOK,
        ), parsed.presentFiles)
        // 未知字段 showName 被忽略；origin 保留
        assertEquals("测试书源", parsed.bookshelf[0].origin)
    }

    // ---- 2) 白名单重名整包拒绝 ----
    @org.junit.Test
    fun `duplicate whitelist entry rejected`() {
        // JDK ZipOutputStream 原生拒绝重名条目，故用原始本地头拼接构造解析器可见的重名归档。
        val base = LegadoFixtures.zip()
        val second = LegadoFixtures.zip(bookshelf = listOf(LegadoFixtures.bookshelfEntry(name = "重复书")))
        val spliced = spliceLocalHeaderBlock(base, second)
        val error = runCatching { parse(spliced) }.exceptionOrNull()
        assertTrue("actual: ${error?.let { it.javaClass.simpleName + ':' + it.message }}", error is LegadoBackupParser.RejectedException)
        assertTrue(error!!.message!!.contains("重名"))
    }

    /** 把 [second] 的首个本地条目块（本地头+数据）拼进 [base] 中央目录之前。 */
    private fun spliceLocalHeaderBlock(base: ByteArray, second: ByteArray): ByteArray {
        val cd = byteArrayOf(0x50, 0x4B, 0x01, 0x02) // PK central header
        val baseCd = indexOf(base, cd)
        val secondCd = indexOf(second, cd)
        assertTrue(baseCd > 0 && secondCd > 0)
        val out = java.io.ByteArrayOutputStream()
        out.write(base, 0, baseCd)
        out.write(second, 0, secondCd)
        out.write(base, baseCd, base.size - baseCd)
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // ---- 3) 大小写/子目录冒充整包拒绝 ----
    @org.junit.Test
    fun `case-variant whitelist name rejected`() {
        val bytes = LegadoFixtures.zip(nameRewrite = { it.replace("bookshelf", "BookShelf") })
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    @org.junit.Test
    fun `subdirectory whitelist name rejected`() {
        val bytes = LegadoFixtures.zip(nameRewrite = { it.replace("bookshelf.json", "sub/bookshelf.json") })
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    @org.junit.Test
    fun `directory masquerading as whitelist rejected`() {
        val bytes = LegadoFixtures.zip(entries = listOf("BookShelf.json/" to ByteArray(0)))
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    // ---- 4) 路径防护 ----
    @org.junit.Test
    fun `traversal path rejected`() {
        val bytes = LegadoFixtures.zip(entries = listOf("../evil.json" to ByteArray(0)))
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    @org.junit.Test
    fun `absolute path rejected`() {
        val bytes = LegadoFixtures.zip(entries = listOf("/abs/evil.json" to ByteArray(0)))
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    @org.junit.Test
    fun `backslash path rejected`() {
        val bytes = LegadoFixtures.zip(entries = listOf("dir\\evil.json" to ByteArray(0)))
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    // ---- 5) 条目数上限 ----
    @org.junit.Test
    fun `entry count over budget rejected`() {
        val many = (0 until 300).associate { "extra/file$it.json" to ByteArray(1) }
        val bytes = LegadoFixtures.zip(entries = many.map { it.key to it.value })
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    // ---- 6) 无白名单 ----
    @org.junit.Test
    fun `missing whitelist rejected`() {
        val bytes = LegadoFixtures.zip(bookshelf = null, sessions = null, days = null, books = null)
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    // ---- 7) 行级严格 schema ----
    @org.junit.Test
    fun `bookshelf row missing required field counted invalid`() {
        val bad = "{\"name\":\"无章书\",\"author\":\"佚名\"}" // 缺 durChapterIndex/durChapterPos
        val zip = LegadoFixtures.zip(bookshelf = listOf(LegadoFixtures.bookshelfEntry(), bad))
        val parsed = parse(zip)
        assertEquals(1, parsed.bookshelf.size)
        assertEquals(1, parsed.rowErrors.bookshelf)
    }

    @org.junit.Test
    fun `session end not after start invalid`() {
        val bad = LegadoFixtures.session(start = 1_000L, end = 1_000L)
        val zip = LegadoFixtures.zip(sessions = listOf(LegadoFixtures.session(), bad))
        val parsed = parse(zip)
        assertEquals(1, parsed.sessions.size)
        assertEquals(1, parsed.rowErrors.sessions)
    }

    @org.junit.Test
    fun `session words missing kept null not zero`() {
        val zip = LegadoFixtures.zip(sessions = listOf(LegadoFixtures.session(words = null)))
        val parsed = parse(zip)
        assertEquals(1, parsed.sessions.size)
        assertNull(parsed.sessions[0].words)
        assertNotEquals(0L, parsed.sessions[0].words ?: -1L) // null ≠ 0
    }

    @org.junit.Test
    fun `string-typed number field invalid`() {
        val bad = LegadoFixtures.session(start = 1L, end = 2L).replace("\"startTime\":1", "\"startTime\":\"1\"")
        val zip = LegadoFixtures.zip(sessions = listOf(bad))
        val parsed = parse(zip)
        assertEquals(0, parsed.sessions.size)
        assertEquals(1, parsed.rowErrors.sessions)
    }

    @org.junit.Test
    fun `corrupted archive rejected`() {
        val bytes = ByteArray(64) { 'x'.code.toByte() }
        assertTrue(runCatching { parse(bytes) }.exceptionOrNull() is LegadoBackupParser.RejectedException)
    }

    // ---- 8) 身份指纹 ----
    @org.junit.Test
    fun `fingerprint order and namespace sensitive`() {
        val a = LegadoFingerprint.sessionFingerprint("d", "n", "a", "u", 1, 2, 3)
        val b = LegadoFingerprint.sessionFingerprint("d", "a", "n", "u", 1, 2, 3) // 交换 name/author
        assertNotEquals(a, b)
        val withNull = LegadoFingerprint.sessionFingerprint("d", "n", "a", "u", 1, 2, null)
        val withZero = LegadoFingerprint.sessionFingerprint("d", "n", "a", "u", 1, 2, 0)
        assertNotEquals(withNull, withZero) // 缺失 ≠ 0
        val ns1 = LegadoFingerprint.sessionRowId("s1", a)
        val ns2 = LegadoFingerprint.sessionRowId("s2", a)
        assertNotEquals(ns1, ns2) // 来源库命名空间
    }

    @org.junit.Test
    fun `day and book snapshot ids stable and distinct`() {
        val d1 = LegadoFingerprint.daySnapshotId("s", "dev", "n", "a", "2026-11-01")
        val d2 = LegadoFingerprint.daySnapshotId("s", "dev", "n", "a", "2026-11-02")
        assertNotEquals(d1, d2)
        val b1 = LegadoFingerprint.bookSnapshotId("s", "dev", "n", "a")
        assertNotEquals(d1, b1)
        assertEquals(d1, LegadoFingerprint.daySnapshotId("s", "dev", "n", "a", "2026-11-01"))
    }

    @org.junit.Test
    fun `doc identity reflects chapter changes`() {
        val a = LegadoFingerprint.docIdentity(100, listOf(0, 50))
        val b = LegadoFingerprint.docIdentity(100, listOf(0, 60))
        assertNotEquals(a, b)
        assertEquals(a, LegadoFingerprint.docIdentity(100, listOf(0, 50)))
        assertFalse(a == LegadoFingerprint.docIdentity(101, listOf(0, 50)))
        assertTrue(a.isNotEmpty())
    }
}
