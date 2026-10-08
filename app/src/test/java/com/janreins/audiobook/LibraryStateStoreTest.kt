package com.janreins.audiobook

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.LibraryFilter
import com.janreins.audiobook.data.LibrarySort
import com.janreins.audiobook.data.LibraryStateStore
import com.janreins.audiobook.data.PreferencesManager
import com.janreins.audiobook.data.model.BookProgress
import com.janreins.audiobook.data.model.Bookmark
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryStateStoreTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var store: LibraryStateStore

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("audiobook_player_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = LibraryStateStore(context)
    }

    @Test fun finishedFlagSetsClearsAndPersistsPerBook() {
        assertFalse(store.isFinished("book"))
        store.setFinished("book", true)
        assertTrue(LibraryStateStore(context).isFinished("book"))
        assertFalse(store.isFinished("other"))
        store.setFinished("book", false)
        assertFalse(LibraryStateStore(context).isFinished("book"))
    }

    @Test fun lastPlayedTimestampIsNullableAndPersists() {
        assertNull(store.getLastPlayedAt("book"))
        store.setLastPlayedAt("book", 123456L)
        assertEquals(123456L, LibraryStateStore(context).getLastPlayedAt("book"))
        store.setLastPlayedAt("book", 0L)
        assertEquals(0L, store.getLastPlayedAt("book"))
        assertNull(store.getLastPlayedAt("other"))
    }

    @Test fun sortAndFilterDefaultAndPersistEveryOption() {
        assertEquals(LibrarySort.TITLE, store.getSort())
        assertEquals(LibraryFilter.ALL, store.getFilter())
        LibrarySort.entries.forEach {
            store.setSort(it)
            assertEquals(it, LibraryStateStore(context).getSort())
        }
        LibraryFilter.entries.forEach {
            store.setFilter(it)
            assertEquals(it, LibraryStateStore(context).getFilter())
        }
    }

    @Test fun unknownOrMissingStoredChoicesFallBackToDefaults() {
        prefs.edit().putString("library_sort", "FUTURE_SORT")
            .putString("library_filter", "FUTURE_FILTER").commit()
        assertEquals(LibrarySort.TITLE, store.getSort())
        assertEquals(LibraryFilter.ALL, store.getFilter())
        prefs.edit().remove("library_sort").remove("library_filter").commit()
        assertEquals(LibrarySort.TITLE, store.getSort())
        assertEquals(LibraryFilter.ALL, store.getFilter())
    }

    @Test fun libraryStateDoesNotAlterExistingProgressOrBookmarks() {
        val existing = PreferencesManager(context)
        val progress = BookProgress(2, 123L)
        val bookmark = Bookmark("bookmark", "book", 456L, "Note", trackIndex = 1)
        existing.saveBookProgress("book", progress)
        existing.addBookmark(bookmark)
        store.setFinished("book", true)
        store.setLastPlayedAt("book", 789L)
        store.setSort(LibrarySort.RECENT)
        store.setFilter(LibraryFilter.FINISHED)
        store.setFinished("book", false)
        assertEquals(progress, existing.getBookProgress("book"))
        assertEquals(listOf(bookmark), existing.getBookmarks("book"))
    }

    @Test fun revisionsAreSharedAcrossPlayerAndViewModelStoreInstances() {
        val other = LibraryStateStore(context)
        val before = store.libraryVersion.value
        other.setFinished("book", true)
        assertTrue(store.libraryVersion.value > before)
        val afterFinished = store.libraryVersion.value
        other.notifyProgressChanged()
        assertTrue(store.libraryVersion.value > afterFinished)
    }
}
