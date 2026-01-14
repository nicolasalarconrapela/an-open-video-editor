package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun TimelinePrecisionView(
    tracks: List<TimelineUiTrack>,
    zoomLevel: Float,
    currentTimeMs: Long,
    listState: LazyListState,
    onZoom: (Float) -> Unit,
    onTrim: (clipId: String, trimInMs: Long, trimOutMs: Long) -> Unit,
    onSeek: (timeMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailRepository: ThumbnailRepository? = null,
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey)? = null
) {
    val basePixelsPerSecond = 80f
    val pixelsPerSecond = (basePixelsPerSecond * zoomLevel).coerceIn(20f, 200f)
    val clipHeight = 58.dp
    val waveformHeight = 42.dp
    val spacing = 6.dp
    val masterClips = tracks.firstOrNull()?.clips.orEmpty()
    var lastScrollMs by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val contentPaddingPx = with(density) { 16.dp.toPx() }
    val scope = rememberCoroutineScope()
    val thumbnailState = remember { mutableStateMapOf<String, android.graphics.Bitmap?>() }

    LaunchedEffect(currentTimeMs, pixelsPerSecond, masterClips) {
        if (masterClips.isEmpty()) return@LaunchedEffect
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
        val viewportWidthPx = listState.layoutInfo.viewportSize.width.toFloat()
        val halfViewportPx = (viewportWidthPx / 2f).coerceAtLeast(0f)
        val rawOffsetPx = ((remainingMs / 1000f) * pixelsPerSecond) - halfViewportPx + contentPaddingPx
        var offsetPx = rawOffsetPx.toInt()
        if (offsetPx < 0 && targetIndex > 0) {
            val prevDuration = masterClips[targetIndex - 1].durationMs
            val prevWidthPx = ((prevDuration / 1000f) * pixelsPerSecond).toInt()
            targetIndex -= 1
            offsetPx += prevWidthPx
        }
        listState.animateScrollToItem(
            targetIndex.coerceIn(0, masterClips.lastIndex),
            offsetPx.coerceAtLeast(0)
        )
        lastScrollMs = currentTimeMs
    }

    Box(
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
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            TimelineTimeRuler(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                pixelsPerSecond = pixelsPerSecond,
                onSeek = onSeek
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(clipHeight),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            items(track.clips, key = { it.id }) { clip ->
                                val widthDp = ((clip.durationMs / 1000f) * pixelsPerSecond)
                                    .coerceAtLeast(48f)
                                    .dp
                                val isAudio = clip.type == TimelineClipType.Audio
                                val isVideo = clip.type == TimelineClipType.Video

                                Box(
                                    modifier = Modifier
                                        .width(widthDp)
                                        .height(if (isAudio) waveformHeight else clipHeight)
                                        .background(
                                            color = Color(0xFF1E1E1E),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(0.dp) // Removed padding to let thumbnails fill
                                        .clipToBounds()
                                ) {
                                    if (isVideo && thumbnailRepository != null && thumbnailKeyProvider != null) {
                                        // Render Filmstrip
                                        val thumbnailCount = (widthDp.value / 48f).toInt().coerceAtLeast(1)
                                        val intervalMs = clip.durationMs / thumbnailCount

                                        Row(modifier = Modifier.fillMaxSize()) {
                                            repeat(thumbnailCount) { i ->
                                                val timeMs = i * intervalMs
                                                val key = thumbnailKeyProvider(timeMs * 1000, clip, 0)
                                                val bitmap = thumbnailState[key.keyString()]

                                                LaunchedEffect(key) {
                                                    if (thumbnailState[key.keyString()] == null) {
                                                        val bmp = thumbnailRepository.getOrRequest(key)
                                                        if (bmp != null) {
                                                            thumbnailState[key.keyString()] = bmp
                                                        }
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .background(Color.Black)
                                                ) {
                                                    if (bitmap != null) {
                                                        androidx.compose.foundation.Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // Overlay for selection/text readability
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f))
                                        )
                                    } else if (isAudio) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(24.dp)
                                                    .background(
                                                        color = Color(0xFFE91E63), // Pink for Audio
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp)
                                                    .background(
                                                        color = Color(0xFF9C27B0), // Purple for others
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                            )
                                        }
                                    }
                                    Text(
                                        modifier = Modifier.align(Alignment.BottomStart),
                                        text = clip.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    if (clip.isSelected) {
                                        TrimHandles(
                                            modifier = Modifier.fillMaxWidth(),
                                            onTrim = { trimIn, trimOut ->
                                                onTrim(clip.id, trimIn, trimOut)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(clipHeight + 24.dp)
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
