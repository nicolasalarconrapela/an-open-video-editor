package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.calculatePrefetchWindowMs
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.scheduler.ThumbnailScheduler
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineUiClip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ThumbnailRequestCoordinator(
    private val repository: ThumbnailRepository,
    private val scheduler: ThumbnailScheduler,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher
) {
    private val thumbnailState = mutableStateMapOf<String, Bitmap?>()
    private val currentJob = mutableStateOf<Job?>(null)

    fun state(): Map<String, Bitmap?> = thumbnailState

    fun requestFilmstrip(
        clips: List<TimelineUiClip>,
        clipStartTimes: Map<String, Long>,
        viewportRangeMs: LongRange,
        thumbnailIntervalMs: Long,
        playheadTimeMs: Long,
        zoomBucket: Int,
        thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey
    ) {
        currentJob.value?.cancel()
        val job = scope.launch(ioDispatcher) {
            val neededKeys = mutableSetOf<String>()
            val keys = mutableListOf<Pair<Long, ThumbnailKey>>()
            val prefetchWindowMs = calculatePrefetchWindowMs(viewportRangeMs)
            val prefetchRangeMs = (viewportRangeMs.first - prefetchWindowMs)..(viewportRangeMs.last + prefetchWindowMs)
            clips.forEach { clip ->
                val clipStartMs = clipStartTimes[clip.id] ?: 0L
                val clipEndMs = clipStartMs + clip.durationMs
                val visibleStartMs = max(clipStartMs, viewportRangeMs.first)
                val visibleEndMs = min(clipEndMs, viewportRangeMs.last)
                if (visibleEndMs > visibleStartMs) {
                    var timeMs = visibleStartMs
                    while (timeMs <= visibleEndMs) {
                        val key = thumbnailKeyProvider(timeMs * 1000, clip, zoomBucket)
                        val keyString = key.keyString()
                        neededKeys.add(keyString)
                        keys.add(timeMs to key)
                        timeMs += thumbnailIntervalMs
                    }
                }
                val prefetchStartMs = max(clipStartMs, prefetchRangeMs.first)
                val prefetchEndMs = min(clipEndMs, prefetchRangeMs.last)
                if (prefetchEndMs <= prefetchStartMs) return@forEach
                var timeMs = prefetchStartMs
                while (timeMs <= prefetchEndMs) {
                    val key = thumbnailKeyProvider(timeMs * 1000, clip, zoomBucket)
                    val isCachedInMemory = repository.peek(key) != null
                    val isCachedOnDisk = repository.isCachedOnDisk(key)
                    if (!isCachedInMemory && !isCachedOnDisk) {
                        keys.add(timeMs to key)
                    }
                    timeMs += thumbnailIntervalMs
                }
            }
            val orderedKeys = keys.distinctBy { it.second.keyString() }
                .sortedBy { abs(it.first - playheadTimeMs) }
                .map { it.second }
            val deferreds = scheduler.scheduleKeys(orderedKeys) { key ->
                val isVisible = key.keyString() in neededKeys
                repository.getOrRequest(
                    key = key,
                    storeInMemory = isVisible,
                    storeOnDisk = true,
                    returnBitmap = isVisible
                )
            }
            deferreds.forEachIndexed { index, deferred ->
                val keyString = orderedKeys[index].keyString()
                if (keyString !in neededKeys) return@forEachIndexed
                launch {
                    val bitmap = deferred.await()
                    if (bitmap != null) {
                        withContext(mainDispatcher) {
                            if (keyString in neededKeys) {
                                thumbnailState[keyString] = bitmap
                            }
                        }
                    }
                }
            }
            withContext(mainDispatcher) {
                val staleKeys = thumbnailState.keys - neededKeys
                staleKeys.forEach { thumbnailState.remove(it) }
            }
        }
        currentJob.value = job
    }

    fun requestPrecision(
        keys: List<ThumbnailKey>,
        playheadTimeMs: Long
    ) {
        android.util.Log.d("ThumbnailCoordinator", "requestPrecision called with ${keys.size} keys")
        currentJob.value?.cancel()
        val job = scope.launch(ioDispatcher) {
            val neededKeys = mutableSetOf<String>()
            val orderedKeys = keys.distinctBy { it.keyString() }
                .sortedBy { abs(it.timeUs / 1000 - playheadTimeMs) }
            val deferreds = scheduler.scheduleKeys(orderedKeys) { key ->
                repository.getOrRequest(
                    key = key,
                    storeInMemory = true,
                    storeOnDisk = true,
                    returnBitmap = true
                )
            }
            deferreds.forEachIndexed { index, deferred ->
                val key = orderedKeys[index]
                val keyString = key.keyString()
                neededKeys.add(keyString)
                android.util.Log.d("ThumbnailCoordinator", "Requesting precision thumbnail: $keyString")
                launch {
                    val bitmap = deferred.await()
                    if (bitmap != null) {
                        withContext(mainDispatcher) {
                            if (keyString in neededKeys) {
                                thumbnailState[keyString] = bitmap
                            }
                        }
                    } else {
                        android.util.Log.w("ThumbnailCoordinator", "Precision thumbnail returned NULL: $keyString")
                    }
                }
            }
            withContext(mainDispatcher) {
                val staleKeys = thumbnailState.keys - neededKeys
                staleKeys.forEach { thumbnailState.remove(it) }
            }
        }
        currentJob.value = job
    }
}
