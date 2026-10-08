package com.janreins.audiobook

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.*
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import com.janreins.audiobook.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterExtractionTest {
    @Test fun quickTimeChapters() = assertFixture("fx.m4b", Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, 0))
    @Test fun neroChaptersWithoutEnds() = assertFixture("fx_chpl_only.m4b", Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, 0))
    @Test fun id3ChaptersAndToc() = assertFixture("fx.mp3", Mp3Extractor())

    private fun assertFixture(name: String, extractor: Extractor) {
        val bytes = javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }
        val formats = mutableListOf<Format>()
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput {
                override fun format(format: Format) { if (type == C.TRACK_TYPE_AUDIO) formats += format }
                override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
                    val read = input.read(ByteArray(length.coerceAtMost(4096)), 0, length.coerceAtMost(4096))
                    if (read == -1 && !allowEndOfInput) throw java.io.EOFException()
                    return read
                }
                override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) { data.skipBytes(length) }
                override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {}
            }
            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {}
        })
        val source = ByteArrayDataSource(bytes)
        fun open(position: Long): DefaultExtractorInput {
            source.open(DataSpec.Builder().setUri(Uri.parse("memory://fixture")).setPosition(position).build())
            return DefaultExtractorInput(source, position, bytes.size.toLong())
        }
        var input = open(0)
        val seek = PositionHolder()
        try {
            var iterations = 0
            while (true) {
                check(iterations++ < 10000) { "Extractor failed to finish" }
                when (extractor.read(input, seek)) {
                    Extractor.RESULT_END_OF_INPUT -> break
                    Extractor.RESULT_SEEK -> { source.close(); input = open(seek.position) }
                }
            }
        } finally { source.close(); extractor.release() }
        val raw = formats.asReversed().map { ChapterMetadata.map(it.metadata) }.first { it.isNotEmpty() }
        val chapters = ChapterRules.buildForTrack(0, raw, 4000)
        assertEquals(listOf("Opening", "Middle Part", "Ending"), chapters.map { it.title })
        assertEquals(listOf(0L, 1000L, 2500L), chapters.map { it.startMs })
        assertEquals(listOf(1000L, 2500L, 4000L), chapters.map { it.endMs })
        if (name == "fx_chpl_only.m4b") assertTrue(raw.all { it.endMs == C.TIME_UNSET })
    }
}
