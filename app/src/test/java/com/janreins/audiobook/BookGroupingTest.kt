package com.janreins.audiobook

import android.net.Uri
import com.janreins.audiobook.data.BookGrouping
import com.janreins.audiobook.data.model.ScannedAudioFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookGroupingTest {
    private fun file(id: String, name: String, parent: String? = null, parentName: String? = null) =
        ScannedAudioFile(Uri.parse("content://books/$id"), id, name, 50, 1,
            parent, parentName, parent == null, 1000)

    @Test fun rootFilesAreSeparateBooksSortedNaturally() {
        val books = BookGrouping.group(listOf(file("10", "Book_10.mp3"), file("2", "Book_2.mp3")))
        assertEquals(listOf("file:2", "file:10"), books.map { it.id })
        assertEquals("Book 2", books.first().title)
        assertTrue(books.all { it.trackCount == 1 })
    }

    @Test fun folderTracksUseNaturalOrderAndSumDurationAndSize() {
        val book = BookGrouping.group(listOf(
            file("10", "Chapter 10.mp3", "folder", " My_Book "),
            file("2", "Chapter 2.mp3", "folder", " My_Book ")
        )).single()
        assertEquals("folder:folder", book.id)
        assertEquals("My Book", book.title)
        assertEquals(listOf("2", "10"), book.tracks.map { it.documentId })
        assertEquals(book.tracks.first().uri, book.uri)
        assertEquals("2 files", book.fileName)
        assertEquals(2000L, book.durationMs)
        assertEquals(100L, book.sizeBytes)
        assertEquals("00:02", book.formattedDuration)
    }

    @Test fun singleFileSubfolderRemainsFolderBookAndBooksAreSorted() {
        val books = BookGrouping.group(listOf(file("x", "Chapter.mp3", "f", "Book 10"),
            file("y", "Chapter.mp3", "g", "Book 2")))
        assertEquals(listOf("folder:g", "folder:f"), books.map { it.id })
        assertTrue(books.all { it.tracks.size == 1 })
        assertTrue(BookGrouping.group(emptyList()).isEmpty())
    }

    @Test fun coversAndTrackTimestampsPropagateWithoutCoveringRootFiles() {
        val uri = Uri.parse("content://books/cover")
        val root = file("root", "Loose.mp3").copy(folderCoverUri = uri, folderCoverKey = "root-cover")
        val later = file("10", "Chapter 10.mp3", "f", "Book").copy(
            lastModified = 123, folderCoverUri = uri, folderCoverKey = "cover|50|123")
        val first = file("2", "Chapter 2.mp3", "f", "Book").copy(lastModified = 456)
        val books = BookGrouping.group(listOf(root, later, first))
        val folder = books.single { it.id == "folder:f" }
        assertEquals(uri, folder.coverUri)
        assertEquals("cover|50|123", folder.coverKey)
        assertEquals(listOf(456L, 123L), folder.tracks.map { it.lastModified })
        assertNull(books.single { it.id == "file:root" }.coverUri)
        assertNull(books.single { it.id == "file:root" }.coverKey)
    }

}
