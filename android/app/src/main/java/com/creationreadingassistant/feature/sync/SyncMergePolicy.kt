package com.creationreadingassistant.feature.sync

/**
 * 同步合并冲突策略（唯一真相源）。
 *
 * 两条合并路径必须共用同一策略，避免语义分叉：
 * - 局域网同步拉取：[com.creationreadingassistant.data.repository.SyncRepository]
 * - 本地 JSON / WebDAV 恢复：[JsonBridge.importFromString]
 *
 * 规则：
 * 1. 远端 revision > 本地 revision → 接受远端
 * 2. 远端 revision == 本地 revision → 比较 updated_at，取较新者
 * 3. 远端 revision < 本地 revision → 保留本地（跳过）
 */
object SyncMergePolicy {
    fun shouldAcceptRemote(
        remoteRevision: Int, localRevision: Int,
        remoteUpdatedAt: String, localUpdatedAt: String,
    ): Boolean {
        if (remoteRevision > localRevision) return true
        if (remoteRevision < localRevision) return false
        return remoteUpdatedAt > localUpdatedAt
    }
}
