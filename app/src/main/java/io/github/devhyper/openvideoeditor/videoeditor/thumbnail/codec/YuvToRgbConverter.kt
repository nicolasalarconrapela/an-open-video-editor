package io.github.devhyper.openvideoeditor.videoeditor.thumbnail.codec

import android.graphics.Bitmap
import android.media.Image
import kotlin.math.roundToInt

class YuvToRgbConverter {
    private var argbBuffer: IntArray? = null

    fun yuvToRgb(image: Image, output: Bitmap) {
        val width = image.width
        val height = image.height
        val outBuffer = ensureArgbBuffer(width * height)
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        var outIndex = 0

        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val uvRow = row / 2
            val uRowOffset = uvRow * uRowStride
            val vRowOffset = uvRow * vRowStride
            for (col in 0 until width) {
                val yIndex = yRowOffset + col * yPixelStride
                val uvCol = col / 2
                val uIndex = uRowOffset + uvCol * uPixelStride
                val vIndex = vRowOffset + uvCol * vPixelStride
                val y = yBuffer.get(yIndex).toInt() and 0xFF
                val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128
                val r = (y + 1.402f * v).roundToInt().coerceIn(0, 255)
                val g = (y - 0.344f * u - 0.714f * v).roundToInt().coerceIn(0, 255)
                val b = (y + 1.772f * u).roundToInt().coerceIn(0, 255)
                outBuffer[outIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(outBuffer, 0, width, 0, 0, width, height)
    }

    private fun ensureArgbBuffer(size: Int): IntArray {
        val current = argbBuffer
        return if (current == null || current.size < size) {
            IntArray(size).also { argbBuffer = it }
        } else {
            current
        }
    }
}
