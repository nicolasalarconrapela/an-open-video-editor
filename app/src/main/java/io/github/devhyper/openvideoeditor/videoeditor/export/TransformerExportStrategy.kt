package io.github.devhyper.openvideoeditor.videoeditor.export

import io.github.devhyper.openvideoeditor.videoeditor.ExportManager
import io.github.devhyper.openvideoeditor.videoeditor.ExportSettings

class TransformerExportStrategy(
    private val manager: ExportManager
) : ExportStrategy {
    override fun export(
        exportSettings: ExportSettings,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        manager.exportWithTransformer(exportSettings, onCompleted, onError)
    }
}
