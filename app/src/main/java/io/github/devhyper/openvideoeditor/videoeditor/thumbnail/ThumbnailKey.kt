package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

data class ThumbnailKey(
    val videoIdOrUri: String,
    val timeUs: Long,
    val targetWidth: Int,
    val targetHeight: Int,
    val rotationDegrees: Int,
    val zoomBucket: Int
) {
    fun keyString(): String =
        "$videoIdOrUri|$timeUs|$targetWidth|$targetHeight|$rotationDegrees|$zoomBucket"
}
