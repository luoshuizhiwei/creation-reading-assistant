package com.creationreadingassistant.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 同步信封 —— 严格对齐 src/types/sync.ts 的 SyncEnvelope<T>。
 *
 * 字段语义：
 * - type: inspiration | book | progress | session
 * - payload: 业务对象（原生端 JSON 桥接时即实体本身；P3 局域网同步时改为跨端对象）
 * - deletedAt: 软删除时间戳，非空表示已删除
 *
 * 该模型是 P1「JSON 导出/导入桥接」与 P3「局域网同步」共用的契约。
 */
@Serializable
data class SyncEnvelope<T>(
    val id: String,
    val type: String,
    val revision: Int,
    val deviceId: String,
    @SerialName("updatedAt") val updatedAt: String,
    val deletedAt: String? = null,
    val payload: T,
)
