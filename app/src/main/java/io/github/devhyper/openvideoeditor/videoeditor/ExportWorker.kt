package io.github.devhyper.openvideoeditor.videoeditor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class ExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PROJECT_SNAPSHOT_PATH = "project_snapshot_path"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_EXPORT_AUDIO = "export_audio"
        const val KEY_EXPORT_VIDEO = "export_video"
        const val KEY_HDR_MODE = "hdr_mode"
        const val KEY_AUDIO_MIME_TYPE = "audio_mime_type"
        const val KEY_VIDEO_MIME_TYPE = "video_mime_type"
        const val KEY_FRAMERATE = "framerate"
        const val KEY_SPEED = "speed"
        const val KEY_LOSSLESS_CUT = "lossless_cut"
        const val KEY_SEGMENTED_MIN_DURATION_MS = "segmented_min_duration_ms"
        const val KEY_SEGMENTED_MIN_SIZE_BYTES = "segmented_min_size_bytes"
        const val KEY_SEGMENT_DURATION_MS = "segment_duration_ms"
        const val KEY_PROGRESS = "progress"
        private const val NOTIFICATION_CHANNEL_ID = "export_channel"
        private const val NOTIFICATION_ID = 5001
    }

    private val dataStore = SettingsDataStore(applicationContext)
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var transformManager: TransformManager? = null
    private var snapshotFile: File? = null

    override suspend fun doWork(): Result = coroutineScope {
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)
        val snapshotPath = inputData.getString(KEY_PROJECT_SNAPSHOT_PATH)
        if (outputPath.isNullOrBlank() || snapshotPath.isNullOrBlank()) {
            dataStore.setExportState(ExportState.FAILED)
            dataStore.setExportError(applicationContext.getString(R.string.export_error_missing_input))
            return@coroutineScope Result.failure()
        }

        val snapshotFile = File(snapshotPath)
        this.snapshotFile = snapshotFile
        val projectData = ProjectData.readFromFile(snapshotFile)
        if (projectData == null) {
            dataStore.setExportState(ExportState.FAILED)
            dataStore.setExportError(applicationContext.getString(R.string.export_error_missing_input))
            snapshotFile.delete()
            return@coroutineScope Result.failure()
        }

        val exportSettings = ExportSettings().apply {
            this.outputPath = outputPath
            exportAudio = inputData.getBoolean(KEY_EXPORT_AUDIO, true)
            exportVideo = inputData.getBoolean(KEY_EXPORT_VIDEO, true)
            hdrMode = inputData.getInt(KEY_HDR_MODE, hdrMode)
            audioMimeType = inputData.getString(KEY_AUDIO_MIME_TYPE)
            videoMimeType = inputData.getString(KEY_VIDEO_MIME_TYPE)
            framerate = inputData.getFloat(KEY_FRAMERATE, 0F)
            speed = inputData.getFloat(KEY_SPEED, 0F)
            losslessCut = inputData.getBoolean(KEY_LOSSLESS_CUT, false)
            segmentedExportMinDurationMs =
                inputData.getLong(KEY_SEGMENTED_MIN_DURATION_MS, segmentedExportMinDurationMs)
            segmentedExportMinSizeBytes =
                inputData.getLong(KEY_SEGMENTED_MIN_SIZE_BYTES, segmentedExportMinSizeBytes)
            segmentDurationMs = inputData.getLong(KEY_SEGMENT_DURATION_MS, segmentDurationMs)
        }

        createNotificationChannel()
        setForeground(createForegroundInfo(null))

        dataStore.setExportState(ExportState.RUNNING)
        dataStore.setExportOutputPath(outputPath)
        dataStore.setExportProgress(0F)
        dataStore.setExportError(null)

        val completion = CompletableDeferred<ExportOutcome>()
        val manager = TransformManager().also { transformManager = it }
        manager.initForExport(applicationContext, projectData)

        val progressJob = launch {
            while (isActive && !completion.isCompleted) {
                val progressValue = manager.getProgress()
                val progressForUi = if (progressValue < 0F) -1F else progressValue.coerceIn(0F, 1F)
                dataStore.setExportProgress(progressForUi)
                setProgress(workDataOf(KEY_PROGRESS to progressForUi))
                updateNotification(progressForUi)
                setForeground(createForegroundInfo(progressForUi))
                delay(500)
            }
        }

        val errorMessage = { applicationContext.getString(R.string.ffmpeg_error) }
        val completionHandler: (ExportOutcome) -> Unit = { outcome ->
            if (!completion.isCompleted) {
                completion.complete(outcome)
            }
        }

        manager.export(
            applicationContext,
            exportSettings,
            transformerListener = object : androidx.media3.transformer.Transformer.Listener {
                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    result: androidx.media3.transformer.ExportResult,
                    exception: androidx.media3.transformer.ExportException,
                ) {
                    runBlocking {
                        dataStore.setExportError(exception.toString())
                    }
                    completionHandler(ExportOutcome.FAILED)
                }
            },
            onFFmpegError = {
                runBlocking {
                    dataStore.setExportError(errorMessage())
                }
                completionHandler(ExportOutcome.FAILED)
            },
            onExportCompleted = {
                completionHandler(ExportOutcome.SUCCESS)
            },
        )

        val outcome = completion.await()
        progressJob.cancel()
        progressJob.join()

        when (outcome) {
            ExportOutcome.SUCCESS -> {
                dataStore.setExportProgress(1F)
                dataStore.setExportState(ExportState.COMPLETED)
                dataStore.setExportWorkId(null)
                updateNotification(1F, completed = true)
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(outputPath),
                    null,
                ) { _, _ -> }
                snapshotFile.delete()
                Result.success()
            }

            ExportOutcome.FAILED -> {
                dataStore.setExportState(ExportState.FAILED)
                dataStore.setExportWorkId(null)
                updateNotification(null, failed = true)
                snapshotFile.delete()
                Result.failure()
            }
        }
    }

    override fun onStopped() {
        super.onStopped()
        transformManager?.cancel()
        snapshotFile?.delete()
        runBlocking {
            dataStore.setExportState(ExportState.CANCELLED)
            dataStore.setExportProgress(0F)
            dataStore.setExportError(null)
            dataStore.setExportWorkId(null)
        }
    }

    private fun createForegroundInfo(progress: Float?): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.export_notification_title))
            .setContentText(applicationContext.getString(R.string.export_notification_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                ((progress ?: 0F).coerceIn(0F, 1F) * 100).toInt(),
                progress == null || progress < 0F,
            )
            .build()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, foregroundServiceType)
    }

    private fun updateNotification(
        progress: Float?,
        completed: Boolean = false,
        failed: Boolean = false,
    ) {
        val titleRes = when {
            completed -> R.string.export_notification_complete
            failed -> R.string.export_notification_failed
            else -> R.string.export_notification_title
        }
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(titleRes))
            .setContentText(applicationContext.getString(R.string.export_notification_body))
            .setOnlyAlertOnce(true)
            .setOngoing(!completed && !failed)
            .setProgress(
                100,
                ((progress ?: 0F).coerceIn(0F, 1F) * 100).toInt(),
                progress == null || progress < 0F,
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.export_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }
}

private enum class ExportOutcome {
    SUCCESS,
    FAILED,
}
