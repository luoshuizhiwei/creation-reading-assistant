package com.creationreadingassistant.data.local

import android.content.Context
import android.util.Log
import com.creationreadingassistant.feature.log.AppLog
import java.io.File

/**
 * 升级前的保险丝：Room 打开数据库之前，若磁盘上的版本低于代码版本，先整份复制一份快照。
 *
 * **为什么必须有这个东西。**
 * [DatabaseModule] 上挂着 `fallbackToDestructiveMigration()`。它的语义是：
 * 迁移一旦失败（抛异常、或者压根没有对应版本的迁移路径），Room **不会崩溃**，
 * 而是删掉整个库重建一个空的。用户看到的现象是「打开应用，书、进度、高亮、笔记、
 * 灵感、同步状态全没了」，并且没有任何报错、没有任何提示、不可撤销。
 *
 * 2026-07-27 就差点发生：手工建的部分索引让 Room 的迁移后校验必定失败
 * （详见 [AppDatabase.dropPartialIndexes]）。那次运气好，抛的是 IllegalStateException
 * 且发生在 `onUpgrade` 内部，直接闪退了 —— 用户至少知道出事了。换一种失败姿势就是静默清库。
 *
 * 有了这份快照，最坏结果从「数据永久消失」降级为「数据还在文件里，能捞回来」。
 *
 * **代价可以忽略。** 库文件是几百 KB 量级，且只在「磁盘版本 < 代码版本」的那一次启动复制一次，
 * 之后每次启动都是一次 file header 读取（100 字节）后直接返回。
 *
 * 快照放在 `noBackupFilesDir`，不进系统云备份 —— 它是本地兜底，不是备份产品。
 */
object DatabaseSafetyNet {

    private const val TAG = "DbSafetyNet"

    /** 快照子目录名。 */
    internal const val SNAPSHOT_DIR = "db-snapshots"

    /** 保留最近几份快照。2 份足够覆盖「升级 → 发现不对 → 再升级一次」的场景。 */
    internal const val KEEP_SNAPSHOTS = 2

    /**
     * 整库快照的体积上限。超过就**不复制**。
     *
     * 原因：这份复制发生在 Room 打开库之前、且是同步 I/O。2026-11-17 Mate 70 实测：
     * 用户库 5.17 GB + WAL 987 MB，一次复制 90 秒以上，主线程被完全占住，
     * 兼容容器在 9–12 秒就把进程判为无响应杀掉 —— 迁移永远执行不到，
     * 而每次启动都会重来一遍（库始终停在 v15）。
     * [DatabaseModule] 已移除 fallbackToDestructiveMigration()，迁移失败会抛异常而不是静默清库，
     * 所以对大库跳过快照不会带来"悄悄丢数据"的风险。
     */
    internal const val MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024

    /**
     * SQLite 文件头里 user_version 的字节偏移，见 https://sqlite.org/fileformat.html#user_version_number
     * 4 字节大端。
     */
    private const val USER_VERSION_OFFSET = 60
    private const val HEADER_SIZE = 100

    /**
     * 若磁盘上的库比代码旧，就在 Room 打开它之前留一份快照。
     *
     * 必须在 `Room.databaseBuilder(...).build()` **之前**调用：build() 本身不打开库，
     * 但第一次查询就会触发 onUpgrade，那时再复制已经晚了。
     *
     * 任何失败都只记日志、不抛异常 —— 保险丝烧断了也不能反过来把应用点着。
     */
    fun snapshotIfUpgrading(context: Context, dbName: String, codeVersion: Int) {
        runCatching {
            val dbFile = context.getDatabasePath(dbName)
            val onDisk = readUserVersion(dbFile) ?: return
            // onDisk == 0 表示空库/新建库；>= codeVersion 表示不升级（含降级，降级另有风险但不在此处理）
            if (onDisk <= 0 || onDisk >= codeVersion) return

            val dbBytes = dbFile.length()
            if (dbBytes > MAX_SNAPSHOT_BYTES) {
                AppLog.w(
                    TAG,
                    "迁移前快照跳过：库 " + (dbBytes / (1024 * 1024)) + " MB 超过上限 " +
                        (MAX_SNAPSHOT_BYTES / (1024 * 1024)) + " MB（v" + onDisk + " → v" + codeVersion + "）",
                )
                return
            }

            val dir = File(context.noBackupFilesDir, SNAPSHOT_DIR).apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            val prefix = "$dbName.v$onDisk.$stamp"

            // -wal 里可能压着尚未 checkpoint 的已提交事务，只复制主文件会丢掉它们。三个一起走。
            var copied = 0
            for (suffix in FILE_SUFFIXES) {
                val src = File(dbFile.parentFile, dbFile.name + suffix)
                if (!src.exists()) continue
                src.copyTo(File(dir, prefix + suffix), overwrite = true)
                copied++
            }

            AppLog.i(TAG, "迁移前快照已保存：v$onDisk → v$codeVersion，$copied 个文件，位于 $prefix")
            prune(dir, dbName, KEEP_SNAPSHOTS)
        }.onFailure {
            // 复制失败不该拦住应用启动。宁可没有保险，也不能开不了机。
            Log.w(TAG, "迁移前快照失败，继续启动", it)
        }
    }

    /**
     * R8 书架"一直显示刷新"修复（P2）：Android SQLite 检测到库文件损坏时，
     * 默认行为是**删库重建**（SQLiteDatabaseCorruptException → 空库），用户上百小时的
     * 数据会静默消失。开库前用只读连接做一次 integrity_check；异常时先整份快照
     * 保住损坏现场（数据仍可从主文件/-wal 捞回），再让系统按原行为重建。
     *
     * 任何失败都只记日志、不抛异常、不阻塞启动。
     */
    fun snapshotIfCorrupt(context: Context, dbName: String) {
        runCatching {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.isFile || dbFile.length() <= 0L) return
            // integrity_check 是全库扫描，代价与库体积成正比。2026-11-17 Mate 70 实测：
            // 5.17 GB 的库上这一步动辄数十秒，主线程直接被卡到被系统判为无响应并杀掉，
            // 应用永远起不来。大库跳过预检：真损坏时 openDatabase 仍会抛
            // SQLiteDatabaseCorruptException，走下面的 onFailure 分支照样留现场快照。
            if (dbFile.length() > MAX_SNAPSHOT_BYTES) {
                AppLog.w(
                    TAG,
                    "开库前完整性预检跳过：库 " + (dbFile.length() / (1024 * 1024)) + " MB 超过上限 " +
                        (MAX_SNAPSHOT_BYTES / (1024 * 1024)) + " MB",
                )
                return
            }
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )
            val ok = db.use { d ->
                d.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                }
            }
            if (!ok) {
                val saved = snapshotNow(context, dbName)
                AppLog.e(TAG, "开库前完整性检查失败，损坏现场快照 saved=$saved")
                Log.e(TAG, "Database integrity_check failed; snapshot saved=$saved")
            }
        }.onFailure {
            // 损坏库 openDatabase 会抛 SQLiteDatabaseCorruptException —— 正是要拦截的情况。
            val saved = runCatching { snapshotNow(context, dbName) }.getOrDefault(false)
            AppLog.e(TAG, "开库前检测到损坏，损坏现场快照 saved=$saved: ${it.message}")
            Log.e(TAG, "Corrupt database detected before open; snapshot saved=$saved", it)
        }
    }

    /**
     * 恢复/导入数据前的即时快照：无论版本如何，都整份复制一份库文件。
     *
     * 供 WebDAV 下载恢复、本地 JSON 导入等「会覆写当前数据」的入口调用。
     * 与 [snapshotIfUpgrading] 同原则：任何失败只记日志、不抛异常 ——
     * 快照失败不能反过来拦住恢复本身。
     *
     * @return 快照是否成功写入（失败时调用方仅记日志，不阻塞恢复）
     */
    fun snapshotNow(context: Context, dbName: String): Boolean = runCatching {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.isFile || dbFile.length() <= 0L) return@runCatching false

        val dir = File(context.noBackupFilesDir, SNAPSHOT_DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val prefix = "$dbName.restore.$stamp"

        // -wal 里可能压着尚未 checkpoint 的已提交事务，只复制主文件会丢掉它们。三个一起走。
        var copied = 0
        for (suffix in FILE_SUFFIXES) {
            val src = File(dbFile.parentFile, dbFile.name + suffix)
            if (!src.exists()) continue
            src.copyTo(File(dir, prefix + suffix), overwrite = true)
            copied++
        }

        AppLog.i(TAG, "恢复前快照已保存：$copied 个文件，位于 $prefix")
        pruneByPrefix(dir, "$dbName.restore.", KEEP_RESTORE_SNAPSHOTS)
        true
    }.onFailure {
        // 快照失败只记日志，不阻塞恢复（同 snapshotIfUpgrading 原则）。
        Log.w(TAG, "恢复前快照失败，继续恢复", it)
    }.getOrDefault(false)

    /** 恢复前快照保留组数：比升级快照多一份，给「恢复坏了 → 再恢复一次」留余地。 */
    private const val KEEP_RESTORE_SNAPSHOTS = 3

    /** 主库 / WAL / 共享内存索引。缺哪个都正常，按存在与否复制。 */
    private val FILE_SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * 直接读 SQLite 文件头拿 user_version，不经过 SQLiteDatabase。
     *
     * 刻意不打开数据库：打开会触发 WAL 恢复、可能加锁、失败姿势也多，
     * 而这里只需要一个「要不要复制」的判断，读 100 字节最省事也最不容易出错。
     *
     * WAL 模式下主文件头可能落后于 WAL 里最新的 user_version，也就是**读到的版本只会偏旧**。
     * 偏旧的后果是多复制一次（无害）；不会出现「该复制却没复制」。方向是安全的。
     *
     * @return 文件不存在、太短、或不是 SQLite 文件时返回 null
     */
    internal fun readUserVersion(dbFile: File): Int? {
        if (!dbFile.isFile || dbFile.length() < HEADER_SIZE) return null
        return runCatching {
            val header = ByteArray(HEADER_SIZE)
            dbFile.inputStream().use { stream ->
                var read = 0
                while (read < HEADER_SIZE) {
                    val n = stream.read(header, read, HEADER_SIZE - read)
                    if (n < 0) return null
                    read += n
                }
            }
            if (!header.startsWith(SQLITE_MAGIC)) return null
            var v = 0
            for (i in 0 until 4) {
                v = (v shl 8) or (header[USER_VERSION_OFFSET + i].toInt() and 0xFF)
            }
            v
        }.getOrNull()
    }

    /** "SQLite format 3 " */
    private val SQLITE_MAGIC = byteArrayOf(
        0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
        0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00,
    )

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    /**
     * 只保留最近 [keep] 组快照，其余删掉。
     *
     * 按「组」而不是按文件删：一组是 `<prefix>` + `<prefix>-wal` + `<prefix>-shm`，
     * 少删一个 -wal 就意味着这组快照恢复出来的数据是残缺的。
     */
    internal fun prune(dir: File, dbName: String, keep: Int) = pruneByPrefix(dir, "$dbName.v", keep)

    /** 按文件名前缀清理快照组（升级快照用 `<dbName>.v`，恢复快照用 `<dbName>.restore.`）。 */
    internal fun pruneByPrefix(dir: File, prefix: String, keep: Int) {
        val files = dir.listFiles() ?: return
        // 组名 = 去掉 -wal / -shm 后缀
        val groups = files
            .filter { it.name.startsWith(prefix) }
            .groupBy { it.name.removeSuffix("-wal").removeSuffix("-shm") }
        if (groups.size <= keep) return

        groups.entries
            .sortedByDescending { (_, members) -> members.maxOf { it.lastModified() } }
            .drop(keep)
            .forEach { (_, members) -> members.forEach { it.delete() } }
    }

    /**
     * 列出现存快照，最新在前。供「我的 → 诊断」之类的入口展示与恢复用。
     * 返回的是每组的主库文件。
     */
    fun listSnapshots(context: Context, dbName: String): List<File> {
        val dir = File(context.noBackupFilesDir, SNAPSHOT_DIR)
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.name.startsWith("$dbName.v") && !it.name.endsWith("-wal") && !it.name.endsWith("-shm") }
            .sortedByDescending { it.lastModified() }
    }
}
