package com.creationreadingassistant.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.creationreadingassistant.data.security.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配对配置的安全存储（token / deviceId / baseUrl）。
 * 使用 EncryptedSharedPreferences（AndroidKeyStore 主密钥 + AES256 加密封值）。
 */
@Singleton
class SyncConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { SecurePrefs.open(context, PREFS_NAME) }

    data class Config(
        val baseUrl: String,
        val token: String,
        val deviceId: String,
        val pairedAt: String,
        val lastSyncAt: String? = null,
    )

    var config: Config?
        get() {
            val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
            val token = prefs.getString(KEY_TOKEN, null) ?: return null
            val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return null
            val pairedAt = prefs.getString(KEY_PAIRED_AT, null) ?: return null
            val lastSyncAt = prefs.getString(KEY_LAST_SYNC, null)
            return Config(baseUrl, token, deviceId, pairedAt, lastSyncAt)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_BASE_URL); remove(KEY_TOKEN); remove(KEY_DEVICE_ID); remove(KEY_PAIRED_AT); remove(KEY_LAST_SYNC)
                } else {
                    putString(KEY_BASE_URL, value.baseUrl)
                    putString(KEY_TOKEN, value.token)
                    putString(KEY_DEVICE_ID, value.deviceId)
                    putString(KEY_PAIRED_AT, value.pairedAt)
                    putString(KEY_LAST_SYNC, value.lastSyncAt)
                }
                apply()
            }
        }

    /** 更新最近同步时间，保留其它字段。 */
    fun markSyncedAt(iso: String) {
        val c = config ?: return
        config = c.copy(lastSyncAt = iso)
    }

    fun clear() {
        config = null
    }

    companion object {
        private const val PREFS_NAME = "sync_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PAIRED_AT = "paired_at"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
