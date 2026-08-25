package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudiobookRepository
import com.example.data.PreferencesManager
import com.example.data.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Audiobook Player", appName)
  }

  @Test
  fun `format duration helper works correctly`() {
    assertEquals("00:00", AudiobookRepository.formatDuration(0L))
    assertEquals("00:15", AudiobookRepository.formatDuration(15_000L))
    assertEquals("01:30", AudiobookRepository.formatDuration(90_000L))
    assertEquals("1:02:15", AudiobookRepository.formatDuration(3735_000L))
  }

  @Test
  fun `preferences manager saves and restores bookmarks and position`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)

    prefs.savePlaybackPosition("book_123", 45000L)
    assertEquals(45000L, prefs.getPlaybackPosition("book_123"))

    val bookmark = Bookmark(
      id = "bm_1",
      audiobookId = "book_123",
      positionMs = 30000L,
      title = "Chapter 2 Intro"
    )
    prefs.addBookmark(bookmark)

    val savedBookmarks = prefs.getBookmarks("book_123")
    assertEquals(1, savedBookmarks.size)
    assertEquals("Chapter 2 Intro", savedBookmarks[0].title)
    assertEquals(30000L, savedBookmarks[0].positionMs)

    prefs.deleteBookmark("book_123", "bm_1")
    assertTrue(prefs.getBookmarks("book_123").isEmpty())
  }
}
