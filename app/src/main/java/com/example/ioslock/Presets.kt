package com.example.ioslock

class Preset(
    val name: String,
    val sky: IntArray,
    val mountainBack: Int,
    val mountainFront: Int
)

object Presets {
    const val PREFS = "clock_prefs"
    const val KEY = "preset"
    const val KEY_ANIM = "animate"
    const val KEY_MOUNTAINS = "mountains"
    const val KEY_BG_MODE = "bg_mode"            // 0 = color preset, 1 = my photo
    const val KEY_BG_VERSION = "bg_version"      // goes up each time the photo changes
    const val PHOTO_FILE = "background.jpg"

    val all: List<Preset> = listOf(
        Preset(
            "Alpine",
            intArrayOf(0xFF03070E.toInt(), 0xFF334453.toInt(), 0xFFD4D9CB.toInt()),
            0xFF18232E.toInt(), 0xFF070C12.toInt()
        ),
        Preset(
            "Dusk",
            intArrayOf(0xFF1A1038.toInt(), 0xFF7C4676.toInt(), 0xFFF3A57E.toInt()),
            0xFF3A1F4F.toInt(), 0xFF140B2B.toInt()
        ),
        Preset(
            "Ocean",
            intArrayOf(0xFF05203B.toInt(), 0xFF1B6485.toInt(), 0xFF9FDDE2.toInt()),
            0xFF0E3A58.toInt(), 0xFF05182B.toInt()
        ),
        Preset(
            "Midnight",
            intArrayOf(0xFF010206.toInt(), 0xFF0E1222.toInt(), 0xFF353B5C.toInt()),
            0xFF12162A.toInt(), 0xFF000000.toInt()
        )
    )

    fun get(index: Int): Preset = all[index.coerceIn(0, all.size - 1)]
}
