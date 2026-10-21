package com.example.gazedot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class CircleView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun setStyle(color: Int, opacityPercent: Int) {
        val a = (opacityPercent * 255 / 100).coerceIn(0, 255)
        fill.color = color
        fill.alpha = a
        ring.color = color
        ring.alpha = (a + 90).coerceAtMost(255)
        ring.strokeWidth = 2f * resources.displayMetrics.density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - ring.strokeWidth / 2f
        if (r <= 0f) return
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, ring)
    }
}
