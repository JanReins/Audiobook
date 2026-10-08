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

    @Test fun invertedEntriesAreDroppedAndOpenEndsInferred() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, C.TIME_UNSET, "First"),
            RawChapter(1000, 500, "Inverted"), RawChapter(1500, C.TIME_UNSET, "Last")), 4000)
        assertEquals(listOf("First", "Last"), result.map { it.title })
        assertEquals(listOf(1500L, 4000L), result.map { it.endMs })
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
        assertEquals(listOf(0L, 50_000L, 99_000L), result.map { it.startMs })
        assertEquals(listOf(50_000L, 99_000L, 100_000L), result.map { it.endMs })
    }

    @Test fun lastChapterIsNeverUnderOneSecondAndExtraTailChaptersAreDropped() {
        // Starts in the final second and just past the end: the first is kept at 1 s before the end,
        // the next would be under 1 s and is dropped; the last chapter can't end the book on selection.
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, C.TIME_UNSET, "Body"),
            RawChapter(99_700, C.TIME_UNSET, "Final second"), RawChapter(100_500, 103_000, "Past end")), 100_000)
        assertEquals(listOf("Body", "Final second"), result.map { it.title })
        assertEquals(listOf(0L to 99_000L, 99_000L to 100_000L), result.map { it.startMs to it.endMs })
        assertTrue(result.last().endMs - result.last().startMs >= ChapterRules.MIN_LAST_CHAPTER_MS)
        // A normal last chapter is untouched.
        val normal = ChapterRules.buildForTrack(0, listOf(RawChapter(0, C.TIME_UNSET, "A"),
            RawChapter(98_000, C.TIME_UNSET, "B")), 100_000)
        assertEquals(listOf(0L to 98_000L, 98_000L to 100_000L), normal.map { it.startMs to it.endMs })
    }

    @Test fun shiftedTailChapterRunsToTheEndOfTheFileDespiteAStatedEnd() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 9_600, "Body"),
            RawChapter(9_600, 9_700, "Tail")), 10_000)
        assertEquals(listOf(0L to 9_000L, 9_000L to 10_000L), result.map { it.startMs to it.endMs })
        assertEquals(listOf("Body", "Tail"), result.map { it.title })
    }

    @Test fun endEqualToStartIsAnUnknownEndUnlessItDuplicatesAnotherStart() {
        val result = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 0, "One"),
            RawChapter(30_000, 30_000, "Two"), RawChapter(60_000, 60_000, "Marker"),
            RawChapter(60_000, 90_000, "Three")), 120_000)
        assertEquals(listOf("One", "Two", "Three"), result.map { it.title })
        assertEquals(listOf(0L to 30_000L, 30_000L to 60_000L, 60_000L to 90_000L), result.map { it.startMs to it.endMs })
        // Last one with end == start: open, so it runs to the end of the file.
        val last = ChapterRules.buildForTrack(0, listOf(RawChapter(0, 50_000, "A"), RawChapter(50_000, 50_000, "B")), 120_000)
        assertEquals(50_000L to 120_000L, last.last().let { it.startMs to it.endMs })
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
