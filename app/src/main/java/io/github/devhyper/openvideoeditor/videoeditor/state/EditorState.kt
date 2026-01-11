package io.github.devhyper.openvideoeditor.videoeditor.state

import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineClipType

enum class EditorMode {
    BLOCKS,
    PRECISION
}

sealed class TimelineBlock(
    open val id: String,
    open val durationMs: Long,
    open val label: String,
    open val clipRefs: List<ClipRef> = emptyList()
) {
    data class Intro(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)

    data class Roll(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)

    data class Outro(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)

    data class LowerThird(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)

    data class Logo(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)

    data class AudioBed(
        override val id: String,
        override val durationMs: Long,
        override val label: String,
        override val clipRefs: List<ClipRef> = emptyList()
    ) : TimelineBlock(id, durationMs, label, clipRefs)
}

data class ClipRef(
    val id: String,
    val type: TimelineClipType,
    val durationMs: Long,
    val label: String
)

data class ClipUi(
    val id: String,
    val type: TimelineClipType,
    val durationMs: Long,
    val label: String,
    val isPlaceholder: Boolean
)

data class EditorState(
    val currentTimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedBlockId: String? = null,
    val mode: EditorMode = EditorMode.BLOCKS,
    val zoomLevel: Float = 1f,
    val blocks: List<TimelineBlock> = emptyList()
) {
    fun expandedClips(): List<ClipUi> {
        val block = blocks.firstOrNull { it.id == selectedBlockId }
        val clips = block?.clipRefs.orEmpty()
        if (clips.isEmpty()) {
            return listOf(
                ClipUi(
                    id = "placeholder-${selectedBlockId ?: "empty"}",
                    type = TimelineClipType.Video,
                    durationMs = 1_000L,
                    label = "Placeholder",
                    isPlaceholder = true
                )
            )
        }
        return clips.map { clip ->
            ClipUi(
                id = clip.id,
                type = clip.type,
                durationMs = clip.durationMs,
                label = clip.label,
                isPlaceholder = false
            )
        }
    }
}
