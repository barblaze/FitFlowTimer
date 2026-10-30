package com.fitflow.timer.ui.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.fitflow.timer.R
import kotlin.math.abs
import kotlin.math.min

class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var animatedProgress = 0f
    private var currentColor = ContextCompat.getColor(context, R.color.brand)
    private var colorAnimator: ValueAnimator? = null

    var progress: Float
        get() = animatedProgress
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (abs(clamped - animatedProgress) < 0.0005f) return
            animatedProgress = clamped
            invalidate()
        }

    fun setPhaseColor(color: Int, animate: Boolean = true) {
        colorAnimator?.cancel()
        if (!animate) {
            currentColor = color
            invalidate()
            return
        }
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, color).apply {
            duration = 450L
            addUpdateListener {
                currentColor = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val stroke = 14f * density
        val radius = min(width, height) / 2f - stroke * 1.7f
        if (radius <= 0f) return

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.strokeWidth = stroke
        trackPaint.color = ContextCompat.getColor(context, R.color.track)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        val angle = 360f * animatedProgress
        if (angle <= 0.1f) return

        glowPaint.strokeWidth = stroke * 2.4f
        glowPaint.color = currentColor
        glowPaint.alpha = 38
        canvas.drawArc(rect, -90f, angle, false, glowPaint)

        progressPaint.strokeWidth = stroke
        progressPaint.color = currentColor
        progressPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(currentColor, lighten(currentColor, 0.35f), currentColor),
            floatArrayOf(0f, 0.6f, 1f),
        )
        canvas.save()
        canvas.rotate(-90f, cx, cy)
        canvas.drawArc(rect, 0f, angle, false, progressPaint)
        canvas.restore()
    }

    private fun lighten(color: Int, amount: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt()
        return Color.rgb(r, g, b)
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
