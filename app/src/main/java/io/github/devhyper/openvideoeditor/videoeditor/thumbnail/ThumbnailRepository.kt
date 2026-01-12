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

    suspend fun getOrRequest(key: ThumbnailKey): Bitmap? {
        val keyString = key.keyString()
        memoryCache.get(keyString)?.let { return it }
        val diskBitmap = withContext(dispatcher) { diskCache?.get(keyString) }
        if (diskBitmap != null) {
            memoryCache.put(keyString, diskBitmap)
            return diskBitmap
        }
        val deferred = inFlight.computeIfAbsent(keyString) {
            scope.async(dispatcher) {
                val decoded = decode(key)
                ensureActive()
                if (decoded != null) {
                    memoryCache.put(keyString, decoded)
                    diskCache?.put(keyString, decoded)
                }
                decoded
            }.also { job ->
                job.invokeOnCompletion { inFlight.remove(keyString) }
            }
        }
        return deferred.await()
    }

    fun cancel(key: ThumbnailKey) {
        inFlight.remove(key.keyString())?.cancel()
    }

    fun cancelAll() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }
}
