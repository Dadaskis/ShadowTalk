package com.shadowtalk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Custom view that draws a simple bar-style waveform on a Canvas.
 * Data is set after loading or recording audio (not real-time).
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint used to draw each vertical bar of the waveform
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Normalized amplitude values between 0.0 and 1.0
    private var amplitudes: List<Float> = emptyList()

    init {
        // Read optional waveform color from XML layout attributes
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveformView)
        val defaultColor = ContextCompat.getColor(context, R.color.accent_purple)
        barPaint.color = typedArray.getColor(R.styleable.WaveformView_waveformColor, defaultColor)
        typedArray.recycle()
    }

    /**
     * Updates the waveform data and triggers a redraw.
     *
     * @param samples List of normalized amplitudes (0.0 to 1.0)
     */
    fun setWaveformData(samples: List<Float>) {
        amplitudes = samples
        invalidate()
    }

    /**
     * Clears the waveform display.
     */
    fun clearWaveform() {
        amplitudes = emptyList()
        invalidate()
    }

    /**
     * Draws vertical bars centered vertically, one bar per amplitude sample.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (amplitudes.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // Calculate bar width with a small gap between bars
        val barCount = amplitudes.size
        val gap = 2f
        val barWidth = ((width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)

        amplitudes.forEachIndexed { index, amplitude ->
            // Scale bar height by amplitude; very quiet sections stay as thin lines
            val barHeight = (amplitude * height * 0.95f).coerceAtLeast(1f)
            val left = index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f

            canvas.drawRect(left, top, right, bottom, barPaint)
        }
    }
}
