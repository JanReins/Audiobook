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
            RawChapter(1200, 1500, "Hidden", true), RawChapter(6000, 7000, "Outside"))
        val result = ChapterRules.buildForTrack(2, raw, 4000)
        assertEquals(listOf(0L, 1000L, 2500L), result.map { it.startMs })
        assertEquals(listOf("First", "Middle", "Last"), result.map { it.title })
        assertTrue(result.all { it.trackIndex == 2 })
    }

    @Test fun statedEndPastTheFileIsClampedToItsDuration() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 1000, "A"), RawChapter(1000, 9000, "B")), 4000)
        assertEquals(listOf(1000L, 4000L), result.map { it.endMs })
    }

    @Test fun zeroLengthAndInvertedEntriesAreDroppedAndOpenEndsInferred() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, C.TIME_UNSET, "First"),
            RawChapter(500, 500, "Zero"), RawChapter(1000, 500, "Inverted"), RawChapter(1500, C.TIME_UNSET, "Last")), 2000)
        assertEquals(listOf("First", "Last"), result.map { it.title })
        assertEquals(listOf(1500L, 2000L), result.map { it.endMs })
    }

    @Test fun zeroLengthMarkerDoesNotShadowRealChapterWithSameStart() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 0, "junk-marker"),
            RawChapter(0, 60_000, "Real Chapter 1"), RawChapter(60_000, 120_000, "Chapter 2")), 120_000)
        assertEquals(listOf("Real Chapter 1", "Chapter 2"), result.map { it.title })
        assertEquals(listOf(0L to 60_000L, 60_000L to 120_000L), result.map { it.startMs to it.endMs })
    }

    @Test fun unknownOrNegativeStartsAreClampedToZero() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(C.TIME_UNSET, 10_000, "A"),
            RawChapter(10_000, 20_000, "B")), 20_000)
        assertEquals(listOf(0L, 10_000L), result.map { it.startMs })
        assertEquals("A", result.first().title)
        val negative = ChapterRules.buildForTrack(0, listOf(RawChapter(-500, C.TIME_UNSET, "N"),
            RawChapter(1000, C.TIME_UNSET, "M")), 2000)
        assertEquals(listOf(0L to 1000L, 1000L to 2000L), negative.map { it.startMs to it.endMs })
    }

    @Test fun lastChapterJustPastAnUnderestimatedDurationIsKeptAndClamped() {
        // VBR MP3: scanned duration 100 s, real file a bit longer; ID3 says the last chapter starts at 101 s.
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 50_000, "One"),
            RawChapter(50_000, 101_000, "Two"), RawChapter(101_000, 130_000, "Three"),
            RawChapter(100_000 + ChapterRules.END_TOLERANCE_MS, 140_000, "Beyond")), 100_000)
        assertEquals(listOf("One", "Two", "Three"), result.map { it.title })
        assertEquals(listOf(0L, 50_000L, 99_999L), result.map { it.startMs })
        assertEquals(listOf(50_000L, 100_000L, 100_000L), result.map { it.endMs })
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
