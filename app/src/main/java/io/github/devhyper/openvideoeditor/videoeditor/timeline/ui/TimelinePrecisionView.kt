package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRequestCoordinator
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
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
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey)? = null,
    selectedClipId: String? = null
) {
    val basePixelsPerSecond = 80f
    val pixelsPerSecond = (basePixelsPerSecond * zoomLevel).coerceIn(20f, 200f)
    val clipHeight = 64.dp
    val spacing = 0.dp

    // Map continuous zoom to discrete LOD buckets
    val lodBucket = remember(zoomLevel) {
        io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailLODConfig.getBucketForZoom(
            zoomLevel
        )
    }

    // Track zoom gesture state
    var isZooming by remember { mutableStateOf(false) }
    var zoomGestureEndTime by remember { mutableLongStateOf(0L) }

    // Identify tracks
    val videoTrack =
        tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Video } }
    val audioTrack =
        tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Audio } }
    val masterClips = videoTrack?.clips.orEmpty()

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
    val density = androidx.compose.ui.platform.LocalDensity.current
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
                // Multi-segment - split in PX, with exact duration accounting
                var remainingWidthPx = totalWidthPx
                var currentOffsetMs = 0L
                var segIndex = 0

                while (remainingWidthPx > 0f) {
                    val segWidthPx = remainingWidthPx.coerceAtMost(maxSegmentWidthPx)
                    val isLastSeg = remainingWidthPx <= maxSegmentWidthPx

                    // Provisional duration based on px->ms, rounded, never 0ms
                    val computedMs = ((segWidthPx / pixelsPerSecond) * 1000f)
                        .roundToInt()
                        .toLong()
                        .coerceAtLeast(1L)

                    // Ensure sum of seg durations == clip.durationMs (last segment takes remainder)
                    val segDurationMs = if (isLastSeg) {
                        (clip.durationMs - currentOffsetMs).coerceAtLeast(1L)
                    } else {
                        computedMs.coerceAtMost(
                            (clip.durationMs - currentOffsetMs - 1L).coerceAtLeast(1L)
                        )
                    }

                    result.add(
                        TimelineSegment(
                            id = "${clip.id}_$segIndex",
                            clip = clip,
                            clipIndex = index,
                            segmentIndex = segIndex,
                            isFirst = segIndex == 0,
                            isLast = isLastSeg,
                            widthPx = segWidthPx,
                            startTimeOffsetMs = currentOffsetMs,
                            durationMs = segDurationMs
                        )
                    )

                    remainingWidthPx -= segWidthPx
                    currentOffsetMs += segDurationMs
                    segIndex++

                    // Safety: if we've consumed full duration, stop
                    if (currentOffsetMs >= clip.durationMs) break
                }
            }
        }
        result
    }
    val segmentStartTimesMs = remember(segments) {
        var accumulated = 0L
        segments.map { segment ->
            val start = accumulated
            accumulated += segment.durationMs
            start
        }
    }
    val segmentStartOffsetsPx = remember(segments) {
        var accumulated = 0f
        segments.map { segment ->
            val start = accumulated
            accumulated += segment.widthPx
            start
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectZoomGesturesWithActivity(
                    onGestureStart = {
                        isZooming = true
                    },
                    onGestureEnd = {
                        isZooming = false
                        zoomGestureEndTime = System.currentTimeMillis()
                    },
                    onZoom = { zoom ->
                        if (zoom != 1f) {
                            isZooming = true
                            onZoom(zoom)
                        }
                    }
                )
            }
    ) {
        val halfWidthDp = maxWidth / 2
        val horizontalPadding = PaddingValues(horizontal = halfWidthDp)

        LaunchedEffect(currentTimeMs, pixelsPerSecond, masterClips, segments) {
            if (masterClips.isEmpty() || listState.isScrollInProgress) return@LaunchedEffect
            if (abs(currentTimeMs - lastScrollMs) < 120L) return@LaunchedEffect
            if (segments.isEmpty()) return@LaunchedEffect

            val totalDurationMs =
                segmentStartTimesMs.lastOrNull()?.plus(segments.lastOrNull()?.durationMs ?: 0L)
                    ?: 0L
            val clampedTimeMs = currentTimeMs.coerceIn(0L, totalDurationMs)
            val searchIndex = segmentStartTimesMs.binarySearch(clampedTimeMs)
            val targetSegmentIndex = if (searchIndex >= 0) {
                searchIndex
            } else {
                (-(searchIndex + 1) - 1).coerceIn(0, segments.lastIndex)
            }
            val segmentStartMs = segmentStartTimesMs.getOrElse(targetSegmentIndex) { 0L }
            val remainingMs =
                (clampedTimeMs - segmentStartMs).coerceIn(0L, segments[targetSegmentIndex].durationMs)

            val offsetPx = ((remainingMs / 1000f) * pixelsPerSecond).toInt()
            listState.scrollToItem(targetSegmentIndex, offsetPx)
            lastScrollMs = currentTimeMs
        }

        LaunchedEffect(listState, segments, pixelsPerSecond) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (listState.isScrollInProgress) {
                        val timeMs = segmentStartTimesMs.getOrElse(index) { 0L }
                        val offsetMs = ((offset / pixelsPerSecond) * 1000).toLong()
                        onSeek(timeMs + offsetMs)
                    }
                }
        }

        // Time Ruler Scroll State
        val rulerScrollState = rememberScrollState()

        // Sync Ruler with Video Scroll
        LaunchedEffect(listState.firstVisibleItemScrollOffset, listState.firstVisibleItemIndex) {
            if (!listState.isScrollInProgress) return@LaunchedEffect
            if (segments.isEmpty()) return@LaunchedEffect

            val safeIndex = listState.firstVisibleItemIndex.coerceIn(0, segments.lastIndex)
            val totalOffsetPx =
                segmentStartOffsetsPx.getOrElse(safeIndex) { 0f } +
                    listState.firstVisibleItemScrollOffset
            rulerScrollState.scrollTo(totalOffsetPx.toInt())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Calculate total duration for ruler (max of all tracks)
            val totalDurationMs = remember(tracks) {
                tracks.maxOfOrNull { track ->
                    track.clips.fold(0L) { acc, clip -> acc + clip.durationMs }
                } ?: 300_000L // Default 5 min
            }
            val totalSeconds = ((totalDurationMs / 1000) + 60).toInt() // Add buffer

            // Line 0: Time Ruler
            TimelineTimeRuler(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                pixelsPerSecond = pixelsPerSecond,
                scrollState = rulerScrollState,
                horizontalPadding = halfWidthDp,
                totalSeconds = totalSeconds,
                onSeek = onSeek
            )

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
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = "Loop",
                        tint = Color.White
                    )
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White
                    )
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
                    LaunchedEffect(
                        listState.firstVisibleItemScrollOffset,
                        listState.firstVisibleItemIndex
                    ) {
                        if (!listState.isScrollInProgress) return@LaunchedEffect
                        if (segments.isEmpty()) return@LaunchedEffect

                        // Calculate current pixel offset from segments
                        val safeIndex =
                            listState.firstVisibleItemIndex.coerceIn(0, segments.lastIndex)
                        val totalOffsetPx =
                            segmentStartOffsetsPx.getOrElse(safeIndex) { 0f } +
                                listState.firstVisibleItemScrollOffset

                        // Apply to audio scroll
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
                                    .background(
                                        Color(0xFFE91E63).copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp)
                                    ),
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
                    itemsIndexed(segments, key = { _, seg -> seg.id }) { _, segment ->
                        val clip = segment.clip
                        val widthPx = segment.widthPx
                        var dragOffsetPx by remember(segment.id) { mutableFloatStateOf(0f) }

                        val shape = RoundedCornerShape(
                            topStart = if (segment.isFirst) 8.dp else 0.dp,
                            bottomStart = if (segment.isFirst) 8.dp else 0.dp,
                            topEnd = if (segment.isLast) 8.dp else 0.dp,
                            bottomEnd = if (segment.isLast) 8.dp else 0.dp
                        )

                        val isSelected = clip.id == selectedClipId

                        Box(
                            modifier = Modifier
                                .width(with(density) { segment.widthPx.toDp() })
                                .fillMaxHeight()
                                .background(Color(0xFF1E1E1E), shape)
                                .clipToBounds()
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color(0xFFFFD700) else Color.Transparent,
                                    shape = shape
                                )
                                .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                                .pointerInput(segment.id, widthPx) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            // D&D Logic based on width (simplified)
                                            val shift = (dragOffsetPx / widthPx).roundToInt()

                                            if (shift != 0) {
                                                val targetClipIndex =
                                                    (segment.clipIndex + shift).coerceIn(
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
                                        // Split based on global playhead position relative to this clip
                                        val clipStartMs = clipStartTimes[clip.id] ?: 0L
                                        val relMs = currentTimeMs - clipStartMs
                                        if (relMs in 1 until clip.durationMs) {
                                            onSplit(clip.id, relMs)
                                        }
                                    },
                                    onLongClick = {
                                        onClipSelected(clip)
                                    }
                                )
                        ) {
                            // Trim Handles (only if selected)
                            if (isSelected) {
                                if (segment.isFirst) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(10.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFFFFD700).copy(alpha = 0.5f))
                                    )
                                }
                                if (segment.isLast) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(10.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFFFFD700).copy(alpha = 0.5f))
                                    )
                                }
                            }
                            if (thumbnailKeyProvider != null) {
                                // Calculate thumbnails for THIS segment using LOD config
                                val lodConfig =
                                    io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailLODConfig.getConfig(
                                        lodBucket
                                    )

                                val intervalMs =
                                    io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailLODConfig.calculateIntervalMs(
                                        bucket = lodBucket,
                                        pixelsPerSecond = pixelsPerSecond,
                                        thumbnailWidthPx = with(density) { 48.dp.toPx() }
                                    )

                                val thumbnailCount = (segment.durationMs / intervalMs).toInt()
                                    .coerceIn(1, lodConfig.maxThumbsPerSegment)

                                Row(modifier = Modifier.fillMaxSize()) {
                                    repeat(thumbnailCount) { i ->
                                        val localTimeMs =
                                            segment.startTimeOffsetMs + (i * intervalMs)

                                        // Try current LOD first
                                        val currentKey = thumbnailKeyProvider(
                                            localTimeMs * 1000,
                                            clip,
                                            lodBucket.level
                                        )
                                        var bitmap = thumbnailState[currentKey.keyString()]

                                        // Fallback to lower LOD if not available
                                        if (bitmap == null) {
                                            val fallbackBuckets =
                                                io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailLODConfig.getFallbackBuckets(
                                                    lodBucket
                                                )
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
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF2A2A2A))
                                                .border(0.5.dp, Color(0xFF3E3E3E))
                                        ) {
                                            androidx.compose.animation.Crossfade(
                                                targetState = bitmap,
                                                animationSpec = androidx.compose.animation.core.tween(
                                                    300
                                                ),
                                                label = "PrecThumbnail"
                                            ) { targetBitmap ->
                                                if (targetBitmap != null) {
                                                    Image(
                                                        bitmap = targetBitmap.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0xFF2A2A2A))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Request thumbnails with LOD system
                                val isActiveGesture = isZooming || listState.isScrollInProgress
                                val timeSinceZoomEnd =
                                    System.currentTimeMillis() - zoomGestureEndTime
                                val shouldRefine = !isActiveGesture && timeSinceZoomEnd > 300

                                // Bucketize playhead to avoid restarting effect too frequently
                                val playheadBucket =
                                    remember(currentTimeMs) { currentTimeMs / 200L }

                                LaunchedEffect(
                                    segment.id,
                                    lodBucket,
                                    isActiveGesture,
                                    shouldRefine,
                                    playheadBucket
                                ) {
                                    if (isActiveGesture) {
                                        // During gesture: minimal loading
                                        delay(100)
                                    } else {
                                        // Idle: full loading + refinement
                                        delay(150)
                                    }

                                    // Build keys for this segment
                                    val keys = (0 until thumbnailCount).map { i ->
                                        val localTimeMs =
                                            segment.startTimeOffsetMs + (i * intervalMs)
                                        thumbnailKeyProvider(
                                            localTimeMs * 1000,
                                            clip,
                                            lodBucket.level
                                        )
                                    }

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
    scrollState: ScrollState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    totalSeconds: Int = 300,
    onSeek: (Long) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val secondWidthDp = with(density) { pixelsPerSecond.toDp() }

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        repeat(totalSeconds) { sec ->
            Box(
                modifier = Modifier
                    .width(secondWidthDp)
                    .fillMaxHeight()
                    .clickable { onSeek(sec * 1000L) },
                contentAlignment = Alignment.BottomStart
            ) {
                // Tick mark
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(if (sec % 5 == 0) 12.dp else 6.dp)
                        .background(if (sec % 5 == 0) Color.Gray else Color.DarkGray)
                        .align(Alignment.BottomStart)
                )

                if (sec % 5 == 0) {
                    Text(
                        text = "${sec}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        modifier = Modifier
                            .padding(start = 4.dp, bottom = 4.dp)
                            .align(Alignment.BottomStart)
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

private suspend fun PointerInputScope.detectZoomGesturesWithActivity(
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
    onZoom: (zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f) {
                    onZoom(zoomChange)

                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        onGestureEnd()
    }
}
