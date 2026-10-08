package com.janreins.audiobook

import android.content.Context
import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.ChapterRepository
import com.janreins.audiobook.player.RawChapter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterCacheTest {
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "chapters").deleteRecursively()
        ChapterRepository.init(context)
    }
    @Test fun roundTripIncludingEmptyListsAndUnknownEnds() {
        val key = ChapterRepository.key("doc", 100, 1)
        val raw = listOf(RawChapter(0, 1000, "Opening"), RawChapter(1000, C.TIME_UNSET, null, true))
        ChapterRepository.write(key, raw)
        assertEquals(raw, ChapterRepository.read(key))
        ChapterRepository.write(key, emptyList())
        assertEquals(emptyList<RawChapter>(), ChapterRepository.read(key))
    }
    @Test fun modificationInvalidatesAndPruneRemovesUnseen() {
        val old = ChapterRepository.key("doc", 100, 1)
        val updated = ChapterRepository.key("doc", 100, 2)
        ChapterRepository.write(old, emptyList())
        assertNull(ChapterRepository.read(updated))
        assertNull(ChapterRepository.read(ChapterRepository.key("doc", 101, 1)))
        ChapterRepository.write(updated, listOf(RawChapter(0, 1000, "New")))
        ChapterRepository.prune(setOf(updated))
        assertNull(ChapterRepository.read(old))
        assertNotNull(ChapterRepository.read(updated))
    }
}
