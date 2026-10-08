package com.janreins.audiobook.player

object SeekMath {
    fun clampSeek(current: Long, delta: Long, duration: Long): Long {
        val target = when {
            delta > 0 && current > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && current < Long.MIN_VALUE - delta -> Long.MIN_VALUE
            else -> current + delta
        }
        return if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
    }
}
