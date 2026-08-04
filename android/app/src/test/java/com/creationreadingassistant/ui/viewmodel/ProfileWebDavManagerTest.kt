package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileWebDavManagerTest {

    private val configStore = mockk<WebDavConfigStore>(relaxed = true)
    private val webDavBackup = mockk<WebDavBackup>(relaxed = true)
    private val jsonBridge = mockk<JsonBridge>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val cfg = WebDavConfigStore.Config("https://dav.example.com/dav", "user", "pass")

    private fun manager() = ProfileWebDavManager(configStore, webDavBackup, jsonBridge)

    @Test
    fun `backup without config fails with guidance message`() = runTest {
        every { configStore.config } returns null

        val result = manager().backup(context)

        assertTrue(result.isFailure)
        assertEquals("请先填写 WebDAV 配置", result.exceptionOrNull()?.message)
    }

    @Test
    fun `backup uploads latest and timestamped archives`() = runTest {
        every { configStore.config } returns cfg
        coEvery { jsonBridge.exportToString(context) } returns """{"v":1}"""
        coEvery { webDavBackup.put(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = manager().backup(context)

        assertEquals("备份成功", result.getOrNull())
        coVerify(exactly = 1) { webDavBackup.put("https://dav.example.com/dav", "user", "pass", "cra-backup-latest.json", """{"v":1}""") }
        coVerify(exactly = 1) { webDavBackup.put("https://dav.example.com/dav", "user", "pass", match { it.startsWith("cra-backup-") && it != "cra-backup-latest.json" && it.endsWith(".json") }, """{"v":1}""") }
    }

    @Test
    fun `backup propagates upload failure`() = runTest {
        every { configStore.config } returns cfg
        coEvery { jsonBridge.exportToString(context) } returns "{}"
        coEvery { webDavBackup.put(any(), any(), any(), any(), any()) } returns Result.failure(IllegalStateException("WebDAV 返回 507"))

        val result = manager().backup(context)

        assertTrue(result.isFailure)
        assertEquals("WebDAV 返回 507", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test without config fails with guidance message`() = runTest {
        every { configStore.config } returns null

        val result = manager().test()

        assertTrue(result.isFailure)
        assertEquals("请先填写 WebDAV 配置", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test returns webdav prefixed message on success`() = runTest {
        every { configStore.config } returns cfg
        coEvery { webDavBackup.test(cfg.url, cfg.user, cfg.pass) } returns Result.success("连接成功（HTTP 200）")

        val result = manager().test()

        assertEquals("WebDAV 连接成功（HTTP 200）", result.getOrNull())
    }

    @Test
    fun `downloadRestore fetches and imports backup json`() = runTest {
        every { configStore.config } returns cfg
        coEvery { webDavBackup.get(cfg.url, cfg.user, cfg.pass, "cra-backup-latest.json") } returns Result.success("""{"v":1}""")
        coEvery { jsonBridge.importFromString(context, """{"v":1}""") } returns Unit

        val result = manager().downloadRestore(context)

        assertEquals("已从 WebDAV 恢复备份", result.getOrNull())
        coVerify(exactly = 1) { jsonBridge.importFromString(context, """{"v":1}""") }
    }

    @Test
    fun `downloadRestore without config fails with guidance message`() = runTest {
        every { configStore.config } returns null

        val result = manager().downloadRestore(context)

        assertTrue(result.isFailure)
        assertEquals("请先填写 WebDAV 配置", result.exceptionOrNull()?.message)
    }

    @Test
    fun `listBackups returns null without config`() = runTest {
        every { configStore.config } returns null

        assertNull(manager().listBackups())
    }

    @Test
    fun `listBackups returns success with backup list`() = runTest {
        every { configStore.config } returns cfg
        val files = listOf(WebDavBackup.BackupFile(name = "cra-backup-latest.json", size = 123L, lastModified = "2026-08-04T10:00:00Z"))
        coEvery { webDavBackup.listBackups(cfg.url, cfg.user, cfg.pass) } returns Result.success(files)

        val result = manager().listBackups()

        assertEquals(files, result?.getOrNull())
    }

    @Test
    fun `listBackups returns failure when listing fails`() = runTest {
        every { configStore.config } returns cfg
        coEvery { webDavBackup.listBackups(cfg.url, cfg.user, cfg.pass) } returns Result.failure(IllegalStateException("网络错误"))

        val result = manager().listBackups()

        assertTrue(result!!.isFailure)
    }
}
