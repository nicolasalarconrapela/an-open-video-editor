package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineUiClip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class ThumbnailRequestCoordinator(
    private val repository: ThumbnailRepository,
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
        zoomBucket: Int,
        thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey
    ) {
        currentJob.value?.cancel()
        val job = scope.launch(ioDispatcher) {
            val neededKeys = mutableSetOf<String>()
            clips.forEach { clip ->
                val clipStartMs = clipStartTimes[clip.id] ?: 0L
                val clipEndMs = clipStartMs + clip.durationMs
                val startMs = max(clipStartMs, viewportRangeMs.first)
                val endMs = min(clipEndMs, viewportRangeMs.last)
                if (endMs <= startMs) return@forEach
                var timeMs = startMs
                while (timeMs <= endMs) {
                    val key = thumbnailKeyProvider(timeMs * 1000, clip, zoomBucket)
                    val keyString = key.keyString()
                    neededKeys.add(keyString)
                    val bitmap = repository.getOrRequest(key)
                    if (bitmap != null) {
                        withContext(mainDispatcher) {
                            thumbnailState[keyString] = bitmap
                        }
                    }
                    timeMs += thumbnailIntervalMs
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
        keys: List<ThumbnailKey>
    ) {
        currentJob.value?.cancel()
        val job = scope.launch(ioDispatcher) {
            val neededKeys = mutableSetOf<String>()
            keys.forEach { key ->
                val keyString = key.keyString()
                neededKeys.add(keyString)
                val bitmap = repository.getOrRequest(key)
                if (bitmap != null) {
                    withContext(mainDispatcher) {
                        thumbnailState[keyString] = bitmap
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
