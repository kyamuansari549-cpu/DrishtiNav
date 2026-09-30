package com.drishtinav.app.perception

import android.media.Image
import java.nio.ByteOrder

/**
 * Reads metric depth from ARCore's 16-bit depth image.
 *
 * Each pixel holds depth in millimetres as an unsigned 16-bit value;
 * 0 means "no depth estimate". The depth image shares the camera image's
 * sensor (landscape) orientation, so callers must pass coordinates in that
 * space — see [ObstacleFusion] for the portrait-to-sensor mapping.
 */
object DepthSampler {

    /**
     * Median depth in metres inside a small patch around the normalized
     * sensor-space point (u, v), or null when no valid samples exist.
     */
    fun sampleDepthMeters(
        depthImage: Image,
        u: Float,
        v: Float,
        patchRadius: Int = 2
    ): Float? {
        val plane = depthImage.planes[0]
        // Depth is little-endian uint16 on device; enforce native order explicitly.
        val buffer = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val width = depthImage.width
        val height = depthImage.height
        // Strides are in bytes; convert to 16-bit pixel units.
        val rowStridePx = plane.rowStride / 2
        val pixelStridePx = plane.pixelStride / 2

        val cx = (u * width).toInt().coerceIn(0, width - 1)
        val cy = (v * height).toInt().coerceIn(0, height - 1)

        val samples = ArrayList<Int>(25)
        for (dy in -patchRadius..patchRadius) {
            for (dx in -patchRadius..patchRadius) {
                val x = (cx + dx).coerceIn(0, width - 1)
                val y = (cy + dy).coerceIn(0, height - 1)
                val index = y * rowStridePx + x * pixelStridePx
                if (index < buffer.limit()) {
                    val mm = buffer.get(index).toInt() and 0xFFFF
                    if (mm > 0) samples.add(mm)
                }
            }
        }
        if (samples.isEmpty()) return null
        samples.sort()
        return samples[samples.size / 2] / 1000f
    }
}
