package com.creationreadingassistant.ui.viewmodel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProfileUpdateChecker 直接单测：检查更新从 AboutSubPage 下沉后的全部分支
 * （发现新版 / 已最新 / 无 Android 发布 / 网络异常）都可在 JVM 上锁定，
 * fetch 注入后测试不碰网络。
 */
class ProfileUpdateCheckerTest {

    private fun releasesJson(vararg tags: String) = tags.joinToString(
        prefix = "[", postfix = "]", separator = ",",
    ) { """{"tag_name":"$it","html_url":"https://github.com/x/repo/releases/tag/$it","body":"更新说明 $it","draft":false}""" }

    @Test
    fun `newer android release reports update with tag notes and url`() = runTest {
        val checker = ProfileUpdateChecker { releasesJson("android-v9.9.9") }

        val outcome = checker.check("1.0.0").getOrThrow()

        assertTrue(outcome.hasUpdate)
        assertEquals("android-v9.9.9", outcome.latestTag)
        assertEquals("更新说明 android-v9.9.9", outcome.releaseNotes)
        assertEquals("https://github.com/x/repo/releases/tag/android-v9.9.9", outcome.downloadUrl)
        assertTrue(outcome.status.contains("发现新版本 android-v9.9.9"))
    }

    @Test
    fun `local version equal or newer keeps up-to-date message with raw version name`() = runTest {
        // 本地 versionName 带 v 前缀时比较要去前缀，展示保持原样（与下沉前文案一致）
        val checker = ProfileUpdateChecker { releasesJson("android-v1.0.0") }

        val outcome = checker.check("v1.0.0").getOrThrow()

        assertFalse(outcome.hasUpdate)
        assertNull(outcome.latestTag)
        assertEquals("已是最新版本（当前 v1.0.0）", outcome.status)
    }

    @Test
    fun `releases without android tag yield no version info`() = runTest {
        val checker = ProfileUpdateChecker { releasesJson("v2.1.0") }

        assertEquals("未获取到版本信息", checker.check("1.0.0").getOrThrow().status)
    }

    @Test
    fun `malformed json is handled as missing version info not a crash`() = runTest {
        val checker = ProfileUpdateChecker { "not json" }

        assertEquals("未获取到版本信息", checker.check("1.0.0").getOrThrow().status)
    }

    @Test
    fun `fetch failure surfaces as Result failure for the viewmodel error path`() = runTest {
        val boom = java.io.IOException("network down")
        val checker = ProfileUpdateChecker { throw boom }

        val result = checker.check("1.0.0")

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }
}
