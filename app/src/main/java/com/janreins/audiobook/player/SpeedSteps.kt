package com.janreins.audiobook.player

import java.util.Locale
import kotlin.math.roundToInt

object SpeedSteps {
    const val MIN = 0.5f
    const val MAX = 3f
    const val STEP = 0.05f
    fun clamp(speed: Float): Float = if (speed.isNaN()) 1f else speed.coerceIn(MIN, MAX)
    fun snap(speed: Float): Float = (clamp(speed) * 20).roundToInt() / 20f
    fun stepUp(speed: Float): Float = snap(snap(speed) + STEP)
    fun stepDown(speed: Float): Float = snap(snap(speed) - STEP)
    fun format(speed: Float): String = String.format(Locale.ROOT, "%.2f", snap(speed))
        .trimEnd('0').trimEnd('.') + "x"
}
