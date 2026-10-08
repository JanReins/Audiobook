package com.janreins.audiobook.player

import android.view.KeyEvent

/**
 * Hardware next/previous keys (Bluetooth headsets, car stereos) on a single-file book. Media3 would
 * otherwise restart the file on "previous" and ignore "next", so they become the configured skips.
 * Multi-file books keep normal track navigation.
 */
object MediaKeySkips {
    /** -1 for skip back, +1 for skip forward, or null to let Media3 handle the key. */
    fun direction(keyCode: Int, mediaItemCount: Int): Int? {
        if (mediaItemCount != 1) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> -1
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> 1
            else -> null
        }
    }
}
