package com.example.ioslock

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.SurfaceHolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Wake-up animation timing in milliseconds. Change these to tune how it feels.
private const val STAGGER_MS = 80L       // delay between one digit and the next
private const val DIGIT_MS = 600f        // how long each digit takes to appear
private const val DATE_MS = 450f
private const val MOUNTAIN_MS = 800f
private const val LEAD_IN_MS = 180L      // starts the animation already this far in, so it never feels late
private const val ANIM_TOTAL_MS = 1000L
private const val HIDDEN = -1_000_000L   // "before the animation starts" state
private const val DONE = 1_000_000L      // "animation finished" state

// Digit layout
private const val GAP = 0.15f
private const val COLON_W = 0.32f

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private class Cell(val ch: Char, val x: Float, val w: Float, val path: Path?)

    inner class ClockEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val prefs = getSharedPreferences(Presets.PREFS, Context.MODE_PRIVATE)

        private var visible = false
        private var animating = false
        private var hidden = false
        private var animStart = 0L
        private var useHardware = Build.VERSION.SDK_INT >= 26

        // Settings, read once and refreshed only when they change
        private var presetIndex = 0
        private var target = Presets.TARGET_LOCK
        private var animOn = true
        private var mountainsOn = true
        private var photoMode = false
        private var photoVersion = -1
        private var photo: Bitmap? = null

        private val scene = Scene()
        private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val scrimPaint = Paint()
        private var scrimH = 0f
        private val dst = RectF()

        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Text and digit shapes, rebuilt only when the minute or screen size changes
        private var timeText = ""
        private var dateText = ""
        private val cells = ArrayList<Cell>()
        private var layoutW = 0f
        private var layoutH = 0f
        private var layoutText = ""
        private var dh = 0f
        private var top = 0f

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible || !animating) return
                if (SystemClock.uptimeMillis() - animStart + LEAD_IN_MS >= ANIM_TOTAL_MS) {
                    animating = false
                }
                draw()
                if (animating) Choreographer.getInstance().postFrameCallback(this)
            }
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (animOn) {
                            Choreographer.getInstance().removeFrameCallback(frameCallback)
                            animating = false
                            hidden = true
                            draw(true)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (visible && animOn) startAnimation()
                    }
                    else -> {
                        refreshText()
                        draw()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            digitPaint.style = Paint.Style.STROKE
            digitPaint.strokeJoin = Paint.Join.ROUND
            digitPaint.strokeCap = Paint.Cap.BUTT
            dotPaint.style = Paint.Style.FILL

            datePaint.typeface = loadCustomFont() ?: weighted(Typeface.DEFAULT, 600, Typeface.BOLD)
            datePaint.textAlign = Paint.Align.CENTER
            datePaint.color = Color.argb(238, 255, 255, 255)

            loadPrefs()
            refreshText()
            prefs.registerOnSharedPreferenceChangeListener(this)

            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_TIME_TICK)
            filter.addAction(Intent.ACTION_TIME_CHANGED)
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            registerReceiver(receiver, filter)
        }

        override fun onDestroy() {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
            releasePhoto()
            super.onDestroy()
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

        // ---------- settings ----------

        private fun loadPrefs() {
            presetIndex = prefs.getInt(Presets.KEY, 0).coerceIn(0, Presets.all.size - 1)
            target = prefs.getInt(Presets.KEY_TARGET, Presets.TARGET_LOCK)
            animOn = prefs.getBoolean(Presets.KEY_ANIM, true)
            mountainsOn = prefs.getBoolean(Presets.KEY_MOUNTAINS, true)
            photoMode = prefs.getInt(Presets.KEY_BG_MODE, 0) == 1
            if (!animOn) hidden = false

            val version = prefs.getInt(Presets.KEY_BG_VERSION, 0)
            if (!photoMode) {
                releasePhoto()
            } else if (photo == null || version != photoVersion) {
                loadPhoto()
            }
            photoVersion = version
        }

        private fun loadPhoto() {
            releasePhoto()
            photo = try {
                BitmapFactory.decodeFile(File(filesDir, Presets.PHOTO_FILE).absolutePath)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            if (photo == null) photoMode = false
        }

        private fun releasePhoto() {
            photo?.recycle()
            photo = null
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            loadPrefs()
            if (visible || hidden) draw(true)
        }

        private fun refreshText() {
            val now = Date()
            val locale = Locale.getDefault()
            // Locale.US keeps the digits 0-9 even on phones set to Bengali, Arabic, etc.
            val timePattern =
                if (DateFormat.is24HourFormat(this@ClockWallpaperService)) "HH:mm" else "h:mm"
            timeText = SimpleDateFormat(timePattern, Locale.US).format(now)
            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            dateText = SimpleDateFormat(datePattern, locale).format(now)
        }

        // ---------- visibility and animation ----------

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                refreshText()
                startAnimation()
            } else {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                animating = false
                if (animOn) {
                    // Paint the "before" frame now, so the next time the screen turns on it
                    // already shows an empty start and never flashes the finished clock.
                    hidden = true
                    draw(true)
                }
            }
        }

        private fun startAnimation() {
            hidden = false
            if (!animOn) {
                animating = false
                draw()
                return
            }
            val now = SystemClock.uptimeMillis()
            if (animating && now - animStart < 500L) return   // already started a moment ago
            animStart = now
            animating = true
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            draw()   // first frame right away, no waiting for the next vsync
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        private fun currentElapsed(): Long = when {
            hidden -> HIDDEN
            animating -> SystemClock.uptimeMillis() - animStart + LEAD_IN_MS
            else -> DONE
        }

        /** 0 to 1 with a fast start and a soft landing. */
        private fun ease(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            val inv = 1f - c
            return 1f - inv * inv * inv
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

        // ---------- drawing ----------

        private fun draw(force: Boolean = false) {
            if (!visible && !force) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = lockCanvasSafe(holder)
                if (canvas != null) drawFrame(canvas, currentElapsed())
            } catch (e: Exception) {
                // the surface went away; nothing to draw on
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }

        private fun lockCanvasSafe(holder: SurfaceHolder): Canvas? {
            if (useHardware) {
                try {
                    return holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    useHardware = false
                }
            }
            return holder.lockCanvas()
        }

        /**
         * Uses the "Show the clock on" choice from the app (lock / home / both).
         * Telling the two screens apart needs Android 14+; older versions show the clock on both.
         */
        private fun shouldShowClock(): Boolean {
            if (isPreview) return true
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

        private fun drawFrame(canvas: Canvas, elapsed: Long) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            scene.update(w, h, presetIndex)

            val p = photo
            if (photoMode && p != null) {
                dst.set(0f, 0f, w, h)
                canvas.drawBitmap(p, null, dst, photoPaint)
                if (scrimH != h) {
                    // soft dark tint so the clock stays readable on any photo
                    scrimPaint.shader = LinearGradient(
                        0f, 0f, 0f, h,
                        Color.argb(120, 0, 0, 0), Color.argb(40, 0, 0, 0),
                        Shader.TileMode.CLAMP
                    )
                    scrimH = h
                }
                canvas.drawRect(0f, 0f, w, h, scrimPaint)
            } else {
                scene.drawSky(canvas)
            }

            if (shouldShowClock()) drawClock(canvas, w, h, elapsed)

            if (mountainsOn) {
                scene.drawMountains(canvas, 1f - ease(elapsed.toFloat() / MOUNTAIN_MS))
            }
        }

        private fun ensureLayout(w: Float, h: Float) {
            if (w == layoutW && h == layoutH && timeText == layoutText) return
            layoutW = w
            layoutH = h
            layoutText = timeText

            var units = GAP * (timeText.length - 1)
            timeText.forEach { units += if (it == ':') COLON_W else 1f }

            val dw = (w * 0.86f) / units
            dh = h * 0.40f
            top = h * 0.105f

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
            dotPaint.shader = glass
            digitPaint.strokeWidth = TallDigits.strokeWidth(dw)
            datePaint.textSize = w * 0.05f

            cells.clear()
            var x = (w - units * dw) / 2f
            timeText.forEach { ch ->
                val cw = if (ch == ':') COLON_W * dw else dw
                val path = if (ch == ':') null else TallDigits.path(ch, x, top, dw, dh)
                cells.add(Cell(ch, x, cw, path))
                x += cw + GAP * dw
            }
        }

        private fun drawClock(canvas: Canvas, w: Float, h: Float, elapsed: Long) {
            ensureLayout(w, h)

            val dateP = ease(elapsed.toFloat() / DATE_MS)
            datePaint.alpha = (238 * dateP).toInt()
            canvas.drawText(
                dateText,
                w / 2f,
                h * 0.075f + (1f - dateP) * h * 0.012f,
                datePaint
            )

            cells.forEachIndexed { index, cell ->
                // each digit starts a little after the one before it
                val p = ease((elapsed - index * STAGGER_MS).toFloat() / DIGIT_MS)
                val alpha = (255 * p).toInt()
                digitPaint.alpha = alpha
                dotPaint.alpha = alpha
                val scale = 0.9f + 0.1f * p

                canvas.save()
                canvas.translate(0f, (1f - p) * dh * 0.10f)
                canvas.scale(scale, scale, cell.x + cell.w / 2f, top + dh)
                val path = cell.path
                if (path == null) {
                    val r = cell.w / 2f
                    canvas.drawCircle(cell.x + r, top + dh * 0.315f, r, dotPaint)
                    canvas.drawCircle(cell.x + r, top + dh * 0.757f, r, dotPaint)
                } else {
                    canvas.drawPath(path, digitPaint)
                }
                canvas.restore()
            }
        }
    }
}
