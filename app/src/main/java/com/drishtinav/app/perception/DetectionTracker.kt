package com.drishtinav.app.perception

import android.graphics.RectF
import android.os.SystemClock

/**
 * Temporal smoothing for detections: associates this frame's boxes with
 * tracks from previous frames (same label + IoU overlap) and only surfaces
 * objects that persist.
 *
 * Why: EfficientDet-Lite0 flickers — a phantom "bottle" or a half-seen
 * "chair" appears for one frame and vanishes. Announcing those produces
 * false alerts that erode the user's trust. Requiring an object to be seen
 * in [minHits] consecutive frames kills most flicker while adding only
 * ~400 ms of latency (one extra detection interval) — acceptable for a
 * walking aid, where real obstacles persist for many frames.
 *
 * Also smooths boxes/scores with an exponential moving average so the
 * fused distance and direction don't jump around between frames.
 *
 * Not thread-safe: use from the single perception thread, like
 * [ObstacleDetector]. Tracks self-heal: missed tracks expire after
 * [maxMisses] frames, and a gap longer than [resetGapMs] (pause/resume)
 * clears everything so no ghost from before the pause can resurface.
 */
class DetectionTracker(
    private val iouThreshold: Float = 0.45f,
    private val minHits: Int = 2,
    private val maxMisses: Int = 2,
    private val resetGapMs: Long = 2000L,
    private val boxAlpha: Float = 0.6f,
    private val scoreAlpha: Float = 0.5f
) {

    private data class Track(
        var label: String,
        var box: RectF,
        var score: Float,
        var hits: Int,
        var misses: Int
    )

    private val tracks = mutableListOf<Track>()
    private var lastUpdateMs = 0L

    /**
     * Updates tracks with this frame's raw detections and returns the
     * stable subset (seen in at least [minHits] frames) with smoothed
     * boxes and scores.
     */
    fun track(detections: List<RawDetection>): List<RawDetection> {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateMs > resetGapMs) tracks.clear()
        lastUpdateMs = now

        // Greedy association, highest score first: each detection takes the
        // best unmatched same-label track above the IoU threshold.
        val matched = BooleanArray(tracks.size)
        val assignments = HashMap<RawDetection, Track>(detections.size)
        for (det in detections.sortedByDescending { it.score }) {
            var best = -1
            var bestIou = iouThreshold
            for (i in tracks.indices) {
                val t = tracks[i]
                if (!matched[i] && t.label == det.label) {
                    val iou = Geometry.iou(t.box, det.box)
                    if (iou > bestIou) {
                        bestIou = iou
                        best = i
                    }
                }
            }
            if (best >= 0) {
                matched[best] = true
                assignments[det] = tracks[best]
            }
        }

        for ((det, t) in assignments) {
            t.box = emaBox(t.box, det.box)
            t.score = scoreAlpha * det.score + (1f - scoreAlpha) * t.score
            t.hits += 1
            t.misses = 0
        }
        for (i in tracks.indices) {
            if (!matched[i]) tracks[i].misses += 1
        }
        tracks.removeAll { it.misses > maxMisses }
        for (det in detections) {
            if (det !in assignments) {
                tracks.add(Track(det.label, RectF(det.box), det.score, hits = 1, misses = 0))
            }
        }

        return tracks
            .filter { it.hits >= minHits }
            .map { RawDetection(it.label, it.score, RectF(it.box)) }
    }

    /** Drops all tracks — call when the model is recreated, if ever needed. */
    fun reset() {
        tracks.clear()
    }

    private fun emaBox(old: RectF, new: RectF): RectF {
        return RectF(
            boxAlpha * new.left + (1f - boxAlpha) * old.left,
            boxAlpha * new.top + (1f - boxAlpha) * old.top,
            boxAlpha * new.right + (1f - boxAlpha) * old.right,
            boxAlpha * new.bottom + (1f - boxAlpha) * old.bottom
        )
    }
}
