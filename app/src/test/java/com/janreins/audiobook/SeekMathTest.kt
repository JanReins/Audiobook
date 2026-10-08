package com.janreins.audiobook

import com.janreins.audiobook.player.SeekMath
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekMathTest {
    @Test fun clampsBothEnds() {
        assertEquals(0L, SeekMath.clampSeek(10_000, -60_000, 100_000))
        assertEquals(100_000L, SeekMath.clampSeek(80_000, 60_000, 100_000))
        assertEquals(65_000L, SeekMath.clampSeek(50_000, 15_000, 100_000))
        assertEquals(35_000L, SeekMath.clampSeek(50_000, -15_000, 100_000))
    }

    @Test fun unknownDurationHasNoUpperBound() {
        assertEquals(140_000L, SeekMath.clampSeek(80_000, 60_000, 0))
        assertEquals(140_000L, SeekMath.clampSeek(80_000, 60_000, Long.MIN_VALUE + 1))
        assertEquals(0L, SeekMath.clampSeek(10_000, -60_000, -1))
    }

    @Test fun avoidsOverflow() {
        assertEquals(Long.MAX_VALUE, SeekMath.clampSeek(Long.MAX_VALUE - 1, 60_000, 0))
        assertEquals(0L, SeekMath.clampSeek(1, Long.MIN_VALUE, 0))
        assertEquals(0L, SeekMath.clampSeek(-1, Long.MIN_VALUE, 0))
    }
}
