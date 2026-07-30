package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.data.local.entity.BookEntity
import java.io.File

/**
 * 本地正文引用是否足以尝试打开。
 *
 * 文件大小为 0 可能只是旧版本没有写入元数据，不能据此否定一个仍存在的 SAF Uri
 * 或内部文件路径。真正的可读性由打开文件时的解析结果确认。
 */
internal fun BookEntity.hasLocalBookSource(): Boolean {
    if (content_status == "missing" || content_status == "failed" || content_status == "downloading") {
        return false
    }
    val localPathReadable = local_content_path
        ?.removePrefix("file://")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.let { it.isFile && it.length() > 0L }
        ?: false
    val uriReadableOrDelegated = local_uri
        ?.takeIf { it.isNotBlank() }
        ?.let { uri ->
            if (uri.startsWith("file://", ignoreCase = true)) {
                File(uri.removePrefix("file://")).let { it.isFile && it.length() > 0L }
            } else {
                // A content provider can only be authoritatively checked with ContentResolver.
                // Keep it actionable here and let the reader surface a recoverable open error.
                true
            }
        }
        ?: false
    return localPathReadable || uriReadableOrDelegated
}
