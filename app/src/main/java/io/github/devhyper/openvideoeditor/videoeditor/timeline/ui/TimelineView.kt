package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class TimelineUiClip(
    val id: String,
    val durationMs: Long,
    val label: String
)

@Composable
fun TimelineView(
    clips: List<TimelineUiClip>,
    pixelsPerSecond: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val clipMinWidthDp = 48.dp
    val clipHeight = 56.dp

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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(clips, key = { it.id }) { clip ->
                val widthPx = (clip.durationMs / 1000f) * pixelsPerSecond
                val widthDp = with(density) { widthPx.toDp() }
                val clipWidth = max(widthDp.value, clipMinWidthDp.value).dp
                Box(
                    modifier = Modifier
                        .width(clipWidth)
                        .height(clipHeight)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = clip.label,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
    }
}
