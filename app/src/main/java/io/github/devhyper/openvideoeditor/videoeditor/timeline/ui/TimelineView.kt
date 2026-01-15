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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRequestCoordinator
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
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
    val mediaUri: String,
    val isSelected: Boolean = false
)

data class TimelineUiTrack(
    val id: String,
    val label: String,
    val clips: List<TimelineUiClip>
)

// Filmstrip architecture data structures
data class FilmstripCell(
    val clipId: String,
    val timeMs: Long,
    val thumbnailKey: ThumbnailKey
)

data class ClipBoundary(
    val clipId: String,
    val startMs: Long,
    val endMs: Long,
    val clip: TimelineUiClip
)

// Helper functions for filmstrip rendering
private fun buildClipBoundaries(
    clips: List<TimelineUiClip>,
    clipStartTimes: Map<String, Long>
): List<ClipBoundary> {
    return clips.map { clip ->
        val startMs = clipStartTimes[clip.id] ?: 0L
        ClipBoundary(
            clipId = clip.id,
            startMs = startMs,
            endMs = startMs + clip.durationMs,
            clip = clip
        )
    }
}

private fun buildFilmstripCells(
    clips: List<TimelineUiClip>,
    clipStartTimes: Map<String, Long>,
    viewportRangeMs: LongRange,
    thumbnailIntervalMs: Long,
    thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey,
    zoomBucket: Int
): List<FilmstripCell> {
    val cells = mutableListOf<FilmstripCell>()
    
    clips.forEach { clip ->
        val clipStartMs = clipStartTimes[clip.id] ?: 0L
        val clipEndMs = clipStartMs + clip.durationMs
        
        // Only generate cells for clips that overlap with viewport
        if (clipEndMs > viewportRangeMs.first && clipStartMs < viewportRangeMs.last) {
            val visibleStart = max(clipStartMs, viewportRangeMs.first)
            val visibleEnd = min(clipEndMs, viewportRangeMs.last)
            
            var timeMs = visibleStart
            while (timeMs <= visibleEnd) {
                val key = thumbnailKeyProvider(timeMs * 1000, clip, zoomBucket)
                cells.add(
                    FilmstripCell(
                        clipId = clip.id,
                        timeMs = timeMs,
                        thumbnailKey = key
                    )
                )
                timeMs += thumbnailIntervalMs
            }
        }
    }
    
    return cells.sortedBy { it.timeMs }
}

private fun findClipAtTime(
    boundaries: List<ClipBoundary>,
    timeMs: Long
): TimelineUiClip? {
    return boundaries.firstOrNull { boundary ->
        timeMs >= boundary.startMs && timeMs < boundary.endMs
    }?.clip
}

private fun isClipBoundary(
    cell: FilmstripCell,
    boundaries: List<ClipBoundary>,
    nextCell: FilmstripCell?
): Boolean {
    if (nextCell == null) return false
    return cell.clipId != nextCell.clipId
}

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
    val clipMinWidthDp = 48.dp
    val clipHeight = 80.dp
    val thumbnailHeight = 72.dp
    val thumbnailWidth = 96.dp
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val masterClips = tracks.firstOrNull()?.clips.orEmpty()
    val thumbnailState = thumbnailCoordinator.state()
    val currentTimeMs by remember(masterClips, pixelsPerSecond, listState) {
        derivedStateOf {
            if (masterClips.isEmpty()) {
                return@derivedStateOf 0L
            }
            val firstIndex = listState.firstVisibleItemIndex.coerceIn(0, masterClips.lastIndex)
            val timeBeforeMs = masterClips.take(firstIndex).sumOf { it.durationMs }
            val offsetMs =
                ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
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
            val offsetMs =
                ((listState.firstVisibleItemScrollOffset / pixelsPerSecond) * 1000f).toLong()
            val startMs = (timeBeforeMs + offsetMs).coerceAtLeast(0L)
            val viewportWidthPx =
                listState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(0f)
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
    remember(tracks) {
        tracks.maxOfOrNull { track -> track.clips.sumOf { it.durationMs } } ?: 0L
    }
    val electricBlue = Color(0xFF2979FF)
    val absoluteBlack = Color.Black

    val requestedRange = viewportRangeMs
    LaunchedEffect(requestedRange, zoomBucket, thumbnailIntervalMs, masterClips) {
        if (masterClips.isEmpty()) return@LaunchedEffect
        delay(120)
        if (requestedRange != viewportRangeMs) return@LaunchedEffect
        thumbnailCoordinator.requestFilmstrip(
            clips = masterClips,
            clipStartTimes = clipStartTimes,
            viewportRangeMs = requestedRange,
            thumbnailIntervalMs = thumbnailIntervalMs,
            zoomBucket = zoomBucket,
            thumbnailKeyProvider = thumbnailKeyProvider
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(absoluteBlack)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomChange(zoom)
                    }
                }
            }
    ) {
        // Single continuous track design
        val videoTrack = tracks.firstOrNull() ?: return

        // Build clip boundaries for selection detection
        val clipBoundaries = remember(videoTrack.clips, clipStartTimes) {
            buildClipBoundaries(videoTrack.clips, clipStartTimes)
        }

        // Build filmstrip cells (only visible ones)
        val filmstripCells = remember(
            videoTrack.clips,
            clipStartTimes,
            viewportRangeMs,
            thumbnailIntervalMs,
            zoomBucket
        ) {
            if (thumbnailKeyProvider != null) {
                buildFilmstripCells(
                    videoTrack.clips,
                    clipStartTimes,
                    viewportRangeMs,
                    thumbnailIntervalMs,
                    thumbnailKeyProvider,
                    zoomBucket
                )
            } else {
                emptyList()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Single continuous card with filmstrip
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
                            filmstripCells,
                            key = { _, cell -> "${cell.clipId}_${cell.timeMs}" }
                        ) { index, cell ->
                            // Render thumbnail cell
                            val bitmap = thumbnailState[cell.thumbnailKey.keyString()]
                            val nextCell = filmstripCells.getOrNull(index + 1)
                            val prevCell = filmstripCells.getOrNull(index - 1)

                            val isStart = prevCell?.clipId != cell.clipId
                            val isEnd = nextCell?.clipId != cell.clipId
                            
                            val shape = RoundedCornerShape(
                                topStart = if (isStart) 8.dp else 0.dp,
                                bottomStart = if (isStart) 8.dp else 0.dp,
                                topEnd = if (isEnd) 8.dp else 0.dp,
                                bottomEnd = if (isEnd) 8.dp else 0.dp
                            )

                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(thumbnailWidth, thumbnailHeight)
                                        .clip(shape)
                                        .clickable {
                                            val clip = findClipAtTime(clipBoundaries, cell.timeMs)
                                            clip?.let { onClipSelected(videoTrack.id, it) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF1E1E1E))
                                        )
                                    }
                                }

                                // Separator at clip boundary
                                if (isEnd) {
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

            // Playhead overlay (centered vertical line)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // Vertical line
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
