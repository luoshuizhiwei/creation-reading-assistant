package com.creationreadingassistant.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * 桌面端同步服务端 API（Electron 主进程 node:http 自建）。
 * 鉴权由 [AuthInterceptor] 统一注入 x-device-id / x-sync-token 头。
 *
 * 端点对齐 src/types/sync.ts 与 Electron 服务端实现：
 * - POST /sync/pair                 完成配对，返回 token
 * - GET  /sync/manifest             仅清单（轻量探查）
 * - POST /sync/pull                 拉取全量数据
 * - POST /sync/push                 推送本地变更，返回冲突
 * - GET  /sync/books/{id}/file      下载书籍正文文件
 * - PUT  /sync/books/{id}/file      上传书籍正文文件
 * - GET  /sync/books/{id}/chunks/{index}  分块下载
 */
interface SyncApi {

    @POST("sync/pair")
    suspend fun pair(@Body body: SyncContract.PairRequestBody): SyncContract.PairingAck

    @GET("sync/manifest")
    suspend fun manifest(): SyncContract.SyncManifest

    @POST("sync/pull")
    suspend fun pull(@Body body: SyncContract.SyncPullPayloadRequest): SyncContract.SyncPullResponse

    @POST("sync/push")
    suspend fun push(@Body body: SyncContract.SyncPushPayload): SyncContract.SyncPushResult

    @Streaming
    @GET("sync/books/{id}/file")
    suspend fun getBookFile(@Path("id") id: String): Response<ResponseBody>

    @Streaming
    @PUT("sync/books/{id}/file")
    suspend fun putBookFile(@Path("id") id: String, @Body body: RequestBody): Response<Unit>

    @Streaming
    @GET("sync/books/{id}/chunks/{index}")
    suspend fun getBookChunk(@Path("id") id: String, @Path("index") index: Int): Response<ResponseBody>
}
