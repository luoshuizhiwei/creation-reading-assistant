package com.creationreadingassistant.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提供本机设备标识与信息，用于配对与同步信封的 device 字段。
 * deviceId 采用 Android ID（重置出厂会变，足够局域网配对标识用途）。
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("HardwareIds")
    fun provide(): SyncContract.DeviceInfo {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
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
