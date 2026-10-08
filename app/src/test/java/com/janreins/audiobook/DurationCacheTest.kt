package com.janreins.audiobook

import com.janreins.audiobook.data.DurationCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DurationCacheTest {
    @Test fun keyIncludesDocumentSizeAndModificationTime() {
        assertEquals("primary:Books/chapter.mp3|42|123", DurationCache.key("primary:Books/chapter.mp3", 42, 123))
    }
    @Test fun jsonRoundTrip() {
        val values = mapOf("a|1|2" to 0L, "quoted\"key" to 123456789L)
        assertEquals(values, DurationCache.deserialize(DurationCache.serialize(values)))
    }
    @Test fun badJsonIsEmpty() {
        assertTrue(DurationCache.deserialize("broken").isEmpty())
        assertTrue(DurationCache.deserialize("{\"a\":\"invalid\"}").isEmpty())
    }
}
