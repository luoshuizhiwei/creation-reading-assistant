package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 超大文件候选指纹的测试。
 *
 * 指纹只用于去重候选提示（方案 §5.6），这里锁定：格式稳定、大小参与指纹、
 * 头/尾任一分块不同都产生不同指纹、32 MiB 以下不启用、读取失败不产出指纹。
 */
class SourceFingerprintTest {

    @Test
    fun `md5 hex matches the known vector`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", SourceFingerprint.md5Hex("abc".toByteArray()))
    }

    @Test
    fun `large file threshold is strictly above 32 MiB`() {
        assertFalse(SourceFingerprint.isLargeFile(null))
        assertFalse(SourceFingerprint.isLargeFile(0L))
        assertFalse(SourceFingerprint.isLargeFile(32L * 1024 * 1024))
        assertTrue(SourceFingerprint.isLargeFile(32L * 1024 * 1024 + 1))
    }

    @Test
    fun `fingerprint embeds version size and both chunk digests`() {
        val fp = SourceFingerprint.fromChunks(40L * 1024 * 1024, "aa11", "bb22")

        assertEquals("fp1|41943040|aa11|bb22", fp)
    }

    @Test
    fun `same chunks and size produce the same fingerprint`() {
        val a = SourceFingerprint.fromChunks(40L * 1024 * 1024, "aa11", "bb22")
        val b = SourceFingerprint.fromChunks(40L * 1024 * 1024, "aa11", "bb22")

        assertEquals(a, b)
    }

    @Test
    fun `different size or tail chunk yields a different fingerprint`() {
        val base = SourceFingerprint.fromChunks(40L * 1024 * 1024, "aa11", "bb22")

        assertNotEquals(base, SourceFingerprint.fromChunks(40L * 1024 * 1024 + 1, "aa11", "bb22"))
        assertNotEquals(base, SourceFingerprint.fromChunks(40L * 1024 * 1024, "aa11", "cc22"))
        assertNotEquals(base, SourceFingerprint.fromChunks(40L * 1024 * 1024, "dd11", "bb22"))
    }

    @Test
    fun `compute reads exactly two streams and hashes head and tail chunks`() {
        val size = 40L * 1024 * 1024
        val data = ByteArray(size.toInt()) // 全 0：头块与尾块内容一致，MD5 可预先确定
        var opened = 0
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers {
            opened++
            ByteArrayInputStream(data)
        }

        val fingerprint = SourceFingerprint.compute(resolver, mockk<Uri>(), size)
        val chunkMd5 = SourceFingerprint.md5Hex(ByteArray(SourceFingerprint.CHUNK_BYTES))

        assertEquals(2, opened)
        assertEquals(SourceFingerprint.fromChunks(size, chunkMd5, chunkMd5), fingerprint)
    }

    @Test
    fun `compute swallows stream failures and returns null`() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } throws IOException("无法读取")

        assertNull(SourceFingerprint.compute(resolver, mockk<Uri>(), 40L * 1024 * 1024))
    }

    @Test
    fun `compute refuses small files without opening streams`() {
        var opened = 0
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers {
            opened++
            throw IOException("不应到达")
        }

        assertNull(SourceFingerprint.compute(resolver, mockk<Uri>(), 1024L))
        assertEquals(0, opened)
    }
}
