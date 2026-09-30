package com.drishtinav.app.perception

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import kotlin.math.min

/**
 * Converts ARCore camera frames (YUV_420_888, sensor/landscape orientation)
 * into Bitmaps the object detector can consume.
 */
object FrameConverter {

    /**
     * Converts a YUV_420_888 [Image] to ARGB_8888, honouring row/pixel strides.
     * Must be called before [Image.close].
     */
    fun yuv420ToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val out = IntArray(width * height)
        val yRow = ByteArray(yRowStride)

        for (j in 0 until height) {
            yBuffer.position(j * yRowStride)
            yBuffer.get(yRow, 0, min(width, yRowStride))
            val uvJ = j / 2
            for (i in 0 until width) {
                val y = yRow[i].toInt() and 0xFF
                val uvOffset = uvJ * uvRowStride + (i / 2) * uvPixelStride
                val u = (uBuffer.get(uvOffset).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvOffset).toInt() and 0xFF) - 128

                // BT.601 YUV -> RGB
                var r = (y + 1.402f * v).toInt()
                var g = (y - 0.344136f * u - 0.714136f * v).toInt()
                var b = (y + 1.772f * u).toInt()
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                out[j * width + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Rotates the sensor-landscape bitmap to upright portrait, matching what
     * the user sees on screen (app is portrait-locked). After this rotation,
     * bitmap-left == user's left, which is what [ObstacleFusion] relies on
     * for direction zones.
     */
    fun toPortrait(src: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return rotated
    }
}
