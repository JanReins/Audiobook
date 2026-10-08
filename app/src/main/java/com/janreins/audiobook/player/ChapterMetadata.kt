package com.janreins.audiobook.player

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter

@OptIn(UnstableApi::class)
object ChapterMetadata {
    fun map(metadata: Metadata?): List<RawChapter> = if (metadata == null) emptyList() else
        (0 until metadata.length()).mapNotNull { i ->
            (metadata[i] as? Chapter)?.let { RawChapter(it.startTimeMs, it.endTimeMs, it.title?.value, it.isHidden) }
        }
}
