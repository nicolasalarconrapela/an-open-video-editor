package io.github.devhyper.openvideoeditor.videoeditor.export

import io.github.devhyper.openvideoeditor.videoeditor.ExportManager
import io.github.devhyper.openvideoeditor.videoeditor.ExportSettings

class FfmpegLosslessStrategy(
    private val manager: ExportManager,
    private val onFfmpegError: () -> Unit
) : ExportStrategy {
    override fun export(
        exportSettings: ExportSettings,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trim = manager.getMergedTrim()
        if (trim == null) {
            onError("No trim available for lossless export.")
            return
        }
        manager.ffmpegLosslessCut(
            manager.getContext(),
            trim,
            exportSettings.outputPath,
            false,
            onFfmpegError,
            onCompleted
        )
    }
}
