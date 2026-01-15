package io.github.devhyper.openvideoeditor.videoeditor.export

import io.github.devhyper.openvideoeditor.videoeditor.ExportManager
import io.github.devhyper.openvideoeditor.videoeditor.ExportSettings
import io.github.devhyper.openvideoeditor.videoeditor.buildSegmentRanges

class SegmentedExportStrategy(
    private val manager: ExportManager,
    private val onFfmpegError: () -> Unit
) : ExportStrategy {
    override fun export(
        exportSettings: ExportSettings,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val context = manager.getContext()
        val totalDurationMs = manager.getExportDurationMs(context)
        val state = manager.resolveSegmentExportState(context, exportSettings, totalDurationMs)
        val segments = buildSegmentRanges(totalDurationMs, exportSettings.segmentDurationMs)
        val filteredSegments = segments.filter { it.durationMs > 0 }
        if (filteredSegments.isEmpty()) {
            onFfmpegError()
            return
        }
        if (manager.canUseLosslessSegmentCopy(exportSettings)) {
            manager.startSegmentedExportWithFfmpeg(
                context,
                manager.getProjectUri(),
                onFfmpegError,
                filteredSegments,
                state,
                onCompleted
            )
        } else {
            manager.startSegmentedExportWithTransformer(
                context,
                exportSettings,
                onError,
                onFfmpegError,
                filteredSegments,
                state,
                onCompleted
            )
        }
    }
}
