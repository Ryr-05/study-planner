package io.github.ryr05.studyplanner.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Night-desk palette: deep aubergine ground, one lavender accent.
 * Priority colors exist only to signal real state (how urgent a task is).
 */
object Palette {
    // Ground and solid surfaces
    val Night = Color(0xFF0E0A17)
    val Surface = Color(0xFF1A1427)
    val SurfaceHigh = Color(0xFF241C36)
    val Outline = Color(0xFF3A2F52)
    val Hairline = Color(0xFF2A2140)

    // The single accent
    val Lavender = Color(0xFFB9A3FF)
    val LavenderLight = Color(0xFFCEBEFF)
    val OnLavender = Color(0xFF1B1033)

    // Text (all pass WCAG AA on Night and Surface)
    val TextPrimary = Color(0xFFF2EEFA)
    val TextSecondary = Color(0xFFB5AACB)
    val TextTertiary = Color(0xFF948AAD)

    // Semantic: task priority
    val PriorityHigh = Color(0xFFFF8FA3)
    val PriorityMedium = Color(0xFFFFC978)
    val PriorityLow = Color(0xFF8DB8FF)

    // Background aurora, kept low in saturation so content stays readable
    val AuroraViolet = Color(0xFF5B3FC4)
    val AuroraPlum = Color(0xFF8E3F8A)
    val AuroraIndigo = Color(0xFF2B2E86)

    // Glass material
    val GlassTint = Color(0xFF2A1F48)
    val GlassFallback = Color(0xFF221A38)
    val Shadow = Color(0xFF05020C)
}
