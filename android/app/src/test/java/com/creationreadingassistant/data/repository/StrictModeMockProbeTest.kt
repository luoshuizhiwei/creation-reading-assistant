package com.creationreadingassistant.data.repository

import android.os.StrictMode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.Runs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 探针：JVM 单测能否用 mockk 顶替 android.os.StrictMode（v3 缺陷 D 的前置验证）。
 *
 * 目的：让 [SearchIndexRepository] 的 [allowDiskReads] 恢复 v1 的真实生产策略
 * （get → 放开 → 执行 → 严格恢复），不再为 JVM 存根在生产代码里 catch Throwable。
 */
class StrictModeMockProbeTest {

    @Test
    fun strictModeRoundTripCanBeMocked() {
        val policy = mockk<StrictMode.ThreadPolicy>(relaxed = true)
        val builderAfterReads = mockk<StrictMode.ThreadPolicy.Builder>(relaxed = true)
        val builderAfterWrites = mockk<StrictMode.ThreadPolicy.Builder>(relaxed = true)

        mockkConstructor(StrictMode.ThreadPolicy.Builder::class)
        every { anyConstructed<StrictMode.ThreadPolicy.Builder>().permitDiskReads() } returns builderAfterReads
        every { builderAfterReads.permitDiskWrites() } returns builderAfterWrites
        every { builderAfterWrites.build() } returns policy

        mockkStatic(StrictMode::class)
        every { StrictMode.getThreadPolicy() } returns policy
        every { StrictMode.setThreadPolicy(any()) } just Runs

        val old = StrictMode.getThreadPolicy()
        val relaxed = StrictMode.ThreadPolicy.Builder(old)
            .permitDiskReads()
            .permitDiskWrites()
            .build()
        assertNotNull("放开后的策略不得为 null（存根默认返回 null 会 NPE）", relaxed)
        assertSame(policy, relaxed)
        StrictMode.setThreadPolicy(relaxed)
        StrictMode.setThreadPolicy(old)
    }
}
