package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    val basePixelsPerSecond = 80f
    val pixelsPerSecond = (basePixelsPerSecond * zoomLevel).coerceIn(20f, 200f)
    val clipHeight = 64.dp
    val waveformHeight = 48.dp
    val spacing = 8.dp
    val masterClips = tracks.firstOrNull()?.clips.orEmpty()
    var lastScrollMs by remember { mutableLongStateOf(0L) }

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
        val offsetPx = ((remainingMs / 1000f) * pixelsPerSecond).toInt()
        listState.animateScrollToItem(
            targetIndex.coerceIn(0, masterClips.lastIndex),
            offsetPx
        )
        lastScrollMs = currentTimeMs
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
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
                    .height(28.dp),
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
                            color = MaterialTheme.colorScheme.onBackground
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
                                Box(
                                    modifier = Modifier
                                        .width(widthDp)
                                        .height(if (isAudio) waveformHeight else clipHeight)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    if (isAudio) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(24.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                        )
                                    }
                                    Text(
                                        modifier = Modifier.align(Alignment.BottomStart),
                                        text = clip.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .height(clipHeight + 28.dp)
                .background(MaterialTheme.colorScheme.tertiary)
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
                        color = MaterialTheme.colorScheme.onBackground
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
