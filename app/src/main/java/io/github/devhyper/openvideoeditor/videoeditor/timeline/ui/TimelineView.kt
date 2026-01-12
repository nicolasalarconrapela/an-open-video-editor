package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRepository
import kotlin.math.max
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import io.github.devhyper.openvideoeditor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

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
    val isSelected: Boolean = false
)

data class TimelineUiTrack(
    val id: String,
    val label: String,
    val clips: List<TimelineUiClip>
)

@Composable
fun TimelineView(
    tracks: List<TimelineUiTrack>,
    pixelsPerSecond: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbnailRepository: ThumbnailRepository? = null,
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey)? = null,
    zoomBucket: Int = 0,
    onClipSelected: (trackId: String, clip: TimelineUiClip) -> Unit = { _, _ -> },
    onClipMoved: (trackId: String, fromId: String, toIndex: Int) -> Unit = { _, _, _ -> },
    onZoomChange: (zoomDelta: Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val clipMinWidthDp = 48.dp
    val clipHeight = 56.dp
    val thumbnailHeight = 40.dp
    val thumbnailWidth = 56.dp
    val spacingDp = 8.dp
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val masterClips = tracks.firstOrNull()?.clips.orEmpty()
    val thumbnailState = remember { mutableStateMapOf<String, android.graphics.Bitmap?>() }
    var thumbnailJob by remember { mutableStateOf<Job?>(null) }
    val currentTimeMs by remember(masterClips, pixelsPerSecond, listState) {
        derivedStateOf {
            if (masterClips.isEmpty()) {
                return@derivedStateOf 0L
            }
            val firstIndex = listState.firstVisibleItemIndex.coerceIn(0, masterClips.lastIndex)
            val timeBeforeMs = masterClips.take(firstIndex).sumOf { it.durationMs }
            val offsetMs = ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
            (timeBeforeMs + offsetMs).coerceAtLeast(0L)
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
    val viewportRangeMs by remember(masterClips, pixelsPerSecond, listState) {
        derivedStateOf {
            if (masterClips.isEmpty() || pixelsPerSecond <= 0f) {
                return@derivedStateOf 0L..0L
            }
            val firstIndex = listState.firstVisibleItemIndex.coerceIn(0, masterClips.lastIndex)
            val timeBeforeMs = masterClips.take(firstIndex).sumOf { it.durationMs }
            val offsetMs = ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
            val startMs = (timeBeforeMs + offsetMs).coerceAtLeast(0L)
            val viewportWidthPx = listState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(0f)
            val durationMs = ((viewportWidthPx / pixelsPerSecond) * 1000f).toLong()
            startMs..(startMs + durationMs)
        }
    }
    val thumbnailIntervalMs by remember(pixelsPerSecond) {
        derivedStateOf {
            val intervalPx = with(density) { thumbnailWidth.toPx() }
            ((intervalPx / pixelsPerSecond) * 1000f).toLong().coerceAtLeast(200L)
        }
    }
    val totalDurationMs = remember(tracks) {
        tracks.maxOfOrNull { track -> track.clips.sumOf { it.durationMs } } ?: 0L
    }
    val timelineCurrentTimeLabel = stringResource(
        R.string.timeline_current_time,
        currentTimeMs / 1000
    )
    val gridLineColor = MaterialTheme.colorScheme.outline
    val gridTextColor = MaterialTheme.colorScheme.onBackground
    val gridTextSizePx = with(density) { 12.dp.toPx() }

    if (thumbnailRepository != null && thumbnailKeyProvider != null) {
        val requestedRange = viewportRangeMs
        LaunchedEffect(requestedRange, zoomBucket, thumbnailIntervalMs, masterClips) {
            if (masterClips.isEmpty()) return@LaunchedEffect
            delay(120)
            if (requestedRange != viewportRangeMs) return@LaunchedEffect
            thumbnailRepository.cancelAll()
            thumbnailJob?.cancel()
            thumbnailState.clear()
            val job = scope.launch(Dispatchers.IO) {
                masterClips.forEach { clip ->
                    val clipStartMs = clipStartTimes[clip.id] ?: 0L
                    val clipEndMs = clipStartMs + clip.durationMs
                    val startMs = max(clipStartMs, requestedRange.first)
                    val endMs = min(clipEndMs, requestedRange.last)
                    if (endMs <= startMs) return@forEach
                    var timeMs = startMs
                    while (timeMs <= endMs) {
                        val key = thumbnailKeyProvider(timeMs * 1000, clip, zoomBucket)
                        val bitmap = thumbnailRepository.getOrRequest(key)
                        if (bitmap != null) {
                            thumbnailState[key.keyString()] = bitmap
                        }
                        timeMs += thumbnailIntervalMs
                    }
                }
            }
            thumbnailJob = job
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomChange(zoom)
                    }
                }
            }
    ) {
        Column {


            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(track.clips, key = { it.id }) { clip ->
                                val widthPx = (clip.durationMs / 1000f) * pixelsPerSecond
                                val widthDp = with(density) { widthPx.toDp() }
                                val clipWidth = max(widthDp.value, clipMinWidthDp.value).dp
                                val isSelected = clip.isSelected
                                val isDragging = draggingClipId == clip.id
                                val (containerColor, contentColor) = when (clip.type) {
                                    TimelineClipType.Video -> {
                                        MaterialTheme.colorScheme.primaryContainer to
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                    }

                                    TimelineClipType.Audio -> {
                                        MaterialTheme.colorScheme.secondaryContainer to
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                    }

                                    TimelineClipType.Overlay -> {
                                        MaterialTheme.colorScheme.tertiaryContainer to
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                }
                                val clipContainer = if (isSelected) {
                                    MaterialTheme.colorScheme.inversePrimary
                                } else {
                                    containerColor
                                }
                                val clipContent = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    contentColor
                                }
                                val clipContentDescription = stringResource(
                                    R.string.timeline_clip_item,
                                    clip.label
                                )
                                Box(
                                    modifier = Modifier
                                        .width(clipWidth)
                                        .height(clipHeight)
                                        .graphicsLayer {
                                            translationX = if (isDragging) dragOffsetPx else 0f
                                        }
                                        .pointerInput(track.id, clip.id, listState.layoutInfo) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingClipId = clip.id
                                                    dragOffsetPx = 0f
                                                },
                                                onDragEnd = {
                                                    val layoutInfo = listState.layoutInfo
                                                    val currentIndex =
                                                        track.clips.indexOfFirst { it.id == clip.id }
                                                    val itemInfo =
                                                        layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                            info.index == currentIndex
                                                        }
                                                    if (itemInfo != null) {
                                                        val dragCenter =
                                                            itemInfo.offset + (itemInfo.size / 2) + dragOffsetPx
                                                        val targetInfo =
                                                            layoutInfo.visibleItemsInfo.minByOrNull { info ->
                                                                abs((info.offset + (info.size / 2)) - dragCenter)
                                                            }
                                                        if (targetInfo != null && targetInfo.index != currentIndex) {
                                                            onClipMoved(
                                                                track.id,
                                                                clip.id,
                                                                targetInfo.index
                                                            )
                                                        }
                                                    }
                                                    draggingClipId = null
                                                    dragOffsetPx = 0f
                                                },
                                                onDragCancel = {
                                                    draggingClipId = null
                                                    dragOffsetPx = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetPx += dragAmount.x
                                                }
                                            )
                                        }
                                        .clickable { onClipSelected(track.id, clip) }
                                        .padding(horizontal = 4.dp)
                                        .semantics {
                                            contentDescription = clipContentDescription
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (thumbnailRepository != null && thumbnailKeyProvider != null && clip.type == TimelineClipType.Video) {
                                        val clipStartMs = clipStartTimes[clip.id] ?: 0L
                                        val clipEndMs = clipStartMs + clip.durationMs
                                        val visibleStart = max(clipStartMs, viewportRangeMs.first)
                                        val visibleEnd = min(clipEndMs, viewportRangeMs.last)
                                        val times = remember(
                                            clipStartMs,
                                            clip.durationMs,
                                            viewportRangeMs,
                                            thumbnailIntervalMs
                                        ) {
                                            buildList {
                                                if (visibleEnd <= visibleStart) return@buildList
                                                var timeMs = visibleStart
                                                while (timeMs <= visibleEnd) {
                                                    add(timeMs)
                                                    timeMs += thumbnailIntervalMs
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            times.forEach { timeMs ->
                                                val key = thumbnailKeyProvider(
                                                    timeMs * 1000,
                                                    clip,
                                                    zoomBucket
                                                )
                                                val bitmap = thumbnailState[key.keyString()]
                                                if (bitmap != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(
                                                            thumbnailWidth,
                                                            thumbnailHeight
                                                        )
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(thumbnailWidth, thumbnailHeight)
                                                            .background(Color.Black.copy(alpha = 0.15f))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (clip.type == TimelineClipType.Overlay) {
                                            Icon(
                                                imageVector = Icons.Filled.TextFields,
                                                contentDescription = null,
                                                tint = clipContent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (clip.type == TimelineClipType.Audio) {
                                            Icon(
                                                imageVector = Icons.Filled.LibraryMusic,
                                                contentDescription = null,
                                                tint = clipContent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Text(
                                            text = clip.label,
                                            color = clipContent,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis

                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)   // ✅ aquí SÍ hay BoxScope
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(Color.White)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(12.dp)
                            .offset(y = (-6).dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

