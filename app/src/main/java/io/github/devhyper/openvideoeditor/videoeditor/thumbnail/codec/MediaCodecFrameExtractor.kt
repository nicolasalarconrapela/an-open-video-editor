package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.codec

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.nio.ByteBuffer
import kotlin.math.min

class MediaCodecFrameExtractor(
    private val yuvToRgbConverter: YuvToRgbConverter = YuvToRgbConverter()
) {
    fun extractFrame(
        context: Context,
        uriString: String,
        timeUs: Long,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ): Bitmap? {
        val uri = Uri.parse(uriString)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = selectVideoTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val codecInstance = MediaCodec.createDecoderByType(mime)
            codec = codecInstance
            codecInstance.configure(format, null, null, 0)
            codecInstance.start()
            extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var output: Bitmap? = null
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codecInstance.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codecInstance.getInputBuffer(inputIndex)
                        val sampleSize = readSampleData(extractor, inputBuffer)
                        if (sampleSize < 0) {
                            codecInstance.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codecInstance.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codecInstance.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        val image = codecInstance.getOutputImage(outputIndex)
                        if (image != null) {
                            output = image.toBitmap(targetWidth, targetHeight, rotationDegrees)
                            image.close()
                            outputDone = true
                        }
                        codecInstance.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // no-op
                    }
                }
            }

            return output
        } catch (_: Exception) {
            return fallbackWithRetriever(
                context,
                uri,
                timeUs,
                targetWidth,
                targetHeight,
                rotationDegrees
            )
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
                // ignore
            }
            extractor.release()
        }
    }

    private fun Image.toBitmap(
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ): Bitmap {
        val sourceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(this, sourceBitmap)
        val scaled = scaleBitmap(sourceBitmap, targetWidth, targetHeight)
        return if (rotationDegrees == 0) {
            scaled
        } else {
            val rotated = scaled.rotate(rotationDegrees.toFloat())
            if (rotated != scaled) scaled.recycle()
            rotated
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return bitmap
        val widthScale = targetWidth.toFloat() / bitmap.width
        val heightScale = targetHeight.toFloat() / bitmap.height
        val scale = min(widthScale, heightScale)
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        if (scaledWidth == bitmap.width && scaledHeight == bitmap.height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun readSampleData(extractor: MediaExtractor, buffer: ByteBuffer?): Int {
        if (buffer == null) return -1
        buffer.clear()
        return extractor.readSampleData(buffer, 0)
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return index
        }
        return null
    }

    private fun fallbackWithRetriever(
        context: Context,
        uri: Uri,
        timeUs: Long,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.frameAtTime
            } ?: return null
            val scaled = scaleBitmap(frame, targetWidth, targetHeight)
            if (rotationDegrees == 0) scaled else scaled.rotate(rotationDegrees.toFloat())
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
