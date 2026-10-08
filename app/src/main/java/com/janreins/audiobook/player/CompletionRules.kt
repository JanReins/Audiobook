package com.janreins.audiobook.player

/** When a book counts as "at the end", and what that means for resume and the Finished flag. */
object CompletionRules {
    /** Same tolerance as [PlaybackPositions.resumePosition]: the last 2 s count as the end. */
    const val END_TOLERANCE_MS = 2_000L

    /**
     * True when [positionMs] in [trackIndex] is at the end of the whole book. With an unknown track
     * duration the position can't tell, so the stored Finished flag decides (last track only).
     */
    fun isAtEnd(
        trackIndex: Int, trackCount: Int, positionMs: Long, trackDurationMs: Long, finished: Boolean
    ): Boolean {
        if (trackCount <= 0 || trackIndex < trackCount - 1) return false
        if (trackDurationMs <= 0L) return finished
        return positionMs >= trackDurationMs - END_TOLERANCE_MS
    }

    /**
     * Pressing play restarts the book from the first track only if playback actually ended or the
     * resume point is at the end. Anywhere else (including after seeking back from the end) it continues.
     */
    fun shouldRestart(ended: Boolean, atEnd: Boolean): Boolean = ended || atEnd

    /** An explicit seek, skip or scrub away from the end means the book is being listened to again. */
    fun finishedAfterSeek(wasFinished: Boolean, atEnd: Boolean): Boolean = wasFinished && atEnd
}
