package io.github.devhyper.openvideoeditor.engine.cache

/**
 * Abstraction for Data fetching.
 * The View/ViewModel implements this to provide data to the Engine or vice-versa.
 */
interface MediaDataProvider {
    suspend fun getThumbnail(sourcePath: String, timeUs: Long, width: Int): Any? 
    suspend fun getWaveformSamples(sourcePath: String, timeRangeUs: LongRange): FloatArray
}

interface AsyncPrefetcher {
    fun requestPrefetch(sourcePath: String, timesUs: List<Long>)
}
