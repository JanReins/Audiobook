package com.janreins.audiobook

import androidx.media3.common.C
import com.janreins.audiobook.player.*
import org.junit.Assert.*
import org.junit.Test

class ChapterRulesTest {
    @Test fun chplInfersEndsAndQtKeepsEnds() {
        val starts = listOf(0L, 1000L, 2500L)
        val ends = listOf(1000L, 2500L, 4000L)
        for (known in listOf(false, true)) {
            val raw = starts.mapIndexed { i, start -> RawChapter(start, if (known) ends[i] else C.TIME_UNSET, "Title") }
            val result = ChapterRules.buildForTrack(0, raw, 4000)
            assertEquals(starts, result.map { it.startMs })
            assertEquals(ends, result.map { it.endMs })
            assertTrue(result.all { it.embedded })
        }
    }

    @Test fun sortsDeduplicatesDropsHiddenAndBeyondDurationAndFillsGap() {
        val raw = listOf(RawChapter(2500, 4000, "Last"), RawChapter(500, 1000, "First"),
            RawChapter(500, 2000, "Duplicate"), RawChapter(1000, 2500, "Middle"),
            RawChapter(1200, 1500, "Hidden", true), RawChapter(4000, 5000, "Outside"))
        val result = ChapterRules.buildForTrack(2, raw, 4000)
        assertEquals(listOf(0L, 1000L, 2500L), result.map { it.startMs })
        assertEquals(listOf("First", "Middle", "Last"), result.map { it.title })
        assertTrue(result.all { it.trackIndex == 2 })
    }

    @Test fun statedEndPastTheFileIsClampedToItsDuration() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 1000, "A"), RawChapter(1000, 9000, "B")), 4000)
        assertEquals(listOf(1000L, 4000L), result.map { it.endMs })
    }

    @Test fun invalidEndsAreInferredAndZeroLengthAtEndIsDropped() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 0, "First"),
            RawChapter(1000, 500, "Second"), RawChapter(2000, C.TIME_UNSET, "Zero")), 2000)
        assertEquals(listOf(1000L, 2000L), result.map { it.endMs })
        assertEquals(2, result.size)
    }

    @Test fun unknownDurationKeepsLastOpenAndEmptyResultFallsBack() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, C.TIME_UNSET, null),
            RawChapter(1000, C.TIME_UNSET, "Last")), 0)
        assertEquals(1000L, result.first().endMs)
        assertEquals(C.TIME_UNSET, result.last().endMs)
        val fallback = ChapterRules.buildForTrack(3, listOf(RawChapter(0, 1, "Hidden", true)), 0).single()
        assertEquals(BookChapter(3, 0, C.TIME_UNSET, "", false), fallback)
    }
}
