package io.github.devhyper.openvideoeditor.engine.model

import io.github.devhyper.openvideoeditor.engine.core.TimeRange
import java.util.UUID

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    EFFECT
}

/**
 * Represents an item in the timeline.
 */
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val mediaType: MediaType,
    val mediaPath: String, // URI or File path
    
    /** Where this clip is placed in the Timeline */
    val timelineStartUs: Long,
    
    /** Mapping logic for Source content */
    val timeMap: TimeMap,
    
    /** Layer priority (higher stays on top) */
    val zIndex: Int = 0
) : Comparable<Clip> {
    
    // Properties
    val opacity = PropertyTrack(1.0f)
    val volume = PropertyTrack(1.0f)
    val scale = PropertyTrack(1.0f)
    val rotation = PropertyTrack(0.0f)

    /**
     * Efficiently cached TimeRange
     */
    val range: TimeRange
        get() = TimeRange(timelineStartUs, timeMap.timelineDurationUs)

    fun resolveSourceTime(timelineTimeUs: Long): Long {
        if (!range.contains(timelineTimeUs)) return -1
        val relative = timelineTimeUs - timelineStartUs
        return timeMap.mapTimelineToSource(relative)
    }

    override fun compareTo(other: Clip): Int {
        val startDiff = this.timelineStartUs.compareTo(other.timelineStartUs)
        if (startDiff != 0) return startDiff
        return this.zIndex.compareTo(other.zIndex)
    }
}
