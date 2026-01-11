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
                android.util.Log.d("ExportDebug", "⏸️ PAUSE action received")
                val workerId = intent.getStringExtra("workerId")
                android.util.Log.d("ExportDebug", "⏸️ workerId from intent: $workerId")
                
                VideoExportWorker.setPaused(true)
                // Force stop FFmpeg immediately to free resources without waiting for Worker cancellation
                com.arthenica.ffmpegkit.FFmpegKit.cancel()
                
                if (workerId != null) {
                    android.util.Log.d("ExportDebug", "⏸️ Cancelling worker by ID: $workerId")
                    workManager.cancelWorkById(UUID.fromString(workerId))
                    android.util.Log.d("ExportDebug", "⏸️ Worker cancellation requested")
                } else {
                    android.util.Log.e("ExportDebug", "❌ PAUSE failed: workerId is null! Cannot cancel worker.")
                    // Try to cancel by tag as fallback
                    android.util.Log.d("ExportDebug", "⏸️ Attempting to cancel by tag 'video_export'")
                    workManager.cancelAllWorkByTag("video_export")
                }
            }
            "RESUME" -> {
                android.util.Log.d("ExportDebug", "▶️ RESUME action received")
                VideoExportWorker.setPaused(false)
                
                val projectDataPath = intent.getStringExtra("projectDataPath")
                val exportSettingsPath = intent.getStringExtra("exportSettingsPath")
                
                android.util.Log.d("ExportDebug", "▶️ Resume paths - projectData: $projectDataPath, settings: $exportSettingsPath")
                
                if (projectDataPath != null && exportSettingsPath != null) {
                    // Verify files exist before attempting resume
                    val projectDataFile = java.io.File(projectDataPath)
                    val settingsFile = java.io.File(exportSettingsPath)
                    
                    if (!projectDataFile.exists()) {
                        android.util.Log.e("ExportDebug", "❌ Project data file not found: $projectDataPath")
                        return
                    }
                    if (!settingsFile.exists()) {
                        android.util.Log.e("ExportDebug", "❌ Settings file not found: $exportSettingsPath")
                        return
                    }
                    
                    android.util.Log.d("ExportDebug", "✅ Resume files exist, creating worker...")
                    
                    val inputData = workDataOf(
                        VideoExportWorker.KEY_PROJECT_DATA_PATH to projectDataPath,
                        VideoExportWorker.KEY_EXPORT_SETTINGS_PATH to exportSettingsPath,
                        "isResume" to true
                    )
                    val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
                        .setInputData(inputData)
                        .addTag("video_export")
                        .build()

                    // Use enqueueUniqueWork with REPLACE to ensure the resume worker starts
                    // REPLACE will cancel any existing work with this name and start fresh
                    android.util.Log.d("ExportDebug", "📤 Enqueuing resume worker with ID: ${request.id}")
                    workManager.enqueueUniqueWork(
                        "video_export_unique_resume",
                        androidx.work.ExistingWorkPolicy.REPLACE, // Changed from KEEP to REPLACE
                        request
                    )
                    android.util.Log.d("ExportDebug", "✅ Resume worker enqueued successfully")
                } else {
                    android.util.Log.e("ExportDebug", "❌ Resume failed: missing paths in intent")
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
