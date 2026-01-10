package io.github.devhyper.openvideoeditor.videoeditor

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.UUID

class ExportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val workManager = WorkManager.getInstance(context)
        
        android.util.Log.d("ExportDebug", "📩 ExportActionReceiver received action: ${intent?.action}")

        when (intent?.action) {
            "PAUSE" -> {
                val workerId = intent.getStringExtra("workerId")
                VideoExportWorker.setPaused(true)
                // Force stop FFmpeg immediately to free resources without waiting for Worker cancellation
                com.arthenica.ffmpegkit.FFmpegKit.cancel()
                if (workerId != null) {
                    workManager.cancelWorkById(UUID.fromString(workerId))
                }
            }
            "RESUME" -> {
                VideoExportWorker.setPaused(false)
                
                val projectDataPath = intent.getStringExtra("projectDataPath")
                val exportSettingsPath = intent.getStringExtra("exportSettingsPath")
                
                if (projectDataPath != null && exportSettingsPath != null) {
                    val inputData = workDataOf(
                        VideoExportWorker.KEY_PROJECT_DATA_PATH to projectDataPath,
                        VideoExportWorker.KEY_EXPORT_SETTINGS_PATH to exportSettingsPath,
                        "isResume" to true
                    )
                    val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
                        .setInputData(inputData)
                        .addTag("video_export")
                        .build()
                    workManager.enqueue(request)
                    // The worker will replace the "Paused" notification with its foreground one
                }
            }
            "CANCEL_PAUSED" -> {
                // Reset pause state
                VideoExportWorker.setPaused(false)
                
                val projectDataPath = intent.getStringExtra("projectDataPath")
                val exportSettingsPath = intent.getStringExtra("exportSettingsPath")
                
                // Cleanup
                try {
                    if (projectDataPath != null) File(projectDataPath).delete()
                    if (exportSettingsPath != null) {
                         // We need to read the settings to get the output path for cleanup
                        try {
                            java.io.FileInputStream(File(exportSettingsPath)).use { fileIn ->
                                java.io.ObjectInputStream(fileIn).use { objectIn ->
                                    val settings = objectIn.readObject() as? ExportSettings
                                    if (settings != null) {
                                         // Clean up segments!
                                         val dummyProjectData = ProjectData("", mutableListOf(), mutableListOf()) // Dummy just to access cleanup
                                         ExportManager(context, dummyProjectData).cleanupSegments(context, settings.outputPath)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        File(exportSettingsPath).delete()
                    }
                } catch (e: Exception) {}
                
                // Remove notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(VideoExportWorker.NOTIFICATION_ID)
            }
        }
    }
}
