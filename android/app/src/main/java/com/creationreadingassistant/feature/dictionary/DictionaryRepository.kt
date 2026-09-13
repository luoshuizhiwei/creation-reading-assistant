package com.creationreadingassistant.feature.dictionary

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一次离线查询的命中（带来源词典名，便于 UI 区分是哪个词库给的释义）。 */
internal data class DictionaryLookupEntry(
    val sourceName: String,
    val word: String,
    val content: String,
    val typeCode: Char,
)

/**
 * 一次查词的完整结果（R3-X1 缺陷收口）。
 *
 * 为什么要显式带出 [unreadableDictionaries]：词库文件损坏、`.idx` 被截断或目录权限异常时，
 * 该词库会被跳过。如果只返回「命中列表为空」，UI 只能得出「这个词不在库里」的结论 ——
 * 而这可能完全是错的（词在库里，只是库没读上）。把跳过的词库名带出去，
 * UI 才有依据区分「查不到」与「打不开」，不静默伪造一个错误结论。
 */
internal data class DictionaryLookupOutcome(
    val entries: List<DictionaryLookupEntry> = emptyList(),
    /** 本次参与查询的已安装词库数（含打不开的那些）。 */
    val scannedDictionaries: Int = 0,
    /** 打开或查询过程中抛异常、被跳过的词库名。 */
    val unreadableDictionaries: List<String> = emptyList(),
)

/**
 * 离线词典仓储（R3-X1）。
 *
 * - 词库落在应用私有目录 `filesDir/dictionaries/<base>`，卸载/导入都在应用内完成，不需要存储权限；
 * - 已打开的词典用 **LRU 上限 2** 常驻（[StarDictDictionary] 内部只缓存索引字节 + 一个文件句柄，
 *   词条正文按需随机读），避免每次查词都重新解析几 MB 的 `.idx`；
 * - 所有磁盘动作走 [Dispatchers.IO]，调用方从 UI 直接 await 即可。
 */
@Singleton
internal class DictionaryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "dictionaries")

    /** `.dict.dz` 原地解压失败时的回退目录（例如词库目录只读）。 */
    private val cacheDir = File(context.cacheDir, "dictionary-cache")

    private val contentResolver = context.contentResolver

    /** 已打开句柄，最近使用的在末尾；超过 [MAX_OPEN] 时关闭最久未用的。 */
    private val opened = LinkedHashMap<String, StarDictDictionary>()

    suspend fun installed(): List<InstalledDictionary> = withContext(Dispatchers.IO) {
        StarDictImporter.listInstalled(root, cacheDir)
    }

    /** 从用户选中的 zip 导入。返回导入结果（含失败原因，供 UI 提示）。 */
    suspend fun install(uri: Uri): StarDictImporter.Result = withContext(Dispatchers.IO) {
        val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
            ?: return@withContext StarDictImporter.Result.Failed("无法读取所选文件")
        stream.use { StarDictImporter.installFromZip(it, root, cacheDir) }
    }

    suspend fun uninstall(baseName: String): Boolean = withContext(Dispatchers.IO) {
        opened.remove(baseName)?.close()
        StarDictImporter.uninstall(File(root, baseName))
    }

    /**
     * 在全部已安装词库里查词。
     *
     * 单个词库打不开或查询抛异常只记入 [DictionaryLookupOutcome.unreadableDictionaries]，
     * **不让整次查询失败** —— 一个坏词库不该让用户失去其它词库的释义。
     * 逐词库 `runCatching` 也保证损坏文件不会以未捕获异常打穿到 ViewModel 协程里。
     */
    suspend fun lookup(word: String, perDictionaryLimit: Int = 4): DictionaryLookupOutcome =
        withContext(Dispatchers.IO) {
            val key = word.trim()
            if (key.isEmpty()) return@withContext DictionaryLookupOutcome()

            val dictionaries = installed()
            val hits = ArrayList<DictionaryLookupEntry>()
            val unreadable = ArrayList<String>()
            dictionaries.forEach { dict ->
                val entries = runCatching {
                    val handle = openHandle(dict) ?: error("词库无法打开：${dict.baseName}")
                    handle.lookup(key, perDictionaryLimit)
                }.getOrElse {
                    unreadable += dict.bookName
                    emptyList()
                }
                entries.forEach { hit ->
                    hits += DictionaryLookupEntry(
                        sourceName = dict.bookName,
                        word = hit.word,
                        content = hit.content,
                        typeCode = hit.typeCode,
                    )
                }
            }
            DictionaryLookupOutcome(
                entries = hits,
                scannedDictionaries = dictionaries.size,
                unreadableDictionaries = unreadable,
            )
        }

    private fun openHandle(dict: InstalledDictionary): StarDictDictionary? {
        opened.remove(dict.baseName)?.let { cached ->
            // 命中缓存：重新插回末尾即「最近使用」，不重新解析 .idx
            opened[dict.baseName] = cached
            return cached
        }
        val handle = StarDictDictionary.open(dict.dir, dict.baseName, cacheDir) ?: return null
        opened[dict.baseName] = handle
        while (opened.size > MAX_OPEN) {
            val oldest = opened.keys.firstOrNull() ?: break
            opened.remove(oldest)?.close()
        }
        return handle
    }

    private companion object {
        /** 同时最多常驻 2 个词库索引；查词只命中少数词库，没必要全开。 */
        const val MAX_OPEN = 2
    }
}
