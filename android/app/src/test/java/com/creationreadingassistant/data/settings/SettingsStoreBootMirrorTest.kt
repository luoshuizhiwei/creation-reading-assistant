package com.creationreadingassistant.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.creationreadingassistant.data.security.SecurePrefs
import com.creationreadingassistant.testutil.InMemorySharedPreferences
import com.creationreadingassistant.testutil.testDataStoreContext
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * 冷启动首帧主题回归：界面**第一个值**就必须是用户保存的深浅/纸色。
 *
 * 背景：DataStore 是异步的，StateFlow 的首帧值只能是硬编码默认值。冷启动（长时间后台后
 * 进程被杀）时界面先用默认值画一帧「日间」，等 DataStore 读到真实的 `dark`/`night` 后
 * 才纠正成「夜间」——用户看到的就是「夜间 → 闪成日间 → 又回夜间」。
 *
 * 修复：写入时把这两个字段镜像到 SharedPreferences，启动时同步读回作首帧初始值。
 * 本测试用**新实例 + 同一份 SharedPreferences**模拟冷启动，断言首帧值不等于默认值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreBootMirrorTest {

    private lateinit var scope: CoroutineScope
    private lateinit var context: Context
    private val securePrefsMap = HashMap<String, SharedPreferences>()

    @Before
    fun setUp() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            securePrefsMap.getOrPut(secondArg()) { InMemorySharedPreferences() }
        }
        scope = CoroutineScope(UnconfinedTestDispatcher())
        context = testDataStoreContext()
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(SecurePrefs)
    }

    @Test
    fun `cold start appearance first value uses stored theme instead of default`() = runTest {
        val first = SettingsStore(context, scope)
        first.appearance.first() // 等磁盘状态加载，避免基于旧缓存值更新
        first.updateAppearance { copy(themeMode = "dark") }

        // 模拟冷启动：新建实例（DataStore 尚未 emit），但 SharedPreferences 镜像仍在。
        val restarted = SettingsStore(context, scope)
        assertEquals(
            "冷启动首帧必须是保存的 dark，用默认值会让界面先闪成日间",
            "dark",
            restarted.appearance.value.themeMode,
        )
        assertNotEquals("system", restarted.appearance.value.themeMode)
    }

    @Test
    fun `cold start reader first value uses stored paper instead of default`() = runTest {
        val first = SettingsStore(context, scope)
        first.reader.first()
        first.updateReader { copy(background = "night") }

        val restarted = SettingsStore(context, scope)
        assertEquals(
            "冷启动首帧必须是保存的夜间纸色，用默认值会让阅读页先闪成白纸",
            "night",
            restarted.reader.value.background,
        )
        assertNotEquals("follow", restarted.reader.value.background)
    }

    @Test
    fun `fresh install without mirror still yields legal first values`() = runTest {
        // 全新环境：镜像缺失时必须回退默认值，不能抛异常或留下脏值。
        val fresh = SettingsStore(context, scope)
        assertNotEquals("镜像缺失时首帧主题仍应是合法值", "", fresh.appearance.value.themeMode)
        assertNotEquals("镜像缺失时首帧纸色仍应是合法值", "", fresh.reader.value.background)
    }

    @Test
    fun `legacy datastore theme backfills a missing boot mirror after first load`() = runTest {
        // 先用正式写路径建立“旧用户已有 DataStore 主题”的状态，再抹去只有新版才有的镜像，
        // 模拟从未带 BootMirror 的版本升级。
        val existingUser = SettingsStore(context, scope)
        existingUser.appearance.first()
        existingUser.updateAppearance { copy(themeMode = "dark") }
        context.getSharedPreferences("cra_boot_mirror", Context.MODE_PRIVATE).edit().clear().apply()
        assertEquals("升级前没有启动镜像", "system", BootMirror.themeMode(context))
        assertEquals(false, BootMirror.hasThemeMode(context))

        val upgraded = SettingsStore(context, scope)
        upgraded.appearance.first { it.themeMode == "dark" }

        assertEquals("首次读到 DataStore 的权威值后必须补齐镜像", "dark", BootMirror.themeMode(context))
        assertEquals(true, BootMirror.hasThemeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("下一次冷启动首帧应直接使用已回填的主题", "dark", nextColdStart.appearance.value.themeMode)
    }

    @Test
    fun `legacy datastore explicit light theme backfills a missing boot mirror after first load`() = runTest {
        val existingUser = SettingsStore(context, scope)
        existingUser.appearance.first()
        existingUser.updateAppearance { copy(themeMode = "light") }
        context.getSharedPreferences("cra_boot_mirror", Context.MODE_PRIVATE).edit().clear().apply()
        assertEquals("升级前没有启动镜像", "system", BootMirror.themeMode(context))
        assertEquals(false, BootMirror.hasThemeMode(context))

        val upgraded = SettingsStore(context, scope)
        upgraded.appearance.first { it.themeMode == "light" }

        assertEquals("首次读到 DataStore 的显式 light 后必须补齐镜像", "light", BootMirror.themeMode(context))
        assertEquals(true, BootMirror.hasThemeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("下一次冷启动首帧应直接使用已回填的 light 主题", "light", nextColdStart.appearance.value.themeMode)
    }

    @Test
    fun `legacy datastore default system theme marks mirror as present after first load`() = runTest {
        // 先确保底层 DataStore 存有 system，再抹去镜像，模拟升级前无镜像但 DataStore 默认跟随系统的用户。
        // 关键：themeMode 的默认值本身就是 "system"，仅等 themeMode 会在 DataStore 回填前就命中
        // StateFlow 的初始值（bootAppearance），无法证明「已加载权威数据并补齐镜像」。这里同时种入
        // 一个**非镜像、非初始**的外观字段 amoledPureBlack=true，等待条件必须同时满足两者，
        // 才能确保等到的是 DataStore 加载后的权威数据（初始 bootAppearance 的 amoledPureBlack=false）。
        val existingUser = SettingsStore(context, scope)
        existingUser.appearance.first()
        existingUser.updateAppearance { copy(themeMode = "system", amoledPureBlack = true) }
        context.getSharedPreferences("cra_boot_mirror", Context.MODE_PRIVATE).edit().clear().apply()
        assertEquals("升级前无镜像标记", false, BootMirror.hasThemeMode(context))
        assertEquals("升级前读取回落为 system", "system", BootMirror.themeMode(context))

        val upgraded = SettingsStore(context, scope)
        upgraded.appearance.first { it.themeMode == "system" && it.amoledPureBlack }

        assertEquals("首次加载后必须记录镜像已就绪状态", true, BootMirror.hasThemeMode(context))
        assertEquals("回填后镜像主题为 system", "system", BootMirror.themeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("冷启动首帧为 system", "system", nextColdStart.appearance.value.themeMode)
    }

    // ── S06 主题决议五大路径与系统映射测试 ────────────────────────────────────

    @Test
    fun `theme resolver handles device light and follow system correctly`() {
        val dark = ThemeResolver.resolveAppDarkTheme(themeMode = "system", isSystemDark = false)
        assertEquals("跟随系统且设备为浅色时，应用必须为浅色", false, dark)
    }

    @Test
    fun `theme resolver handles device dark and follow system correctly`() {
        val dark = ThemeResolver.resolveAppDarkTheme(themeMode = "system", isSystemDark = true)
        assertEquals("跟随系统且设备为深色时，应用必须为深色", true, dark)
    }

    @Test
    fun `theme resolver handles explicit light regardless of device state`() {
        assertEquals(
            "显式浅色且设备为深色时，应用必须强制为浅色",
            false,
            ThemeResolver.resolveAppDarkTheme(themeMode = "light", isSystemDark = true),
        )
        assertEquals(
            "显式浅色且设备为浅色时，应用必须为浅色",
            false,
            ThemeResolver.resolveAppDarkTheme(themeMode = "light", isSystemDark = false),
        )
    }

    @Test
    fun `theme resolver handles explicit dark regardless of device state`() {
        assertEquals(
            "显式深色且设备为浅色时，应用必须强制为深色",
            true,
            ThemeResolver.resolveAppDarkTheme(themeMode = "dark", isSystemDark = false),
        )
        assertEquals(
            "显式深色且设备为深色时，应用必须为深色",
            true,
            ThemeResolver.resolveAppDarkTheme(themeMode = "dark", isSystemDark = true),
        )
    }

    @Test
    fun `theme resolver transitions from explicit mode back to follow system dynamically`() {
        // 从显式深色切回跟随系统：
        // 切回前恒为深色
        assertEquals(true, ThemeResolver.resolveAppDarkTheme("dark", isSystemDark = false))
        // 切回后立即跟随系统当前深浅色，绝不固定浅色或深色
        assertEquals(
            "从 dark 切回 system 后，设备浅色时立即为浅色",
            false,
            ThemeResolver.resolveAppDarkTheme("system", isSystemDark = false),
        )
        assertEquals(
            "从 dark 切回 system 后，设备深色时立即为深色",
            true,
            ThemeResolver.resolveAppDarkTheme("system", isSystemDark = true),
        )

        // 从显式浅色切回跟随系统：
        // 切回前恒为浅色
        assertEquals(false, ThemeResolver.resolveAppDarkTheme("light", isSystemDark = true))
        // 切回后立即跟随系统当前深浅色
        assertEquals(
            "从 light 切回 system 后，设备深色时立即为深色",
            true,
            ThemeResolver.resolveAppDarkTheme("system", isSystemDark = true),
        )
        assertEquals(
            "从 light 切回 system 后，设备浅色时立即为浅色",
            false,
            ThemeResolver.resolveAppDarkTheme("system", isSystemDark = false),
        )
    }

    @Test
    fun `theme resolver falls back to system dark state for unknown modes`() {
        assertEquals(true, ThemeResolver.resolveAppDarkTheme("unknown", isSystemDark = true))
        assertEquals(false, ThemeResolver.resolveAppDarkTheme("unknown", isSystemDark = false))
    }

    @Test
    fun `boot mirror desiredNightMode maps explicit modes and reset contract`() {
        assertEquals(
            "显式深色映射为 MODE_NIGHT_YES",
            android.app.UiModeManager.MODE_NIGHT_YES,
            BootMirror.desiredNightMode("dark"),
        )
        assertEquals(
            "显式浅色映射为 MODE_NIGHT_NO",
            android.app.UiModeManager.MODE_NIGHT_NO,
            BootMirror.desiredNightMode("light"),
        )
        assertEquals(
            "跟随系统映射为 MODE_NIGHT_AUTO（AOSP 内部重置 UI_MODE_NIGHT_UNDEFINED 信号）",
            android.app.UiModeManager.MODE_NIGHT_AUTO,
            BootMirror.desiredNightMode("system"),
        )
        assertEquals(
            "未知模式安全回退为 MODE_NIGHT_AUTO",
            android.app.UiModeManager.MODE_NIGHT_AUTO,
            BootMirror.desiredNightMode("unrecognized"),
        )
    }
}
