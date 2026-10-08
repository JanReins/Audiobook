package com.janreins.audiobook

import android.net.Uri
import com.janreins.audiobook.data.BookStatus
import com.janreins.audiobook.data.LibraryFilter
import com.janreins.audiobook.data.LibrarySort
import com.janreins.audiobook.data.LibrarySorting
import com.janreins.audiobook.data.model.Audiobook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySortingTest {
    private fun book(id: String, title: String, duration: Long, fileName: String = "$title.mp3") =
        Audiobook(id, Uri.parse("content://books/$id"), title, fileName, duration)

    private val books = listOf(
        book("10", "Book 10", 2000L),
        book("2", "Book 2", 1000L, "SPECIAL.M4B"),
        book("1", "Book 1", 3000L),
        book("3", "Book 3", 1000L)
    )
    private val statuses = mapOf(
        "10" to BookStatus(0.8f, false, 100L),
        "2" to BookStatus(0f, true, 200L),
        "1" to BookStatus(0f, false, null),
        "3" to BookStatus(0f, false, null)
    )

    private fun apply(sort: LibrarySort = LibrarySort.TITLE,
                      filter: LibraryFilter = LibraryFilter.ALL, query: String = "") =
        LibrarySorting.apply(books, { statuses.getValue(it.id) }, sort, filter, query).map { it.id }

    @Test fun titleUsesNaturalOrder() {
        assertEquals(listOf("1", "2", "3", "10"), apply())
    }

    @Test fun recentPutsNewestFirstAndNeverPlayedLastInTitleOrder() {
        assertEquals(listOf("2", "10", "1", "3"), apply(LibrarySort.RECENT))
    }

    @Test fun recentTiesUseTitleAndPlayedAtZeroPrecedesNeverPlayed() {
        val tied = books.associate { it.id to BookStatus(0f, false, if (it.id == "1") null else 0L) }
        val result = LibrarySorting.apply(books, { tied.getValue(it.id) },
            LibrarySort.RECENT, LibraryFilter.ALL, "")
        assertEquals(listOf("2", "3", "10", "1"), result.map { it.id })
    }

    @Test fun progressCountsFinishedAsOneEvenWithZeroSavedProgress() {
        assertEquals(listOf("2", "10", "1", "3"), apply(LibrarySort.PROGRESS))
    }

    @Test fun durationIsDescendingWithTitleTies() {
        assertEquals(listOf("1", "10", "2", "3"), apply(LibrarySort.DURATION))
    }

    @Test fun eachFilterUsesFinishedFlagAndProgress() {
        assertEquals(listOf("1", "2", "3", "10"), apply(filter = LibraryFilter.ALL))
        assertEquals(listOf("10"), apply(filter = LibraryFilter.IN_PROGRESS))
        assertEquals(listOf("1", "3"), apply(filter = LibraryFilter.NOT_STARTED))
        assertEquals(listOf("2"), apply(filter = LibraryFilter.FINISHED))
    }

    @Test fun fullProgressWithoutFinishedFlagStillCountsAsInProgress() {
        val result = LibrarySorting.apply(books, { BookStatus(1f, false, null) },
            LibrarySort.PROGRESS, LibraryFilter.IN_PROGRESS, "")
        assertEquals(4, result.size)
        assertEquals(emptyList<Audiobook>(), LibrarySorting.apply(books,
            { BookStatus(1f, false, null) }, LibrarySort.TITLE, LibraryFilter.FINISHED, ""))
    }

    @Test fun queryAndFilterMatchTitleOrFilenameIgnoringCase() {
        assertEquals(listOf("2"), apply(filter = LibraryFilter.FINISHED, query = "special"))
        assertEquals(emptyList<String>(), apply(filter = LibraryFilter.NOT_STARTED, query = "special"))
        assertEquals(listOf("10"), apply(filter = LibraryFilter.IN_PROGRESS, query = "bOoK"))
        assertEquals(listOf("1", "3"), apply(filter = LibraryFilter.NOT_STARTED, query = "  "))
        assertEquals(emptyList<String>(), apply(query = "missing"))
    }

    @Test fun emptyLibraryIsSupported() {
        assertEquals(emptyList<Audiobook>(), LibrarySorting.apply(emptyList(),
            { error("No status should be read") }, LibrarySort.TITLE, LibraryFilter.ALL, ""))
    }
}
