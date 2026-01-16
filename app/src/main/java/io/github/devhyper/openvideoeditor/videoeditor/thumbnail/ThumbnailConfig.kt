package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import kotlin.math.roundToLong

const val THUMBNAIL_MEMORY_CACHE_BYTES = 16 * 1024 * 1024
const val THUMBNAIL_DISK_CACHE_BYTES = 64 * 1024 * 1024
const val THUMBNAIL_MIN_INTERVAL_MS = 200L
const val THUMBNAIL_PREFETCH_RATIO = 0.5f
const val THUMBNAIL_PREFETCH_MIN_MS = 2_000L
const val THUMBNAIL_PREFETCH_MAX_MS = 30_000L

fun calculateMemoryCacheBytes(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val totalBytes = activityManager.memoryClass.toLong() * 1024L * 1024L
    val target = (totalBytes * 0.05).roundToLong()
    val minBytes = 8L * 1024L * 1024L
    val maxBytes = 64L * 1024L * 1024L
    return target.coerceIn(minBytes, maxBytes).toInt()
}

fun calculateDiskCacheBytes(context: Context): Long {
    val stat = StatFs(context.cacheDir.absolutePath)
    val availableBytes = stat.availableBytes
    val target = (availableBytes * 0.02).roundToLong()
    val minBytes = 64L * 1024L * 1024L
    val maxBytes = 512L * 1024L * 1024L
    val safeMax = (availableBytes * 0.1).roundToLong().coerceAtLeast(minBytes)
    return target.coerceIn(minBytes, minOf(maxBytes, safeMax)).coerceAtMost(availableBytes)
}

fun calculatePrefetchWindowMs(viewportRangeMs: LongRange): Long {
    val durationMs = (viewportRangeMs.last - viewportRangeMs.first).coerceAtLeast(0L)
    val target = (durationMs * THUMBNAIL_PREFETCH_RATIO).roundToLong()
    return target.coerceIn(THUMBNAIL_PREFETCH_MIN_MS, THUMBNAIL_PREFETCH_MAX_MS)
}

fun calculateThumbnailMaxConcurrent(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return if (activityManager.isLowRamDevice) 1 else 2
}
