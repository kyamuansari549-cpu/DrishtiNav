package com.drishtinav.app.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions

/** One raw detection before depth fusion. Box coords are in bitmap pixels. */
data class RawDetection(
    val label: String,
    val score: Float,
    val box: RectF
)

/**
 * On-device object detection via MediaPipe Tasks (EfficientDet-Lite0).
 * Runs fully offline from the model bundled in assets — no network, no
 * server, so it keeps working on the street with no signal.
 *
 * Accuracy hardening on top of the raw model output:
 * - Per-class score thresholds: small handheld/food classes flicker badly
 *   on Lite0, so they need stronger evidence than the 0.5 default. The
 *   safety-critical "person" class keeps the default threshold.
 * - Plausibility filter: drops degenerate boxes, tiny specks (< 0.4% of
 *   the frame), and tiny boxes hugging the frame edge (classic partial
 *   detections / sensor noise).
 * - Non-maximum suppression (IoU 0.5): the Tasks API exposes no NMS knob,
 *   so duplicate boxes for one object are merged here, keeping the best.
 *
 * Not thread-safe: create one instance and use it from a single background
 * thread (see MainActivity's perception thread).
 */
class ObstacleDetector(context: Context) {

    private val detector: ObjectDetector

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()
        val options = ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .build()
        detector = ObjectDetector.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): List<RawDetection> {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val width = bitmap.width
        val height = bitmap.height
        val frameArea = (width * height).toFloat()
        val raw = detector.detect(mpImage).detections().mapNotNull { detection ->
            val category = detection.categories().firstOrNull() ?: return@mapNotNull null
            val label = category.categoryName().replace('_', ' ')
            val score = category.score()
            // Per-class bar, on top of the model's global 0.5 cutoff.
            if (score < (MIN_SCORE_BY_LABEL[label] ?: SCORE_THRESHOLD)) return@mapNotNull null
            RawDetection(label, score, RectF(detection.boundingBox()))
        }
        return nonMaxSuppression(raw.filter { plausible(it, width, height, frameArea) })
    }

    fun close() {
        detector.close()
    }

    /**
     * Drops boxes that are almost certainly noise: zero/negative area,
     * specks under [MIN_REL_AREA] of the frame, and small boxes whose
     * centre sits within [EDGE_MARGIN] of the frame border (partial objects
     * sliding in/out, or edge artefacts).
     */
    private fun plausible(d: RawDetection, width: Int, height: Int, frameArea: Float): Boolean {
        val bw = d.box.width()
        val bh = d.box.height()
        if (bw <= 0f || bh <= 0f) return false
        val relArea = (bw * bh) / frameArea
        if (relArea < MIN_REL_AREA) return false
        val (cx, cy) = Geometry.normalisedCentre(d.box, width.toFloat(), height.toFloat())
        val nearEdge = cx < EDGE_MARGIN || cx > 1f - EDGE_MARGIN ||
                cy < EDGE_MARGIN || cy > 1f - EDGE_MARGIN
        if (nearEdge && relArea < EDGE_MAX_AREA) return false
        return true
    }

    /** Greedy NMS: highest score first, suppress same-object duplicates. */
    private fun nonMaxSuppression(detections: List<RawDetection>): List<RawDetection> {
        if (detections.size < 2) return detections
        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        val kept = ArrayList<RawDetection>(sorted.size)
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            val it = sorted.iterator()
            while (it.hasNext()) {
                if (Geometry.iou(best.box, it.next().box) > NMS_IOU_THRESHOLD) it.remove()
            }
        }
        return kept
    }

    companion object {
        const val MODEL_ASSET_PATH = "efficientdet_lite0.tflite"
        const val MAX_RESULTS = 8
        const val SCORE_THRESHOLD = 0.5f

        /** IoU above which two boxes are considered the same object. */
        private const val NMS_IOU_THRESHOLD = 0.5f

        /** Boxes smaller than 0.4% of the frame are noise at this resolution. */
        private const val MIN_REL_AREA = 0.004f

        /** Border band (fraction of frame) treated as the "edge zone". */
        private const val EDGE_MARGIN = 0.02f

        /** Boxes under 1% of the frame are dropped when in the edge zone. */
        private const val EDGE_MAX_AREA = 0.01f

        /**
         * Small, frequently-flickering COCO classes need stronger evidence.
         * Everything else uses [SCORE_THRESHOLD].
         */
        private val MIN_SCORE_BY_LABEL = mapOf(
            "bottle" to 0.60f,
            "cup" to 0.60f,
            "fork" to 0.60f,
            "knife" to 0.60f,
            "spoon" to 0.60f,
            "bowl" to 0.60f,
            "banana" to 0.60f,
            "apple" to 0.60f,
            "sandwich" to 0.60f,
            "orange" to 0.60f,
            "broccoli" to 0.60f,
            "carrot" to 0.60f,
            "hot dog" to 0.60f,
            "pizza" to 0.60f,
            "donut" to 0.60f,
            "cake" to 0.60f,
            "cell phone" to 0.60f,
            "remote" to 0.60f,
            "scissors" to 0.60f,
            "toothbrush" to 0.65f,
            "book" to 0.60f,
            "clock" to 0.60f,
            "vase" to 0.60f
        )
    }
}
