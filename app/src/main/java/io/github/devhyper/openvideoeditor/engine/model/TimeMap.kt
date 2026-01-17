package io.github.devhyper.openvideoeditor.engine.model

import io.github.devhyper.openvideoeditor.engine.core.TimeRange
import kotlin.math.abs

/**
 * Handles mapping between Timeline Time (Global) and Source Time (Media).
 * Supports trimming, speed changes, and reverse playback.
 */
data class TimeMap(
    /** The start time of this clip in the Source media (e.g. video file) */
    val sourceInUs: Long,
    
    /** The duration of the clip in the Source media content. */
    val sourceDurationUs: Long,
    
    /** Speed multiplier. 1.0 = normal, 2.0 = 2x faster, -1.0 = reverse. */
    val speedMultiplier: Double = 1.0
) {
    /**
     * Calculated duration in the Timeline.
     * timelineDuration = sourceDuration / abs(speed)
     */
    val timelineDurationUs: Long
        get() = (sourceDurationUs / abs(speedMultiplier)).toLong()

    /**
     * Converts a Timeline time (relative to clip start) to Source time.
     * @param relativeTimelineUs Time elapsed since the start of the clip in the timeline.
     */
    fun mapTimelineToSource(relativeTimelineUs: Long): Long {
        // T_source = T_sourceIn + (T_timeline_rel * speed)
        // If speed is negative (reverse), logic usually implies playing from the END of the source segment back to start.
        // But typically sourceIn denotes the "start point in source file" for the beginning of the clip event.
        // If Reverse: We often define sourceIn as the start point, but play backwards? 
        // Standard NLE way: 
        // Normal: Stream [sourceIn -> sourceIn + sourceDuration]
        // Reverse: Stream [sourceIn + sourceDuration -> sourceIn]
        
        return if (speedMultiplier >= 0) {
            sourceInUs + (relativeTimelineUs * speedMultiplier).toLong()
        } else {
            // Reverse: relativeTime 0 maps to (sourceIn + sourceDuration)
            // relativeTime END maps to sourceIn
            val sourceEnd = sourceInUs + sourceDurationUs
            sourceEnd - (relativeTimelineUs * abs(speedMultiplier)).toLong()
        }
    }
}
