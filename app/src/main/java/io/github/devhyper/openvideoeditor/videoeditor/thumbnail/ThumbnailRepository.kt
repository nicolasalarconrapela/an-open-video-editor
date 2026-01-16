package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.graphics.Bitmap
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache.BitmapMemoryCache
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache.DiskThumbnailCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ThumbnailRepository(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val memoryCache: BitmapMemoryCache,
    private val diskCache: DiskThumbnailCache?,
    private val decode: suspend (ThumbnailKey) -> Bitmap?
) {
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    fun peek(key: ThumbnailKey): Bitmap? = memoryCache.get(key.keyString())

    suspend fun getOrRequest(
        key: ThumbnailKey,
        storeInMemory: Boolean = true,
        storeOnDisk: Boolean = true,
        returnBitmap: Boolean = true
    ): Bitmap? {
        val keyString = key.keyString()
        android.util.Log.d("ThumbnailRepo", "Requesting key: $keyString")
        
        memoryCache.get(keyString)?.let { 
            android.util.Log.d("ThumbnailRepo", "Memory cache HIT for: $keyString")
            return it 
        }
        
        val diskBitmap = withContext(dispatcher) { diskCache?.get(keyString) }
        if (diskBitmap != null) {
            android.util.Log.d("ThumbnailRepo", "Disk cache HIT for: $keyString")
            if (storeInMemory) {
                memoryCache.put(keyString, diskBitmap)
            }
            return if (returnBitmap) diskBitmap else null
        }
        
        android.util.Log.d("ThumbnailRepo", "Decoding new thumbnail for: $keyString")
        val deferred = inFlight.computeIfAbsent(keyString) {
            scope.async(dispatcher) {
                val decoded = decode(key)
                ensureActive()
                if (decoded != null) {
                    android.util.Log.d("ThumbnailRepo", "Decode SUCCESS for: $keyString (${decoded.width}x${decoded.height})")
                    if (storeOnDisk) {
                        diskCache?.put(keyString, decoded)
                    }
                } else {
                    android.util.Log.w("ThumbnailRepo", "Decode FAILED (null) for: $keyString")
                }
                decoded
            }.also { job ->
                job.invokeOnCompletion { inFlight.remove(keyString) }
            }
        }
        val decoded = deferred.await()
        if (decoded != null && storeInMemory) {
            memoryCache.put(keyString, decoded)
        }
        if (decoded != null && !returnBitmap && !storeInMemory) {
            decoded.recycle()
            return null
        }
        return if (returnBitmap) decoded else null
    }

    fun cancel(key: ThumbnailKey) {
        inFlight.remove(key.keyString())?.cancel()
    }

    fun cancelAll() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }
}
