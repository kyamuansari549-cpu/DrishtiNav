package com.drishtinav.app.perception

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Small geometry helpers shared by the detector (NMS) and the tracker
 * (cross-frame association). Pure math — no Android framework beyond RectF.
 */
object Geometry {

    /** Intersection-over-union of two boxes in the same coordinate space. */
    fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = interRight - interLeft
        val interH = interBottom - interTop
        if (interW <= 0f || interH <= 0f) return 0f
        val inter = interW * interH
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else (inter / union).coerceIn(0f, 1f)
    }

    /** Centre of [box] as normalised (x, y) within a [width]×[height] frame. */
    fun normalisedCentre(box: RectF, width: Float, height: Float): Pair<Float, Float> {
        return Pair(
            ((box.left + box.right) / 2f / width).coerceIn(0f, 1f),
            ((box.top + box.bottom) / 2f / height).coerceIn(0f, 1f)
        )
    }
}
