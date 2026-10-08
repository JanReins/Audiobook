package com.janreins.audiobook.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.janreins.audiobook.data.BitmapDecoding
import com.janreins.audiobook.data.CoverPlaceholders
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class CoverBitmapLoader(context: Context) : BitmapLoader {
    private val context = context.applicationContext
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/", ignoreCase = true)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit(Callable {
        BitmapDecoding.decodeDownsampled(data, 512) ?: throw IOException("Cannot decode cover")
    })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit(Callable {
        BitmapDecoding.decodeDownsampled(context, uri, 512) ?: throw IOException("Cannot decode cover: $uri")
    })

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        metadata.artworkData?.let { return decodeBitmap(it) }
        metadata.artworkUri?.let { return loadBitmap(it) }
        val title = (metadata.albumTitle ?: metadata.title ?: "?").toString()
        // Same colour as the in-app placeholder, which is keyed by book id.
        val bookId = metadata.extras?.getString(EXTRA_BOOK_ID) ?: title
        val key = "$bookId|$title"
        // The notification asks again on every state change; reuse the last placeholder for the same book.
        lastPlaceholder?.let { (cachedKey, future) -> if (cachedKey == key) return future }
        return executor.submit(Callable { CoverPlaceholders.placeholderBitmap(title, bookId, 512) })
            .also { lastPlaceholder = key to it }
    }

    private var lastPlaceholder: Pair<String, ListenableFuture<Bitmap>>? = null

    companion object {
        /** MediaMetadata extra carrying the book id, so the placeholder colour matches the app. */
        const val EXTRA_BOOK_ID = "com.janreins.audiobook.BOOK_ID"
    }

    fun release() { executor.shutdownNow() }
}
