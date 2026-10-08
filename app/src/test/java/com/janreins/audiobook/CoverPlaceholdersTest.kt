package com.janreins.audiobook

import com.janreins.audiobook.data.CoverPlaceholders
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CoverPlaceholdersTest {
    @Test fun initialsUseFirstTwoWordsStartingWithLettersOrDigits() {
        listOf("the hobbit" to "TH", "Dune" to "D", "1984" to "1", "" to "?",
            "   " to "?", "🎧 📖" to "?", "🎧 the hobbit returns" to "TH",
            "  Dune\nMessiah  " to "DM").forEach { (title, expected) ->
            assertEquals(title, expected, CoverPlaceholders.initials(title))
        }
    }

    @Test fun initialsUseRootLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("IS", CoverPlaceholders.initials("invisible sun"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun colorsAreStableAndInRangeEvenForMinimumHash() {
        assertEquals(8, CoverPlaceholders.PALETTE.size)
        listOf("", "book", "folder:primary:Books/Dune", "polygenelubricants").forEach { id ->
            for (size in listOf(1, 3, 8)) {
                val index = CoverPlaceholders.colorIndex(id, size)
                assertTrue(index in 0 until size)
                assertEquals(index, CoverPlaceholders.colorIndex(id, size))
            }
        }
    }
}
