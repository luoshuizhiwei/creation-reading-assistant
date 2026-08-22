package com.creationreadingassistant.feature.reader.pager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动灰度健康状态。只在新分页器实际显示期间安装未捕获异常观察器；
 * force-stop、系统回收、ANR 不会经过它，因此不会被误记成分页器崩溃。
 *
 * 走 @Singleton Hilt 注入的原因：
 * 1. 全局只需要一份 SharedPreferences 引用和 crash count。
 * 2. installCrashGuard() 操作的是全局 Thread.UncaughtExceptionHandler，
 *    单进程里永远只该有一个最新 guard；Composable remember 创建的对象
 *    无法防止不同 Activity/多实例的竞态。
 * 3. isAutoDisabled 崩溃计数阈值是整个进程级的健康闸门，
 *    不该因为 Composable 重组创建新对象而重置。
 */
@Singleton
class PagerHealthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isAutoDisabled: Boolean get() = prefs.getInt(KEY_CRASH_COUNT, 0) >= 2

    fun clearAfterStableRead() {
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
    }

    fun installCrashGuard(): AutoCloseable {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val closed = AtomicBoolean(false)
        val guard = Thread.UncaughtExceptionHandler { thread, throwable ->
            val count = prefs.getInt(KEY_CRASH_COUNT, 0)
            prefs.edit().putInt(KEY_CRASH_COUNT, (count + 1).coerceAtMost(2)).apply()
            previous?.uncaughtException(thread, throwable)
        }
        Thread.setDefaultUncaughtExceptionHandler(guard)
        return AutoCloseable {
            if (closed.compareAndSet(false, true) &&
                Thread.getDefaultUncaughtExceptionHandler() === guard
            ) {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }
        }
    }

    companion object {
        private const val PREFS = "reader_pager_health"
        private const val KEY_CRASH_COUNT = "consecutive_crash_count"
    }
}
