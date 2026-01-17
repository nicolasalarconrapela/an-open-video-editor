package io.github.devhyper.openvideoeditor.engine.track

import io.github.devhyper.openvideoeditor.engine.core.TimeRange
import io.github.devhyper.openvideoeditor.engine.model.Clip
import java.util.Collections

class Track(
    val id: String,
    val type: io.github.devhyper.openvideoeditor.engine.model.MediaType
) {
    // Sorted list for O(log N) access
    private val clips = ArrayList<Clip>()
    private var isDirty = false

    fun addClip(clip: Clip) {
        clips.add(clip)
        isDirty = true
    }

    fun removeClip(clipId: String): Boolean {
        val removed = clips.removeIf { it.id == clipId }
        if (removed) isDirty = true
        return removed
    }

    private fun ensureSorted() {
        if (isDirty) {
            clips.sort()
            isDirty = false
        }
    }

    /**
     * Efficiently finds clips active at time T using Binary Search.
     * Returns a list because multiple clips might overlap (transitions, effects).
     */
    fun getClipsAt(timeUs: Long): List<Clip> {
        ensureSorted()
        if (clips.isEmpty()) return emptyList()

        val results = ArrayList<Clip>()
        
        // Binary search to find a "potential" starting clip.
        // Since clips have duration, a clip starting BEFORE T might extend OVER T.
        // We find the insertion point for T.
        // Any clip that contains T must start <= T.
        
        var idx = clips.binarySearch {
            // We search for a clip starting exactly at T.
            // If not found, binarySearch returns insertion point.
            it.timelineStartUs.compareTo(timeUs)
        }

        // If exact match found (start == T), it's a candidate
        if (idx >= 0) {
            // Check overlaps forward
            checkForward(idx, timeUs, results)
            // Check overlaps backward (in case a previous clip is very long)
            checkBackward(idx - 1, timeUs, results)
        } else {
            // Insertion point is -(idx + 1). 
            // This is the point where the clip starting AFTER T would be.
            // So we check from insertionPoint - 1 downwards.
            val insertionPoint = -(idx + 1)
            checkBackward(insertionPoint - 1, timeUs, results)
             // Also check forward just in case of zero-duration logical clips or exact matches logic quirks
            checkForward(insertionPoint, timeUs, results)
        }
        
        return results.sortedBy { it.zIndex }
    }

    private fun checkBackward(startIndex: Int, timeUs: Long, out: MutableList<Clip>) {
        for (i in startIndex downTo 0) {
            val clip = clips[i]
            if (clip.range.contains(timeUs)) {
                out.add(clip)
            } else {
                // Optimization: If clip ends well before T, we can stop?
                // Not necessarily if clips are not sorted by end time (they are sorted by start).
                // However, usually tracks don't have nested overlaps of massive magnitude variance.
                // But to be safe in a generic engine, we iterate.
                // For valid 'NLE' tracks (single layer), clips DON'T overlap except for transitions.
                // If this is a multitrack container, overlaps happen.
                
                // Heuristic: If we are extremely far back, we might stop. 
                // But for now, correctness > partial generic optimization. 
            }
            // Optimization: If clip.start > timeUs (impossible in checkBackward), break.
        }
    }

    private fun checkForward(startIndex: Int, timeUs: Long, out: MutableList<Clip>) {
        for (i in startIndex until clips.size) {
            val clip = clips[i]
            if (clip.timelineStartUs > timeUs) {
                // Since clips are sorted by start, any subsequent clip starts after T
                // and thus cannot contain T (unless duration is negative logic? no).
                break
            }
            if (clip.range.contains(timeUs)) {
                out.add(clip)
            }
        }
    }

    /**
     * Find clips intersecting a range (for rendering thumbnails in UI).
     */
    fun getClipsInRange(range: TimeRange): List<Clip> {
        ensureSorted()
        // Determine start index via binary search
        // Check all clips until start > range.end
        // Filter by intersection
        // TODO: specific efficient range query
        return clips.filter { it.range.overlaps(range) }
    }
}
