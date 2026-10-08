package com.janreins.audiobook.player

object SmartRewind {
    fun rewindMsFor(pausedForMs: Long): Long = when {
        pausedForMs < 10_000L -> 0L
        pausedForMs < 60_000L -> 2_000L
        pausedForMs < 300_000L -> 5_000L
        pausedForMs < 1_800_000L -> 10_000L
        else -> 20_000L
    }
}
