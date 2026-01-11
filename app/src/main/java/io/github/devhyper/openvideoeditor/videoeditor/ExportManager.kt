package io.github.devhyper.openvideoeditor.videoeditor

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
import androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED
import androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.SessionState
import io.github.devhyper.openvideoeditor.misc.getVideoFileDuration
import java.io.File
import kotlin.math.ceil
import io.github.devhyper.openvideoeditor.R
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition

class ExportManager(private val context: Context, private val projectData: ProjectData) {

    private var transformer: Transformer? = null
    private val originalMedia: MediaItem = MediaItem.fromUri(projectData.uri)
    private var isCancelled = false

    fun export(
        exportSettings: ExportSettings,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        android.util.Log.d("ExportDebug", "🚀 ExportManager.export called. Output: ${exportSettings.outputPath}")
        val outputPath = exportSettings.outputPath
        val totalDurationMs = getExportDurationMs(context)
        
        val onFFmpegErrorLocal = { onError(context.getString(R.string.ffmpeg_error)) }
        
        if (shouldUseSegmentedExport(context, exportSettings)) {
            val state = resolveSegmentExportState(context, exportSettings, totalDurationMs)
            val segments = buildSegmentRanges(totalDurationMs, exportSettings.segmentDurationMs)
            val filteredSegments = segments.filter { it.durationMs > 0 }
            if (filteredSegments.isEmpty()) {
                onFFmpegErrorLocal()
                return
            }
            if (canUseLosslessSegmentCopy(exportSettings)) {
                startSegmentedExportWithFfmpeg(
                    context,
                    projectData.uri,
                    onFFmpegErrorLocal,
                    filteredSegments,
                    state,
                    onCompleted
                )
            } else {
                startSegmentedExportWithTransformer(
                    context,
                    exportSettings,
                    onError,
                    onFFmpegErrorLocal,
                    filteredSegments,
                    state,
                    onCompleted
                )
            }
            return
        }
        if (exportSettings.losslessCut) {
            val trim = getMergedTrim()
            if (trim != null) {
                ffmpegLosslessCut(context, trim, outputPath, false, onFFmpegErrorLocal, onCompleted)
            }
        } else {
            val fd =
                context.contentResolver.openFileDescriptor(
                    outputPath.toUri(),
                    "rw"
                )?.fileDescriptor
            val effectArray = getEffectArray()
            effectArray.apply {
                if (exportSettings.speed > 0) {
                    add(SpeedChangeEffect(exportSettings.speed))
                }
                if (exportSettings.framerate > 0) {
                    add(FrameDropEffect.createDefaultFrameDropEffect(exportSettings.framerate))
                }
            }
            
            val trimmedExportMedia = getTrimmedExportMedia()
            
            val editedMediaItem = EditedMediaItem.Builder(trimmedExportMedia)
                .setEffects(Effects(projectData.audioProcessors, effectArray))
                .setRemoveAudio(!exportSettings.exportAudio)
                .setRemoveVideo(!exportSettings.exportVideo)
                .build()
                
            val transformerListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                     onCompleted()
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    onError(exception.toString())
                }
            }

            transformer = Transformer.Builder(context)
                .setTransformationRequest(
                    TransformationRequest.Builder()
                        .setHdrMode(exportSettings.hdrMode)
                        .setAudioMimeType(exportSettings.audioMimeType)
                        .setVideoMimeType(exportSettings.videoMimeType)
                        .build()
                )
                .setMuxerFactory(CustomMuxer.Factory(fd))
                .addListener(transformerListener)
                .build()
            if (fd != null) {
                transformer!!.start(editedMediaItem, "")
            } else {
                transformer!!.start(editedMediaItem, outputPath)
            }
        }
    }

    fun cancel() {
        android.util.Log.d("ExportDebug", "🛑 ExportManager.cancel called")
        isCancelled = true
        // Cancel all FFmpeg sessions
        FFmpegKit.cancel()
        // Cancel transformer if active
        transformer?.cancel()
    }

    fun cleanupSegments(context: Context, outputPath: String) {
        cleanupSegmentsStatic(context, outputPath)
        projectData.segmentExportState = null
    }

    companion object {
        /**
         * Static version of cleanupSegments that doesn't require a ProjectData instance.
         * Use this when you only need to delete segment files without updating ProjectData state.
         */
        fun cleanupSegmentsStatic(context: Context, outputPath: String) {
            try {
                val baseDirectory = File(context.filesDir, "segmented_exports")
                val segmentDirectory = File(baseDirectory, outputPath.hashCode().toString())
                if (segmentDirectory.exists()) {
                    segmentDirectory.deleteRecursively()
                    android.util.Log.d("ExportDebug", "🗑️ Cleaned up segment directory: ${segmentDirectory.absolutePath}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ExportDebug", "Failed to cleanup segments", e)
            }
        }
    }

    private var currentProgress: Float = 0f
    
    fun getProgress(): Float {
        // If transformer is active, use its progress (polling)
        if (transformer != null) {
            val progressHolder = ProgressHolder()
            return when (transformer?.getProgress(progressHolder)) {
                PROGRESS_STATE_UNAVAILABLE -> -1F
                PROGRESS_STATE_NOT_STARTED -> 1F
                else -> progressHolder.progress.toFloat() / 100F
            }
        }
        // Otherwise return the progress tracked from FFmpeg stats
        return currentProgress
    }
    
    private fun getTrimmedExportMedia(): MediaItem {
        val trim = getMergedTrim()
        return if (trim != null) {
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(trim.first)
                .setEndPositionMs(trim.second).build()
            originalMedia.buildUpon().setClippingConfiguration(clipConfig).build()
        } else {
            originalMedia
        }
    }

    private fun getEffectArray(): MutableList<Effect> {
        val effectArray = mutableListOf<Effect>()
        for (userEffect in projectData.videoEffects) {
            effectArray.add(userEffect.effect())
        }
        return effectArray
    }
    
    // Made public or internal so TransformManager can use it if needed, or kept private
    private fun getMergedTrim(): Trim? {
        if (projectData.mediaTrims.isNotEmpty()) {
            var currentPair = projectData.mediaTrims[0]

            if (projectData.mediaTrims.size > 1) {
                for (i in 1 until projectData.mediaTrims.size) {
                    val cutStart =
                        currentPair.first - (projectData.mediaTrims[i - 1].first - projectData.mediaTrims[i].first)
                    val cutEnd =
                        currentPair.second - (projectData.mediaTrims[i - 1].second - projectData.mediaTrims[i].second)
                    currentPair = Trim(cutStart, cutEnd)
                }
            }

            return currentPair
        }
        return null
    }

    private fun getExportDurationMs(context: Context): Long {
        val trim = getMergedTrim()
        val baseDuration = getVideoFileDuration(context, projectData.uri.toUri()) ?: 0L
        return if (trim != null) {
            (trim.second - trim.first).coerceAtMost(baseDuration)
        } else {
            baseDuration
        }
    }

    private fun getExportFileSize(context: Context): Long? {
        val fd = context.contentResolver.openAssetFileDescriptor(projectData.uri.toUri(), "r")
        val fileSize = fd?.length
        fd?.close()
        return fileSize
    }

    private fun shouldUseSegmentedExport(context: Context, exportSettings: ExportSettings): Boolean {
        val durationMs = getExportDurationMs(context)
        val fileSize = getExportFileSize(context) ?: 0L
        val durationThresholdReached =
            exportSettings.segmentedExportMinDurationMs > 0 &&
                durationMs >= exportSettings.segmentedExportMinDurationMs
        val sizeThresholdReached =
            exportSettings.segmentedExportMinSizeBytes > 0 &&
                fileSize >= exportSettings.segmentedExportMinSizeBytes
        return durationThresholdReached || sizeThresholdReached
    }

    private fun getSegmentExportDirectory(context: Context, outputPath: String): File {
        val baseDirectory = File(context.filesDir, "segmented_exports")
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
        val segmentDirectory = File(baseDirectory, outputPath.hashCode().toString())
        if (!segmentDirectory.exists()) {
            segmentDirectory.mkdirs()
        }
        return segmentDirectory
    }

    private fun resolveSegmentExportState(
        context: Context,
        exportSettings: ExportSettings,
        totalDurationMs: Long,
    ): SegmentExportState {
        val segmentDirectory = getSegmentExportDirectory(context, exportSettings.outputPath)
        val existingState = projectData.segmentExportState
        return if (
            existingState == null ||
            existingState.outputPath != exportSettings.outputPath ||
            existingState.segmentDurationMs != exportSettings.segmentDurationMs ||
            existingState.totalDurationMs != totalDurationMs ||
            existingState.segmentDirectoryPath != segmentDirectory.absolutePath
        ) {
            SegmentExportState(
                outputPath = exportSettings.outputPath,
                segmentDurationMs = exportSettings.segmentDurationMs,
                totalDurationMs = totalDurationMs,
                segmentDirectoryPath = segmentDirectory.absolutePath,
            ).also { projectData.segmentExportState = it }
        } else {
            existingState
        }
    }

    private fun clearSegmentExportState() {
        projectData.segmentExportState = null
    }

    private fun segmentFilePath(state: SegmentExportState, index: Int): String {
        return File(state.segmentDirectoryPath, "segment_$index.mp4").absolutePath
    }

    private fun runConcat(
        context: Context,
        state: SegmentExportState,
        segments: List<SegmentRange>,
        onFFmpegError: () -> Unit,
        onCompleted: () -> Unit
    ) {
        if (isCancelled) return

        val listFile = File(state.segmentDirectoryPath, "concat_list.txt")
        listFile.bufferedWriter().use { writer ->
            segments.forEach { segment ->
                val segmentPath = segmentFilePath(state, segment.index)
                writer.appendLine("file '${segmentPath.replace("'", "\\'")}'")
            }
        }
        val outputSafPath =
            FFmpegKitConfig.getSafParameterForWrite(context, state.outputPath.toUri())
        
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            // Concat is fast, but we can try to estimate if we knew total size or duration.
            // For now, let's just leave it near 100% or indeterminate?
            // Actually, concat is the final step. We can map it from 95% to 100%?
            // Or just leave it. The loop handles segments progress.
        }
            
        FFmpegKit.executeAsync(
            "-f concat -safe 0 -i ${listFile.absolutePath} -c copy $outputSafPath"
        ) { session ->
            if (isCancelled || VideoExportWorker.isPausedFlow.value) {
                android.util.Log.d("ExportDebug", "🛑 Concat aborted (Cancelled or Paused)")
                return@executeAsync
            }

            val completed = session.state == SessionState.COMPLETED
            if (completed) {
                android.util.Log.d("ExportDebug", "✅ Concat completed successfully")
                clearSegmentExportState()
                currentProgress = 1f
                onCompleted()
            } else {
                android.util.Log.e("ExportDebug", "❌ Concat failed or cancelled")
                onFFmpegError()
            }
        }
    }

    private fun canUseLosslessSegmentCopy(exportSettings: ExportSettings): Boolean {
        val noEffects = projectData.videoEffects.isEmpty() && projectData.audioProcessors.isEmpty()
        val noSpeedOrFramerate = exportSettings.speed <= 0 && exportSettings.framerate <= 0
        val defaultMimeTypes =
            exportSettings.audioMimeType == null && exportSettings.videoMimeType == null
        val keepAudioVideo = exportSettings.exportAudio && exportSettings.exportVideo
        return noEffects && noSpeedOrFramerate && defaultMimeTypes && keepAudioVideo
    }

    private fun startSegmentedExportWithFfmpeg(
        context: Context,
        inputUri: String,
        onFFmpegError: () -> Unit,
        segments: List<SegmentRange>,
        state: SegmentExportState,
        onCompleted: () -> Unit
    ) {
        val trim = getMergedTrim()
        val baseOffsetMs = trim?.first ?: 0L
        
        // Restore state from disk: Check which segments already exist AND are valid (size > 0)
        segments.forEach { segment ->
            val path = segmentFilePath(state, segment.index)
            val file = File(path)
            // Only consider segment complete if file exists AND has content (not corrupted/partial)
            if (file.exists() && file.length() > 0 && !state.completedSegments.contains(segment.index)) {
                state.completedSegments.add(segment.index)
            }
        }

        // Remove any "completed" segments that no longer exist or are empty (corrupted)
        state.completedSegments.removeIf { index ->
            val segmentFile = File(segmentFilePath(state, index))
            !segmentFile.exists() || segmentFile.length() == 0L
        }

        fun exportNextSegment(startIndex: Int) {
            if (isCancelled || VideoExportWorker.isPausedFlow.value) {
                android.util.Log.d("ExportDebug", "🛑 exportNextSegment (FFmpeg) aborted (Cancelled or Paused)")
                return
            }

            val nextSegment =
                segments.drop(startIndex).firstOrNull { !state.completedSegments.contains(it.index) }
            if (nextSegment == null) {
                runConcat(context, state, segments, onFFmpegError, onCompleted)
                return
            }
            val segmentPath = segmentFilePath(state, nextSegment.index)
            val ffmpegInputPath =
                FFmpegKitConfig.getSafParameterForRead(context, inputUri.toUri())
            val segmentStartMs = baseOffsetMs + nextSegment.startMs
            // We can track progress based on completed segments + current segment progress
            val totalSegments = segments.count { it.durationMs > 0 }
            // simple progress:
            // currentProgress = (completedSegments / total) + (currentSegmentProgress / total)
            
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                val segmentDuration = nextSegment.durationMs
                val timeInSegment = stats.time
                val segmentProgress = (timeInSegment / segmentDuration.toDouble()).coerceIn(0.0, 1.0)
                
                val completedCount = state.completedSegments.size
                val totalProgress = (completedCount + segmentProgress) / totalSegments.toDouble()
                currentProgress = totalProgress.toFloat()
            }

            FFmpegKit.executeAsync(
                "-ss ${segmentStartMs}ms -t ${nextSegment.durationMs}ms -i $ffmpegInputPath -c copy $segmentPath"
            ) { session ->
                if (isCancelled || VideoExportWorker.isPausedFlow.value) return@executeAsync

                val file = File(segmentPath)
                if (session.state == SessionState.COMPLETED && file.exists() && file.length() > 0L) {
                    state.completedSegments.add(nextSegment.index)
                    exportNextSegment(nextSegment.index + 1)
                } else {
                    onFFmpegError()
                }
            }
        }

        exportNextSegment(0)
    }

    private fun startSegmentedExportWithTransformer(
        context: Context,
        exportSettings: ExportSettings,
        onError: (String) -> Unit,
        onFFmpegError: () -> Unit,
        segments: List<SegmentRange>,
        state: SegmentExportState,
        onCompleted: () -> Unit
    ) {
        val trim = getMergedTrim()
        val baseOffsetMs = trim?.first ?: 0L
        
        // Restore state from disk: Check which segments already exist AND are valid
        segments.forEach { segment ->
            val path = segmentFilePath(state, segment.index)
            val file = File(path)
            if (file.exists() && file.length() > 0 && !state.completedSegments.contains(segment.index)) {
                state.completedSegments.add(segment.index)
            }
        }
        
        // Remove corrupted/missing segments
        state.completedSegments.removeIf { index ->
            val segmentFile = File(segmentFilePath(state, index))
            !segmentFile.exists() || segmentFile.length() == 0L
        }
        val effectArray = getEffectArray()
        effectArray.apply {
            if (exportSettings.speed > 0) {
                add(SpeedChangeEffect(exportSettings.speed))
            }
            if (exportSettings.framerate > 0) {
                add(FrameDropEffect.createDefaultFrameDropEffect(exportSettings.framerate))
            }
        }

        fun exportNextSegment(startIndex: Int) {
            if (isCancelled || VideoExportWorker.isPausedFlow.value) {
                android.util.Log.d("ExportDebug", "🛑 exportNextSegment (Transformer) aborted (Cancelled or Paused)")
                return
            }

            val nextSegment =
                segments.drop(startIndex).firstOrNull { !state.completedSegments.contains(it.index) }
            if (nextSegment == null) {
                runConcat(context, state, segments, onFFmpegError, onCompleted)
                return
            }
            val startMs = baseOffsetMs + nextSegment.startMs
            val endMs = startMs + nextSegment.durationMs
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(startMs)
                .setEndPositionMs(endMs).build()
            val segmentMedia = originalMedia.buildUpon().setClippingConfiguration(clipConfig).build()
            val editedMediaItem = EditedMediaItem.Builder(segmentMedia)
                .setEffects(Effects(projectData.audioProcessors, effectArray))
                .setRemoveAudio(!exportSettings.exportAudio)
                .setRemoveVideo(!exportSettings.exportVideo)
                .build()
            transformer = Transformer.Builder(context)
                .setTransformationRequest(
                    TransformationRequest.Builder()
                        .setHdrMode(exportSettings.hdrMode)
                        .setAudioMimeType(exportSettings.audioMimeType)
                        .setVideoMimeType(exportSettings.videoMimeType)
                        .build()
                )
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: androidx.media3.transformer.Composition,
                            result: androidx.media3.transformer.ExportResult,
                        ) {
                            if (isCancelled || VideoExportWorker.isPausedFlow.value) return

                            state.completedSegments.add(nextSegment.index)
                            exportNextSegment(nextSegment.index + 1)
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            result: androidx.media3.transformer.ExportResult,
                            exception: androidx.media3.transformer.ExportException,
                        ) {
                            onError(exception.toString())
                        }
                    }
                )
                .build()
            transformer!!.start(editedMediaItem, segmentFilePath(state, nextSegment.index))
        }

        exportNextSegment(0)
    }

    private fun ffmpegLosslessCut(
        context: Context,
        trim: Trim,
        outputPath: String,
        audioFallback: Boolean,
        onFFmpegError: () -> Unit,
        onCompleted: () -> Unit
    ) {
        val ffmpegInputPath =
            FFmpegKitConfig.getSafParameterForRead(context, projectData.uri.toUri())
        val ffmpegOutputPath = FFmpegKitConfig.getSafParameterForWrite(context, outputPath.toUri())
        val audioCodec = if (audioFallback) "aac" else "copy"
        val durationMs = trim.second - trim.first
        
        FFmpegKitConfig.enableStatisticsCallback { stats ->
             val time = stats.time
             currentProgress = (time / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
        }

        FFmpegKit.executeAsync(
            "-i $ffmpegInputPath -ss ${trim.first}ms -to ${trim.second}ms -c:v copy -c:a $audioCodec $ffmpegOutputPath"
        ) {
            if (isCancelled || VideoExportWorker.isPausedFlow.value) return@executeAsync

            val fd = context.contentResolver.openAssetFileDescriptor(outputPath.toUri(), "r")
            if (fd != null) {
                val fileSize = fd.length
                fd.close()
                if (fileSize != 0L) {
                    currentProgress = 1f
                    onCompleted()
                    return@executeAsync
                }
            }
            if (audioFallback) {
                onFFmpegError()
            } else {
                ffmpegLosslessCut(context, trim, outputPath, true, onFFmpegError, onCompleted)
            }
        }
    }
}
