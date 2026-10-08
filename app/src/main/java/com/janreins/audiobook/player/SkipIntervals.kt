package com.janreins.audiobook.player

object SkipIntervals {
    val allowedValues: List<Int> = listOf(5, 10, 15, 30, 45, 60)
    const val DEFAULT_BACK = 15
    const val DEFAULT_FORWARD = 30
    fun sanitize(seconds: Int, default: Int = DEFAULT_BACK): Int =
        if (seconds in allowedValues) seconds else if (default in allowedValues) default else DEFAULT_BACK
}
