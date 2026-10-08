package com.janreins.audiobook.data

import com.janreins.audiobook.data.model.Audiobook
import com.janreins.audiobook.data.model.BookProgress
import com.janreins.audiobook.data.model.Bookmark

object ProgressMigration {
    fun migrate(
        book: Audiobook,
        hasProgress: (String) -> Boolean,
        position: (String) -> Long,
        saveProgress: (String, BookProgress) -> Unit,
        bookmarks: (String) -> List<Bookmark>,
        saveBookmarks: (String, List<Bookmark>) -> Unit,
        isMigrated: (String) -> Boolean,
        markMigrated: (String) -> Unit,
        lastPlayed: () -> String?,
        saveLastPlayed: (String?) -> Unit
    ) {
        if (isMigrated(book.id)) return
        val legacyIds = book.tracks.map { it.uri.toString() }
        if (!hasProgress(book.id)) {
            val index = legacyIds.indexOfLast { hasProgress(it) || position(it) > 0 }
            if (index >= 0) saveProgress(book.id, BookProgress(index, position(legacyIds[index])))
        }
        val merged = bookmarks(book.id) + legacyIds.flatMapIndexed { index, id ->
            bookmarks(id).map { it.copy(audiobookId = book.id, trackIndex = index) }
        }
        if (merged.isNotEmpty()) saveBookmarks(book.id, merged.distinctBy { it.id })
        if (lastPlayed() in legacyIds) saveLastPlayed(book.id)
        markMigrated(book.id)
    }
}
