package com.janreins.audiobook

import com.janreins.audiobook.player.SkipIntervals
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipIntervalsTest {
    @Test fun allowedValuesAndDefaults() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60), SkipIntervals.allowedValues)
        SkipIntervals.allowedValues.forEach { assertEquals(it, SkipIntervals.sanitize(it)) }
        assertEquals(15, SkipIntervals.sanitize(7))
        assertEquals(30, SkipIntervals.sanitize(-1, SkipIntervals.DEFAULT_FORWARD))
        assertEquals(15, SkipIntervals.sanitize(999, 999))
    }
}
