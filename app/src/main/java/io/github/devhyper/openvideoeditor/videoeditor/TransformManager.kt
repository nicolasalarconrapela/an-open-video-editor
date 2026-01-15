package io.github.devhyper.openvideoeditor.videoeditor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.exoplayer.ExoPlayer
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
import io.github.devhyper.openvideoeditor.misc.PROJECT_FILE_EXT
import io.github.devhyper.openvideoeditor.misc.getFileNameFromUri
import io.github.devhyper.openvideoeditor.misc.getVideoFileDuration
import io.github.devhyper.openvideoeditor.videoeditor.state.ClipSource
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineClipType
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.math.ceil


typealias Trim = Pair<Long, Long>
typealias ImageConstructor = () -> ImageVector
typealias EffectConstructor = () -> Effect
typealias Editor = @Composable (MutableStateFlow<EffectConstructor?>) -> Unit

class EffectDialogSetting(
    val key: String,
    val stringResId: Int,
    val textfieldValidation: ((String) -> String)? = null,
    val dropdownOptions: MutableList<String>? = null
) {
    var selection = ""
}

class ExportSettings {
    var exportAudio = true
    var exportVideo = true
    var hdrMode: Int = HDR_MODE_KEEP_HDR
    var audioMimeType: String? = null
    var videoMimeType: String? = null
    var framerate: Float = 0F
    var speed: Float = 0F
    var outputPath: String = ""
    var losslessCut: Boolean = false
    var segmentedExportMinDurationMs: Long = 10 * 60 * 1000L
    var segmentedExportMinSizeBytes: Long = 1024L * 1024L * 1024L
    var segmentDurationMs: Long = 5 * 60 * 1000L

    /*
    fun log() {
        Log.i(
            "open-video-editor",
            "\nexportVideo: $exportVideo\nexportAudio: $exportAudio\nhdrMode: $hdrMode\naudioMimeType: $audioMimeType\nvideoMimeType: $videoMimeType\noutputPath: $outputPath"
        )
    }
     */

    fun setMediaToExportString(string: String) {
        when (string) {
            "Video and Audio" -> {
                exportVideo = true; exportAudio = true; }

            "Video only" -> {
                exportVideo = true; exportAudio = false; }

            "Audio only" -> {
                exportVideo = false; exportAudio = true; }
        }
    }

    fun setHdrModeString(string: String) {
        when (string) {
            "Keep HDR" -> {
                hdrMode = HDR_MODE_KEEP_HDR
            }

            "HDR as SDR" -> {
                hdrMode = HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
            }

            "HDR to SDR (Mediacodec)" -> {
                hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
            }

            "HDR to SDR (OpenGL)" -> {
                hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            }
        }
    }

    fun setAudioMimeTypeString(string: String) {
        audioMimeType = if (string == "Original") {
            null
        } else {
            string
        }
    }

    fun setVideoMimeTypeString(string: String) {
        videoMimeType = if (string == "Original") {
            null
        } else {
            string
        }
    }
}

fun getMediaToExportStrings(): ImmutableList<String> {
    return persistentListOf("Video and Audio", "Video only", "Audio only")
}

fun getHdrModesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Keep HDR",
        "HDR as SDR",
        "HDR to SDR (Mediacodec)",
        "HDR to SDR (OpenGL)"
    )
}

fun getAudioMimeTypesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Original",
        MimeTypes.AUDIO_AAC,
        MimeTypes.AUDIO_AMR_NB,
        MimeTypes.AUDIO_AMR_WB
    )
}

fun getVideoMimeTypesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Original",
        MimeTypes.VIDEO_H263,
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_MP4V
    )
}

class DialogUserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val args: PersistentList<EffectDialogSetting>,
    val callback: (Map<String, String>) -> EffectConstructor
)

class OnVideoUserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val editor: Editor,
) {
    var callback: (EffectConstructor) -> Unit = {}

    private val effect = MutableStateFlow<EffectConstructor?>(null)

    fun runCallback() {
        effect.value?.let { callback(it) }
    }

    @Composable
    fun Editor() {
        editor(effect)
    }
}

class UserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val effect: EffectConstructor
) : java.io.Serializable

data class ProjectData(
    val uri: String,

    val videoEffects: MutableList<UserEffect> = mutableListOf(),
    val audioProcessors: MutableList<AudioProcessor> = mutableListOf(),
    val mediaTrims: MutableList<Trim> = mutableListOf(),
    var segmentExportState: SegmentExportState? = null,
    var proxyPath: String? = null,
    var proxyQuality: String? = null,
) : java.io.Serializable {
    companion object {
        fun read(uri: String, context: Context): ProjectData? {
            var projectData: ProjectData? = null
            val parsedUri = uri.toUri()
            val inputStream = if (parsedUri.scheme == ContentResolver.SCHEME_FILE) {
                File(parsedUri.path ?: return null).inputStream()
            } else {
                context.contentResolver.openInputStream(parsedUri)
            }
            inputStream?.let {
                val input = ObjectInputStream(it)
                projectData = input.readObject() as ProjectData?
                input.close()
            }
            return projectData
        }
    }

    fun write(uri: String, context: Context) {
        val parsedUri = uri.toUri()
        val outputStream = if (parsedUri.scheme == ContentResolver.SCHEME_FILE) {
            File(parsedUri.path ?: return).outputStream()
        } else {
            context.contentResolver.openOutputStream(parsedUri)
        }
        outputStream?.let {
            val output = ObjectOutputStream(it)
            output.writeObject(this)
            output.close()
        }
    }
}

data class SegmentExportState(
    var outputPath: String,
    var segmentDurationMs: Long,
    var totalDurationMs: Long,
    var segmentDirectoryPath: String,
    var completedSegments: MutableSet<Int> = mutableSetOf(),
) : java.io.Serializable

data class SegmentRange(
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
)

internal fun buildSegmentRanges(
    totalDurationMs: Long,
    segmentDurationMs: Long,
): List<SegmentRange> {
    if (segmentDurationMs <= 0 || totalDurationMs <= 0) {
        return emptyList()
    }
    val segmentCount = ceil(totalDurationMs.toDouble() / segmentDurationMs.toDouble()).toInt()
    return (0 until segmentCount).map { index ->
        val startMs = segmentDurationMs * index
        val durationMs = minOf(segmentDurationMs, totalDurationMs - startMs)
        SegmentRange(index, startMs, durationMs)
    }
}

class TransformManager {
    lateinit var player: ExoPlayer

    private var hasInitialized = false

    private var transformer: Transformer? = null

    private lateinit var originalMedia: MediaItem

    private lateinit var previewMedia: MediaItem

    private lateinit var trimmedExportMedia: MediaItem

    private lateinit var trimmedPreviewMedia: MediaItem

    lateinit var projectData: ProjectData

    fun init(
        exoPlayer: ExoPlayer,
        uri: String,
        context: Context,
        viewModel: VideoEditorViewModel,
        requestVideoPermission: ActivityResultLauncher<String>
    ) {
        if (hasInitialized) {
            if (exoPlayer != player) {
                if (player.availableCommands.contains(Player.COMMAND_RELEASE)) {
                    player.release()
                }
                player = exoPlayer
            }
        } else {
            player = exoPlayer
            projectData = if (getFileNameFromUri(context, uri.toUri()).substringAfterLast(
                    '.',
                    ""
                ).substringBeforeLast(' ') == PROJECT_FILE_EXT
            ) {
                ProjectData.read(uri, context) ?: ProjectData(uri)
            } else {
                ProjectData(uri)
            }
            var projectSavingSupported = false
            if (requestPersistablePermissions(
                    context,
                    uri.toUri(),
                    requestVideoPermission
                )
            ) {
                projectSavingSupported = true
            }
            viewModel.setProjectSavingSupported(projectSavingSupported)
            hasInitialized = true
        }
        originalMedia = MediaItem.fromUri(projectData.uri)
        previewMedia = MediaItem.fromUri(resolvePreviewUri(context))
        rebuildMediaTrims()
        player.apply {
            stop()
            setMediaItem(trimmedPreviewMedia)
            setVideoEffects(getEffectArray())
            prepare()
        }
    }

    fun getPreviewSource(context: Context): String {
        return resolvePreviewUri(context)
    }

    private fun resolvePreviewUri(context: Context): String {
        val dataStore = SettingsDataStore(context)
        val proxyEnabled = dataStore.getProxyEnabledBlocking()
        val proxyQuality = dataStore.getProxyQualityBlocking()
        val existingProxy = projectData.proxyPath?.takeIf { path ->
            projectData.proxyQuality == proxyQuality && File(path).exists() && File(path).length() > 0L
        }
        if (proxyEnabled) {
            generateProxyIfNeeded(context, proxyQuality) { proxyPath ->
                if (proxyPath != existingProxy && hasInitialized) {
                    updatePreviewMedia(proxyPath)
                }
            }
        }
        return if (proxyEnabled) {
            existingProxy ?: projectData.uri
        } else {
            projectData.uri
        }
    }

    private fun requestPersistablePermissions(
        context: Context,
        uri: Uri,
        requestVideoPermission: ActivityResultLauncher<String>
    ): Boolean {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    requestVideoPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
                }
            } else {
                if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestVideoPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            return false
        }
        return true
    }

    private fun getEffectArray(): MutableList<Effect> {
        val effectArray = mutableListOf<Effect>()
        for (userEffect in projectData.videoEffects) {
            effectArray.add(userEffect.effect())
        }
        return effectArray
    }

    fun buildClipSources(context: Context): List<ClipSource> {
        val baseDuration = getVideoFileDuration(context, projectData.uri.toUri()) ?: 0L
        val trim = getMergedTrim()
        val clipDuration = if (trim != null) {
            (trim.second - trim.first).coerceAtLeast(0L)
        } else {
            baseDuration
        }
        if (clipDuration <= 0L) {
            return emptyList()
        }
        return listOf(
            ClipSource(
                id = "clip-main",
                durationMs = clipDuration,
                label = getFileNameFromUri(context, projectData.uri.toUri()),
                type = TimelineClipType.Video,
                mediaUri = projectData.uri
            )
        )
    }

    fun getMergedTrim(): Trim? {
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

    fun clearMediaTrims() {
        projectData.mediaTrims.clear()
        updateMediaTrims()
    }

    fun addVideoEffect(effect: UserEffect) {
        projectData.videoEffects.add(effect)
        updateVideoEffects()
    }

    fun addAudioProcessor(processor: AudioProcessor) {
        projectData.audioProcessors.add(processor)
        updateAudioProcessors()
    }

    fun addMediaTrim(trim: Trim) {
        if (projectData.mediaTrims.isNotEmpty() && trim == projectData.mediaTrims.last()) {
            return
        }
        projectData.mediaTrims.add(trim)
        updateMediaTrims()
    }

    fun removeVideoEffect(effect: UserEffect) {
        projectData.videoEffects.remove(effect)
        updateVideoEffects()
    }

    fun removeAudioProcessor(processor: AudioProcessor) {
        projectData.audioProcessors.remove(processor)
        updateAudioProcessors()
    }

    fun removeMediaTrim(trim: Trim) {
        projectData.mediaTrims.remove(trim)
        updateMediaTrims()
    }

    private fun updateVideoEffects() {
        player.apply {
            stop()
            setVideoEffects(getEffectArray())
            prepare()
        }
    }

    private fun updateAudioProcessors() {
        // TODO
    }

    private fun rebuildMediaTrims() {
        val trim = getMergedTrim()
        trimmedExportMedia = if (trim != null) {
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(trim.first)
                .setEndPositionMs(trim.second).build()
            originalMedia.buildUpon().setClippingConfiguration(clipConfig).build()
        } else {
            originalMedia
        }
        trimmedPreviewMedia = if (trim != null) {
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(trim.first)
                .setEndPositionMs(trim.second).build()
            previewMedia.buildUpon().setClippingConfiguration(clipConfig).build()
        } else {
            previewMedia
        }
    }

    private fun updateMediaTrims() {
        rebuildMediaTrims()

        player.apply {
            stop()
            setMediaItem(trimmedPreviewMedia)
            setVideoEffects(getEffectArray())
            prepare()
        }
    }

    private fun updatePreviewMedia(uri: String) {
        val currentPosition = player.currentPosition
        previewMedia = MediaItem.fromUri(uri)
        rebuildMediaTrims()
        player.apply {
            stop()
            setMediaItem(trimmedPreviewMedia)
            setVideoEffects(getEffectArray())
            prepare()
            if (currentPosition > 0) {
                seekTo(currentPosition)
            }
        }
    }

    private fun getProxyFile(context: Context, qualityKey: String): File {
        val proxyDirectory = File(context.cacheDir, "proxies")
        if (!proxyDirectory.exists()) {
            proxyDirectory.mkdirs()
        }
        return File(proxyDirectory, "${projectData.uri.hashCode()}_${qualityKey}.mp4")
    }

    private fun generateProxyIfNeeded(
        context: Context,
        qualityKey: String,
        onProxyReady: (String) -> Unit,
    ) {
        val quality = ProxyQuality.fromKey(qualityKey)
        val existingProxy = projectData.proxyPath?.takeIf { path ->
            projectData.proxyQuality == quality.key && File(path).exists() && File(path).length() > 0L
        }
        if (existingProxy != null) {
            onProxyReady(existingProxy)
            return
        }
        val proxyFile = getProxyFile(context, quality.key)
        val ffmpegInputPath =
            FFmpegKitConfig.getSafParameterForRead(context, projectData.uri.toUri())
        val command = buildString {
            append("-i ")
            append(ffmpegInputPath)
            append(" -vf scale=-2:")
            append(quality.height)
            append(" -c:v libx264 -preset veryfast -b:v ")
            append(quality.videoBitrateKbps)
            append("k -maxrate ")
            append(quality.videoBitrateKbps)
            append("k -bufsize ")
            append(quality.videoBitrateKbps * 2)
            append("k -c:a aac -b:a ")
            append(quality.audioBitrateKbps)
            append("k -movflags +faststart -y ")
            append(proxyFile.absolutePath)
        }
        FFmpegKit.executeAsync(command) { session ->
            val completed = session.state == SessionState.COMPLETED
            if (completed && proxyFile.exists() && proxyFile.length() > 0L) {
                projectData.proxyPath = proxyFile.absolutePath
                projectData.proxyQuality = quality.key
                onProxyReady(proxyFile.absolutePath)
            }
        }
    }

    private fun ffmpegLosslessCut(
        context: Context,
        trim: Trim,
        outputPath: String,
        audioFallback: Boolean,
        onFFmpegError: () -> Unit
    ) {
        val ffmpegInputPath =
            FFmpegKitConfig.getSafParameterForRead(context, projectData.uri.toUri())
        val ffmpegOutputPath = FFmpegKitConfig.getSafParameterForWrite(context, outputPath.toUri())
        val audioCodec = if (audioFallback) "aac" else "copy"
        FFmpegKit.executeAsync(
            "-i $ffmpegInputPath -ss ${trim.first}ms -to ${trim.second}ms -c:v copy -c:a $audioCodec $ffmpegOutputPath"
        ) {
            val fd = context.contentResolver.openAssetFileDescriptor(outputPath.toUri(), "r")
            if (fd != null) {
                val fileSize = fd.length
                fd.close()
                if (fileSize != 0L) {
                    return@executeAsync
                }
            }
            if (audioFallback) {
                onFFmpegError()
            } else {
                ffmpegLosslessCut(context, trim, outputPath, true, onFFmpegError)
            }
        }
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

    private fun shouldUseSegmentedExport(
        context: Context,
        exportSettings: ExportSettings
    ): Boolean {
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
    ) {
        val listFile = File(state.segmentDirectoryPath, "concat_list.txt")
        listFile.bufferedWriter().use { writer ->
            segments.forEach { segment ->
                val segmentPath = segmentFilePath(state, segment.index)
                writer.appendLine("file '''${segmentPath.replace("'", "\\'")}'''")
            }
        }
        val outputSafPath =
            FFmpegKitConfig.getSafParameterForWrite(context, state.outputPath.toUri())
        FFmpegKit.executeAsync(
            "-f concat -safe 0 -i ${listFile.absolutePath} -c copy $outputSafPath"
        ) { session ->
            val completed = session.state == SessionState.COMPLETED
            if (completed) {
                clearSegmentExportState()
            } else {
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
        exportSettings: ExportSettings,
        onFFmpegError: () -> Unit,
        segments: List<SegmentRange>,
        state: SegmentExportState,
    ) {
        val trim = getMergedTrim()
        val baseOffsetMs = trim?.first ?: 0L
        state.completedSegments.removeIf { index ->
            val segmentPath = segmentFilePath(state, index)
            !File(segmentPath).exists()
        }

        fun exportNextSegment(startIndex: Int) {
            val nextSegment =
                segments.drop(startIndex)
                    .firstOrNull { !state.completedSegments.contains(it.index) }
            if (nextSegment == null) {
                runConcat(context, state, segments, onFFmpegError)
                return
            }
            val segmentPath = segmentFilePath(state, nextSegment.index)
            val ffmpegInputPath =
                FFmpegKitConfig.getSafParameterForRead(context, projectData.uri.toUri())
            val segmentStartMs = baseOffsetMs + nextSegment.startMs
            FFmpegKit.executeAsync(
                "-ss ${segmentStartMs}ms -t ${nextSegment.durationMs}ms -i $ffmpegInputPath -c copy $segmentPath"
            ) { session ->
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
        transformerListener: Transformer.Listener,
        onFFmpegError: () -> Unit,
        segments: List<SegmentRange>,
        state: SegmentExportState,
    ) {
        val trim = getMergedTrim()
        val baseOffsetMs = trim?.first ?: 0L
        state.completedSegments.removeIf { index ->
            val segmentPath = segmentFilePath(state, index)
            !File(segmentPath).exists()
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
            val nextSegment =
                segments.drop(startIndex)
                    .firstOrNull { !state.completedSegments.contains(it.index) }
            if (nextSegment == null) {
                runConcat(context, state, segments, onFFmpegError)
                return
            }
            val startMs = baseOffsetMs + nextSegment.startMs
            val endMs = startMs + nextSegment.durationMs
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(startMs)
                .setEndPositionMs(endMs).build()
            val segmentMedia =
                originalMedia.buildUpon().setClippingConfiguration(clipConfig).build()
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
                            state.completedSegments.add(nextSegment.index)
                            exportNextSegment(nextSegment.index + 1)
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            result: androidx.media3.transformer.ExportResult,
                            exception: androidx.media3.transformer.ExportException,
                        ) {
                            transformerListener.onError(composition, result, exception)
                        }
                    }
                )
                .build()
            transformer!!.start(editedMediaItem, segmentFilePath(state, nextSegment.index))
        }

        exportNextSegment(0)
    }

    @SuppressLint("Recycle")
    fun export(
        context: Context,
        exportSettings: ExportSettings,
        transformerListener: Transformer.Listener,
        onFFmpegError: () -> Unit
    ) {
        // exportSettings.log()
        player.release()
        val outputPath = exportSettings.outputPath
        val totalDurationMs = getExportDurationMs(context)
        if (shouldUseSegmentedExport(context, exportSettings)) {
            val state = resolveSegmentExportState(context, exportSettings, totalDurationMs)
            val segments = buildSegmentRanges(totalDurationMs, exportSettings.segmentDurationMs)
            val filteredSegments = segments.filter { it.durationMs > 0 }
            if (filteredSegments.isEmpty()) {
                onFFmpegError()
                return
            }
            if (canUseLosslessSegmentCopy(exportSettings)) {
                startSegmentedExportWithFfmpeg(
                    context,
                    exportSettings,
                    onFFmpegError,
                    filteredSegments,
                    state,
                )
            } else {
                startSegmentedExportWithTransformer(
                    context,
                    exportSettings,
                    transformerListener,
                    onFFmpegError,
                    filteredSegments,
                    state,
                )
            }
            return
        }
        if (exportSettings.losslessCut) {
            val trim = getMergedTrim()
            if (trim != null) {
                ffmpegLosslessCut(context, trim, outputPath, false, onFFmpegError)
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
            val editedMediaItem = EditedMediaItem.Builder(trimmedExportMedia)
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
        FFmpegKit.cancel()
        transformer?.cancel()
    }

    fun getProgress(): Float {
        val ffmpegSessions = FFmpegKit.listSessions()
        return if (ffmpegSessions.isNotEmpty()) {
            val sessionState = ffmpegSessions.last().state
            return when (sessionState) {
                SessionState.COMPLETED -> 1F
                SessionState.RUNNING -> 0.5F
                SessionState.CREATED -> 0F
                else -> -1F
            }
        } else {
            val progressHolder = ProgressHolder()
            when (transformer?.getProgress(progressHolder)) {
                PROGRESS_STATE_UNAVAILABLE -> -1F
                PROGRESS_STATE_NOT_STARTED -> 1F
                else -> progressHolder.progress.toFloat() / 100F
            }
        }
    }
}

private enum class ProxyQuality(
    val key: String,
    val height: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
) {
    LOW("low", 360, 800, 96),
    MEDIUM("medium", 480, 1200, 96),
    HIGH("high", 720, 2500, 128);

    companion object {
        fun fromKey(key: String?): ProxyQuality {
            return values().firstOrNull { it.key == key } ?: MEDIUM
        }
    }
}
