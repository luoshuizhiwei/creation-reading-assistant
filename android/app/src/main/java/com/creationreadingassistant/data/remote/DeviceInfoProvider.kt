package com.creationreadingassistant.data.remote

import android.content.Context
import android.os.Build
import com.creationreadingassistant.data.security.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提供本机设备标识与信息，用于配对与同步信封的 device 字段。
 * deviceId 采用应用级唯一 UUID（持久化于安全存储，避免读取系统 HardwareIds）。
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun provide(): SyncContract.DeviceInfo {
        val id = SecurePrefs.getOrCreateAppInstanceId(context)
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".replaceFirstChar { it.uppercase() }
        val now = Instant.now().toString()
        return SyncContract.DeviceInfo(
            deviceId = id,
            name = name,
            platform = SyncContract.SyncPlatform.android,
            pairedAt = now,
            lastSeenAt = now,
        )
    }
}
