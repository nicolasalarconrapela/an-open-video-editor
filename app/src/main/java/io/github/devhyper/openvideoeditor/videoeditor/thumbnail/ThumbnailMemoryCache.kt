package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

object ThumbnailMemoryCache {
    private val cache = LruCache<ThumbnailKey, ImageBitmap>(128)

    fun get(key: ThumbnailKey): ImageBitmap? = cache.get(key)

    fun put(key: ThumbnailKey, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}
