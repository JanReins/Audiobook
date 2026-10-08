package com.janreins.audiobook.data

import com.janreins.audiobook.data.model.Audiobook

enum class LibrarySort { TITLE, RECENT, PROGRESS, DURATION }
enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED }

data class BookStatus(val progressFraction: Float, val finished: Boolean, val lastPlayedAt: Long?)

object LibrarySorting {
    fun apply(
        books: List<Audiobook>,
        statusOf: (Audiobook) -> BookStatus,
        sort: LibrarySort,
        filter: LibraryFilter,
        query: String
    ): List<Audiobook> {
        val titleOrder = compareBy<Audiobook, String>(NaturalStringComparator) { it.title }
        val statuses = books.associate { it.id to statusOf(it) }
        val order = when (sort) {
            LibrarySort.TITLE -> titleOrder
            LibrarySort.RECENT -> compareByDescending<Audiobook> { statuses.getValue(it.id).lastPlayedAt != null }
                .thenByDescending { statuses.getValue(it.id).lastPlayedAt }.then(titleOrder)
            LibrarySort.PROGRESS -> compareByDescending<Audiobook> {
                val status = statuses.getValue(it.id)
                if (status.finished) 1f else status.progressFraction
            }.then(titleOrder)
            LibrarySort.DURATION -> compareByDescending<Audiobook> { it.durationMs }.then(titleOrder)
        }
        return books.filter { book ->
            val status = statuses.getValue(book.id)
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.IN_PROGRESS -> !status.finished && status.progressFraction > 0f
                LibraryFilter.NOT_STARTED -> !status.finished && status.progressFraction == 0f
                LibraryFilter.FINISHED -> status.finished
            }
            matchesFilter && (query.isBlank() || book.title.contains(query, ignoreCase = true) ||
                book.fileName.contains(query, ignoreCase = true))
        }.sortedWith(order)
    }
}
