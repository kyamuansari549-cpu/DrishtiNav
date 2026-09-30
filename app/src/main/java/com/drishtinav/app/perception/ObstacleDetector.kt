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
        return detector.detect(mpImage).detections().mapNotNull { detection ->
            val category = detection.categories().firstOrNull() ?: return@mapNotNull null
            RawDetection(
                label = category.categoryName().replace('_', ' '),
                score = category.score(),
                box = RectF(detection.boundingBox())
            )
        }
    }

    fun close() {
        detector.close()
    }

    companion object {
        const val MODEL_ASSET_PATH = "efficientdet_lite0.tflite"
        const val MAX_RESULTS = 5
        const val SCORE_THRESHOLD = 0.5f
    }
}
