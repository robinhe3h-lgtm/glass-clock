package com.example.ioslock

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine() {

        private var visible = false
        private var receiverRegistered = false

        private val bgPaint = Paint()
        private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val tickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                draw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            digitPaint.style = Paint.Style.STROKE
            digitPaint.strokeJoin = Paint.Join.ROUND
            digitPaint.strokeCap = Paint.Cap.BUTT
            dotPaint.style = Paint.Style.FILL

            datePaint.apply {
                typeface = loadCustomFont() ?: weighted(Typeface.DEFAULT, 600, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                color = Color.argb(238, 255, 255, 255)
            }
        }

        /** Optional: drop a font file at app/src/main/assets/fonts/clock.ttf to use it for the date. */
        private fun loadCustomFont(): Typeface? = try {
            Typeface.createFromAsset(assets, "fonts/clock.ttf")
        } catch (e: Exception) {
            null
        }

        private fun weighted(base: Typeface, weight: Int, fallbackStyle: Int): Typeface =
            if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, weight, false)
            } else {
                Typeface.create(base, fallbackStyle)
            }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                registerTick()
                draw()
            } else {
                unregisterTick()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onDestroy() {
            unregisterTick()
            super.onDestroy()
        }

        private fun registerTick() {
            if (receiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(tickReceiver, filter)
            receiverRegistered = true
        }

        private fun unregisterTick() {
            if (!receiverRegistered) return
            try {
                unregisterReceiver(tickReceiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
            receiverRegistered = false
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) drawFrame(canvas)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        /** On Android 14+ the clock only shows on the lock screen; the home screen stays clean. */
        private fun shouldShowClock(): Boolean {
            if (isPreview) return true
            if (Build.VERSION.SDK_INT >= 34) {
                val flags = try {
                    wallpaperFlags
                } catch (e: Exception) {
                    return true
                }
                return (flags and WallpaperManager.FLAG_LOCK) != 0
            }
            return true
        }

        private fun drawFrame(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            val prefs = getSharedPreferences(Presets.PREFS, Context.MODE_PRIVATE)
            val preset = Presets.get(prefs.getInt(Presets.KEY, 0))

            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                preset.sky,
                floatArrayOf(0f, 0.32f, 0.56f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            if (shouldShowClock()) drawClock(canvas, w, h)

            drawMountains(canvas, w, h, preset)
        }

        private fun drawClock(canvas: Canvas, w: Float, h: Float) {
            val now = Date()
            val locale = Locale.getDefault()

            // Locale.US keeps the digits 0-9 even on phones set to Bengali, Arabic, etc.
            val timePattern =
                if (DateFormat.is24HourFormat(this@ClockWallpaperService)) "HH:mm" else "h:mm"
            val timeText = SimpleDateFormat(timePattern, Locale.US).format(now)

            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            val dateText = SimpleDateFormat(datePattern, locale).format(now)

            datePaint.textSize = w * 0.05f
            canvas.drawText(dateText, w / 2f, h * 0.075f, datePaint)

            val gap = 0.15f
            val colonW = 0.32f
            var units = gap * (timeText.length - 1)
            timeText.forEach { units += if (it == ':') colonW else 1f }

            val dw = (w * 0.86f) / units
            val dh = h * 0.40f
            val top = h * 0.105f

            // Glass look: see-through grey at the top, bright and solid near the bottom
            val glass = LinearGradient(
                0f, top, 0f, top + dh,
                intArrayOf(
                    Color.argb(150, 150, 160, 172),
                    Color.argb(176, 170, 185, 195),
                    Color.argb(246, 232, 242, 236)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            digitPaint.shader = glass
            digitPaint.strokeWidth = TallDigits.strokeWidth(dw)
            dotPaint.shader = glass

            var x = (w - units * dw) / 2f
            timeText.forEach { ch ->
                if (ch == ':') {
                    val r = colonW * dw / 2f
                    canvas.drawCircle(x + r, top + dh * 0.315f, r, dotPaint)
                    canvas.drawCircle(x + r, top + dh * 0.757f, r, dotPaint)
                    x += colonW * dw
                } else {
                    canvas.drawPath(TallDigits.path(ch, x, top, dw, dh), digitPaint)
                    x += dw
                }
                x += gap * dw
            }
        }

        private fun drawMountains(canvas: Canvas, w: Float, h: Float, preset: Preset) {
            mountainPaint.pathEffect = CornerPathEffect(w * 0.012f)

            val back = Path().apply {
                moveTo(0f, h * 0.52f)
                lineTo(w * 0.10f, h * 0.47f)
                lineTo(w * 0.22f, h * 0.51f)
                lineTo(w * 0.36f, h * 0.455f)
                lineTo(w * 0.50f, h * 0.50f)
                lineTo(w * 0.66f, h * 0.465f)
                lineTo(w * 0.82f, h * 0.505f)
                lineTo(w, h * 0.46f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            mountainPaint.color = preset.mountainBack
            canvas.drawPath(back, mountainPaint)

            val front = Path().apply {
                moveTo(0f, h * 0.60f)
                lineTo(w * 0.14f, h * 0.55f)
                lineTo(w * 0.30f, h * 0.62f)
                lineTo(w * 0.48f, h * 0.575f)
                lineTo(w * 0.64f, h * 0.64f)
                lineTo(w * 0.80f, h * 0.58f)
                lineTo(w, h * 0.62f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            mountainPaint.color = preset.mountainFront
            canvas.drawPath(front, mountainPaint)
        }
    }
}
