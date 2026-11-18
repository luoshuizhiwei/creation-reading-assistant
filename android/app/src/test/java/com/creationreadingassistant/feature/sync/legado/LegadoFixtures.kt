package com.creationreadingassistant.feature.sync.legado

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 合成 Legado 备份夹具（契约 §8 JVM 门禁）：按 MD3 固定提交 schema 生成
 * 四文件 ZIP，虚构书名与时间线。合成数据非隐私，可作为普通测试资源。
 */
object LegadoFixtures {

    fun bookshelfEntry(
        name: String = "测试书甲",
        author: String = "测试作者",
        bookUrl: String = "",
        durChapterIndex: Int = 2,
        durChapterPos: Int = 150,
        durChapterTime: Long = 1_700_000_000_000L,
        durChapterTitle: String? = "第二章 边界",
        totalChapterNum: Int? = 3,
        origin: String? = "测试书源",
        extraJson: String = "",
    ): String = buildString {
        append('{')
        append("\"name\":\"").append(name).append("\",")
        append("\"author\":\"").append(author).append("\",")
        append("\"bookUrl\":\"").append(bookUrl).append("\",")
        if (origin != null) append("\"origin\":\"").append(origin).append("\",")
        append("\"durChapterIndex\":").append(durChapterIndex).append(',')
        append("\"durChapterPos\":").append(durChapterPos).append(',')
        append("\"durChapterTime\":").append(durChapterTime).append(',')
        if (durChapterTitle != null) append("\"durChapterTitle\":\"").append(durChapterTitle).append("\",")
        if (totalChapterNum != null) append("\"totalChapterNum\":").append(totalChapterNum).append(',')
        append(extraJson)
        append("\"showName\":\"").append(name).append("\"}") // 未知额外字段（必须被忽略）
    }

    fun session(
        deviceId: String = "dev-a",
        bookName: String = "测试书甲",
        bookAuthor: String = "测试作者",
        bookUrl: String = "legado://book/1",
        start: Long = 1_700_000_000_000L,
        end: Long = 1_700_000_600_000L,
        words: Long? = 2,
    ): String = buildString {
        append('{')
        append("\"deviceId\":\"").append(deviceId).append("\",")
        append("\"bookName\":\"").append(bookName).append("\",")
        append("\"bookAuthor\":\"").append(bookAuthor).append("\",")
        append("\"bookUrl\":\"").append(bookUrl).append("\",")
        append("\"startTime\":").append(start).append(',')
        append("\"endTime\":").append(end)
        if (words != null) append(",\"words\":").append(words)
        append('}')
    }

    fun day(
        deviceId: String = "dev-a",
        bookName: String = "测试书甲",
        bookAuthor: String = "测试作者",
        date: String = "2026-11-01",
        readTime: Long = 600_000L,
        readWords: Long = 2,
        firstRead: Long = 1_700_000_000_000L,
        lastRead: Long = 1_700_000_600_000L,
    ): String = buildString {
        append('{')
        append("\"deviceId\":\"").append(deviceId).append("\",")
        append("\"bookName\":\"").append(bookName).append("\",")
        append("\"bookAuthor\":\"").append(bookAuthor).append("\",")
        append("\"date\":\"").append(date).append("\",")
        append("\"readTime\":").append(readTime).append(',')
        append("\"readWords\":").append(readWords).append(',')
        append("\"firstReadTime\":").append(firstRead).append(',')
        append("\"lastReadTime\":").append(lastRead)
        append('}')
    }

    fun book(
        deviceId: String = "dev-a",
        bookName: String = "测试书甲",
        bookAuthor: String = "测试作者",
        readTime: Long = 3_600_000L,
        lastRead: Long = 1_700_000_600_000L,
    ): String = buildString {
        append('{')
        append("\"deviceId\":\"").append(deviceId).append("\",")
        append("\"bookName\":\"").append(bookName).append("\",")
        append("\"bookAuthor\":\"").append(bookAuthor).append("\",")
        append("\"readTime\":").append(readTime).append(',')
        append("\"lastRead\":").append(lastRead)
        append('}')
    }

    private fun array(elements: List<String>): String =
        "[" + elements.joinToString(",") + "]"

    /**
     * 生成 ZIP。`nameRewrite` 可把白名单文件名改成大小写变体/子目录路径
     * （供「冒充」拒绝用例），`entries` 直接给出额外条目。
     */
    fun zip(
        bookshelf: List<String>? = listOf(bookshelfEntry()),
        sessions: List<String>? = listOf(session()),
        days: List<String>? = listOf(day()),
        books: List<String>? = listOf(book()),
        extraEntries: Map<String, ByteArray> = emptyMap(),
        nameRewrite: (String) -> String = { it },
        entries: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val files = LinkedHashMap<String, ByteArray>()
            if (bookshelf != null) files[LegadoBackupParser.FILE_BOOKSHELF] = array(bookshelf).toByteArray()
            if (sessions != null) files[LegadoBackupParser.FILE_SESSION] = array(sessions).toByteArray()
            if (days != null) files[LegadoBackupParser.FILE_DAY] = array(days).toByteArray()
            if (books != null) files[LegadoBackupParser.FILE_BOOK] = array(books).toByteArray()
            files.putAll(extraEntries)
            files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(nameRewrite(name))).also { zip.write(bytes) ; zip.closeEntry() } }
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)).also { zip.write(bytes); zip.closeEntry() } }
        }
        return out.toByteArray()
    }

    fun unzipEntries(zip: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) {
                    val out = ByteArrayOutputStream()
                    z.copyTo(out)
                    result[e.name] = out.toByteArray()
                }
                z.closeEntry()
            }
        }
        return result
    }
}
