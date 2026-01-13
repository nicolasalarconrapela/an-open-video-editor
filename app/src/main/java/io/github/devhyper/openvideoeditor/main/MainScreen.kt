package io.github.devhyper.openvideoeditor.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.MediaMetadataRetriever
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.misc.PROJECT_FILE_EXT
import io.github.devhyper.openvideoeditor.settings.SettingsActivity
import io.github.devhyper.openvideoeditor.ui.theme.OpenVideoEditorTheme
import io.github.devhyper.openvideoeditor.videoeditor.ProjectData
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pickMedia: ActivityResultLauncher<PickVisualMediaRequest>,
    onOpenProject: (String) -> Unit
) {
    val activity = LocalContext.current as Activity
    val projectEntries by produceState(initialValue = emptyList<ProjectEntry>()) {
        value = loadProjectEntries(activity)
    }
    val appVersion = rememberAppVersion()
    OpenVideoEditorTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.projects_title),
                            )
                        },
                        actions = {
                            IconButton(onClick = {
                                val intent = Intent(activity, SettingsActivity::class.java)
                                activity.startActivity(intent)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                        }
                    )
                }, content = { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        ProjectsGrid(
                            projects = projectEntries,
                            onOpenProject = onOpenProject,
                            onAddProject = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        Text(
                            text = stringResource(
                                R.string.app_version,
                                appVersion.name,
                                appVersion.code
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .widthIn(max = 320.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ProjectsGrid(
    projects: List<ProjectEntry>,
    onOpenProject: (String) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gridItems = remember(projects) { projects + ProjectEntry.addNew(context) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(gridItems) { entry ->
            if (entry.isAddCard) {
                AddProjectCard(onAddProject = onAddProject)
            } else {
                ProjectCard(
                    entry = entry,
                    onOpenProject = onOpenProject
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    entry: ProjectEntry,
    onOpenProject: (String) -> Unit
) {
    val thumbnail = rememberProjectThumbnail(entry.uri)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProject(entry.uri) }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AddProjectCard(
    onAddProject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddProject() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_project),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    val subtitle: String,
    val isAddCard: Boolean = false
) {
    companion object {
        fun addNew(context: Context): ProjectEntry {
            return ProjectEntry(
                uri = "",
                title = context.getString(R.string.add_project),
                subtitle = "",
                isAddCard = true
            )
        }
    }
}

private suspend fun loadProjectEntries(context: Context): List<ProjectEntry> {
    return withContext(Dispatchers.IO) {
        val projectsDir = File(context.filesDir, "projects")
        val now = System.currentTimeMillis()
        projectsDir
            .listFiles()
            ?.filter { it.extension.equals(PROJECT_FILE_EXT, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                ProjectEntry(
                    uri = file.toUri().toString(),
                    title = file.nameWithoutExtension,
                    subtitle = buildProjectSubtitle(context, file, now)
                )
            }
            ?: emptyList()
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
