package com.creationreadingassistant.feature.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定配对地址的安全边界。
 *
 * 同步链路是明文 HTTP，且配对成功后本机会把整个书库和创作数据推给对端，
 * 所以「只接受局域网地址」是一条安全约束而不是易用性偏好 —— 一旦放宽，
 * 一张伪造二维码就能把用户全部数据导向公网服务器。
 */
class PairingManagerLanHostTest {

    @Test
    fun `accepts loopback and private ipv4`() {
        listOf(
            "127.0.0.1",
            "127.255.255.255",
            "10.0.0.5",
            "10.255.255.254",
            "192.168.1.100",
            "172.16.0.1",
            "172.31.255.254",
            "169.254.1.1",
        ).forEach { host ->
            assertTrue("应接受局域网地址：$host", PairingManager.isLanHost(host))
        }
    }

    @Test
    fun `accepts localhost and mdns names`() {
        assertTrue(PairingManager.isLanHost("localhost"))
        assertTrue(PairingManager.isLanHost("LocalHost"))
        assertTrue(PairingManager.isLanHost("desktop.local"))
    }

    @Test
    fun `accepts loopback and private ipv6`() {
        listOf("::1", "[::1]", "fe80::1", "fe80::1%wlan0", "fd12::3456", "fc00::1").forEach { host ->
            assertTrue("应接受局域网 IPv6：$host", PairingManager.isLanHost(host))
        }
    }

    @Test
    fun `rejects public ipv4`() {
        listOf(
            "8.8.8.8",
            "1.2.3.4",
            "11.0.0.1",       // 紧邻 10/8 之外
            "172.15.0.1",     // 172.16/12 下边界之外
            "172.32.0.1",     // 172.16/12 上边界之外
            "192.169.1.1",    // 紧邻 192.168/16 之外
            "169.253.0.1",    // 紧邻 169.254/16 之外
        ).forEach { host ->
            assertFalse("应拒绝公网地址：$host", PairingManager.isLanHost(host))
        }
    }

    @Test
    fun `rejects public hostnames because dns is not resolved`() {
        // 不做 DNS 解析，避免攻击者用解析到内网的域名绕过校验（DNS rebinding）。
        listOf("evil.com", "sync.example.org", "localhost.evil.com", "192.168.1.1.evil.com").forEach { host ->
            assertFalse("应拒绝非字面量主机：$host", PairingManager.isLanHost(host))
        }
    }

    @Test
    fun `rejects public ipv6`() {
        listOf("2001:db8::1", "2606:4700::1111").forEach { host ->
            assertFalse("应拒绝公网 IPv6：$host", PairingManager.isLanHost(host))
        }
    }

    @Test
    fun `rejects non decimal and shorthand ipv4 forms`() {
        // 这些写法系统能解析成回环/内网，但形态不规范；一律拒绝而不是尝试解释，
        // 以免解析差异成为绕过点。
        listOf(
            "0x7f.0.0.1",
            "0177.0.0.1",
            "127.1",            // 简写回环
            "2130706433",       // 十进制整数形式的 127.0.0.1
            "127.0.0.01",
            "",
            "   ",
            "999.1.1.1",
        ).forEach { host ->
            assertFalse("应拒绝不规范写法：$host", PairingManager.isLanHost(host))
        }
    }
}
