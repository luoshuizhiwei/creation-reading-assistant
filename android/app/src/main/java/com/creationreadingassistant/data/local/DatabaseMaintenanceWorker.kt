package com.creationreadingassistant.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.creationreadingassistant.data.settings.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DATABASE_COMPACTION_WORK_NAME = "db_vacuum_after_index_removal"

/** Room / StatFs 与纯 compaction holder 之间的 Android 边界。 */
internal class RoomDatabaseCompactionBackend(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val context: Context,
) : DatabaseCompactionBackend {
    override suspend fun isDone(): Boolean = settingsStore.isDatabaseVacuumAfterIndexRemovalDone()

    override suspend fun markDone() = settingsStore.markDatabaseVacuumAfterIndexRemovalDone()

    override fun readMetrics(): DatabaseCompactionMetrics {
        val sqlite = database.openHelper.readableDatabase
        return DatabaseCompactionMetrics(
            pageCount = sqlite.longPragma("page_count"),
            pageSize = sqlite.longPragma("page_size"),
            freelistCount = sqlite.longPragma("freelist_count"),
        )
    }

    override fun totalBytes(): Long = databaseFilesTotalBytes(context)

    override fun hasOpenReadingSession(): Boolean =
        database.openHelper.readableDatabase.longQuery(
            "SELECT COUNT(*) FROM reading_sessions WHERE ended_at IS NULL AND deleted_at IS NULL",
        ) > 0L

    override fun checkpointAndVacuum() {
        val sqlite = database.openHelper.writableDatabase
        // checkpoint 有结果行，必须走 query；VACUUM 不在 Room/ SQLite 事务内执行。
        sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        sqlite.execSQL("VACUUM")
    }
}

internal class StatFsDatabaseSpaceProvider(
    private val context: Context,
) : DatabaseSpaceProvider {
    override fun availableBytes(): Long {
        val path = context.getDatabasePath(AppDatabase.DB_NAME).parentFile?.absolutePath ?: "/data"
        return StatFs(path).availableBytes
    }
}

internal fun databaseFilesTotalBytes(context: Context): Long {
    val database = context.getDatabasePath(AppDatabase.DB_NAME)
    return listOf(database, File(database.path + "-wal"), File(database.path + "-shm"))
        .filter { it.isFile }
        .sumOf { it.length() }
}

internal fun isAppForeground(context: Context): Boolean {
    val importance = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.runningAppProcesses?.firstOrNull { it.processName == context.packageName }?.importance
    }.getOrNull() ?: return true // 无法判定时按前台处理，宁可不执行也不阻塞用户。
    return importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
}

private fun SupportSQLiteDatabase.longPragma(name: String): Long = longQuery("PRAGMA $name")

private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
    check(cursor.moveToFirst()) { "数据库查询没有返回结果: $sql" }
    cursor.getLong(0)
}

/** 仅登记任务；调用方必须位于后台线程，不能在 Application.onCreate 主线程执行。 */
class DatabaseMaintenanceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun startOnce() {
        val request = OneTimeWorkRequestBuilder<DatabaseMaintenanceWorker>()
            // 首屏先完成；真正执行时还会再次检查后台与阅读会话。
            .setInitialDelay(60L, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
            .build()
        // 不 await Operation：这里只登记，首屏不依赖任务初始化或执行结果。
        WorkManager.getInstance(context).enqueueUniqueWork(
            DATABASE_COMPACTION_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class DatabaseMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val compaction = EntryPointAccessors.fromApplication(
            applicationContext,
            DatabaseMaintenanceEntryPoint::class.java,
        ).compaction()
        return when (compaction.compactOnce().reason) {
            DatabaseCompactionReason.SUCCESS,
            DatabaseCompactionReason.ALREADY_DONE,
            DatabaseCompactionReason.BELOW_THRESHOLD
            -> Result.success()
            DatabaseCompactionReason.INSUFFICIENT_SPACE,
            DatabaseCompactionReason.DEFERRED_FOREGROUND,
            DatabaseCompactionReason.DEFERRED_READING_SESSION,
            DatabaseCompactionReason.FAILED
            -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseMaintenanceEntryPoint {
    fun databaseMaintenanceScheduler(): DatabaseMaintenanceScheduler
    fun compaction(): DatabaseCompaction
}
