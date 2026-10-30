package com.fitflow.timer.ui.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class TimeDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
        letterSpacing = -0.03f
    }

    private var text = "00:00"
    private var textSizePx = 0f
    private var textColor = Color.WHITE
    private var colorAnimator: ValueAnimator? = null

    init {
        contentDescription = text
    }

    fun setTime(value: String) {
        if (text == value) return
        val previousReference = referenceFor(text)
        text = value
        contentDescription = value
        if (referenceFor(value) != previousReference) {
            requestLayout()
        } else {
            invalidate()
        }
    }

    fun setTimeColor(color: Int, animate: Boolean = true) {
        colorAnimator?.cancel()
        if (!animate) {
            textColor = color
            invalidate()
            return
        }
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), textColor, color).apply {
            duration = 350L
            addUpdateListener {
                textColor = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val availableWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            (resources.displayMetrics.widthPixels * 0.6f).toInt().coerceAtLeast(1)
        } else {
            (widthSize - paddingLeft - paddingRight).coerceAtLeast(1)
        }
        val availableHeight = if (heightMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            ((heightSize - paddingTop - paddingBottom) * 0.65f).toInt().coerceAtLeast(1)
        }

        updateTextSize(availableWidth, availableHeight)

        val textHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent).toInt() + 1
        val contentWidth =
            (paint.measureText(referenceFor(text)) + paddingLeft + paddingRight).toInt()
        val desiredWidth = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> contentWidth
            MeasureSpec.AT_MOST -> minOf(contentWidth, widthSize)
            else -> widthSize
        }
        val desiredHeight = textHeight + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (text.isEmpty()) return
        paint.textSize = textSizePx
        paint.color = textColor
        val metrics = paint.fontMetrics
        val x = width / 2f
        val y = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, y, paint)
    }

    private fun updateTextSize(availableWidth: Int, availableHeight: Int) {
        val reference = referenceFor(text)
        var size = MAX_TEXT_SIZE_SP * density

        paint.textSize = size
        val usableWidth = availableWidth * 0.94f
        val widthAtMax = paint.measureText(reference) * 1.02f
        if (widthAtMax > usableWidth) {
            size *= usableWidth / widthAtMax
        }

        paint.textSize = size
        val heightAtSize = paint.fontMetrics.descent - paint.fontMetrics.ascent
        if (heightAtSize > availableHeight) {
            size *= availableHeight / heightAtSize
        }

        textSizePx = size.coerceAtLeast(MIN_TEXT_SIZE_SP * density)
        paint.textSize = textSizePx
    }

    private fun referenceFor(value: String): String =
        if (value.count { it == ':' } >= 2) "88:88:88" else "88:88"

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_TEXT_SIZE_SP = 120f
        const val MIN_TEXT_SIZE_SP = 24f
    }
}
