package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapActionPresetTest {

    private fun action(
        x: Float,
        y: Float = 500f,
        mode: String = "three-zone",
        canPrevious: Boolean = true,
        canNext: Boolean = true,
        preset: String = TapActionPreset.STANDARD,
    ) = resolveReaderTapAction(
        x = x,
        y = y,
        width = 900f,
        height = 1000f,
        tapZoneMode = mode,
        canPrevious = canPrevious,
        canNext = canNext,
        preset = preset,
    )

    @Test
    fun standardPresetLeftTurnBackRightTurnForward() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(100f, preset = TapActionPreset.STANDARD))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f, preset = TapActionPreset.STANDARD))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(800f, preset = TapActionPreset.STANDARD))
    }

    @Test
    fun leftHandPresetInvertsLeftAndRightTurns() {
        // 左手模式：点击左侧翻下一页，点击右侧翻上一页，中央仍是菜单
        assertEquals(ReaderTapAction.NEXT_PAGE, action(100f, preset = TapActionPreset.LEFT_HAND))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f, preset = TapActionPreset.LEFT_HAND))
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(800f, preset = TapActionPreset.LEFT_HAND))
    }

    @Test
    fun bothNextPresetMakesBothSidesTurnForward() {
        // 双向推进模式：左侧和右侧均向前翻页，中央仍是菜单
        assertEquals(ReaderTapAction.NEXT_PAGE, action(100f, preset = TapActionPreset.BOTH_NEXT))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f, preset = TapActionPreset.BOTH_NEXT))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(800f, preset = TapActionPreset.BOTH_NEXT))
    }

    @Test
    fun centerZoneAlwaysTogglesControlsAcrossAllPresets() {
        listOf(TapActionPreset.STANDARD, TapActionPreset.LEFT_HAND, TapActionPreset.BOTH_NEXT).forEach { preset ->
            assertEquals(
                "preset=$preset center should toggle controls",
                ReaderTapAction.TOGGLE_CONTROLS,
                action(450f, 500f, preset = preset),
            )
        }
    }

    @Test
    fun fiveZoneTopAndBottomTurnsWorkWithPresets() {
        // 五分区在所有预设下，顶部均为上一页，底部均为下一页
        listOf(TapActionPreset.STANDARD, TapActionPreset.LEFT_HAND, TapActionPreset.BOTH_NEXT).forEach { preset ->
            assertEquals(
                "preset=$preset top zone should turn prev",
                ReaderTapAction.PREVIOUS_PAGE,
                action(450f, 50f, mode = "five-zone", preset = preset),
            )
            assertEquals(
                "preset=$preset bottom zone should turn next",
                ReaderTapAction.NEXT_PAGE,
                action(450f, 950f, mode = "five-zone", preset = preset),
            )
        }
    }

    @Test
    fun boundaryProtectionWorksWhenPagesUnavailable() {
        // 首页无法上一页时返回 NONE
        assertEquals(ReaderTapAction.NONE, action(100f, canPrevious = false, preset = TapActionPreset.STANDARD))
        assertEquals(ReaderTapAction.NONE, action(800f, canPrevious = false, preset = TapActionPreset.LEFT_HAND))

        // 末页无法下一页时返回 NONE
        assertEquals(ReaderTapAction.NONE, action(800f, canNext = false, preset = TapActionPreset.STANDARD))
        assertEquals(ReaderTapAction.NONE, action(100f, canNext = false, preset = TapActionPreset.LEFT_HAND))
        assertEquals(ReaderTapAction.NONE, action(100f, canNext = false, preset = TapActionPreset.BOTH_NEXT))
        assertEquals(ReaderTapAction.NONE, action(800f, canNext = false, preset = TapActionPreset.BOTH_NEXT))
    }

    // ── S09-B：完整行为矩阵（预设 × 分区模式），钉住「只统一几何、不改预设语义」 ──

    @Test
    fun fullPresetByZoneModeMatrix() {
        data class Expectation(val label: String, val preset: String, val mode: String, val zones: Map<String, ReaderTapAction>)

        listOf(
            Expectation(
                "standard / three-zone", TapActionPreset.STANDARD, "three-zone",
                mapOf("left" to ReaderTapAction.PREVIOUS_PAGE, "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.NEXT_PAGE),
            ),
            Expectation(
                "left_hand / three-zone", TapActionPreset.LEFT_HAND, "three-zone",
                mapOf("left" to ReaderTapAction.NEXT_PAGE, "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.PREVIOUS_PAGE),
            ),
            Expectation(
                "both_next / three-zone", TapActionPreset.BOTH_NEXT, "three-zone",
                mapOf("left" to ReaderTapAction.NEXT_PAGE, "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.NEXT_PAGE),
            ),
            Expectation(
                "standard / five-zone", TapActionPreset.STANDARD, "five-zone",
                mapOf(
                    "top" to ReaderTapAction.PREVIOUS_PAGE, "left" to ReaderTapAction.PREVIOUS_PAGE,
                    "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.NEXT_PAGE,
                    "bottom" to ReaderTapAction.NEXT_PAGE,
                ),
            ),
            Expectation(
                "left_hand / five-zone", TapActionPreset.LEFT_HAND, "five-zone",
                mapOf(
                    "top" to ReaderTapAction.PREVIOUS_PAGE, "left" to ReaderTapAction.NEXT_PAGE,
                    "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.PREVIOUS_PAGE,
                    "bottom" to ReaderTapAction.NEXT_PAGE,
                ),
            ),
            Expectation(
                "both_next / five-zone", TapActionPreset.BOTH_NEXT, "five-zone",
                mapOf(
                    "top" to ReaderTapAction.PREVIOUS_PAGE, "left" to ReaderTapAction.NEXT_PAGE,
                    "center" to ReaderTapAction.TOGGLE_CONTROLS, "right" to ReaderTapAction.NEXT_PAGE,
                    "bottom" to ReaderTapAction.NEXT_PAGE,
                ),
            ),
        ).forEach { case ->
            val probes: Map<String, Pair<Float, Float>> = mapOf(
                "left" to (100f to 500f),
                "center" to (450f to 500f),
                "right" to (800f to 500f),
                "top" to (450f to 50f),
                "bottom" to (450f to 950f),
            )
            case.zones.forEach { (zone, expected) ->
                val (x, y) = probes.getValue(zone)
                // 顶/底带在 three-zone 下不存在，用例不会请求它们。
                assertEquals(
                    "${case.label} 的 $zone 区",
                    expected,
                    action(x, y, mode = case.mode, preset = case.preset),
                )
            }
        }
    }

    @Test
    fun bothNextThreeZoneHasNoTapBackPathByDesign() {
        // 双向推进 + 三分区：左右都是下一页，中央是菜单 —— 回退只能靠滑动手势。
        // 这是既有产品语义（本片不新增回退手势），但预览卡的说明必须与之一致，
        // 不得声称三分区还有「顶部热区」可回退。
        listOf(100f, 450f, 800f).forEach { x ->
            assertTrue(
                "x=$x 不得返回上一页",
                action(x, preset = TapActionPreset.BOTH_NEXT) != ReaderTapAction.PREVIOUS_PAGE,
            )
        }
        // 对照：五分区下顶部确实提供回退，说明文案在两种模式下必须不同。
        assertEquals(
            ReaderTapAction.PREVIOUS_PAGE,
            action(450f, 50f, mode = "five-zone", preset = TapActionPreset.BOTH_NEXT),
        )
    }

    @Test
    fun fiveZoneTopAndBottomRespectPageAvailabilityAcrossPresets() {
        listOf(TapActionPreset.STANDARD, TapActionPreset.LEFT_HAND, TapActionPreset.BOTH_NEXT).forEach { preset ->
            assertEquals(
                "preset=$preset 首页点顶部不得伪装成成功翻页",
                ReaderTapAction.NONE,
                action(450f, 50f, mode = "five-zone", canPrevious = false, preset = preset),
            )
            assertEquals(
                "preset=$preset 末页点底部不得伪装成成功翻页",
                ReaderTapAction.NONE,
                action(450f, 950f, mode = "five-zone", canNext = false, preset = preset),
            )
            // 反向对照：可用时同样的位置必须真的翻页。
            assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(450f, 50f, mode = "five-zone", preset = preset))
            assertEquals(ReaderTapAction.NEXT_PAGE, action(450f, 950f, mode = "five-zone", preset = preset))
        }
    }
}
