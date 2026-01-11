package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import io.github.devhyper.openvideoeditor.R
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

data class TimelineUiClip(
    val id: String,
    val durationMs: Long,
    val label: String,
    val isSelected: Boolean = false
)

@Composable
fun TimelineView(
    clips: List<TimelineUiClip>,
    pixelsPerSecond: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onClipSelected: (TimelineUiClip) -> Unit = {},
    onClipMoved: (fromId: String, toIndex: Int) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val clipMinWidthDp = 48.dp
    val clipHeight = 56.dp
    val spacingDp = 8.dp
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val currentTimeMs by remember(clips, pixelsPerSecond, listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                return@derivedStateOf 0L
            }
            val centerOffset = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val selectedItem = visibleItems.firstOrNull { info ->
                centerOffset in info.offset..(info.offset + info.size)
            }
            if (selectedItem == null) {
                return@derivedStateOf 0L
            }
            val clipIndex = selectedItem.index.coerceIn(0, clips.lastIndex)
            val clip = clips[clipIndex]
            val durationBefore = clips.take(clipIndex).sumOf { it.durationMs }
            val fraction = if (selectedItem.size == 0) {
                0f
            } else {
                (centerOffset - selectedItem.offset).toFloat() / selectedItem.size.toFloat()
            }
            val clipOffsetMs = (clip.durationMs * fraction).toLong()
            (durationBefore + clipOffsetMs).coerceAtLeast(0L)
        }
    }
    val timelineCurrentTimeLabel = stringResource(
        R.string.timeline_current_time,
        currentTimeMs / 1000
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clipToBounds()
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(spacingDp)
        ) {
            items(clips, key = { it.id }) { clip ->
                val widthPx = (clip.durationMs / 1000f) * pixelsPerSecond
                val widthDp = with(density) { widthPx.toDp() }
                val clipWidth = max(widthDp.value, clipMinWidthDp.value).dp
                val isSelected = clip.isSelected
                val clipContentDescription = stringResource(
                    R.string.timeline_clip_item,
                    clip.label
                )
                val isDragging = draggingClipId == clip.id
                Box(
                    modifier = Modifier
                        .width(clipWidth)
                        .height(clipHeight)
                        .graphicsLayer { translationX = if (isDragging) dragOffsetPx else 0f }
                        .pointerInput(clip.id, listState.layoutInfo) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingClipId = clip.id
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    val layoutInfo = listState.layoutInfo
                                    val currentIndex = clips.indexOfFirst { it.id == clip.id }
                                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        info.index == currentIndex
                                    }
                                    if (itemInfo != null) {
                                        val dragCenter = itemInfo.offset + (itemInfo.size / 2) + dragOffsetPx
                                        val targetInfo = layoutInfo.visibleItemsInfo.minByOrNull { info ->
                                            abs((info.offset + (info.size / 2)) - dragCenter)
                                        }
                                        if (targetInfo != null && targetInfo.index != currentIndex) {
                                            onClipMoved(clip.id, targetInfo.index)
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
                        .clickable { onClipSelected(clip) }
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
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
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
