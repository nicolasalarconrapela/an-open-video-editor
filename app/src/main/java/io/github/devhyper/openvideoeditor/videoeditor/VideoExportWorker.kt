package io.github.devhyper.openvideoeditor.videoeditor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import kotlin.coroutines.resume
import io.github.devhyper.openvideoeditor.R
import kotlinx.coroutines.delay
import android.content.pm.ServiceInfo
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class VideoExportWorker(val context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_PROJECT_DATA_PATH = "projectDataPath"
        const val KEY_EXPORT_SETTINGS_PATH = "exportSettingsPath"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS = "progress"
    }

    override suspend fun doWork(): Result {
        val projectDataPath = inputData.getString(KEY_PROJECT_DATA_PATH) ?: return Result.failure()
        val exportSettingsPath = inputData.getString(KEY_EXPORT_SETTINGS_PATH) ?: return Result.failure()

        val projectData = readObject<ProjectData>(projectDataPath)
        val exportSettings = readObject<ExportSettings>(exportSettingsPath)

        if (projectData == null || exportSettings == null) return Result.failure()

        // Cleanup temp files if desired? Or let system handle cache.
        // We will leave them for now.

        setForeground(createForegroundInfo(0f))

        val exportManager = ExportManager(context, projectData)

        return try {
            coroutineScope {
                // Launch progress poller
                val progressJob = launch {
                    while (isActive) {
                        val progress = exportManager.getProgress()
                        if (progress >= 0) {
                            setForeground(createForegroundInfo(progress))
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                        }
                        delay(500)
                    }
                }

                suspendCancellableCoroutine<Result> { continuation ->
                    exportManager.export(
                        exportSettings,
                        onCompleted = {
                            progressJob.cancel()
                            if (continuation.isActive) {
                                continuation.resume(Result.success())
                            }
                        },
                        onError = { error ->
                            progressJob.cancel()
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(workDataOf(KEY_ERROR to error)))
                            }
                        }
                    )
                    
                    continuation.invokeOnCancellation {
                        exportManager.cancel()
                        progressJob.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to e.toString()))
        }
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val channelId = "export_channel"
        val title = context.getString(R.string.exporting)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Export", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("${(progress * 100).toInt()}%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)
            // Add Cancel action?
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        
        if (Build.VERSION.SDK_INT >= 29) { // Build.VERSION_CODES.Q
             var type = 0
             if(Build.VERSION.SDK_INT >= 34) {
                 type = 8192
             } else {
                 type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
             } 
             // Actually, if we don't declare data sync in manifest for < 34, we might crash if we use it?
             // Android 14 requires explicit type. < 14 matches manifest or default.
             // Best to use specific type if we declared it.
             if (Build.VERSION.SDK_INT >= 34) {
                 // FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING = 8192
                  return ForegroundInfo(1, notification, 8192)
             }
        }
        return ForegroundInfo(1, notification)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readObject(path: String): T? {
        try {
            FileInputStream(File(path)).use { fileIn ->
                ObjectInputStream(fileIn).use { objectIn ->
                    return objectIn.readObject() as T
                }
            }
        } catch (e: Exception) {
            return null
        }
    }
}
