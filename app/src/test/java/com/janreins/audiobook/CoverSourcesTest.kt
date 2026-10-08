package com.janreins.audiobook

import com.janreins.audiobook.data.CoverSources
import org.junit.Assert.*
import org.junit.Test

class CoverSourcesTest {
    @Test fun namesUsePriorityRatherThanListingOrder() {
        val priority = listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg", "folder.png")
        priority.indices.forEach { index ->
            assertEquals(priority[index], CoverSources.pickFolderCover(priority.drop(index).reversed()))
        }
    }

    @Test fun matchingIsCaseInsensitiveAndReturnsOriginalName() {
        assertEquals("CoVeR.JpEg", CoverSources.pickFolderCover(listOf("FOLDER.JPG", "CoVeR.JpEg")))
    }

    @Test fun unknownNamesAndEmptyListingsHaveNoCover() {
        assertNull(CoverSources.pickFolderCover(listOf("cover.webp", "uncover.jpg", "photo.png", "cover.jpg.bak")))
        assertNull(CoverSources.pickFolderCover(emptyList()))
    }
}
