package com.creationreadingassistant.feature.reader.pager

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 自动灰度健康状态。只在新分页器实际显示期间安装未捕获异常观察器；
 * force-stop、系统回收、ANR 不会经过它，因此不会被误记成分页器崩溃。
 */
class PagerHealthStore(context: Context) {
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
            prefs.edit().putInt(KEY_CRASH_COUNT, (count + 1).coerceAtMost(2)).commit()
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
