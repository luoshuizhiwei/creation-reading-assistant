package com.creationreadingassistant.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS 运行时申请公共 helper（P3.2 片 3 从 TTS 路径抽取）。
 *
 * - API < 33 无需运行时申请，恒视为已授予；
 * - 非 Activity 上下文无法弹系统授权框，返回 false 由调用方提示到设置里开；
 * - requestCode 按调用方区分：1001 = TTS 播放控制，2002 = 阅读目标提醒。
 */
object NotificationPermission {

    const val REQUEST_TTS = 1001
    const val REQUEST_GOAL = 2002

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return checkPostNotificationsGranted(context)
    }

    /** API 33+ 才引用该权限字段（lint InlinedApi 需版本守卫内联才能识别）。 */
    private fun checkPostNotificationsGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /** 已授予返回 true；发起申请或无法申请返回 false。 */
    fun requestIfNeeded(activity: Activity, requestCode: Int): Boolean {
        if (isGranted(activity)) return true
        requestPostNotifications(activity, requestCode)
        return false
    }

    @Suppress("InlinedApi") // 调用方仅在 isGranted() 已确认 API 33+ 时到达此处
    private fun requestPostNotifications(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode,
        )
    }
}
