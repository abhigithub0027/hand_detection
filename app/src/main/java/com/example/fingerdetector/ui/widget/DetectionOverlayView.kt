package com.example.fingerdetector.ui.widget

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class OverlayMode {
        PALM,
        FINGER
    }

    var overlayMode: OverlayMode = OverlayMode.PALM
        set(value) {
            field = value
            invalidate()
        }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x6A000000
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    private val path = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        path.reset()
        val fullPath = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        }
        val cutout = when (overlayMode) {
            OverlayMode.PALM -> RectF(
                width * 0.12f,
                height * 0.18f,
                width * 0.88f,
                height * 0.82f
            )

            OverlayMode.FINGER -> RectF(
                width * 0.28f,
                height * 0.20f,
                width * 0.72f,
                height * 0.80f
            )
        }

        path.addPath(fullPath)
        val cutoutPath = Path().apply {
            if (overlayMode == OverlayMode.PALM) {
                addRoundRect(cutout, 52f, 52f, Path.Direction.CCW)
            } else {
                addOval(cutout, Path.Direction.CCW)
            }
        }
        path.op(cutoutPath, Path.Op.DIFFERENCE)
        canvas.drawPath(path, scrimPaint)
        canvas.drawPath(cutoutPath, strokePaint)
    }
}
