package com.janreins.audiobook

import com.janreins.audiobook.player.SleepFade
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepFadeTest {
    @Test fun linearRampAndBounds() {
        assertEquals(1f, SleepFade.volumeFor(30_000L), 0.0001f)
        assertEquals(0.55f, SleepFade.volumeFor(15_000L), 0.0001f)
        assertEquals(0.1f, SleepFade.volumeFor(0L), 0.0001f)
        assertEquals(1f, SleepFade.volumeFor(60_000L), 0.0001f)
        assertEquals(0.1f, SleepFade.volumeFor(-1L), 0.0001f)
        assertEquals(0.55f, SleepFade.volumeFor(5_000L, 10_000L), 0.0001f)
    }
}
