package com.creationreadingassistant.feature.sync

import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫码配对：解析桌面端展示的二维码 → 调用 /sync/pair → 持久化配对配置。
 *
 * 二维码内容兼容三种形态（尽力兼容桌面端不同版本）：
 *  1) JSON：含 address / pairingUrl / baseUrl 之一 + 可选 token / pairingToken
 *  2) URL：含 /sync/pair?token=...，从中抽取 host 与 token
 *  3) 裸地址：http(s)://host:port，当作同步根地址
 */
@Singleton
class PairingManager @Inject constructor(
    private val apiProvider: SyncApiProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val configStore: SyncConfigStore,
) {
    data class PairingInfo(
        val baseUrl: String,
        val oneTimeToken: String? = null,
    )

    fun parseQr(raw: String): PairingInfo {
        val trimmed = raw.trim()
        // 1) 尝试 JSON。地址校验必须留在 runCatching 之外：否则「拒绝公网地址」的异常
        //    会被这里的 catch 吞掉，退化成后续分支的误导性解析错误。
        val fromJson = runCatching {
            val obj = JSONObject(trimmed)
            val address = obj.optString("address").takeIf { it.isNotEmpty() }
                ?: obj.optString("pairingUrl").takeIf { it.isNotEmpty() }
                ?: obj.optString("baseUrl").takeIf { it.isNotEmpty() }
            val token = obj.optString("token").takeIf { it.isNotEmpty() }
                ?: obj.optString("pairingToken").takeIf { it.isNotEmpty() }
            address?.let { it to token }
        }.getOrNull()
        if (fromJson != null) {
            return PairingInfo(normalize(fromJson.first), fromJson.second)
        }
        // 2) 尝试 URL（含 /sync/pair）
        if (trimmed.startsWith("http", ignoreCase = true)) {
            val token = Regex("""[?&]token=([^&]+)""").find(trimmed)?.groupValues?.get(1)
            val base = if (trimmed.contains("/sync/pair")) trimmed.substringBefore("/sync/pair") else trimmed
            return PairingInfo(normalize(base), token)
        }
        // 3) 兜底：当作裸地址
        return PairingInfo(normalize(trimmed))
    }

    private fun normalize(url: String): String {
        var u = url.trim().trimEnd('/')
        if (!u.startsWith("http", ignoreCase = true)) u = "http://$u"
        requireLanTarget(u)
        return u
    }

    /**
     * 拒绝指向公网的配对地址。
     *
     * 同步链路是明文 HTTP，且配对成功后本机会把整个书库和创作数据推送给对端，
     * 因此二维码只允许指向本机或局域网内的桌面端。若不做这一层校验，
     * 一张伪造二维码就能把地址指到任意公网服务器并静默窃取全部数据。
     */
    private fun requireLanTarget(url: String) {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
            ?: throw IllegalArgumentException("配对地址格式无法解析：$url")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("配对地址协议不受支持：$scheme")
        }
        val host = uri.host ?: throw IllegalArgumentException("配对地址缺少主机名：$url")
        if (!isLanHost(host)) {
            throw IllegalArgumentException(
                "已拒绝配对：$host 不在局域网内。同步二维码只能指向本机或同一局域网的桌面端。",
            )
        }
    }

    companion object {
        /**
         * 判断主机是否属于本机/局域网。
         *
         * 只按字面量判断，不做 DNS 解析：解析既会引入主线程网络调用，
         * 也会让攻击者可以用一个解析到内网地址的域名绕过校验（DNS rebinding）。
         * 因此非 IP 主机名仅放行 localhost 与 mDNS 的 *.local。
         */
        internal fun isLanHost(rawHost: String): Boolean {
            val host = rawHost.trim().removePrefix("[").removeSuffix("]").lowercase()
            if (host.isEmpty()) return false
            if (host == "localhost") return true
            if (host.endsWith(".local")) return true
            if (host.contains(':')) return isPrivateIpv6(host)
            return isPrivateIpv4(host)
        }

        private fun isPrivateIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val nums = parts.map { part ->
                // 拒绝空段、超长段和非数字段（"0x7f" 之类）。
                if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return false
                // 拒绝前导零（"010"）：不同解析器对它有十进制/八进制两种解释，
                // 留着就意味着「本地判定为内网、实际连到别处」的绕过空间。
                if (part.length > 1 && part[0] == '0') return false
                part.toInt()
            }
            if (nums.any { it > 255 }) return false
            val (a, b) = nums
            return when {
                a == 127 -> true                    // 127.0.0.0/8   回环
                a == 10 -> true                     // 10.0.0.0/8    私有
                a == 192 && b == 168 -> true        // 192.168.0.0/16 私有
                a == 172 && b in 16..31 -> true     // 172.16.0.0/12  私有
                a == 169 && b == 254 -> true        // 169.254.0.0/16 链路本地
                else -> false
            }
        }

        private fun isPrivateIpv6(host: String): Boolean {
            val h = host.substringBefore('%')       // 去掉 zone id，如 fe80::1%wlan0
            if (h == "::1") return true
            val firstGroup = h.substringBefore(':').ifEmpty { return false }
            val prefix = firstGroup.toIntOrNull(16) ?: return false
            if ((prefix and 0xFE00) == 0xFC00) return true   // fc00::/7  唯一本地地址
            return (prefix and 0xFFC0) == 0xFE80             // fe80::/10 链路本地
        }
    }

    /**
     * 完成配对。调用桌面端 /sync/pair，优先采用服务端返回的 token；
     * 若服务端未返回，则回退到二维码携带的一次性 token（否则报错）。
     */
    suspend fun pair(info: PairingInfo): SyncConfigStore.Config {
        val device = deviceInfoProvider.provide()
        val api = apiProvider.forBaseUrl(info.baseUrl)
        val ack = api.pair(SyncContract.PairRequestBody(device, info.oneTimeToken))
        val token = ack.token ?: info.oneTimeToken
            ?: error("配对失败：服务端未返回 token，且二维码未携带一次性 token")
        val deviceId = ack.deviceId ?: device.deviceId
        val config = SyncConfigStore.Config(
            baseUrl = info.baseUrl,
            token = token,
            deviceId = deviceId,
            pairedAt = Instant.now().toString(),
        )
        configStore.config = config
        return config
    }

    fun isPaired(): Boolean = configStore.config != null

    fun clearPairing() = configStore.clear()
}
