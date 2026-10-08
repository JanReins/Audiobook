package com.janreins.audiobook

import com.janreins.audiobook.player.PlaybackPositions
import org.junit.Assert.*
import org.junit.Test

class PlaybackPositionsTest {
    @Test fun resumePositions() {
        assertEquals(3000L, PlaybackPositions.resumePosition(3000, 10000))
        assertEquals(0L, PlaybackPositions.resumePosition(-1, 10000))
        assertEquals(0L, PlaybackPositions.resumePosition(8000, 10000))
        assertEquals(0L, PlaybackPositions.resumePosition(10000, 10000))
        assertEquals(0L, PlaybackPositions.resumePosition(12000, 10000))
        assertEquals(3000L, PlaybackPositions.resumePosition(3000, 0))
        assertEquals(3000L, PlaybackPositions.resumePosition(3000, -1))
        assertEquals(0L, PlaybackPositions.resumePosition(-1, 0))
    }

    @Test fun completionKeepsEndPositionAndReplayResumesAtZero() {
        val end = PlaybackPositions.positionAfterCompletion(10000L)
        assertEquals(10000L, end)
        assertEquals(0L, PlaybackPositions.resumePosition(end, 10000L))
        assertEquals(0L, PlaybackPositions.positionAfterCompletion(-1L))
    }

    @Test fun savesAreThrottled() {
        val throttle = PlaybackPositions.SaveThrottle()
        assertTrue(throttle.shouldSave(1000))
        throttle.markSaved(1000)
        assertFalse(throttle.shouldSave(5999))
        assertTrue(throttle.shouldSave(6000))
        throttle.markSaved(6000)
        assertFalse(throttle.shouldSave(6001))
        assertTrue(throttle.shouldSave(11001))
    }
}
