package io.github.devhyper.openvideoeditor.videoeditor

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore
import java.io.File
import java.util.UUID

object ExportWorkManager {
    const val EXPORT_WORK_NAME = "video_export"
    private const val EXPORT_SNAPSHOT_DIR = "export_snapshots"

    suspend fun enqueueExport(
        context: Context,
        exportSettings: ExportSettings,
        projectData: ProjectData,
    ): UUID {
        val snapshotFile = createSnapshotFile(context)
        projectData.writeToFile(snapshotFile)

        val data = workDataOf(
            ExportWorker.KEY_PROJECT_SNAPSHOT_PATH to snapshotFile.absolutePath,
            ExportWorker.KEY_OUTPUT_PATH to exportSettings.outputPath,
            ExportWorker.KEY_EXPORT_AUDIO to exportSettings.exportAudio,
            ExportWorker.KEY_EXPORT_VIDEO to exportSettings.exportVideo,
            ExportWorker.KEY_HDR_MODE to exportSettings.hdrMode,
            ExportWorker.KEY_AUDIO_MIME_TYPE to exportSettings.audioMimeType,
            ExportWorker.KEY_VIDEO_MIME_TYPE to exportSettings.videoMimeType,
            ExportWorker.KEY_FRAMERATE to exportSettings.framerate,
            ExportWorker.KEY_SPEED to exportSettings.speed,
            ExportWorker.KEY_LOSSLESS_CUT to exportSettings.losslessCut,
            ExportWorker.KEY_SEGMENTED_MIN_DURATION_MS to exportSettings.segmentedExportMinDurationMs,
            ExportWorker.KEY_SEGMENTED_MIN_SIZE_BYTES to exportSettings.segmentedExportMinSizeBytes,
            ExportWorker.KEY_SEGMENT_DURATION_MS to exportSettings.segmentDurationMs,
        )

        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(data)
            .addTag(EXPORT_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPORT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )

        val dataStore = SettingsDataStore(context)
        dataStore.setExportState(ExportState.RUNNING)
        dataStore.setExportProgress(0F)
        dataStore.setExportOutputPath(exportSettings.outputPath)
        dataStore.setExportWorkId(request.id.toString())
        dataStore.setExportError(null)

        return request.id
    }

    suspend fun cancelExport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(EXPORT_WORK_NAME)
        val dataStore = SettingsDataStore(context)
        dataStore.setExportState(ExportState.CANCELLED)
        dataStore.setExportProgress(0F)
        dataStore.setExportError(null)
        dataStore.setExportWorkId(null)
    }

    suspend fun clearExportState(context: Context) {
        val dataStore = SettingsDataStore(context)
        dataStore.clearExportState()
    }

    private fun createSnapshotFile(context: Context): File {
        val directory = File(context.filesDir, EXPORT_SNAPSHOT_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "export_${System.currentTimeMillis()}.dat")
    }
}
