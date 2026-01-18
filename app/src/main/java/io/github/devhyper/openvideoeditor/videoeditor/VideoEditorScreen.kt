package io.github.devhyper.openvideoeditor.videoeditor

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper.getMainLooper
import android.provider.MediaStore
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.Commands
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.misc.DropdownSetting
import io.github.devhyper.openvideoeditor.misc.ListDialog
import io.github.devhyper.openvideoeditor.misc.PLAYER_SEEK_BACK_INCREMENT
import io.github.devhyper.openvideoeditor.misc.PLAYER_SEEK_FORWARD_INCREMENT
import io.github.devhyper.openvideoeditor.misc.PROJECT_FILE_EXT
import io.github.devhyper.openvideoeditor.misc.REFRESH_RATE
import io.github.devhyper.openvideoeditor.misc.SwitchSetting
import io.github.devhyper.openvideoeditor.misc.TextfieldSetting
import io.github.devhyper.openvideoeditor.misc.formatMinSec
import io.github.devhyper.openvideoeditor.misc.getFileNameFromUri
import io.github.devhyper.openvideoeditor.misc.toLongPair
import io.github.devhyper.openvideoeditor.misc.validateUFloatAndNonzero
import io.github.devhyper.openvideoeditor.misc.validateUInt
import io.github.devhyper.openvideoeditor.settings.SettingsActivity
import io.github.devhyper.openvideoeditor.ui.theme.OpenVideoEditorTheme
import io.github.devhyper.openvideoeditor.videoeditor.state.EditorState
import io.github.devhyper.openvideoeditor.videoeditor.state.ClipSource
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailKey
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRepository
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.ThumbnailRequestCoordinator
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache.BitmapMemoryCache
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache.DiskThumbnailCache
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.calculateDiskCacheBytes
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.calculateMemoryCacheBytes
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.calculateThumbnailMaxConcurrent
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.scheduler.ThumbnailScheduler
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineClipType
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineUiClip
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelineUiTrack
import io.github.devhyper.openvideoeditor.videoeditor.timeline.ui.TimelinePrecisionView
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.scale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditorScreen(
    uri: String,
    createDocument: ActivityResultLauncher<String>,
    requestVideoPermission: ActivityResultLauncher<String>,
    viewModel: VideoEditorViewModel
) {
    val editorState by viewModel.state.collectAsState()
    val screenScope = rememberCoroutineScope()

    val context = LocalContext.current

    val controlsVisible by viewModel.controlsVisible.collectAsState()

    val player = remember {
        ExoPlayer.Builder(context)
            .apply {
                setSeekBackIncrementMs(PLAYER_SEEK_BACK_INCREMENT)
                setSeekForwardIncrementMs(PLAYER_SEEK_FORWARD_INCREMENT)
            }
            .build()
    }

    LaunchedEffect(player) {
        viewModel.startPlaybackSync(player)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        player.pause()
    }

    var currentTime by rememberSaveable { mutableLongStateOf(0L) }

    val transformManager = remember {
        viewModel.transformManager.apply {
            init(player, uri, context, viewModel, requestVideoPermission)
            player.seekTo(currentTime)
        }
    }

    var listenerRepeating by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    var fpm by remember { mutableFloatStateOf(0F) }

    var totalDuration by remember { mutableLongStateOf(0L) }

    var totalDurationFrames by remember { mutableLongStateOf(0L) }

    var currentTimeFrames by remember { mutableLongStateOf(0L) }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }

    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    var playerViewSet by remember { mutableStateOf(false) }

    val currentEditingEffect by viewModel.currentEditingEffect.collectAsState()

    val filterDurationEditorEnabled by viewModel.filterDurationEditorEnabled.collectAsState()

    val filterDurationEditorSliderPosition by viewModel.filterDurationEditorSliderPosition.collectAsState()

    val startFilterSelected by viewModel.startFilterSelected.collectAsState()

    val timelineListState = rememberLazyListState()
    val videoTrackLabel = stringResource(R.string.timeline_track_video)
    val audioTrackLabel = stringResource(R.string.timeline_track_audio)
    val overlayTrackLabel = stringResource(R.string.timeline_track_overlay)
    val timelineTracks = remember(
        editorState.clips,
        videoTrackLabel,
        audioTrackLabel,
        overlayTrackLabel
    ) {
        buildTimelineTracks(
            clips = editorState.clips,
            videoLabel = videoTrackLabel,
            audioLabel = audioTrackLabel,
            overlayLabel = overlayTrackLabel
        )
    }

    val videoTitle = remember(uri) { getFileNameFromUri(context, uri.toUri()) }

    LaunchedEffect(uri) {
        if (viewModel.state.value.clips.isEmpty()) {
            viewModel.setClips(transformManager.buildClipSources(context))
        }
        val path = Uri.parse(uri).path
        if (path != null && File(path).exists() && path.endsWith(".$PROJECT_FILE_EXT", ignoreCase = true)) {
            viewModel.setProjectOutputPath(path)
        }
    }

    val workManager = remember { WorkManager.getInstance(context) }
    // We observe all video_export works
    val exportWorkInfos by workManager.getWorkInfosByTagFlow("video_export")
        .collectAsState(initial = emptyList())

    // Track work ID from ViewModel (persists across recompositions)
    val currentExportWorkId by viewModel.currentExportWorkId.collectAsState()
    var showCompletionDialog by rememberSaveable { mutableStateOf(false) }
    val globalPaused by VideoExportWorker.isPausedFlow.collectAsState()

    // Find active export (RUNNING or ENQUEUED)
    val activeExport = exportWorkInfos.firstOrNull {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    }
    val pausedExport = if (globalPaused && currentExportWorkId != null) {
        exportWorkInfos.firstOrNull { it.id.toString() == currentExportWorkId }
    } else {
        null
    }

    LaunchedEffect(activeExport?.id) {
        if (activeExport != null && activeExport.id.toString() != currentExportWorkId) {
            viewModel.setCurrentExportWorkId(activeExport.id.toString())
        }
    }

    // Check if our session's export just finished
    val sessionExport = if (currentExportWorkId != null) {
        exportWorkInfos.firstOrNull { it.id.toString() == currentExportWorkId }
    } else null

    // Trigger completion dialog when our export finishes successfully
    if (sessionExport?.state == WorkInfo.State.SUCCEEDED && !showCompletionDialog) {
        showCompletionDialog = true
    }

    // Show progress dialog only for active exports
    if (activeExport != null) {
        ExportProgressDialog(activeExport, videoTitle, isFinished = false) {
            workManager.cancelWorkById(activeExport.id)
            viewModel.setCurrentExportWorkId(null)
        }
    } else if (globalPaused && pausedExport != null) {
        ExportProgressDialog(pausedExport, videoTitle, isFinished = false) {
            val projectDataPath =
                pausedExport.inputData.getString(VideoExportWorker.KEY_PROJECT_DATA_PATH)
            val exportSettingsPath =
                pausedExport.inputData.getString(VideoExportWorker.KEY_EXPORT_SETTINGS_PATH)
            if (projectDataPath != null && exportSettingsPath != null) {
                val intent = Intent(context, ExportActionReceiver::class.java).apply {
                    action = "CANCEL_PAUSED"
                    putExtra("projectDataPath", projectDataPath)
                    putExtra("exportSettingsPath", exportSettingsPath)
                }
                context.sendBroadcast(intent)
            }
            viewModel.setCurrentExportWorkId(null)
        }
    } else if (showCompletionDialog && sessionExport != null) {
        // Show completion dialog
        ExportProgressDialog(sessionExport, videoTitle, isFinished = true) {
            showCompletionDialog = false
            viewModel.setCurrentExportWorkId(null)
            // Prune old completed works
            workManager.pruneWork()
        }
    }

    // Initialize ThumbnailRepository
    val memoryCache = remember(context) { BitmapMemoryCache(calculateMemoryCacheBytes(context)) }
    val diskCache = remember(context) { DiskThumbnailCache(context, calculateDiskCacheBytes(context)) }
    val thumbnailScheduler = remember(context, screenScope) {
        ThumbnailScheduler(
            scope = screenScope,
            dispatcher = Dispatchers.IO,
            maxConcurrent = calculateThumbnailMaxConcurrent(context)
        )
    }
    val thumbnailRepository = remember(context, screenScope) {
        ThumbnailRepository(
            scope = screenScope,
            dispatcher = Dispatchers.IO,
            memoryCache = memoryCache,
            diskCache = diskCache,
            decode = { key ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, key.videoIdOrUri.toUri())


                    val targetWidth = key.targetWidth.coerceAtLeast(1)
                    val targetHeight = key.targetHeight.coerceAtLeast(1)
                    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            key.timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetWidth,
                            targetHeight
                        ) ?: retriever.getFrameAtTime(
                            key.timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    } else {
                        retriever.getFrameAtTime(key.timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    if (frame == null) {
                        return@ThumbnailRepository null
                    }



                    val shouldScale = frame.width != targetWidth || frame.height != targetHeight
                    val result = if (shouldScale) {
                        frame.scale(targetWidth, targetHeight).also {
                            if (it != frame) {
                                frame.recycle()
                            }
                        }
                    } else {
                        frame
                    }

                    result
                } catch (e: Exception) {
                    android.util.Log.e("ThumbnailDecode", "Exception decoding thumbnail for ${key.videoIdOrUri} at ${key.timeUs}", e)
                    null
                } finally {
                    retriever.release()
                }
            }
        )
    }
    val thumbnailCoordinator = remember(thumbnailRepository, screenScope) {
        ThumbnailRequestCoordinator(
            repository = thumbnailRepository,
            scheduler = thumbnailScheduler,
            scope = screenScope,
            ioDispatcher = Dispatchers.IO,
            mainDispatcher = Dispatchers.Main
        )
    }

    val density = LocalDensity.current
    val thumbnailKeyProvider: (Long, TimelineUiClip, Int) -> ThumbnailKey = remember(density) {
        { timeUs, clip, zoom ->
            val baseWidth = with(density) { 96.dp.roundToPx() }.coerceAtLeast(1)
            val baseHeight = with(density) { 72.dp.roundToPx() }.coerceAtLeast(1)
            val scale = when (zoom) {
                0 -> 0.75f
                1 -> 1.0f
                else -> 1.25f
            }
            val targetWidth = (baseWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (baseHeight * scale).roundToInt().coerceAtLeast(1)
            ThumbnailKey(
                videoIdOrUri = clip.mediaUri,
                timeUs = timeUs,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                rotationDegrees = 0,
                zoomBucket = zoom
            )
        }
    }

    var textureView by remember { mutableStateOf<TextureView?>(null) }

    OpenVideoEditorTheme(forceDarkTheme = true, forceBlackStatusBar = true) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                DisposableEffect(key1 = Unit) {
                    val listenerHandler = Handler(getMainLooper())
                    val listener =
                        object : Player.Listener {
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.e(
                                    "VideoEditor",
                                    "Player error: ${error.message}",
                                    error
                                )
                            }

                            override fun onAvailableCommandsChanged(
                                availableCommands: Commands
                            ) {
                                super.onAvailableCommandsChanged(availableCommands)

                                val currentTextureView = textureView
                                if (!playerViewSet && availableCommands.contains(Player.COMMAND_SET_VIDEO_SURFACE) && currentTextureView != null) {
                                    player.setVideoTextureView(currentTextureView)
                                    playerViewSet = true
                                }

                                if (availableCommands.contains(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
                                    viewModel.setFilterDurationEditorSliderPosition(0f..player.duration.toFloat())
                                }
                            }

                            override fun onEvents(
                                regularPlayer: Player,
                                events: Player.Events
                            ) {
                                super.onEvents(player, events)

                                if (player.duration > 0L) {
                                    totalDuration = player.duration
                                    fpm = (player.videoFormat?.frameRate ?: 0F) / 1000F
                                    if (fpm > 0F) {
                                        totalDurationFrames = (totalDuration * fpm).toLong()
                                    }
                                }

                                isPlaying = player.isPlaying
                                playbackState = player.playbackState

                                if (isPlaying) {
                                    if (!listenerRepeating) {
                                        listenerRepeating = true
                                        listenerHandler.post(
                                            object : Runnable {
                                                override fun run() {
                                                    currentTime =
                                                        player.currentPosition.coerceAtLeast(0L)
                                                    currentTimeFrames =
                                                        ((currentTime * fpm).toLong()).coerceAtMost(
                                                            totalDurationFrames
                                                        )
                                                    if (listenerRepeating) {
                                                        listenerHandler.postDelayed(
                                                            this,
                                                            REFRESH_RATE
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    listenerRepeating = false
                                    currentTime =
                                        player.currentPosition.coerceAtLeast(0L)
                                    currentTimeFrames =
                                        ((currentTime * fpm).toLong()).coerceAtMost(
                                            totalDurationFrames
                                        )
                                }
                            }
                        }

                    player.addListener(listener)

                    onDispose {
                        player.removeListener(listener)
                        player.release()
                    }
                }

                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(scale, offset, textureView) {
                    val view = textureView
                    if (view != null && view.width > 0) {
                        val matrix = Matrix()
                        val centerX = view.width / 2f
                        val centerY = view.height / 2f
                        matrix.postScale(scale, scale, centerX, centerY)
                        matrix.postTranslate(offset.x, offset.y)
                        view.setTransform(matrix)
                    }
                }

                val androidViewModifier = Modifier
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 10f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { viewModel.setControlsVisible(!controlsVisible) }
                        )
                    }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                ) {
                    AndroidView(
                        modifier = androidViewModifier.fillMaxSize(),
                        factory = {
                            textureView = TextureView(context).apply {
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                            }
                            textureView!!
                        }
                    )

                    val videoFormat = player.videoFormat
                    if (videoFormat != null) {
                        Box(
                            modifier = Modifier
                                .width(videoFormat.width.dp)
                                .height(videoFormat.height.dp)
                                .align(Alignment.Center)
                                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                        ) {
                            currentEditingEffect?.Editor()
                        }
                    }

                    PlayerControls(
                        modifier = Modifier
                            .fillMaxSize(),
                        isVisible = { controlsVisible },
                        isPlaying = { isPlaying },
                        title = { getFileNameFromUri(context, uri.toUri()) },
                        transformManager = transformManager,
                        createDocument = createDocument,
                        playbackState = { playbackState },
                        onReplayClick = { player.seekBack() },
                        onForwardClick = { player.seekForward() },
                        onPauseToggle = {
                            when {
                                player.isPlaying -> {
                                    player.pause()
                                }

                                player.isPlaying.not() &&
                                        playbackState == Player.STATE_ENDED -> {
                                    player.seekTo(0)
                                    player.playWhenReady = true
                                }

                                else -> {
                                    player.play()
                                }
                            }
                            isPlaying = isPlaying.not()
                        },
                        playbackSpeed = { playbackSpeed },
                        onPlaybackSpeedChange = { speed ->
                            playbackSpeed = speed
                            player.playbackParameters = PlaybackParameters(speed)
                        },
                        onCaptureClick = {
                            screenScope.launch(Dispatchers.IO) {
                                saveFrame(context, uri, currentTime)
                            }
                        },
                        viewModel = viewModel
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                ) {
                    BottomControls(
                        modifier = Modifier.fillMaxWidth(),
                        fpm = { fpm },
                        totalDuration = { totalDuration },
                        totalDurationFrames = { totalDurationFrames },
                        currentTime = { currentTime },
                        currentTimeFrames = { currentTimeFrames },
                        onSeekChanged = { timeMs ->
                            if (filterDurationEditorEnabled) {
                                var range: ClosedFloatingPointRange<Float>? = null
                                if (startFilterSelected && timeMs < filterDurationEditorSliderPosition.endInclusive) {
                                    range = timeMs..filterDurationEditorSliderPosition.endInclusive
                                } else if (!startFilterSelected && timeMs > filterDurationEditorSliderPosition.start) {
                                    range = filterDurationEditorSliderPosition.start..timeMs
                                }
                                if (range != null) {
                                    viewModel.setFilterDurationEditorSliderPosition(range)
                                    player.seekTo(timeMs.toLong())
                                }
                            } else {
                                player.seekTo(timeMs.toLong())
                            }
                        },
                        transformManager = transformManager,
                        editorState = editorState,
                        timelineTracks = timelineTracks,
                        timelineListState = timelineListState,
                        onPlayerSeek = { timeMs -> player.seekTo(timeMs) },
                        viewModel = viewModel,
                        thumbnailCoordinator = thumbnailCoordinator,
                        thumbnailKeyProvider = thumbnailKeyProvider
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean,
    isPlaying: () -> Boolean,
    title: () -> String,
    transformManager: TransformManager,
    createDocument: ActivityResultLauncher<String>,
    onReplayClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPauseToggle: () -> Unit,
    playbackState: () -> Int,
    playbackSpeed: () -> Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onCaptureClick: () -> Unit,
    viewModel: VideoEditorViewModel
) {

    val visible = remember(isVisible()) { isVisible() }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = SolidColor(colorScheme.scrim),
                    alpha = 0.5F
                )
        ) {
            Box(
                modifier = Modifier
                    .safeContentPadding()
                    .fillMaxSize()
            ) {
                TopControls(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    title = title,
                    transformManager = transformManager,
                    createDocument = createDocument,
                    onCaptureClick = onCaptureClick,
                    viewModel = viewModel
                )

                CenterControls(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    isPlaying = isPlaying,
                    onReplayClick = onReplayClick,
                    onForwardClick = onForwardClick,
                    onPauseToggle = onPauseToggle,
                    playbackState = playbackState,
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = onPlaybackSpeedChange
                )
            }
        }
    }
}

@Composable
private fun TopControls(
    modifier: Modifier = Modifier,
    title: () -> String,
    transformManager: TransformManager,
    createDocument: ActivityResultLauncher<String>,
    onCaptureClick: () -> Unit,
    viewModel: VideoEditorViewModel
) {
    val activity = LocalContext.current as Activity
    val projectOutputPath by viewModel.projectOutputPath.collectAsState()
    val projectSavingSupported by viewModel.projectSavingSupported.collectAsState()
    val videoTitle = remember(title()) { title() }
    val scope = rememberCoroutineScope()
    var showThreeDotMenu by remember { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { activity.finish() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }

        IconButton(onClick = onCaptureClick) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capture Frame",
                tint = Color.White
            )
        }

        Text(
            text = videoTitle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f, false)
        )

        IconButton(onClick = { showThreeDotMenu = !showThreeDotMenu }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_vertical_options)
            )
            DropdownMenu(
                expanded = showThreeDotMenu,
                onDismissRequest = { showThreeDotMenu = false },
                content = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = {
                            showThreeDotMenu = false
                            val intent = Intent(activity, SettingsActivity::class.java)
                            activity.startActivity(intent)
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = { showThreeDotMenu = false; showExportDialog = true })
                    DropdownMenuItem(
                        enabled = projectSavingSupported,
                        text = { Text(stringResource(R.string.save_project)) },
                        onClick = {
                            showThreeDotMenu = false
                            scope.launch(Dispatchers.IO) {
                                val savedPath = saveInternalProject(
                                    activity = activity,
                                    transformManager = transformManager,
                                    videoTitle = videoTitle,
                                    existingPath = projectOutputPath
                                )
                                if (savedPath != null) {
                                    viewModel.setProjectOutputPath(savedPath)
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            activity.getString(R.string.project_saved),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            activity.getString(R.string.project_save_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        })
                })
        }
    }

    if (showExportDialog) {
        ExportDialog(transformManager, createDocument, videoTitle, activity, viewModel) {
            showExportDialog = false
        }
    }
}

@Composable
private fun CenterControls(
    modifier: Modifier = Modifier,
    isPlaying: () -> Boolean,
    playbackState: () -> Int,
    onReplayClick: () -> Unit,
    onPauseToggle: () -> Unit,
    onForwardClick: () -> Unit,
    playbackSpeed: () -> Float,
    onPlaybackSpeedChange: (Float) -> Unit
) {
    val isVideoPlaying = remember(isPlaying()) { isPlaying() }

    val playerState = remember(playbackState()) { playbackState() }

    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(modifier = Modifier.size(40.dp), onClick = onReplayClick) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                imageVector = Icons.Filled.Replay5,
                contentDescription = stringResource(R.string.replay_5_seconds),
            )
        }

        IconButton(modifier = Modifier.size(40.dp), onClick = onPauseToggle) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                imageVector =
                    when {
                        isVideoPlaying -> {
                            Icons.Filled.Pause
                        }

                        playerState == Player.STATE_ENDED -> {
                            Icons.Filled.Replay
                        }

                        else -> {
                            Icons.Filled.PlayArrow
                        }
                    },
                contentDescription = stringResource(R.string.play_pause),
            )
        }

        IconButton(modifier = Modifier.size(40.dp), onClick = onForwardClick) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                imageVector = Icons.Filled.Forward10,
                contentDescription = stringResource(R.string.forward_10_seconds),
            )
        }

        Box {
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = "${playbackSpeed()}x",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val speeds = listOf(0.1f, 0.2f, 0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                speeds.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("${speed}x") },
                        onClick = {
                            onPlaybackSpeedChange(speed)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    fpm: () -> Float,
    totalDuration: () -> Long,
    totalDurationFrames: () -> Long,
    currentTime: () -> Long,
    currentTimeFrames: () -> Long,
    onSeekChanged: (timeMs: Float) -> Unit,
    transformManager: TransformManager,
    editorState: EditorState,
    timelineTracks: List<TimelineUiTrack>,
    timelineListState: LazyListState,
    onPlayerSeek: (Long) -> Unit,
    viewModel: VideoEditorViewModel,
    thumbnailCoordinator: ThumbnailRequestCoordinator,
    thumbnailKeyProvider: ((timeUs: Long, clip: TimelineUiClip, zoomBucket: Int) -> ThumbnailKey)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filterSheetState = rememberModalBottomSheetState()
    val layerSheetState = rememberModalBottomSheetState()
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showLayerBottomSheet by remember { mutableStateOf(false) }
    var showFrameDialog by remember { mutableStateOf(false) }

    val videoFpm = remember(fpm()) { fpm() }
    remember(totalDuration()) { totalDuration() }
    val durationFrames = remember(totalDurationFrames()) { totalDurationFrames() }
    remember(currentTime()) { currentTime() }
    val videoTimeFrames = remember(currentTimeFrames()) { currentTimeFrames() }

    Column(
        modifier = modifier
            .padding(bottom = 0.dp)
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0E0F12))
        ) {
            TimelinePrecisionView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                tracks = timelineTracks,
                zoomLevel = editorState.zoomLevel,
                currentTimeMs = editorState.currentTimeMs,
                listState = timelineListState,
                onZoom = { zoomDelta ->
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.ZoomByDelta(zoomDelta))
                },
                onTrim = { clipId, trimInMs, trimOutMs ->
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.SelectBlock(clipId))
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.Trim(trimInMs, trimOutMs))
                },
                onSplit = { clipId, atMs ->
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.SelectBlock(clipId))
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.Split(atMs))
                },
                onMove = { fromIndex, toIndex ->
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.MoveBlock(fromIndex, toIndex))
                },
                onClipSelected = { clip ->
                    viewModel.onEvent(VideoEditorViewModel.EditorEvent.SelectBlock(clip.id))
                },
                onSeek = { timeMs -> onPlayerSeek(timeMs) },
                thumbnailCoordinator = thumbnailCoordinator,
                thumbnailKeyProvider = thumbnailKeyProvider,
                selectedClipId = editorState.selectedBlockId
            )
        }

        EditorToolRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            tools = listOf(
                EditorToolAction(
                    label = stringResource(R.string.video_filters),
                    icon = Icons.Filled.Filter,
                    onClick = { showFilterBottomSheet = true }
                ),
                EditorToolAction(
                    label = stringResource(R.string.video_layers),
                    icon = Icons.Filled.Layers,
                    onClick = { showLayerBottomSheet = true }
                ),
                EditorToolAction(
                    label = stringResource(R.string.frames),
                    icon = Icons.Filled.PhotoCamera,
                    onClick = { showFrameDialog = true }
                ),
                EditorToolAction(
                    label = stringResource(R.string.text),
                    icon = Icons.Filled.TextFields,
                    onClick = {
                        val effect = onVideoUserEffectsArray.firstOrNull { effect ->
                            effect.stringResId == R.string.text
                        }
                        if (effect != null) {
                            viewModel.setCurrentEditingEffect(effect)
                            viewModel.setControlsVisible(false)
                        }
                    }
                ),
                EditorToolAction(
                    label = stringResource(R.string.crop),
                    icon = Icons.Filled.ContentCut,
                    onClick = {
                        val effect = onVideoUserEffectsArray.firstOrNull { effect ->
                            effect.stringResId == R.string.crop
                        }
                        if (effect != null) {
                            viewModel.setCurrentEditingEffect(effect)
                            viewModel.setControlsVisible(false)
                        }
                    }
                )
            )
        )
    }
    when {
        showFilterBottomSheet -> {
            ModalBottomSheet(
                modifier = Modifier.fillMaxSize(),
                onDismissRequest = {
                    showFilterBottomSheet = false
                },
                sheetState = filterSheetState
            ) {
                FilterDrawer(transformManager, viewModel) {
                    scope.launch { filterSheetState.hide() }.invokeOnCompletion {
                        if (!filterSheetState.isVisible) {
                            showFilterBottomSheet = false
                        }
                    }
                }
            }
        }

        showLayerBottomSheet -> {
            ModalBottomSheet(
                modifier = Modifier.fillMaxSize(),
                onDismissRequest = {
                    showLayerBottomSheet = false
                },
                sheetState = layerSheetState
            ) {
                LayerDrawer(transformManager)
            }
        }

        showFrameDialog -> {
            var newFrame by remember { mutableLongStateOf(-1L) }
            ListDialog(
                title = stringResource(R.string.frames),
                dismissText = stringResource(R.string.dismiss),
                acceptText = stringResource(R.string.accept),
                onDismissRequest = { showFrameDialog = false },
                onAcceptRequest = {
                    if (newFrame >= 0L) {
                        showFrameDialog = false
                        val timeMs = (newFrame / videoFpm) + 1F
                        onSeekChanged(timeMs)
                    }
                },
                listItems = {
                    item {
                        Text("$videoTimeFrames/$durationFrames")
                        TextfieldSetting(
                            name = stringResource(R.string.new_frame),
                            keyboardType = KeyboardType.Number,
                            onValueChanged = {
                                val errorTxt = validateUInt(it)
                                if (errorTxt.isEmpty()) {
                                    val newLongFrame = it.toLong()
                                    if (newLongFrame <= durationFrames) {
                                        newFrame = newLongFrame
                                    } else {
                                        newFrame = -1L
                                        return@TextfieldSetting context.getString(R.string.input_frame_must_less_or_equal) + " $durationFrames"
                                    }
                                }
                                errorTxt
                            })
                    }
                }
            )
        }
    }
}

private fun buildTimelineTracks(
    clips: List<ClipSource>,
    videoLabel: String,
    audioLabel: String,
    overlayLabel: String
): List<TimelineUiTrack> {
    val videoClips = clips
        .filter { it.type == TimelineClipType.Video }
        .map { it.toUiClip() }
    val audioClips = clips
        .filter { it.type == TimelineClipType.Audio }
        .map { it.toUiClip() }
    val overlayClips = clips
        .filter { it.type == TimelineClipType.Overlay }
        .map { it.toUiClip() }
    return listOf(
        TimelineUiTrack(id = "track-video", label = videoLabel, clips = videoClips),
        TimelineUiTrack(id = "track-audio", label = audioLabel, clips = audioClips),
        TimelineUiTrack(id = "track-overlay", label = overlayLabel, clips = overlayClips)
    )
}

private fun ClipSource.toUiClip(): TimelineUiClip = TimelineUiClip(
    id = id,
    durationMs = durationMs,
    label = label,
    type = type,
    mediaUri = mediaUri
)

@Composable
private fun MiniPreviewStrip(
    modifier: Modifier = Modifier,
    transformManager: TransformManager,
    durationMs: Long,
    currentTimeMs: Long,
) {
    val context = LocalContext.current
    val previewSource = remember(durationMs) { transformManager.getPreviewSource(context) }
    val previewFrames by produceState(
        initialValue = emptyList<androidx.compose.ui.graphics.ImageBitmap>(),
        previewSource,
        durationMs
    ) {
        value = withContext(Dispatchers.IO) {
            if (durationMs <= 0L) {
                emptyList()
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    if (previewSource.startsWith("content://") || previewSource.startsWith("file://")) {
                        retriever.setDataSource(context, previewSource.toUri())
                    } else {
                        retriever.setDataSource(previewSource)
                    }
                    val frameCount = 8
                    val stepMs = (durationMs / frameCount).coerceAtLeast(1L)
                    val frames = mutableListOf<androidx.compose.ui.graphics.ImageBitmap>()
                    for (index in 0 until frameCount) {
                        val timeUs = (index * stepMs) * 1000L
                        val bitmap = retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (bitmap != null) {
                            frames.add(bitmap.asImageBitmap())
                        }
                    }
                    frames
                } catch (_: Exception) {
                    emptyList()
                } finally {
                    retriever.release()
                }
            }
        }
    }
    val progress =
        if (durationMs > 0L) (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(
            0f,
            1f
        ) else 0f
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .border(1.dp, colorScheme.outline, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant)
    ) {
        if (previewFrames.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(previewFrames.size) { index ->
                    Image(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(72.dp),
                        bitmap = previewFrames[index],
                        contentDescription = null
                    )
                }
            }
        }
        val progressColor = colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val xPos = size.width * progress
            drawLine(
                color = progressColor,
                start = Offset(xPos, 0f),
                end = Offset(xPos, size.height),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
private fun LayerDrawer(transformManager: TransformManager) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.video_layers),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.headlineMedium
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            items(transformManager.projectData.videoEffects)
            { effect ->
                LayerDrawerItem(
                    stringResId = effect.stringResId,
                    icon = effect.icon(),
                    range = 0L..transformManager.player.duration,
                    onClick = {
                        transformManager.removeVideoEffect(effect)
                    }
                )
            }
            /*
            items(transformManager.projectData.audioProcessors)
            { processor ->
                LayerDrawerItem(
                    stringResId = processor.toString(),
                    icon = Icons.Filled.Audiotrack,
                    range = 0L..transformManager.player.duration,
                    onClick = {
                        transformManager.removeAudioProcessor(processor)
                    }
                )
            }
            */
            val trim = transformManager.getMergedTrim()
            if (trim != null) {
                item()
                {
                    LayerDrawerItem(
                        stringResId = R.string.trim,
                        icon = Icons.Filled.ContentCut,
                        range = trim.first..trim.second,
                        onClick = {
                            transformManager.clearMediaTrims()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerDrawerItem(
    stringResId: Int,
    icon: ImageVector,
    range: LongRange,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(R.string.layer_icon))
            Column(
                modifier = Modifier
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(stringResId))
                Text(
                    text = "${range.first.formatMinSec()}:${range.last.formatMinSec()}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.remove_filter)
            )
        }
    }
}

@Composable
private fun FilterDrawer(
    transformManager: TransformManager,
    viewModel: VideoEditorViewModel,
    onDismissRequest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.video_filters),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.headlineMedium
        )
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            columns = GridCells.Adaptive(120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FilterDrawerItem(R.string.trim, Icons.Filled.ContentCut, onClick = {
                    viewModel.setFilterDurationEditorEnabled(true)
                    viewModel.setFilterDurationCallback { range ->
                        transformManager.addMediaTrim(
                            range.toLongPair()
                        )
                    }
                    onDismissRequest()
                })
            }
            items(userEffectsArray) { userEffect ->
                userEffect.run {
                    FilterDrawerItem(
                        stringResId,
                        icon(),
                        onClick = { transformManager.addVideoEffect(this) }
                    )
                }
            }
            items(dialogUserEffectsArray) { dialogUserEffect ->
                dialogUserEffect.run {
                    DialogFilterDrawerItem(
                        stringResId,
                        icon,
                        args,
                        transformManager,
                        viewModel,
                        callback
                    )
                }
            }
            items(onVideoUserEffectsArray) { onVideoUserEffect ->
                onVideoUserEffect.run {
                    FilterDrawerItem(stringResId, icon()) {
                        callback = {
                            transformManager.addVideoEffect(UserEffect(stringResId, icon, it))
                        }
                        viewModel.setCurrentEditingEffect(this)
                        onDismissRequest()
                        viewModel.setControlsVisible(false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogFilterDrawerItem(
    stringResId: Int,
    icon: ImageConstructor,
    args: PersistentList<EffectDialogSetting>,
    transformManager: TransformManager,
    viewModel: VideoEditorViewModel,
    callback: (Map<String, String>) -> EffectConstructor
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    FilterDrawerItem(
        stringResId,
        icon(),
        onClick = { showFilterDialog = true; viewModel.setFilterDialogArgs(args) })
    if (showFilterDialog) {
        FilterDialog(stringResId = stringResId, viewModel = viewModel, callback = { argMap ->
            val effect = callback(argMap)
            UserEffect(stringResId, icon, effect)
        }, transformManager) {
            showFilterDialog = false
        }
    }
}

@Composable
private fun FilterDrawerItem(
    stringResId: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(stringResId)
            )
        }
        Text(
            textAlign = TextAlign.Center,
            softWrap = false,
            text = stringResource(stringResId)
        )
    }
}

@Composable
private fun FilterDialog(
    stringResId: Int,
    viewModel: VideoEditorViewModel,
    callback: (Map<String, String>) -> UserEffect,
    transformManager: TransformManager,
    onDismissRequest: () -> Unit
) {
    val args by viewModel.filterDialogArgs.collectAsState()
    ListDialog(
        title = stringResource(stringResId),
        dismissText = stringResource(R.string.cancel),
        acceptText = stringResource(R.string.add),
        onDismissRequest = onDismissRequest,
        onAcceptRequest = {
            var error = false
            val callbackArgsMap = mutableMapOf<String, String>()
            for (arg in args) {
                val string = arg.selection
                if (string.isEmpty()) {
                    error = true
                    break
                }
                callbackArgsMap[arg.key] = string
            }
            if (!error) {
                val userEffect = callback(callbackArgsMap.toMap())
                transformManager.addVideoEffect(userEffect)
                onDismissRequest()
            }
        },
    ) {
        for (arg in args) {
            val textfield = arg.textfieldValidation
            val dropdown = arg.dropdownOptions
            if (textfield != null) {
                item {
                    TextfieldSetting(
                        name = stringResource(arg.stringResId),
                        onValueChanged = {
                            val error = textfield(it)
                            if (error.isEmpty()) {
                                arg.selection = it
                            } else {
                                arg.selection = ""
                            }
                            viewModel.setFilterDialogArgs(args)
                            error
                        })
                }
            } else if (dropdown != null) {
                item {
                    DropdownSetting(
                        name = stringResource(arg.stringResId),
                        options = dropdown.toImmutableList()
                    ) {
                        arg.selection = it
                        viewModel.setFilterDialogArgs(args)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    transformManager: TransformManager,
    createDocument: ActivityResultLauncher<String>,
    title: String,
    activity: Activity,
    viewModel: VideoEditorViewModel,
    onDismissRequest: () -> Unit
) {
    val outputPath by viewModel.outputPath.collectAsState()
    val exportDismissRequest = {
        onDismissRequest()
        viewModel.setOutputPath("")
        activity.recreate()
    }

    val context = LocalContext.current
    val exportSettings: ExportSettings by remember { mutableStateOf(ExportSettings()) }
    var exportString: String? by remember { mutableStateOf(null) }
    var infoDialogText by remember { mutableStateOf("") }
    if (outputPath.isNotEmpty()) {
        exportSettings.outputPath = outputPath
        if (exportString != null) {
            ExportFailedAlertDialog(exportString!!) {
                exportString = null; exportDismissRequest()
            }
        } else {
            // Trigger WorkManager
            SideEffect {
                val workId = startExportWork(context, transformManager, exportSettings)
                if (workId != null) {
                    viewModel.setCurrentExportWorkId(workId)
                }
                // Clear outputPath so the settings dialog shows again on next export
                viewModel.setOutputPath("")
                onDismissRequest() // Close the settings dialog
            }
        }
    } else {
        ListDialog(
            title = stringResource(R.string.export),
            dismissText = stringResource(R.string.cancel),
            acceptText = stringResource(R.string.export),
            onDismissRequest = onDismissRequest,
            onAcceptRequest = {
                val dotIndex: Int = title.lastIndexOf('.')
                // Use original filename with .mp4 extension
                val fileName: String = if (dotIndex > 0) {
                    title.take(dotIndex) + ".mp4"
                } else {
                    "$title.mp4"
                }
                createDocument.launch(fileName)
            },
        ) {
            item {
                DropdownSetting(
                    name = stringResource(R.string.media_to_export),
                    options = getMediaToExportStrings()
                ) {
                    exportSettings.setMediaToExportString(it)
                }
            }
            item {
                DropdownSetting(
                    name = stringResource(R.string.hdr_mode),
                    options = getHdrModesStrings()
                ) {
                    exportSettings.setHdrModeString(it)
                }
            }
            item {
                DropdownSetting(
                    name = stringResource(R.string.audio_type),
                    options = getAudioMimeTypesStrings()
                ) {
                    exportSettings.setAudioMimeTypeString(it)
                }
            }
            item {
                DropdownSetting(
                    name = stringResource(R.string.video_type),
                    options = getVideoMimeTypesStrings()
                ) {
                    exportSettings.setVideoMimeTypeString(it)
                }
            }
            item {
                TextfieldSetting(
                    name = stringResource(R.string.speed),
                    keyboardType = KeyboardType.Decimal
                ) {
                    val errorMsg = validateUFloatAndNonzero(it)
                    if (errorMsg.isEmpty()) {
                        exportSettings.speed = it.toFloat()
                    } else {
                        exportSettings.speed = 0F
                    }
                    errorMsg
                }
            }
            item {
                TextfieldSetting(
                    name = stringResource(R.string.framerate),
                    keyboardType = KeyboardType.Decimal
                ) {
                    var errorMsg = validateUFloatAndNonzero(it)
                    if (errorMsg.isEmpty()) {
                        val framerate = it.toFloat()
                        val originalFramerate =
                            transformManager.player.videoFormat?.frameRate
                        if (originalFramerate != null && framerate >= originalFramerate) {
                            errorMsg =
                                context.getString(R.string.framerate_must_lower) + "($originalFramerate)."
                        } else {
                            exportSettings.framerate = framerate
                        }
                    } else {
                        exportSettings.framerate = 0F
                    }
                    errorMsg
                }
            }
            item {
                SwitchSetting(
                    name = stringResource(R.string.lossless_cut),
                    enabled = transformManager.projectData.mediaTrims.isNotEmpty(),
                    startChecked = false
                ) {
                    exportSettings.losslessCut = it
                    if (it) {
                        infoDialogText =
                            context.getString(R.string.enabling_lossless_cut_will_only_export_trims)
                    }
                }
            }
        }
        if (infoDialogText.isNotEmpty()) {
            AlertDialog(
                title = { Text(stringResource(R.string.setting_info)) },
                text = { Text(infoDialogText) },
                onDismissRequest = { infoDialogText = "" },
                confirmButton = {
                    TextButton(
                        onClick = {
                            infoDialogText = ""
                        }
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                })
        }
    }
}

@Composable
fun ExportProgressDialog(
    workInfo: WorkInfo,
    videoTitle: String,
    isFinished: Boolean,
    onDismissOrCancel: () -> Unit
) {
    val context = LocalContext.current
    val progress = workInfo.progress.getFloat(VideoExportWorker.KEY_PROGRESS, 0f)
    val projectDataPath =
        workInfo.inputData.getString(VideoExportWorker.KEY_PROJECT_DATA_PATH)
    val exportSettingsPath =
        workInfo.inputData.getString(VideoExportWorker.KEY_EXPORT_SETTINGS_PATH)

    val animatedProgress = animateFloatAsState(
        targetValue = if (isFinished) 1f else progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "export_progress_animation"
    ).value

    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isFinished) stringResource(R.string.exported) else stringResource(R.string.exporting),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isFinished) {
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    trackColor = colorScheme.inversePrimary,
                )

                Text(
                    text = "${((if (isFinished) 1f else progress) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 16.dp)
                )

                if (isFinished) {
                    TextButton(
                        onClick = onDismissOrCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                } else {
                    val globalPaused by VideoExportWorker.isPausedFlow.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onDismissOrCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }

                        TextButton(
                            onClick = {
                                val action = if (globalPaused) "RESUME" else "PAUSE"
                                val intent = Intent(context, ExportActionReceiver::class.java).apply {
                                    this.action = action
                                    if (action == "PAUSE") {
                                        putExtra("workerId", workInfo.id.toString())
                                    } else {
                                        if (projectDataPath != null && exportSettingsPath != null) {
                                            putExtra("projectDataPath", projectDataPath)
                                            putExtra("exportSettingsPath", exportSettingsPath)
                                        } else {
                                            android.util.Log.e(
                                                "ExportDebug",
                                                "❌ Resume failed: missing paths in dialog."
                                            )
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.export_resume_missing_data),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@TextButton
                                        }
                                    }
                                }
                                context.sendBroadcast(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (globalPaused) stringResource(R.string.resume) else stringResource(
                                    R.string.pause
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun startExportWork(
    context: Context,
    transformManager: TransformManager,
    exportSettings: ExportSettings
): String? {
    // Use unique filenames to prevent conflicts if multiple exports are triggered
    val uniqueId = java.util.UUID.randomUUID().toString()
    val projectDataFile = File(context.cacheDir, "project_data_$uniqueId.tmp")
    val settingsFile = File(context.cacheDir, "export_settings_$uniqueId.tmp")

    try {
        ObjectOutputStream(projectDataFile.outputStream()).use { it.writeObject(transformManager.projectData) }
        ObjectOutputStream(settingsFile.outputStream()).use { it.writeObject(exportSettings) }

        val inputData = workDataOf(
            VideoExportWorker.KEY_PROJECT_DATA_PATH to projectDataFile.absolutePath,
            VideoExportWorker.KEY_EXPORT_SETTINGS_PATH to settingsFile.absolutePath
        )

        val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
            .setInputData(inputData)
            .addTag("video_export")
            .build()

        // Use enqueueUniqueWork with REPLACE to ensure only one export runs at a time
        // If user clicks export again, the previous one is cancelled and replaced
        WorkManager.getInstance(context).enqueueUniqueWork(
            "video_export_main",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id.toString()
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun ExportFailedAlertDialog(exceptionString: String, onDismissRequest: () -> Unit) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.error))
        },
        text = {
            Text(text = exceptionString)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {

        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

private suspend fun saveFrame(context: Context, uri: String, timeMs: Long) {
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            val bitmap =
                retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val filename = "frame_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenVideoEditor")
                }
                val resolver = context.contentResolver
                val imageUri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Frame saved to Pictures",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to capture frame",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } finally {
            retriever.release()
        }
    }
}

private data class EditorToolAction(
    val label: String,
    val icon: ImageVector,
    val hasBadge: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun EditorToolRow(
    tools: List<EditorToolAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { tool ->
            EditorToolButton(tool = tool)
        }
    }
}

@Composable
private fun EditorToolButton(tool: EditorToolAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box {
            IconButton(onClick = tool.onClick) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (tool.hasBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252))
                        .offset(x = 2.dp, y = (-2).dp)
                )
            }
        }
    }
}

@Composable
private fun AddButton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFECECEC)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Añadir",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun createInternalProjectFile(context: Context, videoTitle: String): File {
    val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    val baseName = videoTitle.substringBeforeLast('.').ifBlank { "project" }
    val sanitized = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
    var projectFile = File(projectsDir, "$sanitized.$PROJECT_FILE_EXT")
    if (projectFile.exists()) {
        val timestamp = System.currentTimeMillis()
        projectFile = File(projectsDir, "${sanitized}_$timestamp.$PROJECT_FILE_EXT")
    }
    return projectFile
}

private fun saveInternalProject(
    activity: Activity,
    transformManager: TransformManager,
    videoTitle: String,
    existingPath: String?
): String? {
    return runCatching {
        val projectFile = if (!existingPath.isNullOrEmpty()) {
            File(existingPath)
        } else {
            createInternalProjectFile(activity, videoTitle)
        }
        transformManager.projectData.write(projectFile.toUri().toString(), activity)
        projectFile.absolutePath
    }.getOrNull()
}
