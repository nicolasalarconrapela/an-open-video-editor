package io.github.devhyper.openvideoeditor.engine.core

import io.github.devhyper.openvideoeditor.engine.model.Clip
import io.github.devhyper.openvideoeditor.engine.track.Track
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Engine Root.
 */
class Timeline {
    private val tracks = CopyOnWriteArrayList<Track>()

    fun addTrack(track: Track) {
        tracks.add(track)
    }

    /**
     * Resolutions.
     * Returns the "State of the World" at time T.
     */
    fun resolve(timeUs: Long): TimelineState {
        val activeClips = ArrayList<ActiveClip>()
        
        tracks.forEach { track ->
            val clips = track.getClipsAt(timeUs)
            clips.forEach { clip ->
                // Calculate current properties based on keyframes
                val relativeTime = timeUs - clip.timelineStartUs
                val opacity = clip.opacity.getValueAt(relativeTime)
                val volume = clip.volume.getValueAt(relativeTime)
                
                // Calculate Source Time
                val sourceTime = clip.resolveSourceTime(timeUs)
                
                activeClips.add(
                    ActiveClip(
                        clip = clip,
                        currentSourceTimeUs = sourceTime,
                        currentOpacity = opacity,
                        currentVolume = volume
                    )
                )
            }
        }
        
        // Sort by Global Z-Index (Track order + Clip Z) if needed.
        // For now trusting Track Add Order as basic layer order.
        return TimelineState(activeClips)
    }
}

/**
 * Snapshot of the engine at a specific frame.
 * Ready for Renderer to consume.
 */
data class TimelineState(
    val activeClips: List<ActiveClip>
)

data class ActiveClip(
    val clip: Clip,
    val currentSourceTimeUs: Long,
    val currentOpacity: Float,
    val currentVolume: Float
)
