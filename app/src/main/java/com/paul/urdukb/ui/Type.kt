package com.paul.urdukb.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's type scale. Screens use these names instead of loose font sizes, so a text size is changed in one place.
 * Sizes are in sp, so they follow the person's system font size.
 */
object Type {
    val caption = 13.sp
    val body = 15.sp
    val bodyLarge = 17.sp
    val title = 20.sp
    val headline = 28.sp
    val display = 30.sp
}

/** The same scale as a Material typography, for components that read it from the theme. */
val AppTypography = Typography(
    bodySmall = TextStyle(fontSize = Type.caption, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = Type.body, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = Type.bodyLarge, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = Type.bodyLarge, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = Type.title, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = Type.headline, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    headlineLarge = TextStyle(fontSize = Type.display, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = Type.body, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)
