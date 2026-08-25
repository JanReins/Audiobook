package com.janreins.audiobook.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.janreins.audiobook.data.model.Audiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

/**
 * Scans and loads audio files from the selected Storage Access Framework (SAF) folder,
 * including any sub-folders, into a clean, flat list of audiobooks.
 * Runs completely locally on-device without any internet connection.
 */
class AudiobookRepository(private val context: Context) {

    // Common audiobook & audio file extensions supported by Android's media player
    private val supportedExtensions = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "wav", "wma", "opus"
    )

    /**
     * Scans the chosen directory URI and any nested sub-folders.
     * Returns a flat, naturally sorted list of all discovered audio files.
     */
    suspend fun loadAudiobooksFromFolder(folderUri: Uri): List<Audiobook> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Audiobook>()
        val rootDirectory = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()

        if (!rootDirectory.exists() || !rootDirectory.isDirectory) {
            return@withContext emptyList()
        }

        // Recursively traverse the selected folder and its sub-folders (up to 3 levels deep)
        scanDirectoryRecursively(
            directory = rootDirectory,
            currentDepth = 0,
            maxDepth = 3,
            results = results
        )

        // Natural sort by title (e.g., Chapter 1, Chapter 2, ..., Chapter 10)
        return@withContext results.sortedWith(NaturalOrderComparator())
    }

    /**
     * Helper method to scan files in a directory and recurse into sub-folders.
     * Handles edge cases gently: empty sub-folders, permission issues, or non-audio items.
     */
    private fun scanDirectoryRecursively(
        directory: DocumentFile,
        currentDepth: Int,
        maxDepth: Int,
        results: MutableList<Audiobook>
    ) {
        val files = try {
            directory.listFiles()
        } catch (e: Exception) {
            // If listing files fails for a sub-folder, skip gracefully
            emptyArray()
        }

        for (file in files) {
            try {
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
                } else if (file.isDirectory && currentDepth < maxDepth) {
                    // Dive into the sub-folder to look for more audio files
                    scanDirectoryRecursively(
                        directory = file,
                        currentDepth = currentDepth + 1,
                        maxDepth = maxDepth,
                        results = results
                    )
                }
            } catch (e: Exception) {
                // Ignore single file or sub-folder read failures so the rest continues uninterrupted
            }
        }
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
     * Removes the file extension and underscores to produce a clean, human-readable title.
     */
    private fun cleanFileName(fileName: String): String {
        val withoutExt = fileName.substringBeforeLast('.')
        // Replace multiple underscores or hyphens with a space for readability
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
