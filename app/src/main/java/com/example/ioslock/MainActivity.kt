package com.example.ioslock

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "iOS Clock Wallpaper"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Pick a color, then set it as your wallpaper. " +
                "Choose \"Lock screen\" (or both) when Android asks."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        })

        val prefs = getSharedPreferences(Presets.PREFS, MODE_PRIVATE)
        Presets.names.forEachIndexed { index, name ->
            root.addView(Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener {
                    prefs.edit().putInt(Presets.KEY, index).apply()
                    Toast.makeText(this@MainActivity, "$name selected", Toast.LENGTH_SHORT).show()
                }
            })
        }

        root.addView(Button(this).apply {
            text = "Set as wallpaper"
            isAllCaps = false
            setOnClickListener { openWallpaperPicker() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        root.addView(TextView(this).apply {
            text = "Tip: your phone's own lock screen clock may still show on top. " +
                "Turn it off in Settings → Lock screen → Clock style."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(root)
    }

    private fun openWallpaperPicker() {
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
                Toast.makeText(
                    this,
                    "Open Settings → Wallpaper and choose \"iOS Clock Wallpaper\"",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
