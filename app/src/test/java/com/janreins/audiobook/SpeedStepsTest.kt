package com.janreins.audiobook

import com.janreins.audiobook.player.SpeedSteps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SpeedStepsTest {
    @Test fun clampsAndSnaps() {
        assertEquals(0.5f, SpeedSteps.clamp(-1f), 0f)
        assertEquals(3f, SpeedSteps.clamp(5f), 0f)
        assertEquals(1f, SpeedSteps.clamp(Float.NaN), 0f)
        assertEquals(0.5f, SpeedSteps.snap(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(3f, SpeedSteps.snap(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1.25f, SpeedSteps.snap(1.26f), 0f)
        assertEquals(1.3f, SpeedSteps.snap(1.28f), 0f)
        for (step in 10..60) assertEquals(step / 20f, SpeedSteps.snap(step / 20f), 0f)
    }
    @Test fun stepsAndBounds() {
        assertEquals(0.5f, SpeedSteps.stepDown(0.5f), 0f)
        assertEquals(3f, SpeedSteps.stepUp(3f), 0f)
        assertEquals(1.05f, SpeedSteps.stepUp(1f), 0f)
        assertEquals(0.95f, SpeedSteps.stepDown(1f), 0f)
    }
    @Test fun formattingDoesNotDependOnLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1x", SpeedSteps.format(1f))
            assertEquals("1.25x", SpeedSteps.format(1.25f))
            assertEquals("1.2x", SpeedSteps.format(1.2f))
            assertEquals("0.5x", SpeedSteps.format(0.5f))
            assertEquals("3x", SpeedSteps.format(3f))
        } finally { Locale.setDefault(original) }
    }
}
