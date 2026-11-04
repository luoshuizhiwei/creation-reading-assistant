package com.creationreadingassistant.acceptance

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.sqlite.db.SupportSQLiteDatabase
import com.creationreadingassistant.data.local.AppDatabase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest

/**
 * CRA-D2-DEVICE-ARTIFACTS-REPAIR-v4
 *
 * D2 测试专属隔离上下文与生命周期/存储管理：
 *
 * 核心增强（余项 1）：
 * 1. 消除二选一误判：针对 ImportHistoryStore (import_history.preferences_pb) 和
 *    LibraryRootStore (library_root.preferences_pb) 两个具体 Store，同时监控
 *    测试隔离目录与宿主默认目录的前后指纹（exists, length, lastModified, sha256）。
 * 2. 完整记录所有可能触碰路径 (candidateDataStorePaths) 与实际存在文件 (actualDataStoreFiles)。
 * 3. 无法完全排他确认时严谨标为 MAY_TOUCH_BOTH，并输出具体的保护与恢复要求。
 * 4. 严守边界：不反射改单例，不删除宿主既有文件。
 */
class D2AcceptTestContext(
    base: Context,
    val root: File,
    provider: D2AcceptDocProvider,
) : ContextWrapper(base) {

    private val resolver: ContentResolver = ContentResolver.wrap(provider)

    // 初始 Store 指纹缓存（由测试前抓取）
    private var initialFingerprints: Map<String, StoreFileFingerprint> = emptyMap()

    override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").also { it.mkdirs() }

    /**
     * 重写 [getDatabasePath]，确保 Room 建立的 SQLite 数据库文件严格位于测试 root 的 databases 目录下。
     */
    override fun getDatabasePath(name: String): File {
        val dbDir = File(root, "databases").also { it.mkdirs() }
        return File(dbDir, name)
    }

    override fun getApplicationContext(): Context = this

    override fun getContentResolver(): ContentResolver = resolver

    /**
     * 断言当前打开的数据库实例底层物理文件严格位于测试 root 内。
     */
    fun assertDatabasePathIsolated(db: AppDatabase) {
        val writableDb: SupportSQLiteDatabase = db.openHelper.writableDatabase
        val actualPath = writableDb.path
        assertNotNull("底层数据库路径不可为空", actualPath)
        assertTrue(
            "实际 SQLite 路径必须位于本测试 root 内：actualPath=$actualPath, root=${root.absolutePath}",
            actualPath!!.startsWith(root.absolutePath)
        )
    }

    /**
     * 记录当前时刻两个具体 Store 在隔离目录与宿主目录下的快照指纹。
     */
    fun snapshotInitialStoreFingerprints() {
        initialFingerprints = probeTargetStoreFingerprints()
    }

    /**
     * 探测两个具体 Store（import_history 和 library_root）在隔离目录与宿主目录下的完整指纹。
     */
    fun probeTargetStoreFingerprints(): Map<String, StoreFileFingerprint> {
        val map = mutableMapOf<String, StoreFileFingerprint>()
        val isolatedDir = File(getFilesDir(), "datastore")
        val hostDir = File(baseContext.filesDir, "datastore")

        for (target in TARGET_STORES) {
            val isoFile = File(isolatedDir, target.fileName)
            val hostFile = File(hostDir, target.fileName)

            map["isolated_${target.storeName}"] = captureFileFingerprint(isoFile)
            map["host_${target.storeName}"] = captureFileFingerprint(hostFile)
        }
        return map
    }

    private fun captureFileFingerprint(file: File): StoreFileFingerprint {
        val exists = file.exists() && file.isFile
        val length = if (exists) file.length() else -1L
        val lastModified = if (exists) file.lastModified() else -1L
        val sha256 = if (exists) computeFileSha256(file) else null
        return StoreFileFingerprint(
            path = file.absolutePath,
            exists = exists,
            length = length,
            lastModified = lastModified,
            sha256 = sha256,
        )
    }

    /**
     * 真实扫描并登记实际触碰的物理路径集合与前后指纹对账。
     */
    fun collectTouchedPaths(dbName: String): D2StorageTouchRecord {
        val dbFile = getDatabasePath(dbName)
        val filesDir = getFilesDir()
        val cacheDir = getCacheDir()
        val dataStoreDir = File(filesDir, "datastore")
        val hostDsDir = File(baseContext.filesDir, "datastore")

        // 1. 获取当前时刻指纹
        val currentFingerprints = probeTargetStoreFingerprints()
        val beforeFp = if (initialFingerprints.isNotEmpty()) initialFingerprints else currentFingerprints

        // 2. 收集所有可能触碰的具体路径（4 个具体文件全路径）
        val candidatePaths = mutableListOf<String>()
        for (target in TARGET_STORES) {
            candidatePaths.add(File(dataStoreDir, target.fileName).absolutePath)
            candidatePaths.add(File(hostDsDir, target.fileName).absolutePath)
        }

        // 3. 收集实际存在的物理文件（双方均扫描，杜绝二选一遗漏）
        val actualFiles = mutableListOf<String>()
        for ((_, fp) in currentFingerprints) {
            if (fp.exists && !actualFiles.contains(fp.path)) {
                actualFiles.add(fp.path)
            }
        }
        // 亦包含目录下任意残留的其他 .preferences_pb
        dataStoreDir.listFiles()?.filter { it.extension == "preferences_pb" }?.forEach {
            if (!actualFiles.contains(it.absolutePath)) actualFiles.add(it.absolutePath)
        }
        hostDsDir.listFiles()?.filter { it.extension == "preferences_pb" }?.forEach {
            if (!actualFiles.contains(it.absolutePath)) actualFiles.add(it.absolutePath)
        }

        // 4. 分析具体变更归属
        var isolatedChanged = false
        var hostChanged = false

        for (target in TARGET_STORES) {
            val isoBefore = beforeFp["isolated_${target.storeName}"]
            val isoAfter = currentFingerprints["isolated_${target.storeName}"]
            if (isoBefore != null && isoAfter != null) {
                if (isoBefore.exists != isoAfter.exists || isoBefore.sha256 != isoAfter.sha256 || isoBefore.length != isoAfter.length) {
                    isolatedChanged = true
                }
            }

            val hostBefore = beforeFp["host_${target.storeName}"]
            val hostAfter = currentFingerprints["host_${target.storeName}"]
            if (hostBefore != null && hostAfter != null) {
                if (hostBefore.exists != hostAfter.exists || hostBefore.sha256 != hostAfter.sha256 || hostBefore.length != hostAfter.length) {
                    hostChanged = true
                }
            }
        }

        // 5. 严格判定实际归属
        val actualLocation: String = when {
            isolatedChanged && !hostChanged -> "TEST_ISOLATED"
            !isolatedChanged && hostChanged -> "HOST_DEFAULT"
            isolatedChanged && hostChanged -> "MAY_TOUCH_BOTH"
            else -> {
                // 若测试中未触发新写入，但双方或任一方存在文件，则安全判定为 MAY_TOUCH_BOTH
                if (actualFiles.isNotEmpty()) "MAY_TOUCH_BOTH" else "NOT_MODIFIED"
            }
        }

        val protectionRequirement = "保护与恢复合同：测试严禁使用反射重置 preferencesDataStore 静态单例，亦不得主动删除宿主默认目录文件。宿主默认 DataStore 路径已纳入 candidateDataStorePaths 与 actualDataStoreFiles。若下游执行设备运行，工件 C 必须在每次方法前后保存并恢复宿主 DataStore 文件基线；若无法提供宿主 DataStore 恢复保障，设备验收前必须拒绝执行。"

        return D2StorageTouchRecord(
            rootDir = root.absolutePath,
            databaseFile = dbFile.absolutePath,
            databaseWalFile = File("${dbFile.absolutePath}-wal").absolutePath,
            databaseShmFile = File("${dbFile.absolutePath}-shm").absolutePath,
            filesDir = filesDir.absolutePath,
            cacheDir = cacheDir.absolutePath,
            dataStoreDir = dataStoreDir.absolutePath,
            actualDataStoreLocation = actualLocation,
            actualDataStoreFiles = actualFiles,
            candidateDataStorePaths = candidatePaths,
            storeFingerprintsBefore = beforeFp,
            storeFingerprintsAfter = currentFingerprints,
            protectionAndRecoveryRequirement = protectionRequirement,
        )
    }

    /**
     * 精确清理当前方法创建的数据库句柄与临时测试文件。
     * 规则：
     * 1. 优雅关闭 Room 连接句柄；
     * 2. 精确删除 d2-accept.db, -wal, -shm, -journal 4 类数据库文件；
     * 3. 精确清理临时 source 目录与 seed 目录；
     * 4. 绝对不删除 DataStore 文件所在根目录，以保护全局单例生命周期；
     * 5. 绝对不删除宿主既有文件。
     */
    fun cleanup(db: AppDatabase?, dbName: String): List<String> {
        val deletedFiles = mutableListOf<String>()
        runCatching {
            if (db?.isOpen == true) {
                db.close()
            }
        }
        val dbFile = getDatabasePath(dbName)
        val auxiliaryFiles = listOf(
            dbFile,
            File("${dbFile.absolutePath}-wal"),
            File("${dbFile.absolutePath}-shm"),
            File("${dbFile.absolutePath}-journal"),
        )
        for (file in auxiliaryFiles) {
            if (file.exists()) {
                val path = file.absolutePath
                if (file.delete()) {
                    deletedFiles.add(path)
                }
            }
        }

        // 精确清理临时 source 目录
        val sourceDir = File(root, "source")
        if (sourceDir.exists()) {
            sourceDir.deleteRecursively()
            deletedFiles.add(sourceDir.absolutePath)
        }

        // 清理临时 seed 目录
        val seedDir = File(root, "seed")
        if (seedDir.exists()) {
            seedDir.deleteRecursively()
            deletedFiles.add(seedDir.absolutePath)
        }

        return deletedFiles
    }

    private fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        data class StoreProbeTarget(val storeName: String, val fileName: String)

        val TARGET_STORES = listOf(
            StoreProbeTarget("import_history", "import_history.preferences_pb"),
            StoreProbeTarget("library_root", "library_root.preferences_pb"),
        )
    }
}

/**
 * 单个 DataStore 文件的物理指纹快照。
 */
data class StoreFileFingerprint(
    val path: String,
    val exists: Boolean,
    val length: Long,
    val lastModified: Long,
    val sha256: String?,
)

/**
 * 触碰路径清单（机器可读）。
 */
data class D2StorageTouchRecord(
    val rootDir: String,
    val databaseFile: String,
    val databaseWalFile: String,
    val databaseShmFile: String,
    val filesDir: String,
    val cacheDir: String,
    val dataStoreDir: String,
    val actualDataStoreLocation: String,
    val actualDataStoreFiles: List<String>,
    val candidateDataStorePaths: List<String>,
    val storeFingerprintsBefore: Map<String, StoreFileFingerprint>,
    val storeFingerprintsAfter: Map<String, StoreFileFingerprint>,
    val protectionAndRecoveryRequirement: String,
)

/**
 * 进程内真实 ContentProvider，按 documentId 提供真实文件字节。
 * 代替 SAF 传输层的测试接缝，不声称系统 SAF picker 通过。
 */
class D2AcceptDocProvider : ContentProvider() {
    private val files = HashMap<String, File>()

    fun serve(documentId: String, file: File) {
        files[documentId] = file
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val docId = uri.lastPathSegment ?: throw java.io.FileNotFoundException("Uri 缺少 lastPathSegment: $uri")
        val file = files[docId] ?: throw java.io.FileNotFoundException("未找到托管文件: $docId (uri=$uri)")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val docId = uri.lastPathSegment ?: return null
        val file = files[docId] ?: return null
        return MatrixCursor(
            arrayOf(
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
        ).apply {
            addRow(arrayOf<Any>(file.length(), file.lastModified()))
        }
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
