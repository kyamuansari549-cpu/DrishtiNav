package com.drishtinav.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.drishtinav.app.R
import kotlin.math.min

/**
 * Live "sonar" indicator: while scanning, three rings expand outward from
 * the core in a loop over a soft teal glow. [pulseUrgent] fires a double
 * amber shockwave with a swelling, glowing core for urgent obstacle alerts.
 * Idle state shows faint static rings and a dim core.
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
    /** Wide, faint stroke drawn under each ring for a neon glow feel. */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val urgentGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = amber
    }
    private val urgentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = amber
    }
    /** Radial glow behind the core — rebuilt when the size changes. */
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var glowBuiltFor = 0f
    private var glowBuiltUrgent = false

    private var animator: ValueAnimator? = null
    private var phase = 0f
    /** 1 → 0 decay after [pulseUrgent]; drives the amber shockwaves. */
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
                // Ease the urgent decay so the shockwave lingers, then fades.
                if (urgentFlash > 0f) urgentFlash = (urgentFlash - 0.035f).coerceAtLeast(0f)
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

    /** Call on an urgent alert: double amber shockwave + swelling core. */
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

    private fun ensureCoreGlow(cx: Float, cy: Float, radius: Float, urgent: Boolean) {
        if (glowBuiltFor == radius && glowBuiltUrgent == urgent) return
        glowBuiltFor = radius
        glowBuiltUrgent = urgent
        val c = if (urgent) amber else teal
        coreGlowPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                (0x55 shl 24) or (c and 0x00FFFFFF),
                (0x18 shl 24) or (c and 0x00FFFFFF),
                0x00000000
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f - dp(4f)
        if (maxR <= 0f) return

        if (!running) {
            // Idle: faint static rings + dim core, no glow.
            glowBuiltFor = 0f
            glowBuiltUrgent = false
            ringPaint.strokeWidth = dp(2f)
            ringPaint.alpha = 40
            for (f in floatArrayOf(0.45f, 0.75f, 1f)) {
                canvas.drawCircle(cx, cy, maxR * f, ringPaint)
            }
            dotPaint.alpha = 90
            canvas.drawCircle(cx, cy, dp(4f), dotPaint)
            return
        }

        val urgent = urgentFlash > 0f

        // Core glow halo.
        ensureCoreGlow(cx, cy, maxR * 0.55f, urgent)
        coreGlowPaint.alpha = if (urgent) 255 else 160
        canvas.drawCircle(cx, cy, maxR * 0.55f, coreGlowPaint)

        // Expanding echo rings — each with a soft glow underlay.
        for (i in 0 until 3) {
            val p = (phase + i / 3f) % 1f
            // Ease-out so rings decelerate as they expand: smoother motion.
            val eased = 1f - (1f - p) * (1f - p)
            val r = dp(6f) + eased * (maxR - dp(6f))
            val alpha = ((1f - p) * 200).toInt()
            glowPaint.strokeWidth = dp(7f)
            glowPaint.alpha = (alpha * 0.35f).toInt()
            canvas.drawCircle(cx, cy, r, glowPaint)
            ringPaint.strokeWidth = dp(2.5f)
            ringPaint.alpha = alpha
            canvas.drawCircle(cx, cy, r, ringPaint)
        }

        // Core — swells while the urgent flash is active.
        val coreR = dp(4.5f) * (1f + urgentFlash * 0.8f)
        dotPaint.alpha = 255
        canvas.drawCircle(cx, cy, coreR, dotPaint)

        // Urgent double shockwave.
        if (urgent) {
            for (wave in 0 until 2) {
                val wp = ((1f - urgentFlash) + wave * 0.38f) % 1f
                val wAlpha = (urgentFlash * (1f - wave * 0.35f) * 255).toInt()
                    .coerceIn(0, 255)
                urgentGlowPaint.strokeWidth = dp(8f)
                urgentGlowPaint.alpha = (wAlpha * 0.4f).toInt()
                canvas.drawCircle(cx, cy, dp(6f) + wp * maxR, urgentGlowPaint)
                urgentPaint.strokeWidth = dp(3.5f)
                urgentPaint.alpha = wAlpha
                canvas.drawCircle(cx, cy, dp(6f) + wp * maxR, urgentPaint)
            }
            urgentDotPaint.alpha = (urgentFlash * 255).toInt()
            canvas.drawCircle(cx, cy, coreR, urgentDotPaint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
