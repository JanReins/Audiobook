package com.janreins.audiobook.data.model

import android.net.Uri

data class AudioTrack(
    val uri: Uri,
    val documentId: String,
    val fileName: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long
)

data class BookProgress(val trackIndex: Int = 0, val positionMs: Long = 0L)
