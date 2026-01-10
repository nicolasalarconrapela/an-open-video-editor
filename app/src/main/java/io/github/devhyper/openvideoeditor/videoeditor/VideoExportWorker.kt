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
import io.github.devhyper.openvideoeditor.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import kotlin.coroutines.resume


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
        
        // Global state for pause/resume of exports
        private val _isPaused = MutableStateFlow(false)
        val isPausedFlow = _isPaused.asStateFlow()
        
        fun setPaused(paused: Boolean) {
            android.util.Log.d("ExportDebug", "${if (paused) "⏸️" else "▶️"} VideoExportWorker.setPaused: $paused")
            _isPaused.value = paused
        }
    }

    // --- INICIO DE CAMBIOS ---

    // 1. Implementa getForegroundInfo() para que WorkManager gestione el inicio del servicio.
    //    Esto previene ForegroundServiceStartNotAllowedException.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Al inicio, el progreso es 0. Esta será la notificación inicial.
        return createForegroundInfo(0f, false)
    }

    // --- FIN DE CAMBIOS ---

    override suspend fun doWork(): Result {
        val projectDataPath = inputData.getString(KEY_PROJECT_DATA_PATH) ?: return Result.failure()
        val exportSettingsPath = inputData.getString(KEY_EXPORT_SETTINGS_PATH) ?: return Result.failure()

        val projectData = readObject<ProjectData>(projectDataPath)
        val exportSettings = readObject<ExportSettings>(exportSettingsPath)
        val isResume = inputData.getBoolean("isResume", false)
        android.util.Log.d("ExportDebug", "🎬 VideoExportWorker.doWork started. isResume: $isResume")

        if (projectData == null || exportSettings == null) return Result.failure()

        val outputPath = exportSettings.outputPath

        // Reset pause state at the start of each export
        _isPaused.value = false

        // If this is a fresh start (not a resume), clean up any previous segments for this output path
        if (!isResume) {
            ExportManager(context, projectData).cleanupSegments(context, outputPath)
        }

        // --- INICIO DE CAMBIOS ---

        // 2. Elimina la llamada inicial a setForeground. WorkManager ya se encarga de esto
        //    usando la información de getForegroundInfo().
        // setForeground(createForegroundInfo(0f, false)) // <-- LÍNEA ELIMINADA

        // --- FIN DE CAMBIOS ---

        startTimeMs = System.currentTimeMillis()

        val exportManager = ExportManager(context, projectData)

        return try {
            coroutineScope {
                // Launch progress poller
                val progressJob = launch {
                    while (isActive) {
                        val progress = exportManager.getProgress()
                        if (progress >= 0) {
                            // 3. Mantenemos esta llamada para ACTUALIZAR la notificación existente.
                            //    Esto es seguro y necesario.
                            setForeground(createForegroundInfo(progress, false))
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
                            showCompletionNotification(outputPath, Status.SUCCESS)
                            if (continuation.isActive) {
                                continuation.resume(Result.success(workDataOf(KEY_OUTPUT_PATH to outputPath)))
                            }
                        },
                        onError = { error ->
                            progressJob.cancel()
                            showCompletionNotification(outputPath, Status.FAILED, error)
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(workDataOf(KEY_ERROR to error)))
                            }
                        }
                    )
                    

                    
                    continuation.invokeOnCancellation {
                        android.util.Log.d("ExportDebug", "🚱 VideoExportWorker cancelled. Paused: ${_isPaused.value}")
                        exportManager.cancel()
                        progressJob.cancel()
                        if (_isPaused.value) {
                            showPausedNotification(projectDataPath, exportSettingsPath, exportManager.getProgress())
                        } else {
                            // Cleanup segments if cancelled and NOT paused
                            exportManager.cleanupSegments(context, outputPath)
                            showCompletionNotification(outputPath, Status.CANCELLED)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExportDebug", "⚠️ VideoExportWorker exception: $e")
            // La posición de tu cursor estaba aquí. Esta lógica para manejar la pausa parece correcta.
            if (!kotlin.coroutines.coroutineContext.isActive && _isPaused.value) {
                android.util.Log.d("ExportDebug", "⏸️ VideoExportWorker stopped for pause (success result)")
                Result.success() // Stopped for pause
            } else {
                // Cleanup segments on error
                ExportManager(context, projectData).cleanupSegments(context, outputPath)
                showCompletionNotification(outputPath, Status.FAILED, e.toString())
                Result.failure(workDataOf(KEY_ERROR to e.toString()))
            }
        } finally {
            // Cleanup temp files only if NOT paused
            if (!_isPaused.value) {
                try {
                    File(projectDataPath).delete()
                    File(exportSettingsPath).delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    enum class Status { SUCCESS, FAILED, CANCELLED }

    private var startTimeMs: Long = 0L
    // Este método ya estaba bien preparado, especialmente con la comprobación para Android 14 (API 34)
    private fun createForegroundInfo(progress: Float, paused: Boolean): ForegroundInfo {
        val channelId = "export_channel"
        val title = if (paused) "⏸️ Pausado" else "🎬 " + context.getString(R.string.exporting)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Export", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        // Calculate remaining time estimate
        val progressInt = (progress * 100).toInt()
        var timeText = "$progressInt%"

        if (!paused && progress > 0.01f && startTimeMs > 0) {
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val estimatedTotalMs = (elapsedMs / progress).toLong()
            val remainingMs = estimatedTotalMs - elapsedMs
            val remainingSec = (remainingMs / 1000).coerceAtLeast(0)
            val mins = remainingSec / 60
            val secs = remainingSec % 60
            timeText = "$progressInt%  ${String.format("%02d:%02d", mins, secs)}"
        } else if (paused) {
            timeText = "$progressInt% (Pausado)"
        }

        val pauseResumeIntent = Intent(context, ExportActionReceiver::class.java).apply {
            action = if (paused) "RESUME" else "PAUSE"
            putExtra("workerId", id.toString())
            putExtra("projectDataPath", inputData.getString(KEY_PROJECT_DATA_PATH))
            putExtra("exportSettingsPath", inputData.getString(KEY_EXPORT_SETTINGS_PATH))
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context, 0, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressInt, false)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(id))
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) context.getString(R.string.resume) else context.getString(R.string.pause),
                pauseResumePendingIntent
            )

        val notification = builder.build()
        
        if (Build.VERSION.SDK_INT >= 34) {
            return ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
    
    private fun showPausedNotification(projectDataPath: String, settingsPath: String, progress: Float) {
        val channelId = "export_channel"
        val progressInt = (progress * 100).toInt()
        
        val resumeIntent = Intent(context, ExportActionReceiver::class.java).apply {
            action = "RESUME"
            putExtra("projectDataPath", projectDataPath)
            putExtra("exportSettingsPath", settingsPath)
        }
        val cancelIntent = Intent(context, ExportActionReceiver::class.java).apply {
            action = "CANCEL_PAUSED"
            putExtra("projectDataPath", projectDataPath)
            putExtra("exportSettingsPath", settingsPath)
        }
        
        val resumePI = PendingIntent.getBroadcast(context, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelPI = PendingIntent.getBroadcast(context, 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("⏸️ Exportación Pausada")
            .setContentText("$progressInt% - Toca para reanudar")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, progressInt, false)
            .addAction(android.R.drawable.ic_media_play, context.getString(R.string.resume), resumePI)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), cancelPI)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(outputPath: String, status: Status, errorMsg: String? = null) {
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
        
        val notificationTitle = when (status) {
            Status.SUCCESS -> "✅ " + context.getString(R.string.export_complete)
            Status.FAILED -> "❌ " + context.getString(R.string.export_failed)
            Status.CANCELLED -> "🛑 " + context.getString(R.string.export_cancelled)
        }
        
        val notificationText = if (status == Status.FAILED && errorMsg != null) {
            errorMsg
        } else {
            videoName
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .apply {
                if (status == Status.SUCCESS) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()
        
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readObject(path: String): T? {
        val file = File(path)
        if (!file.exists()) {
            return null
        }

        // Usa el bloque 'use' para la gestión automática de recursos.
        // 'use' cerrará los streams (ObjectInputStream y FileInputStream) automáticamente
        // al final del bloque, incluso si ocurre una excepción.
        // Esto previene fugas de recursos y es la forma recomendada en Kotlin.
        return try {
            ObjectInputStream(FileInputStream(file)).use { objectInputStream ->
                objectInputStream.readObject() as? T
            }
        } catch (e: Exception) {
            // Si ocurre cualquier error durante la lectura (archivo corrupto, etc.),
            // se captura aquí y se devuelve null.
            android.util.Log.e("ExportDebug", "Error al leer el objeto desde $path", e)
            null
        }
    }

}
