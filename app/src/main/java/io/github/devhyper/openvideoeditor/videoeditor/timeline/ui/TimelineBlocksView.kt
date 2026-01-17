package io.github.devhyper.openvideoeditor.videoeditor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.videoeditor.state.TimelineBlock

@Composable
fun TimelineBlocksView(
    blocks: List<TimelineBlock>,
    selectedId: String?,
    onSelect: (TimelineBlock) -> Unit,
    modifier: Modifier = Modifier,
    pixelsPerSecond: Float = 80f
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blocks, key = { it.id }) { block ->
            // Calculate width in px, then convert to dp
            val widthPx = (block.durationMs / 1000f) * pixelsPerSecond
            val widthDp = with(density) { widthPx.toDp() }.coerceAtLeast(72.dp)
            
            val isSelected = block.id == selectedId
            val (icon, label) = blockIconAndLabel(block)

            val electricBlue = Color(0xFF2979FF)
            val darkGrey = Color(0xFF1E1E1E)

            Box(
                modifier = Modifier
                    .width(widthDp)
                    .height(72.dp)
                    .clickable { onSelect(block) }
                    .background(
                        color = if (isSelected) {
                            electricBlue
                        } else {
                            darkGrey
                        }, shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun blockIconAndLabel(block: TimelineBlock): Pair<ImageVector, String> = when (block) {
    is TimelineBlock.Intro -> Icons.Filled.Movie to block.label
    is TimelineBlock.Roll -> Icons.Filled.Star to block.label
    is TimelineBlock.Outro -> Icons.Filled.Movie to block.label
    is TimelineBlock.LowerThird -> Icons.Filled.Title to block.label
    is TimelineBlock.Logo -> Icons.Filled.Widgets to block.label
    is TimelineBlock.AudioBed -> Icons.Filled.MusicNote to block.label
}
