package com.janreins.audiobook.data.model

import android.net.Uri

/**
 * Represents an audiobook audio file on the device's storage.
 *
 * @param id Unique identifier (typically the string representation of its content URI)
 * @param uri The content URI to stream audio from
 * @param title Clean, user-friendly title (file name without extension, or embedded tag)
 * @param fileName Original file name including extension
 * @param durationMs Total duration of the audio file in milliseconds (0 if unknown)
 * @param sizeBytes File size in bytes for helpful file size display
 * @param formattedDuration Human-readable duration string (e.g., "1h 24m" or "45:12")
 */
data class Audiobook(
    val id: String,
    val uri: Uri,
    val title: String,
    val fileName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val formattedDuration: String = ""
)

/**
 * A user-created bookmark marking a specific timestamp in an audiobook.
 *
 * @param id Unique identifier for the bookmark
 * @param audiobookId The ID of the book this bookmark belongs to
 * @param positionMs Position in milliseconds where the bookmark was saved
 * @param title User-provided or auto-generated name (e.g. "Chapter 3", "Interesting clue")
 * @param createdAt Epoch timestamp when this bookmark was created
 */
data class Bookmark(
    val id: String,
    val audiobookId: String,
    val positionMs: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Available sleep timer intervals.
 */
enum class SleepTimerOption(val minutes: Int, val label: String) {
    OFF(0, "Off"),
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_45(45, "45 min"),
    MIN_60(60, "60 min")
}
