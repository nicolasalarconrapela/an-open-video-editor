package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.scheduler.ThumbnailScheduler
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineUiClip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val refineJob = mutableStateOf<Job?>(null)
    
    // Debounce state
    private var lastRequestTime = 0L
    private val debounceMs = 150L

    fun state(): Map<String, Bitmap?> = thumbnailState

    /**
     * Request thumbnails with LOD support and intelligent prioritization.
     * @param isScrolling If true, only loads visible thumbnails, no refinement
     */
    fun requestFilmstripLOD(
        clips: List<TimelineUiClip>,
        clipStartTimes: Map<String, Long>,
        viewportRangeMs: LongRange,
        lodBucket: ThumbnailLODConfig.LODBucket,
        pixelsPerSecond: Float,
        thumbnailWidthPx: Float,
        playheadTimeMs: Long,
        thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey,
        isScrolling: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce during active scrolling
        if (isScrolling && (currentTime - lastRequestTime) < debounceMs) {
            return
        }
        lastRequestTime = currentTime
        
        currentJob.value?.cancel()
        
        val job = scope.launch(ioDispatcher) {
            // Calculate interval based on LOD config
            val intervalMs = ThumbnailLODConfig.calculateIntervalMs(
                bucket = lodBucket,
                pixelsPerSecond = pixelsPerSecond,
                thumbnailWidthPx = thumbnailWidthPx
            )
            
            val config = ThumbnailLODConfig.getConfig(lodBucket)
            val neededKeys = mutableSetOf<String>()
            val prioritizedKeys = mutableListOf<PrioritizedKey>()
            
            // Phase 1: Collect keys for visible viewport
            clips.forEach { clip ->
                val clipStartMs = clipStartTimes[clip.id] ?: 0L
                val clipEndMs = clipStartMs + clip.durationMs
                val visibleStartMs = max(clipStartMs, viewportRangeMs.first)
                val visibleEndMs = min(clipEndMs, viewportRangeMs.last)
                
                if (visibleEndMs > visibleStartMs) {
                    var timeMs = visibleStartMs
                    var thumbCount = 0
                    
                    while (timeMs <= visibleEndMs && thumbCount < config.maxThumbsPerSegment) {
                        val key = thumbnailKeyProvider(timeMs * 1000, clip, lodBucket.level)
                        val keyString = key.keyString()
                        
                        // Calculate priority (distance from playhead)
                        val distanceFromPlayhead = abs(timeMs - playheadTimeMs)
                        val priority = when {
                            distanceFromPlayhead < 1000L -> Priority.CRITICAL  // < 1s from playhead
                            distanceFromPlayhead < 5000L -> Priority.HIGH      // < 5s from playhead
                            else -> Priority.NORMAL
                        }
                        
                        neededKeys.add(keyString)
                        prioritizedKeys.add(PrioritizedKey(key, timeMs, priority))
                        
                        timeMs += intervalMs
                        thumbCount++
                    }
                }
            }
            
            // Phase 2: Request fallback thumbnails for missing high-LOD ones
            val fallbackBuckets = ThumbnailLODConfig.getFallbackBuckets(lodBucket)
            val fallbackKeys = mutableListOf<PrioritizedKey>()
            
            prioritizedKeys.forEach { pk ->
                // Check if current bucket exists
                val existsInCache = repository.peek(pk.key) != null
                
                if (!existsInCache && fallbackBuckets.isNotEmpty()) {
                    // Try fallback buckets
                    for (fallbackBucket in fallbackBuckets) {
                        val fallbackKey = thumbnailKeyProvider(
                            pk.key.timeUs,
                            clips.firstOrNull { it.mediaUri == pk.key.videoIdOrUri } ?: continue,
                            fallbackBucket.level
                        )
                        
                        if (repository.peek(fallbackKey) != null) {
                            // Use fallback temporarily
                            withContext(mainDispatcher) {
                                thumbnailState[pk.key.keyString()] = repository.peek(fallbackKey)
                            }
                            break
                        }
                    }
                }
            }
            
            // Phase 3: Schedule requests sorted by priority
            val orderedKeys = prioritizedKeys
                .sortedWith(compareBy<PrioritizedKey> { it.priority.value }
                    .thenBy { abs(it.timeMs - playheadTimeMs) })
                .map { it.key }
            
            val deferreds = scheduler.scheduleKeys(orderedKeys) { key ->
                repository.getOrRequest(
                    key = key,
                    storeInMemory = true,
                    storeOnDisk = true,
                    returnBitmap = true
                )
            }
            
            // Await and update state
            deferreds.forEachIndexed { index, deferred ->
                val keyString = orderedKeys[index].keyString()
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
            
            // Cleanup stale thumbnails
            withContext(mainDispatcher) {
                val staleKeys = thumbnailState.keys - neededKeys
                staleKeys.forEach { thumbnailState.remove(it) }
            }
            
            // Phase 4: Refinement pass after idle (only if not scrolling)
            if (!isScrolling) {
                delay(300) // Wait for user to settle
                requestRefinementAroundPlayhead(
                    clips = clips,
                    clipStartTimes = clipStartTimes,
                    playheadTimeMs = playheadTimeMs,
                    lodBucket = lodBucket,
                    intervalMs = intervalMs / 2, // Higher density around playhead
                    thumbnailKeyProvider = thumbnailKeyProvider,
                    radiusMs = 3000L // 3 seconds around playhead
                )
            }
        }
        
        currentJob.value = job
    }
    
    /**
     * Refinement pass: higher density around playhead when idle.
     */
    private suspend fun requestRefinementAroundPlayhead(
        clips: List<TimelineUiClip>,
        clipStartTimes: Map<String, Long>,
        playheadTimeMs: Long,
        lodBucket: ThumbnailLODConfig.LODBucket,
        intervalMs: Long,
        thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey,
        radiusMs: Long
    ) {
        refineJob.value?.cancel()
        
        val job = scope.launch(ioDispatcher) {
            val refineRangeMs = (playheadTimeMs - radiusMs)..(playheadTimeMs + radiusMs)
            val refineKeys = mutableListOf<ThumbnailKey>()
            
            clips.forEach { clip ->
                val clipStartMs = clipStartTimes[clip.id] ?: 0L
                val clipEndMs = clipStartMs + clip.durationMs
                val refineStartMs = max(clipStartMs, refineRangeMs.first)
                val refineEndMs = min(clipEndMs, refineRangeMs.last)
                
                if (refineEndMs > refineStartMs) {
                    var timeMs = refineStartMs
                    while (timeMs <= refineEndMs) {
                        val key = thumbnailKeyProvider(timeMs * 1000, clip, lodBucket.level)
                        
                        // Only request if not already in memory
                        if (repository.peek(key) == null) {
                            refineKeys.add(key)
                        }
                        
                        timeMs += intervalMs
                    }
                }
            }
            
            // Request refined thumbnails
            val deferreds = scheduler.scheduleKeys(refineKeys) { key ->
                repository.getOrRequest(
                    key = key,
                    storeInMemory = true,
                    storeOnDisk = true,
                    returnBitmap = true
                )
            }
            
            deferreds.forEachIndexed { index, deferred ->
                launch {
                    val bitmap = deferred.await()
                    if (bitmap != null) {
                        withContext(mainDispatcher) {
                            thumbnailState[refineKeys[index].keyString()] = bitmap
                        }
                    }
                }
            }
        }
        
        refineJob.value = job
    }

    /**
     * Legacy method for backwards compatibility.
     */
    fun requestFilmstrip(
        clips: List<TimelineUiClip>,
        clipStartTimes: Map<String, Long>,
        viewportRangeMs: LongRange,
        thumbnailIntervalMs: Long,
        playheadTimeMs: Long,
        zoomBucket: Int,
        thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey
    ) {
        // Convert to LOD system
        val lodBucket = when (zoomBucket) {
            0 -> ThumbnailLODConfig.LODBucket.OVERVIEW
            1 -> ThumbnailLODConfig.LODBucket.NORMAL
            2 -> ThumbnailLODConfig.LODBucket.DETAILED
            else -> ThumbnailLODConfig.LODBucket.ULTRA
        }
        
        // Assume 96px thumbnail width and estimate pixelsPerSecond
        val estimatedPxPerSec = (1000f / thumbnailIntervalMs) * 96f
        
        requestFilmstripLOD(
            clips = clips,
            clipStartTimes = clipStartTimes,
            viewportRangeMs = viewportRangeMs,
            lodBucket = lodBucket,
            pixelsPerSecond = estimatedPxPerSec,
            thumbnailWidthPx = 96f,
            playheadTimeMs = playheadTimeMs,
            thumbnailKeyProvider = thumbnailKeyProvider,
            isScrolling = false
        )
    }

    fun requestPrecision(
        keys: List<ThumbnailKey>,
        playheadTimeMs: Long
    ) {
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
    
    // Helper classes
    private data class PrioritizedKey(
        val key: ThumbnailKey,
        val timeMs: Long,
        val priority: Priority
    )
    
    private enum class Priority(val value: Int) {
        CRITICAL(0),
        HIGH(1),
        NORMAL(2),
        LOW(3)
    }
}
