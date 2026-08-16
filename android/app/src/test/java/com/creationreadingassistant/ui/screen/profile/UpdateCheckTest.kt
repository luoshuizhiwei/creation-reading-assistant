package com.creationreadingassistant.ui.screen.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁定检查更新的纯逻辑：releases 列表按 android-v 前缀过滤、draft 排除、
 * 多个候选取版本号最高者；版本比较为逐段数字比较（非字符串字典序），
 * 且本地版本名的 `-p4` 类构建后缀不参与比较。
 */
class UpdateCheckTest {

    private fun releaseJson(
        tag: String,
        body: String? = null,
        draft: Boolean = false,
        htmlUrl: String = "https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases/tag/$tag",
    ): String {
        val bodyField = body?.let { "\"body\":\"$it\"," } ?: "\"body\":null,"
        return """{"tag_name":"$tag",$bodyField"draft":$draft,"html_url":"$htmlUrl","prerelease":false}"""
    }

    private fun releasesJson(vararg releases: String): String = "[" + releases.joinToString(",") + "]"

    // ---- latestAndroidRelease ----

    @Test
    fun `filters android tags from mixed desktop releases`() {
        val json = releasesJson(
            releaseJson("v2.1.0"), // 桌面端发布，应忽略
            releaseJson("android-v0.5.0", body = "更新说明\\n第二行"),
        )
        val latest = UpdateCheck.latestAndroidRelease(json)!!
        assertEquals("android-v0.5.0", latest.tag)
        assertEquals("0.5.0", latest.version)
        assertEquals("更新说明\n第二行", latest.notes)
        assertEquals(
            "https://github.com/luoshuizhiwei/creation-reading-assistant-releases/releases/tag/android-v0.5.0",
            latest.pageUrl,
        )
    }

    @Test
    fun `picks highest version among android tags`() {
        val json = releasesJson(
            releaseJson("android-v0.5.0"),
            releaseJson("android-v0.10.0"), // 数字比较应大于 0.9.x，字典序则会排错
            releaseJson("android-v0.9.3"),
        )
        assertEquals("android-v0.10.0", UpdateCheck.latestAndroidRelease(json)!!.tag)
    }

    @Test
    fun `skips draft releases`() {
        val json = releasesJson(
            releaseJson("android-v9.9.9", draft = true),
            releaseJson("android-v0.5.0"),
        )
        assertEquals("android-v0.5.0", UpdateCheck.latestAndroidRelease(json)!!.tag)
    }

    @Test
    fun `blank body becomes null notes`() {
        val latest = UpdateCheck.latestAndroidRelease(releasesJson(releaseJson("android-v0.5.0", body = "  ")))!!
        assertNull(latest.notes)
    }

    @Test
    fun `returns null when no android tag`() {
        assertNull(UpdateCheck.latestAndroidRelease(releasesJson(releaseJson("v2.1.0"))))
    }

    @Test
    fun `returns null on malformed json`() {
        assertNull(UpdateCheck.latestAndroidRelease("not json"))
        assertNull(UpdateCheck.latestAndroidRelease("""{"tag_name":"android-v1.0.0"}""")) // 非数组
        assertNull(UpdateCheck.latestAndroidRelease("[]"))
    }

    // ---- compareVersions ----

    @Test
    fun `equal versions compare zero`() {
        assertEquals(0, UpdateCheck.compareVersions("1.2.3", "1.2.3"))
        assertEquals(0, UpdateCheck.compareVersions("1.2", "1.2.0")) // 缺段视为 0
    }

    @Test
    fun `compares segments numerically not lexicographically`() {
        assertEquals(1, UpdateCheck.compareVersions("1.10.0", "1.9.9").sign)
        assertEquals(1, UpdateCheck.compareVersions("0.10.0", "0.9.1").sign)
        assertEquals(-1, UpdateCheck.compareVersions("1.9.9", "1.10.0").sign)
    }

    @Test
    fun `major beats minor and patch`() {
        assertEquals(1, UpdateCheck.compareVersions("2.0.0", "1.99.99").sign)
        assertEquals(1, UpdateCheck.compareVersions("1.3.0", "1.2.99").sign)
    }

    @Test
    fun `build suffix does not affect comparison`() {
        // 本地 versionName 可能是 0.4.0-p4 这类开发构建名
        assertEquals(0, UpdateCheck.compareVersions("0.4.0-p4", "0.4.0"))
        assertEquals(1, UpdateCheck.compareVersions("0.5.0", "0.4.0-p4").sign)
    }

    @Test
    fun `leading v prefix tolerated`() {
        assertEquals(0, UpdateCheck.compareVersions("v1.2.3", "1.2.3"))
    }

    private val Int.sign: Int get() = compareTo(0)
}
