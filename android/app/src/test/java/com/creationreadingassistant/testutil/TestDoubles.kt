package com.creationreadingassistant.testutil

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

/**
 * JVM 内存版 SharedPreferences，用于在单测中替代 EncryptedSharedPreferences
 * （AndroidKeyStore 在纯 JVM 环境不可用）。
 */
class InMemorySharedPreferences : SharedPreferences {

    // 真实 SharedPreferences 允许 null 键（EncryptedSharedPreferences 用 null 键
    // 存主密钥元数据），此处对齐该语义。
    private val data = LinkedHashMap<String?, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> =
        LinkedHashMap(data.filterKeys { it != null }.mapKeys { it.key!! })

    @Suppress("UNCHECKED_CAST")
    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.remove(it) }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String?, Any?>()
        private var clearRequested = false

        /** 与真实 SharedPreferences 一致：允许 null 键与 null 值。 */
        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values)
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key] = REMOVE_SENTINEL
        }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val changed = mutableListOf<String?>()
            synchronized(data) {
                if (clearRequested) {
                    changed.addAll(data.keys)
                    data.clear()
                }
                for ((key, value) in pending) {
                    if (value === REMOVE_SENTINEL) {
                        if (data.containsKey(key)) changed.add(key)
                        data.remove(key)
                    } else {
                        data[key] = value
                        changed.add(key)
                    }
                }
            }
            changed.forEach { key -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) } }
        }
    }

    private companion object {
        private val REMOVE_SENTINEL = Any()
    }
}

/**
 * DataStore 单测的稳定文件根目录。
 *
 * preferencesDataStore 委托在每个顶级属性上只创建一次 DataStore 实例，并以首个
 * Context 的 filesDir 作为落盘目录；因此所有 JVM 测试必须共用同一个稳定目录，
 * 避免不同测试类之间的委托缓存指向不存在的目录。
 */
object TestDataStoreDirs {
    val root: File by lazy {
        val dir = Files.createDirectories(File(System.getProperty("java.io.tmpdir"), "cra-datastore-tests").toPath()).toFile()
        dir
    }
}

/**
 * 构造一个 filesDir 指向 [TestDataStoreDirs.root] 的 mock Context，
 * 供依赖 preferencesDataStore 的存储类在 JVM 测试中真实读写磁盘。
 *
 * 同时把 getSharedPreferences 降级到 [InMemorySharedPreferences]，供需要
 * 明文 SharedPreferences 兼容层的路径使用。注意：经 SecurePrefs 的加密存储
 * 路径请在测试里用 mockkObject(SecurePrefs) 直接拦截（见各存储类测试），
 * JVM 上 EncryptedSharedPreferences 的加解密行为不可靠。
 */
fun testDataStoreContext(): Context {
    val store = HashMap<String, SharedPreferences>()
    lateinit var context: Context
    context = mockk {
        every { filesDir } returns TestDataStoreDirs.root
        // preferencesDataStore 委托初始化时会取 applicationContext
        every { applicationContext } answers { context }
        every { getSharedPreferences(any(), any()) } answers {
            store.getOrPut(firstArg()) { InMemorySharedPreferences() }
        }
        every { deleteSharedPreferences(any()) } answers {
            store.remove(firstArg<String>()) != null
        }
    }
    return context
}

/**
 * 构造一个把加密存储降级到 [InMemorySharedPreferences] 的 mock Context：
 * SecurePrefs 在 JVM 上创建 EncryptedSharedPreferences 失败后会回退明文存储，
 * 此处直接提供内存实现，保证敏感配置读写往返可在纯 JVM 环境验证。
 */
fun testSecurePrefsContext(): Context {
    val store = HashMap<String, SharedPreferences>()
    lateinit var context: Context
    context = mockk {
        every { filesDir } returns TestDataStoreDirs.root
        every { applicationContext } answers { context }
        every { getSharedPreferences(any(), any()) } answers {
            store.getOrPut(firstArg()) { InMemorySharedPreferences() }
        }
        every { deleteSharedPreferences(any()) } answers {
            store.remove(firstArg<String>()) != null
        }
    }
    return context
}
