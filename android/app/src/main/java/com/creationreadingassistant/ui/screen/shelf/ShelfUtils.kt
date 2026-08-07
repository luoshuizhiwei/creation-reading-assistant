package com.creationreadingassistant.ui.screen.shelf

/**
 * 将字节数格式化为 `KB` / `MB` 字符串（书架专用，size 为 Int）。
 *
 * 注意：与 `ui/screen/profile/ProfileUtils.kt` 中的 `formatBytes(size: Long)` 签名不同
 *（此处为 Int 且 size<=0 时返回「未知」），故保留独立版本，不做去重。
 */
internal fun formatBytes(size: Int): String {
    if (size <= 0) return "未知"
    val kb = size / 1024.0
    return if (kb < 1024) "%.1f KB".format(kb) else "%.2f MB".format(kb / 1024)
}

/** 将毫秒时长格式化为「X 天 Y 小时」/「X 小时 Y 分钟」/「Y 分钟」（委托 ui/util 公共实现）。 */
internal fun formatDuration(ms: Long): String =
    com.creationreadingassistant.ui.util.formatDuration(ms)

/**
 * 生成文字封面 DataURL（SVG），对齐网页版 BookDetailSheet.generateTextCoverDataUrl。
 * 取书名前 4 个字符，使用与 BookCover 文字回退一致的稳定色相，避免独立实现算法不一致。
 */
internal fun generateTextCoverDataUrl(title: String): String {
    val safeTitle = (title.ifBlank { "未命名" }).trim()
    val display = safeTitle.take(4)
    val hue = ((safeTitle.hashCode()).rem(360) + 360).rem(360)
    val bg = "hsl($hue, 42%, 45%)"
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" width="240" height="336" viewBox="0 0 240 336">
          <rect width="240" height="336" fill="$bg"/>
          <text x="20" y="120" font-family="sans-serif" font-size="48" font-weight="700" fill="rgba(255,255,255,0.96)">${esc(display)}</text>
          <text x="20" y="312" font-family="sans-serif" font-size="16" fill="rgba(255,255,255,0.78)">${esc(safeTitle.take(12))}</text>
        </svg>
    """.trimIndent()
    return "data:image/svg+xml;utf8," + java.net.URLEncoder.encode(svg, "UTF-8").replace("+", "%20")
}
