package com.fitflow.timer.ui.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class PhaseBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var color = Color.TRANSPARENT
    private var animator: ValueAnimator? = null

    fun setPhaseColor(color: Int, animate: Boolean = true) {
        animator?.cancel()
        if (!animate) {
            this.color = color
            rebuildShader()
            invalidate()
            return
        }
        animator = ValueAnimator.ofObject(ArgbEvaluator(), this.color, color).apply {
            duration = 600L
            addUpdateListener {
                this@PhaseBackdropView.color = it.animatedValue as Int
                rebuildShader()
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun rebuildShader() {
        if (width == 0 || height == 0) return
        val top = Color.argb(76, Color.red(color), Color.green(color), Color.blue(color))
        val mid = Color.argb(26, Color.red(color), Color.green(color), Color.blue(color))
        paint.shader = RadialGradient(
            width * 0.5f,
            height * 0.12f,
            max(width, height) * 0.9f,
            intArrayOf(top, mid, Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
