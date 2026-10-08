package com.janreins.audiobook.player

object SleepFade {
    fun volumeFor(remainingMs: Long, fadeMs: Long = 30_000L): Float {
        if (fadeMs <= 0L) return if (remainingMs > 0L) 1f else 0.1f
        return 0.1f + 0.9f * (remainingMs.toDouble() / fadeMs).coerceIn(0.0, 1.0).toFloat()
    }
}
