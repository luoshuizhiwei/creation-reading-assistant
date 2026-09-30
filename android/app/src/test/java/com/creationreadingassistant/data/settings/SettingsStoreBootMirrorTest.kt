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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
 *
 * ## 等待口径（2026-09-30 隔离修复）
 *
 * `appearance` / `reader` 都是 `ds.data.map { ... }.stateIn(scope, WhileSubscribed, 镜像值)`。
 * 由此有两个必须记住的事实：
 *
 * 1. **StateFlow 的 `first()` / `first { 值谓词 }` 会立刻命中初始（镜像/默认）值并返回**，
 *    所以 `appearance.first()` **不是**「等磁盘状态加载」（旧注释如此声称，但实现并非如此）；
 * 2. **StateFlow 会合并相等的值**：当 DataStore 读出的值恰好等于初始值时上游不向下游发射，
 *    而镜像回填副作用发生在**上游 `map` 内部**（先于 `state.value =` 赋值）——「下游收到
 *    发射」与「回填已发生」是两件事。
 *
 * 旧写法把「镜像已回填」建立在值谓词等待上，于是断言变成「上游是否刚好抢在断言前跑完」的
 * 时序赌博：2026-09-30 联合全量首轮
 * `legacy datastore default system theme marks mirror as present after first load`
 * 就在 `SettingsStoreBootMirrorTest.kt:146` 失败（期望 `hasThemeMode == true` 实得 `false`）
 * —— 等待返回了、回填却还没跑。
 *
 * 因此本类统一改为**订阅上游 + 等可观测副作用**（启动镜像被回填），见
 * [awaitAppearanceUpstream] / [awaitBootThemeBackfilled]；并用
 * [appearance state flow holds the mirror value while data store holds the authoritative value]
 * 把这条机制固定成**确定性**回归用例。
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
        // 资源关闭：本类在 scope 上启动的迁移/共享协程（SettingsStore.init 的
        // migrateLegacyReaderDefaultsOnce、stateIn 的 WhileSubscribed 上游）都在这里停止，
        // 不会带着 mock Context 跨测试方法存活。
        scope.cancel()
        unmockkObject(SecurePrefs)
    }

    /**
     * 等「DataStore → [SettingsStore.appearance] 上游」真正跑过一次，并（可选）等镜像回填到 [expected]。
     *
     * 为什么不能写成 `appearance.first { ... }`：
     * 1. `appearance` 是 `stateIn(scope, WhileSubscribed, initialValue = 镜像值)`。**StateFlow 会
     *    合并相等的值**：当 DataStore 读出的 `AppearanceSettings` 恰好等于初始值时（默认主题 +
     *    默认调色板就是这种情况），上游**不会**向下游发射 —— 于是 `first { 值谓词 }` 要么立刻
     *    命中初始值返回、要么永远等不到发射，两种都不是「上游已跑过」的证据；
     * 2. 而镜像回填副作用发生在**上游 `map` 内部**（先于 `state.value =` 赋值）。也就是说
     *    「下游有没有收到发射」与「回填有没有发生」本来就是两件事。
     *
     * 因此这里显式**订阅上游**（`collect` 只保持订阅、不依赖是否发射），再在真实时间上等那个
     * 副作用出现。轮询带超时，失败信息给出实际镜像状态，不会静默通过。
     */
    private suspend fun awaitAppearanceUpstream(store: SettingsStore, expected: String? = null) {
        val subscription = scope.launch { store.appearance.collect { } }
        try {
            withContext(Dispatchers.Default) {
                val deadline = System.nanoTime() + WAIT_TIMEOUT_NANOS
                while (true) {
                    val has = BootMirror.hasThemeMode(context)
                    val mode = BootMirror.themeMode(context)
                    if (has && (expected == null || mode == expected)) return@withContext
                    if (System.nanoTime() > deadline) {
                        throw AssertionError(
                            "等待 appearance 上游回填启动镜像超时：" +
                                "expected=${expected ?: "<任意>"}, hasThemeMode=$has, themeMode=$mode",
                        )
                    }
                    Thread.sleep(5)
                }
            }
        } finally {
            subscription.cancel()
        }
    }

    /** 等启动镜像被回填为 [expected]（同时保证上游已跑过一次）。 */
    private suspend fun awaitBootThemeBackfilled(store: SettingsStore, expected: String) {
        awaitAppearanceUpstream(store, expected)
    }

    /** 抹掉启动镜像，模拟「从未带 BootMirror 的旧版本升级」。 */
    private fun clearBootMirror() {
        context.getSharedPreferences("cra_boot_mirror", Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 底层 DataStore 里 `appearance_theme_mode` 的权威值（不经过 StateFlow 缓存）。 */
    private suspend fun storedThemeMode(store: SettingsStore): String? =
        store.preferencesDataStore.data.first()
            .asMap()
            .entries
            .firstOrNull { it.key.name == "appearance_theme_mode" }
            ?.value as? String

    @Test
    fun `cold start appearance first value uses stored theme instead of default`() = runTest {
        val first = SettingsStore(context, scope)
        // 等权威值真正加载进 StateFlow（不是等它的初始值）。
        awaitAppearanceUpstream(first)
        first.updateAppearance { copy(themeMode = "dark") }
        assertEquals("dark", storedThemeMode(first))

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
        awaitAppearanceUpstream(first)
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
        awaitAppearanceUpstream(existingUser)
        existingUser.updateAppearance { copy(themeMode = "dark") }
        clearBootMirror()
        assertEquals("升级前没有启动镜像", "system", BootMirror.themeMode(context))
        assertEquals(false, BootMirror.hasThemeMode(context))

        val upgraded = SettingsStore(context, scope)
        awaitBootThemeBackfilled(upgraded, "dark")

        assertEquals("首次读到 DataStore 的权威值后必须补齐镜像", "dark", BootMirror.themeMode(context))
        assertEquals(true, BootMirror.hasThemeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("下一次冷启动首帧应直接使用已回填的主题", "dark", nextColdStart.appearance.value.themeMode)
    }

    @Test
    fun `legacy datastore explicit light theme backfills a missing boot mirror after first load`() = runTest {
        val existingUser = SettingsStore(context, scope)
        awaitAppearanceUpstream(existingUser)
        existingUser.updateAppearance { copy(themeMode = "light") }
        clearBootMirror()
        assertEquals("升级前没有启动镜像", "system", BootMirror.themeMode(context))
        assertEquals(false, BootMirror.hasThemeMode(context))

        val upgraded = SettingsStore(context, scope)
        awaitBootThemeBackfilled(upgraded, "light")

        assertEquals("首次读到 DataStore 的显式 light 后必须补齐镜像", "light", BootMirror.themeMode(context))
        assertEquals(true, BootMirror.hasThemeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("下一次冷启动首帧应直接使用已回填的 light 主题", "light", nextColdStart.appearance.value.themeMode)
    }

    @Test
    fun `legacy datastore default system theme marks mirror as present after first load`() = runTest {
        // 先确保底层 DataStore 存有 system，再抹去镜像，模拟升级前无镜像但 DataStore 默认跟随系统的用户
        val existingUser = SettingsStore(context, scope)
        awaitAppearanceUpstream(existingUser)
        existingUser.updateAppearance { copy(themeMode = "system") }
        clearBootMirror()
        assertEquals("升级前无镜像标记", false, BootMirror.hasThemeMode(context))
        assertEquals("升级前读取回落为 system", "system", BootMirror.themeMode(context))

        val upgraded = SettingsStore(context, scope)
        // 这里必须等**回填副作用**，不能等值谓词：期望值 system 恰等于镜像/默认初始值，
        // `first { it.themeMode == "system" }` 会立刻命中初始值返回，回填却可能还没跑。
        awaitBootThemeBackfilled(upgraded, "system")

        assertEquals("首次加载后必须记录镜像已就绪状态", true, BootMirror.hasThemeMode(context))
        assertEquals("回填后镜像主题为 system", "system", BootMirror.themeMode(context))
        val nextColdStart = SettingsStore(context, scope)
        assertEquals("冷启动首帧为 system", "system", nextColdStart.appearance.value.themeMode)
    }

    // ── 根因守卫：StateFlow 的值谓词不是 DataStore 的同步点 ────────────────────

    /**
     * 把 2026-09-30 偶发失败的机制固定成**确定性**回归用例（不依赖时序）。
     *
     * 让 DataStore 的权威值为 `dark`、镜像抹空，于是新实例的 StateFlow 在未被订阅时只有
     * 初始值 `system` —— 它与 DataStore 的 `dark` **不同**，也正是
     * `first { it.themeMode == "system" }` 能够立刻命中的那个值。所以值谓词等待不能作为
     * 「上游已跑过 / 镜像已回填」的证据；只有等副作用（[awaitBootThemeBackfilled]）才可以。
     */
    @Test
    fun `appearance state flow holds the mirror value while data store holds the authoritative value`() = runTest {
        val seeded = SettingsStore(context, scope)
        awaitAppearanceUpstream(seeded)
        seeded.updateAppearance { copy(themeMode = "dark") }
        assertEquals(
            "权威单事实源（底层 DataStore）应已写入 dark",
            "dark",
            storedThemeMode(seeded),
        )

        clearBootMirror()

        val restarted = SettingsStore(context, scope)
        assertEquals(
            "未订阅时 StateFlow 只有初始值：来自镜像/默认，不是 DataStore 的 dark",
            "system",
            restarted.appearance.value.themeMode,
        )
        assertEquals(
            "同一时刻 DataStore 的权威值仍是 dark",
            "dark",
            storedThemeMode(restarted),
        )

        // 对照：等副作用则必然等到上游真的补回镜像为止（确定性，且不可能被初始值命中）。
        awaitBootThemeBackfilled(restarted, "dark")
        assertTrue("等到副作用后镜像必然存在", BootMirror.hasThemeMode(context))
        assertEquals("且回填的是 DataStore 的权威值 dark", "dark", BootMirror.themeMode(context))
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

    private companion object {
        /** 等上游副作用的真实时间上限：DataStore 首读 + 一次 map 的正常耗时远低于此。 */
        const val WAIT_TIMEOUT_NANOS = 10_000_000_000L
    }
}
