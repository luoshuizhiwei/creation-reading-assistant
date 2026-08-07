package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.DatabaseSafetyNet
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore

/**
 * 「我的」页 WebDAV 远程备份管理器 —— 备份上传 / 连接测试 / 下载恢复 / 备份列表。
 *
 * 纯逻辑类：不持有协程作用域，所有入口均为 suspend 函数，由调用方（ViewModel）
 * 负责 launch 调度与状态更新；因此可以在 JVM 测试中用 runTest 直接驱动。
 */
class ProfileWebDavManager(
    private val webDavConfigStore: WebDavConfigStore,
    private val webDavBackup: WebDavBackup,
    private val jsonBridge: JsonBridge,
) {

    /**
     * 备份列表。返回 null 表示尚未配置 WebDAV（调用方应保持现状不做更新）；
     * 失败时返回 Result.failure（调用方通常置空列表）。
     */
    suspend fun listBackups(): Result<List<WebDavBackup.BackupFile>>? {
        val cfg = webDavConfigStore.config ?: return null
        return runCatching { webDavBackup.listBackups(cfg.url, cfg.user, cfg.pass).getOrThrow() }
    }

    /** 全量备份：写「最新」固定名 + 带时间戳归档。成功消息「备份成功」。 */
    suspend fun backup(context: Context): Result<String> = runCatching {
        val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
        val json = jsonBridge.exportToString(context)
        // 同时写「最新」固定名（供下载恢复确定性拉取）与带时间戳的归档
        webDavBackup.put(cfg.url, cfg.user, cfg.pass, "cra-backup-latest.json", json).getOrThrow()
        webDavBackup.put(cfg.url, cfg.user, cfg.pass, "cra-backup-${System.currentTimeMillis()}.json", json).getOrThrow()
        "备份成功"
    }

    /** 连接测试。成功消息形如「WebDAV 连接成功（HTTP 200）」。 */
    suspend fun test(): Result<String> = runCatching {
        val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
        "WebDAV ${webDavBackup.test(cfg.url, cfg.user, cfg.pass).getOrThrow()}"
    }

    /** 下载指定备份并恢复。执行前先做整库文件快照兜底；成功消息「已从 WebDAV 恢复备份」。 */
    suspend fun downloadRestore(context: Context, filename: String = "cra-backup-latest.json"): Result<String> = runCatching {
        val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
        // 恢复会合并覆写当前数据：先留一份整库快照，万一恢复出问题可从快照捞回。
        // 快照失败只记日志、不阻塞恢复（DatabaseSafetyNet 内部已吞异常）。
        DatabaseSafetyNet.snapshotNow(context, AppDatabase.DB_NAME)
        val json = webDavBackup.get(cfg.url, cfg.user, cfg.pass, filename).getOrThrow()
        jsonBridge.importFromString(context, json)
        "已从 WebDAV 恢复备份"
    }
}
