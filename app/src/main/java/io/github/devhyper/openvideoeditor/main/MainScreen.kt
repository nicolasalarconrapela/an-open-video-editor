package io.github.devhyper.openvideoeditor.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.misc.PROJECT_FILE_EXT
import io.github.devhyper.openvideoeditor.settings.SettingsActivity
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore
import io.github.devhyper.openvideoeditor.ui.theme.AbsoluteBlack
import io.github.devhyper.openvideoeditor.ui.theme.ElectricBlue
import io.github.devhyper.openvideoeditor.ui.theme.GlassBackground
import io.github.devhyper.openvideoeditor.ui.theme.GlassBorder
import io.github.devhyper.openvideoeditor.ui.theme.LocalUiCascadingEffect
import io.github.devhyper.openvideoeditor.ui.theme.OpenVideoEditorTheme
import io.github.devhyper.openvideoeditor.ui.theme.TextGray
import io.github.devhyper.openvideoeditor.ui.theme.TextWhite
import io.github.devhyper.openvideoeditor.videoeditor.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pickMedia: ActivityResultLauncher<PickVisualMediaRequest>,
    onOpenProject: (String) -> Unit
) {
    val activity = LocalContext.current as Activity
    val dataStore = remember { SettingsDataStore(activity) }
    val scope = rememberCoroutineScope()
    var refreshToken by rememberSaveable { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val projectEntries by produceState(
        initialValue = emptyList<ProjectEntry>(),
        key1 = refreshToken
    ) {
        value = loadProjectEntries(activity)
    }
    val appVersion = rememberAppVersion()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OpenVideoEditorTheme {
        // Use a Box to support the absolute black background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AbsoluteBlack)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        containerColor = ElectricBlue,
                        contentColor = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.new_project)
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    // Custom Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.projects_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextWhite
                        )
                        IconButton(onClick = {
                            val intent = Intent(activity, SettingsActivity::class.java)
                            activity.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = TextGray
                            )
                        }
                    }

                    if (projectEntries.isEmpty()) {
                        EmptyProjectsState(
                            modifier = Modifier
                                .weight(1f) // Fix layout issue by using weight instead of fillMaxSize
                                .padding(horizontal = 24.dp)
                        )
                    } else {
                        ProjectsGrid(
                            projects = projectEntries,
                            onOpenProject = { projectUri ->
                                scope.launch {
                                    dataStore.addRecentProject(projectUri)
                                }
                                onOpenProject(projectUri)
                            },
                            onProjectsChanged = { refreshToken += 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (projectEntries.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.app_version,
                                appVersion.name,
                                appVersion.code
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsGrid(
    projects: List<ProjectEntry>,
    onOpenProject: (String) -> Unit,
    onProjectsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(projects) { entry ->
            ProjectCard(
                entry = entry,
                onOpenProject = onOpenProject,
                onProjectsChanged = onProjectsChanged
            )
        }
    }
}

@Composable
private fun EmptyProjectsState(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = stringResource(R.string.create_first_project),
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.create_first_project_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProjectCard(
    entry: ProjectEntry,
    onOpenProject: (String) -> Unit,
    onProjectsChanged: () -> Unit
) {
    val thumbnail = rememberProjectThumbnail(entry.uri)
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var renameValue by rememberSaveable(entry.title) { mutableStateOf(entry.title) }
    val useUiCascadingEffect = LocalUiCascadingEffect.current
    val cardBackground = if (useUiCascadingEffect) {
        GlassBackground
    } else {
        Color(0xFF111111)
    }
    val cardBorder = if (useUiCascadingEffect) GlassBorder else Color.Transparent

    // Glassmorphism Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .border(
                BorderStroke(1.dp, cardBorder),
                RoundedCornerShape(16.dp)
            )
            .clickable { onOpenProject(entry.uri) }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF111111)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = TextGray
                        )
                    }
                }

                // Overlay gradient for text readability if needed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.project_options),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme.copy(surface = Color(0xFF1E1E1E))
                    ) {
                         DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_project), color = TextWhite) },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_project), color = TextWhite) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    maxLines = 1
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            // Removed containerColor to prevent build error
            title = { Text(stringResource(R.string.delete_project), color = TextWhite) },
            text = { Text(stringResource(R.string.confirm_delete_project), color = TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = deleteProjectFile(entry.uri)
                        if (deleted) {
                            onProjectsChanged()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = ElectricBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextGray)
                }
            }
        )
    }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            // Removed containerColor to prevent build error
            title = { Text(stringResource(R.string.rename_project), color = TextWhite) },
            text = {
                TextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.project_name), color = TextGray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = ElectricBlue,
                        focusedIndicatorColor = ElectricBlue,
                        focusedLabelColor = ElectricBlue
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val renamed = renameProjectFile(
                            projectUri = entry.uri,
                            newName = renameValue
                        )
                        if (renamed) {
                            onProjectsChanged()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = ElectricBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextGray)
                }
            }
        )
    }
}

private data class AppVersion(
    val name: String,
    val code: Long
)

@Composable
private fun rememberAppVersion(): AppVersion {
    val context = LocalContext.current
    return remember(context) { loadAppVersion(context) }
}

private fun loadAppVersion(context: Context): AppVersion {
    val packageManager = context.packageManager
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "0.0.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return AppVersion(versionName, versionCode)
}

private data class ProjectEntry(
    val uri: String,
    val title: String,
    val subtitle: String
)

private suspend fun loadProjectEntries(context: Context): List<ProjectEntry> {
    return withContext(Dispatchers.IO) {
        val dataStore = SettingsDataStore(context)
        val projectsDir = File(context.filesDir, "projects")
        val now = System.currentTimeMillis()
        val recentUris = dataStore.getRecentProjectsAsync().first()
        val allFiles = projectsDir
            .listFiles()
            ?.filter { it.extension.equals(PROJECT_FILE_EXT, ignoreCase = true) }
            ?: emptyList()
        val filesByUri = allFiles.associateBy { it.toUri().toString() }
        val recentEntries = recentUris.mapNotNull { uri ->
            filesByUri[uri]?.let { file ->
                ProjectEntry(
                    uri = uri,
                    title = file.nameWithoutExtension,
                    subtitle = buildProjectSubtitle(context, file, now)
                )
            }
        }
        val recentUriSet = recentUris.toSet()
        val remainingEntries = allFiles
            .filterNot { file -> recentUriSet.contains(file.toUri().toString()) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                ProjectEntry(
                    uri = file.toUri().toString(),
                    title = file.nameWithoutExtension,
                    subtitle = buildProjectSubtitle(context, file, now)
                )
            }
        recentEntries + remainingEntries
    }
}

private fun buildProjectSubtitle(context: Context, file: File, now: Long): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        file.lastModified(),
        now,
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val size = Formatter.formatShortFileSize(context, file.length())
    return "$relative • $size"
}

@Composable
private fun rememberProjectThumbnail(projectUri: String): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, projectUri) {
        value = withContext(Dispatchers.IO) {
            loadProjectThumbnail(context, projectUri)
        }
    }
    return thumbnail
}

private fun loadProjectThumbnail(
    context: Context,
    projectUri: String
): androidx.compose.ui.graphics.ImageBitmap? {
    return runCatching {
        val projectData = ProjectData.read(projectUri, context) ?: return null
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, projectData.uri.toUri())
        val bitmap = retriever.frameAtTime ?: return null
        retriever.release()
        bitmap.asImageBitmap()
    }.getOrNull()
}

private fun deleteProjectFile(projectUri: String): Boolean {
    return runCatching {
        val file = File(projectUri.toUri().path ?: return false)
        file.delete()
    }.getOrDefault(false)
}

private fun renameProjectFile(
    projectUri: String,
    newName: String
): Boolean {
    return runCatching {
        val file = File(projectUri.toUri().path ?: return false)
        val sanitized = newName.ifBlank { file.nameWithoutExtension }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val target = File(file.parentFile, "$sanitized.$PROJECT_FILE_EXT")
        if (target.exists()) {
            false
        } else {
            file.renameTo(target)
        }
    }.getOrDefault(false)
}
