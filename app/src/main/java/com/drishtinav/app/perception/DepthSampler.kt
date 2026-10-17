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

    /**
     * Distance to the nearest surface inside a normalised sensor-space
     * rectangle (uMin/vMin/uMax/vMax in 0..1), or null when depth support
     * is too weak.
     *
     * Why not just the box centre: the centre of a detection often lands
     * on background — between a chair's legs, beside a person's torso —
     * so a centre sample can report the wall 3 m behind the obstacle
     * instead of the obstacle itself (a dangerous over-estimate). Instead
     * we sample a 3×3 grid over the inner 60% of the box and take the
     * 30th percentile of all valid pixels: close to the nearest surface,
     * robust to a few background pixels leaking in.
     *
     * Rejection rules: pixels with no estimate (0) and pixels beyond
     * [MAX_DEPTH_MM] (out-of-range noise — ARCore depth is only reliable
     * to a few metres) are ignored. If fewer than [MIN_DEPTH_SUPPORT] of
     * the attempted samples are valid, the detection has no depth support
     * and null is returned so callers can drop it as a likely phantom.
     */
    fun sampleRegionDepthMeters(
        depthImage: Image,
        uMin: Float,
        vMin: Float,
        uMax: Float,
        vMax: Float
    ): Float? {
        val plane = depthImage.planes[0]
        val buffer = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val width = depthImage.width
        val height = depthImage.height
        val rowStridePx = plane.rowStride / 2
        val pixelStridePx = plane.pixelStride / 2

        // Inner 60% of the box: shrink 20% per side to stay on the object.
        val iu0 = uMin + 0.2f * (uMax - uMin)
        val iu1 = uMax - 0.2f * (uMax - uMin)
        val iv0 = vMin + 0.2f * (vMax - vMin)
        val iv1 = vMax - 0.2f * (vMax - vMin)

        val samples = ArrayList<Int>(81)
        var attempted = 0
        for (gy in 0..2) {
            for (gx in 0..2) {
                val u = (iu0 + (iu1 - iu0) * gx / 2f).coerceIn(0f, 1f)
                val v = (iv0 + (iv1 - iv0) * gy / 2f).coerceIn(0f, 1f)
                val cx = (u * width).toInt().coerceIn(0, width - 1)
                val cy = (v * height).toInt().coerceIn(0, height - 1)
                // 3×3 px patch around each grid point.
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        attempted++
                        val x = (cx + dx).coerceIn(0, width - 1)
                        val y = (cy + dy).coerceIn(0, height - 1)
                        val index = y * rowStridePx + x * pixelStridePx
                        if (index >= buffer.limit()) continue
                        val mm = buffer.get(index).toInt() and 0xFFFF
                        if (mm > 0 && mm <= MAX_DEPTH_MM) samples.add(mm)
                    }
                }
            }
        }
        if (samples.isEmpty()) return null
        if (samples.size.toFloat() / attempted < MIN_DEPTH_SUPPORT) return null
        samples.sort()
        val rank = ((samples.size - 1) * DEPTH_PERCENTILE).toInt()
            .coerceIn(0, samples.size - 1)
        return samples[rank] / 1000f
    }

    /** Samples beyond 8 m are out-of-range noise, not obstacles. */
    private const val MAX_DEPTH_MM = 8000

    /** Minimum fraction of valid samples for a detection to count as ranged. */
    private const val MIN_DEPTH_SUPPORT = 0.25f

    /** Percentile of valid depths reported (nearest-surface estimate). */
    private const val DEPTH_PERCENTILE = 0.3f
}
