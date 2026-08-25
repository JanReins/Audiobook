package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.Audiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

/**
 * Scans and loads audio files from the selected Storage Access Framework (SAF) folder.
 * Runs completely locally without any network access.
 */
class AudiobookRepository(private val context: Context) {

    // Common audiobook & audio file extensions
    private val supportedExtensions = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "wav", "wma", "opus"
    )

    /**
     * Scans the chosen directory URI and returns a clean list of audiobooks.
     */
    suspend fun loadAudiobooksFromFolder(folderUri: Uri): List<Audiobook> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Audiobook>()
        val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()

        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val files = directory.listFiles()
        for (file in files) {
            if (file.isFile && isAudioFile(file)) {
                val fileName = file.name ?: "Untitled Audio"
                val cleanTitle = cleanFileName(fileName)
                val size = file.length()
                val durationMs = extractDuration(file.uri)

                results.add(
                    Audiobook(
                        id = file.uri.toString(),
                        uri = file.uri,
                        title = cleanTitle,
                        fileName = fileName,
                        durationMs = durationMs,
                        sizeBytes = size,
                        formattedDuration = formatDuration(durationMs)
                    )
                )
            }
        }

        // Natural sort by title (e.g. Chapter 1, Chapter 2, ..., Chapter 10)
        return@withContext results.sortedWith(NaturalOrderComparator())
    }

    /**
     * Checks whether a DocumentFile is a supported audio file based on MIME type or extension.
     */
    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && mime.startsWith("audio/")) {
            return true
        }
        val name = file.name?.lowercase(Locale.ROOT) ?: return false
        val extension = name.substringAfterLast('.', "")
        return extension in supportedExtensions
    }

    /**
     * Removes the file extension and underscores to produce a clean, readable title.
     */
    private fun cleanFileName(fileName: String): String {
        val withoutExt = fileName.substringBeforeLast('.')
        // Replace multiple underscores or hyphens with a space if appropriate
        return withoutExt.replace('_', ' ').trim()
    }

    /**
     * Attempts to read the track duration using Android's MediaMetadataRetriever.
     * Fails gracefully to 0 if the file format header is damaged or unreadable.
     */
    private fun extractDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    companion object {
        /**
         * Helper to format milliseconds into HH:MM:SS or MM:SS format.
         */
        fun formatDuration(ms: Long): String {
            if (ms <= 0) return "00:00"
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Natural comparator for sorting filenames like Chapter 1, Chapter 2, Chapter 10 in logical order.
 */
class NaturalOrderComparator : Comparator<Audiobook> {
    private val pattern = Pattern.compile("(\\d+)|(\\D+)")

    override fun compare(o1: Audiobook, o2: Audiobook): Int {
        val s1 = o1.title
        val s2 = o2.title
        val matcher1 = pattern.matcher(s1)
        val matcher2 = pattern.matcher(s2)

        while (matcher1.find() && matcher2.find()) {
            val chunk1 = matcher1.group()
            val chunk2 = matcher2.group()

            val isDigit1 = chunk1.all { it.isDigit() }
            val isDigit2 = chunk2.all { it.isDigit() }

            val result = if (isDigit1 && isDigit2) {
                val num1 = chunk1.toBigIntegerOrNull()
                val num2 = chunk2.toBigIntegerOrNull()
                if (num1 != null && num2 != null) {
                    num1.compareTo(num2)
                } else {
                    chunk1.compareTo(chunk2, ignoreCase = true)
                }
            } else {
                chunk1.compareTo(chunk2, ignoreCase = true)
            }

            if (result != 0) return result
        }

        return s1.compareTo(s2, ignoreCase = true)
    }
}
