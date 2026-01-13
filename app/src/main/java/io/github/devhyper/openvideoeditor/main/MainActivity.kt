package io.github.devhyper.openvideoeditor.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.devhyper.openvideoeditor.misc.setImmersiveMode
import io.github.devhyper.openvideoeditor.misc.setupSystemUi
import io.github.devhyper.openvideoeditor.settings.SettingsDataStore
import io.github.devhyper.openvideoeditor.videoeditor.VideoEditorActivity

class MainActivity : ComponentActivity() {
    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickProject: ActivityResultLauncher<Array<String>>
    private lateinit var requestPermissions: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSystemUi()

        val dataStore = SettingsDataStore(this)

        // Request all required permissions at startup
        requestPermissions = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Permissions handled, continue with app */ }

        requestRequiredPermissions()

        pickMedia =
            registerForActivityResult(CustomPickVisualMedia { dataStore.getLegacyFilePickerBlocking() }) { uri ->
                if (uri != null) {
                    launchVideoEditor(uri)
                }
            }
        pickProject = registerForActivityResult(
            CustomOpenDocument()
        ) { uri ->
            if (uri != null) {
                launchVideoEditor(uri)
            }
        }

        setContent {
            setImmersiveMode(false)
            MainScreen(pickMedia = pickMedia, pickProject = pickProject)
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Video/Media permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun launchVideoEditor(uri: Uri) {
        val intent = Intent(this, VideoEditorActivity::class.java)
        intent.action = Intent.ACTION_EDIT
        intent.data = uri
        startActivity(intent)
    }
}
