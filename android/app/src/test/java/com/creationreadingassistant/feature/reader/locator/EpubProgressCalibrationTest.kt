package com.creationreadingassistant.feature.reader.locator

import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EPUB 进度校准纯策略锁定：
 * - 无实测数据 → 与旧估算公式一致（安全降级）；
 * - 全部实测 → 真实字符口径精确，末章读完到 100%；
 * - mixedGlobalOffsetForPercent 与 percentFor 互为逆映射（分页跳转坐标不漂移）。
 */
class EpubProgressCalibrationTest {

    private val est = listOf(3000, 6000, 9000)

    private fun model(measured: Map<Int, Int>) =
        EpubProgressCalibration.Model(estimatedLengths = est, measuredRealChars = measured)

    @Test
    fun `no measurements degrade to legacy estimate space`() {
        val m = model(emptyMap())
        assertEquals(1f, EpubProgressCalibration.calibrationRatio(m), 1e-6f)
        val legacyGlobal = LegacyOffsetCodec.chapterStartOffsets(est)[1] + 500
        val legacyPercent = legacyGlobal * 100f / LegacyOffsetCodec.totalChars(est)
        val calibrated = EpubProgressCalibration.percentFor(m, chapterIndex = 1, offsetInChapter = 500)
        // 章间分隔位的 ±n 字符差异对百分比应可忽略
        assertTrue("expected $calibrated ~ $legacyPercent", kotlin.math.abs(calibrated - legacyPercent) < 0.1f)
    }

    @Test
    fun `fully measured model reaches 100 percent at book end`() {
        // 中文书典型：真实字符数约为估算的 1/3
        val m = model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))
        val percent = EpubProgressCalibration.percentFor(m, chapterIndex = 2, offsetInChapter = 3000)
        assertEquals(100f, percent, 0.01f)
    }

    @Test
    fun `fully measured model computes exact real percent`() {
        val m = model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))
        assertEquals(25f, EpubProgressCalibration.percentFor(m, 1, 500), 0.01f)
        assertEquals(0f, EpubProgressCalibration.percentFor(m, 0, 0), 0.01f)
    }

    @Test
    fun `offset beyond chapter length is clamped not overflowing`() {
        val m = model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))
        val percent = EpubProgressCalibration.percentFor(m, 0, 99_999)
        assertTrue(percent <= 100f)
        assertEquals(EpubProgressCalibration.percentFor(m, 0, 1000), percent, 1e-4f)
    }

    @Test
    fun `mixed global offset is inverse of percent with full measurement`() {
        val m = model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))
        val starts = LegacyOffsetCodec.chapterStartOffsets(est)
        val offset = EpubProgressCalibration.mixedGlobalOffsetForPercent(m, 25f)
        // 25% 对应第 1 章 500 真实字符处
        assertEquals(starts[1] + 500, offset!!)
    }

    @Test
    fun `mixed global offset boundaries map to book ends`() {
        val m = model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))
        val starts = LegacyOffsetCodec.chapterStartOffsets(est)
        assertEquals(0, EpubProgressCalibration.mixedGlobalOffsetForPercent(m, 0f))
        val end = EpubProgressCalibration.mixedGlobalOffsetForPercent(m, 100f)
        assertEquals(starts[2] + 3000, end!!)
    }

    @Test
    fun `empty book yields null offset and zero percent`() {
        val m = EpubProgressCalibration.Model(emptyList(), emptyMap())
        assertNull(EpubProgressCalibration.mixedGlobalOffsetForPercent(m, 50f))
        assertEquals(0f, EpubProgressCalibration.percentFor(m, 0, 0), 1e-6f)
    }

    @Test
    fun `ratio ignores invalid samples and clamps extremes`() {
        assertEquals(1f, EpubProgressCalibration.calibrationRatio(model(emptyMap())), 1e-6f)
        // 全部样本非法（0 长度 / 越界索引）→ 回退 1
        assertEquals(1f, EpubProgressCalibration.calibrationRatio(model(mapOf(0 to 0, 9 to 100))), 1e-6f)
        // 正常中文书比值 ~1/3
        assertEquals(
            1f / 3f,
            EpubProgressCalibration.calibrationRatio(model(mapOf(0 to 1000, 1 to 2000, 2 to 3000))),
            1e-4f,
        )
    }
}
