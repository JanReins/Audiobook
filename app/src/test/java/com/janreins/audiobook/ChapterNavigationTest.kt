package com.janreins.audiobook

import com.janreins.audiobook.player.*
import org.junit.Assert.*
import org.junit.Test

class ChapterNavigationTest {
    private val index = ChapterIndex(listOf(BookChapter(0, 0, 10000, "One", true),
        BookChapter(0, 10000, 20000, "Two", true), BookChapter(1, 0, 10000, "Three", false)), true)

    @Test fun previousUsesThreeSecondRuleAndFirstNeverGoesBackFurther() {
        assertEquals(0 to 10000L, ChapterNavigation.previousTarget(index, 0, 13001))
        assertEquals(0 to 0L, ChapterNavigation.previousTarget(index, 0, 13000))
        assertEquals(0 to 0L, ChapterNavigation.previousTarget(index, 0, 11000))
        assertEquals(0 to 0L, ChapterNavigation.previousTarget(index, 0, 2000))
        assertEquals(0 to 0L, ChapterNavigation.previousTarget(index, 0, 5000))
        assertEquals(0 to 10000L, ChapterNavigation.previousTarget(index, 1, 3000))
    }

    @Test fun nextCrossesTracksAndDoesNothingAtBookEnd() {
        assertEquals(0 to 10000L, ChapterNavigation.nextTarget(index, 0, 0, 2))
        assertEquals(1 to 0L, ChapterNavigation.nextTarget(index, 0, 10000, 2))
        assertNull(ChapterNavigation.nextTarget(index, 1, 9000, 2))
        assertEquals(2 to 0L, ChapterNavigation.nextTarget(index, 1, 9000, 3))
    }
}
