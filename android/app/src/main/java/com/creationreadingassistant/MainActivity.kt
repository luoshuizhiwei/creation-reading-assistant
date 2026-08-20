package com.creationreadingassistant

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeys
import com.creationreadingassistant.ui.navigation.AppNavigation
import com.creationreadingassistant.ui.onboarding.OnboardingOverlay
import com.creationreadingassistant.ui.onboarding.isOnboardingCompleted
import com.creationreadingassistant.ui.theme.AppPalette
import com.creationreadingassistant.ui.theme.AppTheme
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
    private val homeContentReported = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Activity 必须先完成系统初始化。之前在崩溃回放分支中先 setContentView 后
        // return，会触发 SuperNotCalledException，并让一份旧日志造成永久闪退循环。
        super.onCreate(savedInstanceState)

        // 主路径必须先绘制。上次崩溃日志在首帧之后于 IO 线程回放。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        App.trace("MainActivity", "super.onCreate done")
        try {
            setContent {
                App.trace("MainActivity", "composable entered")
                val appearance by settingsStore.appearance.collectAsStateWithLifecycle()
                var showOnboarding by remember { mutableStateOf(!isOnboardingCompleted(this@MainActivity)) }
                val darkTheme = when (appearance.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
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
                    Box(Modifier.fillMaxSize()) {
                        AppNavigation()
                        if (showOnboarding) {
                            OnboardingOverlay(onClose = { showOnboarding = false })
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
