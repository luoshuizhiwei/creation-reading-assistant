package com.creationreadingassistant.feature.dictionary

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 一个已安装的离线词典（用于设置页列表展示）。
 */
internal data class InstalledDictionary(
    /** 词库文件基名（同名 .ifo/.idx/.dict 三件套），也是目录名。 */
    val baseName: String,
    val bookName: String,
    val wordCount: Int,
    val description: String,
    val dir: File,
)

/**
 * StarDict 词典导入（R3-X1）。
 *
 * 为什么走 `.zip`：Android 的 SAF 只能授权**单个文档**或整棵目录树；一个 StarDict 词典是
 * `.ifo/.idx/.dict(.dz)` 多文件，选单个文件拿不到兄弟文件，选目录又要处理 DocumentFile 递归。
 * 让用户把词库打成一个 zip 再导入，是「一次授权、零权限、可校验、可回滚」的折中：
 * 导入过程全部在应用私有目录完成，失败即整目录删除，不留半截词库。
 *
 * 安全约束：
 * - **只取 entry 的文件名**（`File(name).name`），忽略目录结构 → 天然免疫 zip-slip；
 * - 限制 entry 数量与解压总字节数 → 抵御 zip 炸弹；
 * - 只接受 ifo / idx / dict / dict.dz 四种扩展名。
 */
internal object StarDictImporter {

    private const val MAX_ENTRIES = 64
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private const val MAX_SINGLE_BYTES = 256L * 1024 * 1024

    /** 安装结果。 */
    sealed interface Result {
        data class Installed(val dictionaries: List<InstalledDictionary>) : Result

        /** 不是有效的 StarDict 词库（缺 ifo/idx/dict 三件套之一）。 */
        data object NotAStarDict : Result

        data class Failed(val reason: String) : Result
    }

    /**
     * 从 zip 流安装词库到 [targetDir]（通常在 `filesDir/dictionaries`）。
     *
     * @param targetDir 目标根目录，每个词库占一个以基名命名的子目录。
     * @param cacheDir `.dict.dz` 原地解压失败时的回退目录；null 表示不接受回退。
     */
    fun installFromZip(
        zip: InputStream,
        targetDir: File,
        cacheDir: File? = null,
    ): Result {
        val staging = File(targetDir, ".staging")
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs() && !staging.isDirectory) return Result.Failed("无法创建导入目录")

        var entries = 0
        var totalBytes = 0L

        try {
            ZipInputStream(zip.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val fileName = File(entry.name).name
                    if (fileName.startsWith(".") || fileName.contains("__MACOSX")) continue
                    val target = resolveTarget(staging, fileName) ?: continue
                    entries += 1
                    if (entries > MAX_ENTRIES) return Result.Failed("压缩包内文件过多")
                    var written = 0L
                    target.outputStream().buffered().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val read = zis.read(buf)
                            if (read <= 0) break
                            written += read
                            totalBytes += read
                            if (written > MAX_SINGLE_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                return Result.Failed("压缩包体积超出限制")
                            }
                            out.write(buf, 0, read)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            staging.deleteRecursively()
            return Result.Failed(t.message ?: "读取压缩包失败")
        }

        // .dict.dz → .dict，之后统一按三件套处理
        staging.listFiles().orEmpty()
            .filter { it.name.endsWith(".dict.dz") }
            .forEach { dz ->
                val base = dz.name.removeSuffix(".dict.dz")
                StarDictFormat.decompressDictDz(dz, File(staging, "$base.dict"))
                dz.delete()
            }

        val bases = staging.listFiles().orEmpty()
            .map { it.name.substringBeforeLast('.') }
            .filter { it.isNotBlank() }
            .distinct()

        val installed = ArrayList<InstalledDictionary>()
        bases.forEach { base ->
            val dir = File(targetDir, base)
            if (dir.exists()) dir.deleteRecursively()
            if (!dir.mkdirs()) return@forEach
            staging.listFiles().orEmpty().filter { it.name.startsWith("$base.") }.forEach { file ->
                if (file.isFile && file.length() > 0) file.renameTo(File(dir, file.name))
            }
            val opened = StarDictDictionary.open(dir, base, cacheDir)
            if (opened == null) {
                dir.deleteRecursively()
            } else {
                opened.use {
                    installed += InstalledDictionary(
                        baseName = base,
                        bookName = it.info.bookName,
                        wordCount = it.info.wordCount,
                        description = it.info.description,
                        dir = dir,
                    )
                }
            }
        }
        staging.deleteRecursively()

        return if (installed.isNotEmpty()) Result.Installed(installed) else Result.NotAStarDict
    }

    /** 列出已安装词典（按目录扫描，不依赖额外的清单文件）。 */
    fun listInstalled(targetDir: File, cacheDir: File? = null): List<InstalledDictionary> {
        if (!targetDir.isDirectory) return emptyList()
        return targetDir.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { dir ->
                val base = dir.name
                StarDictDictionary.open(dir, base, cacheDir)?.use { dict ->
                    InstalledDictionary(
                        baseName = base,
                        bookName = dict.info.bookName,
                        wordCount = dict.info.wordCount,
                        description = dict.info.description,
                        dir = dir,
                    )
                }
            }
            .sortedBy { it.bookName }
    }

    /** 卸载词典：整目录删除。 */
    fun uninstall(dir: File): Boolean = runCatching { dir.deleteRecursively() }.getOrDefault(false)

    /** 只保留 StarDict 词库文件；返回 null 表示该 entry 不需要落盘。 */
    private fun resolveTarget(dir: File, fileName: String): File? {
        val lower = fileName.lowercase()
        val accepted = lower.endsWith(".ifo") ||
            lower.endsWith(".idx") ||
            lower.endsWith(".dict") ||
            lower.endsWith(".dict.dz")
        if (!accepted) return null
        return File(dir, fileName)
    }
}
