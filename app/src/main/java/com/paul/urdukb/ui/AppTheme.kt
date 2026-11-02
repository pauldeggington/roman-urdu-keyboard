package com.paul.urdukb.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.material3.LocalTextStyle

/**
 * The look of the app screens: always the deep green and gold of the website (see [Brand]), whatever the phone's
 * light or dark setting. The keyboard's own colours are a separate choice, made in KeyboardLook.
 */
@Composable
fun RomanUrduTheme(urdu: Boolean = isUrduScreen(), content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        background = Brand.page, onBackground = Brand.text,
        surface = Brand.card, onSurface = Brand.text,
        surfaceVariant = Brand.raised, onSurfaceVariant = Brand.muted,
        primary = Brand.gold, onPrimary = Brand.onGold,
        secondaryContainer = Brand.raised, onSecondaryContainer = Brand.text,
        outline = Brand.line, outlineVariant = Brand.line,
        error = Brand.error, onError = Color(0xFF601410),
    )
    // Urdu script is drawn smaller than Latin at the same size (and its marks need room above and below), so it gets a lift
    val d = LocalDensity.current
    val density = if (urdu) Density(d.density, d.fontScale * URDU_TEXT_LIFT) else d
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = AppFont), content = content)
        }
    }
}

/** Urdu text is drawn this much larger than Latin text of the same size. */
const val URDU_TEXT_LIFT = 1.15f

/** True when the phone's language is Urdu. */
@Composable
fun isUrduScreen(): Boolean = LocalConfiguration.current.locales.get(0)?.language == "ur"

/** Green for "done", red for problems: not part of the Material scheme. */
@Composable
fun statusColors(): Pair<Color, Color> = Brand.green2 to Brand.error
