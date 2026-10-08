package com.janreins.audiobook

import android.net.Uri
import com.janreins.audiobook.data.BookGrouping
import com.janreins.audiobook.data.PreferencesManager
import com.janreins.audiobook.data.ProgressMigration
import com.janreins.audiobook.data.model.BookProgress
import com.janreins.audiobook.data.model.Bookmark
import com.janreins.audiobook.data.model.ScannedAudioFile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgressMigrationTest {
    private lateinit var prefs: PreferencesManager
    private val book get() = BookGrouping.group((0..2).map {
        ScannedAudioFile(Uri.parse("content://old/$it"), "$it", "Chapter $it.mp3", 1, 1,
            "folder", "Book", false, 1000)
    }).single()

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("audiobook_player_preferences", 0).edit().clear().commit()
        prefs = PreferencesManager(context)
    }
    private fun migrate() = ProgressMigration.migrate(book, prefs::hasBookProgress,
        prefs::getPlaybackPosition, prefs::saveBookProgress, prefs::getBookmarks, prefs::saveBookmarks,
        prefs::isMigrated, prefs::markMigrated, prefs::getLastPlayedBookId, prefs::saveLastPlayedBookId)

    @Test fun highestLegacyTrackWinsAndLegacyDataRemains() {
        prefs.savePlaybackPosition("content://old/0", 800)
        prefs.savePlaybackPosition("content://old/1", 100)
        prefs.saveLastPlayedBookId("content://old/1")
        migrate()
        assertEquals(BookProgress(1, 100), prefs.getBookProgress(book.id))
        assertEquals(800L, prefs.getPlaybackPosition("content://old/0"))
        assertEquals(book.id, prefs.getLastPlayedBookId())
    }
    @Test fun existingZeroLegacyPositionCountsAsProgress() {
        prefs.savePlaybackPosition("content://old/2", 0)
        migrate()
        assertEquals(BookProgress(2, 0), prefs.getBookProgress(book.id))
    }
    @Test fun newProgressIsPreserved() {
        prefs.saveBookProgress(book.id, BookProgress(0, 0))
        prefs.savePlaybackPosition("content://old/2", 900)
        migrate()
        assertEquals(BookProgress(), prefs.getBookProgress(book.id))
    }
    @Test fun bookmarksMergeKeepIdsAndMigrationRunsOnce() {
        prefs.addBookmark(Bookmark("new", book.id, 10, "New"))
        prefs.addBookmark(Bookmark("old", "content://old/2", 30, "Old"))
        migrate()
        val migrated = prefs.getBookmarks(book.id).single { it.id == "old" }
        assertEquals(2, migrated.trackIndex)
        assertEquals(book.id, migrated.audiobookId)
        assertEquals(2, prefs.getBookmarks(book.id).size)
        prefs.savePlaybackPosition("content://old/2", 999)
        prefs.addBookmark(Bookmark("late", "content://old/1", 20, "Late"))
        migrate()
        assertFalse(prefs.getBookmarks(book.id).any { it.id == "late" })
        assertEquals(BookProgress(), prefs.getBookProgress(book.id))
        assertTrue(prefs.isMigrated(book.id))
    }
    @Test fun bookmarkDefaultAndTrackIndexRoundTrip() {
        assertEquals(0, Bookmark("a", book.id, 0, "Default").trackIndex)
        prefs.addBookmark(Bookmark("b", book.id, 100, "Track", trackIndex = 2))
        assertEquals(2, prefs.getBookmarks(book.id).single().trackIndex)
        RuntimeEnvironment.getApplication().getSharedPreferences("audiobook_player_preferences", 0)
            .edit().putString("bookmarks_${book.id}", """[{"id":"legacy","positionMs":1}]""").commit()
        assertEquals(0, prefs.getBookmarks(book.id).single().trackIndex)
    }
}
