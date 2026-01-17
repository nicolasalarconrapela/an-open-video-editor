package io.github.devhyper.openvideoeditor.videoeditor.thumbnail

/**
 * LOD (Level of Detail) Configuration for Timeline Thumbnails.
 * Maps zoom levels to discrete buckets for stable thumbnail density.
 */
object ThumbnailLODConfig {
    
    /**
     * Discrete LOD buckets.
     * - OVERVIEW: Very sparse, for quick loading
     * - NORMAL: Medium density
     * - DETAILED: High density
     * - ULTRA: Maximum precision (near 1:1)
     */
    enum class LODBucket(val level: Int) {
        OVERVIEW(0),
        NORMAL(1),
        DETAILED(2),
        ULTRA(3)
    }
    
    /**
     * Configuration for each LOD level.
     * @param minIntervalMs Minimum time between thumbnails (ms)
     * @param thumbsPerSecond Target thumbnails per second
     * @param maxThumbsPerSegment Hard limit of thumbnails per segment
     */
    data class LODConfig(
        val minIntervalMs: Long,
        val thumbsPerSecond: Float,
        val maxThumbsPerSegment: Int
    )
    
    /**
     * LOD configurations by bucket.
     */
    private val configs = mapOf(
        LODBucket.OVERVIEW to LODConfig(
            minIntervalMs = 2000L,      // 1 thumb every 2 seconds
            thumbsPerSecond = 0.5f,
            maxThumbsPerSegment = 10
        ),
        LODBucket.NORMAL to LODConfig(
            minIntervalMs = 1000L,      // 1 thumb per second
            thumbsPerSecond = 1.0f,
            maxThumbsPerSegment = 20
        ),
        LODBucket.DETAILED to LODConfig(
            minIntervalMs = 500L,       // 2 thumbs per second
            thumbsPerSecond = 2.0f,
            maxThumbsPerSegment = 40
        ),
        LODBucket.ULTRA to LODConfig(
            minIntervalMs = 250L,       // 4 thumbs per second
            thumbsPerSecond = 4.0f,
            maxThumbsPerSegment = 80
        )
    )
    
    /**
     * Maps continuous zoom level to discrete LOD bucket.
     * @param zoomLevel Continuous zoom (1.0 = normal)
     * @return Discrete bucket
     */
    fun getBucketForZoom(zoomLevel: Float): LODBucket = when {
        zoomLevel < 0.7f -> LODBucket.OVERVIEW
        zoomLevel < 1.5f -> LODBucket.NORMAL
        zoomLevel < 2.5f -> LODBucket.DETAILED
        else -> LODBucket.ULTRA
    }
    
    /**
     * Gets configuration for a bucket.
     */
    fun getConfig(bucket: LODBucket): LODConfig {
        return configs[bucket] ?: configs[LODBucket.NORMAL]!!
    }
    
    /**
     * Calculates thumbnail interval for given bucket and pixelsPerSecond.
     * @param bucket LOD bucket
     * @param pixelsPerSecond Timeline scale (px/s)
     * @param thumbnailWidthPx Width of each thumbnail in pixels
     * @return Interval in milliseconds
     */
    fun calculateIntervalMs(
        bucket: LODBucket,
        pixelsPerSecond: Float,
        thumbnailWidthPx: Float
    ): Long {
        val config = getConfig(bucket)
        
        // Calculate based on visual density
        val intervalFromPxPerSec = ((thumbnailWidthPx / pixelsPerSecond) * 1000f).toLong()
        
        // Respect minimum interval from config
        return intervalFromPxPerSec.coerceAtLeast(config.minIntervalMs)
    }
    
    /**
     * Fallback bucket priority (for progressive refinement).
     * Returns buckets to try in order when current bucket is not available.
     */
    fun getFallbackBuckets(bucket: LODBucket): List<LODBucket> {
        return when (bucket) {
            LODBucket.ULTRA -> listOf(LODBucket.DETAILED, LODBucket.NORMAL, LODBucket.OVERVIEW)
            LODBucket.DETAILED -> listOf(LODBucket.NORMAL, LODBucket.OVERVIEW)
            LODBucket.NORMAL -> listOf(LODBucket.OVERVIEW)
            LODBucket.OVERVIEW -> emptyList()
        }
    }
}
