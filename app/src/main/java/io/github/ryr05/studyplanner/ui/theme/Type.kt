package io.github.ryr05.studyplanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.ryr05.studyplanner.R

/** Geist variable font (SIL Open Font License, see licenses/Geist-OFL.txt). */
val Geist = FontFamily(
    Font(R.font.geist, weight = FontWeight.Normal),
    Font(R.font.geist, weight = FontWeight.Medium),
    Font(R.font.geist, weight = FontWeight.SemiBold),
    Font(R.font.geist, weight = FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.geist() = copy(fontFamily = Geist)

val AppTypography = Typography(
    displayLarge = base.displayLarge.geist(),
    displayMedium = base.displayMedium.copy(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 56.sp,
        lineHeight = 60.sp, letterSpacing = (-1.5).sp,
    ),
    displaySmall = base.displaySmall.copy(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 34.sp,
        lineHeight = 40.sp, letterSpacing = (-0.8).sp,
    ),
    headlineLarge = base.headlineLarge.geist(),
    headlineMedium = base.headlineMedium.geist(),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
        lineHeight = 28.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = base.titleLarge.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
        lineHeight = 22.sp, letterSpacing = (-0.1).sp,
    ),
    titleSmall = base.titleSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(
        fontFamily = Geist, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        fontFamily = Geist, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodySmall = base.bodySmall.geist(),
    labelLarge = base.labelLarge.copy(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp,
    ),
    labelMedium = base.labelMedium.copy(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = base.labelSmall.copy(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp,
    ),
)
