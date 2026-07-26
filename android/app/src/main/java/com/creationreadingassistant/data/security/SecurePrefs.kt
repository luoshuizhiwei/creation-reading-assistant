package com.creationreadingassistant.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Collections

/**
 * 打开加密 SharedPreferences 的统一入口。
 *
 * 早期实现在任何异常下都直接回退到明文 SharedPreferences，于是同步 token、
 * WebDAV 密码、AI API Key 会静默以明文落盘，用户完全无感。这里改为三级策略：
 *
 *  1. 正常创建加密存储；
 *  2. 失败时删除可能已损坏的 prefs 后重试一次 —— 主密钥被轮换/失效或文件被截断
 *     属于可自愈情况，重建后即可继续加密存储（代价是该项配置需要重新填写）；
 *  3. 仍失败才回退明文，并把 prefs 名记入 [degraded]，供设置页向用户明确告警。
 */
object SecurePrefs {

    private val degradedNames: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    /** 已回退到明文存储的 prefs 名称；非空说明设备上存在未加密的敏感数据。 */
    val degraded: Set<String> get() = degradedNames.toSet()

    /** 是否存在任何降级为明文的敏感存储。UI 可据此提示用户。 */
    val hasDegraded: Boolean get() = degradedNames.isNotEmpty()

    fun open(context: Context, name: String): SharedPreferences {
        create(context, name)?.let { return it }

        Log.w(TAG, "$name 加密存储创建失败，清除后重试")
        runCatching { context.deleteSharedPreferences(name) }
        create(context, name)?.let { return it }

        Log.e(TAG, "$name 加密存储不可用，回退明文存储；该项敏感数据未加密")
        degradedNames.add(name)
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private fun create(context: Context, name: String): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Throwable) {
        // 不打印异常详情之外的内容，避免把密钥材料相关信息写进日志。
        Log.w(TAG, "$name 加密存储创建失败：${e.javaClass.simpleName}")
        null
    }

    private const val TAG = "SecurePrefs"
}
