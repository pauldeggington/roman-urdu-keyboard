package com.paul.urdukb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.paul.urdukb.Palette

/**
 * The look of the app screens. The colours come from [Palette], the same ones the keyboard settings use: wallpaper
 * (Material You) colours by default, or built from the swatch the person picked, light or dark as chosen.
 */
@Composable
fun RomanUrduTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val pal = remember { Palette(ctx) }
    val c = { v: Int -> Color(v) }
    val scheme = (if (pal.dark) darkColorScheme() else lightColorScheme()).copy(
        background = c(pal.appBg), onBackground = c(pal.text),
        surface = c(pal.card), onSurface = c(pal.text),
        surfaceVariant = c(pal.field), onSurfaceVariant = c(pal.textMuted),
        primary = c(pal.primary), onPrimary = c(pal.onPrimary),
        secondaryContainer = c(pal.tonal), onSecondaryContainer = c(pal.onTonal),
        error = c(pal.error),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Green for "done", red for problems: not part of the Material scheme, so read from the palette. */
@Composable
fun statusColors(): Pair<Color, Color> {
    val ctx = LocalContext.current
    val pal = remember { Palette(ctx) }
    return Color(pal.ok) to Color(pal.error)
}
