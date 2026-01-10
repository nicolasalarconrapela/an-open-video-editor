package io.github.devhyper.openvideoeditor.videoeditor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
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
        const val KEY_OUTPUT_PATH = "outputPath"
        const val NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result {
        val projectDataPath = inputData.getString(KEY_PROJECT_DATA_PATH) ?: return Result.failure()
        val exportSettingsPath = inputData.getString(KEY_EXPORT_SETTINGS_PATH) ?: return Result.failure()

        val projectData = readObject<ProjectData>(projectDataPath)
        val exportSettings = readObject<ExportSettings>(exportSettingsPath)

        if (projectData == null || exportSettings == null) return Result.failure()

        val outputPath = exportSettings.outputPath

        setForeground(createForegroundInfo(0f))
        startTimeMs = System.currentTimeMillis()

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
                            showCompletionNotification(outputPath)
                            if (continuation.isActive) {
                                continuation.resume(Result.success(workDataOf(KEY_OUTPUT_PATH to outputPath)))
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

    private var startTimeMs: Long = 0L
    
    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val channelId = "export_channel"
        val title = context.getString(R.string.exporting)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Export", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Calculate remaining time estimate
        val progressInt = (progress * 100).toInt()
        var timeText = "$progressInt%"
        
        if (progress > 0.01f && startTimeMs > 0) {
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val estimatedTotalMs = (elapsedMs / progress).toLong()
            val remainingMs = estimatedTotalMs - elapsedMs
            val remainingSec = (remainingMs / 1000).coerceAtLeast(0)
            val mins = remainingSec / 60
            val secs = remainingSec % 60
            timeText = "$progressInt%  ${String.format("%02d:%02d", mins, secs)}"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressInt, false)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        
        if (Build.VERSION.SDK_INT >= 34) {
            return ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification(outputPath: String) {
        val channelId = "export_complete_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Export Complete", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Extract filename from URI
        // TODO : mejorar
        val videoName = try {
            val uri = outputPath.toUri()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            } ?: uri.lastPathSegment ?: "unknown.mp4"
        } catch (e: Exception) {
            "unknown.mp4"
        }
        
        // Create intent to open the video
        val openVideoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(outputPath.toUri(), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openVideoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.export_complete))
            .setContentText(videoName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
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
