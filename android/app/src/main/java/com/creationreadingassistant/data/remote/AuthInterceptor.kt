package com.creationreadingassistant.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 为每个同步请求注入鉴权头。未配对（无配置）时直接放行（仅 /sync/pair 需要）。
 * 头名对齐桌面端约定：x-device-id + x-sync-token。
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val configStore: SyncConfigStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val cfg = configStore.config
        val request = if (cfg == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .addHeader(HEADER_DEVICE_ID, cfg.deviceId)
                .addHeader(HEADER_TOKEN, cfg.token)
                .build()
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_DEVICE_ID = "x-device-id"
        const val HEADER_TOKEN = "x-sync-token"
    }
}
