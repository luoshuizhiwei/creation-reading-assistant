package com.creationreadingassistant

import android.app.Application
import android.content.Context
import android.os.Trace
import android.util.Log
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.EpubSizeRepairTask
import com.creationreadingassistant.ui.onboarding.ONBOARDING_PREFS
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
        /** 启动接线（后台线程）的 logcat tag，便于过滤启动阶段失败。 */
        private const val TAG_BOOT = "CRA-Boot"

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
                    .ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.time.LocalDateTime.now())
                val rt = Runtime.getRuntime()
                val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                val heapTotalMb = rt.totalMemory() / (1024 * 1024)
                val heapMaxMb = rt.maxMemory() / (1024 * 1024)
                val versionStr = AppContextHolder.appVersion ?: "unknown"
                val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                    "(Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT}, App: $versionStr)"
                val memInfo = "Heap: ${heapUsedMb}MB used / ${heapTotalMb}MB total (max ${heapMaxMb}MB)"
                f.appendText("==== $ts $prefix ====\n$deviceInfo\n$memInfo\n$stack\n\n")
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 最早的代码执行点：先缓存 cacheDir 并安装未捕获处理器，
        // 覆盖 Hilt 初始化 / 类加载等最早期的崩溃。
        AppContextHolder.cacheDir = cacheDir
        base?.let { ctx ->
            runCatching {
                val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                @Suppress("DEPRECATION")
                val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    pInfo.versionCode.toLong()
                }
                AppContextHolder.appVersion = "${pInfo.versionName} ($vCode)"
            }
            // 预热 app_prefs：首次 getSharedPreferences() 要创建 preferences 目录、stat
            // 并解析 XML，在部分设备上实测 ~250ms（StrictMode DiskReadViolation）。
            // 它原本发生在 MainActivity 的 Compose 组合期（isOnboardingCompleted()），
            // 属首帧关键路径；这里提前到进程最早执行点 —— 与上面的 getPackageInfo
            // 同一次磁盘预热窗口，把这份开销从「首帧前」挪到「窗口未显示时」。
            runCatching { ctx.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE) }
        }
        installCrashHandler()
        trace("App", "attachBaseContext")
    }

    override fun onCreate() {
        AppContextHolder.cacheDir = cacheDir
        // 调试日志守卫：语义等同 BuildConfig.DEBUG（本工程 AGP 未启用 buildConfig 生成，
        // 改用 FLAG_DEBUGGABLE 判定；release 包恒为 false）。
        AppLog.debugEnabled =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // A5 主线程 I/O 探测工具：仅调试构建启用 StrictMode，violation 只记日志不崩溃
        // （penaltyLog），release 无影响。日志 tag 为 StrictMode，可通过 adb logcat 过滤。
        if (AppLog.debugEnabled) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build(),
            )
        }
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
        // 让系统按应用内主题解析本应用资源（API 31+）：启动窗口由 system_server 在进程
        // 启动前创建，只有把显式选择交给系统，冷启动首帧才是正确的深浅。老装机（镜像已有显式值但
        // 尚未同步给系统）也在这里收敛。默认/跟随系统模式不主动向系统写入 MODE_NIGHT_AUTO，
        // 避免在定制 ROM 上将干净的跟随系统状态污染为 AUTO 自动调度。失败静默 —— 最坏退回旧行为，不影响启动。
        runCatching {
            val mode = com.creationreadingassistant.data.settings.BootMirror.themeMode(this)
            if (mode != "system") {
                com.creationreadingassistant.data.settings.BootMirror
                    .applyApplicationNightMode(this, mode)
            }
        }
        // 三项启动接线一律放到后台线程，不在主线程串行执行。
        //
        // 为什么必须这样：`EntryPointAccessors.fromApplication(...)` 会即时构建该 EntryPoint
        // 的 Hilt 子图，连带构造 AppDatabase 单例，而 [DatabaseModule.provideDatabase] 里
        // 会调用 [DatabaseSafetyNet.snapshotIfUpgrading] —— 版本升级那一次要把**整个库文件**
        // 复制一份（本机实测库可达 319MB），在主线程上就是数秒级冻结。即使是普通启动，
        // 这三步也各自带磁盘 I/O（WorkManager 库、通知渠道、文件头读取），StrictMode 在
        // 真机上抓到了对应的 DiskReadViolation。
        //
        // 三项都是「发起即忘」的后台初始化，首帧不依赖它们完成：EPUB 修复是异步修复任务、
        // 目标提醒只注册渠道并排期、搜索索引只入队 WorkManager。放到后台既不改变语义，
        // 也把这段耗时从首帧关键路径上摘掉。
        Thread({
            runCatching {
                EntryPointAccessors.fromApplication(this, EpubRepairEntryPoint::class.java)
                    .epubSizeRepairTask()
                    .startOnce()
            }.onFailure { Log.w(TAG_BOOT, "EPUB 修复任务启动失败", it) }
            // 阅读目标提醒（P3.2 片 3）：注册渠道 + 对账 WorkManager 调度
            runCatching {
                EntryPointAccessors.fromApplication(this, com.creationreadingassistant.feature.goal.GoalSchedulerEntryPoint::class.java)
                    .scheduler()
                    .start()
            }.onFailure { Log.w(TAG_BOOT, "阅读目标调度启动失败", it) }
            // 全文搜索索引（R2-S1）：入队一次性 + 每日周期 WorkManager 建索引任务。
            // 修复：此前 SearchIndexScheduler 从未被任何生产代码调用，SearchIndexWorker 因此
            // 从不入队，search_terms 恒为空 —— 全局搜索的「正文」通道始终没有数据可查，
            // 界面会长期停在「全文索引尚未建立」。这里与 GoalScheduler 同款接线。
            runCatching {
                EntryPointAccessors.fromApplication(this, SearchIndexEntryPoint::class.java)
                    .searchIndexScheduler()
                    .start()
            }.onFailure { Log.w(TAG_BOOT, "搜索索引调度启动失败", it) }
        }, "cra-startup").start()
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

/** 在 attachBaseContext 时缓存 cacheDir 与应用版本，供 companion 静态日志使用。 */
internal object AppContextHolder {
    var cacheDir: File? = null
    var appVersion: String? = null
}

/** Application 级入口：获取进程级一次性任务实例，避免在 App 里直接注入依赖。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EpubRepairEntryPoint {
    fun epubSizeRepairTask(): EpubSizeRepairTask
}

/** WorkManager 进程外重建时获取全文搜索索引仓储的 Hilt 入口。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchIndexEntryPoint {
    fun repository(): SearchIndexRepository

    /**
     * 方法名不能叫 `scheduler()`：Hilt 把同模块所有 `@EntryPoint` 合成到同一个
     * `SingletonC` 组件类，而 [com.creationreadingassistant.feature.goal.GoalSchedulerEntryPoint]
     * 已有同签名的 `scheduler()`（返回 `ReadingGoalScheduler`），Java 不允许同一个类里
     * 出现「同名同参、仅返回类型不同」的两个方法 —— 会直接编译失败。
     */
    fun searchIndexScheduler(): SearchIndexScheduler
}
