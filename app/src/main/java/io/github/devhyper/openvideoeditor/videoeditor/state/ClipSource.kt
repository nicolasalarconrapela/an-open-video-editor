package io.github.devhyper.openvideoeditor.videoeditor.state

import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineClipType

data class ClipSource(
    val id: String,
    val durationMs: Long,
    val label: String,
    val type: TimelineClipType,
    val mediaUri: String,
    val sourceStartMs: Long = 0L,
    val timelineStartMs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val layerIndex: Int = 0,
)
