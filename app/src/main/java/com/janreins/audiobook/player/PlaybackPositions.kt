package com.janreins.audiobook.player

object PlaybackPositions {
    fun resumePosition(savedMs: Long, durationMs: Long): Long {
        if (savedMs < 0 || (durationMs > 0 && savedMs >= durationMs - 2000)) return 0L
        return if (durationMs > 0) savedMs.coerceIn(0L, durationMs) else savedMs.coerceAtLeast(0L)
    }

    fun positionAfterCompletion(): Long = 0L

    class SaveThrottle(private val intervalMs: Long = 5000) {
        private var lastSavedMs: Long? = null
        fun shouldSave(nowMs: Long): Boolean = lastSavedMs?.let { nowMs - it >= intervalMs } ?: true
        fun markSaved(nowMs: Long) { lastSavedMs = nowMs }
    }
}
