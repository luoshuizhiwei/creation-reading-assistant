package com.creationreadingassistant.data.remote

import com.creationreadingassistant.domain.model.SyncEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 局域网同步契约 —— 严格对齐 src/types/sync.ts。
 *
 * envelope 的 payload 采用 [JsonObject] 透传：服务端返回的业务对象（LibraryBook /
 * InspirationItem / ReadingProgress / ReadingSession）直接作为 JSON 存入本地表的
 * `payload TEXT` 真相源列，无需在原生端为每类业务对象各建一套模型（见 P3_DESIGN.md）。
 */
object SyncContract {

    @Serializable
    enum class SyncPlatform { desktop, android, ios, web }

    @Serializable
    enum class SyncRecordType { inspiration, book, progress, session }

    @Serializable
    data class DeviceInfo(
        val deviceId: String,
        val name: String,
        val platform: SyncPlatform,
        val pairedAt: String,
        val lastSeenAt: String,
    )

    @Serializable
    data class BookFileManifest(
        val bookId: String,
        val fileName: String,
        val format: String,
        val contentHash: String? = null,
        val size: Int,
        val chunkSize: Int,
    )

    @Serializable
    data class SyncManifest(
        val device: DeviceInfo,
        val generatedAt: String,
        val inspirations: List<SyncEnvelope<JsonObject>>,
        val books: List<SyncEnvelope<JsonObject>>,
        val progress: List<SyncEnvelope<JsonObject>>,
        val sessions: List<SyncEnvelope<JsonObject>>,
        val bookFiles: List<BookFileManifest>,
    )

    @Serializable
    data class SyncPullResponse(
        val manifest: SyncManifest,
        val inspirations: List<SyncEnvelope<JsonObject>>,
        val books: List<SyncEnvelope<JsonObject>>,
        val progress: List<SyncEnvelope<JsonObject>>,
        val sessions: List<SyncEnvelope<JsonObject>>,
    )

    @Serializable
    data class SyncPushPayload(
        val device: DeviceInfo,
        val inspirations: List<SyncEnvelope<JsonObject>> = emptyList(),
        val books: List<SyncEnvelope<JsonObject>> = emptyList(),
        val progress: List<SyncEnvelope<JsonObject>> = emptyList(),
        val sessions: List<SyncEnvelope<JsonObject>> = emptyList(),
    )

    @Serializable
    data class SyncPushResult(
        val ok: Boolean,
        val applied: AppliedCount = AppliedCount(),
        val conflicts: List<SyncEnvelope<JsonObject>> = emptyList(),
        val manifest: SyncManifest,
    ) {
        @Serializable
        data class AppliedCount(
            val inspirations: Int = 0,
            val books: Int = 0,
            val progress: Int = 0,
            val sessions: Int = 0,
        )
    }

    @Serializable
    data class PairingTokenResult(
        val token: String,
        val pairingUrl: String = "",
        val qrPayload: String = "",
        val pairingUrls: List<String> = emptyList(),
        val qrPayloads: List<QrPayloadEntry> = emptyList(),
        val expiresAt: String = "",
    ) {
        @Serializable
        data class QrPayloadEntry(
            val address: String = "",
            val pairingUrl: String = "",
            val qrPayload: String = "",
        )
    }

    /** 移动端调用桌面端 POST /sync/pair 的请求体。 */
    @Serializable
    data class PairRequestBody(
        val device: DeviceInfo,
        val token: String? = null,
    )

    /**
     * 移动端调用 /sync/pair 的响应（桌面端返回形态，见 P3_DESIGN.md 待确认项）。
     * 字段全部可空：若桌面端未返回 token，则回退使用二维码携带的一次性 token。
     */
    @Serializable
    data class PairingAck(
        val token: String? = null,
        val deviceId: String? = null,
        val expiresAt: String? = null,
    )

    /** POST /sync/pull 的请求体。 */
    @Serializable
    data class SyncPullPayloadRequest(val device: DeviceInfo)
}
