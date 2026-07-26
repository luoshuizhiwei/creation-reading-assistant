package com.creationreadingassistant.data.remote

import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType

/**
 * 按 baseUrl 缓存 Retrofit 实例的工厂。
 *
 * 局域网同步的桌面端地址每次配对可能变化（不同 IP:port），不能写死 baseUrl，
 * 因此这里以 host 为 key 复用实例。鉴权头由 [AuthInterceptor] 在请求时注入。
 */
@Singleton
class SyncApiProvider @Inject constructor(
    private val authInterceptor: AuthInterceptor,
    private val configStore: SyncConfigStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val clients = ConcurrentHashMap<String, SyncApi>()

    private fun build(baseUrl: String): SyncApi {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            // 局域网明文 HTTP 需要（network_security_config 已放行 cleartext）
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncApi::class.java)
    }

    /** 获取指定 baseUrl 的 API（缓存）。 */
    fun forBaseUrl(baseUrl: String): SyncApi =
        clients.getOrPut(baseUrl) { build(baseUrl) }

    /** 使用已保存的配对配置获取 API；未配对返回 null。 */
    fun current(): SyncApi? {
        val cfg = configStore.config ?: return null
        return forBaseUrl(cfg.baseUrl)
    }
}
