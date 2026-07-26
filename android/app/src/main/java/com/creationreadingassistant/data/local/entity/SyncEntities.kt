package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 同步账号（WebDAV / 桌面端等），对齐 V2 sync_accounts 表。
 */
@Serializable
@Entity(tableName = "sync_accounts")
data class SyncAccountEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val name: String? = null,
    val endpoint: String? = null,
    val username: String? = null,
    val enabled: Int = 0,
    val last_sync_at: String? = null,
    val created_at: String? = null,
    val device_id: String? = null,
    val revision: Int = 1,
    val payload: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 同步状态键值表（deviceId / token / lastManifest 等），对齐 V2 sync_state 表。
 */
@Serializable
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updated_at: String,
)
