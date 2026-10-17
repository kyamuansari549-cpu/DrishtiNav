package com.drishtinav.app.perception

import android.media.Image

enum class Direction { LEFT, CENTER, RIGHT }
enum class Urgency { URGENT, NEAR, FAR }

/** A detected object with a fused distance + direction, ready for alerts. */
data class Obstacle(
    val label: String,
    /** Metres, or null when depth was unavailable for this object. */
    val distanceMeters: Float?,
    val direction: Direction,
    val urgency: Urgency,
    val score: Float
)

/**
 * Fuses 2D detections with depth: every bounding box gets a metric distance
 * (median depth at its centre) and a left/centre/right zone.
 *
 * Coordinate spaces: detections live in the upright-portrait bitmap produced
 * by [FrameConverter.toPortrait]; the depth image is in sensor-landscape
 * orientation. A portrait point (px, py) maps to sensor coords
 * (u = py, v = 1 - px) — the inverse of the 90° clockwise rotation.
 * If field testing shows left/right mirrored, flip the v mapping here.
 *
 * Ranging: depth is sampled over the inner region of each box (see
 * [DepthSampler.sampleRegionDepthMeters]), not just the box centre — the
 * centre often lands on background, which would over-estimate distance.
 */
object ObstacleFusion {

    // Default distance bands (metres). URGENT interrupts speech; NEAR queues it.
    // Overridable per-call so the user can tune them in Settings.
    const val URGENT_DISTANCE_M = 1.0f
    const val NEAR_DISTANCE_M = 2.0f

    fun fuse(
        detections: List<RawDetection>,
        depthImage: Image?,
        imageWidth: Int,
        imageHeight: Int,
        urgentM: Float = URGENT_DISTANCE_M,
        nearM: Float = NEAR_DISTANCE_M
    ): List<Obstacle> {
        return detections.mapNotNull { detection ->
            val cx = (detection.box.left + detection.box.right) / 2f / imageWidth

            val direction = when {
                cx < 0.4f -> Direction.LEFT
                cx > 0.6f -> Direction.RIGHT
                else -> Direction.CENTER
            }

            // Portrait box -> normalised sensor-space rect.
            // u = py, v = 1 - px, so the box's vertical span becomes the
            // sensor-u span and the horizontal span becomes (flipped) v.
            val w = imageWidth.toFloat()
            val h = imageHeight.toFloat()
            val uMin = (detection.box.top / h).coerceIn(0f, 1f)
            val uMax = (detection.box.bottom / h).coerceIn(0f, 1f)
            val vMin = (1f - detection.box.right / w).coerceIn(0f, 1f)
            val vMax = (1f - detection.box.left / w).coerceIn(0f, 1f)
            val distance = depthImage?.let {
                DepthSampler.sampleRegionDepthMeters(it, uMin, vMin, uMax, vMax)
            }

            val urgency = when {
                distance == null -> Urgency.FAR
                distance < urgentM -> Urgency.URGENT
                distance < nearM -> Urgency.NEAR
                else -> Urgency.FAR
            }

            Obstacle(
                label = detection.label,
                distanceMeters = distance,
                direction = direction,
                urgency = urgency,
                score = detection.score
            )
        }.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
    }
}
