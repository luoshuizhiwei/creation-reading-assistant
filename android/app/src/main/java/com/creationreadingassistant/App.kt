package com.creationreadingassistant

import android.app.Application
import android.content.Context
import android.util.Log
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 代码生成，必须在 AndroidManifest 的
 * application android:name=".App" 中引用。
 *
 * 关键：未捕获异常处理器在 attachBaseContext（最早的代码执行点）就装好，
 * 覆盖 Hilt 图谱构建 / 类加载等最早期的崩溃。初始化失败会先落盘再交还
 * Android 默认处理器，不能吞掉异常后带着不完整的依赖图继续运行。
 */
@HiltAndroidApp
class App : Application() {

    companion object {
        /** 同步落盘启动轨迹，便于无 adb 时也能看出崩在哪一步。 */
        @JvmStatic
        internal fun trace(tag: String, msg: String) {
            runCatching {
                val dir = AppContextHolder.cacheDir ?: return@runCatching
                val f = File(dir, "cra_startup.log")
                val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                f.appendText("[$ts] $tag: $msg\n")
            }
        }

        internal fun writeFatal(prefix: String, stack: String) {
            runCatching {
                val dir = AppContextHolder.cacheDir ?: return@runCatching
                val f = File(dir, "cra_fatal.log")
                val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
                f.appendText("==== $ts $prefix ====\n$stack\n\n")
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 最早的代码执行点：先缓存 cacheDir 并安装未捕获处理器，
        // 覆盖 Hilt 初始化 / 类加载等 onCreate 之前阶段的崩溃。
        AppContextHolder.cacheDir = cacheDir
        installCrashHandler()
        trace("App", "attachBaseContext")
    }

    override fun onCreate() {
        AppContextHolder.cacheDir = cacheDir
        trace("App", "onCreate start")
        try {
            super.onCreate()
            trace("App", "super.onCreate done (Hilt graph built OK)")
        } catch (t: Throwable) {
            val stack = Log.getStackTraceString(t)
            AppLog.e("AppInit", stack)
            writeFatal("App.onCreate/super", stack)
            throw t
        }
        AppLog.i("App", "应用启动")
        trace("App", "onCreate end")
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = Log.getStackTraceString(throwable)
            AppLog.e("Crash", stack)
            writeFatal("uncaught/${thread.name}", stack)
            default?.uncaughtException(thread, throwable)
        }
    }
}

/** 在 attachBaseContext 时缓存 cacheDir，供 companion 静态日志使用。 */
internal object AppContextHolder {
    var cacheDir: File? = null
}
