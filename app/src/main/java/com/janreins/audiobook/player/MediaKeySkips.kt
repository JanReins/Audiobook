package com.janreins.audiobook.player

import android.view.KeyEvent

/** Headset navigation prefers chapters, then files, then configured single-file skips. */
object MediaKeySkips {
    /** -1 for skip back, +1 for skip forward, or null to let Media3 handle the key. */
    fun direction(keyCode: Int, mediaItemCount: Int, chapterCount: Int = 1): Int? {
        if (chapterCount >= 2 || mediaItemCount != 1) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> -1
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> 1
            else -> null
        }
    }
}
