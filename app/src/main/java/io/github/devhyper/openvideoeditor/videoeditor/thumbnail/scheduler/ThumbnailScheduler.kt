package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.scheduler

import android.graphics.Bitmap
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class ThumbnailScheduler(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    maxConcurrent: Int = 3
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()


    fun scheduleViewportRange(
        rangeUs: LongRange,
        stepUs: Long,
        playheadTimeUs: Long,
        zoomBucket: Int,
        prefetchSteps: Int = 2,
        buildKey: (timeUs: Long, zoomBucket: Int) -> ThumbnailKey,
        decode: suspend (ThumbnailKey) -> Bitmap?
    ): List<Deferred<Bitmap?>> {
        val visibleTimes = buildList {
            var time = rangeUs.first
            while (time <= rangeUs.last) {
                add(time)
                time += stepUs
            }
        }
        val prefetchTimes = buildList {
            repeat(prefetchSteps) { offset ->
                val delta = stepUs * (offset + 1)
                add(rangeUs.first - delta)
                add(rangeUs.last + delta)
            }
        }
        return scheduleVisibleTimes(
            visibleTimes = visibleTimes,
            playheadTimeUs = playheadTimeUs,
            zoomBucket = zoomBucket,
            prefetchTimes = prefetchTimes,
            buildKey = buildKey,
            decode = decode
        )
    }

    fun scheduleVisibleTimes(
        visibleTimes: List<Long>,
        playheadTimeUs: Long,
        zoomBucket: Int,
        prefetchTimes: List<Long> = emptyList(),
        buildKey: (timeUs: Long, zoomBucket: Int) -> ThumbnailKey,
        decode: suspend (ThumbnailKey) -> Bitmap?
    ): List<Deferred<Bitmap?>> {
        val orderedVisible = visibleTimes.distinct().sortedBy { abs(it - playheadTimeUs) }
        val orderedPrefetch = prefetchTimes.distinct().sortedBy { abs(it - playheadTimeUs) }
        val orderedKeys = orderedVisible.map { buildKey(it, zoomBucket) } +
                orderedPrefetch.map { buildKey(it, zoomBucket) }
        return scheduleKeys(orderedKeys, decode)
    }

    fun scheduleKeys(
        orderedKeys: List<ThumbnailKey>,
        decode: suspend (ThumbnailKey) -> Bitmap?
    ): List<Deferred<Bitmap?>> {
        val newKeys = orderedKeys.map { it.keyString() }.toSet()
        
        // Cancel jobs that are no longer needed
        val toRemove = inFlight.keys.filter { it !in newKeys }
        toRemove.forEach { key ->
            inFlight.remove(key)?.cancel()
        }

        return orderedKeys.map { key ->
            val keyString = key.keyString()
            // Use putIfAbsent to avoid Recursive update crash in ConcurrentHashMap
            // and checking existing value first for optimization.
            inFlight[keyString] ?: run {
                val newDeferred = scope.async(dispatcher) {
                    semaphore.withPermit {
                        decode(key)
                    }
                }
                
                val prev = inFlight.putIfAbsent(keyString, newDeferred)
                if (prev == null) {
                    // We successfully inserted the new job. Attach cleanup listener.
                    newDeferred.invokeOnCompletion { 
                        inFlight.remove(keyString, newDeferred) 
                    }
                    newDeferred
                } else {
                    // Another thread inserted a job for this key first.
                    // Cancel our redundant job and use the existing one.
                    newDeferred.cancel()
                    prev
                }
            }
        }
    }
}
