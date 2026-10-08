package com.janreins.audiobook.data

import com.janreins.audiobook.data.model.AudioTrack
import com.janreins.audiobook.data.model.Audiobook
import com.janreins.audiobook.data.model.ScannedAudioFile

object BookGrouping {
    fun group(files: List<ScannedAudioFile>): List<Audiobook> = files.groupBy {
        if (it.isInRoot) "file:${it.documentId}" else "folder:${it.parentDocumentId}"
    }.map { (id, members) ->
        val sorted = members.sortedWith(compareBy(NaturalStringComparator) { it.name })
        val first = sorted.first()
        val tracks = sorted.map {
            AudioTrack(it.uri, it.documentId, it.name, cleanFileName(it.name), it.durationMs, it.sizeBytes)
        }
        val duration = tracks.sumOf { it.durationMs }
        Audiobook(id, first.uri,
            if (first.isInRoot) cleanFileName(first.name) else cleanTitle(first.parentName.orEmpty()),
            if (tracks.size == 1) first.name else "${tracks.size} files",
            duration, tracks.sumOf { it.sizeBytes }, AudiobookRepository.formatDuration(duration), tracks)
    }.sortedWith(compareBy(NaturalStringComparator) { it.title })

    private fun cleanTitle(name: String) = name.replace('_', ' ').trim()
    private fun cleanFileName(name: String) = cleanTitle(name.substringBeforeLast('.', name))
}

object NaturalStringComparator : Comparator<String> {
    private val chunks = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val left = chunks.findAll(a).map { it.value }.toList()
        val right = chunks.findAll(b).map { it.value }.toList()
        for ((x, y) in left.zip(right)) {
            val result = if (x.all { it.isDigit() } && y.all { it.isDigit() })
                x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y, ignoreCase = true)
            if (result != 0) return result
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b, ignoreCase = true)
    }
}
