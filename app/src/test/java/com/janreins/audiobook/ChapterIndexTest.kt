package com.janreins.audiobook

import androidx.media3.common.C
import com.janreins.audiobook.player.*
import org.junit.Assert.*
import org.junit.Test

class ChapterIndexTest {
    @Test fun mixesEmbeddedWithFilesAndNumbersBlankTitlesAcrossBook() {
        val index = ChapterIndex.build(listOf(null to 1000L,
            listOf(RawChapter(0, C.TIME_UNSET, " "), RawChapter(1000, C.TIME_UNSET, "Ending")) to 2000L,
            emptyList<RawChapter>() to 0L), listOf("File one", "File two", "File three"))
        assertEquals(listOf("File one", "Chapter 2", "Ending", "File three"), index.chapters.map { it.title })
        assertEquals(listOf(false, true, true, false), index.chapters.map { it.embedded })
        assertTrue(index.hasEmbedded)
        assertTrue(index.navigable)
        assertEquals(C.TIME_UNSET, index.chapters.last().endMs)
    }

    @Test fun binarySearchUsesStartsWithinEachTrackIncludingUnknownEnds() {
        val index = ChapterIndex(listOf(BookChapter(0, 100, 200, "A", true),
            BookChapter(0, 300, C.TIME_UNSET, "B", true), BookChapter(1, 50, C.TIME_UNSET, "C", true),
            BookChapter(1, 500, C.TIME_UNSET, "D", true)), true)
        assertEquals(0, index.indexAt(0, -100))
        assertEquals(0, index.indexAt(0, 299)) // Gaps still belong to preceding chapter.
        assertEquals(1, index.indexAt(0, 300))
        assertEquals(1, index.indexAt(0, Long.MAX_VALUE))
        assertEquals(2, index.indexAt(1, 0))
        assertEquals(2, index.indexAt(1, 499))
        assertEquals(3, index.indexAt(1, 500))
        assertEquals(3, index.indexAt(1, Long.MAX_VALUE))
        assertEquals(0, index.indexAt(-1, 0))
        assertEquals(3, index.indexAt(100, 0))
        assertEquals(0, ChapterIndex(emptyList(), false).indexAt(10, 100))
    }

    @Test fun filesWorkImmediatelyAndSingleFileIsNotNavigable() {
        val files = ChapterIndex.build(listOf(null to 1000L, null to 2000L), listOf("One", "Two"))
        assertTrue(files.navigable)
        assertFalse(files.hasEmbedded)
        assertEquals(1, files.indexAt(1, 0))
        assertFalse(ChapterIndex.build(listOf(null to 0L), listOf("One")).navigable)
    }
}
