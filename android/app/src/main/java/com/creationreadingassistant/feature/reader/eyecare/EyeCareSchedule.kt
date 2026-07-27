package com.creationreadingassistant.feature.reader.eyecare

import kotlin.math.ln
import kotlin.math.pow

object EyeCareSchedule {
    fun isActive(nowMinute: Int, startMinute: Int, endMinute: Int): Boolean {
        val now = nowMinute.floorMod(1_440)
        val start = startMinute.floorMod(1_440)
        val end = endMinute.floorMod(1_440)
        if (start == end) return true
        return if (start < end) now in start until end else now >= start || now < end
    }

    /** 色温近似到 sRGB；供 Multiply 暖色滤镜使用。 */
    fun rgbForKelvin(kelvin: Int): Triple<Int, Int, Int> {
        val temp = kelvin.coerceIn(2_600, 5_500) / 100.0
        val red = if (temp <= 66) 255.0 else 329.698727446 * (temp - 60).pow(-0.1332047592)
        val green = if (temp <= 66) {
            99.4708025861 * ln(temp) - 161.1195681661
        } else {
            288.1221695283 * (temp - 60).pow(-0.0755148492)
        }
        val blue = when {
            temp >= 66 -> 255.0
            temp <= 19 -> 0.0
            else -> 138.5177312231 * ln(temp - 10) - 305.044792731
        }
        return Triple(
            red.coerceIn(0.0, 255.0).toInt(),
            green.coerceIn(0.0, 255.0).toInt(),
            blue.coerceIn(0.0, 255.0).toInt(),
        )
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
