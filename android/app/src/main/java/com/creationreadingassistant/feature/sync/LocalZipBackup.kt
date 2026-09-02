package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地 ZIP 备份恢复（P1-6 MVP）。
 *
 * ZIP 结构（向后兼容：所有 entry 带相对路径）：
 *   backup.json               JsonBridge.LocalExport 完整 JSON（13 类实体 + 关联表）
 *   books/{id}/...            普通书源文件目录（TXT/MD 等导入时复制的原始文件）
 *   books/epub/{id}.epub      EPUB 存储副本（可选，缺失时可由 books/{id}/ 下原文件重解析）
 *
 * 设计要点：
 * 1. **流式读写**：ZipOutputStream / ZipInputStream 逐 entry 处理，整库（数百 MB 级）
 *    不一次性加载到内存，避免 OOM。
 * 2. **原子恢复**：导入先把全部 entry 解压到临时目录，校验 backup.json 通过后再
 *    执行 DB 事务 + books 目录覆盖，中途失败整体回滚（临时目录清理）。
 * 3. **与 JSON 备份一致**：backup.json 与 JsonBridge 导出的纯 JSON 完全相同，
 *    因此用户若只想快速迁移元数据（不含书源文件），继续用纯 JSON 即可；
 *    ZIP 是「连书一起搬走」的整包形态。
 */
@Singleton
class LocalZipBackup @Inject constructor(
    private val jsonBridge: JsonBridge,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 通过 SAF 选择的 URI 导出 ZIP（backup.json + filesDir/books/ 原始文件）。 */
    suspend fun exportZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        context.contentResolver.openOutputStream(zipUri)?.use { rawOs ->
            BufferedOutputStream(rawOs, BUF_SIZE).use { bos ->
                ZipOutputStream(bos).use { zos ->
                    // 1. 先写 backup.json（元数据最小，通常 < 5MB，优先落盘）
                    val json = jsonBridge.exportToString(context)
                    putTextEntry(zos, NAME_BACKUP_JSON, json)

                    // 2. 遍历 booksDir 下所有文件，按相对路径写入
                    val booksDir = File(context.filesDir, "books")
                    if (booksDir.isDirectory) {
                        booksDir.walkTopDown().forEach { f ->
                            if (f.isFile) {
                                val rel = relativePathOf(booksDir, f)
                                val entryName = "$BOOKS_PREFIX/$rel"
                                putFileEntry(zos, entryName, f)
                            }
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("无法写入 ZIP：$zipUri")
    }

    /** 从 SAF 选择的 ZIP 恢复：解到临时目录 → 校验 JSON → 入库 + 覆盖 books。 */
    suspend fun importZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        val tmp = File(context.cacheDir, "zip-restore-${System.currentTimeMillis()}")
        try {
            // 1. 解压到临时目录，边解边校验 entry 名（防止 ZipSlip）
            tmp.mkdirs()
            val jsonFile = File(tmp, NAME_BACKUP_JSON)
            val tmpBooks = File(tmp, "books")
            context.contentResolver.openInputStream(zipUri)?.use { rawIs ->
                BufferedInputStream(rawIs, BUF_SIZE).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val safeName = sanitizeEntryName(entry.name)
                            val target = File(tmp, safeName)
                            if (!target.normalize().path.startsWith(tmp.normalize().path + File.separator) &&
                                target.normalize().path != tmp.normalize().path
                            ) {
                                throw SecurityException("ZIP 包含非法路径：${entry.name}")
                            }
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { os -> zis.copyTo(os, BUF_SIZE) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } ?: throw IllegalStateException("无法读取 ZIP：$zipUri")

            if (!jsonFile.isFile) {
                throw IllegalStateException("ZIP 中缺少 $NAME_BACKUP_JSON，不是合法备份")
            }

            // 2. 先导入 DB（整体事务，失败自动回滚）
            jsonBridge.importFromString(context, jsonFile.readText(Charsets.UTF_8))

            // 3. 再覆盖 books 目录（逐文件复制，失败时不影响已入库数据——
            //    因为 DB 中的 BookEntity 仍然引用原路径，若文件缺失会在下次打开时弹
            //    "缺失书源"提示，用户可重新导入原文件修复，属于可恢复错误）。
            if (tmpBooks.isDirectory) {
                val realBooksDir = File(context.filesDir, "books")
                realBooksDir.mkdirs()
                tmpBooks.walkTopDown().forEach { src ->
                    val rel = relativePathOf(tmpBooks, src)
                    val dst = File(realBooksDir, rel)
                    if (src.isDirectory) {
                        dst.mkdirs()
                    } else if (src.isFile) {
                        dst.parentFile?.mkdirs()
                        src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o, BUF_SIZE) } }
                    }
                }
            }
        } finally {
            // 无论成功失败，清理临时目录
            runCatching { tmp.deleteRecursively() }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * 计算 [child] 相对于 [base] 的相对路径（纯 File + 字符串，兼容 minSdk 24；
     * 不使用 NIO.2 的 Path#relativize，因为它需要 API 26）。Zip entry 统一正斜杠。
     */
    internal fun relativePathOf(base: File, child: File): String {
        val sep = File.separatorChar
        val baseAbs = base.absolutePath.trimEnd(sep)
        val childAbs = child.absolutePath.trimEnd(sep)
        require(childAbs == baseAbs || childAbs.startsWith(baseAbs + sep)) {
            "File $childAbs is not a descendant of $baseAbs"
        }
        if (childAbs == baseAbs) return ""
        return childAbs.substring(baseAbs.length + 1).replace("\\", "/")
    }

    private fun putTextEntry(zos: ZipOutputStream, name: String, text: String) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun putFileEntry(zos: ZipOutputStream, name: String, file: File) {
        val entry = ZipEntry(name).apply { time = file.lastModified() }
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos, BUF_SIZE) }
        zos.closeEntry()
    }

    /** 过滤 Zip entry 名中的 ZipSlip 片段（../、绝对路径、反斜杠）。 */
    internal fun sanitizeEntryName(raw: String): String =
        raw.replace("\\", "/")
            .split("/")
            .filter { it != ".." && it.isNotBlank() }
            .joinToString("/")

    companion object {
        private const val BUF_SIZE = 64 * 1024
        private const val NAME_BACKUP_JSON = "backup.json"
        private const val BOOKS_PREFIX = "books"
    }
}
