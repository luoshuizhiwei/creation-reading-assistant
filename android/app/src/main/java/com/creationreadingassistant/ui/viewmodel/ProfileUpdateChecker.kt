package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.ui.screen.profile.MOBILE_RELEASE_API_URL
import com.creationreadingassistant.ui.screen.profile.UpdateCheck
import java.net.HttpURLConnection
import java.net.URL

/** 关于页「检查更新」的 UI 投影：取代原先内联在 Composable 里的 7 个 remember 状态。 */
data class AboutUpdateState(
    val checking: Boolean = false,
    val status: String? = null,
    val hasUpdate: Boolean = false,
    val latestTag: String? = null,
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
    val lastCheckAt: String? = null,
)

/** 一次检查的结论（文案已在此翻译好，ViewModel 只做投影）。 */
data class UpdateCheckOutcome(
    val status: String,
    val hasUpdate: Boolean = false,
    val latestTag: String? = null,
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
)

/**
 * 检查更新用例：联网拉取 GitHub Releases → 过滤 Android 前缀 tag → 语义化版本比对。
 *
 * [fetchReleases] 可注入，JVM 单测不碰网络即可锁定全部分支；网络/解析异常以
 * Result.failure 返回，由 ViewModel 负责状态投影与日志，与 UI 生命周期解耦。
 */
internal class ProfileUpdateChecker(
    private val fetchReleases: suspend () -> String = ::httpGetReleases,
) {
    /** [currentVersion] 传本地 versionName（可带 v 前缀，比较时自动去掉）。 */
    suspend fun check(currentVersion: String): Result<UpdateCheckOutcome> = runCatching {
        val release = UpdateCheck.latestAndroidRelease(fetchReleases())
        val normalized = currentVersion.trimStart('v', 'V')
        when {
            release == null -> UpdateCheckOutcome("未获取到版本信息")
            UpdateCheck.compareVersions(release.version, normalized) <= 0 ->
                UpdateCheckOutcome("已是最新版本（当前 v$normalized）")
            else -> UpdateCheckOutcome(
                status = "发现新版本 ${release.tag}（当前 v$normalized）",
                hasUpdate = true,
                latestTag = release.tag,
                releaseNotes = release.notes,
                downloadUrl = release.pageUrl,
            )
        }
    }

    companion object {
        private suspend fun httpGetReleases(): String {
            val conn = URL(MOBILE_RELEASE_API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
        }
    }
}
