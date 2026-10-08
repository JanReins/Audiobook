package com.janreins.audiobook.player

import androidx.media3.common.C

data class BookChapter(val trackIndex: Int, val startMs: Long, val endMs: Long, val title: String, val embedded: Boolean)
data class RawChapter(val startMs: Long, val endMs: Long, val title: String?, val hidden: Boolean = false)

object ChapterRules {
    fun buildForTrack(trackIndex: Int, raw: List<RawChapter>, trackDurationMs: Long): List<BookChapter> {
        val sorted = raw.filter { !it.hidden }.sortedBy { it.startMs }.distinctBy { it.startMs }
        val chapters = sorted.mapIndexedNotNull { i, chapter ->
            val inferred = if (chapter.endMs != C.TIME_UNSET && chapter.endMs > chapter.startMs) chapter.endMs
                else sorted.getOrNull(i + 1)?.startMs ?: trackDurationMs.takeIf { it > 0 } ?: C.TIME_UNSET
            // Never run past the file: a stated end beyond the duration is clamped to it.
            val end = if (inferred != C.TIME_UNSET && trackDurationMs > 0) minOf(inferred, trackDurationMs) else inferred
            if ((trackDurationMs > 0 && chapter.startMs >= trackDurationMs) ||
                (end != C.TIME_UNSET && end <= chapter.startMs)) null
            else BookChapter(trackIndex, chapter.startMs, end, chapter.title.orEmpty(), true)
        }
        if (chapters.isEmpty()) return listOf(BookChapter(trackIndex, 0, trackDurationMs.takeIf { it > 0 } ?: C.TIME_UNSET, "", false))
        return chapters.mapIndexed { i, chapter -> if (i == 0 && chapter.startMs > 0) chapter.copy(startMs = 0) else chapter }
    }
}

data class ChapterIndex(val chapters: List<BookChapter>, val hasEmbedded: Boolean) {
    val navigable: Boolean get() = chapters.size >= 2

    fun indexAt(trackIndex: Int, positionMs: Long): Int {
        if (chapters.isEmpty()) return 0
        // Upper bound on (track, start): end times may be unknown or have gaps.
        var low = 0
        var high = chapters.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val chapter = chapters[mid]
            if (chapter.trackIndex < trackIndex || (chapter.trackIndex == trackIndex && chapter.startMs <= positionMs)) low = mid + 1
            else high = mid
        }
        val candidate = (low - 1).coerceAtLeast(0)
        return if (chapters[candidate].trackIndex == trackIndex) candidate
            else low.coerceAtMost(chapters.lastIndex)
    }

    companion object {
        fun build(tracks: List<Pair<List<RawChapter>?, Long>>, trackTitles: List<String>): ChapterIndex {
            val chapters = tracks.flatMapIndexed { trackIndex, (raw, duration) ->
                ChapterRules.buildForTrack(trackIndex, raw.orEmpty(), duration).map {
                    if (!it.embedded) it.copy(title = trackTitles.getOrNull(trackIndex).orEmpty()) else it
                }
            }.mapIndexed { i, chapter ->
                if (chapter.title.isBlank()) chapter.copy(title = "Chapter ${i + 1}") else chapter
            }
            return ChapterIndex(chapters, chapters.any { it.embedded })
        }
    }
}

object ChapterNavigation {
    fun previousTarget(index: ChapterIndex, trackIndex: Int, positionMs: Long): Pair<Int, Long> {
        val i = index.indexAt(trackIndex, positionMs)
        val current = index.chapters.getOrNull(i) ?: return trackIndex to 0L
        val target = if (positionMs - current.startMs > 3000) current else index.chapters[(i - 1).coerceAtLeast(0)]
        return target.trackIndex to target.startMs
    }

    fun nextTarget(index: ChapterIndex, trackIndex: Int, positionMs: Long, mediaItemCount: Int): Pair<Int, Long>? {
        val next = index.chapters.getOrNull(index.indexAt(trackIndex, positionMs) + 1)
        return next?.let { it.trackIndex to it.startMs }
            ?: if (trackIndex < mediaItemCount - 1) trackIndex + 1 to 0L else null
    }
}
