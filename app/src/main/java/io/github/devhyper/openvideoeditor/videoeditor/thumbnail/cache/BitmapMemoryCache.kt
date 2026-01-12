package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache

import android.graphics.Bitmap
import android.util.LruCache
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.THUMBNAIL_MEMORY_CACHE_BYTES

class BitmapMemoryCache(
    maxSizeBytes: Int = THUMBNAIL_MEMORY_CACHE_BYTES
) {
    private val cache = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }
}
