package com.drishtinav.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.drishtinav.app.R
import kotlin.math.min

/**
 * Live "sonar" indicator: while scanning, three rings expand outward from
 * the core in a loop. [pulseUrgent] fires one amber shockwave for urgent
 * obstacle alerts. Idle state shows a faint static mark.
 *
 * Pure canvas drawing — no bitmaps, negligible battery cost.
 */
class SonarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val teal = ContextCompat.getColor(context, R.color.teal)
    private val amber = ContextCompat.getColor(context, R.color.sonar_amber)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = teal
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = teal
    }
    private val urgentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = amber
    }
    private val urgentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = amber
    }

    private var animator: ValueAnimator? = null
    private var phase = 0f
    /** 1 → 0 decay after [pulseUrgent]; drives the amber shockwave. */
    private var urgentFlash = 0f

    var running: Boolean = false
        private set

    fun start() {
        if (running) return
        running = true
        urgentFlash = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                if (urgentFlash > 0f) urgentFlash = (urgentFlash - 0.04f).coerceAtLeast(0f)
                invalidate()
            }
            start()
        }
        invalidate()
    }

    fun stop() {
        running = false
        animator?.cancel()
        animator = null
        urgentFlash = 0f
        invalidate()
    }

    /** Call on an urgent alert: one amber shockwave radiates from the core. */
    fun pulseUrgent() {
        if (running) {
            urgentFlash = 1f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f - dp(3f)
        if (maxR <= 0f) return

        if (!running) {
            // Idle: faint static rings + dim core.
            ringPaint.strokeWidth = dp(2f)
            ringPaint.alpha = 40
            for (f in floatArrayOf(0.45f, 0.75f, 1f)) {
                canvas.drawCircle(cx, cy, maxR * f, ringPaint)
            }
            dotPaint.alpha = 90
            canvas.drawCircle(cx, cy, dp(4f), dotPaint)
            return
        }

        // Expanding echo rings.
        ringPaint.strokeWidth = dp(2.5f)
        for (i in 0 until 3) {
            val p = (phase + i / 3f) % 1f
            ringPaint.alpha = ((1f - p) * 200).toInt()
            canvas.drawCircle(cx, cy, (dp(6f) + p * (maxR - dp(6f))), ringPaint)
        }
        dotPaint.alpha = 255
        canvas.drawCircle(cx, cy, dp(4.5f), dotPaint)

        // Urgent shockwave.
        if (urgentFlash > 0f) {
            val p = 1f - urgentFlash
            urgentPaint.strokeWidth = dp(3f)
            urgentPaint.alpha = (urgentFlash * 255).toInt()
            canvas.drawCircle(cx, cy, dp(6f) + p * maxR, urgentPaint)
            urgentDotPaint.alpha = (urgentFlash * 255).toInt()
            canvas.drawCircle(cx, cy, dp(4.5f), urgentDotPaint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
