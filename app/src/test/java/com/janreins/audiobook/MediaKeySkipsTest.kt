package com.janreins.audiobook

import android.view.KeyEvent
import com.janreins.audiobook.player.MediaKeySkips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaKeySkipsTest {
    @Test fun singleFileBookMapsNextAndPreviousToSkips() {
        assertEquals(-1, MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 1))
        assertEquals(-1, MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, 1))
        assertEquals(1, MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_NEXT, 1))
        assertEquals(1, MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, 1))
    }

    @Test fun multiFileBookKeepsTrackNavigation() {
        assertNull(MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_NEXT, 3))
        assertNull(MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 3))
        assertNull(MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_NEXT, 0))
    }

    @Test fun otherKeysAreLeftToMedia3() {
        assertNull(MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1))
        assertNull(MediaKeySkips.direction(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 1))
    }
}
