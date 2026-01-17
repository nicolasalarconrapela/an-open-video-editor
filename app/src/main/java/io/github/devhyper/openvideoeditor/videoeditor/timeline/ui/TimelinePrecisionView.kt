package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRequestCoordinator
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelinePrecisionView(
    tracks: List<TimelineUiTrack>,
    zoomLevel: Float,
    currentTimeMs: Long,
    listState: LazyListState,
    onZoom: (Float) -> Unit,
    onTrim: (clipId: String, trimInMs: Long, trimOutMs: Long) -> Unit,
    onSplit: (clipId: String, atMs: Long) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onClipSelected: (clip: TimelineUiClip) -> Unit,
    onSeek: (timeMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailCoordinator: ThumbnailRequestCoordinator,
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey)? = null
) {
    val basePixelsPerSecond = 80f
    val pixelsPerSecond = (basePixelsPerSecond * zoomLevel).coerceIn(20f, 200f)
    val clipHeight = 64.dp // Slightly taller for better touch target
    val spacing = 0.dp // No gap for continuous filmstrip
    val zoomBucket = when {
        zoomLevel <= 0.85f -> 0
        zoomLevel <= 1.6f -> 1
        else -> 2
    }

    // Identify tracks
    val videoTrack = tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Video } }
    val audioTrack = tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Audio } }
    val masterClips = videoTrack?.clips.orEmpty()
    
    // Logs removed

    
    val clipStartTimes = remember(masterClips) {
        var accumulated = 0L
        masterClips.associate { clip ->
            val start = accumulated
            accumulated += clip.durationMs
            clip.id to start
        }
    }

    // View States (Controlled by external actions in real app, internal for now)
    var showControls by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }

    var lastScrollMs by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val thumbnailState = thumbnailCoordinator.state()

    // Segment logic to avoid Constraint crashes
    // Work entirely in PX for calculations, convert to DP only for modifiers
    val maxSegmentWidthDp = 2000.dp
    val maxSegmentWidthPx = with(density) { maxSegmentWidthDp.toPx() }
    
    data class TimelineSegment(
        val id: String,
        val clip: TimelineUiClip,
        val clipIndex: Int,
        val segmentIndex: Int,
        val isFirst: Boolean,
        val isLast: Boolean,
        val widthPx: Float,  // Store in px
        val startTimeOffsetMs: Long,
        val durationMs: Long
    )

    val segments = remember(videoTrack?.clips, pixelsPerSecond, density) {
        val result = mutableListOf<TimelineSegment>()
        videoTrack?.clips?.forEachIndexed { index, clip ->
            // Calculate in PX
            val totalWidthPx = (clip.durationMs / 1000f) * pixelsPerSecond
            
            if (totalWidthPx <= maxSegmentWidthPx) {
                // Single segment
                result.add(
                    TimelineSegment(
                        id = "${clip.id}_0",
                        clip = clip,
                        clipIndex = index,
                        segmentIndex = 0,
                        isFirst = true,
                        isLast = true,
                        widthPx = totalWidthPx,
                        startTimeOffsetMs = 0L,
                        durationMs = clip.durationMs
                    )
                )
            } else {
                // Multi-segment - split in PX
                var remainingWidthPx = totalWidthPx
                var currentOffsetMs = 0L
                var segIndex = 0
                
                while (remainingWidthPx > 0f) {
                    val segWidthPx = remainingWidthPx.coerceAtMost(maxSegmentWidthPx)
                    // Duration = (widthPx / pxPerSecond) * 1000
                    val segDurationMs = ((segWidthPx / pixelsPerSecond) * 1000f).toLong()
                    
                    result.add(
                        TimelineSegment(
                            id = "${clip.id}_$segIndex",
                            clip = clip,
                            clipIndex = index,
                            segmentIndex = segIndex,
                            isFirst = segIndex == 0,
                            isLast = remainingWidthPx <= maxSegmentWidthPx,
                            widthPx = segWidthPx,
                            startTimeOffsetMs = currentOffsetMs,
                            durationMs = segDurationMs
                        )
                    )
                    
                    remainingWidthPx -= segWidthPx
                    currentOffsetMs += segDurationMs
                    segIndex++
                }
            }
        }
        result
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoom(zoom)
                    }
                }
            }
    ) {
        val halfWidthDp = maxWidth / 2
        val horizontalPadding = PaddingValues(horizontal = halfWidthDp)

        LaunchedEffect(currentTimeMs, pixelsPerSecond, masterClips) {
            if (masterClips.isEmpty() || listState.isScrollInProgress) return@LaunchedEffect
            if (abs(currentTimeMs - lastScrollMs) < 120L) return@LaunchedEffect

            var remainingMs = currentTimeMs
            var targetSegmentIndex = 0
            
            // Find which segment covers currentTime
            var accrued = 0L
            for ((index, segment) in segments.withIndex()) {
                if (remainingMs < segment.durationMs) {
                    targetSegmentIndex = index
                    break
                }
                remainingMs -= segment.durationMs
            }
            
            val offsetPx = ((remainingMs / 1000f) * pixelsPerSecond).toInt()
            listState.scrollToItem(targetSegmentIndex, offsetPx)
            lastScrollMs = currentTimeMs
        }

        LaunchedEffect(listState, segments, pixelsPerSecond) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (listState.isScrollInProgress) {
                        var timeMs = 0L
                        // Sum duration of previous segments
                        for (i in 0 until index) {
                            timeMs += segments.getOrNull(i)?.durationMs ?: 0L
                        }
                        val offsetMs = ((offset / pixelsPerSecond) * 1000).toLong()
                        onSeek(timeMs + offsetMs)
                    }
                }
        }

        // Thumbnail cleanup handled by ThumbnailRequestCoordinator.

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Line 1: Controls (Hidden by default)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Repeat, contentDescription = "Loop", tint = Color.White)
                    Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = "Volume", tint = Color.White)
                }
            }

            // Line 2: Audio (Hidden by default)
            AnimatedVisibility(
                visible = showAudio,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (audioTrack != null) {
                    // Audio track uses separate ScrollState synced by offset
                    val audioScrollState = rememberScrollState()
                    
                    // Sync audio scroll with video master scroll
                    LaunchedEffect(listState.firstVisibleItemScrollOffset, listState.firstVisibleItemIndex) {
                        if (!listState.isScrollInProgress) return@LaunchedEffect
                        
                        // Calculate current pixel offset from segments
                        var totalOffsetPx = 0f
                        for (i in 0 until listState.firstVisibleItemIndex.coerceIn(0, segments.lastIndex)) {
                            totalOffsetPx += segments[i].widthPx
                        }
                        totalOffsetPx += listState.firstVisibleItemScrollOffset
                        
                        // Apply to audio scroll (convert px to Int for ScrollState)
                        audioScrollState.scrollTo(totalOffsetPx.toInt())
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .horizontalScroll(audioScrollState)
                            .padding(horizontal = halfWidthDp),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        audioTrack.clips.forEach { clip ->
                            val widthPx = (clip.durationMs / 1000f) * pixelsPerSecond
                            val widthDp = with(density) { widthPx.toDp() }
                            
                            Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .background(Color(0xFFE91E63).copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clip.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Line 3: Video Thumbnails (Always Visible)
            if (videoTrack != null) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(clipHeight),
                    state = listState,
                    contentPadding = horizontalPadding,
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    itemsIndexed(segments, key = { _, seg -> seg.id }) { index, segment ->
                        val clip = segment.clip
                        val widthPx = segment.widthPx
                        var dragOffsetPx by remember(segment.id) { mutableFloatStateOf(0f) }

                        val shape = RoundedCornerShape(
                            topStart = if (segment.isFirst) 8.dp else 0.dp,
                            bottomStart = if (segment.isFirst) 8.dp else 0.dp,
                            topEnd = if (segment.isLast) 8.dp else 0.dp,
                            bottomEnd = if (segment.isLast) 8.dp else 0.dp
                        )

                        Box(
                            modifier = Modifier
                                .width(with(density) { segment.widthPx.toDp() })
                                .fillMaxHeight()
                                .background(Color(0xFF1E1E1E), shape)
                                .clipToBounds()
                                .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                                .pointerInput(segment.id, widthPx) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            // D&D Logic based on width
                                            // Very simplified: if we dragged enough to cross a segment width equivalent
                                            // Real implementation needs to look at target segment
                                            
                                            val shift = (dragOffsetPx / widthPx).roundToInt()
                                            
                                            // We only move the CLIP, not the segment. 
                                            // So calculate new CLIP index.
                                            // Current clip index = segment.clipIndex
                                            // Total clips = videoTrack.clips.size
                                            
                                            if (shift != 0) {
                                                val targetClipIndex = (segment.clipIndex + shift).coerceIn(
                                                    0,
                                                    videoTrack.clips.lastIndex
                                                )
                                                if (targetClipIndex != segment.clipIndex) {
                                                    onMove(segment.clipIndex, targetClipIndex)
                                                }
                                            }
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = { dragOffsetPx = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetPx += dragAmount.x
                                        }
                                    )
                                }
                                .combinedClickable(
                                    onClick = { onClipSelected(clip) },
                                    onDoubleClick = {
                                        // Handle Split relative to this segment
                                        val clipStartMs = clipStartTimes[clip.id] ?: 0L
                                        val offsetInClipMs = segment.startTimeOffsetMs + 
                                            (currentTimeMs - (clipStartMs + segment.startTimeOffsetMs))
                                            
                                        // Re-calc: currentTimeMs is global. clipStartMs is global start of clip.
                                        // Relative time in clip = currentTimeMs - clipStartMs
                                        val relMs = currentTimeMs - clipStartMs
                                        if (relMs in 1 until clip.durationMs) {
                                            onSplit(clip.id, relMs)
                                        }
                                    },
                                    onLongClick = {
                                        // Similar logic for Trim
                                    }
                                )
                        ) {
                            if (thumbnailKeyProvider != null) {
                                // How many thumbnails fit in THIS segment?
                                val thumbnailCount = (segment.widthPx / with(density) { 48.dp.toPx() }).toInt().coerceAtLeast(1)
                                val intervalMs = segment.durationMs / thumbnailCount
                                val keys = mutableListOf<ThumbnailKey>()
                                
                                Row(modifier = Modifier.fillMaxSize()) {
                                    repeat(thumbnailCount) { i ->
                                        // Time relative to clip start!
                                        // segment.startTimeOffsetMs is the base for this segment
                                        val localTimeMs = segment.startTimeOffsetMs + (i * intervalMs)
                                        
                                        val key = thumbnailKeyProvider(localTimeMs * 1000, clip, zoomBucket)
                                        val bitmap = thumbnailState[key.keyString()]
                                        keys.add(key)
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF2A2A2A)) // Visible placeholder
                                                .border(0.5.dp, Color(0xFF3E3E3E))
                                        ) {
                                            androidx.compose.animation.Crossfade(
                                                targetState = bitmap,
                                                animationSpec = androidx.compose.animation.core.tween(300),
                                                label = "PrecThumbnail"
                                            ) { targetBitmap ->
                                                if (targetBitmap != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = targetBitmap.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    // Nice placeholder instead of broken image
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(keys) {
                                    thumbnailCoordinator.requestPrecision(
                                        keys = keys,
                                        playheadTimeMs = currentTimeMs
                                    )
                                }
                            }
                        }
                    }
                    // Add Button at the end
                    item {
                        Box(
                            modifier = Modifier
                                .size(clipHeight)
                                .background(Color.White)
                                .clickable { /* Add clip action */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Clip",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Playhead Overlay (Fixed Center)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )
    }
}

@Composable
private fun TimelineTimeRuler(
    modifier: Modifier,
    pixelsPerSecond: Float,
    onSeek: (Long) -> Unit
) {
    val seconds = max(1, (pixelsPerSecond / 10f).toInt())
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(seconds * 10) { index ->
            Box(
                modifier = Modifier
                    .width(pixelsPerSecond.dp)
                    .fillMaxHeight()
                    .clickable { onSeek(index * 1000L) },
                contentAlignment = Alignment.CenterStart
            ) {
                if (index % 2 == 0) {
                    Text(
                        text = "${index}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimHandles(
    modifier: Modifier = Modifier,
    onTrim: (Long, Long) -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onTrim(0L, 500L) }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(6.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onTrim(500L, 1_000L) }
        )
    }
}
