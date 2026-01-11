package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import io.github.devhyper.openvideoeditor.R
import kotlin.math.floor

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
    onClipSelected: (trackId: String, clip: TimelineUiClip) -> Unit = { _, _ -> },
    onClipMoved: (trackId: String, fromId: String, toIndex: Int) -> Unit = { _, _, _ -> },
    onZoomChange: (zoomDelta: Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val clipMinWidthDp = 48.dp
    val clipHeight = 56.dp
    val spacingDp = 8.dp
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val masterClips = tracks.firstOrNull()?.clips.orEmpty()
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
    val totalDurationMs = remember(tracks) {
        tracks.maxOfOrNull { track -> track.clips.sumOf { it.durationMs } } ?: 0L
    }
    val timelineCurrentTimeLabel = stringResource(
        R.string.timeline_current_time,
        currentTimeMs / 1000
    )

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .drawBehind {
                        if (pixelsPerSecond <= 0f || totalDurationMs == 0L) return@drawBehind
                        val layoutInfo = listState.layoutInfo
                        val firstIndex = listState.firstVisibleItemIndex.coerceAtLeast(0)
                        val timeBeforeMs = masterClips.take(firstIndex).sumOf { it.durationMs }
                        val scrollPx =
                            (timeBeforeMs / 1000f) * pixelsPerSecond + listState.firstVisibleItemScrollOffset
                        val startSecond = floor(scrollPx / pixelsPerSecond).toInt().coerceAtLeast(0)
                        val secondsVisible = (size.width / pixelsPerSecond).toInt() + 2
                        val paint = android.graphics.Paint().apply {
                            color = MaterialTheme.colorScheme.onBackground.toArgb()
                            textSize = 24f
                            isAntiAlias = true
                        }
                        repeat(secondsVisible) { offset ->
                            val second = startSecond + offset
                            val x = (second * pixelsPerSecond) - scrollPx
                            if (x >= -pixelsPerSecond && x <= size.width + pixelsPerSecond) {
                                drawLine(
                                    color = MaterialTheme.colorScheme.outline,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                if (second % 2 == 0) {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "${second}s",
                                        x + 4.dp.toPx(),
                                        20.dp.toPx(),
                                        paint
                                    )
                                }
                            }
                        }
                    }
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Column {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacingDp)
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
                        .graphicsLayer { translationX = if (isDragging) dragOffsetPx else 0f }
                        .pointerInput(track.id, clip.id, listState.layoutInfo) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingClipId = clip.id
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    val layoutInfo = listState.layoutInfo
                                    val currentIndex = track.clips.indexOfFirst { it.id == clip.id }
                                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        info.index == currentIndex
                                    }
                                    if (itemInfo != null) {
                                        val dragCenter = itemInfo.offset + (itemInfo.size / 2) + dragOffsetPx
                                        val targetInfo = layoutInfo.visibleItemsInfo.minByOrNull { info ->
                                            abs((info.offset + (info.size / 2)) - dragCenter)
                                        }
                                        if (targetInfo != null && targetInfo.index != currentIndex) {
                                            onClipMoved(track.id, clip.id, targetInfo.index)
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
                        .background(
                            color = clipContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp)
                        .semantics {
                            contentDescription = clipContentDescription
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
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

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.secondary)
        )

        Text(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .semantics {
                    contentDescription = timelineCurrentTimeLabel
                },
            text = timelineCurrentTimeLabel,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
