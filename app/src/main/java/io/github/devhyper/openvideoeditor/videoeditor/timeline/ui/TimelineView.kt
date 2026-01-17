package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRequestCoordinator
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailLODConfig
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.THUMBNAIL_MIN_INTERVAL_MS
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TimelineClipType {
    Video,
    Audio,
    Overlay
}

data class TimelineUiClip(
    val id: String,
    val durationMs: Long,
    val label: String,
    val type: TimelineClipType,
    val mediaUri: String,
    val isSelected: Boolean = false
)

data class TimelineUiTrack(
    val id: String,
    val label: String,
    val clips: List<TimelineUiClip>
)

// Stable data structure for LazyRow - represents a segment of a clip
private data class ClipSegment(
    val clipId: String,
    val clip: TimelineUiClip,
    val segmentIndex: Int,
    val globalStartMs: Long, // Start time in timeline
    val durationMs: Long,    // Duration of this segment
    val isFirstInClip: Boolean,
    val isLastInClip: Boolean
)

@Composable
fun TimelineView(
    tracks: List<TimelineUiTrack>,
    pixelsPerSecond: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbnailCoordinator: ThumbnailRequestCoordinator,
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey),
    zoomBucket: Int = 0,
    onClipSelected: (trackId: String, clip: TimelineUiClip) -> Unit = { _, _ -> },
    onClipMoved: (trackId: String, fromId: String, toIndex: Int) -> Unit = { _, _, _ -> },
    onZoomChange: (zoomDelta: Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val thumbnailWidthDp = 96.dp
    val thumbnailHeightDp = 72.dp
    val maxSegmentWidthDp = 2000.dp
    val thumbnailWidthPx = with(density) { thumbnailWidthDp.toPx() }
    
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    
    val videoTrack = tracks.firstOrNull() ?: return
    val masterClips = videoTrack.clips
    val thumbnailState = thumbnailCoordinator.state()
    
    // Map zoomBucket to LOD bucket
    val lodBucket = remember(zoomBucket) {
        when (zoomBucket) {
            0 -> ThumbnailLODConfig.LODBucket.OVERVIEW
            1 -> ThumbnailLODConfig.LODBucket.NORMAL
            2 -> ThumbnailLODConfig.LODBucket.DETAILED
            else -> ThumbnailLODConfig.LODBucket.ULTRA
        }
    }
    
    // Stable dataset: Build segments from clips
    val segments = remember(masterClips, pixelsPerSecond, density) {
        val result = mutableListOf<ClipSegment>()
        var globalStartMs = 0L
        
        masterClips.forEach { clip ->
            val clipWidthPx = (clip.durationMs / 1000f) * pixelsPerSecond
            val clipWidthDp = with(density) { clipWidthPx.toDp() }
            val maxSegmentWidthPx = with(density) { maxSegmentWidthDp.toPx() }
            
            if (clipWidthDp <= maxSegmentWidthDp) {
                result.add(
                    ClipSegment(
                        clipId = clip.id,
                        clip = clip,
                        segmentIndex = 0,
                        globalStartMs = globalStartMs,
                        durationMs = clip.durationMs,
                        isFirstInClip = true,
                        isLastInClip = true
                    )
                )
            } else {
                var remainingMs = clip.durationMs
                var segmentStartMs = globalStartMs
                var segIndex = 0
                
                while (remainingMs > 0L) {
                    val segmentDurationMs = ((maxSegmentWidthPx / pixelsPerSecond) * 1000f).toLong()
                        .coerceAtMost(remainingMs)
                    
                    result.add(
                        ClipSegment(
                            clipId = clip.id,
                            clip = clip,
                            segmentIndex = segIndex,
                            globalStartMs = segmentStartMs,
                            durationMs = segmentDurationMs,
                            isFirstInClip = segIndex == 0,
                            isLastInClip = segmentDurationMs >= remainingMs
                        )
                    )
                    
                    remainingMs -= segmentDurationMs
                    segmentStartMs += segmentDurationMs
                    segIndex++
                }
            }
            
            globalStartMs += clip.durationMs
        }
        
        result
    }
    
    // Calculate viewport range
    val viewportRangeMs by remember(segments, pixelsPerSecond, listState) {
        derivedStateOf {
            if (segments.isEmpty() || pixelsPerSecond <= 0f) return@derivedStateOf 0L..0L
            
            val firstIndex = listState.firstVisibleItemIndex.coerceIn(0, segments.lastIndex)
            val startMs = segments[firstIndex].globalStartMs +
                    ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
            
            val viewportWidthPx = listState.layoutInfo.viewportSize.width.toFloat()
            val durationMs = ((viewportWidthPx / pixelsPerSecond) * 1000f).toLong()
            
            startMs..(startMs + durationMs)
        }
    }
    
    val currentTimeMs by remember(segments, pixelsPerSecond, listState) {
        derivedStateOf {
            if (segments.isEmpty()) return@derivedStateOf 0L
            val firstIndex = listState.firstVisibleItemIndex.coerceIn(0, segments.lastIndex)
            segments[firstIndex].globalStartMs +
                ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
        }
    }
    
    val clipStartTimes = remember(masterClips) {
        var accumulated = 0L
        masterClips.associate { clip ->
            val start = accumulated
            accumulated += clip.durationMs
            clip.id to start
        }
    }
    
    // Detect scrolling state
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    
    // Request thumbnails with LOD system (throttled during scroll)
    LaunchedEffect(viewportRangeMs, lodBucket, isScrolling) {
        if (masterClips.isEmpty()) return@LaunchedEffect
        
        // Debounce during scroll
        if (isScrolling) {
            delay(50)
        } else {
            delay(150)
        }
        
        thumbnailCoordinator.requestFilmstripLOD(
            clips = masterClips,
            clipStartTimes = clipStartTimes,
            viewportRangeMs = viewportRangeMs,
            lodBucket = lodBucket,
            pixelsPerSecond = pixelsPerSecond,
            thumbnailWidthPx = thumbnailWidthPx,
            playheadTimeMs = currentTimeMs,
            thumbnailKeyProvider = thumbnailKeyProvider,
            isScrolling = isScrolling
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomChange(zoom)
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 0.dp, vertical = 8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = segments,
                            key = { _, seg -> "${seg.clipId}_seg${seg.segmentIndex}" }
                        ) { segmentIndex, segment ->
                            val clip = segment.clip
                            val segmentWidthPx = (segment.durationMs / 1000f) * pixelsPerSecond
                            val segmentWidthDp = with(density) { segmentWidthPx.toDp() }
                            
                            // Calculate how many thumbnails fit in this segment
                            val thumbnailWidthPx = with(density) { thumbnailWidthDp.toPx() }
                            val thumbnailCount = (segmentWidthPx / thumbnailWidthPx).toInt()
                                .coerceAtLeast(1)
                            
                            val shape = RoundedCornerShape(
                                topStart = if (segment.isFirstInClip) 8.dp else 0.dp,
                                bottomStart = if (segment.isFirstInClip) 8.dp else 0.dp,
                                topEnd = if (segment.isLastInClip) 8.dp else 0.dp,
                                bottomEnd = if (segment.isLastInClip) 8.dp else 0.dp
                            )
                            
                            val isDragging = draggingClipId == clip.id
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        alpha = if (isDragging) 0.6f else 1f
                                        translationX = if (isDragging && segment.isFirstInClip) dragOffsetPx else 0f
                                    }
                                    .clickable {
                                        onClipSelected(videoTrack.id, clip)
                                    }
                                    .pointerInput(clip.id, segment.isFirstInClip) {
                                        if (segment.isFirstInClip) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingClipId = clip.id
                                                    dragOffsetPx = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetPx += dragAmount.x
                                                },
                                                onDragEnd = {
                                                    val clipIndex = masterClips.indexOf(clip)
                                                    val cellWidthPx = with(density) { thumbnailWidthDp.toPx() }
                                                    val indexShift = (dragOffsetPx / (cellWidthPx * 3)).toInt()
                                                    val newIndex = (clipIndex + indexShift)
                                                        .coerceIn(0, masterClips.lastIndex)
                                                    
                                                    if (newIndex != clipIndex) {
                                                        onClipMoved(videoTrack.id, clip.id, newIndex)
                                                    }
                                                    
                                                    draggingClipId = null
                                                    dragOffsetPx = 0f
                                                },
                                                onDragCancel = {
                                                    draggingClipId = null
                                                    dragOffsetPx = 0f
                                                }
                                            )
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Render thumbnails for this segment with LOD fallback
                                repeat(thumbnailCount) { thumbIndex ->
                                    val localTimeMs = segment.globalStartMs +
                                            ((thumbIndex.toFloat() / thumbnailCount) * segment.durationMs).toLong()
                                    
                                    // Try current LOD bucket first
                                    val currentKey = thumbnailKeyProvider(localTimeMs * 1000, clip, lodBucket.level)
                                    var bitmap = thumbnailState[currentKey.keyString()]
                                    
                                    // Fallback to lower LOD if not available
                                    if (bitmap == null) {
                                        val fallbackBuckets = ThumbnailLODConfig.getFallbackBuckets(lodBucket)
                                        for (fallback in fallbackBuckets) {
                                            val fallbackKey = thumbnailKeyProvider(
                                                localTimeMs * 1000,
                                                clip,
                                                fallback.level
                                            )
                                            bitmap = thumbnailState[fallbackKey.keyString()]
                                            if (bitmap != null) break
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(thumbnailWidthDp, thumbnailHeightDp)
                                            .clip(shape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.animation.Crossfade(
                                            targetState = bitmap,
                                            animationSpec = androidx.compose.animation.core.tween(300),
                                            label = "ThumbnailFade"
                                        ) { targetBitmap ->
                                            if (targetBitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = targetBitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                // Placeholder
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF1E1E1E))
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Separator at end of clip
                                if (segment.isLastInClip && segmentIndex < segments.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Playhead
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color.White)
                )
            }
        }
    }
}
