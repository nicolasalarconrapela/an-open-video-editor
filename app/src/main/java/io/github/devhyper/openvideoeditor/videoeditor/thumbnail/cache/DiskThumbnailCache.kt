package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.devhyper.openvideoeditor.videoeditor.thumbnail.THUMBNAIL_DISK_CACHE_BYTES
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class DiskThumbnailCache(
    context: Context,
    private val maxSizeBytes: Long = THUMBNAIL_DISK_CACHE_BYTES.toLong()
) {
    private val cacheDir = File(context.cacheDir, "thumbnail_cache").apply {
        mkdirs()
    }
    private val index = ConcurrentHashMap<String, Boolean>()

    init {
        cacheDir.listFiles()?.forEach { file ->
            index[file.nameWithoutExtension] = true
        }
    }

    fun get(key: String): Bitmap? {
        val hash = hashForKey(key)
        if (index[hash] != true) {
            val file = fileForHash(hash)
            if (!file.exists()) return null
            index[hash] = true
        }
        val file = fileForHash(hash)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    fun contains(key: String): Boolean {
        val hash = hashForKey(key)
        if (index[hash] == true) return true
        val file = fileForHash(hash)
        return file.exists().also { exists ->
            if (exists) {
                index[hash] = true
            }
        }
    }

    fun put(
        key: String,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = defaultFormat()
    ) {
        val hash = hashForKey(key)
        val file = fileForHash(hash)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        FileOutputStream(file).use { output ->
            bitmap.compress(format, 80, output)
        }
        file.setLastModified(System.currentTimeMillis())
        index[hash] = true
        trimToSize(maxSizeBytes)
    }

    private fun defaultFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
        index.clear()
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
                index.remove(file.nameWithoutExtension)
            }
        }
    }

    private fun fileForKey(key: String): File = fileForHash(hashForKey(key))

    private fun fileForHash(hash: String): File = File(cacheDir, "$hash.webp")

    private fun hashForKey(key: String): String = sha256(key)

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
