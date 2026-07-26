package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.data.security.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 连接配置的安全存储（url / user / pass）。
 * 与 SyncConfigStore 同机制（AndroidKeyStore 主密钥 + AES256）。
 */
@Singleton
class WebDavConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { SecurePrefs.open(context, PREFS_NAME) }

    data class Config(val url: String, val user: String, val pass: String)

    var config: Config?
        get() {
            val url = prefs.getString(KEY_URL, null) ?: return null
            val user = prefs.getString(KEY_USER, null) ?: return null
            val pass = prefs.getString(KEY_PASS, null) ?: return null
            return Config(url, user, pass)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_URL); remove(KEY_USER); remove(KEY_PASS)
                } else {
                    putString(KEY_URL, value.url)
                    putString(KEY_USER, value.user)
                    putString(KEY_PASS, value.pass)
                }
                apply()
            }
        }

    companion object {
        private const val PREFS_NAME = "webdav_config"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
    }
}
