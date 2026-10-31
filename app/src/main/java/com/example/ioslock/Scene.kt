package com.example.ioslock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Sky gradient and mountains. The shapes are built once per screen size
 * and reused on every frame, so drawing stays fast.
 */
class Scene {

    private var w = 0f
    private var h = 0f
    private var presetIndex = -1

    private val skyPaint = Paint()
    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val back = Path()
    private val front = Path()

    fun update(width: Float, height: Float, index: Int) {
        if (width == w && height == h && index == presetIndex) return
        w = width
        h = height
        presetIndex = index

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Presets.get(index).sky,
            floatArrayOf(0f, 0.32f, 0.56f),
            Shader.TileMode.CLAMP
        )
        mountainPaint.pathEffect = CornerPathEffect(w * 0.012f)

        // The shapes reach below the screen so they can slide up without leaving a gap
        val bottom = h * 1.1f

        back.reset()
        back.moveTo(0f, h * 0.52f)
        back.lineTo(w * 0.10f, h * 0.47f)
        back.lineTo(w * 0.22f, h * 0.51f)
        back.lineTo(w * 0.36f, h * 0.455f)
        back.lineTo(w * 0.50f, h * 0.50f)
        back.lineTo(w * 0.66f, h * 0.465f)
        back.lineTo(w * 0.82f, h * 0.505f)
        back.lineTo(w, h * 0.46f)
        back.lineTo(w, bottom)
        back.lineTo(0f, bottom)
        back.close()

        front.reset()
        front.moveTo(0f, h * 0.60f)
        front.lineTo(w * 0.14f, h * 0.55f)
        front.lineTo(w * 0.30f, h * 0.62f)
        front.lineTo(w * 0.48f, h * 0.575f)
        front.lineTo(w * 0.64f, h * 0.64f)
        front.lineTo(w * 0.80f, h * 0.58f)
        front.lineTo(w, h * 0.62f)
        front.lineTo(w, bottom)
        front.lineTo(0f, bottom)
        front.close()
    }

    fun drawSky(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, skyPaint)
    }

    /** rise: 1 = pushed down (hidden), 0 = in place. */
    fun drawMountains(canvas: Canvas, rise: Float) {
        val p = Presets.get(presetIndex)

        mountainPaint.color = p.mountainBack
        canvas.save()
        canvas.translate(0f, rise * h * 0.04f)
        canvas.drawPath(back, mountainPaint)
        canvas.restore()

        mountainPaint.color = p.mountainFront
        canvas.save()
        canvas.translate(0f, rise * h * 0.07f)
        canvas.drawPath(front, mountainPaint)
        canvas.restore()
    }

    /** A still picture of the scenery with no clock, used as a home screen picture. */
    fun renderBitmap(width: Int, height: Int, index: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        update(width.toFloat(), height.toFloat(), index)
        drawSky(canvas)
        drawMountains(canvas, 0f)
        return bmp
    }
}
