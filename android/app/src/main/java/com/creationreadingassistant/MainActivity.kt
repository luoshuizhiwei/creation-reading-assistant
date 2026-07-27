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
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeys
import com.creationreadingassistant.ui.navigation.AppNavigation
import com.creationreadingassistant.ui.onboarding.OnboardingOverlay
import com.creationreadingassistant.ui.onboarding.isOnboardingCompleted
import com.creationreadingassistant.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Activity 必须先完成系统初始化。之前在崩溃回放分支中先 setContentView 后
        // return，会触发 SuperNotCalledException，并让一份旧日志造成永久闪退循环。
        super.onCreate(savedInstanceState)

        // 1) 读取并回放上次崩溃日志。此时 Activity 已完成初始化，可以安全显示原生视图。
        val cacheDir = runCatching { cacheDir }.getOrNull()
        val fatalFile = cacheDir?.let { File(it, "cra_fatal.log") }
        val fatalText = runCatching { fatalFile?.takeIf { f -> f.exists() }?.readText()?.trim() }.getOrNull()

        if (!fatalText.isNullOrEmpty()) {
            // 保留上次异常用于诊断，但不能阻塞本次正常启动。
            // 旧实现会让用户每次崩溃后的第一次启动停在诊断页。
            AppLog.e("PreviousCrash", fatalText)
            runCatching { fatalFile?.renameTo(File(cacheDir, "cra_fatal.shown")) }
        }

        // 2) 主路径。
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
                AppTheme(darkTheme = darkTheme) {
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        ReaderHardwareKeys.dispatch(event) || super.dispatchKeyEvent(event)

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
