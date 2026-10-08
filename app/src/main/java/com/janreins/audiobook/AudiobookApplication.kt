package com.janreins.audiobook

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.janreins.audiobook.data.CoverStore
import com.janreins.audiobook.ui.components.CoverFetcher
import com.janreins.audiobook.ui.components.CoverKeyer

class AudiobookApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(CoverKeyer())
            add(CoverFetcher.Factory(CoverStore(context)))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .crossfade(true)
        .build()
}
