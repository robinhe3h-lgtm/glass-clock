package com.example.ioslock

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Presets.PREFS, MODE_PRIVATE)
        val pad = dp(24)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Glass Clock Wallpaper"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        root.addView(label("1. Pick a color"))
        Presets.all.forEachIndexed { index, preset ->
            root.addView(Button(this).apply {
                text = preset.name
                isAllCaps = false
                setOnClickListener {
                    prefs.edit().putInt(Presets.KEY, index).apply()
                    Toast.makeText(this@MainActivity, "${preset.name} selected", Toast.LENGTH_SHORT).show()
                }
            })
        }

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
                setOnClickListener {
                    prefs.edit().putInt(Presets.KEY_TARGET, value).apply()
                }
            })
        }
        root.addView(group)
        root.addView(TextView(this).apply {
            text = "Choosing lock or home only needs Android 14 or newer. " +
                "On older phones the clock shows on both screens."
            textSize = 13f
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(CheckBox(this).apply {
            text = "Animate the clock when the screen turns on"
            isChecked = prefs.getBoolean(Presets.KEY_ANIM, true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(Presets.KEY_ANIM, on).apply()
            }
        })

        root.addView(label("3. Set it"))
        root.addView(TextView(this).apply {
            text = "Tap the button, then choose the same screen in the window your phone shows."
            textSize = 14f
        })
        root.addView(Button(this).apply {
            text = "Set as wallpaper"
            isAllCaps = false
            setOnClickListener { openWallpaperPicker() }
        })

        root.addView(label("4. Hide your phone's own clock"))
        root.addView(TextView(this).apply {
            text = "Your phone draws its own clock on top. In Settings, look for Lock screen → " +
                "Clock style (or Clock) and turn it off or choose none."
            textSize = 14f
        })
        root.addView(Button(this).apply {
            text = "Open phone settings"
            isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Open Settings yourself", Toast.LENGTH_SHORT).show()
                }
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(22), 0, dp(6))
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
                    "Open Settings → Wallpaper and choose \"Glass Clock Wallpaper\"",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
