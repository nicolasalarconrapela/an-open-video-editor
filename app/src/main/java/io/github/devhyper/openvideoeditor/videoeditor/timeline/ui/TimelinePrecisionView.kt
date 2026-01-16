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

    // Identify tracks
    val videoTrack = tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Video } }
    val audioTrack = tracks.firstOrNull { it.clips.any { clip -> clip.type == TimelineClipType.Audio } }
    val masterClips = videoTrack?.clips.orEmpty()
    
    android.util.Log.d("TimelinePrecision", "TimelinePrecisionView initialized - videoTrack: ${videoTrack != null}, clips: ${masterClips.size}, thumbnailKeyProvider: ${thumbnailKeyProvider != null}")
    if (masterClips.isNotEmpty()) {
        android.util.Log.d("TimelinePrecision", "First clip: id=${masterClips[0].id}, mediaUri=${masterClips[0].mediaUri}, duration=${masterClips[0].durationMs}ms")
    }
    
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
            var targetIndex = 0
            masterClips.forEachIndexed { index, clip ->
                if (remainingMs <= clip.durationMs) {
                    targetIndex = index
                    return@forEachIndexed
                }
                remainingMs -= clip.durationMs
            }
            val offsetPx = ((remainingMs / 1000f) * pixelsPerSecond).toInt()
            listState.scrollToItem(targetIndex, offsetPx)
            lastScrollMs = currentTimeMs
        }

        LaunchedEffect(listState, masterClips, pixelsPerSecond) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (listState.isScrollInProgress) {
                        var timeMs = 0L
                        for (i in 0 until index) {
                            timeMs += masterClips.getOrNull(i)?.durationMs ?: 0L
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
                   LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        state = listState, // Sync scroll
                        contentPadding = horizontalPadding,
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        items(audioTrack.clips) { clip ->
                             val widthDp = ((clip.durationMs / 1000f) * pixelsPerSecond).dp
                             Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .background(Color(0xFFE91E63).copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                             ) {
                                 Text(
                                     text = clip.label, // "Amor Real..."
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
                    itemsIndexed(videoTrack.clips, key = { _, clip -> clip.id }) { index, clip ->
                        val widthDp = ((clip.durationMs / 1000f) * pixelsPerSecond)
                            .coerceAtLeast(48f)
                            .dp
                        val widthPx = with(density) { widthDp.toPx() }
                        var dragOffsetPx by remember(clip.id) { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .fillMaxHeight()
                                .background(Color(0xFF1E1E1E))
                                .clipToBounds()
                                .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                                .pointerInput(clip.id, widthPx) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val shift = (dragOffsetPx / widthPx).roundToInt()
                                            if (shift != 0) {
                                                val targetIndex = (index + shift).coerceIn(
                                                    0,
                                                    videoTrack.clips.lastIndex
                                                )
                                                if (targetIndex != index) {
                                                    onMove(index, targetIndex)
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
                                        val clipStartMs = clipStartTimes[clip.id] ?: 0L
                                        val offsetMs =
                                            (currentTimeMs - clipStartMs)
                                                .coerceIn(0L, clip.durationMs)
                                        if (offsetMs in 1 until clip.durationMs) {
                                            onSplit(clip.id, offsetMs)
                                        }
                                    },
                                    onLongClick = {
                                        val clipStartMs = clipStartTimes[clip.id] ?: 0L
                                        val offsetMs =
                                            (currentTimeMs - clipStartMs)
                                                .coerceIn(0L, clip.durationMs)
                                        if (offsetMs in 1 until clip.durationMs) {
                                            onTrim(clip.id, 0L, offsetMs)
                                        }
                                    }
                                )
                        ) {
                            android.util.Log.d("TimelinePrecision", "Rendering clip: ${clip.id}, thumbnailKeyProvider: ${thumbnailKeyProvider != null}")
                            if (thumbnailKeyProvider != null) {
                                val thumbnailCount = (widthDp.value / 48f).toInt().coerceAtLeast(1)
                                val intervalMs = clip.durationMs / thumbnailCount
                                val keys = mutableListOf<ThumbnailKey>()
                                android.util.Log.d("TimelinePrecision", "Clip ${clip.id}: generating $thumbnailCount thumbnails, interval: ${intervalMs}ms, mediaUri: ${clip.mediaUri}")
                                
                                Row(modifier = Modifier.fillMaxSize()) {
                                    repeat(thumbnailCount) { i ->
                                        val timeMs = i * intervalMs
                                        val key = thumbnailKeyProvider(timeMs * 1000, clip, 0)
                                        val bitmap = thumbnailState[key.keyString()]
                                        keys.add(key)
                                        
                                        android.util.Log.d("TimelinePrecision", "Thumbnail $i: timeMs=$timeMs, key=${key.keyString()}, bitmap=${bitmap != null}")

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF2A2A2A)) // Visible placeholder
                                                .border(0.5.dp, Color(0xFF3E3E3E))
                                        ) {
                                            if (bitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                // Loading/Error state
                                                Icon(
                                                    imageVector = Icons.Filled.BrokenImage,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.2f),
                                                    modifier = Modifier.align(Alignment.Center).size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(keys) {
                                    android.util.Log.d("TimelinePrecision", "LaunchedEffect triggered for clip ${clip.id} with ${keys.size} keys")
                                    thumbnailCoordinator.requestPrecision(keys)
                                }
                            } else {
                                android.util.Log.w("TimelinePrecision", "thumbnailKeyProvider is NULL for clip ${clip.id}")
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
