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

                    // Use enqueueUniqueWork with KEEP policy. 
                    // If a "video_export" is already RUNNING or ENQUEUED, this new request is IGNORED directly.
                    // This perfectly handles the double-tap resume case. 
                    // However, we must ensure the old worker is fully DEAD before this (which it should be if paused/cancelled).
                    // If we use KEEP, and the old "video_export" is still in CANCELLED state... wait.
                    // If previous work was cancelled, it's finished.
                    // KEEP: "If there is existing pending (uncompleted) work with the same unique name, do nothing."
                    // REPLACE: "If there is existing pending (uncompleted) work with the same unique name, cancel and delete it."
                    // APPEND: "If there is existing pending (uncompleted) work with the same unique name, append."
                    // Since "PAUSE" cancels the work, the previous work should be marked as CANCELLED (completed).
                    // So "KEEP" would see no *pending* work and execute this one.
                    // BUT: If the user taps resume twice very fast:
                    // 1st tap: Enqueues. Work is now RUNNING.
                    // 2nd tap: Enqueues. Work is RUNNING. "KEEP" sees it running -> DOES NOTHING.
                    // Result: Only ONE resume worker runs. CORRECT!
                    workManager.enqueueUniqueWork(
                        "video_export_unique_resume", // Use a unique name for the resume operation specifically
                        androidx.work.ExistingWorkPolicy.KEEP,
                        request
                    )
                    // The worker will replace the "Paused" notification with its foreground one
                }
            }
            "CANCEL_PAUSED" -> {
                // Reset pause state
                VideoExportWorker.setPaused(false)
                
                val projectDataPath = intent.getStringExtra("projectDataPath")
                val exportSettingsPath = intent.getStringExtra("exportSettingsPath")
                
                // Cleanup - READ settings FIRST before deleting anything
                var outputPathForCleanup: String? = null
                
                // Step 1: Read the export settings to get outputPath BEFORE deleting
                if (exportSettingsPath != null) {
                    try {
                        java.io.FileInputStream(File(exportSettingsPath)).use { fileIn ->
                            java.io.ObjectInputStream(fileIn).use { objectIn ->
                                val settings = objectIn.readObject() as? ExportSettings
                                outputPathForCleanup = settings?.outputPath
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ExportDebug", "Failed to read export settings for cleanup", e)
                    }
                }
                
                // Step 2: Cleanup segments if we got the outputPath
                if (outputPathForCleanup != null) {
                    try {
                        ExportManager.cleanupSegmentsStatic(context, outputPathForCleanup!!)
                    } catch (e: Exception) {
                        android.util.Log.e("ExportDebug", "Failed to cleanup segments", e)
                    }
                }
                
                // Step 3: NOW delete the temp files
                try {
                    if (projectDataPath != null) File(projectDataPath).delete()
                    if (exportSettingsPath != null) File(exportSettingsPath).delete()
                } catch (e: Exception) {
                    android.util.Log.e("ExportDebug", "Failed to delete temp files", e)
                }
                
                // Remove notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(VideoExportWorker.NOTIFICATION_ID)
            }
        }
    }
}
