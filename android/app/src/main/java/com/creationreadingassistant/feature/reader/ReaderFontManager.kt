package com.creationreadingassistant.feature.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import java.io.File

/**
 * 正文字体管理：把 SAF 选中的 .ttf/.otf 复制进应用私有目录并缓存 Typeface。
 *
 * - 文件必须复制到 filesDir（content:// 授权重启后失效，直接引用会加载失败）。
 * - 单槽位设计：新导入覆盖旧文件，设置空路径即恢复系统字体。
 * - [loadTypeface] 失败返回 null，调用方回退系统字体，绝不崩溃。
 */
object ReaderFontManager {

    private const val FONT_DIR = "fonts"
    private const val MAX_CACHE = 4

    private val typefaceCache = object : LruCache<String, Typeface?>(MAX_CACHE) {}

    fun fontFileNameOf(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        val fallback = uri.lastPathSegment ?: "custom_font"
        val withExtension = (name ?: fallback)
            .takeIf { it.endsWith(".ttf", ignoreCase = true) || it.endsWith(".otf", ignoreCase = true) }
        return withExtension ?: "$fallback.ttf"
    }

    /**
     * 把 SAF 选中的字体复制进应用私有目录，返回目标绝对路径；失败返回 null。
     * 非 .ttf/.otf 扩展名的文件同样拒绝（避免把图片之类的文件存成字体）。
     */
    fun installFont(context: Context, uri: Uri): String? {
        val name = fontFileNameOf(context, uri)
        if (!name.endsWith(".ttf", ignoreCase = true) && !name.endsWith(".otf", ignoreCase = true)) {
            return null
        }
        return try {
            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            val target = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** 按路径加载 Typeface，LruCache 缓存；失败返回 null（调用方回退系统字体）。 */
    fun loadTypeface(path: String): Typeface? {
        if (path.isBlank()) return null
        typefaceCache.get(path)?.let { return it }
        return try {
            Typeface.createFromFile(path).also { typefaceCache.put(path, it) }
        } catch (_: Exception) {
            typefaceCache.put(path, null)
            null
        }
    }

    /** 清空缓存（换字体文件后防止旧对象残留）。 */
    fun clearCache() = typefaceCache.evictAll()
}
