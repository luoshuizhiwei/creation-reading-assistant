package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时长单位枚举的存储映射。
 *
 * 关键约束：**未知 / 缺失值必须回落到默认小时**——否则一次脏数据就会把全 App 的
 * 时长展示切成天，且用户无从察觉。
 */
class DurationUnitTest {

    @Test
    fun `storage values round trip`() {
        assertEquals(DurationUnit.HOURS, DurationUnit.fromStorage(DurationUnit.HOURS.storageValue))
        assertEquals(DurationUnit.DAYS, DurationUnit.fromStorage(DurationUnit.DAYS.storageValue))
    }

    @Test
    fun `unknown or missing value falls back to hours`() {
        assertEquals(DurationUnit.HOURS, DurationUnit.fromStorage(null))
        assertEquals(DurationUnit.HOURS, DurationUnit.fromStorage(""))
        assertEquals(DurationUnit.HOURS, DurationUnit.fromStorage("Days"))
        assertEquals(DurationUnit.HOURS, DurationUnit.fromStorage("whatever"))
    }
}
