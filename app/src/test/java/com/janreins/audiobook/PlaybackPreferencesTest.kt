package com.janreins.audiobook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackPreferencesTest {
    private lateinit var context: Context
    @Before fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audiobook_player_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun defaults() {
        val prefs = PreferencesManager(context)
        assertEquals(15, prefs.getSkipBackSeconds())
        assertEquals(30, prefs.getSkipForwardSeconds())
        assertTrue(prefs.getSmartRewindEnabled())
        assertTrue(prefs.getSleepFadeOut())
    }
    @Test fun roundTripAcrossInstances() {
        val prefs = PreferencesManager(context)
        prefs.saveSkipBackSeconds(45)
        prefs.saveSkipForwardSeconds(60)
        prefs.saveSmartRewindEnabled(false)
        prefs.saveSleepFadeOut(false)
        val restored = PreferencesManager(context)
        assertEquals(45, restored.getSkipBackSeconds())
        assertEquals(60, restored.getSkipForwardSeconds())
        assertFalse(restored.getSmartRewindEnabled())
        assertFalse(restored.getSleepFadeOut())
        restored.saveSmartRewindEnabled(true)
        restored.saveSleepFadeOut(true)
        assertTrue(prefs.getSmartRewindEnabled())
        assertTrue(prefs.getSleepFadeOut())
    }
    @Test fun invalidIntervalsUseDirectionalDefaults() {
        val prefs = PreferencesManager(context)
        prefs.saveSkipBackSeconds(7)
        prefs.saveSkipForwardSeconds(7)
        assertEquals(15, prefs.getSkipBackSeconds())
        assertEquals(30, prefs.getSkipForwardSeconds())
        context.getSharedPreferences("audiobook_player_preferences", Context.MODE_PRIVATE).edit()
            .putInt("skip_back_seconds", -1).putInt("skip_forward_seconds", 999).commit()
        assertEquals(15, prefs.getSkipBackSeconds())
        assertEquals(30, prefs.getSkipForwardSeconds())
    }
}
