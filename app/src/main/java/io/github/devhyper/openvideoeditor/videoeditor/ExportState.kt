package io.github.devhyper.openvideoeditor.videoeditor

enum class ExportState(val value: String) {
    IDLE("idle"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromValue(value: String?): ExportState {
            return entries.firstOrNull { it.value == value } ?: IDLE
        }
    }
}
