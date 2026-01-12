package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.scheduler

import android.graphics.Bitmap
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ThumbnailScheduler(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    maxConcurrent: Int = 3
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private var viewportJob: Job? = null

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

    private fun scheduleKeys(
        orderedKeys: List<ThumbnailKey>,
        decode: suspend (ThumbnailKey) -> Bitmap?
    ): List<Deferred<Bitmap?>> {
        val newKeys = orderedKeys.map { it.keyString() }.toSet()
        viewportJob?.cancel()
        viewportJob = SupervisorJob(scope.coroutineContext[Job])
        inFlight.entries.forEach { (key, deferred) ->
            if (key !in newKeys) {
                deferred.cancel()
                inFlight.remove(key)
            }
        }
        return orderedKeys.map { key ->
            val keyString = key.keyString()
            inFlight.computeIfAbsent(keyString) {
                scope.async(viewportJob!! + dispatcher) {
                    semaphore.withPermit {
                        decode(key)
                    }
                }.also { deferred ->
                    deferred.invokeOnCompletion {
                        inFlight.remove(keyString)
                    }
                }
            }
        }
    }
}
