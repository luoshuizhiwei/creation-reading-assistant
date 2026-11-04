package com.creationreadingassistant.acceptance

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * 真实 UiAutomation root-shell 通道（stdin 脚本，stdout 读取）。
 *
 * 官方 API 契约：`executeShellCommandRw(String)` 返回 **两个** ParcelFileDescriptor，
 * `fds[0]` 是 stdout 的**读取端**，`fds[1]` 是 stdin 的**写入端**
 * （https://developer.android.com/reference/android/app/UiAutomation#executeShellCommandRw(java.lang.String)）。
 *
 * 使用顺序：先向 fds[1] 写脚本并关闭（发送 EOF），再从 fds[0] 读到 EOF；
 * 两个 fd 都由 AutoClose 包装各自关闭，不重复关闭同一 fd。
 *
 * 注意：`executeShellCommand(String)` **不经过 shell**（按空白拆 argv），
 * 不能用于 `;`/`$?` 这类 shell 语义；多行脚本必须走本通道。
 */
internal object UiAutomationShellChannel {

    /** 默认执行方式与宿主已核的 `adb shell -T su 0 sh -s` 对齐。 */
    const val DEFAULT_COMMAND = "su 0 sh -s"

    fun run(script: String, command: String = DEFAULT_COMMAND): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val fds = uiAutomation.executeShellCommandRw(command)
        check(fds.size == 2) { "executeShellCommandRw must return exactly two descriptors" }
        // fds[1] = stdin：写入后关闭以产生 EOF。
        ParcelFileDescriptor.AutoCloseOutputStream(fds[1]).bufferedWriter().use { writer ->
            writer.write(script)
        }
        // fds[0] = stdout：读到 EOF 后关闭。
        return ParcelFileDescriptor.AutoCloseInputStream(fds[0]).bufferedReader().use { it.readText() }
    }

    /**
     * 保存前缺席探测的固定脚本：不包含任何文件名，避免中文书名号/空格/引号转义。
     */
    fun exportProbeScript(): String =
        "echo CRA94_BEGIN\n" +
            "readlink -f /sdcard/Download\n" +
            "echo CRA94_CANON_RC=\$?\n" +
            "ls -A /storage/emulated/0/Download\n" +
            "echo CRA94_LS_RC=\$?\n"

    data class ExportProbe(
        val canonical: String,
        val canonicalRc: Int?,
        val listingRc: Int?,
        val entries: List<String>,
        val hasMarkers: Boolean,
    ) {
        /** 目录身份与列举都成功才算探测成功；空输出/缺标记一律失败。 */
        fun ok(expectedDirectory: String): Boolean =
            hasMarkers && canonical == expectedDirectory && canonicalRc == 0 && listingRc == 0
    }

    fun parseExportProbe(raw: String): ExportProbe {
        val lines = raw.lines().map { it.trimEnd('\r') }
        val begin = lines.indexOf("CRA94_BEGIN")
        val canonRcIndex = lines.indexOfFirst { it.startsWith("CRA94_CANON_RC=") }
        val lsRcIndex = lines.indexOfFirst { it.startsWith("CRA94_LS_RC=") }
        val canonical = lines.getOrNull(begin + 1)?.trim().orEmpty()
        val canonRc = lines.getOrNull(canonRcIndex)
            ?.removePrefix("CRA94_CANON_RC=")?.trim()?.toIntOrNull()
        val lsRc = lines.getOrNull(lsRcIndex)
            ?.removePrefix("CRA94_LS_RC=")?.trim()?.toIntOrNull()
        val entries = if (begin >= 0 && canonRcIndex > begin && lsRcIndex > canonRcIndex) {
            lines.subList(canonRcIndex + 1, lsRcIndex).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        return ExportProbe(
            canonical = canonical,
            canonicalRc = canonRc,
            listingRc = lsRc,
            entries = entries,
            hasMarkers = begin >= 0 && canonRcIndex > begin && lsRcIndex > canonRcIndex,
        )
    }
}
