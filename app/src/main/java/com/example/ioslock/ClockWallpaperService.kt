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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Wake-up animation timing (milliseconds)
private const val STAGGER_MS = 90L
private const val DIGIT_MS = 700f
private const val DATE_MS = 500f
private const val MOUNTAIN_MS = 900f
private const val ANIM_TOTAL_MS = 1300L
private const val FRAME_MS = 16L

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine() {

        private var visible = false
        private var receiverRegistered = false

        private val handler = Handler(Looper.getMainLooper())
        private var animating = false
        private var animStart = 0L

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

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!visible) return
                if (SystemClock.uptimeMillis() - animStart >= ANIM_TOTAL_MS) animating = false
                draw()
                if (animating) handler.postDelayed(this, FRAME_MS)
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
                startAnimation()
            } else {
                unregisterTick()
                handler.removeCallbacks(frameRunnable)
                animating = false
            }
        }

        /** Runs every time the screen turns on or the wallpaper becomes visible again. */
        private fun startAnimation() {
            val prefs = getSharedPreferences(Presets.PREFS, Context.MODE_PRIVATE)
            handler.removeCallbacks(frameRunnable)
            if (prefs.getBoolean(Presets.KEY_ANIM, true)) {
                animStart = SystemClock.uptimeMillis()
                animating = true
                handler.post(frameRunnable)
            } else {
                animating = false
                draw()
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
            handler.removeCallbacks(frameRunnable)
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

        /**
         * Uses the "Show the clock on" choice from the app (lock / home / both).
         * Telling the two screens apart needs Android 14+; older versions show the clock on both.
         */
        private fun shouldShowClock(): Boolean {
            if (isPreview) return true
            val prefs = getSharedPreferences(Presets.PREFS, Context.MODE_PRIVATE)
            val target = prefs.getInt(Presets.KEY_TARGET, Presets.TARGET_LOCK)
            if (target == Presets.TARGET_BOTH) return true
            if (Build.VERSION.SDK_INT >= 34) {
                val flags = try {
                    wallpaperFlags
                } catch (e: Exception) {
                    return true
                }
                if (flags == 0) return true
                val isLock = (flags and WallpaperManager.FLAG_LOCK) != 0
                val isHome = (flags and WallpaperManager.FLAG_SYSTEM) != 0
                if (isLock && isHome) return true
                return if (target == Presets.TARGET_LOCK) isLock else isHome
            }
            return true
        }

        private fun elapsedMs(): Long =
            if (animating) SystemClock.uptimeMillis() - animStart else Long.MAX_VALUE / 4

        /** 0 to 1 with a fast start and a soft landing. */
        private fun ease(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            val inv = 1f - c
            return 1f - inv * inv * inv
        }

        private fun drawFrame(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            val prefs = getSharedPreferences(Presets.PREFS, Context.MODE_PRIVATE)
            val preset = Presets.get(prefs.getInt(Presets.KEY, 0))
            val elapsed = elapsedMs()

            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                preset.sky,
                floatArrayOf(0f, 0.32f, 0.56f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            if (shouldShowClock()) drawClock(canvas, w, h, elapsed)

            drawMountains(canvas, w, h, preset, elapsed)
        }

        private fun drawClock(canvas: Canvas, w: Float, h: Float, elapsed: Long) {
            val now = Date()
            val locale = Locale.getDefault()

            // Locale.US keeps the digits 0-9 even on phones set to Bengali, Arabic, etc.
            val timePattern =
                if (DateFormat.is24HourFormat(this@ClockWallpaperService)) "HH:mm" else "h:mm"
            val timeText = SimpleDateFormat(timePattern, Locale.US).format(now)

            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            val dateText = SimpleDateFormat(datePattern, locale).format(now)

            val dateP = ease(elapsed.toFloat() / DATE_MS)
            datePaint.textSize = w * 0.05f
            datePaint.alpha = (238 * dateP).toInt()
            canvas.drawText(
                dateText,
                w / 2f,
                h * 0.075f + (1f - dateP) * h * 0.012f,
                datePaint
            )

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
            timeText.forEachIndexed { index, ch ->
                // Each digit starts a little after the one before it
                val p = ease((elapsed - index * STAGGER_MS).toFloat() / DIGIT_MS)
                val alpha = (255 * p).toInt()
                digitPaint.alpha = alpha
                dotPaint.alpha = alpha

                val cellW = if (ch == ':') colonW * dw else dw
                val scale = 0.9f + 0.1f * p

                canvas.save()
                canvas.translate(0f, (1f - p) * dh * 0.10f)
                canvas.scale(scale, scale, x + cellW / 2f, top + dh)

                if (ch == ':') {
                    val r = cellW / 2f
                    canvas.drawCircle(x + r, top + dh * 0.315f, r, dotPaint)
                    canvas.drawCircle(x + r, top + dh * 0.757f, r, dotPaint)
                } else {
                    canvas.drawPath(TallDigits.path(ch, x, top, dw, dh), digitPaint)
                }
                canvas.restore()

                x += cellW + gap * dw
            }
        }

        private fun drawMountains(canvas: Canvas, w: Float, h: Float, preset: Preset, elapsed: Long) {
            mountainPaint.pathEffect = CornerPathEffect(w * 0.012f)
            val rise = 1f - ease(elapsed.toFloat() / MOUNTAIN_MS)

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
            canvas.save()
            canvas.translate(0f, rise * h * 0.04f)
            canvas.drawPath(back, mountainPaint)
            canvas.restore()

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
            canvas.save()
            canvas.translate(0f, rise * h * 0.07f)
            canvas.drawPath(front, mountainPaint)
            canvas.restore()
        }
    }
}
