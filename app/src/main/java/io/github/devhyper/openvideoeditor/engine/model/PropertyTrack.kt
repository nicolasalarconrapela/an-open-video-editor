package io.github.devhyper.openvideoeditor.engine.model

import io.github.devhyper.openvideoeditor.engine.core.TimeRange

enum class InterpolationType {
    LINEAR,
    BEZIER,
    STEP
}

data class Keyframe(
    val timeUs: Long, // Relative to Clip Start
    val value: Float,
    val interpolation: InterpolationType = InterpolationType.LINEAR
)

class PropertyTrack(
    val initialValue: Float
) {
    private val keyframes = ArrayList<Keyframe>()

    fun addKeyframe(timeUs: Long, value: Float, interpolation: InterpolationType = InterpolationType.LINEAR) {
        keyframes.add(Keyframe(timeUs, value, interpolation))
        keyframes.sortBy { it.timeUs }
    }

    fun getValueAt(timeUs: Long): Float {
        if (keyframes.isEmpty()) return initialValue

        // Before first keyframe
        if (timeUs <= keyframes.first().timeUs) return keyframes.first().value
        // After last keyframe
        if (timeUs >= keyframes.last().timeUs) return keyframes.last().value

        // Binary search for the segment
        // We find the first keyframe that is > timeUs, then looking back one step gives us the start
        val index = keyframes.binarySearch { it.timeUs.compareTo(timeUs) }
        
        if (index >= 0) {
             // Exact match
             return keyframes[index].value
        }
        
        // binarySearch returns (-(insertion point) - 1)
        // insertion point is the index of the first element greater than the key
        val insertionPoint = -(index + 1)
        val p1 = keyframes[insertionPoint - 1]
        val p2 = keyframes[insertionPoint]

        val t = (timeUs - p1.timeUs).toFloat() / (p2.timeUs - p1.timeUs)
        
        return when (p1.interpolation) {
            InterpolationType.STEP -> p1.value
            InterpolationType.LINEAR -> p1.value + (p2.value - p1.value) * t
            InterpolationType.BEZIER -> {
                // TODO: Implement proper Bezier. Fallback to EaseInOut for now.
                val easedT = t * t * (3 - 2 * t)
                p1.value + (p2.value - p1.value) * easedT
            }
        }
    }
}
