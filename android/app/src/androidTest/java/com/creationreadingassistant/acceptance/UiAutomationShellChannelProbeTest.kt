package com.creationreadingassistant.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 94-v13：UiAutomation root-shell 通道的**窄只读**验证（不创建、不删除任何资产）。
 *
 * 目的：用生产适配 [UiAutomationShellChannel] 在真实设备上证明
 * `executeShellCommandRw` 的 stdin/stdout 方向与 EOF 顺序可用，并证明
 * 保存前缺席探测的判定规则：
 *  - 目录可列、canonical 与两个 rc 标记齐全才成功；
 *  - 已存在的目标必须被判为“存在”（拒绝保存）；
 *  - 列举失败（rc!=0）或空输出（缺标记）必须判为探测失败，不等于“缺席”。
 *
 * 文件名不进入脚本，因此中文书名号/空格/引号不会造成转义问题；这里用本轮唯一
 * 随机名验证“不存在”分支，并验证任取一个真实已存在条目能被精确匹配。
 */
@RunWith(AndroidJUnit4::class)
class UiAutomationShellChannelProbeTest {

    private val runId = "T94V13-PROBE-" + UUID.randomUUID().toString().take(8)
    private val download = "/storage/emulated/0/Download"

    @Test
    fun channelInterpretsStdinScriptAndExposesExitCode() {
        val output = UiAutomationShellChannel.run("echo CRA94_ECHO\necho CRA94_RC=\$?\n")
        assertTrue("stdin 脚本必须被 shell 解释，实得：$output", output.contains("CRA94_ECHO"))
        assertTrue("退出码标记必须出现，实得：$output", output.contains("CRA94_RC=0"))
    }

    @Test
    fun downloadDirectoryIsListableAndProbeSucceeds() {
        val raw = UiAutomationShellChannel.run(UiAutomationShellChannel.exportProbeScript())
        val probe = UiAutomationShellChannel.parseExportProbe(raw)
        assertTrue("canonical 必须是 $download，实得 ${probe.canonical}", probe.canonical == download)
        assertTrue("canonical rc 必须为 0，实得 ${probe.canonicalRc}", probe.canonicalRc == 0)
        assertTrue("列举 rc 必须为 0，实得 ${probe.listingRc}", probe.listingRc == 0)
        assertTrue("探测必须整体成功", probe.ok(download))
        assertTrue("Download 目录不应为空（用于证明列举真实发生）", probe.entries.isNotEmpty())
    }

    @Test
    fun existingTargetMatchesAndAbsentTargetDoesNot() {
        val raw = UiAutomationShellChannel.run(UiAutomationShellChannel.exportProbeScript())
        val probe = UiAutomationShellChannel.parseExportProbe(raw)
        val existing = probe.entries.first()
        // 生产规则：probe.ok && fileName in entries → 已存在，拒绝保存。
        assertTrue("已存在条目必须能被精确匹配：$existing", existing in probe.entries)
        // 本轮随机目标（含中文书名号与空格）必须判为不存在；文件名从不进入脚本。
        val absent = "《探测 书名》-$runId.md"
        assertFalse("不存在目标不得被误判为存在：$absent", absent in probe.entries)
    }

    @Test
    fun failedListingIsProbeFailureNotEmptySuccess() {
        // 与生产脚本同构：BEGIN → canonical + rc → ls + rc；否则解析器本身就会按“缺标记”拒绝，
        // 无法单独证明“列举 rc!=0”这一分支。
        val raw = UiAutomationShellChannel.run(
            "echo CRA94_BEGIN\n" +
                "readlink -f $download\n" +
                "echo CRA94_CANON_RC=\$?\n" +
                "ls -A $download/cra-v13-missing-$runId\n" +
                "echo CRA94_LS_RC=\$?\n",
        )
        val probe = UiAutomationShellChannel.parseExportProbe(raw)
        assertTrue("失败列举仍须带回 rc 标记：$raw", probe.hasMarkers)
        assertFalse("失败列举的 rc 不得为 0", probe.listingRc == 0)
        assertFalse("探测失败不得被当作成功", probe.ok(download))
    }

    @Test
    fun emptyScriptHasNoMarkersAndIsRejected() {
        val raw = UiAutomationShellChannel.run("")
        val probe = UiAutomationShellChannel.parseExportProbe(raw)
        assertFalse("空脚本不得产生标记：$raw", probe.hasMarkers)
        assertFalse("空输出不得被当作缺席成功", probe.ok(download))
    }
}
