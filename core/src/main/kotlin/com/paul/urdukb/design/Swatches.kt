package com.paul.urdukb.design

/** One colour the person can pick for the keyboard and the app. [tint] says how strongly the colour shows in the greys. */
class Swatch(val id: String, val name: String, val color: Int, val tint: Float = 1f)

object Swatches {
    const val WALLPAPER = "wallpaper"

    val ALL = listOf(
        Swatch(WALLPAPER, "Wallpaper", 0),
        Swatch("mauve", "Mauve", 0xFFC27BA0.toInt()),
        Swatch("rose", "Rose", 0xFFE8618C.toInt()),
        Swatch("coral", "Coral", 0xFFF0735A.toInt()),
        Swatch("terracotta", "Clay", 0xFFC8683F.toInt()),
        Swatch("amber", "Amber", 0xFFE9A23B.toInt()),
        Swatch("lemon", "Lemon", 0xFFD9C533.toInt()),
        Swatch("lime", "Lime", 0xFF8BC34A.toInt()),
        Swatch("green", "Green", 0xFF5FB87A.toInt()),
        Swatch("forest", "Forest", 0xFF3E8E5A.toInt()),
        Swatch("teal", "Teal", 0xFF2FB5A8.toInt()),
        Swatch("cyan", "Cyan", 0xFF36B9D6.toInt()),
        Swatch("sky", "Sky", 0xFF4FA3E0.toInt()),
        Swatch("blue", "Blue", 0xFF3D7BE8.toInt()),
        Swatch("indigo", "Indigo", 0xFF6C7CE8.toInt()),
        Swatch("violet", "Violet", 0xFFA678E3.toInt()),
        Swatch("magenta", "Magenta", 0xFFC653C9.toInt()),
        Swatch("brown", "Brown", 0xFF9C7357.toInt(), tint = 0.55f),
        Swatch("sand", "Sand", 0xFFC9B48A.toInt(), tint = 0.50f),
        Swatch("graphite", "Graphite", 0xFF8A9099.toInt(), tint = 0.18f),
    )

    /** The swatches that build colours from a seed (everything but the wallpaper one). */
    val SEEDED get() = ALL.filter { it.id != WALLPAPER }
}
