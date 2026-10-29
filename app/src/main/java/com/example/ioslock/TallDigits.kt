package com.example.ioslock

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/**
 * Tall, condensed, round-ended digits like the iOS 26 lock screen clock.
 * Each digit is a centre line that gets stroked with a thick pen, so the
 * caller just draws the returned path with strokeWidth(w).
 */
object TallDigits {

    fun strokeWidth(w: Float): Float = w * 0.265f

    fun path(ch: Char, x: Float, y: Float, w: Float, h: Float): Path {
        val s = strokeWidth(w)
        val x0 = x + s / 2f
        val x1 = x + w - s / 2f
        val y0 = y + s / 2f
        val y1 = y + h - s / 2f
        val r = (x1 - x0) / 2f
        val sp = y1 - y0
        val p = Path()

        when (ch) {
            '0' -> stadium(p, x0, x1, y0, y1, r)

            '1' -> {
                p.moveTo(x0, y0 + r * 0.9f)
                p.lineTo(x1, y0)
                p.lineTo(x1, y1)
            }

            '2' -> {
                val a = y0 + sp * 0.48f
                p.moveTo(x0, y0 + sp * 0.24f)
                p.lineTo(x0, y0 + r)
                p.arcTo(RectF(x0, y0, x1, y0 + 2 * r), 180f, 180f)
                p.lineTo(x1, a)
                p.arcTo(RectF(x0, a - r, x1, a + r), 0f, 180f)
                p.lineTo(x0, y1)
                p.lineTo(x1, y1)
            }

            '3' -> {
                p.moveTo(x0, y0 + sp * 0.28f)
                p.lineTo(x0, y0 + r)
                p.arcTo(RectF(x0, y0, x1, y0 + 2 * r), 180f, 180f)
                p.lineTo(x1, y1 - r)
                p.arcTo(RectF(x0, y1 - 2 * r, x1, y1), 0f, 180f)
                p.lineTo(x0, y1 - sp * 0.22f)
                p.moveTo(x + w * 0.3f, y0 + sp * 0.5f)
                p.lineTo(x1, y0 + sp * 0.5f)
            }

            '4' -> {
                val b = y0 + sp * 0.66f
                p.moveTo(x0, y0)
                p.lineTo(x0, b)
                p.lineTo(x1, b)
                p.moveTo(x1, y0)
                p.lineTo(x1, y1)
            }

            '5' -> {
                val a = y0 + sp * 0.42f
                p.moveTo(x1, y0)
                p.lineTo(x0, y0)
                p.lineTo(x0, a)
                p.lineTo(x1, a)
                p.lineTo(x1, y1 - r)
                p.arcTo(RectF(x0, y1 - 2 * r, x1, y1), 0f, 180f)
                p.lineTo(x0, y1 - sp * 0.22f)
            }

            '6' -> {
                nine(p, x, y, w, h)
                val m = Matrix()
                m.setRotate(180f, x + w / 2f, y + h / 2f)
                p.transform(m)
            }

            '7' -> {
                p.moveTo(x0, y0 + sp * 0.22f)
                p.lineTo(x0, y0)
                p.lineTo(x1, y0)
                p.lineTo(x1, y1)
            }

            '8' -> {
                val m = y0 + sp * 0.48f
                stadium(p, x0, x1, y0, m, r)
                stadium(p, x0, x1, m, y1, r)
            }

            '9' -> nine(p, x, y, w, h)
        }
        return p
    }

    private fun nine(p: Path, x: Float, y: Float, w: Float, h: Float) {
        val s = strokeWidth(w)
        val x0 = x + s / 2f
        val x1 = x + w - s / 2f
        val y0 = y + s / 2f
        val y1 = y + h - s / 2f
        val r = (x1 - x0) / 2f
        val sp = y1 - y0
        val loopBottom = y + h * 0.667f - s / 2f

        stadium(p, x0, x1, y0, loopBottom, r)

        p.moveTo(x1, loopBottom - r)
        p.lineTo(x1, y1 - r)
        p.arcTo(RectF(x0, y1 - 2 * r, x1, y1), 0f, 180f)
        p.lineTo(x0, y1 - sp * 0.22f)
    }

    private fun stadium(p: Path, x0: Float, x1: Float, ya: Float, yb: Float, r: Float) {
        p.moveTo(x0, ya + r)
        p.arcTo(RectF(x0, ya, x1, ya + 2 * r), 180f, 180f)
        p.lineTo(x1, yb - r)
        p.arcTo(RectF(x0, yb - 2 * r, x1, yb), 0f, 180f)
        p.close()
    }
}
