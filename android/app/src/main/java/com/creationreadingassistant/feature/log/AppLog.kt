package com.creationreadingassistant.feature.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 应用内诊断日志（对照 web 诊断页「运行中的导入、同步、阅读问题会自动记录」）。
 *
 * 轻量环形缓冲（最多 500 条），记录 INFO/WARN/ERROR/EVENT 级事件。
 * App.kt 会在启动时接管未捕获异常写入 ERROR，ProfileViewModel 在
 * 导入/导出/同步/备份等关键动作后写入 EVENT，供「日志与诊断」页查看、导出、清空。
 */
object AppLog {
    enum class Level { INFO, WARN, ERROR, EVENT }

    data class Entry(
        val id: String,
        val timestamp: String,
        val level: Level,
        val module: String,
        val message: String,
        val code: String? = null,
    )

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private const val MAX = 500

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun i(tag: String, msg: String) = push(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = push(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = push(Level.ERROR, tag, msg)
    fun event(tag: String, msg: String) = push(Level.EVENT, tag, msg)

    fun error(tag: String, msg: String, code: String) = push(Level.ERROR, tag, msg, code)
    fun warn(tag: String, msg: String, code: String) = push(Level.WARN, tag, msg, code)

    private fun push(level: Level, tag: String, msg: String, code: String? = null) {
        val ts = fmt.format(Date())
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            timestamp = ts,
            level = level,
            module = tag,
            message = msg,
            code = code,
        )
        _entries.update { (it + entry).takeLast(MAX) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun snapshot(): String = _entries.value.joinToString("\n") { format(it) }

    private fun format(entry: Entry): String {
        val codePart = entry.code?.let { " [$it]" } ?: ""
        return "${entry.timestamp} [${entry.level.name}] ${entry.module}: ${entry.message}$codePart"
    }
}
