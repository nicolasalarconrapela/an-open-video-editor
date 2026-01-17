package io.github.devhyper.openvideoeditor.engine.logic

import io.github.devhyper.openvideoeditor.engine.model.Clip
import io.github.devhyper.openvideoeditor.engine.model.InterpolationType
import io.github.devhyper.openvideoeditor.engine.track.Track

object TimelineOps {

    /**
     * Creates a crossfade transition between two overlapping clips by automating their opacity.
     * @param clipA The incoming/outgoing clip (must end where B starts or overlap)
     * @param clipB The other clip
     * @param durationUs Duration of the overlap region to fade
     */
    fun applyCrossfade(clipA: Clip, clipB: Clip, durationUs: Long) {
        // Determine the overlap window
        val startOverlap = maxOf(clipA.timelineStartUs, clipB.timelineStartUs)
        val endOverlap = minOf(clipA.range.endUs, clipB.range.endUs)
        
        val validDuration = endOverlap - startOverlap
        if (validDuration <= 0) return // No overlap
        
        // Clip A: Fade Out (1 -> 0)
        clipA.opacity.addKeyframe(startOverlap - clipA.timelineStartUs, 1.0f)
        clipA.opacity.addKeyframe(endOverlap - clipA.timelineStartUs, 0.0f)

        // Clip B: Fade In (0 -> 1)
        clipB.opacity.addKeyframe(startOverlap - clipB.timelineStartUs, 0.0f)
        clipB.opacity.addKeyframe(endOverlap - clipB.timelineStartUs, 1.0f)
    }
    
    private fun maxOf(a: Long, b: Long) = if (a >= b) a else b
    private fun minOf(a: Long, b: Long) = if (a <= b) a else b
}
