package com.creationreadingassistant.ui.screen.profile

import android.content.Context
import com.creationreadingassistant.data.local.entity.BookEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 将 ISO-8601 字符串格式化为 `yyyy/MM/dd HH:mm`，解析失败返回「时间未知」。 */
internal fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "时间未知"
    val instant = runCatching { Instant.parse(iso) }.getOrElse { return "时间未知" }
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}

/** 将 ISO-8601 字符串格式化为短日期 `MM/dd HH:mm`，解析失败返回「未知」。 */
internal fun formatDateTimeShort(iso: String?): String {
    if (iso.isNullOrBlank()) return "未知"
    val instant = runCatching { Instant.parse(iso) }.getOrElse { return "未知" }
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
}

/** 将毫秒时长格式化为「X 小时 Y 分钟」或「Y 分钟」。 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return if (hours > 0) "${hours} 小时 ${minutes} 分钟" else "${minutes} 分钟"
}

/** 将同步耗时毫秒格式化为可读字符串。 */
internal fun formatSyncDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    if (ms < 60_000) return "${"%.1f".format(ms / 1000.0)}秒"
    return "${ms / 60_000}分${(ms % 60_000) / 1000}秒"
}

/** 判断书籍正文是否已下载到本地。 */
internal fun isBookDownloaded(book: BookEntity): Boolean {
    if (book.content_status == "missing" || book.content_status == "failed" || book.content_status == "downloading") return false
    return !book.local_content_path.isNullOrBlank() || !book.local_uri.isNullOrBlank()
}

/** 将字节数格式化为 `KB` / `MB` 字符串。 */
internal fun formatBytes(size: Long): String {
    if (size <= 0) return "0 B"
    val kb = size / 1024.0
    return if (kb < 1024) "%.1f KB".format(kb) else "%.2f MB".format(kb / 1024)
}

/** 清理阅读器正文缓存（含 EPUB 解压缓存、图片缓存），返回释放字节数。 */
internal fun clearReaderCache(context: Context): Long {
    var freed = 0L
    fun clearDir(dir: File?): Long {
        var total = 0L
        dir?.listFiles()?.forEach {
            total += if (it.isDirectory) {
                val sub = clearDir(it)
                it.deleteRecursively()
                sub
            } else {
                val len = it.length()
                if (it.delete()) len else 0L
            }
        }
        return total
    }
    freed += clearDir(context.cacheDir)
    freed += clearDir(context.externalCacheDir)
    return freed
}
