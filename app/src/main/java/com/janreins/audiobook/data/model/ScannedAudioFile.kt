package com.janreins.audiobook.data.model

import android.net.Uri

data class ScannedAudioFile(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val parentDocumentId: String?,
    val parentName: String?,
    val isInRoot: Boolean,
    val durationMs: Long
)
