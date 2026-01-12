package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.THUMBNAIL_DISK_CACHE_BYTES
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class DiskThumbnailCache(
    context: Context,
    private val maxSizeBytes: Long = THUMBNAIL_DISK_CACHE_BYTES.toLong()
) {
    private val cacheDir = File(context.cacheDir, "thumbnail_cache").apply {
        mkdirs()
    }

    fun get(key: String): Bitmap? {
        val file = fileForKey(key)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    fun put(
        key: String,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP_LOSSY
    ) {
        val file = fileForKey(key)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        FileOutputStream(file).use { output ->
            bitmap.compress(format, 80, output)
        }
        file.setLastModified(System.currentTimeMillis())
        trimToSize(maxSizeBytes)
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun trimToSize(maxSize: Long = maxSizeBytes) {
        var totalSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (totalSize <= maxSize) return
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        for (file in files) {
            if (totalSize <= maxSize) break
            val size = file.length()
            if (file.delete()) {
                totalSize -= size
            }
        }
    }

    private fun fileForKey(key: String): File = File(cacheDir, "${sha256(key)}.webp")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val valueByte = byte.toInt() and 0xFF
                append(HEX_CHARS[valueByte ushr 4])
                append(HEX_CHARS[valueByte and 0x0F])
            }
        }
    }

    private companion object {
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
