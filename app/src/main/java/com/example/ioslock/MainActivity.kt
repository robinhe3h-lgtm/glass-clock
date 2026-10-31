package com.example.ioslock

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val REQ_BACKGROUND = 41
private const val REQ_HOME = 42

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences(Presets.PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Glass Clock Wallpaper"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        })

        // 1. Look
        root.addView(label("1. Pick a look"))
        Presets.all.forEachIndexed { index, preset ->
            root.addView(button(preset.name) { choosePreset(index, preset.name) })
        }
        root.addView(button("Use my own photo") { pickImage(REQ_BACKGROUND) })
        root.addView(button("Remove my photo") { removePhoto() })
        root.addView(note("Your photo gets a soft dark tint so the clock stays easy to read."))
        root.addView(checkbox("Show mountains", Presets.KEY_MOUNTAINS, true))
        root.addView(checkbox("Animate the clock when the screen turns on", Presets.KEY_ANIM, true))

        // 2. Where
        root.addView(label("2. Show the clock on"))
        val saved = prefs.getInt(Presets.KEY_TARGET, Presets.TARGET_LOCK)
        val group = RadioGroup(this)
        listOf(
            "Lock screen" to Presets.TARGET_LOCK,
            "Home screen" to Presets.TARGET_HOME,
            "Both screens" to Presets.TARGET_BOTH
        ).forEach { (name, value) ->
            group.addView(RadioButton(this).apply {
                text = name
                id = View.generateViewId()
                isChecked = value == saved
                setOnClickListener { prefs.edit().putInt(Presets.KEY_TARGET, value).apply() }
            })
        }
        root.addView(group)
        root.addView(note(
            "Choosing lock or home only needs Android 14 or newer. " +
                "If your phone's window only offers Home or Both, pick Both: " +
                "for the lock screen option, the app will then offer to keep your home screen separate."
        ))

        // 3. Set
        root.addView(label("3. Set it"))
        root.addView(button("Set as wallpaper") { openWallpaperPicker() })

        // 4. System clock
        root.addView(label("4. Hide your phone's own clock"))
        root.addView(note(
            "Your phone draws its own clock on top. In Settings, look for Lock screen, " +
                "then Clock style, and turn it off or choose none."
        ))
        root.addView(button("Open phone settings") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                toast("Open Settings yourself")
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(Presets.KEY_PENDING_LOCK_ONLY, false)) {
            prefs.edit().putBoolean(Presets.KEY_PENDING_LOCK_ONLY, false).apply()
            if (clockIsHomeWallpaper()) showLockOnlyDialog()
        }
    }

    // ---------- small UI helpers ----------

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun note(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun checkbox(text: String, key: String, default: Boolean): CheckBox = CheckBox(this).apply {
        this.text = text
        isChecked = prefs.getBoolean(key, default)
        setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------- look ----------

    private fun choosePreset(index: Int, name: String) {
        val wasPhoto = prefs.getInt(Presets.KEY_BG_MODE, 0) == 1
        val edit = prefs.edit().putInt(Presets.KEY, index).putInt(Presets.KEY_BG_MODE, 0)
        if (wasPhoto) edit.putBoolean(Presets.KEY_MOUNTAINS, true)
        edit.apply()
        toast("$name selected")
        if (wasPhoto) recreate()
    }

    private fun saveBackground(bmp: Bitmap) {
        try {
            FileOutputStream(File(filesDir, Presets.PHOTO_FILE)).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            prefs.edit()
                .putInt(Presets.KEY_BG_MODE, 1)
                .putInt(Presets.KEY_BG_VERSION, prefs.getInt(Presets.KEY_BG_VERSION, 0) + 1)
                .putBoolean(Presets.KEY_MOUNTAINS, false)
                .apply()
            toast("Photo set. Mountains are hidden; turn them on if you like.")
            recreate()
        } catch (e: Exception) {
            toast("Couldn't save the photo")
        }
    }

    private fun removePhoto() {
        File(filesDir, Presets.PHOTO_FILE).delete()
        prefs.edit()
            .putInt(Presets.KEY_BG_MODE, 0)
            .putInt(Presets.KEY_BG_VERSION, prefs.getInt(Presets.KEY_BG_VERSION, 0) + 1)
            .putBoolean(Presets.KEY_MOUNTAINS, true)
            .apply()
        toast("Photo removed")
        recreate()
    }

    // ---------- picking and preparing pictures ----------

    private fun pickImage(code: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose a picture"), code)
        } catch (e: Exception) {
            toast("No gallery app found")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode != REQ_BACKGROUND && requestCode != REQ_HOME) return

        val size = screenSize()
        toast("Loading picture...")
        Thread {
            val bmp = loadCropped(uri, size.first, size.second)
            runOnUiThread {
                if (bmp == null) {
                    toast("Couldn't open that picture")
                } else if (requestCode == REQ_BACKGROUND) {
                    saveBackground(bmp)
                } else {
                    setHomePicture(bmp)
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return Pair(minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels))
    }

    /** Loads a picture, turns it upright, and crops it to fill a screen-sized bitmap. */
    private fun loadCropped(uri: Uri, tw: Int, th: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minOf(tw, th) &&
                maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxOf(tw, th)
            ) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val rotation = contentResolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0

            val swap = rotation == 90 || rotation == 270
            val rw = (if (swap) raw.height else raw.width).toFloat()
            val rh = (if (swap) raw.width else raw.height).toFloat()
            val scale = maxOf(tw / rw, th / rh)

            val m = Matrix()
            m.postTranslate(-raw.width / 2f, -raw.height / 2f)
            m.postRotate(rotation.toFloat())
            m.postScale(scale, scale)
            m.postTranslate(tw / 2f, th / 2f)

            val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(raw, m, Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle()
            out
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun exifRotation(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    // ---------- setting the wallpaper ----------

    private fun openWallpaperPicker() {
        val lockOnly = prefs.getInt(Presets.KEY_TARGET, Presets.TARGET_LOCK) == Presets.TARGET_LOCK
        prefs.edit().putBoolean(Presets.KEY_PENDING_LOCK_ONLY, lockOnly).apply()
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ClockWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                toast("Open Settings, then Wallpaper, and choose Glass Clock Wallpaper")
            }
        }
    }

    private fun clockIsHomeWallpaper(): Boolean = try {
        WallpaperManager.getInstance(this).wallpaperInfo?.serviceName ==
            ClockWallpaperService::class.java.name
    } catch (e: Exception) {
        false
    }

    private fun showLockOnlyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clock on the lock screen only?")
            .setMessage(
                "Your phone also put the clock on the home screen. To keep it on the lock " +
                    "screen only, give your home screen its own picture.\n\n" +
                    "This changes your home screen picture. If your lock screen goes plain " +
                    "afterwards, set the clock again and choose Both."
            )
            .setPositiveButton("Choose my picture") { _, _ -> pickImage(REQ_HOME) }
            .setNeutralButton("Matching scenery") { _, _ -> useSceneryForHome() }
            .setNegativeButton("Keep both") { _, _ -> }
            .show()
    }

    private fun useSceneryForHome() {
        val size = screenSize()
        val index = prefs.getInt(Presets.KEY, 0)
        Thread {
            val bmp = Scene().renderBitmap(size.first, size.second, index)
            runOnUiThread { setHomePicture(bmp) }
        }.start()
    }

    private fun setHomePicture(bmp: Bitmap) {
        try {
            WallpaperManager.getInstance(this)
                .setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            toast("Home screen picture set")
        } catch (e: Exception) {
            toast("Couldn't set the home screen picture")
        }
    }
}
