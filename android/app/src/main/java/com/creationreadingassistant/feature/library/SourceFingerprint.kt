package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.net.Uri
import java.security.MessageDigest

/**
 * 超大文件的「大小 + 首尾分块哈希」候选指纹（方案 §5.6）。
 *
 * 适用边界（不得越界使用）：
 *  - 只用于去重候选提示与来源索引比对，**不是安全签名**；把指纹当成最终事实去
 *    覆盖原书内容之前，仍必须经过完整导入校验；
 *  - 只对超过 [FULL_HASH_LIMIT_BYTES] 的文件启用 —— 更小的文件在导入时算得起
 *    全量 MD5，指纹反而会引入碰撞面；
 *  - 指纹格式带版本号 `fp1`，未来换参数（块大小/摘要算法）时旧值自然失配，
 *    不会出现跨版本误判「同内容」。
 */
object SourceFingerprint {

    /** 超过该大小（32 MiB）不再计算全量哈希，改用首尾分块候选指纹。 */
    const val FULL_HASH_LIMIT_BYTES: Long = 32L * 1024 * 1024

    /** 首尾各读取的分块大小。两个分块在 >32 MiB 的文件上永不重叠。 */
    const val CHUNK_BYTES: Int = 256 * 1024

    fun isLargeFile(sizeBytes: Long?): Boolean = sizeBytes != null && sizeBytes > FULL_HASH_LIMIT_BYTES

    /**
     * 组装指纹字符串。`sizeBytes` 参与指纹：同内容不同截断大小的文件不会互判同源。
     */
    fun fromChunks(sizeBytes: Long, headMd5Hex: String, tailMd5Hex: String): String =
        "fp1|$sizeBytes|$headMd5Hex|$tailMd5Hex"

    fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * 从 SAF 流计算候选指纹。头部不足一个完整分块（provider 上报的大小不可信或
     * 读取被截断）时返回 null —— 宁可放弃指纹，也不生成一个语义不明的值。
     * 只在 IO 线程调用。
     */
    fun compute(contentResolver: ContentResolver, uri: Uri, sizeBytes: Long): String? {
        if (!isLargeFile(sizeBytes)) return null
        return runCatching {
            val headMd5 = contentResolver.openInputStream(uri)?.use { input ->
                md5Hex(input.readExactly(CHUNK_BYTES) ?: return@use null)
            } ?: return null
            val tailMd5 = contentResolver.openInputStream(uri)?.use { input ->
                skipFully(input, sizeBytes - CHUNK_BYTES)
                md5Hex(input.readExactly(CHUNK_BYTES) ?: return@use null)
            } ?: return null
            fromChunks(sizeBytes, headMd5, tailMd5)
        }.getOrNull()
    }

    /** 恰好读取 [count] 字节；流提前结束返回 null。 */
    private fun java.io.InputStream.readExactly(count: Int): ByteArray? {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = read(result, offset, count - offset)
            if (n < 0) return null
            offset += n
        }
        return result
    }

    /** 顺序跳过 [total] 字节；底层 skip 不保证一次到位，循环补齐。 */
    private fun skipFully(input: java.io.InputStream, total: Long) {
        var remaining = total
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                // 某些流 skip 返回 0，退化为丢弃式读取推进。
                if (input.read() < 0) return
                remaining -= 1
            }
        }
    }
}
