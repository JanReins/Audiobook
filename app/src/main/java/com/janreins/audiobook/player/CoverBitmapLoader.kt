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
        BitmapDecoding.decodeDownsampled(data, MAX_PX) ?: throw IOException("Cannot decode cover")
    })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit(Callable {
        BitmapDecoding.decodeDownsampled(context, uri, MAX_PX) ?: throw IOException("Cannot decode cover: $uri")
    })

    /**
     * The notification asks again on every player change, so the last result is reused. The key follows
     * every input (embedded bytes, folder URI, placeholder id), and embedded bytes win over the folder URI,
     * so when embedded art arrives after a URI-only request it replaces the folder cover. (Media3's
     * CacheBitmapLoader would reuse the folder bitmap there because the URI still matches.)
     */
    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        val data = metadata.artworkData
        val uri = metadata.artworkUri
        val title = (metadata.albumTitle ?: metadata.title ?: "?").toString()
        // Same colour as the in-app placeholder, which is keyed by book id.
        val bookId = metadata.extras?.getString(EXTRA_BOOK_ID) ?: title
        // Every input of the fallback chain is in the key, so any change (e.g. embedded bytes arriving) misses.
        val key = "${data?.let { "${it.size}:${it.contentHashCode()}" }}|$uri|$bookId|$title"
        last?.let { (cachedKey, future) -> if (cachedKey == key) return future }
        val future = executor.submit(Callable {
            // Fall back down the chain, so a broken embedded image or folder file still shows something.
            data?.let { BitmapDecoding.decodeDownsampled(it, MAX_PX) }
                ?: uri?.let { runCatching { BitmapDecoding.decodeDownsampled(context, it, MAX_PX) }.getOrNull() }
                ?: CoverPlaceholders.placeholderBitmap(title, bookId, MAX_PX)
        })
        last = key to future
        return future
    }

    /** Only touched on the session's application thread. */
    private var last: Pair<String, ListenableFuture<Bitmap>>? = null

    companion object {
        /** MediaMetadata extra carrying the book id, so the placeholder colour matches the app. */
        const val EXTRA_BOOK_ID = "com.janreins.audiobook.BOOK_ID"
        private const val MAX_PX = 512
    }

    fun release() { executor.shutdownNow() }
}
