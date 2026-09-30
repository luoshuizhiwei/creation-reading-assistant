package com.creationreadingassistant

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.creationreadingassistant.data.settings.BootMirror
import com.creationreadingassistant.data.settings.DurationPrefs
import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.ThemeResolver
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeys
import com.creationreadingassistant.ui.navigation.AppNavigation
import com.creationreadingassistant.ui.onboarding.OnboardingOverlay
import com.creationreadingassistant.ui.onboarding.isOnboardingCompleted
import com.creationreadingassistant.ui.theme.AppPalette
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import com.creationreadingassistant.ui.window.SystemBarsEffect
import com.creationreadingassistant.ui.window.SystemBarsPolicy
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var durationPrefs: DurationPrefs
    private val homeContentReported = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动窗口主题必须在 super.onCreate 之前定下来：系统按 manifest 的
        // Theme.CreationReadingAssistant 画出第一帧窗口背景，而该主题是浅色父主题
        // （values-night 只在**系统**深色时生效）。用户在应用内单独设了深色、系统却是
        // 浅色时，这一帧就是浅色 —— 表现为「夜间模式进应用先闪一下日间再变回来」。
        // 这里用启动镜像同步读回应用内主题，覆盖窗口主题，让首帧就是正确的深浅。
        applyBootWindowTheme()
        super.onCreate(savedInstanceState)

        // 主路径必须先绘制。上次崩溃日志在首帧之后于 IO 线程回放。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        App.trace("MainActivity", "super.onCreate done")
        try {
            setContent {
                App.trace("MainActivity", "composable entered")
                val appearance by settingsStore.appearance.collectAsStateWithLifecycle()
                // 时长显示单位（全局单一偏好）：根部提供一次，子树各展示位直接取用，
                // 不必把「怎么看时间」这种展示偏好污染到每个页面的 UiState 链上。
                // 长按任意时长文本 → 小时 ⇄ 天，写 DataStore 持久化；用 Toast 回执，
                // 因为长按发生在任意页面，而各页面的 SnackbarHost 互不相通。
                val durationUnit by durationPrefs.unit.collectAsStateWithLifecycle()
                val unitScope = rememberCoroutineScope()
                val context = LocalContext.current
                val toggleDurationUnit: () -> Unit = remember(durationPrefs, unitScope, context) {
                    {
                        unitScope.launch {
                            val next = durationPrefs.toggle()
                            Toast.makeText(
                                context,
                                if (next == DurationUnit.DAYS) {
                                    "阅读时长已改为按「天」显示"
                                } else {
                                    "阅读时长已改为按「小时」显示"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                var showOnboarding by remember { mutableStateOf(!isOnboardingCompleted(this@MainActivity)) }
                val darkTheme = ThemeResolver.resolveAppDarkTheme(
                    themeMode = appearance.themeMode,
                    isSystemDark = isSystemInDarkTheme(),
                )
                // 全局系统栏策略 base：普通页显示状态栏、隐藏导航栏（transient swipe）。
                // 阅读器内部由 SystemBarsEffect 覆盖注册；退出后宿主自动回退本策略。
                SystemBarsEffect(
                    policy = SystemBarsPolicy.normal(appDark = darkTheme),
                    base = true,
                )
                AppTheme(
                    darkTheme = darkTheme,
                    palette = AppPalette.fromStored(appearance.colorPalette),
                    useDynamicColor = appearance.useDynamicColor,
                    amoledPureBlack = appearance.amoledPureBlack,
                ) {
                    CompositionLocalProvider(
                        LocalDurationUnit provides durationUnit,
                        LocalDurationUnitToggle provides toggleDurationUnit,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppNavigation()
                            if (showOnboarding) {
                                OnboardingOverlay(onClose = { showOnboarding = false })
                            }
                        }
                    }
                }
            }
            App.trace("MainActivity", "setContent done")
        } catch (e: Throwable) {
            // 同步组合期崩溃：原生 TextView 兜底（可重复调用，不与 Compose 冲突）。
            AppLog.e("Boot", Log.getStackTraceString(e))
            App.writeFatal("MainActivity.setContent", Log.getStackTraceString(e))
            showErrorScreen(e)
        }
    }

    /**
     * 用启动镜像里的应用内主题覆盖启动窗口主题。
     *
     * 必须在 `super.onCreate` 之前调用：系统先按 manifest 主题画窗口背景，之后
     * Compose 才接管。`values-night/` 只能覆盖「跟随系统 + 系统深色」，覆盖不了
     * 「应用内单独设深色而系统是浅色」—— 那正是用户报的「夜间模式进应用先闪日间」。
     *
     * 只处理明确的 dark/light；`system` 交给 values-night 按系统判定，不做多余覆盖。
     */
    private fun applyBootWindowTheme() {
        val mode = runCatching { BootMirror.themeMode(this) }.getOrDefault("system")
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = ThemeResolver.resolveAppDarkTheme(mode, isSystemDark)
        setTheme(
            if (dark) R.style.Theme_CreationReadingAssistant_Dark
            else R.style.Theme_CreationReadingAssistant,
        )
    }

    internal fun onHomeContentReady() {
        if (!homeContentReported.compareAndSet(false, true)) return
        reportFullyDrawn()
        replayDeferredStartupWork()
    }

    private fun replayDeferredStartupWork() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dir = runCatching { cacheDir }.getOrNull() ?: return@withContext null
                val fatal = File(dir, "cra_fatal.log")
                val text = runCatching {
                    fatal.takeIf(File::exists)?.readText()?.trim()
                }.getOrNull()
                if (!text.isNullOrEmpty()) {
                    runCatching { fatal.renameTo(File(dir, "cra_fatal.shown")) }
                }
                TextStreamLoader(dir).cleanupStaleTempFiles()
                text?.takeIf(String::isNotEmpty)
            }
            result?.let { AppLog.e("PreviousCrash", it) }
        }
    }

    // 公开稳定 seam：repeat DOWN 会进入 onKeyDown，UP（含系统取消的 FLAG_CANCELED）
    // 会进入 onKeyUp；按键桥消费后不再交回系统（音量键翻页），否则交回 super
    // 保证系统音量等默认行为正常。
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ReaderHardwareKeys.dispatch(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ReaderHardwareKeys.dispatch(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** 本次启动失败兜底：直接把异常类型 + 完整栈显示出来。 */
    private fun showErrorScreen(e: Throwable) {
        val msg = "本次启动失败（已被捕获，未闪退）：\n\n" +
            (e.message ?: e.javaClass.name) + "\n\n" +
            Log.getStackTraceString(e)
        val tv = TextView(this).apply {
            text = msg
            setTextColor(0xFFF3EFE6.toInt())
            setBackgroundColor(0xFF1A1917.toInt())
            textSize = 11f
            setPadding(36, 56, 36, 56)
            isVerticalScrollBarEnabled = true
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
