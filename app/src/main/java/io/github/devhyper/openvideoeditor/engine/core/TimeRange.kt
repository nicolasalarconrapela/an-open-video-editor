package io.github.devhyper.openvideoeditor.engine.core

/**
 * Represents a range of time in microseconds.
 * Closed-open interval: [start, end)
 */
data class TimeRange(
    val startUs: Long,
    val durationUs: Long
) {
    val endUs: Long get() = startUs + durationUs

    fun contains(timeUs: Long): Boolean = timeUs in startUs until endUs

    fun overlaps(other: TimeRange): Boolean {
        return startUs < other.endUs && other.startUs < endUs
    }

    /**
     * Clamps a time to this range.
     */
    fun clamp(timeUs: Long): Long {
        return timeUs.coerceIn(startUs, endUs - 1)
    }
}
