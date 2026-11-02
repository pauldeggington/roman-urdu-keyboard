package com.paul.urdukb.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.paul.urdukb.R

/** Inter, the website's face, for every app screen. Urdu text falls through to the phone's Urdu font. */
val AppFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * The app's type scale. Screens use these names instead of loose font sizes, so a text size is changed in one place.
 * Sizes are in sp, so they follow the person's system font size.
 */
object Type {
    val caption = 13.sp
    val body = 15.sp
    val bodyLarge = 17.sp
    val title = 20.sp
    val headline = 30.sp
    val display = 38.sp
}

/** The same scale as a Material typography, for components that read it from the theme. */
val AppTypography = Typography(
    bodySmall = TextStyle(fontFamily = AppFont, fontSize = Type.caption, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontSize = Type.body, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontSize = Type.bodyLarge, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontSize = Type.bodyLarge, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = AppFont, fontSize = Type.title, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = AppFont, fontSize = Type.headline, lineHeight = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = AppFont, fontSize = Type.display, lineHeight = 42.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontSize = Type.body, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)
