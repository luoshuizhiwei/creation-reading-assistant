package com.creationreadingassistant

import android.app.Application
import android.content.Context
import android.os.Trace
import android.util.Log
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.EpubSizeRepairTask
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import java.io.File

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
        /** 启动轨迹写入系统 trace，绝不在首帧关键路径同步落盘。 */
        @JvmStatic
        internal fun trace(tag: String, msg: String) {
            Trace.beginSection("CRA:$tag:$msg")
            Trace.endSection()
        }

        internal fun writeFatal(prefix: String, stack: String) {
            runCatching {
                val dir = AppContextHolder.cacheDir ?: return@runCatching
                val f = File(dir, "cra_fatal.log")
                val ts = java.time.format.DateTimeFormatter
                    .ofPattern("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.time.LocalDateTime.now())
                f.appendText("==== $ts $prefix ====\n$stack\n\n")
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 最早的代码执行点：先缓存 cacheDir 并安装未捕获处理器，
        // 覆盖 Hilt 初始化 / 类加载等最早期的崩溃。
        AppContextHolder.cacheDir = cacheDir
        installCrashHandler()
        trace("App", "attachBaseContext")
    }

    override fun onCreate() {
        AppContextHolder.cacheDir = cacheDir
        // 调试日志守卫：语义等同 BuildConfig.DEBUG（本工程 AGP 未启用 buildConfig 生成，
        // 改用 FLAG_DEBUGGABLE 判定；release 包恒为 false）。
        AppLog.debugEnabled =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // 在 profileable/benchmark 构建中强制启用应用级 atrace，使自定义 Trace section
        // 能被 Perfetto 捕获，用于性能分析；release 用户构建无影响（非 debuggable
        // 且非 profileable 时该方法无效果）。
        runCatching {
            Trace::class.java.getMethod("forceEnableAppTracing").invoke(null)
        }
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
        // EPUB size 修复：进程级一次性懒启动任务（替代此前每个 ShelfViewModel 实例
        // init 各自触发一次的重复执行）。
        EntryPointAccessors.fromApplication(this, EpubRepairEntryPoint::class.java)
            .epubSizeRepairTask()
            .startOnce()
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

/** Application 级入口：获取进程级一次性任务实例，避免在 App 里直接注入依赖。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EpubRepairEntryPoint {
    fun epubSizeRepairTask(): EpubSizeRepairTask
}
