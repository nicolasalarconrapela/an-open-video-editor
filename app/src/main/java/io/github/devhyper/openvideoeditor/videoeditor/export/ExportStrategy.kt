package io.github.devhyper.openvideoeditor.videoeditor.export

import io.github.devhyper.openvideoeditor.videoeditor.ExportSettings

interface ExportStrategy {
    fun export(
        exportSettings: ExportSettings,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    )
}
