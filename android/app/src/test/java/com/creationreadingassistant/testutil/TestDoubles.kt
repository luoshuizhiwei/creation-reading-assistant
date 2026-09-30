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
 * **为什么同一次运行内必须唯一**：`preferencesDataStore` 委托把 DataStore 实例缓存为
 * 进程级单例，并以**首个** Context 的 `filesDir` 作为落盘目录；之后即使传入别的目录也
 * 不会被采用。所以同一次 JVM 运行内所有测试必须共用同一个根，否则会出现
 * 「测试以为在写 A 目录、DataStore 实际写 B 目录」的假隔离。
 *
 * **为什么又要按运行隔离**：该根一旦固定，上一次运行残留的 `app_settings` 等文件会被
 * 本次运行直接读进来 —— 既让「全新环境」类断言读到旧值，也让 DataStore 首次发射的
 * 时机随文件系统状态漂移（2026-09-30 联合全量首轮 `SettingsStoreBootMirrorTest`
 * 偶发失败的环境因素之一）。故以 JVM 启动标识生成一次性目录，并在 JVM 退出时递归
 * 删除（资源关闭，不留跨运行残渣）。
 *
 * **同运行内的跨测试类隔离**不靠目录实现（做不到，见上），而靠「每个测试显式建立自己
 * 依赖的状态并等待其落定」，见 `SettingsStoreBootMirrorTest` 的 await 辅助。
 */
object TestDataStoreDirs {
    val root: File by lazy {
        val dir = Files.createDirectories(
            File(System.getProperty("java.io.tmpdir"), "cra-datastore-tests-${runId()}").toPath(),
        ).toFile()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { dir.deleteRecursively() } })
        dir
    }

    /** 本次 JVM 运行的唯一标识：跨运行不复用同一目录，避免读到上次运行的数据。 */
    private fun runId(): String =
        "${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}"
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
