package com.creationreadingassistant.data.remote

import android.content.SharedPreferences
import com.creationreadingassistant.data.security.SecurePrefs
import com.creationreadingassistant.testutil.InMemorySharedPreferences
import com.creationreadingassistant.testutil.testDataStoreContext
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * SyncConfigStore 读写往返测试。
 *
 * JVM 上 EncryptedSharedPreferences 的加解密行为不可靠，用
 * mockkObject(SecurePrefs) 把加密存储拦截到内存 SharedPreferences，
 * 配置读写语义与真实加密存储一致。
 */
class SyncConfigStoreTest {

    private val securePrefsMap = HashMap<String, SharedPreferences>()

    @Before
    fun setUp() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            securePrefsMap.getOrPut(secondArg()) { InMemorySharedPreferences() }
        }
    }

    @After
    fun tearDown() {
        unmockkObject(SecurePrefs)
    }

    private fun newStore() = SyncConfigStore(testDataStoreContext())

    @Test
    fun `config is null before pairing`() {
        assertNull(newStore().config)
    }

    @Test
    fun `config set and get round trip`() {
        val store = newStore()
        val config = SyncConfigStore.Config(
            baseUrl = "https://sync.example.com",
            token = "token-abc",
            deviceId = "dev-123",
            pairedAt = "2026-01-01T00:00:00Z",
            lastSyncAt = null,
        )

        store.config = config
        assertEquals(config, store.config)
    }

    @Test
    fun `markSyncedAt updates only last sync time`() {
        val store = newStore()
        store.config = SyncConfigStore.Config(
            baseUrl = "https://sync.example.com",
            token = "token-abc",
            deviceId = "dev-123",
            pairedAt = "2026-01-01T00:00:00Z",
        )

        store.markSyncedAt("2026-02-01T00:00:00Z")

        val loaded = store.config!!
        assertEquals("2026-02-01T00:00:00Z", loaded.lastSyncAt)
        assertEquals("token-abc", loaded.token)
        assertEquals("dev-123", loaded.deviceId)
        assertEquals("https://sync.example.com", loaded.baseUrl)
    }

    @Test
    fun `markSyncedAt without pairing is a no-op`() {
        val store = newStore()
        store.markSyncedAt("2026-02-01T00:00:00Z")
        assertNull(store.config)
    }

    @Test
    fun `clear removes pairing config`() {
        val store = newStore()
        store.config = SyncConfigStore.Config(
            baseUrl = "https://sync.example.com",
            token = "token-abc",
            deviceId = "dev-123",
            pairedAt = "2026-01-01T00:00:00Z",
        )

        store.clear()

        assertNull(store.config)
    }
}
