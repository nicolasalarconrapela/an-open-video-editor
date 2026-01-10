package io.github.devhyper.openvideoeditor.videoeditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "PAUSE" -> {
                VideoExportWorker.setPaused(true)
            }
            "RESUME" -> {
                VideoExportWorker.setPaused(false)
            }
        }
    }
}
