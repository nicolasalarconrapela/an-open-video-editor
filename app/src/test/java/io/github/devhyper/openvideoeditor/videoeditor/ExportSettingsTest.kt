package io.github.devhyper.openvideoeditor.videoeditor

import androidx.media3.transformer.Composition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsTest {
    @Test
    fun setMediaToExportString_updatesAudioVideoFlags() {
        val settings = ExportSettings()

        settings.setMediaToExportString("Video and Audio")
        assertTrue(settings.exportVideo)
        assertTrue(settings.exportAudio)

        settings.setMediaToExportString("Video only")
        assertTrue(settings.exportVideo)
        assertFalse(settings.exportAudio)

        settings.setMediaToExportString("Audio only")
        assertFalse(settings.exportVideo)
        assertTrue(settings.exportAudio)
    }

    @Test
    fun setHdrModeString_updatesHdrMode() {
        val settings = ExportSettings()

        settings.setHdrModeString("Keep HDR")
        assertEquals(Composition.HDR_MODE_KEEP_HDR, settings.hdrMode)

        settings.setHdrModeString("HDR as SDR")
        assertEquals(Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR, settings.hdrMode)

        settings.setHdrModeString("HDR to SDR (Mediacodec)")
        assertEquals(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC, settings.hdrMode)

        settings.setHdrModeString("HDR to SDR (OpenGL)")
        assertEquals(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL, settings.hdrMode)
    }

    @Test
    fun setAudioMimeTypeString_handlesOriginal() {
        val settings = ExportSettings()

        settings.setAudioMimeTypeString("Original")
        assertNull(settings.audioMimeType)

        settings.setAudioMimeTypeString("audio/test")
        assertEquals("audio/test", settings.audioMimeType)
    }

    @Test
    fun setVideoMimeTypeString_handlesOriginal() {
        val settings = ExportSettings()

        settings.setVideoMimeTypeString("Original")
        assertNull(settings.videoMimeType)

        settings.setVideoMimeTypeString("video/test")
        assertEquals("video/test", settings.videoMimeType)
    }

    @Test
    fun buildSegmentRanges_handlesEmptyOrInvalidValues() {
        assertTrue(buildSegmentRanges(0L, 1000L).isEmpty())
        assertTrue(buildSegmentRanges(1000L, 0L).isEmpty())
        assertTrue(buildSegmentRanges(-1L, 1000L).isEmpty())
    }

    @Test
    fun buildSegmentRanges_splitsIntoSegments() {
        val segments = buildSegmentRanges(12_000L, 5_000L)

        assertEquals(3, segments.size)
        assertEquals(SegmentRange(0, 0L, 5_000L), segments[0])
        assertEquals(SegmentRange(1, 5_000L, 5_000L), segments[1])
        assertEquals(SegmentRange(2, 10_000L, 2_000L), segments[2])
    }
}
