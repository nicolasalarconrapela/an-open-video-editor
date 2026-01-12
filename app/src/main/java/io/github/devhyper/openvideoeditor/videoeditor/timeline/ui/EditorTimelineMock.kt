package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

enum class TrackType {
    Video,
    Text,
    Music
}

data class EditorClip(
    val id: String,
    val label: String,
    val durationMs: Long,
    val color: Color,
    val trackType: TrackType,
    val offsetPx: Float = 0f
)

data class EditorTrack(
    val id: String,
    val label: String,
    val type: TrackType,
    val clips: List<EditorClip>
)

data class EditorUiState(
    val tracks: List<EditorTrack>,
    val selectedClipId: String? = null,
    val pxPerSecond: Float = 120f,
    val scrollOffsetPx: Float = 0f,
    val currentTimeMs: Long = 0L
)

class EditorTimelineViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        EditorUiState(
            tracks = listOf(
                EditorTrack(
                    id = "track-video",
                    label = "Pista de vídeo",
                    type = TrackType.Video,
                    clips = listOf(
                        EditorClip(
                            id = "clip-video-1",
                            label = "B-roll",
                            durationMs = 3_000L,
                            color = Color(0xFF4C515C),
                            trackType = TrackType.Video
                        )
                    )
                ),
                EditorTrack(
                    id = "track-text",
                    label = "Pista de texto",
                    type = TrackType.Text,
                    clips = listOf(
                        EditorClip(
                            id = "clip-text-1",
                            label = "nNNa",
                            durationMs = 2_000L,
                            color = Color(0xFF3F4149),
                            trackType = TrackType.Text
                        )
                    )
                ),
                EditorTrack(
                    id = "track-music",
                    label = "Pista de música",
                    type = TrackType.Music,
                    clips = listOf(
                        EditorClip(
                            id = "clip-music-1",
                            label = "Clowning Around",
                            durationMs = 4_500L,
                            color = Color(0xFF736300),
                            trackType = TrackType.Music
                        )
                    )
                )
            )
        )
    )

    val state: StateFlow<EditorUiState> = _state

    fun onScrollChanged(scrollPx: Float) {
        _state.update { current ->
            val currentTimeMs = ((scrollPx / current.pxPerSecond) * 1000f).roundToInt().toLong()
            current.copy(scrollOffsetPx = scrollPx, currentTimeMs = currentTimeMs.coerceAtLeast(0L))
        }
    }

    fun onClipSelected(clipId: String) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    fun onClipDragged(clipId: String, deltaPx: Float) {
        _state.update { current ->
            val updatedTracks = current.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(offsetPx = clip.offsetPx + deltaPx)
                    } else {
                        clip
                    }
                }
                track.copy(clips = updatedClips)
            }
            current.copy(tracks = updatedTracks)
        }
    }
}

@Composable
fun EditorTimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorTimelineViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value.toFloat() }
            .collect { scrollPx -> viewModel.onScrollChanged(scrollPx) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TimelineTopArea(
            tracks = state.tracks,
            selectedClipId = state.selectedClipId,
            pxPerSecond = state.pxPerSecond,
            scrollState = scrollState,
            onClipSelected = viewModel::onClipSelected,
            onClipDragged = viewModel::onClipDragged
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AddButton(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            )
        }
        BottomToolbar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        )
    }
}

@Composable
fun TimelineTopArea(
    tracks: List<EditorTrack>,
    selectedClipId: String?,
    pxPerSecond: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    onClipSelected: (String) -> Unit,
    onClipDragged: (String, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFF0E0F12))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tracks.forEach { track ->
                TrackLane(
                    track = track,
                    selectedClipId = selectedClipId,
                    pxPerSecond = pxPerSecond,
                    scrollState = scrollState,
                    onClipSelected = onClipSelected,
                    onClipDragged = onClipDragged
                )
            }
        }
        PlayheadOverlay(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun TrackLane(
    track: EditorTrack,
    selectedClipId: String?,
    pxPerSecond: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    onClipSelected: (String) -> Unit,
    onClipDragged: (String, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            track.clips.forEach { clip ->
                ClipItem(
                    clip = clip,
                    selected = clip.id == selectedClipId,
                    width = (clip.durationMs / 1000f * pxPerSecond).dp,
                    onClipSelected = onClipSelected,
                    onClipDragged = onClipDragged
                )
            }
        }
    }
}

@Composable
fun ClipItem(
    clip: EditorClip,
    selected: Boolean,
    width: Dp,
    onClipSelected: (String) -> Unit,
    onClipDragged: (String, Float) -> Unit
) {
    var dragOffset by remember(clip.id) { mutableStateOf(clip.offsetPx) }
    LaunchedEffect(clip.offsetPx) {
        dragOffset = clip.offsetPx
    }
    val borderColor = if (selected) Color.White else Color.Transparent
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(width.coerceAtLeast(120.dp))
            .height(48.dp)
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .clip(shape)
            .background(clip.color)
            .border(width = 2.dp, color = borderColor, shape = shape)
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragEnd = { dragOffset = clip.offsetPx },
                    onDragCancel = { dragOffset = clip.offsetPx },
                    onDragStart = { onClipSelected(clip.id) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                        onClipDragged(clip.id, dragAmount.x)
                    }
                )
            }
            .padding(8.dp)
    ) {
        if (clip.trackType == TrackType.Video) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E61A8))
                    )
                }
            }
        }
        Text(
            text = clip.label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
fun PlayheadOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(2.dp)
            .background(Color.White)
    )
}

@Composable
fun BottomToolbar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarButton(icon = Icons.Filled.TextFields, label = "Texto")
        ToolbarButton(icon = Icons.Filled.StickyNote2, label = "Stickers")
        ToolbarButton(icon = Icons.Filled.Brush, label = "Dibujar")
        ToolbarButton(icon = Icons.Filled.LibraryMusic, label = "Música")
    }
}

@Composable
fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun AddButton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFECECEC)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Añadir",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
