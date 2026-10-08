package com.janreins.audiobook

import com.janreins.audiobook.player.SmartRewind
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartRewindTest {
    @Test fun thresholdBoundaries() {
        listOf(-1L to 0L, 0L to 0L, 9_999L to 0L, 10_000L to 2_000L,
            59_999L to 2_000L, 60_000L to 5_000L, 299_999L to 5_000L,
            300_000L to 10_000L, 1_799_999L to 10_000L, 1_800_000L to 20_000L,
            Long.MAX_VALUE to 20_000L).forEach { (pause, rewind) ->
            assertEquals("pause=$pause", rewind, SmartRewind.rewindMsFor(pause))
        }
    }
}
