package io.github.devhyper.openvideoeditor.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.devhyper.openvideoeditor.R
import io.github.devhyper.openvideoeditor.misc.PROJECT_MIME_TYPE
import io.github.devhyper.openvideoeditor.settings.SettingsActivity
import io.github.devhyper.openvideoeditor.ui.theme.OpenVideoEditorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pickMedia: ActivityResultLauncher<PickVisualMediaRequest>,
    pickProject: ActivityResultLauncher<Array<String>>
) {
    val activity = LocalContext.current as Activity
    val buttonModifier = Modifier.widthIn(min = 220.dp)
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
                                stringResource(R.string.app_name),
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
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        {
                            Text(
                                stringResource(R.string.select_a_file_to_edit),
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.welcome_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 320.dp)
                            )
                            Button(onClick = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            }, modifier = buttonModifier) {
                                Icon(
                                    imageVector = Icons.Filled.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    style = MaterialTheme.typography.titleLarge,
                                    text = stringResource(R.string.video)
                                )
                            }
                            FilledTonalButton(onClick = {
                                pickProject.launch(
                                    arrayOf(
                                        PROJECT_MIME_TYPE
                                    )
                                )
                            }, modifier = buttonModifier) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    style = MaterialTheme.typography.titleLarge,
                                    text = stringResource(R.string.project)
                                )
                            }
                        }
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
